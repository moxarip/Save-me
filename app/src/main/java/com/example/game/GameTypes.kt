package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.hypot

const val WORLD_WIDTH = 420f
const val WORLD_HEIGHT = 760f
const val HERO_RADIUS = 24f
const val BEE_RADIUS = 9f
const val LINE_RADIUS = 6.5f
const val SAMPLE_STEP = 8f
const val SIMULATION_DURATION = 10.0f

data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)
    fun length(): Float = hypot(x, y)
    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun cross(other: Vec2): Float = x * other.y - y * other.x
    fun normalized(): Vec2 {
        val len = length()
        return if (len > 1e-5f) Vec2(x / len, y / len) else Vec2(0f, 0f)
    }
}

enum class ScreenRoute {
    SPLASH,
    MENU,
    PLAYING,
    LEVELS,
    SKINS
}

enum class GamePhase {
    DRAWING,
    SIMULATING,
    PAUSED,
    VICTORY,
    FAILED
}

enum class FailReason(val arabicMessage: String) {
    NONE(""),
    BEE_STING("لسعتك النحلة!"),
    LAVA_BURN("سقط البطل في الحمم!"),
    BOULDER_CRUSH("سحقتك الصخرة!"),
    OUT_OF_BOUNDS("سقط البطل خارج المرحلة!")
}

enum class HeroSkin(
    val id: String,
    val arabicName: String,
    val subtitle: String,
    val requiredStars: Int
) {
    DOGE("doge", "الكلب المضحك", "لطيف وهادئ... حتى يظهر النحل!", 0),
    NEON_CAT("neon_cat", "القطة النيون", "مضيئة وسريعة التوتر!", 6),
    ESPRESSO_CUP("espresso_cup", "كوب القهوة المتوتر", "مليء بالكافيين والقلق!", 15),
    CROWN_FROG("crown_frog", "الضفدع الملكي", "ملك البركة بلا منازع!", 30);

    companion object {
        fun fromId(id: String): HeroSkin = entries.firstOrNull { it.id == id } ?: DOGE
    }
}

enum class InkColorOption(
    val id: String,
    val arabicName: String,
    val strokeColor: Color,
    val outlineColor: Color,
    val highlightColor: Color
) {
    BLACK(
        "black",
        "أسود حبر",
        Color(0xFF232733),
        Color(0xFF0D0F14),
        Color(0xFF525A70)
    ),
    NEON_BLUE(
        "neon_blue",
        "أزرق نيون",
        Color(0xFF00B4FF),
        Color(0xFF00538A),
        Color(0xFF8BE4FF)
    ),
    GOLD(
        "gold",
        "ذهبي",
        Color(0xFFFFB300),
        Color(0xFF8C5300),
        Color(0xFFFFE57F)
    ),
    RED(
        "red",
        "أحمر",
        Color(0xFFEF4444),
        Color(0xFF7F1D1D),
        Color(0xFFFCA5A5)
    );

    companion object {
        fun fromId(id: String): InkColorOption = entries.firstOrNull { it.id == id } ?: BLACK
    }
}

enum class PlatformStyle {
    GRASS,
    STONE,
    WOOD_BRIDGE
}

data class Platform(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val style: PlatformStyle = PlatformStyle.GRASS
)

data class LavaPit(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class BeehiveSpec(
    val x: Float,
    val y: Float,
    val beeCount: Int,
    val forbiddenRadius: Float = 38f
)

data class BoulderSpec(
    val x: Float,
    val y: Float,
    val radius: Float = 22f,
    val initialVx: Float = 0f,
    val delaySeconds: Float = 0.25f
)

data class SpringPadSpec(
    val x: Float,
    val y: Float,
    val width: Float = 64f,
    val height: Float = 18f,
    val boostVelocity: Float = 640f
)

enum class SkyTheme {
    SUNNY_MEADOW,
    VOLCANIC_SUNSET,
    TWILIGHT_SWARM,
    ROCKY_CANYON,
    MOLTEN_STORM
}

data class LevelData(
    val id: Int,
    val title: String,
    val subtitle: String,
    val heroStart: Vec2,
    val platforms: List<Platform>,
    val lavaPits: List<LavaPit> = emptyList(),
    val beehives: List<BeehiveSpec>,
    val boulders: List<BoulderSpec> = emptyList(),
    val springs: List<SpringPadSpec> = emptyList(),
    val maxInkLength: Float,
    val skyTheme: SkyTheme,
    val hintPath: List<Vec2>
)

data class DrawnPolylineBody(
    val localPoints: List<Vec2>,
    val centerX: Float,
    val centerY: Float,
    val angle: Float,
    val vx: Float,
    val vy: Float,
    val omega: Float,
    val mass: Float,
    val inertia: Float,
    val totalLength: Float
)

data class HeroState(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val radius: Float = HERO_RADIUS,
    val scaredLevel: Float = 0f,
    val isHurt: Boolean = false,
    val hurtAngle: Float = 0f,
    val squashX: Float = 1f,
    val squashY: Float = 1f,
    val lookDirX: Float = 0f,
    val lookDirY: Float = 0f
)

data class BeeState(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float = BEE_RADIUS,
    val wingPhase: Float,
    val wanderSeed: Float,
    val bounceCooldown: Float = 0f,
    val visualAngle: Float = 0f
)

data class BoulderState(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val angle: Float = 0f,
    val delayRemaining: Float = 0f
)

data class SpringRuntimeState(
    val spec: SpringPadSpec,
    val compression: Float = 0f
)

enum class ParticleKind {
    SPARK,
    SWEAT,
    CONFETTI,
    DUST,
    LAVA_BUBBLE,
    EMBER,
    RING
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Color,
    val kind: ParticleKind,
    val rotation: Float = 0f
)

data class GameSettings(
    val sfxVolume: Float = 0.85f,
    val buzzVolume: Float = 0.70f,
    val inkColor: InkColorOption = InkColorOption.BLACK,
    val screenShakeEnabled: Boolean = true
)

data class PlayerProgress(
    val unlockedLevel: Int = 1,
    val levelStars: Map<Int, Int> = emptyMap(),
    val selectedSkin: HeroSkin = HeroSkin.DOGE
) {
    val totalStars: Int get() = levelStars.values.sum()
}

data class PhysicsWorldState(
    val level: LevelData,
    val phase: GamePhase = GamePhase.DRAWING,
    val timeRemaining: Float = SIMULATION_DURATION,
    val elapsedTime: Float = 0f,
    val rawPoints: List<Vec2> = emptyList(),
    val inkUsedLength: Float = 0f,
    val polylineBody: DrawnPolylineBody? = null,
    val hero: HeroState,
    val bees: List<BeeState> = emptyList(),
    val pendingBeeSpawns: List<PendingBeeSpawn> = emptyList(),
    val boulders: List<BoulderState> = emptyList(),
    val springs: List<SpringRuntimeState> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val failReason: FailReason = FailReason.NONE,
    val slowMoRemaining: Float = 0f,
    val shakeIntensity: Float = 0f,
    val forbiddenWarningTimer: Float = 0f,
    val earnedStars: Int = 0,
    val isActivelyDrawing: Boolean = false
) {
    val inkRemainingRatio: Float
        get() = ((level.maxInkLength - inkUsedLength) / level.maxInkLength).coerceIn(0f, 1f)

    val inkRemainingPercent: Int
        get() = (inkRemainingRatio * 100f).toInt().coerceIn(0, 100)
}

data class PendingBeeSpawn(
    val hiveIndex: Int,
    val x: Float,
    val y: Float,
    val spawnDelay: Float,
    val beeId: Int
)
