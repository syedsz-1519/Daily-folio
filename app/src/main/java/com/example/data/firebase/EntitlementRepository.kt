package com.example.data.firebase

import com.example.data.model.UserEntitlement
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class EntitlementRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireOwner(userId: String) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before syncing entitlement.")
        check(uid == userId) { "Signed-in user changed; refusing cross-user entitlement access." }
    }

    fun observeEntitlement(userId: String): Flow<UserEntitlement> =
        db.collection("users")
            .document(userId)
            .collection("entitlement")
            .document("premium")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserEntitlement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: UserEntitlement(userId = userId)
                } else {
                    UserEntitlement(userId = userId)
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, "users/$userId/entitlement/premium")
                }
                throw error
            }

    suspend fun syncEntitlement(
        userId: String,
        adFree: Boolean,
        planType: String,
        productId: String,
        purchaseToken: String
    ): Result<Unit> {
        val docPath = "users/$userId/entitlement/premium"
        return try {
            requireOwner(userId)
            val safePlan = if (planType in setOf("FREE", "MONTHLY", "LIFETIME")) planType else "FREE"
            val docRef = db.collection("users")
                .document(userId)
                .collection("entitlement")
                .document("premium")

            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "adFree" to adFree,
                        "planType" to safePlan,
                        "productId" to productId.take(100),
                        "purchaseToken" to purchaseToken.take(512),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "adFree" to adFree,
                        "planType" to safePlan,
                        "productId" to productId.take(100),
                        "purchaseToken" to purchaseToken.take(512),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
