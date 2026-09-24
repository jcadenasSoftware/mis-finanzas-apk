package com.jcadenas.xpendz.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoalAchievementTrackerTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var tracker: GoalAchievementTracker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("achievement-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        tracker = GoalAchievementTracker(prefs)
    }

    @Test
    fun firstAchievementTriggersCongratulation() {
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))
    }

    @Test
    fun sustainedAchievementDoesNotRepeat() {
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))
        assertFalse(tracker.onProgressEvaluated("g1", achieved = true))
        assertFalse(tracker.onProgressEvaluated("g1", achieved = true))
    }

    @Test
    fun belowTargetDoesNotCongratulate() {
        assertFalse(tracker.onProgressEvaluated("g1", achieved = false))
    }

    @Test
    fun dropBelowTargetAndReachAgainCongratulatesAgain() {
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))
        assertFalse(tracker.onProgressEvaluated("g1", achieved = false))
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))
        assertFalse(tracker.onProgressEvaluated("g1", achieved = true))
    }

    @Test
    fun achievementStatePersistsAcrossInstances() {
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))

        val newTracker = GoalAchievementTracker(prefs)
        assertFalse(newTracker.onProgressEvaluated("g1", achieved = true))
        assertFalse(newTracker.onProgressEvaluated("g1", achieved = false))
        assertTrue(newTracker.onProgressEvaluated("g1", achieved = true))
    }

    @Test
    fun goalsAreTrackedIndependently() {
        assertTrue(tracker.onProgressEvaluated("g1", achieved = true))
        assertTrue(tracker.onProgressEvaluated("g2", achieved = true))
        assertFalse(tracker.onProgressEvaluated("g1", achieved = true))
    }
}
