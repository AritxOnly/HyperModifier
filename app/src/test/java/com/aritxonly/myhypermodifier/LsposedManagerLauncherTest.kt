package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LsposedManagerLauncherTest {
    @Test fun rootBridgeIsRestrictedToVerifiedLsposedProtocol() {
        assertTrue(LsposedManagerLauncher.usesRootManagerBridge("org.lsposed.manager"))
        assertFalse(LsposedManagerLauncher.usesRootManagerBridge("org.matrix.vector.manager"))
        assertFalse(LsposedManagerLauncher.usesRootManagerBridge("other.app"))
        assertFalse(LsposedManagerLauncher.usesRootManagerBridge(null))
    }
    @Test fun managerShortcutNeverMatchesAnOrdinaryBugReport() {
        assertTrue(LsposedManagerLauncher.isManagerShortcut("org.lsposed.manager", "LSPosed", "org.lsposed.manager"))
        assertFalse(LsposedManagerLauncher.isManagerShortcut("bugreport", "错误报告", "org.lsposed.manager"))
        assertFalse(LsposedManagerLauncher.isManagerShortcut("vector", "Vector", "org.lsposed.manager"))
    }
    @Test fun vectorUsesItsOwnManagerNamespace() {
        assertEquals("org.matrix.vector.manager", LsposedManagerLauncher.managerPackage("Vector"))
        assertEquals("org.matrix.vector.manager", LsposedManagerLauncher.managerPackage("VECTOR 2.2"))
    }
    @Test fun lsposedRetainsItsOfficialNamespace() {
        assertEquals("org.lsposed.manager", LsposedManagerLauncher.managerPackage("LSPosed"))
    }
    @Test fun unknownFrameworkNeverFallsBackToLaunchingShell() {
        assertNull(LsposedManagerLauncher.managerPackage(null))
        assertNull(LsposedManagerLauncher.managerPackage(""))
        assertNull(LsposedManagerLauncher.managerPackage("Other framework"))
    }
}
