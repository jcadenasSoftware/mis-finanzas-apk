package com.jcadenas.xpendz.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalAchievementTracker @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun onProgressEvaluated(goalId: String, achieved: Boolean): Boolean {
        val key = keyFor(goalId)
        val wasAchieved = sharedPreferences.getBoolean(key, false)
        if (achieved == wasAchieved) return false
        sharedPreferences.edit().putBoolean(key, achieved).apply()
        return achieved
    }

    private fun keyFor(goalId: String) = "goal_achieved_$goalId"
}
