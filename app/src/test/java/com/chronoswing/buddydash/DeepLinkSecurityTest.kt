package com.chronoswing.buddydash

import android.nfc.NfcAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkSecurityTest {

    @Test
    fun isAuthorizedActionIntentSource_acceptsNdefDiscoveredAction() {
        val source = ActionIntentSource(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
        )
        assertTrue(isAuthorizedActionIntentSource(source, expectedPackageName = "com.chronoswing.buddydash"))
    }

    @Test
    fun isAuthorizedActionIntentSource_acceptsNfcExtras() {
        val source = ActionIntentSource(
            action = "android.intent.action.VIEW",
            hasNfcExtra = true,
        )
        assertTrue(isAuthorizedActionIntentSource(source, expectedPackageName = "com.chronoswing.buddydash"))
    }

    @Test
    fun isAuthorizedActionIntentSource_acceptsInternalActionFlagFromOwnPackage() {
        val source = ActionIntentSource(
            action = "android.intent.action.VIEW",
            isInternalFlag = true,
            packageName = "com.chronoswing.buddydash",
        )
        assertTrue(isAuthorizedActionIntentSource(source, expectedPackageName = "com.chronoswing.buddydash"))
    }

    @Test
    fun isAuthorizedActionIntentSource_rejectsExternalActionViewWithoutNfcOrInternalPackage() {
        val source = ActionIntentSource(
            action = "android.intent.action.VIEW",
            isInternalFlag = false,
            packageName = null,
        )
        assertFalse(isAuthorizedActionIntentSource(source, expectedPackageName = "com.chronoswing.buddydash"))
    }

    @Test
    fun isAuthorizedActionIntentSource_rejectsInternalFlagFromAnotherPackage() {
        val source = ActionIntentSource(
            action = "android.intent.action.VIEW",
            isInternalFlag = true,
            packageName = "com.evil.app",
        )
        assertFalse(isAuthorizedActionIntentSource(source, expectedPackageName = "com.chronoswing.buddydash"))
    }

    @Test
    fun isAuthorizedIntent_rejectsNullIntent() {
        assertFalse(isAuthorizedNfcOrInternalIntent(null, myPackageName = "com.chronoswing.buddydash"))
    }
}
