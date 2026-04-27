package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.AppError
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.Result
import kotlinx.coroutines.tasks.await

data class CrearCierreResponse(
    val cierreId: String,
    val diferencia: Double,
    val nivel: String,
    val resumen: ResumenCierreResponse?,
)

data class ResumenCierreResponse(
    val ingresosEfectivo: Double?,
    val egresosEfectivo: Double?,
    val saldoEsperado: Double?,
    val transferencias: Double?,
)

class CierreRemoteDataSource(
    private val functionsUsEast1: FirebaseFunctions = FirebaseFunctions.getInstance("us-east1"),
) {
    suspend fun crearCierre(
        turnoId: String, cajaId: String, cierreDocId: String, monedas: Double, billetes: Double
    ): Result<CrearCierreResponse> {
        return try {
            val payload = hashMapOf(
                "turnoId" to turnoId, "cajaId" to cajaId, "cierreDocId" to cierreDocId,
                "conteo" to mapOf("monedas" to monedas, "billetes" to billetes)
            )
            val raw = functionsUsEast1.getHttpsCallable("crearCierre").call(payload).await().getData() as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val ok = raw["ok"] as? Boolean
            if (ok == false) return Result.Err(AppError.ServerError("El servidor rechazó el cierre."))
        turnoId: String,
        cajaId: String,
        cierreDocId: String,
        monedas: Double,
        billetes: Double,
    ): Result<CrearCierreResponse> {
        return try {
            val payload = hashMapOf(
                "turnoId" to turnoId,
                "cajaId" to cajaId,
                "cierreDocId" to cierreDocId,
                "conteo" to mapOf(
                    "monedas" to monedas,
                    "billetes" to billetes,
                ),
            )

            val raw = functionsUsEast1
                .getHttpsCallable("crearCierre")
                .call(payload)
                .await()
                .data as? Map<*, *>
                ?: emptyMap<Any?, Any?>()

            val ok = raw["ok"] as? Boolean
            if (ok == false) {
                return Result.Err(AppError.ServerError("El servidor rechazó el cierre."))
            }

            val resumenMap = raw["resumen"] as? Map<*, *>
            val resumen = resumenMap?.let {
                ResumenCierreResponse(
                    ingresosEfectivo = (it["ingresos_efectivo"] as? Number)?.toDouble(),
                    egresosEfectivo = (it["egresos_efectivo"] as? Number)?.toDouble(),
                    saldoEsperado = (it["saldo_esperado"] as? Number)?.toDouble(),
                    transferencias = (it["transferencias"] as? Number)?.toDouble()
                )
            }
            val diferencia = (raw["diferencia"] as? Number)?.toDouble() ?: return Result.Err(AppError.ServerError("Error diferencia"))
            val nivel = raw["nivel"] as? String ?: return Result.Err(AppError.ServerError("Error nivel"))

            Result.Ok(CrearCierreResponse(raw["cierreId"] as? String ?: cierreDocId, diferencia, nivel, resumen))
        } catch (t: FirebaseFunctionsException) {
            Result.Err(mapFunctionsError(t))
        } catch (t: Throwable) {
            Result.Err(AppError.Unknown(t.message ?: "Unknown"))
                    transferencias = (it["transferencias"] as? Number)?.toDouble(),
                )
            }

            val cierreId = raw["cierreId"] as? String ?: cierreDocId
            val diferencia = (raw["diferencia"] as? Number)?.toDouble()
                ?: return Result.Err(AppError.ServerError("Respuesta incompleta (diferencia)."))
            val nivel = raw["nivel"] as? String
                ?: return Result.Err(AppError.ServerError("Respuesta incompleta (nivel)."))

            Result.Ok(
                CrearCierreResponse(
                    cierreId = cierreId,
                    diferencia = diferencia,
                    nivel = nivel,
                    resumen = resumen,
                )
            )
        } catch (t: FirebaseFunctionsException) {
            Result.Err(mapFunctionsError(t))
        } catch (t: Throwable) {
            Result.Err(AppError.Unknown(t.message ?: t.javaClass.simpleName))
        }
    }

    suspend fun exportarCierre(cierreId: String): Result<Unit> {
        return try {
            functionsUsEast1.getHttpsCallable("exportarCierre").call(hashMapOf("cierreId" to cierreId)).await()
            functionsUsEast1
                .getHttpsCallable("exportarCierre")
                .call(hashMapOf("cierreId" to cierreId))
                .await()
            Result.Ok(Unit)
        } catch (t: FirebaseFunctionsException) {
            Result.Err(mapFunctionsError(t))
        } catch (t: Throwable) {
            Result.Err(AppError.Unknown(t.message ?: "Unknown"))
            Result.Err(AppError.Unknown(t.message ?: t.javaClass.simpleName))
        }
    }

    private fun mapFunctionsError(t: FirebaseFunctionsException): AppError {
        return when (t.code) {
            FirebaseFunctionsException.Code.UNAVAILABLE -> AppError.NetworkError
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> AppError.Unauthenticated
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.InsufficientPermissions
            FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.FAILED_PRECONDITION -> AppError.ValidationError(t.message ?: "Error")
            FirebaseFunctionsException.Code.INTERNAL -> AppError.ServerError(t.message ?: "Error")
            else -> AppError.Unknown("Error")
        }
    }
}
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            -> AppError.ValidationError(t.message ?: "No se pudo completar la operación.")
            FirebaseFunctionsException.Code.INTERNAL -> AppError.ServerError(t.message ?: "Error interno.")
            else -> AppError.Unknown("${t.code}: ${t.message ?: (t.details?.toString() ?: "error")}")
        }
    }
}

