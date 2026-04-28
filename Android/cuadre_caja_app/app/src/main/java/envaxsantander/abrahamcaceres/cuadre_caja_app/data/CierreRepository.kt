package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CierreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val remote: CierreRemoteDataSource = CierreRemoteDataSource(),
) {
    fun observeCierre(cierreId: String): Flow<Map<String, Any?>?> = callbackFlow {
        val ref = db.collection("cierres_caja").document(cierreId)
        val listener = ref.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e("FirestoreError", "Error observando cierre", error)
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.data)
        }
        awaitClose { listener.remove() }
    }

    suspend fun crearTurnoAbiertoSiNoExiste(cajaId: String, usuarioId: String): String {
        val existing = db.collection("turnos_caja")
            .whereEqualTo("usuario_id", usuarioId)
            .whereEqualTo("caja_id", cajaId)
            .whereEqualTo("estado", "ABIERTO")
            .limit(1)
            .get()
            .await()

        val doc = existing.documents.firstOrNull()
        if (doc != null) return doc.id

        val ref = db.collection("turnos_caja").document()
        val data = mapOf(
            "id" to ref.id,
            "caja_id" to cajaId,
            "usuario_id" to usuarioId,
            "estado" to "ABIERTO",
            "fecha_inicio" to FieldValue.serverTimestamp(),
            "created_at" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        return ref.id
    }

    fun observeTurnoActivo(cajaId: String, usuarioId: String): Flow<TurnoCaja?> = callbackFlow {
        val query = db.collection("turnos_caja")
            .whereEqualTo("usuario_id", usuarioId)
            .whereEqualTo("caja_id", cajaId)
            .whereEqualTo("estado", "ABIERTO")
            .limit(1)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreError", "Error observando turno activo", error)
                trySend(null)
                return@addSnapshotListener
            }

            val d = snapshot?.documents?.firstOrNull()
            if (d == null) {
                trySend(null)
                return@addSnapshotListener
            }

            trySend(
                TurnoCaja(
                    id = d.id,
                    cajaId = d.getString("caja_id") ?: "",
                    usuarioId = d.getString("usuario_id") ?: usuarioId,
                    estado = d.getString("estado") ?: "",
                    fechaInicio = d.getTimestamp("fecha_inicio"),
                    fechaFin = d.getTimestamp("fecha_fin"),
                    cierreId = d.getString("cierre_id"),
                    createdAt = d.getTimestamp("created_at"),
                ),
            )
        }

        awaitClose { listener.remove() }
    }

    fun observeMovimientos(usuarioId: String): Flow<List<MovimientoCaja>> = callbackFlow {
        // Asegúrate de que los nombres de los campos coincidan EXACTAMENTE con tu consola
        // Sin orderBy en servidor: evita índice compuesto usuario_id + created_at (FAILED_PRECONDITION
        // si el índice aún no está en consola). Ordenamos en cliente por created_at.
        val query = db.collection("movimientos_caja")
            .whereEqualTo("usuario_id", usuarioId)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Es mejor imprimir el error para debuggear el tema de los índices
                android.util.Log.e("FirestoreError", "Error observando movimientos", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            val items = snapshot?.documents?.mapNotNull { d ->
                // Usamos toObject si tu data class MovimientoCaja tiene un constructor vacío
                // o mapeo manual como ya lo estás haciendo:
                try {
                    MovimientoCaja(
                        tipo = d.getString("tipo") ?: "",
                        monto = d.getDouble("monto") ?: 0.0,
                        metodo = d.getString("metodo") ?: "",
                        usuarioId = d.getString("usuario_id") ?: usuarioId,
                        cajaId = d.getString("caja_id") ?: "",
                        turnoId = d.getString("turno_id") ?: "",
                        createdAt = d.getTimestamp("created_at"),
                    )
                } catch (e: Exception) {
                    null // Salta documentos con formato incorrecto
                }
            } ?: emptyList()

            val sorted = items.sortedWith(
                compareBy<MovimientoCaja>(
                    { it.createdAt?.seconds ?: Long.MIN_VALUE },
                    { it.createdAt?.nanoseconds ?: 0 },
                ),
            )

            trySend(sorted)
        }

        // Importante: Libera el listener cuando el Flow ya no se use
        awaitClose { listener.remove() }
    }

    suspend fun crearCierreOficial(
        turnoId: String,
        cajaId: String,
        cierreDocId: String,
        monedas: Double,
        billetes: Double,
    ) = remote.crearCierre(
        turnoId = turnoId,
        cajaId = cajaId,
        cierreDocId = cierreDocId,
        monedas = monedas,
        billetes = billetes,
    )

    suspend fun exportarCierreOficial(cierreId: String) = remote.exportarCierre(cierreId)

    /** Crea documento `cierres_caja` tras subir el PDF al bucket (validación la hace el backend). */
    suspend fun registrarPdfCierreInicial(
        usuarioId: String,
        pdfUrl: String,
        storagePath: String,
    ): String {
        val data = mapOf(
            "usuario_id" to usuarioId,
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
}
