package com.example.game

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class AppUiState(
    val route: ScreenRoute = ScreenRoute.SPLASH,
    val splashProgress: Float = 0f,
    val splashReady: Boolean = false,
    val showSettingsModal: Boolean = false,
    val showHint: Boolean = false,
    val showFailDialog: Boolean = false,
    val currentLevelId: Int = 1,
    val levelTitle: String = Levels.getById(1).title,
    val levelSubtitle: String = Levels.getById(1).subtitle,
    val phase: GamePhase = GamePhase.DRAWING,
    val inkRemainingRatio: Float = 1f,
    val inkRemainingPercent: Int = 100,
    val timeRemaining: Float = SIMULATION_DURATION,
    val forbiddenWarningActive: Boolean = false,
    val failReason: FailReason = FailReason.NONE,
    val earnedStars: Int = 0,
    val progress: PlayerProgress = PlayerProgress(),
    val settings: GameSettings = GameSettings()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = GameStorage(application.applicationContext)
    val soundEngine = SoundEngine()

    private val initialProgress = storage.loadProgress()
    private val initialLevel = Levels.getById(initialProgress.unlockedLevel)

    private val _worldState = mutableStateOf(PhysicsEngine.createInitialState(initialLevel))
    val worldState: State<PhysicsWorldState> = _worldState

    private val _uiState = MutableStateFlow(
        AppUiState(
            progress = initialProgress,
            settings = storage.loadSettings(),
            currentLevelId = initialLevel.id,
            levelTitle = initialLevel.title,
            levelSubtitle = initialLevel.subtitle
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var lastMoveTimeNs: Long = 0L
    private var lastMovePos: Vec2? = null

    init {
        val s = _uiState.value.settings
        soundEngine.updateVolumes(s.sfxVolume, s.buzzVolume)
        runSplashLoader()
    }

    private fun runSplashLoader() {
        viewModelScope.launch {
            val steps = 25
            for (i in 1..steps) {
                delay(24L)
                _uiState.update { it.copy(splashProgress = i.toFloat() / steps) }
            }
            _uiState.update { it.copy(splashReady = true) }
        }
    }

    fun startFromSplash() {
        soundEngine.init()
        _uiState.update { it.copy(route = ScreenRoute.MENU) }
    }

    fun navigateTo(route: ScreenRoute) {
        if (route != ScreenRoute.PLAYING) {
            soundEngine.stopBeeBuzz()
        }
        _uiState.update { it.copy(route = route, showSettingsModal = false) }
    }

    fun openPlayCurrentLevel() {
        val levelId = _uiState.value.progress.unlockedLevel.coerceIn(1, 20)
        startLevel(levelId)
    }

    fun startLevel(levelId: Int) {
        val level = Levels.getById(levelId)
        soundEngine.stopAmbientLoop()
        val freshWorld = PhysicsEngine.createInitialState(level)
        if (level.lavaPits.isNotEmpty()) {
            val initialLavaDist = PhysicsEngine.computeClosestLavaDistance(
                level.lavaPits,
                Vec2(freshWorld.hero.x, freshWorld.hero.y)
            )
            soundEngine.updateEnvironmentAudio(0, 999f, initialLavaDist)
            soundEngine.startAmbientLoop()
        }
        _worldState.value = freshWorld
        _uiState.update {
            it.copy(
                route = ScreenRoute.PLAYING,
                currentLevelId = level.id,
                levelTitle = level.title,
                levelSubtitle = level.subtitle,
                showHint = false,
                showFailDialog = false,
                showSettingsModal = false,
                phase = freshWorld.phase,
                inkRemainingRatio = 1f,
                inkRemainingPercent = 100,
                timeRemaining = SIMULATION_DURATION,
                forbiddenWarningActive = false,
                failReason = FailReason.NONE,
                earnedStars = 0
            )
        }
    }

    fun restartLevel() {
        startLevel(_uiState.value.currentLevelId)
    }

    fun nextLevel() {
        val nextId = (_uiState.value.currentLevelId + 1).coerceAtMost(20)
        startLevel(nextId)
    }

    fun toggleHint() {
        _uiState.update { it.copy(showHint = !it.showHint) }
    }

    fun togglePause() {
        val curWorld = _worldState.value
        val nextPhase = when (curWorld.phase) {
            GamePhase.PAUSED -> if (curWorld.polylineBody != null) GamePhase.SIMULATING else GamePhase.DRAWING
            GamePhase.DRAWING, GamePhase.SIMULATING -> GamePhase.PAUSED
            else -> curWorld.phase
        }
        if (nextPhase == GamePhase.PAUSED) {
            soundEngine.stopAmbientLoop()
        } else if (nextPhase == GamePhase.SIMULATING || curWorld.level.lavaPits.isNotEmpty()) {
            soundEngine.startAmbientLoop()
        }
        _worldState.value = curWorld.copy(phase = nextPhase)
        _uiState.update { it.copy(phase = nextPhase) }
    }

    fun openSettings(open: Boolean) {
        _uiState.update { it.copy(showSettingsModal = open) }
    }

    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val updated = transform(_uiState.value.settings)
        storage.saveSettings(updated)
        soundEngine.updateVolumes(updated.sfxVolume, updated.buzzVolume)
        _uiState.update { it.copy(settings = updated) }
    }

    fun selectSkin(skin: HeroSkin) {
        val progress = _uiState.value.progress
        if (progress.totalStars < skin.requiredStars) {
            soundEngine.playLockedSkin()
            return
        }
        soundEngine.playSkinSelect(skin)
        val updated = storage.saveSelectedSkin(skin)
        _uiState.update { it.copy(progress = updated) }
    }

    fun onCanvasPointerDown(worldPos: Vec2) {
        val cur = _worldState.value
        if (_uiState.value.route != ScreenRoute.PLAYING || cur.phase != GamePhase.DRAWING) return
        lastMoveTimeNs = System.nanoTime()
        lastMovePos = worldPos
        val nextWorld = PhysicsEngine.startStroke(worldPos, cur)
        _worldState.value = nextWorld
        syncHudFromWorld(nextWorld)
    }

    fun onCanvasPointerMove(worldPos: Vec2) {
        val cur = _worldState.value
        if (_uiState.value.route != ScreenRoute.PLAYING || cur.phase != GamePhase.DRAWING) return
        val nowNs = System.nanoTime()
        val dtSec = ((nowNs - lastMoveTimeNs) / 1_000_000_000f).coerceAtLeast(0.008f)
        val prevPos = lastMovePos ?: worldPos
        val speed = (worldPos - prevPos).length() / dtSec

        val (nextWorld, added) = PhysicsEngine.extendStroke(worldPos, cur)
        if (added > 1.5f) {
            soundEngine.playScribble(speed)
            lastMoveTimeNs = nowNs
            lastMovePos = worldPos
        }
        if (nextWorld.phase == GamePhase.SIMULATING && cur.phase == GamePhase.DRAWING) {
            soundEngine.startBeeBuzz()
        }
        _worldState.value = nextWorld
        syncHudFromWorld(nextWorld)
    }

    fun onCanvasPointerUp() {
        val cur = _worldState.value
        if (_uiState.value.route != ScreenRoute.PLAYING || cur.phase != GamePhase.DRAWING) return
        val nextWorld = PhysicsEngine.finalizeStroke(cur)
        if (nextWorld.phase == GamePhase.SIMULATING) {
            soundEngine.startBeeBuzz()
        }
        _worldState.value = nextWorld
        syncHudFromWorld(nextWorld)
    }

    fun onVSyncFrame(dt: Float) {
        val curHud = _uiState.value
        if (curHud.route != ScreenRoute.PLAYING) return

        val curWorld = _worldState.value
        val (nextWorld, events) = PhysicsEngine.step(curWorld, dt)
        _worldState.value = nextWorld

        if (events.bounceCount > 0 || events.springBounce) {
            soundEngine.playBounce(isSpring = events.springBounce)
        }
        if (events.urgentTickSecond != null) {
            soundEngine.playTickTock(events.urgentTickSecond)
        }
        if (nextWorld.phase == GamePhase.SIMULATING || nextWorld.phase == GamePhase.DRAWING) {
            soundEngine.updateEnvironmentAudio(
                beeCount = if (nextWorld.phase == GamePhase.SIMULATING) nextWorld.bees.size else 0,
                closestBeeDist = events.closestBeeDistance,
                closestLavaDist = events.closestLavaDistance
            )
            if (nextWorld.phase == GamePhase.SIMULATING && events.closestLavaDistance < 185f) {
                soundEngine.maybePlayLavaBubble(events.closestLavaDistance)
            }
        }

        var updatedProgress = curHud.progress
        var showFail = curHud.showFailDialog

        if (events.justSucceeded) {
            soundEngine.stopAmbientLoop()
            soundEngine.playVictory()
            updatedProgress = storage.saveLevelVictory(
                levelId = nextWorld.level.id,
                stars = nextWorld.earnedStars
            )
        } else if (events.justFailed != null) {
            soundEngine.stopAmbientLoop()
            soundEngine.playFail(events.justFailed)
        }

        if (nextWorld.phase == GamePhase.FAILED && nextWorld.slowMoRemaining <= 0f && !showFail) {
            showFail = true
        }

        syncHudFromWorld(nextWorld, updatedProgress, showFail)
    }

    private fun syncHudFromWorld(
        world: PhysicsWorldState,
        newProgress: PlayerProgress? = null,
        showFail: Boolean? = null
    ) {
        val hud = _uiState.value
        val nextTenths = ceil((world.timeRemaining * 10f).toDouble()).toInt()
        val prevTenths = ceil((hud.timeRemaining * 10f).toDouble()).toInt()
        val warnActive = world.forbiddenWarningTimer > 0f
        val progressChanged = newProgress != null && newProgress != hud.progress
        val failDialogChanged = showFail != null && showFail != hud.showFailDialog

        if (hud.phase != world.phase ||
            hud.inkRemainingPercent != world.inkRemainingPercent ||
            nextTenths != prevTenths ||
            hud.forbiddenWarningActive != warnActive ||
            hud.earnedStars != world.earnedStars ||
            hud.failReason != world.failReason ||
            progressChanged ||
            failDialogChanged
        ) {
            _uiState.update {
                it.copy(
                    phase = world.phase,
                    inkRemainingRatio = world.inkRemainingRatio,
                    inkRemainingPercent = world.inkRemainingPercent,
                    timeRemaining = world.timeRemaining,
                    forbiddenWarningActive = warnActive,
                    failReason = world.failReason,
                    earnedStars = world.earnedStars,
                    progress = newProgress ?: it.progress,
                    showFailDialog = showFail ?: it.showFailDialog
                )
            }
        }
    }

    fun onPauseLifecycle() {
        soundEngine.stopAmbientLoop()
    }

    fun onResumeLifecycle() {
        val world = _worldState.value
        if (_uiState.value.route == ScreenRoute.PLAYING &&
            (world.phase == GamePhase.SIMULATING || (world.phase == GamePhase.DRAWING && world.level.lavaPits.isNotEmpty()))
        ) {
            soundEngine.startAmbientLoop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }
}
