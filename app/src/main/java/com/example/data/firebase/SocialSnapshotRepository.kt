package com.example.data.firebase

import com.example.data.model.SocialSnapshot
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

class SocialSnapshotRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireOwner(userId: String) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing snapshots.")
        check(uid == userId) { "Signed-in user changed; refusing cross-user snapshot access." }
    }

    fun observeSnapshots(userId: String): Flow<List<SocialSnapshot>> =
        db.collection("users")
            .document(userId)
            .collection("socialSnapshots")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SocialSnapshot::class.java,
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
                    handleFirestoreError(toLog, OperationType.LIST, "users/$userId/socialSnapshots")
                }
                throw error
            }

    suspend fun upsertSnapshot(
        userId: String,
        docId: String,
        sourceType: String,
        label: String,
        totalMinutes: Long,
        subscriberCount: Long,
        viewCount: Long,
        videoCount: Long,
        platformMinutes: Map<String, Long>
    ): Result<Unit> {
        val safeDocId = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "snap_default" }
        val docPath = "users/$userId/socialSnapshots/$safeDocId"
        return try {
            requireOwner(userId)
            val docRef = db.collection("users")
                .document(userId)
                .collection("socialSnapshots")
                .document(safeDocId)

            val existing = docRef.get().await()
            val boundedPlatforms = platformMinutes.entries
                .take(20)
                .associate { (k, v) -> k.take(40) to v.coerceAtLeast(0L) }

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to userId,
                    "sourceType" to sourceType,
                    "label" to label.trim().take(140).ifBlank { safeDocId },
                    "totalMinutes" to totalMinutes.coerceIn(0L, 10080L),
                    "subscriberCount" to subscriberCount.coerceAtLeast(0L),
                    "viewCount" to viewCount.coerceAtLeast(0L),
                    "videoCount" to videoCount.coerceAtLeast(0L),
                    "platformMinutes" to boundedPlatforms,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "sourceType" to sourceType,
                    "label" to label.trim().take(140).ifBlank { safeDocId },
                    "totalMinutes" to totalMinutes.coerceIn(0L, 10080L),
                    "subscriberCount" to subscriberCount.coerceAtLeast(0L),
                    "viewCount" to viewCount.coerceAtLeast(0L),
                    "videoCount" to videoCount.coerceAtLeast(0L),
                    "platformMinutes" to boundedPlatforms,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit> {
        val docPath = "users/$userId/socialSnapshots/$snapshotId"
        return try {
            requireOwner(userId)
            db.collection("users")
                .document(userId)
                .collection("socialSnapshots")
                .document(snapshotId)
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
