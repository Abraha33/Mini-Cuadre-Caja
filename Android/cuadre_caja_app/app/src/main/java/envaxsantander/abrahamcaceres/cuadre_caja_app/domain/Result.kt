package envaxsantander.abrahamcaceres.cuadre_caja_app.domain

sealed class Result<out T> {
    data class Ok<T>(val data: T) : Result<T>()
    data class Err(val error: AppError) : Result<Nothing>()
}

