package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.Timestamp

data class MovimientoCaja(
    val tipo: String = "",
    val monto: Double = 0.0,
    val metodo: String = "",
    val usuarioId: String = "",
    val cajaId: String = "",
    val turnoId: String = "",
    val createdAt: Timestamp? = null,
)

