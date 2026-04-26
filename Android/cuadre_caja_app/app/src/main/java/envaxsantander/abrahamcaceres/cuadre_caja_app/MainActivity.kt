package envaxsantander.abrahamcaceres.cuadre_caja_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.theme.Cuadre_caja_appTheme
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.CierreCajaScreen
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.AuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cuadre_caja_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val auth = remember { FirebaseAuth.getInstance() }
                    var authed by remember { mutableStateOf(auth.currentUser != null) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { fa ->
                            authed = fa.currentUser != null
                        }
                        auth.addAuthStateListener(listener)
                        onDispose { auth.removeAuthStateListener(listener) }
                    }

                    if (!authed) {
                        AuthScreen(onAuthed = { authed = true })
                    } else {
                        CierreCajaScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}