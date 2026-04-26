package envaxsantander.abrahamcaceres.cuadre_caja_app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class CierreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    fun observeMovimientos(usuarioId: String): Flow<List<MovimientoCaja>> = callbackFlow {
        // Asegúrate de que los nombres de los campos coincidan EXACTAMENTE con tu consola
        val query = db.collection("movimientos_caja")
            .whereEqualTo("usuario_id", usuarioId)
            .orderBy("created_at", Query.Direction.ASCENDING)

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
                        createdAt = d.getTimestamp("created_at"),
                    )
                } catch (e: Exception) {
                    null // Salta documentos con formato incorrecto
                }
            } ?: emptyList()

            trySend(items)
        }

        // Importante: Libera el listener cuando el Flow ya no se use
        awaitClose { listener.remove() }
    }
}
