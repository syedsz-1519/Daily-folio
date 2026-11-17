package com.example.data.firebase

import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JournalRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireOwner(userId: String) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing journal entries.")
        check(uid == userId) { "Signed-in user changed; refusing cross-user journal access." }
    }

    fun observeEntries(userId: String): Flow<List<JournalEntry>> =
        db.collection("users")
            .document(userId)
            .collection("entries")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    JournalEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, "users/$userId/entries")
                }
                throw error
            }

    suspend fun createEntry(
        userId: String,
        title: String,
        content: String,
        mood: JournalMood,
        tags: List<String>,
        pinned: Boolean,
        sharedUrl: String? = null,
        sharedPlatform: String? = null,
        sharedSummary: String? = null,
        socialSnapshotSummary: String? = null,
        totalSocialMinutes: Long = 0L
    ): Result<String> {
        var docPath = "users/$userId/entries"
        return try {
            requireOwner(userId)
            val cleanTitle = title.trim()
            val cleanContent = content.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 140) {
                "Entry title must be between 1 and 140 characters."
            }
            require(cleanContent.isNotEmpty() && cleanContent.length <= 20000) {
                "Entry content must be between 1 and 20,000 characters."
            }

            val docRef = db.collection("users")
                .document(userId)
                .collection("entries")
                .document()
            docPath = docRef.path

            val entry = JournalEntry(
                userId = userId,
                title = cleanTitle,
                content = cleanContent,
                mood = mood.name,
                tags = tags,
                pinned = pinned,
                sharedUrl = sharedUrl,
                sharedPlatform = sharedPlatform,
                sharedSummary = sharedSummary,
                socialSnapshotSummary = socialSnapshotSummary,
                totalSocialMinutes = totalSocialMinutes
            )
            docRef.set(entry.toCreateMap(userId)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEntry(
        userId: String,
        entryId: String,
        title: String,
        content: String,
        mood: JournalMood,
        tags: List<String>,
        pinned: Boolean,
        socialSnapshotSummary: String? = null,
        totalSocialMinutes: Long = 0L
    ): Result<Unit> {
        val docPath = "users/$userId/entries/$entryId"
        return try {
            requireOwner(userId)
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            val cleanTitle = title.trim()
            val cleanContent = content.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 140) {
                "Entry title must be between 1 and 140 characters."
            }
            require(cleanContent.isNotEmpty() && cleanContent.length <= 20000) {
                "Entry content must be between 1 and 20,000 characters."
            }

            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "content" to cleanContent,
                "mood" to mood.name,
                "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.take(10),
                "pinned" to pinned,
                "totalSocialMinutes" to totalSocialMinutes.coerceIn(0L, 1440L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanSnap = socialSnapshotSummary?.trim()?.take(300)
            if (!cleanSnap.isNullOrEmpty()) {
                updates["socialSnapshotSummary"] = cleanSnap
            }

            db.collection("users")
                .document(userId)
                .collection("entries")
                .document(entryId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePinned(
        userId: String,
        entryId: String,
        pinned: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/entries/$entryId"
        return try {
            requireOwner(userId)
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            db.collection("users")
                .document(userId)
                .collection("entries")
                .document(entryId)
                .update(
                    mapOf(
                        "pinned" to pinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(userId: String, entryId: String): Result<Unit> {
        val docPath = "users/$userId/entries/$entryId"
        return try {
            requireOwner(userId)
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            db.collection("users")
                .document(userId)
                .collection("entries")
                .document(entryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
