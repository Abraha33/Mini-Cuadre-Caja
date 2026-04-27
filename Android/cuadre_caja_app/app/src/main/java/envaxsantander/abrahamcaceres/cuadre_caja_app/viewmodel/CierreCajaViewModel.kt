package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.CierreRepository
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.MovimientoCaja
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class CierreCajaViewModel(
    private val repo: CierreRepository = CierreRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {
    private val tag = "CierreCaja"

    private val _state = MutableStateFlow(CierreState())
    val state = _state.asStateFlow()

    /** Misma región que Cloud Functions (`crearCierre`, `exportarCierre`). */
    private val functionsUsEast1: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("us-east1")
    }

    private var turnoJob: Job? = null

    init {
        refreshAuthAndStart()
    }

    private fun refreshAuthAndStart() {
        val uid = auth.currentUser?.uid
        _state.update { it.copy(usuarioId = uid) }
        Log.d(tag, "auth: currentUser uid=${uid ?: "null"}")

        turnoJob?.cancel()
        turnoJob = null

        if (uid.isNullOrBlank()) {
            _state.update {
                it.copy(
                    cierreError = "No autenticado: inicia sesión para continuar.",
                    puedeOperar = false,
                    turnoBloqueoMsg = "Sin sesión",
                    turnoId = null,
                    turnoEstado = null,
                    rol = null,
                )
            }
            recalcular()
            return
        }

        viewModelScope.launch {
            cargarRol(uid)
            ensureTurnoYObservar(uid)
        }
    }

    private suspend fun cargarRol(uid: String) {
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(uid)
                .get()
                .await()

            val rol = snap.getString("rol")
            _state.update { it.copy(rol = rol) }
        } catch (t: Throwable) {
            Log.e(tag, "rol: failed", t)
            _state.update { it.copy(rol = null, cierreError = "No se pudo cargar rol: ${t.message ?: t.javaClass.simpleName}") }
        }
    }

    private suspend fun ensureTurnoYObservar(uid: String) {
        val cajaId = _state.value.cajaId

        try {
            // Garantiza un turno ABIERTO para operar (MVP).
            repo.crearTurnoAbiertoSiNoExiste(cajaId = cajaId, usuarioId = uid)
        } catch (t: Throwable) {
            Log.e(tag, "turno: crearTurnoAbiertoSiNoExiste failed", t)
            _state.update {
                it.copy(
                    puedeOperar = false,
                    turnoBloqueoMsg = "No se pudo abrir turno: ${t.message ?: t.javaClass.simpleName}",
                )
            }
            recalcular()
            return
        }

        turnoJob = viewModelScope.launch {
            repo.observeTurnoActivo(cajaId = cajaId, usuarioId = uid).collectLatest { turno ->
                val abierto = turno != null && turno.estado == "ABIERTO"
                _state.update {
                    it.copy(
                        turnoId = turno?.id,
                        turnoEstado = turno?.estado,
                        puedeOperar = abierto && !it.cierreCompletado,
                        turnoBloqueoMsg = when {
                            it.cierreCompletado -> "Caja cerrada (cierre completado)."
                            turno == null -> "Sin turno ABIERTO."
                            turno.estado != "ABIERTO" -> "Turno no operable: ${turno.estado}"
                            else -> null
                        },
                    )
                }

                // Si ya no hay turno activo, deja de escuchar movimientos (evita “seguir operando” en UI).
                if (abierto) {
                    observeMovimientos(uid)
                } else {
                    movimientosJob?.cancel()
                    movimientosJob = null
                }

                recalcular()
            }
        }
    }

    private var movimientosJob: Job? = null
    private var cierreJob: Job? = null

    private fun observeMovimientos(usuarioId: String) {
        if (movimientosJob != null) return

        movimientosJob = viewModelScope.launch {
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

        val requiereAdmin = nivel == "CRITICAL" && s.rol != "admin"

        _state.update {
            val zOk = it.estadoZ == "VALIDADO" || it.estadoZ == "WARNING"
            val puedeCerrarBase =
                zOk &&
                    total > 0.0 &&
                    !it.cierreCompletado &&
                    it.puedeOperar

            val rolOk = it.rol == "admin" || it.rol == "cajero"
            val puedeCerrar = puedeCerrarBase && rolOk && !requiereAdmin

            it.copy(
                totalContado = total,
                diferencia = diferencia,
                nivel = nivel,
                canClose = puedeCerrar,
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

        if (!_state.value.puedeOperar) {
            _state.update { it.copy(estadoZ = "ERROR", cierreError = "Caja cerrada: no puedes subir PDF.") }
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
                observeCierreDoc(docId)
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

    private fun observeCierreDoc(cierreId: String) {
        cierreJob?.cancel()
        cierreJob = viewModelScope.launch {
            repo.observeCierre(cierreId).collectLatest { data ->
                val z = data?.get("z") as? Map<*, *>
                val validacion = z?.get("validacion") as? String
                val resumen = (z?.get("resumen") as? Map<*, *>) ?: emptyMap<Any?, Any?>()

                val zResumen = ZResumen(
                    ventas = (resumen["ventas"] as? Number)?.toDouble(),
                    devoluciones = (resumen["devoluciones"] as? Number)?.toDouble(),
                    neto = (resumen["neto"] as? Number)?.toDouble(),
                    diffVsIngresosTotal = (resumen["diff_vs_ingresos_total"] as? Number)?.toDouble(),
                    nivel = resumen["nivel"] as? String,
                )

                if (!validacion.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            estadoZ = validacion,
                            zResumen = zResumen,
                        )
                    }
                    recalcular()
                }
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
            "z" to mapOf(
                "pdf_url" to pdfUrl,
                "pdf_path" to storagePath,
                "validacion" to "SUBIDO",
            ),
            "created_at" to FieldValue.serverTimestamp(),
        )

        val ref = db.collection("cierres_caja").add(data).await()
        return ref.id
    }

    fun guardarCierre() {
        viewModelScope.launch {
            if (auth.currentUser?.uid == null) return@launch

            val s = _state.value

            if (!s.puedeOperar) {
                _state.update { it.copy(cierreError = "No se puede cerrar: caja cerrada / sin turno abierto.") }
                return@launch
            }

            val zOk = s.estadoZ == "VALIDADO" || s.estadoZ == "WARNING"
            if (!(zOk && s.totalContado > 0.0)) {
                _state.update { it.copy(cierreError = "No se puede cerrar: falta Z o conteo.") }
                return@launch
            }

            val turnoId = s.turnoId
            if (turnoId.isNullOrBlank()) {
                _state.update { it.copy(cierreError = "No se puede cerrar: falta turnoId.") }
                return@launch
            }

            if (s.nivel == "CRITICAL" && s.rol != "admin") {
                _state.update { it.copy(cierreError = "Diferencia CRITICAL: requiere admin para cerrar.") }
                return@launch
            }

            _state.update { it.copy(loading = true, cierreError = null) }

            val cierreDocId = s.cierreDocId
            if (cierreDocId.isNullOrBlank()) {
                _state.update {
                    it.copy(loading = false, cierreError = "Falta id de cierre (sube el PDF del informe Z primero).")
                }
                return@launch
            }

            try {
                val payload = hashMapOf(
                    "turnoId" to turnoId,
                    "cajaId" to s.cajaId,
                    "cierreDocId" to cierreDocId,
                    "conteo" to mapOf(
                        "monedas" to (s.monedas.toDoubleOrNull() ?: 0.0),
                        "billetes" to (s.billetes.toDoubleOrNull() ?: 0.0),
                    ),
                )

                val result = functionsUsEast1
                    .getHttpsCallable("crearCierre")
                    .call(payload)
                    .await()
                    .getData() as? Map<*, *>

                val diferenciaSrv = (result?.get("diferencia") as? Number)?.toDouble() ?: s.diferencia
                val nivelSrv = result?.get("nivel") as? String ?: s.nivel
                val resumenMap = result?.get("resumen") as? Map<*, *>
                val ingSrv = (resumenMap?.get("ingresos_efectivo") as? Number)?.toDouble()
                val egrSrv = (resumenMap?.get("egresos_efectivo") as? Number)?.toDouble()
                val saldoSrv = (resumenMap?.get("saldo_esperado") as? Number)?.toDouble()
                val transSrv = (resumenMap?.get("transferencias") as? Number)?.toDouble()

                _state.update {
                    it.copy(
                        loading = false,
                        cierreCompletado = true,
                        canClose = false,
                        puedeOperar = false,
                        turnoEstado = "CERRADO",
                        turnoBloqueoMsg = "Caja cerrada (turno cerrado).",
                        diferencia = diferenciaSrv,
                        nivel = nivelSrv,
                        ingresos = ingSrv ?: it.ingresos,
                        egresos = egrSrv ?: it.egresos,
                        saldoEsperado = saldoSrv ?: it.saldoEsperado,
                        transferencias = transSrv ?: it.transferencias,
                    )
                }
                recalcular()
            } catch (t: FirebaseFunctionsException) {
                Log.e(tag, "guardarCierre: functions", t)
                _state.update {
                    it.copy(
                        loading = false,
                        cierreError = "${t.code}: ${t.message ?: t.details}",
                    )
                }
            } catch (t: Throwable) {
                Log.e(tag, "guardarCierre: failed", t)
                _state.update {
                    it.copy(
                        loading = false,
                        cierreError = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun exportarCierre() {
        val cid = _state.value.cierreDocId
        if (cid.isNullOrBlank()) {
            _state.update { it.copy(exportSheetsError = "Sin id de cierre (sube PDF y cierra primero).") }
            return
        }
        if (!_state.value.cierreCompletado) {
            _state.update { it.copy(exportSheetsError = "Completa el cierre antes de exportar.") }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(exportSheetsLoading = true, exportSheetsError = null, exportSheetsOk = false)
            }
            try {
                functionsUsEast1
                    .getHttpsCallable("exportarCierre")
                    .call(hashMapOf("cierreId" to cid))
                    .await()
                Log.d(tag, "exportarCierre: ok")
                _state.update {
                    it.copy(exportSheetsLoading = false, exportSheetsOk = true, exportSheetsError = null)
                }
            } catch (t: FirebaseFunctionsException) {
                Log.e(tag, "exportarCierre: functions", t)
                _state.update {
                    it.copy(
                        exportSheetsLoading = false,
                        exportSheetsError = "${t.code}: ${t.message ?: t.details}",
                    )
                }
            } catch (t: Throwable) {
                Log.e(tag, "exportarCierre: failed", t)
                _state.update {
                    it.copy(
                        exportSheetsLoading = false,
                        exportSheetsError = t.message ?: t.javaClass.simpleName,
                    )
                }
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
