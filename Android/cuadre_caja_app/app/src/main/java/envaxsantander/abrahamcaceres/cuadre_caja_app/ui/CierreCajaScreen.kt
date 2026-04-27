package envaxsantander.abrahamcaceres.cuadre_caja_app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.CierreCajaViewModel

@Composable
fun CierreCajaScreen(
    modifier: Modifier = Modifier,
    viewModel: CierreCajaViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.uploadPdf(it) }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Cierre de Caja", style = MaterialTheme.typography.headlineSmall)
            state.usuarioId?.let { uid ->
                Spacer(Modifier.height(4.dp))
                Text("Usuario: $uid", style = MaterialTheme.typography.bodySmall)
            }
            state.rol?.let { rol ->
                Spacer(Modifier.height(4.dp))
                Text("Rol: $rol", style = MaterialTheme.typography.bodySmall)
            }
            state.turnoId?.let { tid ->
                Spacer(Modifier.height(4.dp))
                Text("Turno: $tid (${state.turnoEstado ?: "?"})", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            state.turnoBloqueoMsg?.let { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Estado operación", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Resumen ERP")
                    Text("Ingresos (efectivo): ${state.ingresos}")
                    Text("Egresos (efectivo): ${state.egresos}")
                    Text("Saldo esperado efectivo: ${state.saldoEsperado}")
                    Text("Transferencias (Nequi/Bancolombia): ${state.transferencias}")
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Informe Z")

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { launcher.launch("application/pdf") },
                        enabled = state.puedeOperar && !state.loading,
                    ) {
                        Text("Subir Informe Z")
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Estado Z: ${state.estadoZ}")

                    state.cierreError?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }

                    state.zResumen?.let { z ->
                        Spacer(Modifier.height(8.dp))
                        Text("Ventas Z: ${z.ventas ?: "-"}")
                        Text("Devoluciones Z: ${z.devoluciones ?: "-"}")
                        Text("Neto Z: ${z.neto ?: "-"}")
                        z.diffVsIngresosTotal?.let { diff ->
                            Text("Diff vs ERP ingresos total: $diff")
                        }
                        z.nivel?.let { lvl ->
                            Text("Nivel validación: $lvl")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Conteo físico")

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.monedas,
                        onValueChange = { viewModel.onMonedasChange(it) },
                        label = { Text("Monedas") },
                        enabled = state.puedeOperar && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.billetes,
                        onValueChange = { viewModel.onBilletesChange(it) },
                        label = { Text("Billetes") },
                        enabled = state.puedeOperar && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Total contado: ${state.totalContado}")
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val diff = state.diferenciaOficial
                    val lvl = state.nivelOficial
                    Text("Diferencia (oficial): ${diff ?: "-"}")
                    Text("Nivel (oficial): ${lvl ?: "-"}")
                    if (!state.cierreCompletado) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nota: el nivel y la diferencia oficiales solo se determinan al cerrar (servidor).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.guardarCierre() },
                enabled = state.canClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("CERRAR CAJA")
            }

            state.cierreError?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            if (state.cierreCompletado) {
                Spacer(Modifier.height(8.dp))
                Text("Cierre guardado: OK")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.exportarCierre() },
                    enabled = !state.exportSheetsLoading &&
                        state.cierreDocId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exportar a Sheets / email")
                }
                state.exportSheetsError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
                if (state.exportSheetsOk) {
                    Spacer(Modifier.height(8.dp))
                    Text("Exportación enviada (revisa tu hoja y correo).")
                }
            }

            if (state.loading) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            }
        }
    }
}

// Overload para compatibilidad con Live Edit/Apply Changes:
// evita crashes cuando Android Studio intenta invocar la firma anterior.
@Composable
fun CierreCajaScreen(viewModel: CierreCajaViewModel = viewModel()) {
    CierreCajaScreen(modifier = Modifier, viewModel = viewModel)
}

