package com.germanchoconta.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.ui.theme.GymTrackerTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Privacidad de Health Connect",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Health Connect es opcional. GymTracker solo solicita lectura de sueño y etapas disponibles, frecuencia cardíaca en reposo y HRV registrada específicamente como RMSSD para mostrar contexto informativo de recuperación.",
                        )
                        Text(
                            "Estos datos no se usan para diagnosticar condiciones médicas, prescribir tratamiento ni cambiar automáticamente cargas, TARGET, PREVIOUS, rutinas o progresión.",
                        )
                        Text(
                            "PR9 no escribe datos de salud, no convierte sesiones externas en workouts, no guarda estos registros en Room, no los incluye en backup/CSV y no los envía a un backend ni a analytics. La lectura es acotada y se realiza bajo demanda mientras usas la app; no se solicita acceso en segundo plano ni historial ampliado.",
                        )
                        Text(
                            "Puedes usar completamente el logger, Historial, Progreso y Backup/Restore sin conceder permisos. Al desconectar Health Connect se revocan los permisos de GymTracker y se limpia el contexto que estaba únicamente en memoria.",
                        )
                        Text(
                            "Si varias aplicaciones aportan un mismo tipo de dato, GymTracker mantiene sus orígenes separados en lugar de inventar una deduplicación entre fuentes.",
                        )
                        Button(
                            onClick = ::finish,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}
