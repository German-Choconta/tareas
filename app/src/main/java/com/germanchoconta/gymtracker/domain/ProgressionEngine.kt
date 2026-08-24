package com.germanchoconta.gymtracker.domain

import kotlin.math.max

object ProgressionEngine {
    /**
     * Conservative double-progression rule for working sets.
     * The engine never invents a performance target from recovery data.
     */
    fun recommend(
        currentLoadKg: Double,
        lastSession: List<SetPerformance>,
        target: ProgressionTarget,
        consecutiveUnderTargetSessions: Int = 0,
    ): ProgressionRecommendation {
        require(currentLoadKg >= 0.0)
        require(consecutiveUnderTargetSessions >= 0)

        if (lastSession.isEmpty()) {
            return ProgressionRecommendation(
                action = ProgressionAction.KEEP_LOAD,
                suggestedLoadKg = currentLoadKg,
                reason = "No hay una sesión previa comparable; repite la carga y crea una referencia.",
            )
        }

        val allAtTopOfRange = lastSession.all { set ->
            set.reps >= target.maxReps && (set.rir == null || set.rir >= target.targetRir)
        }

        if (allAtTopOfRange) {
            return ProgressionRecommendation(
                action = ProgressionAction.INCREASE_LOAD,
                suggestedLoadKg = currentLoadKg + target.loadIncrementKg,
                reason = "Todas las series alcanzaron el techo de repeticiones sin superar el esfuerzo objetivo.",
            )
        }

        val belowFloor = lastSession.count { it.reps < target.minReps }
        val majorityBelowFloor = belowFloor > lastSession.size / 2
        if (majorityBelowFloor && consecutiveUnderTargetSessions >= 1) {
            return ProgressionRecommendation(
                action = ProgressionAction.REDUCE_LOAD,
                suggestedLoadKg = max(0.0, currentLoadKg - target.loadIncrementKg),
                reason = "La mayoría de series quedó por debajo del rango durante sesiones consecutivas.",
            )
        }

        return ProgressionRecommendation(
            action = ProgressionAction.KEEP_LOAD,
            suggestedLoadKg = currentLoadKg,
            reason = "Mantén la carga e intenta sumar repeticiones dentro del rango antes de subir peso.",
        )
    }

    fun estimatedOneRepMaxEpley(loadKg: Double, reps: Int): Double {
        require(loadKg >= 0.0)
        require(reps > 0)
        return loadKg * (1.0 + reps / 30.0)
    }
}
