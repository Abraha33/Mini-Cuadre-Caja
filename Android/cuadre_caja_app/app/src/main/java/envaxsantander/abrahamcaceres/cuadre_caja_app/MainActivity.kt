package envaxsantander.abrahamcaceres.cuadre_caja_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.AuthScreen
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.CierreCajaScreen
import envaxsantander.abrahamcaceres.cuadre_caja_app.ui.theme.Cuadre_caja_appTheme
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.AuthSessionState
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cuadre_caja_appTheme {
                val authViewModel: AuthViewModel = viewModel()
                val session by authViewModel.session.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val s = session) {
                        AuthSessionState.Checking -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is AuthSessionState.SignedOut -> {
                            AuthScreen(
                                viewModel = authViewModel,
                                session = s,
                            )
                        }
                        is AuthSessionState.Authenticated -> {
                            CierreCajaScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                onSignOut = { authViewModel.signOut() },
                            )
                        }
                    }
                }
            }
        }
    }
}
