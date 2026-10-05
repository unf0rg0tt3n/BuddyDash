package com.chronoswing.buddydash.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcFinishWorkflowTest {

    @Test
    fun evaluateFinishOutcome_bothEnabled_bothSucceed_returnsFinishedWithPowerOff() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Success,
            powerOffResult = FinishActionResult.Success,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = true),
        )
        assertEquals(NfcActionOutcome.FinishedWithPowerOff, outcome)
    }

    @Test
    fun evaluateFinishOutcome_bothEnabled_plateClearAlreadyDone_powerOffSucceeds_returnsFinishedWithPowerOff() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.AlreadyDone,
            powerOffResult = FinishActionResult.Success,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = true),
        )
        assertEquals(NfcActionOutcome.FinishedWithPowerOff, outcome)
    }

    @Test
    fun evaluateFinishOutcome_bothEnabled_clearPlateFails_returnsApiFailed() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Failed,
            powerOffResult = FinishActionResult.Success,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = true),
        )
        assertEquals(NfcActionOutcome.ApiFailed, outcome)
    }

    @Test
    fun evaluateFinishOutcome_bothEnabled_powerOffFails_returnsApiFailed() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Success,
            powerOffResult = FinishActionResult.Failed,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = true),
        )
        assertEquals(NfcActionOutcome.ApiFailed, outcome)
    }

    @Test
    fun evaluateFinishOutcome_onlyClearPlateEnabled_succeeds_returnsFinishedPlateClear() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Success,
            powerOffResult = FinishActionResult.Skipped,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = false),
        )
        assertEquals(NfcActionOutcome.FinishedPlateClear, outcome)
    }

    @Test
    fun evaluateFinishOutcome_onlyClearPlateEnabled_fails_returnsApiFailed() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Failed,
            powerOffResult = FinishActionResult.Skipped,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = false),
        )
        assertEquals(NfcActionOutcome.ApiFailed, outcome)
    }

    @Test
    fun evaluateFinishOutcome_onlyPowerOffEnabled_succeeds_returnsFinishedPowerOff() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Skipped,
            powerOffResult = FinishActionResult.Success,
            config = FinishWorkflowConfig(clearPlate = false, powerOff = true),
        )
        assertEquals(NfcActionOutcome.FinishedPowerOff, outcome)
    }

    @Test
    fun evaluateFinishOutcome_onlyPowerOffEnabled_fails_returnsApiFailed() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Skipped,
            powerOffResult = FinishActionResult.Failed,
            config = FinishWorkflowConfig(clearPlate = false, powerOff = true),
        )
        assertEquals(NfcActionOutcome.ApiFailed, outcome)
    }

    @Test
    fun evaluateFinishOutcome_bothEnabled_noOutletConfigured_returnsFinishedPlateClear() {
        val outcome = evaluateFinishOutcome(
            clearPlateResult = FinishActionResult.Success,
            powerOffResult = FinishActionResult.Skipped,
            config = FinishWorkflowConfig(clearPlate = true, powerOff = true),
        )
        assertEquals(NfcActionOutcome.FinishedPlateClear, outcome)
    }
}
