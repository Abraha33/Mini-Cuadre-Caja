package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import com.google.firebase.Timestamp

data class CierreState(
    val cajaId: String = "caja_1",
    val usuarioId: String? = null,
    val rol: String? = null,

    val turnoId: String? = null,
    val turnoEstado: String? = null,
    val puedeOperar: Boolean = false,
    val turnoBloqueoMsg: String? = null,

    val ingresos: Double = 0.0,
    val egresos: Double = 0.0,
    val saldoEsperado: Double = 0.0,
    val transferencias: Double = 0.0,

    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,

    val monedas: String = "",
    val billetes: String = "",
    val totalContado: Double = 0.0,

    /** Resultado oficial (servidor). No calcular en cliente. */
    val diferenciaOficial: Double? = null,
    /** Resultado oficial (servidor). No calcular en cliente. */
    val nivelOficial: String? = null,

    val estadoZ: String = "PENDIENTE",
    val zResumen: ZResumen? = null,
    val zPdfUrl: String? = null,
    val zPdfPath: String? = null,
    val cierreDocId: String? = null,

    /** Cierre oficial (HTTP / callable), no confundir con subida del PDF Z. */
    val cargandoCierre: Boolean = false,
    val subiendoInformeZ: Boolean = false,
    /** 0f..1f mientras sube; 0f si no hay progreso aún. */
    val progresoSubidaInformeZ: Float = 0f,
    val canClose: Boolean = false,
    val cierreCompletado: Boolean = false,
    val cierreError: String? = null,

    val exportSheetsLoading: Boolean = false,
    val exportSheetsError: String? = null,
    val exportSheetsOk: Boolean = false,
)

data class ZResumen(
    val ventas: Double? = null,
    val devoluciones: Double? = null,
    val neto: Double? = null,
    val diffVsIngresosTotal: Double? = null,
    val nivel: String? = null,
)

