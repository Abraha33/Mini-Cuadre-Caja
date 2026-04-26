package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.CierreRepository
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.MovimientoCaja
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class CierreCajaViewModel(
    private val repo: CierreRepository = CierreRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {
    private val tag = "CierreCaja"

    private val _state = MutableStateFlow(CierreState())
    val state = _state.asStateFlow()

    init {
        refreshAuthAndStart()
    }

    private fun refreshAuthAndStart() {
        val uid = auth.currentUser?.uid
        _state.update { it.copy(usuarioId = uid) }
        Log.d(tag, "auth: currentUser uid=${uid ?: "null"}")

        if (uid.isNullOrBlank()) {
            _state.update { it.copy(cierreError = "No autenticado: inicia sesión para continuar.") }
            recalcular()
            return
        }

        observeMovimientos()
        recalcular()
    }

    private fun observeMovimientos() {
        val usuarioId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repo.observeMovimientos(usuarioId).collect { movimientos ->
                val resumen = calcularResumen(movimientos)
                val rango = calcularRangoFechas(movimientos)
                _state.update {
                    it.copy(
                        ingresos = resumen.ingresos,
                        egresos = resumen.egresos,
                        saldoEsperado = resumen.saldoEsperado,
                        transferencias = resumen.transferencias,
                        fechaInicio = rango.inicio,
                        fechaFin = rango.fin,
                    )
                }
                recalcular()
            }
        }
    }

    fun onMonedasChange(value: String) {
        _state.update { it.copy(monedas = value) }
        recalcular()
    }

    fun onBilletesChange(value: String) {
        _state.update { it.copy(billetes = value) }
        recalcular()
    }

    private fun recalcular() {
        val s = _state.value

        val total = (s.monedas.toDoubleOrNull() ?: 0.0) +
            (s.billetes.toDoubleOrNull() ?: 0.0)

        val diferencia = total - s.saldoEsperado

        val nivel = when {
            abs(diferencia) <= 5000 -> "LOW"
            abs(diferencia) <= 20000 -> "MEDIUM"
            else -> "CRITICAL"
        }

        _state.update {
            val canClose =
                it.estadoZ != "PENDIENTE" &&
                    it.estadoZ != "ERROR" &&
                    total > 0.0 &&
                    !it.cierreCompletado
            it.copy(
                totalContado = total,
                diferencia = diferencia,
                nivel = nivel,
                canClose = canClose,
            )
        }
    }

    fun uploadPdf(uri: Uri) {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _state.update { it.copy(estadoZ = "ERROR", cierreError = "No autenticado: inicia sesión para subir PDF.") }
            recalcular()
            return
        }
        val storage = FirebaseStorage.getInstance()
        val db = FirebaseFirestore.getInstance()

        val fileName = "cierres/${userId}_${System.currentTimeMillis()}.pdf"
        val ref = storage.reference.child(fileName)

        _state.update { it.copy(loading = true, estadoZ = "SUBIENDO", cierreError = null) }

        viewModelScope.launch {
            try {
                Log.d(tag, "uploadPdf: putFile uri=$uri path=$fileName")
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()
                Log.d(tag, "uploadPdf: uploaded url=$url")

                val docId = guardarPdfEnFirestore(
                    db = db,
                    userId = userId,
                    pdfUrl = url,
                    storagePath = fileName,
                )
                Log.d(tag, "uploadPdf: firestore docId=$docId")

                _state.update {
                    it.copy(
                        estadoZ = "SUBIDO",
                        zPdfUrl = url,
                        zPdfPath = fileName,
                        cierreDocId = docId,
                        loading = false,
                    )
                }
                recalcular()
            } catch (t: Throwable) {
                Log.e(tag, "uploadPdf: failed", t)
                _state.update {
                    it.copy(
                        estadoZ = "ERROR",
                        loading = false,
                        cierreError = (t.message ?: t.javaClass.simpleName),
                    )
                }
                recalcular()
            }
        }
    }

    private suspend fun guardarPdfEnFirestore(
        db: FirebaseFirestore,
        userId: String,
        pdfUrl: String,
        storagePath: String,
    ): String {
        val data = mapOf(
            "usuario_id" to userId,
            "z_pdf_url" to pdfUrl,
            "z_pdf_path" to storagePath,
            "estado" to "PDF_CARGADO",
            "created_at" to FieldValue.serverTimestamp(),
        )

        val ref = db.collection("cierres_caja").add(data).await()
        return ref.id
    }

    fun guardarCierre() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            val db = FirebaseFirestore.getInstance()

            val s = _state.value

            if (!(s.estadoZ != "PENDIENTE" && s.estadoZ != "ERROR" && s.totalContado > 0.0)) {
                _state.update { it.copy(cierreError = "No se puede cerrar: falta Z o conteo.") }
                return@launch
            }

            _state.update { it.copy(loading = true, cierreError = null) }

            try {
                val fechaInicio = s.fechaInicio ?: Timestamp.now()
                val fechaFin = s.fechaFin ?: Timestamp.now()

                val cierre = hashMapOf(
                    "caja_id" to s.cajaId,
                    "usuario_id" to userId,
                    "fecha_inicio" to fechaInicio,
                    "fecha_fin" to fechaFin,
                    "resumen" to mapOf(
                        "ingresos_efectivo" to s.ingresos,
                        "egresos_efectivo" to s.egresos,
                        "saldo_esperado" to s.saldoEsperado,
                        "transferencias" to s.transferencias,
                    ),
                    "conteo" to mapOf(
                        "monedas" to (s.monedas.toDoubleOrNull() ?: 0.0),
                        "billetes" to (s.billetes.toDoubleOrNull() ?: 0.0),
                        "total" to s.totalContado,
                    ),
                    "diferencia" to s.diferencia,
                    "nivel" to s.nivel,
                    "z" to mapOf(
                        "pdf_url" to s.zPdfUrl,
                        "pdf_path" to s.zPdfPath,
                        "validacion" to s.estadoZ,
                    ),
                    "estado" to "COMPLETADO",
                    "created_at" to FieldValue.serverTimestamp(),
                )

                val cierreRef = s.cierreDocId?.let { db.collection("cierres_caja").document(it) }
                    ?: db.collection("cierres_caja").document()

                cierreRef.set(cierre, SetOptions.merge()).await()

                db.collection("logs").add(
                    mapOf(
                        "modulo" to "cierre_caja",
                        "accion" to "CREATE",
                        "referencia_id" to cierreRef.id,
                        "usuario_id" to userId,
                        "timestamp" to FieldValue.serverTimestamp(),
                    ),
                ).await()

                _state.update {
                    it.copy(
                        loading = false,
                        cierreCompletado = true,
                        canClose = false,
                    )
                }
            } catch (_: Throwable) {
                _state.update { it.copy(loading = false, cierreError = "Error guardando cierre", estadoZ = it.estadoZ) }
            }
        }
    }

    private data class Resumen(
        val ingresos: Double,
        val egresos: Double,
        val transferencias: Double,
        val saldoEsperado: Double,
    )

    private data class RangoFechas(
        val inicio: Timestamp?,
        val fin: Timestamp?,
    )

    private fun calcularResumen(movimientos: List<MovimientoCaja>): Resumen {
        var ingresos = 0.0
        var egresos = 0.0
        var transferencias = 0.0

        movimientos.forEach { m ->
            val isEfectivo = m.metodo == "efectivo"
            if (isEfectivo) {
                if (m.tipo == "ingreso") ingresos += m.monto else egresos += m.monto
            } else {
                transferencias += m.monto
            }
        }

        val saldo = ingresos - egresos
        return Resumen(
            ingresos = ingresos,
            egresos = egresos,
            transferencias = transferencias,
            saldoEsperado = saldo,
        )
    }

    private fun calcularRangoFechas(movimientos: List<MovimientoCaja>): RangoFechas {
        val times = movimientos.mapNotNull { it.createdAt }.sortedBy { it.seconds }
        if (times.isEmpty()) return RangoFechas(inicio = null, fin = null)
        return RangoFechas(inicio = times.first(), fin = times.last())
    }
}

