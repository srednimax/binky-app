package app.bunny.tracker.ui.observations

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.ActivityLevel
import app.bunny.tracker.data.Appetite
import app.bunny.tracker.data.Cecotropes
import app.bunny.tracker.data.DroppingsAmount
import app.bunny.tracker.data.DroppingsForm
import app.bunny.tracker.data.DroppingsSize
import app.bunny.tracker.data.Mood
import app.bunny.tracker.data.SymptomEntity
import app.bunny.tracker.data.WaterIntake

/*
 * Every observation vocabulary as user-facing text.
 *
 * The enums are stored **by name** and rendered through `strings.xml`, so the stored value and the
 * shown value can never be the same string (house rule, ADR-0013). The same rule applies to a
 * built-in symptom, which stores a stable key and no label at all (ADR-0010) — the labels are here,
 * which is why `SymptomRepository.add` takes them as a parameter rather than looking them up: the
 * data layer stays free of `R.string`.
 */

/**
 * The built-in symptoms' display text, keyed by the stable key stored on the row.
 *
 * Kept in step with `BUILT_IN_SYMPTOM_KEYS` by `ObservationLabelsTest`, which is the only mechanism
 * that can catch a key shipped without a label: adding a key seeds a row on the next launch, and a
 * missing label would render as a blank line in the picker rather than as any kind of error.
 */
val BUILT_IN_SYMPTOM_LABELS: Map<String, Int> =
    mapOf(
        "head_tilt" to R.string.symptom_head_tilt,
        "drooling_or_wet_chin" to R.string.symptom_drooling_or_wet_chin,
        "sneezing_or_nasal_discharge" to R.string.symptom_sneezing_or_nasal_discharge,
        "eye_discharge" to R.string.symptom_eye_discharge,
        "dirty_bottom" to R.string.symptom_dirty_bottom,
        "loud_teeth_grinding" to R.string.symptom_loud_teeth_grinding,
        "hunched_posture" to R.string.symptom_hunched_posture,
        "laboured_breathing" to R.string.symptom_laboured_breathing,
        "not_drinking" to R.string.symptom_not_drinking,
        "limping" to R.string.symptom_limping,
        "ear_scratching" to R.string.symptom_ear_scratching,
        "blood_in_urine" to R.string.symptom_blood_in_urine,
        "hiding_more_than_usual" to R.string.symptom_hiding_more_than_usual,
    )

/**
 * One symptom's display text — a built-in's translated label, or an owner-added row's literal text.
 *
 * `key == null` *is* the built-in/owner-added distinction (ADR-0010), so this needs no third case.
 * A built-in whose key has no label falls back to the key rather than to blank: a legible oddity
 * beats an invisible one.
 */
@Composable
fun symptomLabel(symptom: SymptomEntity): String =
    when {
        symptom.key != null -> BUILT_IN_SYMPTOM_LABELS[symptom.key]?.let { stringResource(it) } ?: symptom.key
        else -> symptom.label.orEmpty()
    }

/**
 * Every built-in's key mapped to its label **as currently resolved**, for
 * `SymptomRepository.add`'s duplicate check.
 *
 * Resolved here rather than there because that check has to compare against what the owner can
 * actually see, in their language — which needs a `Context`, which belongs to the UI (ADR-0010).
 */
@Composable
fun builtInSymptomLabels(): Map<String, String> =
    BUILT_IN_SYMPTOM_LABELS.mapValues { (_, labelRes) -> stringResource(labelRes) }

/**
 * The label for a graded field's value, or **"not checked"** when it is `null`.
 *
 * `null` is not a missing value to be papered over: it is the recorded fact that nobody looked, and
 * showing it as "normal" would be the unverified reassurance ADR-0001 exists to forbid.
 */
@Composable
fun notCheckedLabel(): String = stringResource(R.string.observation_not_checked)

@StringRes
private fun DroppingsAmount.labelRes(): Int =
    when (this) {
        DroppingsAmount.NONE -> R.string.droppings_amount_none
        DroppingsAmount.FEW -> R.string.droppings_amount_few
        DroppingsAmount.NORMAL -> R.string.droppings_amount_normal
        DroppingsAmount.MANY -> R.string.droppings_amount_many
    }

@StringRes
private fun DroppingsSize.labelRes(): Int =
    when (this) {
        DroppingsSize.SMALL -> R.string.droppings_size_small
        DroppingsSize.NORMAL -> R.string.droppings_size_normal
        DroppingsSize.LARGE -> R.string.droppings_size_large
    }

@StringRes
private fun DroppingsForm.labelRes(): Int =
    when (this) {
        DroppingsForm.ROUND -> R.string.droppings_form_round
        DroppingsForm.MISSHAPEN -> R.string.droppings_form_misshapen
        DroppingsForm.STRUNG_TOGETHER -> R.string.droppings_form_strung_together
        DroppingsForm.SOFT -> R.string.droppings_form_soft
        DroppingsForm.DIARRHOEA -> R.string.droppings_form_diarrhoea
    }

@StringRes
private fun Cecotropes.labelRes(): Int =
    when (this) {
        Cecotropes.EATEN -> R.string.cecotropes_eaten
        Cecotropes.LEFT_UNEATEN -> R.string.cecotropes_left_uneaten
    }

@StringRes
private fun Appetite.labelRes(): Int =
    when (this) {
        Appetite.NONE -> R.string.appetite_none
        Appetite.REDUCED -> R.string.appetite_reduced
        Appetite.NORMAL -> R.string.appetite_normal
        Appetite.EAGER -> R.string.appetite_eager
    }

@StringRes
private fun Mood.labelRes(): Int =
    when (this) {
        Mood.WITHDRAWN -> R.string.mood_withdrawn
        Mood.SUBDUED -> R.string.mood_subdued
        Mood.NORMAL -> R.string.mood_normal
        Mood.BRIGHT -> R.string.mood_bright
    }

@StringRes
private fun ActivityLevel.labelRes(): Int =
    when (this) {
        ActivityLevel.LETHARGIC -> R.string.activity_lethargic
        ActivityLevel.QUIET -> R.string.activity_quiet
        ActivityLevel.NORMAL -> R.string.activity_normal
        ActivityLevel.ACTIVE -> R.string.activity_active
    }

@StringRes
private fun WaterIntake.labelRes(): Int =
    when (this) {
        WaterIntake.NONE -> R.string.water_none
        WaterIntake.LESS -> R.string.water_less
        WaterIntake.NORMAL -> R.string.water_normal
        WaterIntake.MORE -> R.string.water_more
    }

/**
 * One label function per vocabulary, taking the nullable value.
 *
 * Kotlin note: these are overloads on distinct enum types, so `optionLabel(it)` resolves without a
 * cast wherever a form or a timeline row renders one — and adding a value to any of the enums above
 * breaks the corresponding `when` at compile time, which is the whole reason they are enums rather
 * than strings.
 */
@Composable fun label(value: DroppingsAmount?): String =
    value?.let {
        stringResource(
            it.labelRes(),
        )
    } ?: notCheckedLabel()

@Composable fun label(value: DroppingsSize?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: DroppingsForm?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: Cecotropes?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: Appetite?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: Mood?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: ActivityLevel?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()

@Composable fun label(value: WaterIntake?): String = value?.let { stringResource(it.labelRes()) } ?: notCheckedLabel()
