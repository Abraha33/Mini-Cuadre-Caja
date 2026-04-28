package envaxsantander.abrahamcaceres.cuadre_caja_app.domain

/**
 * Authenticated user snapshot (Auth + Firestore profile).
 */
data class UserSession(
    val uid: String,
    val email: String?,
    val nombre: String,
    val rol: String,
    val activo: Boolean,
)
