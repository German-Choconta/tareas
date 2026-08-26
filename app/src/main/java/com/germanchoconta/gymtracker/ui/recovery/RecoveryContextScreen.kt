package com.germanchoconta.gymtracker.ui.recovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.contracts.HealthPermissionsRequestContract
import com.germanchoconta.gymtracker.PRIVACY_POLICY_URL
import com.germanchoconta.gymtracker.data.health.RecoveryAvailability
import com.germanchoconta.gymtracker.data.health.RecoveryContext
import com.germanchoconta.gymtracker.data.health.RecoveryHrvRmssd
import com.germanchoconta.gymtracker.data.health.RecoveryPermission
import com.germanchoconta.gymtracker.data.health.RecoveryRestingHeartRate
import com.germanchoconta.gymtracker.data.health.RecoverySleepSession
import java.time.Duration
import java.time.format.DateTimeFormatter

@Composable
fun RecoveryContextRoute(
    state: RecoveryUiState,
    viewModel: RecoveryContextViewModel,
    onBack: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = HealthPermissionsRequestContract(),
    ) {
        viewModel.onPermissionResult()
    }
    RecoveryContextScreen(
        state = state,
        onBack = onBack,
        onRequestPermissions = { permissionLauncher.launch(viewModel.requestedPermissionStrings) },
        onRefresh = viewModel::refresh,
        onDisconnect = viewModel::disconnect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecoveryContextScreen(
    state: RecoveryUiState,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contexto de recuperación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Health Connect es opcional",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "GymTracker lee bajo demanda sueño, frecuencia cardíaca en reposo y HRV (RMSSD) para dar contexto. No es un diagnóstico ni una instrucción para cambiar tus cargas.",
                    )
                    Text(
                        "El logger, PREVIOUS + TARGET + TODAY, Historial, Progreso y Backup siguen funcionando sin permisos de salud.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            when (state.availability) {
                null -> item(key = "checking") { LoadingRow("Comprobando Health Connect…") }
                RecoveryAvailability.UNAVAILABLE -> item(key = "unavailable") {
                    StateCard(
                        title = "Health Connect no está disponible",
                        body = "Este dispositivo no ofrece actualmente un proveedor compatible. GymTracker sigue funcionando normalmente.",
                    )
                }
                RecoveryAvailability.PROVIDER_UPDATE_REQUIRED -> item(key = "provider-update") {
                    StateCard(
                        title = "Health Connect necesita instalarse o actualizarse",
                        body = "Actualiza el proveedor de Health Connect desde Android/Google Play y vuelve a esta pantalla. No se bloquea ninguna función del logger.",
                    )
                }
                RecoveryAvailability.AVAILABLE -> {
                    item(key = "permissions") {
                        PermissionCard(
                            state = state,
                            onRequestPermissions = onRequestPermissions,
                            onRefresh = onRefresh,
                            onDisconnect = onDisconnect,
                        )
                    }

                    if (state.permissionChanged) {
                        item(key = "permission-changed") {
                            StateCard(
                                title = "Los permisos cambiaron",
                                body = "Health Connect cambió los permisos durante la lectura. El contexto anterior se descartó; revisa los permisos o vuelve a intentar.",
                            )
                        }
                    }

                    if (state.error) {
                        item(key = "error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StateCard(
                                    title = "No se pudo leer Health Connect",
                                    body = "No se modificó ningún workout ni dato de GymTracker. Puedes intentar la lectura de nuevo.",
                                )
                                Button(
                                    onClick = onRefresh,
                                    enabled = !state.loading,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    if (state.loading) {
                        item(key = "loading") { LoadingRow("Leyendo contexto de recuperación…") }
                    } else if (state.hasAnyPermission && !state.error) {
                        val context = state.context
                        if (context == null || context.isEmpty) {
                            item(key = "empty") {
                                StateCard(
                                    title = "No hay datos para hoy",
                                    body = "Los permisos disponibles no devolvieron registros para el día de recuperación actual. Tener permisos y no tener datos son estados distintos.",
                                )
                            }
                        } else {
                            item(key = "day-rule") {
                                Text(
                                    "Día ${context.day}: el sueño se asigna al día en que termina; las métricas instantáneas usan la zona horaria local actual. Fuentes distintas permanecen separadas.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            recoveryContextItems(context)
                        }
                    }
                }
            }

            item(key = "privacy-bottom") {
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "PR9 no guarda estos registros en Room, no los incluye en backup/CSV, no los escribe en Health Connect y no solicita acceso en segundo plano ni historial ampliado.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Política de privacidad")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recoveryContextItems(context: RecoveryContext) {
    if (context.sleepSessions.isNotEmpty()) {
        item(key = "sleep-title") { Text("Sueño", style = MaterialTheme.typography.titleLarge) }
        context.sleepSessions.forEachIndexed { index, session ->
            item(key = "sleep-${session.sourcePackage}-$index-${session.endTime}") {
                SleepCard(session, context)
            }
        }
    }

    if (context.restingHeartRates.isNotEmpty()) {
        item(key = "rhr-title") { Text("Frecuencia cardíaca en reposo", style = MaterialTheme.typography.titleLarge) }
        context.restingHeartRates.forEach { record ->
            item(key = "rhr-${record.sourcePackage}") { RestingHeartRateCard(record, context) }
        }
    }

    if (context.hrvRmssd.isNotEmpty()) {
        item(key = "hrv-title") { Text("HRV (RMSSD)", style = MaterialTheme.typography.titleLarge) }
        context.hrvRmssd.forEach { record ->
            item(key = "hrv-${record.sourcePackage}") { HrvCard(record, context) }
        }
    }
}

@Composable
private fun PermissionCard(
    state: RecoveryUiState,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val permissionText = when {
                !state.hasAnyPermission -> "Sin permisos de Health Connect."
                state.allPermissionsGranted -> "Los tres permisos de lectura están concedidos."
                else -> "Permisos parciales: ${state.grantedPermissions.size} de ${RecoveryPermission.entries.size}."
            }
            Text("Permisos", style = MaterialTheme.typography.titleMedium)
            Text(permissionText)
            if (!state.allPermissionsGranted) {
                val missing = RecoveryPermission.entries.filterNot { it in state.grantedPermissions }
                Text(
                    "Faltan: ${missing.joinToString { permissionLabel(it) }}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onRequestPermissions,
                    enabled = !state.loading,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(if (state.hasAnyPermission) "Revisar permisos" else "Conectar Health Connect")
                }
            }
            if (state.hasAnyPermission) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !state.loading,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Actualizar")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !state.loading,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Desconectar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepCard(session: RecoverySleepSession, context: RecoveryContext) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(formatDuration(session.duration), style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatTime(session.startTime, context)}–${formatTime(session.endTime, context)} · Origen: ${sourceLabel(session.sourcePackage)}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (session.stages.hasKnownStages) {
                val stages = buildList {
                    if (!session.stages.light.isZero) add("Ligero ${formatDuration(session.stages.light)}")
                    if (!session.stages.deep.isZero) add("Profundo ${formatDuration(session.stages.deep)}")
                    if (!session.stages.rem.isZero) add("REM ${formatDuration(session.stages.rem)}")
                    if (!session.stages.sleepingUnspecified.isZero) add("Sueño sin etapa ${formatDuration(session.stages.sleepingUnspecified)}")
                    if (!session.stages.awake.isZero) add("Despierto ${formatDuration(session.stages.awake)}")
                }
                Text(stages.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Sin etapas disponibles.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RestingHeartRateCard(record: RecoveryRestingHeartRate, context: RecoveryContext) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${record.beatsPerMinute} bpm", style = MaterialTheme.typography.titleMedium)
            Text(
                "Último valor del origen hoy · ${formatTime(record.time, context)} · ${sourceLabel(record.sourcePackage)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HrvCard(record: RecoveryHrvRmssd, context: RecoveryContext) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(String.format(LocalLocale.current.platformLocale, "%.1f ms", record.milliseconds), style = MaterialTheme.typography.titleMedium)
            Text(
                "RMSSD; no se mezcla con SDNN u otras métricas · ${formatTime(record.time, context)} · ${sourceLabel(record.sourcePackage)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StateCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}

@Composable
private fun LoadingRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}

private fun permissionLabel(permission: RecoveryPermission): String = when (permission) {
    RecoveryPermission.SLEEP -> "sueño"
    RecoveryPermission.RESTING_HEART_RATE -> "frecuencia en reposo"
    RecoveryPermission.HRV_RMSSD -> "HRV RMSSD"
}

private fun formatTime(time: java.time.Instant, context: RecoveryContext): String =
    DateTimeFormatter.ofPattern("HH:mm").format(time.atZone(context.zoneId))

private fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        else -> "${minutes} min"
    }
}

private fun sourceLabel(sourcePackage: String): String = sourcePackage.ifBlank { "origen desconocido" }
