package envaxsantander.abrahamcaceres.cuadre_caja_app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel.CierreCajaViewModel

@Composable
fun CierreCajaScreen(
    modifier: Modifier = Modifier,
    viewModel: CierreCajaViewModel = viewModel(),
    onSignOut: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Algunos proveedores no exponen permiso persistente; la lectura puede seguir funcionando.
        }
        viewModel.uploadPdf(uri, context.contentResolver)
    }

    val bloqueaPorCierre = state.cargandoCierre
    val bloqueaSubidaZ = state.subiendoInformeZ
    val muestraProgreso = state.subiendoInformeZ || state.cargandoCierre

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Cierre de Caja", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onSignOut, enabled = !bloqueaPorCierre && !bloqueaSubidaZ) {
                        Text("Cerrar sesión")
                    }
                }
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    openPdfLauncher.launch(arrayOf("application/pdf"))
                                },
                                enabled = state.puedeOperar && !bloqueaSubidaZ,
                            ) {
                                Text("Subir Informe Z")
                            }
                            if (state.estadoZ == "ERROR" && !bloqueaSubidaZ) {
                                TextButton(
                                    onClick = {
                                        openPdfLauncher.launch(arrayOf("application/pdf"))
                                    },
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text("Estado Z: ${state.estadoZ}")

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
                            enabled = state.puedeOperar && !bloqueaPorCierre,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.billetes,
                            onValueChange = { viewModel.onBilletesChange(it) },
                            label = { Text("Billetes") },
                            enabled = state.puedeOperar && !bloqueaPorCierre,
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

                if (state.cierreCompletado) {
                    Spacer(Modifier.height(16.dp))
                    Text("Cierre guardado: OK", style = MaterialTheme.typography.titleSmall)
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

                Spacer(Modifier.height(80.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (muestraProgreso) {
                    if (state.subiendoInformeZ && state.progresoSubidaInformeZ > 0f) {
                        LinearProgressIndicator(
                            progress = { state.progresoSubidaInformeZ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                        Text(
                            "Subiendo informe Z… ${(state.progresoSubidaInformeZ * 100f).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                        Text(
                            text = if (state.subiendoInformeZ) {
                                "Subiendo informe Z…"
                            } else {
                                "Cerrando caja…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.cierreError?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Visible,
                    )
                }
                Button(
                    onClick = { viewModel.guardarCierre() },
                    enabled = state.canClose && !bloqueaSubidaZ,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("CERRAR CAJA")
                }
            }
        }
    }
}
