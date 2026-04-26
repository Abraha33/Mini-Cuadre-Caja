package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.Timestamp

data class TurnoCaja(
    val id: String = "",
    val cajaId: String = "",
    val usuarioId: String = "",
    val estado: String = "",
    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,
    val cierreId: String? = null,
    val createdAt: Timestamp? = null,
)
