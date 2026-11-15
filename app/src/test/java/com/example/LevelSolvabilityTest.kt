package com.example

import com.example.game.FailReason
import com.example.game.GamePhase
import com.example.game.HeroSkin
import com.example.game.Levels
import com.example.game.PhysicsEngine
import com.example.game.SoundEngine
import com.example.game.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSolvabilityTest {

    @Test
    fun allTwentyLevelsAreSolvableWithHintPath() {
        assertEquals(20, Levels.all.size)

        for (level in Levels.all) {
            var state = PhysicsEngine.createInitialState(level)
            val hint = level.hintPath
            assertTrue("Level ${level.id} hintPath must have >= 2 points", hint.size >= 2)

            state = PhysicsEngine.startStroke(hint.first(), state)
            for (i in 1 until hint.size) {
                val (next, _) = PhysicsEngine.extendStroke(hint[i], state)
                state = next
            }
            if (state.phase == GamePhase.DRAWING) {
                state = PhysicsEngine.finalizeStroke(state)
            }
            assertEquals(
                "Level ${level.id} should enter SIMULATING after drawing hint",
                GamePhase.SIMULATING,
                state.phase
            )

            // Simulate 10.3 seconds at 60 FPS
            repeat(620) {
                if (state.phase == GamePhase.SIMULATING) {
                    state = PhysicsEngine.step(state, 1f / 60f).first
                }
            }

            assertEquals(
                "Level ${level.id} (${level.title}) failed with hint! failReason=${state.failReason}, timeRem=${state.timeRemaining}",
                GamePhase.VICTORY,
                state.phase
            )
            assertTrue(
                "Level ${level.id} hint should earn >= 2 stars (earned=${state.earnedStars}, ink=${state.inkRemainingPercent}%)",
                state.earnedStars >= 2
            )
        }
    }

    @Test
    fun lavaProximityDistanceReflectsHeroDistanceToLava() {
        val safeLevel = Levels.getById(1)
        val safeState = PhysicsEngine.createInitialState(safeLevel)
        val (_, safeEvents) = PhysicsEngine.step(safeState, 1f / 60f)
        assertEquals(999f, safeEvents.closestLavaDistance, 0.01f)

        val lavaLevel = Levels.getById(5)
        val pit = lavaLevel.lavaPits.first()
        val nearLava = PhysicsEngine.computeClosestLavaDistance(
            lavaLevel.lavaPits,
            Vec2(pit.x + pit.width / 2f, pit.y - 40f)
        )
        assertEquals(40f, nearLava, 0.5f)
    }

    @Test
    fun soundEngineProceduralAudioMethodsAreSafe() {
        val soundEngine = SoundEngine()
        soundEngine.init()
        HeroSkin.entries.forEach { skin ->
            soundEngine.playSkinSelect(skin)
        }
        soundEngine.playLockedSkin()
        soundEngine.maybePlayLavaBubble(80f)
        soundEngine.playFail(FailReason.LAVA_BURN)
        soundEngine.updateEnvironmentAudio(beeCount = 5, closestBeeDist = 110f, closestLavaDist = 90f)
        soundEngine.stopAmbientLoop()
        soundEngine.release()
    }
}

