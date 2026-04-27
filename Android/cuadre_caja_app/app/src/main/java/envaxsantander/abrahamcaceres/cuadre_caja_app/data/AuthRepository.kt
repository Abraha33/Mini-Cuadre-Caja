package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.AppError
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.Result
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.UserSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.Ok(Unit)
        } catch (t: Throwable) {
            Result.Err(t.toAppError())
        }
    }

    /**
     * Creates Auth user and `usuarios/{uid}` profile. New users default to [defaultRol] (e.g. cajero).
     */
    suspend fun register(
        email: String,
        password: String,
        nombre: String,
        defaultRol: String = "cajero",
    ): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.Err(AppError.Unknown("Usuario sin UID"))
            val userData = mapOf(
                "nombre" to nombre,
                "rol" to defaultRol,
                "activo" to true,
            )
            db.collection(USUARIOS).document(uid).set(userData).await()
            Result.Ok(Unit)
        } catch (t: Throwable) {
            Result.Err(t.toAppError())
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Loads Firestore profile for [uid]. Retries briefly when the document is not yet visible
     * (e.g. right after registration).
     */
    suspend fun fetchUserSession(uid: String, email: String?): Result<UserSession> {
        repeat(PROFILE_READ_RETRIES) { attempt ->
            try {
                val snap = db.collection(USUARIOS).document(uid).get().await()
                if (snap.exists()) {
                    val nombre = snap.getString("nombre")?.trim().orEmpty().ifBlank { "Usuario" }
                    val rol = snap.getString("rol")?.trim().orEmpty().ifBlank { "cajero" }
                    val activo = snap.getBoolean("activo") ?: true
                    return Result.Ok(
                        UserSession(
                            uid = uid,
                            email = email,
                            nombre = nombre,
                            rol = rol,
                            activo = activo,
                        ),
                    )
                }
                if (attempt < PROFILE_READ_RETRIES - 1) {
                    delay(PROFILE_RETRY_DELAY_MS)
                }
            } catch (t: Throwable) {
                return Result.Err(t.toAppError())
            }
        }
        return Result.Err(
            AppError.ValidationError("Perfil de usuario no encontrado. Contacta al administrador."),
        )
    }

    private companion object {
        const val USUARIOS = "usuarios"
        const val PROFILE_READ_RETRIES = 5
        const val PROFILE_RETRY_DELAY_MS = 200L
    }
}
