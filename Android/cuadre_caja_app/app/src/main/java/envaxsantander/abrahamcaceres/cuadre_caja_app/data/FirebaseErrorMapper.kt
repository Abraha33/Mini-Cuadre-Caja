package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.AppError

internal fun Throwable.toAppError(): AppError {
    return when (this) {
        is FirebaseAuthException -> mapAuthException(this)
        is FirebaseFirestoreException -> when (this.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                AppError.InsufficientPermissions
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                AppError.NetworkError
            else -> AppError.ServerError(message ?: this.code.name)
        }
        is FirebaseNetworkException -> AppError.NetworkError
        else -> AppError.Unknown(message ?: javaClass.simpleName)
    }
}

private fun mapAuthException(e: FirebaseAuthException): AppError {
    return when (e.errorCode) {
        "ERROR_INVALID_EMAIL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        -> AppError.ValidationError("Correo o contraseña incorrectos.")

        "ERROR_USER_DISABLED" ->
            AppError.ValidationError("Esta cuenta está deshabilitada.")

        "ERROR_USER_NOT_FOUND" ->
            AppError.ValidationError("No existe una cuenta con este correo.")

        "ERROR_EMAIL_ALREADY_IN_USE" ->
            AppError.ValidationError("Este correo ya está registrado.")

        "ERROR_WEAK_PASSWORD" ->
            AppError.ValidationError("La contraseña es demasiado débil.")

        "ERROR_NETWORK_REQUEST_FAILED" ->
            AppError.NetworkError

        else -> AppError.ServerError(e.message ?: e.errorCode)
    }
}
