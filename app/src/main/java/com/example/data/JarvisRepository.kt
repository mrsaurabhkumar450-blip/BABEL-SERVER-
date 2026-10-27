package com.example.data

import android.content.Context
import android.util.Log
import com.example.security.EncryptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class JarvisRepository(
    private val context: Context,
    private val encryptionManager: EncryptionManager
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _localLogs = MutableStateFlow<List<CommandLogItem>>(emptyList())
    val localLogs = _localLogs.asStateFlow()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { authInstance ->
            trySend(authInstance.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun logCommand(item: CommandLogItem) {
        // Encrypt the command response before persisting
        val encryptedResponse = encryptionManager.encrypt(item.response)
        val encryptedCommand = encryptionManager.encrypt(item.command)
        val secureItem = item.copy(
            command = item.command, // Keep readable locally for UI
            response = item.response,
            isEncrypted = true
        )

        // Update local list
        val updated = listOf(secureItem) + _localLogs.value.take(49)
        _localLogs.value = updated

        // Sync to Firestore if authenticated
        val user = auth.currentUser
        if (user != null) {
            try {
                val firestoreMap = hashMapOf(
                    "id" to item.id,
                    "timestamp" to item.timestamp,
                    "encCommand" to encryptedCommand,
                    "encResponse" to encryptedResponse,
                    "actionType" to item.actionType,
                    "isVoice" to item.isVoice
                )
                firestore.collection("users")
                    .document(user.uid)
                    .collection("commands")
                    .document(item.id)
                    .set(firestoreMap)
                    .await()
            } catch (e: Exception) {
                Log.e("JarvisRepository", "Error syncing command to Firestore", e)
            }
        }
    }

    suspend fun syncRemoteCommands(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val snapshot = firestore.collection("users")
                .document(user.uid)
                .collection("commands")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()

            val remoteItems = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val encCommand = doc.getString("encCommand") ?: ""
                val encResponse = doc.getString("encResponse") ?: ""
                val actionType = doc.getString("actionType") ?: "NONE"
                val isVoice = doc.getBoolean("isVoice") ?: false
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                CommandLogItem(
                    id = id,
                    timestamp = timestamp,
                    command = encryptionManager.decrypt(encCommand),
                    response = encryptionManager.decrypt(encResponse),
                    actionType = actionType,
                    isVoice = isVoice
                )
            }
            if (remoteItems.isNotEmpty()) {
                _localLogs.value = remoteItems
            }
            true
        } catch (e: Exception) {
            Log.e("JarvisRepository", "Failed to sync remote logs", e)
            false
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
