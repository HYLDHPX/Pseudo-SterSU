package me.weishu.kernelsu.ui.component.rain

import me.weishu.kernelsu.ui.util.SidebarWeatherCondition
import me.weishu.kernelsu.ui.util.SidebarWeatherConfig
import me.weishu.kernelsu.ui.util.SidebarWeatherError
import me.weishu.kernelsu.ui.util.SidebarWeatherReading
import me.weishu.kernelsu.ui.util.SidebarWeatherSource
import me.weishu.kernelsu.ui.util.SidebarWeatherState
import org.junit.Assert.*
import org.junit.Test

class RainRuntimeTest {
    private val now = 1_000_000_000L
    private val config = SidebarWeatherConfig(enabled = true, source = SidebarWeatherSource.Xiaomi, xiaomiAccepted = true)
    private fun state(age: Long = 0, description: String = "rain") = SidebarWeatherState(
        key = config.cacheKey(), reading = SidebarWeatherReading(20.0, description, "Test city", now - age),
    )

    @Test fun acceptedFreshWeatherReachesTheLiveSceneResolver() {
        val snapshot = rainWeatherSnapshot(config, state(), now)
        assertEquals(RainWeatherStatus.Ready, snapshot.status)
        assertEquals(SidebarWeatherCondition.Rain, snapshot.condition)
        val settings = RainSettings(atmosphere = true, atmosphereFollowsWeather = true)
        assertEquals(RainStyle.MediumRain, resolveRainScene(settings, RainTimeOfDay.Night, snapshot.condition))
    }

    @Test fun disablingOrRevokingWeatherNeverUsesCachedReadings() {
        assertEquals(RainWeatherStatus.Disabled, rainWeatherSnapshot(config.copy(enabled = false), state(), now).status)
        val unapproved = rainWeatherSnapshot(config.copy(xiaomiAccepted = false), state(), now)
        assertEquals(RainWeatherStatus.ConsentRequired, unapproved.status)
        assertNull(unapproved.condition)
        val denied = rainWeatherSnapshot(config, state().copy(error = SidebarWeatherError.Permission), now)
        assertEquals(RainWeatherStatus.Failed, denied.status)
        assertNull(denied.condition)
    }

    @Test fun mismatchedStaleFutureAndUnsupportedReadingsFallBackToClock() {
        assertEquals(RainWeatherStatus.Waiting, rainWeatherSnapshot(config, state().copy(key = "another provider"), now).status)
        assertEquals(RainWeatherStatus.Stale, rainWeatherSnapshot(config, state(3_600_001L), now).status)
        assertEquals(RainWeatherStatus.Stale, rainWeatherSnapshot(config, state(-1L), now).status)
        val unsupported = rainWeatherSnapshot(config, state(description = "cloudy"), now)
        assertEquals(RainWeatherStatus.Unsupported, unsupported.status)
        assertNull(unsupported.condition)
        assertEquals(RainStyle.HeavyRain, resolveRainScene(
            RainSettings(atmosphere = true, atmosphereFollowsWeather = true), RainTimeOfDay.Night, unsupported.condition))
    }

    @Test fun failedRefreshCanKeepRecentCacheButNeverAnExpiredOne() {
        assertEquals(RainWeatherStatus.Ready, rainWeatherSnapshot(config, state().copy(error = SidebarWeatherError.Network), now).status)
        assertEquals(RainWeatherStatus.Stale, rainWeatherSnapshot(config, state(3_600_001L).copy(error = SidebarWeatherError.Network), now).status)
        assertEquals(RainWeatherStatus.Failed, rainWeatherSnapshot(config, SidebarWeatherState(key = config.cacheKey(), error = SidebarWeatherError.Network), now).status)
    }

    @Test fun aLongRefreshIntervalGetsABoundedCacheWindow() {
        val slow = config.copy(refreshMinutes = 120)
        assertEquals(RainWeatherStatus.Ready, rainWeatherSnapshot(slow, state(14_400_000L), now).status)
        assertEquals(RainWeatherStatus.Stale, rainWeatherSnapshot(slow, state(14_400_001L), now).status)
    }
}
