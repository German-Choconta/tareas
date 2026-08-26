package com.germanchoconta.gymtracker.domain

data class ProgressionObservation(
    val workoutId: String,
    val startedAt: Long,
    val workoutSetId: String,
    val setPosition: Int,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int? = null,
) {
    init {
        require(setPosition >= 0)
        require(loadGrams >= 0L)
        require(reps > 0)
        require(rirTenths == null || rirTenths in 0..100)
    }
}

data class ProgressionTarget(
    val minReps: Int,
    val maxReps: Int,
    val targetRirTenths: Int?,
    val loadIncrementGrams: Long,
) {
    init {
        require(minReps > 0)
        require(maxReps >= minReps)
        require(targetRirTenths == null || targetRirTenths in 0..100)
        require(loadIncrementGrams > 0L)
    }
}

enum class ProgressionAction {
    NO_BASELINE,
    INCREASE_LOAD,
    HOLD_LOAD,
    REDUCE_LOAD,
    REVIEW,
}

data class ProgressionRecommendation(
    val action: ProgressionAction,
    val reason: String,
    val suggestedLoadGrams: Long? = null,
    val suggestedReps: Int? = null,
    val previousLoadGrams: Long? = null,
    val previousReps: Int? = null,
    val previousRirTenths: Int? = null,
)
