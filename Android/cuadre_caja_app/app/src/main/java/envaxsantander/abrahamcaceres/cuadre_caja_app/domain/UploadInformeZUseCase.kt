package envaxsantander.abrahamcaceres.cuadre_caja_app.domain

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.CierreRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resumeWithException

data class InformeZUploadOk(
    val cierreDocId: String,
    val downloadUrl: String,
    val storagePath: String,
)

/**
 * Valida PDF, sube a Storage con tarea cancelable y registra metadatos en Firestore.
 */
class UploadInformeZUseCase(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val repo: CierreRepository = CierreRepository(),
) {
    suspend operator fun invoke(
        resolver: ContentResolver,
        uri: Uri,
        userId: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit,
    ): Result<InformeZUploadOk> {
        val validation = validatePdfSelection(resolver, uri)
        if (validation != null) return Result.Err(validation)

        val fileName = "cierres/${userId}_${System.currentTimeMillis()}.pdf"
        val ref = storage.reference.child(fileName)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/pdf")
            .build()

        return try {
            withTimeout(UPLOAD_TIMEOUT_MS) {
                val task = ref.putFile(uri, metadata)
                task.awaitComplete(onProgress)
                val url = ref.downloadUrl.await().toString()
                val docId = repo.registrarPdfCierreInicial(
                    usuarioId = userId,
                    pdfUrl = url,
                    storagePath = fileName,
                )
                Result.Ok(
                    InformeZUploadOk(
                        cierreDocId = docId,
                        downloadUrl = url,
                        storagePath = fileName,
                    ),
                )
            }
        } catch (_: TimeoutCancellationException) {
            Log.e(TAG, "invoke: timeout path=$fileName")
            Result.Err(AppError.UploadTimeout)
        } catch (t: StorageException) {
            Log.e(TAG, "invoke: StorageException code=${t.errorCode} message=${t.message}", t)
            Result.Err(mapStorageException(t))
        } catch (t: Throwable) {
            Log.e(TAG, "invoke: failed path=$fileName", t)
            Result.Err(
                AppError.Unknown(
                    t.message?.takeIf { it.isNotBlank() }
                        ?: "Error al subir el informe Z. Vuelve a intentarlo.",
                ),
            )
        }
    }

    private fun validatePdfSelection(resolver: ContentResolver, uri: Uri): AppError? {
        val mime = resolver.getType(uri)?.lowercase(Locale.getDefault())
        if (!mime.isNullOrBlank()) {
            val allowedMime = mime == "application/pdf" ||
                mime == "application/x-pdf" ||
                mime == "application/octet-stream"
            if (!allowedMime) {
                return AppError.ValidationError(
                    "El archivo debe ser PDF. Tipo detectado: $mime",
                )
            }
        }
        if (!headerLooksLikePdf(resolver, uri)) {
            return AppError.ValidationError("El archivo no es un PDF válido (cabecera incorrecta).")
        }
        return null
    }

    private fun headerLooksLikePdf(resolver: ContentResolver, uri: Uri): Boolean {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(5)
                val n = input.read(buf, 0, 5)
                n >= 4 && String(buf, 0, 4, Charsets.US_ASCII) == "%PDF"
            } == true
        } catch (t: Throwable) {
            Log.w(TAG, "headerLooksLikePdf: cannot read uri=$uri", t)
            false
        }
    }

    private fun mapStorageException(t: StorageException): AppError {
        val code = t.errorCode
        return when (code) {
            StorageException.ERROR_NOT_AUTHENTICATED -> AppError.Unauthenticated
            StorageException.ERROR_NOT_AUTHORIZED -> AppError.InsufficientPermissions
            StorageException.ERROR_QUOTA_EXCEEDED ->
                AppError.ServerError("Cuota de almacenamiento excedida. Contacta al administrador.")
            // Reintentos / red (Firebase Storage Android; evitar -13040 cancelación confundida con red)
            -13030, -13031 -> AppError.NetworkError
            else ->
                AppError.Unknown(
                    "No se pudo subir el PDF (código $code). ${t.message ?: "Revisa conexión y permisos."}",
                )
        }
    }

    private companion object {
        const val TAG = "UploadInformeZ"
        const val UPLOAD_TIMEOUT_MS = 120_000L
    }
}

private suspend fun UploadTask.awaitComplete(
    onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit,
): Unit = suspendCancellableCoroutine { cont ->
    addOnProgressListener { snap ->
        val total = snap.totalByteCount
        if (total > 0L) {
            onProgress(snap.bytesTransferred, total)
        }
    }
    addOnSuccessListener {
        if (cont.isActive) {
            cont.resumeWith(kotlin.Result.success(Unit))
        }
    }
    addOnFailureListener { e ->
        if (cont.isActive) {
            cont.resumeWithException(e)
        }
    }
    cont.invokeOnCancellation {
        try {
            cancel()
        } catch (t: Throwable) {
            Log.w("UploadInformeZ", "cancel upload", t)
        }
    }
}
