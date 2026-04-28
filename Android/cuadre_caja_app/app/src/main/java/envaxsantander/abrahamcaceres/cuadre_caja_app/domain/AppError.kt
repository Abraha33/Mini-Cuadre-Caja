package envaxsantander.abrahamcaceres.cuadre_caja_app.domain

sealed class AppError {
    object NetworkError : AppError()
    object Unauthenticated : AppError()
    object InsufficientPermissions : AppError()
    object UploadTimeout : AppError()
    data class ValidationError(val message: String) : AppError()
    data class ServerError(val message: String) : AppError()
    data class Unknown(val message: String) : AppError()
}

fun AppError.toUserMessage(): String {
    return when (this) {
        AppError.NetworkError -> "No hay conexión con el servidor. Verifica tu internet e intenta de nuevo."
        AppError.Unauthenticated -> "Debes iniciar sesión para continuar."
        AppError.InsufficientPermissions -> "No tienes permisos para ejecutar esta acción."
        AppError.UploadTimeout -> "La subida del PDF tardó demasiado. Revisa tu conexión y vuelve a intentarlo."
        is AppError.ValidationError -> this.message.ifBlank { "Datos inválidos. Revisa e intenta de nuevo." }
        is AppError.ServerError -> this.message.ifBlank { "Error del servidor. Intenta de nuevo." }
        is AppError.Unknown -> this.message.ifBlank { "Ocurrió un error inesperado." }
    }
}

