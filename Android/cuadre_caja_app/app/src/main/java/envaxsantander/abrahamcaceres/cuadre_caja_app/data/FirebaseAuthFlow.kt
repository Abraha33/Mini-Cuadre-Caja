package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Cold flow of auth user; emits [FirebaseAuth.getCurrentUser] immediately and on every auth change.
 */
fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    trySend(currentUser)
    awaitClose { removeAuthStateListener(listener) }
}
