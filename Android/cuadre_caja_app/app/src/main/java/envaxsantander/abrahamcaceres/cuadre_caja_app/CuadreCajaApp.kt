package envaxsantander.abrahamcaceres.cuadre_caja_app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class CuadreCajaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Offline-first: persistence cache on device.
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }
}

@Composable
fun CierreCajaScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Tu contenido aquí (botones, campos de texto, etc.)
    }
}