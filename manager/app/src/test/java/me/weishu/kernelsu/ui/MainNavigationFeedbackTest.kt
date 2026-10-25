package me.weishu.kernelsu.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import me.weishu.kernelsu.ui.component.bottombar.MainPagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationFeedbackTest {
    @Test
    fun selectionUpdatesBeforeThePagerAnimationStarts() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        try {
            val state = createState(scope)
            state.animateToPage(2)

            assertEquals(2, state.selectedPage)
            assertTrue(state.isNavigating)
            state.syncSettledPage(0)
            assertEquals("An older settled page cannot undo the new selection", 2, state.selectedPage)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun repeatedTapOnTheSameDestinationDoesNotRestartItsAnimation() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        try {
            val state = createState(scope)
            state.animateToPage(2)
            val firstAnimation = scope.coroutineContext.job.children.single()

            repeat(5) { state.animateToPage(2) }

            assertSame(firstAnimation, scope.coroutineContext.job.children.single())
            assertEquals(2, state.selectedPage)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun rapidChangesCancelThePreviousDestinationAndKeepOnlyTheLatestSelection() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        try {
            val state = createState(scope)
            state.animateToPage(2)
            val firstAnimation = scope.coroutineContext.job.children.single()

            state.animateToPage(1)

            assertTrue(firstAnimation.isCancelled)
            assertEquals(1, state.selectedPage)
            assertEquals(1, scope.coroutineContext.job.children.count { it.isActive })
            state.animateToPage(99)
            assertEquals(1, state.selectedPage)
        } finally {
            scope.cancel()
        }
    }

    private fun createState(scope: CoroutineScope): MainPagerState {
        val count = mutableIntStateOf(4)
        val pager = object : PagerState() {
            override val pageCount: Int get() = count.intValue
        }
        return MainPagerState(pager, scope, count).also { it.updateFeatureAvailability(true) }
    }
}
