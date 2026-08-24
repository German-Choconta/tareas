package com.germanchoconta.gymtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.domain.ProgressionEngine
import com.germanchoconta.gymtracker.domain.ProgressionTarget
import com.germanchoconta.gymtracker.domain.SetPerformance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymTrackerApp() {
    val previous = listOf(
        SetPerformance(40.0, 10, 2),
        SetPerformance(40.0, 9, 2),
        SetPerformance(40.0, 8, 1),
    )
    val target = ProgressionTarget(minReps = 8, maxReps = 12, targetRir = 1, loadIncrementKg = 2.5)
    val recommendation = ProgressionEngine.recommend(40.0, previous, target)

    Scaffold(
        topBar = { TopAppBar(title = { Text("GymTracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = "Añadir ejercicio")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Entrenamiento de hoy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("V1: registrar → comparar → progresar", style = MaterialTheme.typography.bodyMedium)
            }

            item {
                ExerciseCard(
                    title = "Press inclinado",
                    previous = previous,
                    suggestedLoad = recommendation.suggestedLoadKg,
                    reason = recommendation.reason,
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    title: String,
    previous: List<SetPerformance>,
    suggestedLoad: Double,
    reason: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Última sesión", style = MaterialTheme.typography.labelLarge)
            previous.forEachIndexed { index, set ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Serie ${index + 1}")
                    Text("${set.loadKg} kg × ${set.reps} · RIR ${set.rir ?: "—"}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Sugerencia: ${suggestedLoad} kg", style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Empezar ejercicio")
            }
        }
    }
}
