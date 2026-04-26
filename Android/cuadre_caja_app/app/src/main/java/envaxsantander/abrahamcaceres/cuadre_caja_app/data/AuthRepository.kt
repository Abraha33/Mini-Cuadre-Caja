package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(
        email: String,
        password: String,
        nombre: String,
        rol: String = "cajero",
    ) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Usuario sin UID")

        val userData = mapOf(
            "nombre" to nombre,
            "rol" to rol,
            "activo" to true,
        )

        db.collection("usuarios").document(uid).set(userData).await()
    }
}
