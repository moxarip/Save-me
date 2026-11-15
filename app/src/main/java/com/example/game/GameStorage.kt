package com.example.game

import android.content.Context
import android.content.SharedPreferences

class GameStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("draw_to_save_prefs", Context.MODE_PRIVATE)

    fun loadProgress(): PlayerProgress {
        val unlocked = prefs.getInt(KEY_UNLOCKED_LEVEL, 1).coerceIn(1, 20)
        val skinId = prefs.getString(KEY_SELECTED_SKIN, HeroSkin.DOGE.id) ?: HeroSkin.DOGE.id
        val starsMap = mutableMapOf<Int, Int>()
        for (levelId in 1..20) {
            val s = prefs.getInt("$KEY_STARS_PREFIX$levelId", 0)
            if (s > 0) starsMap[levelId] = s.coerceIn(1, 3)
        }
        return PlayerProgress(
            unlockedLevel = unlocked,
            levelStars = starsMap,
            selectedSkin = HeroSkin.fromId(skinId)
        )
    }

    fun saveLevelVictory(levelId: Int, stars: Int): PlayerProgress {
        val current = loadProgress()
        val existingStars = current.levelStars[levelId] ?: 0
        val bestStars = maxOf(existingStars, stars.coerceIn(1, 3))
        val nextUnlocked = maxOf(current.unlockedLevel, (levelId + 1).coerceAtMost(20))

        prefs.edit()
            .putInt("$KEY_STARS_PREFIX$levelId", bestStars)
            .putInt(KEY_UNLOCKED_LEVEL, nextUnlocked)
            .apply()

        return loadProgress()
    }

    fun saveSelectedSkin(skin: HeroSkin): PlayerProgress {
        prefs.edit().putString(KEY_SELECTED_SKIN, skin.id).apply()
        return loadProgress()
    }

    fun loadSettings(): GameSettings {
        val sfx = prefs.getFloat(KEY_SFX_VOL, 0.85f).coerceIn(0f, 1f)
        val buzz = prefs.getFloat(KEY_BUZZ_VOL, 0.70f).coerceIn(0f, 1f)
        val inkId = prefs.getString(KEY_INK_COLOR, InkColorOption.BLACK.id) ?: InkColorOption.BLACK.id
        val shake = prefs.getBoolean(KEY_SCREEN_SHAKE, true)
        return GameSettings(
            sfxVolume = sfx,
            buzzVolume = buzz,
            inkColor = InkColorOption.fromId(inkId),
            screenShakeEnabled = shake
        )
    }

    fun saveSettings(settings: GameSettings) {
        prefs.edit()
            .putFloat(KEY_SFX_VOL, settings.sfxVolume)
            .putFloat(KEY_BUZZ_VOL, settings.buzzVolume)
            .putString(KEY_INK_COLOR, settings.inkColor.id)
            .putBoolean(KEY_SCREEN_SHAKE, settings.screenShakeEnabled)
            .apply()
    }

    companion object {
        private const val KEY_UNLOCKED_LEVEL = "unlocked_level"
        private const val KEY_SELECTED_SKIN = "selected_skin"
        private const val KEY_STARS_PREFIX = "level_stars_"
        private const val KEY_SFX_VOL = "sfx_volume"
        private const val KEY_BUZZ_VOL = "buzz_volume"
        private const val KEY_INK_COLOR = "ink_color"
        private const val KEY_SCREEN_SHAKE = "screen_shake"
    }
}
