package com.germanchoconta.gymtracker.domain

object ProgressionEngine {
    private val newestFirst = compareByDescending<ProgressionObservation> { it.startedAt }
        .thenByDescending { it.workoutId }
        .thenByDescending { it.workoutSetId }

    /**
     * Conservative, deterministic double-progression guidance for one set position.
     * Recommendations are derived output only; callers decide whether to apply a suggested load.
     */
    fun recommend(
        setPosition: Int,
        observations: List<ProgressionObservation>,
        target: ProgressionTarget,
    ): ProgressionRecommendation {
        require(setPosition >= 0)

        val comparable = observations
            .asSequence()
            .filter { it.setPosition == setPosition }
            .sortedWith(newestFirst)
            .distinctBy { it.workoutId }
            .toList()
        val latest = comparable.firstOrNull()
            ?: return ProgressionRecommendation(
                action = ProgressionAction.NO_BASELINE,
                reason = "No hay una serie WORK completada y comparable para esta posición todavía.",
            )

        if (latest.loadGrams == 0L) {
            return recommendationFromLatest(
                action = ProgressionAction.HOLD_LOAD,
                latest = latest,
                reason = "La referencia no usa carga externa; progresa con repeticiones o esfuerzo sin inventar peso.",
                suggestedReps = nextRepAim(latest.reps, target),
            )
        }

        if (latest.reps >= target.maxReps) {
            val targetRir = target.targetRirTenths
            val actualRir = latest.rirTenths
            if (targetRir != null && actualRir != null && actualRir < targetRir) {
                return recommendationFromLatest(
                    action = ProgressionAction.HOLD_LOAD,
                    latest = latest,
                    reason = "Llegaste al techo de reps, pero el RIR registrado fue menor al objetivo; mantén la carga.",
                    suggestedLoadGrams = latest.loadGrams,
                    suggestedReps = target.maxReps,
                )
            }

            val increased = try {
                Math.addExact(latest.loadGrams, target.loadIncrementGrams)
            } catch (_: ArithmeticException) {
                return recommendationFromLatest(
                    action = ProgressionAction.REVIEW,
                    latest = latest,
                    reason = "La suma de carga excede el rango entero seguro; revisa el incremento configurado.",
                )
            }
            val rirClause = when {
                targetRir == null -> ""
                actualRir == null -> " El esfuerzo no se registró, así que la decisión usa solo reps."
                else -> " El RIR registrado no contradice el objetivo."
            }
            return recommendationFromLatest(
                action = ProgressionAction.INCREASE_LOAD,
                latest = latest,
                reason = "La referencia alcanzó el techo de ${target.maxReps} reps.$rirClause",
                suggestedLoadGrams = increased,
                suggestedReps = target.minReps,
            )
        }

        if (latest.reps >= target.minReps) {
            return recommendationFromLatest(
                action = ProgressionAction.HOLD_LOAD,
                latest = latest,
                reason = "La referencia está dentro del rango; mantén la carga e intenta sumar una repetición.",
                suggestedLoadGrams = latest.loadGrams,
                suggestedReps = nextRepAim(latest.reps, target),
            )
        }

        val previous = comparable.getOrNull(1)
        if (previous == null || previous.reps >= target.minReps) {
            return recommendationFromLatest(
                action = ProgressionAction.HOLD_LOAD,
                latest = latest,
                reason = "Una sola sesión por debajo del rango no justifica reducir la carga.",
                suggestedLoadGrams = latest.loadGrams,
                suggestedReps = target.minReps,
            )
        }

        if (previous.loadGrams != latest.loadGrams) {
            return recommendationFromLatest(
                action = ProgressionAction.REVIEW,
                latest = latest,
                reason = "Las dos referencias bajo rango usaron cargas distintas; revisa el contexto antes de reducir.",
                suggestedReps = target.minReps,
            )
        }

        val reduced = if (target.loadIncrementGrams >= latest.loadGrams) {
            0L
        } else {
            latest.loadGrams - target.loadIncrementGrams
        }
        return recommendationFromLatest(
            action = ProgressionAction.REDUCE_LOAD,
            latest = latest,
            reason = "Dos sesiones comparables consecutivas quedaron bajo ${target.minReps} reps con la misma carga.",
            suggestedLoadGrams = reduced,
            suggestedReps = target.minReps,
        )
    }

    private fun nextRepAim(reps: Int, target: ProgressionTarget): Int =
        when {
            reps < target.minReps -> target.minReps
            reps >= target.maxReps -> target.maxReps
            else -> reps + 1
        }

    private fun recommendationFromLatest(
        action: ProgressionAction,
        latest: ProgressionObservation,
        reason: String,
        suggestedLoadGrams: Long? = null,
        suggestedReps: Int? = null,
    ) = ProgressionRecommendation(
        action = action,
        reason = "${baseWorkEvidence(latest)}\n$reason",
        suggestedLoadGrams = suggestedLoadGrams,
        suggestedReps = suggestedReps,
        previousLoadGrams = latest.loadGrams,
        previousReps = latest.reps,
        previousRirTenths = latest.rirTenths,
    )

    private fun baseWorkEvidence(observation: ProgressionObservation): String {
        val rir = observation.rirTenths?.let { " · RIR ${rirTenthsText(it)}" }.orEmpty()
        return "BASE WORK • ${loadKilogramsText(observation.loadGrams)} kg × ${observation.reps}$rir"
    }

    private fun loadKilogramsText(grams: Long): String {
        val whole = grams / 1_000L
        val fraction = (grams % 1_000L).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
    }

    private fun rirTenthsText(tenths: Int): String {
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (fraction == 0) whole.toString() else "$whole.$fraction"
    }
}
