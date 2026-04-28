package envaxsantander.abrahamcaceres.cuadre_caja_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.AuthFormMode
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.AuthSessionState
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    session: AuthSessionState,
) {
    val form by viewModel.form.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassword by remember { mutableStateOf(false) }
    val canOfferRegister = form.failedLoginAttempts >= 2

    LaunchedEffect(session) {
        if (session is AuthSessionState.SignedOut) {
            val msg = session.message
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(message = msg)
                viewModel.consumeSignedOutMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Mini Cuadre Caja",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (form.mode == AuthFormMode.Login) {
                    "Inicia sesión con tu correo y contraseña."
                } else {
                    "Completa los datos para crear tu cuenta."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = form.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Correo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !form.submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            OutlinedTextField(
                value = form.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !form.submitting,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(
                        onClick = { showPassword = !showPassword },
                        enabled = !form.submitting,
                    ) {
                        Text(if (showPassword) "Ocultar" else "Mostrar")
                    }
                },
            )

            if (form.mode == AuthFormMode.Register) {
                OutlinedTextField(
                    value = form.nombre,
                    onValueChange = viewModel::onNombreChange,
                    label = { Text("Nombre completo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !form.submitting,
                )
                OutlinedTextField(
                    value = form.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = { Text("Confirmar contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !form.submitting,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }

            form.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { viewModel.submit() },
                enabled = !form.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (form.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        when (form.mode) {
                            AuthFormMode.Login -> "Entrar"
                            AuthFormMode.Register -> "Crear cuenta"
                        },
                    )
                }
            }

            if (canOfferRegister) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (form.mode == AuthFormMode.Login) {
                    Text(
                        text = "Tras 2 intentos fallidos de inicio de sesión puedes crear una cuenta nueva.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.setMode(AuthFormMode.Register) },
                        enabled = !form.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Crear cuenta (registro)")
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.setMode(AuthFormMode.Login) },
                        enabled = !form.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Volver a iniciar sesión")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
