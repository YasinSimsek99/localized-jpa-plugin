package com.localizedjpa.intellij

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [LocalizedJpaStartupActivity].
 *
 * Uses IntelliJ Platform Test SDK to mock project structure and libraries.
 */
class LocalizedJpaStartupActivityTest : BasePlatformTestCase() {

    private val activity = LocalizedJpaStartupActivity()

    fun `test activity can be instantiated`() {
        assertNotNull(activity)
    }

    fun `test hasLocalizedJpaLibrary detects library`() {
        // Initially no library
        assertFalse("Should not find library initially", activity.hasLocalizedJpaLibrary(project))

        // Add "localized-jpa" library to module with explicit Name which logic checks
        ModuleRootModificationUtil.addModuleLibrary(
            myFixture.module,
            "localized-jpa-0.9.0", 
            listOf(), 
            listOf()
        )
        
        // Should find it now
        assertTrue("Should find localized-jpa library", activity.hasLocalizedJpaLibrary(project))
    }

    fun `test hasLocalizedJpaLibrary ignores other libraries`() {
        // Add irrelevant library
        ModuleRootModificationUtil.addModuleLibrary(
            myFixture.module,
            "spring-boot",
            listOf(), 
            listOf()
        )
        
        assertFalse("Should not find library if strictly looking for localized-jpa", activity.hasLocalizedJpaLibrary(project))
    }
}
