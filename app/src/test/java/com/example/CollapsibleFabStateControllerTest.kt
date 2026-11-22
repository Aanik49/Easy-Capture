package com.example

import com.example.ui.components.CollapsibleFabStateController
import com.example.ui.components.FAB_AUTO_COLLAPSE_TIMEOUT_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollapsibleFabStateControllerTest {

    @Test
    fun startsCollapsed_expandsOnTap_andAutoCollapsesAfter2Seconds() = runTest {
        val controller = CollapsibleFabStateController(scope = this)

        assertFalse(controller.isExpanded.value)

        controller.expand()
        assertTrue(controller.isExpanded.value)

        advanceTimeBy(FAB_AUTO_COLLAPSE_TIMEOUT_MS - 1L)
        runCurrent()
        assertTrue(controller.isExpanded.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.isExpanded.value)
    }

    @Test
    fun interactionWhileExpanded_resetsTwoSecondAutoCollapseTimer() = runTest {
        val controller = CollapsibleFabStateController(scope = this)

        controller.expand()
        advanceTimeBy(1_500L)
        runCurrent()
        assertTrue(controller.isExpanded.value)

        // Interacting (e.g. tapping Pause) resets the 2-second timeout
        controller.onInteractionWhileExpanded()
        advanceTimeBy(1_500L)
        runCurrent()
        assertTrue(controller.isExpanded.value)

        advanceTimeBy(500L)
        runCurrent()
        assertFalse(controller.isExpanded.value)
    }

    @Test
    fun collapseNow_immediatelyReturnsToSingleIconState() = runTest {
        val controller = CollapsibleFabStateController(scope = this)

        controller.expand()
        assertTrue(controller.isExpanded.value)

        controller.collapseNow()
        assertFalse(controller.isExpanded.value)
    }
}
