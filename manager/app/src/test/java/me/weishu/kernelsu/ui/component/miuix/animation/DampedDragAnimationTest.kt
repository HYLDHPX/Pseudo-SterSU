package me.weishu.kernelsu.ui.component.miuix.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DampedDragAnimationTest {
    @Test
    fun selectionFinishesWithoutWaitingForPressOrReleaseDeformation() = runBlocking {
        val clock = BroadcastFrameClock()
        val animation = createAnimation(this)
        val selection = launch(clock) {
            animation.animateToValue(3f, tween(180, easing = FastOutSlowInEasing))
        }

        advanceFrames(clock, 0, 14)

        assertTrue(selection.isCompleted)
        assertEquals(3f, animation.value, 0.001f)
        assertEquals(0f, animation.pressProgress, 0f)
        assertEquals(1f, animation.scaleX, 0f)
        assertEquals(1f, animation.scaleY, 0f)
    }

    @Test
    fun newerSelectionCancelsTheOldDestinationInsteadOfQueuingIt() = runBlocking {
        val clock = BroadcastFrameClock()
        val animation = createAnimation(this)
        val oldSelection = launch(clock) { animation.animateToValue(3f, tween(180)) }
        advanceFrames(clock, 0, 5)
        assertTrue(animation.value > 0f && animation.value < 3f)

        val latestSelection = launch(clock) { animation.animateToValue(1f, tween(180)) }
        advanceFrames(clock, 5, 14)

        assertTrue(oldSelection.isCancelled)
        assertTrue(latestSelection.isCompleted)
        assertEquals(1f, animation.value, 0.001f)
        assertEquals(1f, animation.targetValue, 0f)
    }

    @Test
    fun cancellingTheCollectorAlsoStopsItsAnimationChildren() = runBlocking {
        val clock = BroadcastFrameClock()
        val animation = createAnimation(this)
        val selection = launch(clock) { animation.animateToValue(3f, tween(180)) }
        advanceFrames(clock, 0, 5)
        selection.cancelAndJoin()
        val stoppedValue = animation.value

        advanceFrames(clock, 5, 20)

        assertFalse(clock.hasAwaiters)
        assertEquals(stoppedValue, animation.value, 0f)
        val returnSelection = launch(clock) { animation.animateToValue(0f, tween(180)) }
        advanceFrames(clock, 25, 14)
        assertTrue(returnSelection.isCompleted)
        assertEquals(0f, animation.value, 0.001f)
    }

    @Test
    fun destinationsAreClampedWhenNavigationItemsChange() = runBlocking {
        val clock = BroadcastFrameClock()
        val animation = createAnimation(this)
        val lastSelection = launch(clock) { animation.animateToValue(10f, tween(180)) }
        advanceFrames(clock, 0, 14)
        assertTrue(lastSelection.isCompleted)
        assertEquals(3f, animation.value, 0.001f)

        val firstSelection = launch(clock) { animation.animateToValue(-2f, tween(180)) }
        advanceFrames(clock, 14, 14)
        assertTrue(firstSelection.isCompleted)
        assertEquals(0f, animation.value, 0.001f)
    }

    private fun createAnimation(scope: CoroutineScope) = DampedDragAnimation(
        animationScope = scope,
        initialValue = 0f,
        valueRange = 0f..3f,
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = 1.04f,
        onDragStarted = {},
        onDragStopped = {},
        onDrag = { _, _ -> },
    )

    private suspend fun advanceFrames(clock: BroadcastFrameClock, start: Int, count: Int) {
        repeat(count) { index ->
            // Let animation children register for the next frame, then deliver
            // a deterministic 60 Hz frame without sleeping or Android's Looper.
            repeat(3) { yield() }
            clock.sendFrame((start + index) * 16_666_667L)
        }
        repeat(3) { yield() }
    }
}
