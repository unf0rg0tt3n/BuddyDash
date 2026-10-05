package com.chronoswing.buddydash.util

import android.net.Uri
import com.chronoswing.buddydash.data.model.Printer
import com.chronoswing.buddydash.data.model.PrinterStatus

// ── Deep-link model ───────────────────────────────────────────────

enum class NfcActionKind(val slug: String) {
    ClearPlate("clear-plate"),
    TogglePower("toggle-power"),
    Finish("finish"),
}

data class NfcDeepLink(
    val printerKey: String,
    val action: NfcActionKind,
)

// ── Outcomes ──────────────────────────────────────────────────────

sealed class NfcActionOutcome {
    /** Feedback tier for haptics. */
    enum class Tier { Success, Noop, Warning, Failure }

    abstract val tier: Tier

    data object Debounced : NfcActionOutcome() { override val tier = Tier.Noop }
    data object InvalidLink : NfcActionOutcome() { override val tier = Tier.Failure }
    data object MissingCredentials : NfcActionOutcome() { override val tier = Tier.Failure }
    data object ConnectionRequired : NfcActionOutcome() { override val tier = Tier.Warning }
    data object PrinterNotFound : NfcActionOutcome() { override val tier = Tier.Failure }
    data object ApiFailed : NfcActionOutcome() { override val tier = Tier.Failure }

    // clear-plate
    data class PlateCleared(val printerName: String) : NfcActionOutcome() { override val tier = Tier.Success }
    data object PlateAlreadyClear : NfcActionOutcome() { override val tier = Tier.Noop }
    data object PrinterBusyPlateUnchanged : NfcActionOutcome() { override val tier = Tier.Warning }

    // toggle-power
    data class PowerOn(val printerName: String) : NfcActionOutcome() { override val tier = Tier.Success }
    data class PowerOff(val printerName: String) : NfcActionOutcome() { override val tier = Tier.Success }
    data object PrinterBusyPowerUnchanged : NfcActionOutcome() { override val tier = Tier.Warning }
    data object SmartOutletUnavailable : NfcActionOutcome() { override val tier = Tier.Warning }
    data object SmartOutletStateUnknown : NfcActionOutcome() { override val tier = Tier.Warning }

    // finish
    data object FinishedWithPowerOff : NfcActionOutcome() { override val tier = Tier.Success }
    data object FinishedPlateClear : NfcActionOutcome() { override val tier = Tier.Success }
    data object FinishedPowerOff : NfcActionOutcome() { override val tier = Tier.Success }
    data object PrinterBusyFinishSkipped : NfcActionOutcome() { override val tier = Tier.Warning }
}

// ── Finish workflow evaluation ─────────────────────────────────────

data class FinishWorkflowConfig(
    val clearPlate: Boolean = true,
    val powerOff: Boolean = true,
)

enum class FinishActionResult {
    Success,
    AlreadyDone,
    Failed,
    Skipped,
}

fun evaluateFinishOutcome(
    clearPlateResult: FinishActionResult,
    powerOffResult: FinishActionResult,
    config: FinishWorkflowConfig,
): NfcActionOutcome {
    if (config.clearPlate && clearPlateResult == FinishActionResult.Failed) {
        return NfcActionOutcome.ApiFailed
    }
    if (config.powerOff && powerOffResult == FinishActionResult.Failed) {
        return NfcActionOutcome.ApiFailed
    }

    val didPowerOff = powerOffResult == FinishActionResult.Success
    val didClearPlate = clearPlateResult == FinishActionResult.Success ||
        clearPlateResult == FinishActionResult.AlreadyDone

    return when {
        config.powerOff && didPowerOff && config.clearPlate -> NfcActionOutcome.FinishedWithPowerOff
        config.powerOff && didPowerOff && !config.clearPlate -> NfcActionOutcome.FinishedPowerOff
        config.clearPlate && didClearPlate -> NfcActionOutcome.FinishedPlateClear
        config.powerOff && powerOffResult == FinishActionResult.AlreadyDone && config.clearPlate -> NfcActionOutcome.FinishedPlateClear
        else -> NfcActionOutcome.FinishedPlateClear
    }
}

// ── Deep-link parsing ─────────────────────────────────────────────

private val NFC_URI_PATTERN = Regex(
    """buddydash://printer/([^/]+)/(clear-plate|toggle-power|finish)/?""",
    RegexOption.IGNORE_CASE,
)

private val ACTION_SLUGS = NfcActionKind.entries.associateBy { it.slug }

fun parseNfcDeepLink(uri: Uri?): NfcDeepLink? {
    if (uri == null) return null
    parseNfcFromUriString(uri.toString())?.let { return it }
    val host = uri.host ?: return null
    if (!host.equals("printer", ignoreCase = true)) return null
    val parts = uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
    if (parts.size == 2) {
        val action = ACTION_SLUGS[parts[1].lowercase()] ?: return null
        val key = (Uri.decode(parts[0]) ?: parts[0]).trim()
        if (key.isEmpty()) return null
        return NfcDeepLink(printerKey = key, action = action)
    }
    return null
}

internal fun parseNfcFromUriString(raw: String): NfcDeepLink? {
    val match = NFC_URI_PATTERN.matchEntire(raw.trim()) ?: return null
    val key = (Uri.decode(match.groupValues[1]) ?: match.groupValues[1]).trim()
    if (key.isEmpty()) return null
    val action = ACTION_SLUGS[match.groupValues[2].lowercase()] ?: return null
    return NfcDeepLink(printerKey = key, action = action)
}

// ── Safety helpers ─────────────────────────────────────────────────

private val SAFE_POWER_OFF_STATES = setOf(
    "IDLE", "FINISH", "SUCCESS", "FAILED", "CANCEL", "CANCELLED", "STOP", "STOPPED",
)

/**
 * True when the printer is safe to power off.
 * Uses a strict allowlist of confirmed idle/completed states.
 * When in doubt, returns false.
 */
fun isPrinterSafeToPowerOff(status: PrinterStatus?): Boolean {
    if (status == null) return false
    if (!status.connected) return false
    if (status.hasActiveFault()) return false
    val raw = status.rawState?.uppercase()?.trim() ?: return false
    if (raw !in SAFE_POWER_OFF_STATES) return false
    if (raw.contains("HEAT") || raw.contains("COOL")) return false
    if (raw.contains("LOAD") || raw.contains("UNLOAD")) return false
    if (raw.contains("UPDATE") || raw.contains("FIRMWARE")) return false
    return true
}

// ── Debounce (shared for all NFC actions) ──────────────────────────

object NfcActionDebounce {
    private const val DEBOUNCE_MS = 2_000L

    @Volatile private var lastKey: String? = null
    @Volatile private var lastAtMillis: Long = 0L

    @Synchronized
    fun shouldProcess(actionKey: String): Boolean {
        val now = System.currentTimeMillis()
        if (lastKey == actionKey && now - lastAtMillis < DEBOUNCE_MS) return false
        lastKey = actionKey
        lastAtMillis = now
        return true
    }
}

// ── URI builders ──────────────────────────────────────────────────

fun buildNfcActionUri(printerId: Int, action: NfcActionKind): String =
    "buddydash://printer/$printerId/${action.slug}"
