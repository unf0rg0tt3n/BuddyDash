package com.chronoswing.buddydash.util

import com.chronoswing.buddydash.data.model.Printer
import com.chronoswing.buddydash.data.model.PrinterSmartPlugState
import com.chronoswing.buddydash.data.model.PrinterStatus
import com.chronoswing.buddydash.data.model.SmartOutletPowerState
import com.chronoswing.buddydash.data.HomePrintersCacheRepository
import com.chronoswing.buddydash.network.BambuddyApiClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class SmartPlugToggleResult(
    val outcome: NfcActionOutcome,
    val updatedPlugState: PrinterSmartPlugState? = null,
    val updatedLiveStatus: PrinterStatus? = null,
)

/**
 * Toggle smart-outlet power for [printer].
 * When [forceUnsafePowerOff] is false (NFC default), busy printers block power-off.
 * In-app UI may set [forceUnsafePowerOff] after user confirmation.
 */
suspend fun toggleSmartPlugPower(
    apiClient: BambuddyApiClient,
    serverUrl: String,
    apiKey: String,
    printer: Printer,
    forceUnsafePowerOff: Boolean = false,
): SmartPlugToggleResult {
    val plugState = printer.smartPlugState
        ?: apiClient.fetchPrinterSmartPlugState(serverUrl, apiKey, printer.id).getOrNull()
        ?: return SmartPlugToggleResult(NfcActionOutcome.SmartOutletUnavailable)

    val currentPower = plugState.displayPowerState
    if (currentPower == SmartOutletPowerState.Unknown) {
        return SmartPlugToggleResult(NfcActionOutcome.SmartOutletStateUnknown, plugState)
    }

    val plugId = plugState.config.id

    return if (currentPower == SmartOutletPowerState.Off) {
        apiClient.controlSmartPlug(serverUrl, apiKey, plugId, action = "on").fold(
            onSuccess = {
                SmartPlugToggleResult(
                    outcome = NfcActionOutcome.PowerOn(printer.name),
                    updatedPlugState = refreshPlugState(apiClient, serverUrl, apiKey, printer.id)
                        ?: plugState.copy(
                            config = plugState.config.copy(lastState = "on"),
                        ),
                    updatedLiveStatus = apiClient.fetchPrinterStatus(serverUrl, apiKey, printer.id)
                        .getOrNull(),
                )
            },
            onFailure = { error ->
                SmartPlugToggleResult(mapToggleFailure(error), plugState)
            },
        )
    } else {
        // Turning OFF:
        // When not forcing unsafe power off, we MUST fetch fresh status from network to verify
        val liveStatusResult = apiClient.fetchPrinterStatus(serverUrl, apiKey, printer.id)
        val liveStatus = liveStatusResult.getOrNull()

        if (!forceUnsafePowerOff) {
            if (liveStatus == null) {
                // If we cannot verify live status from network, fail closed unless forced
                val cached = printer.liveStatus
                if (cached != null && !isPrinterSafeToPowerOff(cached)) {
                    return SmartPlugToggleResult(NfcActionOutcome.PrinterBusyPowerUnchanged, plugState)
                }
                val error = liveStatusResult.exceptionOrNull()
                return SmartPlugToggleResult(
                    outcome = if (error != null) mapToggleFailure(error) else NfcActionOutcome.ConnectionRequired,
                    updatedPlugState = plugState,
                )
            }
            if (!isPrinterSafeToPowerOff(liveStatus)) {
                return SmartPlugToggleResult(
                    outcome = NfcActionOutcome.PrinterBusyPowerUnchanged,
                    updatedPlugState = plugState,
                    updatedLiveStatus = liveStatus,
                )
            }
        }
        apiClient.controlSmartPlug(serverUrl, apiKey, plugId, action = "off").fold(
            onSuccess = {
                SmartPlugToggleResult(
                    outcome = NfcActionOutcome.PowerOff(printer.name),
                    updatedPlugState = refreshPlugState(apiClient, serverUrl, apiKey, printer.id)
                        ?: plugState.copy(
                            config = plugState.config.copy(lastState = "off"),
                        ),
                    updatedLiveStatus = liveStatus ?: apiClient.fetchPrinterStatus(serverUrl, apiKey, printer.id)
                        .getOrNull(),
                )
            },
            onFailure = { error ->
                SmartPlugToggleResult(mapToggleFailure(error), plugState, liveStatus)
            },
        )
    }
}

private suspend fun refreshPlugState(
    apiClient: BambuddyApiClient,
    serverUrl: String,
    apiKey: String,
    printerId: Int,
): PrinterSmartPlugState? =
    apiClient.fetchPrinterSmartPlugState(serverUrl, apiKey, printerId).getOrNull()

private fun mapToggleFailure(error: Throwable): NfcActionOutcome =
    if (error.isConnectivityFailure()) NfcActionOutcome.ConnectionRequired
    else NfcActionOutcome.SmartOutletUnavailable

private fun Throwable.isConnectivityFailure(): Boolean =
    this is IOException ||
        this is UnknownHostException ||
        this is SocketTimeoutException ||
        message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        message?.contains("Failed to connect", ignoreCase = true) == true ||
        message?.contains("timeout", ignoreCase = true) == true

/** Keep Home cache in sync after a successful in-app smart-outlet toggle. */
suspend fun refreshHomeCacheAfterSmartPlugToggle(
    homePrintersCacheRepository: HomePrintersCacheRepository,
    apiClient: BambuddyApiClient,
    serverUrl: String,
    apiKey: String,
    printerId: Int,
    updatedPlugState: PrinterSmartPlugState?,
    updatedLiveStatus: PrinterStatus?,
) {
    val snapshot = homePrintersCacheRepository.load(serverUrl)
    val basePrinters = snapshot?.printers
        ?: apiClient.fetchPrinters(serverUrl, apiKey).getOrNull()
    if (basePrinters.isNullOrEmpty()) return
    val updated = basePrinters.map { cached ->
        if (cached.id != printerId) cached
        else cached.copy(
            smartPlugState = updatedPlugState ?: cached.smartPlugState,
            liveStatus = updatedLiveStatus ?: cached.liveStatus,
        )
    }
    homePrintersCacheRepository.save(
        serverUrl = serverUrl,
        printers = updated,
        lastUpdatedAtMillis = System.currentTimeMillis(),
    )
}
