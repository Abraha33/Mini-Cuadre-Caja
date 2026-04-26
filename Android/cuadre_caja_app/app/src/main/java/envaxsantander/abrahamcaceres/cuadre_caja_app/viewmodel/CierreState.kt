package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import com.google.firebase.Timestamp

data class CierreState(
    val cajaId: String = "caja_1",
    val usuarioId: String? = null,
    val ingresos: Double = 0.0,
    val egresos: Double = 0.0,
    val saldoEsperado: Double = 0.0,
    val transferencias: Double = 0.0,

    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,

    val monedas: String = "",
    val billetes: String = "",
    val totalContado: Double = 0.0,

    val diferencia: Double = 0.0,
    val nivel: String = "LOW",

    val estadoZ: String = "PENDIENTE",
    val zResumen: ZResumen? = null,
    val zPdfUrl: String? = null,
    val zPdfPath: String? = null,
    val cierreDocId: String? = null,

    val loading: Boolean = false,
    val canClose: Boolean = false,
    val cierreCompletado: Boolean = false,
    val cierreError: String? = null,
)

data class ZResumen(
    val ventas: Double,
    val devoluciones: Double,
)

