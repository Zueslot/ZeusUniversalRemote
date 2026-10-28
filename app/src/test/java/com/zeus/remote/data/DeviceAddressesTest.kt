package com.zeus.remote.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAddressesTest {
    @Test fun pastedConnectionErrorsCannotBecomePartOfTheAddress() {
        assertEquals("10.0.0.130", singleLineDeviceAddress("10.0.0.130\r\n\r\nFailure in SSL library\nPairing failed: Read error"))
        assertEquals("10.0.0.180", singleLineDeviceAddress("  10.0.0.180\nTurn Shield on and select its HDMI input. "))
    }
    @Test fun ordinaryInputAndPartialEditsArePreserved() {
        assertEquals("192.168.1.4", singleLineDeviceAddress(" 192.168.1.4 "))
        assertEquals("10.", singleLineDeviceAddress("10."))
        assertEquals("", singleLineDeviceAddress(" \n "))
    }
}
