package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.AppUiState
import com.example.game.GamePhase
import com.example.game.GameRenderer
import com.example.game.GameRenderer.drawHero
import com.example.game.GameRenderer.drawMenuScene
import com.example.game.GameRenderer.renderWorld
import com.example.game.GameSettings
import com.example.game.GameViewModel
import com.example.game.HeroSkin
import com.example.game.HeroState
import com.example.game.InkColorOption
import com.example.game.Levels
import com.example.game.ScreenRoute
import com.example.ui.theme.CardWhite
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GrassGreen
import com.example.ui.theme.HoneyCream
import com.example.ui.theme.HoneyDark
import com.example.ui.theme.HoneyGold
import com.example.ui.theme.InkDark
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.StarYellow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DrawToSaveApp(
    uiState: AppUiState,
    viewModel: GameViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState.route) {
            ScreenRoute.SPLASH -> SplashScreen(
                progress = uiState.splashProgress,
                ready = uiState.splashReady,
                skin = uiState.progress.selectedSkin,
                onStart = { viewModel.startFromSplash() }
            )

            ScreenRoute.MENU -> MainMenuScreen(
                uiState = uiState,
                onPlay = { viewModel.openPlayCurrentLevel() },
                onLevels = { viewModel.navigateTo(ScreenRoute.LEVELS) },
                onSkins = { viewModel.navigateTo(ScreenRoute.SKINS) },
                onSettings = { viewModel.openSettings(true) }
            )

            ScreenRoute.LEVELS -> LevelSelectScreen(
                uiState = uiState,
                onBack = { viewModel.navigateTo(ScreenRoute.MENU) },
                onSelectLevel = { id -> viewModel.startLevel(id) }
            )

            ScreenRoute.SKINS -> SkinsGalleryScreen(
                uiState = uiState,
                onBack = { viewModel.navigateTo(ScreenRoute.MENU) },
                onSelectSkin = { skin -> viewModel.selectSkin(skin) }
            )

            ScreenRoute.PLAYING -> GameplayScreen(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        if (uiState.showSettingsModal) {
            SettingsDialog(
                settings = uiState.settings,
                onUpdate = { viewModel.updateSettings(it) },
                onDismiss = { viewModel.openSettings(false) }
            )
        }
    }
}

@Composable
private fun SplashScreen(
    progress: Float,
    ready: Boolean,
    skin: HeroSkin,
    onStart: () -> Unit
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNs = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                elapsed += ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNs = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFFBAE6FD), HoneyCream)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated hero emblem
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)),
                border = BorderStroke(3.dp, InkDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.size(144.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform({
                        translate(size.width / 2f, size.height / 2f + 10f)
                        scale(1.9f, 1.9f, Offset.Zero)
                    }) {
                        drawHero(
                            hero = HeroState(x = 0f, y = 0f),
                            skin = skin,
                            phase = GamePhase.VICTORY,
                            elapsed = elapsed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ارسم للإنقاذ",
                style = MaterialTheme.typography.displayLarge,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "تحدي النحل",
                style = MaterialTheme.typography.headlineMedium,
                color = HoneyDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ارسم درعك الذكي واحمِ البطل لمدة ١٠ ثوانٍ!",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, InkDark, RoundedCornerShape(8.dp)),
                color = HoneyGold,
                trackColor = Color.White,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = ready, enter = fadeIn() + scaleIn()) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("splash_start_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoneyGold,
                        contentColor = InkDark
                    ),
                    border = BorderStroke(2.5.dp, InkDark),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "بدء")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ابدأ اللعب",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    uiState: AppUiState,
    onPlay: () -> Unit,
    onLevels: () -> Unit,
    onSkins: () -> Unit,
    onSettings: () -> Unit
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNs = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                elapsed += ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNs = now
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Live animated background scene
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMenuScene(elapsed = elapsed, skin = uiState.progress.selectedSkin)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Total stars + Settings
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardWhite.copy(alpha = 0.94f),
                    border = BorderStroke(2.dp, InkDark),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "النجوم",
                            tint = StarYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${uiState.progress.totalStars} / 60",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkDark
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(50.dp)
                        .border(2.dp, InkDark, CircleShape)
                        .testTag("menu_settings_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = CardWhite.copy(alpha = 0.94f),
                        contentColor = InkDark
                    )
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Game Logo Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite.copy(alpha = 0.92f)),
                border = BorderStroke(3.dp, InkDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ارسم للإنقاذ",
                        style = MaterialTheme.typography.displayLarge,
                        color = InkDark
                    )
                    Text(
                        text = "تحدي النحل • ٢٠ مرحلة",
                        style = MaterialTheme.typography.titleMedium,
                        color = HoneyDark
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Menu Action Buttons
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .testTag("menu_play_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoneyGold,
                        contentColor = InkDark
                    ),
                    border = BorderStroke(2.5.dp, InkDark),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "العب الآن", modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "العب الآن (المرحلة ${uiState.progress.unlockedLevel})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onLevels,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("menu_levels_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBlue,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = "المراحل")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("المراحل", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = onSkins,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("menu_skins_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GrassGreen,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Icon(Icons.Filled.Face, contentDescription = "الشخصيات")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الشخصيات", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelSelectScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onSelectLevel: (Int) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HoneyCream)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, InkDark, CircleShape)
                    .testTag("levels_back_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = CardWhite,
                    contentColor = InkDark
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }

            Text(
                text = "اختر المرحلة",
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                border = BorderStroke(2.dp, InkDark)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Star, contentDescription = "النجوم", tint = StarYellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${uiState.progress.totalStars}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Levels.all, key = { it.id }) { level ->
                val isUnlocked = level.id <= uiState.progress.unlockedLevel
                val stars = uiState.progress.levelStars[level.id] ?: 0
                Card(
                    modifier = Modifier
                        .aspectRatio(0.92f)
                        .testTag("level_tile_${level.id}")
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = isUnlocked) { onSelectLevel(level.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) CardWhite else Color(0xFFE7E2D8)
                    ),
                    border = BorderStroke(
                        width = if (level.id == uiState.progress.unlockedLevel) 2.5.dp else 1.8.dp,
                        color = if (isUnlocked) InkDark else Color(0xFFB8B0A2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 4.dp else 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!isUnlocked) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "مقفلة",
                                tint = Color(0xFF78716C),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${level.id}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF78716C)
                            )
                        } else {
                            Text(
                                text = "${level.id}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = InkDark
                            )
                            Text(
                                text = level.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.Center) {
                                for (s in 1..3) {
                                    Icon(
                                        imageVector = if (s <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = null,
                                        tint = if (s <= stars) StarYellow else Color(0xFFD6CFC2),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinsGalleryScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onSelectSkin: (HeroSkin) -> Unit
) {
    BackHandler(onBack = onBack)

    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNs = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                elapsed += ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNs = now
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HoneyCream)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, InkDark, CircleShape)
                    .testTag("skins_back_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = CardWhite,
                    contentColor = InkDark
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }

            Text(
                text = "شخصيات البطل",
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                border = BorderStroke(2.dp, InkDark)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Star, contentDescription = "النجوم", tint = StarYellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${uiState.progress.totalStars}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroSkin.entries.forEach { skin ->
                val unlocked = uiState.progress.totalStars >= skin.requiredStars
                val selected = uiState.progress.selectedSkin == skin

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skin_card_${skin.id}")
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelectSkin(skin) },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Color(0xFFFFF3C4) else CardWhite
                    ),
                    border = BorderStroke(
                        width = if (selected) 3.dp else 2.dp,
                        color = if (selected) HoneyDark else InkDark
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFE0F2FE))
                                .border(2.dp, InkDark, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                withTransform({
                                    translate(size.width / 2f, size.height / 2f + 6f)
                                    scale(1.1f, 1.1f, Offset.Zero)
                                }) {
                                    drawHero(
                                        hero = HeroState(x = 0f, y = 0f),
                                        skin = skin,
                                        phase = if (selected) GamePhase.VICTORY else GamePhase.DRAWING,
                                        elapsed = elapsed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = skin.arabicName,
                                style = MaterialTheme.typography.titleLarge,
                                color = InkDark
                            )
                            Text(
                                text = skin.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (unlocked) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) GrassGreen else Color(0xFFE2E8F0)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = if (selected) "مُختار حالياً" else "اضغط للاختيار",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) Color.White else InkDark
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = DangerRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "يتطلب ${skin.requiredStars} نجمة",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = DangerRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameplayScreen(
    uiState: AppUiState,
    viewModel: GameViewModel
) {
    LaunchedEffect(uiState.currentLevelId) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                    viewModel.onVSyncFrame(dt)
                }
                lastNs = now
            }
        }
    }

    BackHandler {
        if (uiState.phase == GamePhase.DRAWING || uiState.phase == GamePhase.SIMULATING) {
            viewModel.togglePause()
        } else {
            viewModel.navigateTo(ScreenRoute.MENU)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen physics Canvas (reads viewModel.worldState.value ONLY inside DrawScope)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_canvas")
                    .pointerInput(uiState.currentLevelId, uiState.phase) {
                        if (uiState.phase != GamePhase.DRAWING) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val transform = GameRenderer.computeTransform(
                                androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                            )
                            viewModel.onCanvasPointerDown(transform.screenToWorld(down.position))
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    viewModel.onCanvasPointerUp()
                                    break
                                } else {
                                    viewModel.onCanvasPointerMove(transform.screenToWorld(change.position))
                                    change.consume()
                                }
                            }
                        }
                    }
            ) {
                renderWorld(
                    state = viewModel.worldState.value,
                    skin = uiState.progress.selectedSkin,
                    inkOption = uiState.settings.inkColor,
                    showHint = uiState.showHint,
                    screenShakeEnabled = uiState.settings.screenShakeEnabled
                )
            }
        }

        // Top HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Level badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite.copy(alpha = 0.94f),
                    border = BorderStroke(2.dp, InkDark),
                    shadowElevation = 3.dp
                ) {
                    Text(
                        text = "المرحلة ${uiState.currentLevelId}",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkDark,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                // Live Ink Progress Bar with 3-Star markers (40% & 70%)
                InkProgressMeter(
                    inkRatio = uiState.inkRemainingRatio,
                    inkPercent = uiState.inkRemainingPercent,
                    modifier = Modifier.weight(1f)
                )

                // Quick Restart
                FilledTonalIconButton(
                    onClick = { viewModel.restartLevel() },
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, InkDark, CircleShape)
                        .testTag("hud_restart_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = CardWhite.copy(alpha = 0.94f),
                        contentColor = InkDark
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "إعادة سريعة")
                }

                // Hint Toggle
                FilledTonalIconButton(
                    onClick = { viewModel.toggleHint() },
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, InkDark, CircleShape)
                        .testTag("hud_hint_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (uiState.showHint) HoneyGold else CardWhite.copy(alpha = 0.94f),
                        contentColor = InkDark
                    )
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = "تلميح")
                }

                // Pause
                FilledTonalIconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, InkDark, CircleShape)
                        .testTag("hud_pause_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = CardWhite.copy(alpha = 0.94f),
                        contentColor = InkDark
                    )
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "إيقاف مؤقت")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simulation Countdown Timer OR Drawing Instruction Banner
            if (uiState.phase == GamePhase.SIMULATING) {
                CountdownBadge(secondsRemaining = uiState.timeRemaining)
            } else if (uiState.phase == GamePhase.DRAWING) {
                val warn = uiState.forbiddenWarningActive
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (warn) DangerRed else CardWhite.copy(alpha = 0.88f),
                    border = BorderStroke(2.dp, InkDark),
                    shadowElevation = 3.dp
                ) {
                    Text(
                        text = if (warn) "لا ترسم داخل الدوائر الحمراء!" else uiState.levelSubtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (warn) Color.White else InkDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Pause Modal
        if (uiState.phase == GamePhase.PAUSED) {
            PauseDialog(
                levelNumber = uiState.currentLevelId,
                onResume = { viewModel.togglePause() },
                onRestart = { viewModel.restartLevel() },
                onSettings = { viewModel.openSettings(true) },
                onMenu = { viewModel.navigateTo(ScreenRoute.MENU) }
            )
        }

        // Victory Modal
        if (uiState.phase == GamePhase.VICTORY) {
            VictoryDialog(
                levelId = uiState.currentLevelId,
                stars = uiState.earnedStars,
                inkPercent = uiState.inkRemainingPercent,
                hasNextLevel = uiState.currentLevelId < 20,
                onNext = { viewModel.nextLevel() },
                onReplay = { viewModel.restartLevel() },
                onMenu = { viewModel.navigateTo(ScreenRoute.MENU) }
            )
        }

        // Fail Modal (after brief slow-motion reaction)
        if (uiState.showFailDialog && uiState.phase == GamePhase.FAILED) {
            FailDialog(
                reasonText = uiState.failReason.arabicMessage,
                onRetry = { viewModel.restartLevel() },
                onHintAndRetry = {
                    viewModel.restartLevel()
                    viewModel.toggleHint()
                },
                onMenu = { viewModel.navigateTo(ScreenRoute.MENU) }
            )
        }
    }
}

@Composable
private fun InkProgressMeter(
    inkRatio: Float,
    inkPercent: Int,
    modifier: Modifier = Modifier
) {
    val activeStars = when {
        inkPercent > 70 -> 3
        inkPercent > 40 -> 2
        inkPercent > 0 -> 1
        else -> 0
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CardWhite.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, InkDark),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الحبر $inkPercent%",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = InkDark
                )
                Row {
                    for (s in 1..3) {
                        Icon(
                            imageVector = if (s <= activeStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (s <= activeStars) StarYellow else Color(0xFFCBD5E1),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE2E8F0))
                    .border(1.2.dp, InkDark, RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(inkRatio)
                        .fillMaxHeight()
                        .background(
                            when {
                                inkPercent > 70 -> GrassGreen
                                inkPercent > 40 -> HoneyGold
                                else -> DangerRed
                            }
                        )
                )
                // Threshold tick marks at 40% and 70%
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(0.4f))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(InkDark.copy(alpha = 0.65f)))
                    Spacer(modifier = Modifier.weight(0.3f))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(InkDark.copy(alpha = 0.65f)))
                    Spacer(modifier = Modifier.weight(0.3f))
                }
            }
        }
    }
}

@Composable
private fun CountdownBadge(secondsRemaining: Float) {
    val urgent = secondsRemaining <= 3.05f
    val infinite = rememberInfiniteTransition(label = "countdown_pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (urgent) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = Modifier.scale(pulseScale),
        shape = RoundedCornerShape(24.dp),
        color = if (urgent) DangerRed else CardWhite.copy(alpha = 0.95f),
        border = BorderStroke(2.5.dp, InkDark),
        shadowElevation = 6.dp
    ) {
        Text(
            text = String.format(Locale.US, "%.1f", secondsRemaining),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            ),
            color = if (urgent) Color.White else InkDark,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VictoryDialog(
    levelId: Int,
    stars: Int,
    inkPercent: Int,
    hasNextLevel: Boolean,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onMenu: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(3.dp, InkDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("أحسنت!", style = MaterialTheme.typography.displayLarge, color = GrassGreen)
                Text(
                    text = "تم إنقاذ البطل في المرحلة $levelId",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in 1..3) {
                        Icon(
                            imageVector = if (s <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (s <= stars) StarYellow else Color(0xFFD1D5DB),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "الحبر المتبقي: $inkPercent%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (hasNextLevel) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("victory_next_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HoneyGold,
                            contentColor = InkDark
                        ),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Text("المرحلة التالية", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("victory_replay_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Text("إعادة", color = InkDark)
                    }
                    OutlinedButton(
                        onClick = onMenu,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("victory_menu_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Text("القائمة", color = InkDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailDialog(
    reasonText: String,
    onRetry: () -> Unit,
    onHintAndRetry: () -> Unit,
    onMenu: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(3.dp, InkDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("حاول مجدداً!", style = MaterialTheme.typography.headlineMedium, color = DangerRed)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("fail_retry_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoneyGold,
                        contentColor = InkDark
                    ),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة المحاولة", style = MaterialTheme.typography.titleLarge)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onHintAndRetry,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("fail_hint_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = HoneyDark)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تلميح", color = InkDark)
                    }
                    OutlinedButton(
                        onClick = onMenu,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("fail_menu_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, InkDark)
                    ) {
                        Text("القائمة", color = InkDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseDialog(
    levelNumber: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(3.dp, InkDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("إيقاف مؤقت", style = MaterialTheme.typography.headlineMedium, color = InkDark)
                Text("المرحلة $levelNumber", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pause_resume_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HoneyGold, contentColor = InkDark),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Text("استئناف", style = MaterialTheme.typography.titleLarge)
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_restart_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Text("إعادة المرحلة", color = InkDark)
                }

                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_settings_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Text("الإعدادات", color = InkDark)
                }

                OutlinedButton(
                    onClick = onMenu,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_menu_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Text("القائمة الرئيسية", color = InkDark)
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(3.dp, InkDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "الإعدادات",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkDark,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // SFX Volume
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المؤثرات الصوتية", style = MaterialTheme.typography.titleMedium)
                        Text("${(settings.sfxVolume * 100).toInt()}%", color = TextSecondary)
                    }
                    Slider(
                        value = settings.sfxVolume,
                        onValueChange = { v -> onUpdate { it.copy(sfxVolume = v) } },
                        colors = SliderDefaults.colors(thumbColor = HoneyDark, activeTrackColor = HoneyGold)
                    )
                }

                // Bee Buzz Volume
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("صوت طنين النحل", style = MaterialTheme.typography.titleMedium)
                        Text("${(settings.buzzVolume * 100).toInt()}%", color = TextSecondary)
                    }
                    Slider(
                        value = settings.buzzVolume,
                        onValueChange = { v -> onUpdate { it.copy(buzzVolume = v) } },
                        colors = SliderDefaults.colors(thumbColor = HoneyDark, activeTrackColor = HoneyGold)
                    )
                }

                // Ink Color Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لون الحبر", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InkColorOption.entries.forEach { option ->
                            val selected = settings.inkColor == option
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Color(0xFFFFF3C4) else Color(0xFFF3F4F6))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.2.dp,
                                        color = if (selected) InkDark else Color(0xFFD1D5DB),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onUpdate { it.copy(inkColor = option) } }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(option.strokeColor)
                                        .border(2.dp, InkDark, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = option.arabicName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Screen Shake Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اهتزاز الشاشة", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = settings.screenShakeEnabled,
                        onCheckedChange = { enabled -> onUpdate { it.copy(screenShakeEnabled = enabled) } }
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_close_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HoneyGold, contentColor = InkDark),
                    border = BorderStroke(2.dp, InkDark)
                ) {
                    Text("حفظ وإغلاق", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
