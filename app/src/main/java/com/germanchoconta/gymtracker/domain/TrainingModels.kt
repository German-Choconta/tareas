package com.germanchoconta.gymtracker.domain

data class SetPerformance(
    val loadKg: Double,
    val reps: Int,
    val rir: Int? = null,
) {
    init {
        require(loadKg >= 0.0) { "Load cannot be negative" }
        require(reps >= 0) { "Repetitions cannot be negative" }
        require(rir == null || rir >= 0) { "RIR cannot be negative" }
    }

    val volumeKg: Double get() = loadKg * reps
}

data class ProgressionTarget(
    val minReps: Int,
    val maxReps: Int,
    val targetRir: Int,
    val loadIncrementKg: Double,
) {
    init {
        require(minReps > 0)
        require(maxReps >= minReps)
        require(targetRir >= 0)
        require(loadIncrementKg > 0)
    }
}

enum class ProgressionAction {
    INCREASE_LOAD,
    KEEP_LOAD,
    REDUCE_LOAD,
}

data class ProgressionRecommendation(
    val action: ProgressionAction,
    val suggestedLoadKg: Double,
    val reason: String,
)
