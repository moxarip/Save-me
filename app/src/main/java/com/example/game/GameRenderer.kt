package com.example.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object GameRenderer {

    data class ViewportTransform(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    ) {
        fun screenToWorld(screenPos: Offset): Vec2 {
            return Vec2(
                (screenPos.x - offsetX) / scale,
                (screenPos.y - offsetY) / scale
            )
        }
    }

    fun computeTransform(canvasSize: Size): ViewportTransform {
        val scaleX = canvasSize.width / WORLD_WIDTH
        val scaleY = canvasSize.height / WORLD_HEIGHT
        val scale = minOf(scaleX, scaleY)
        val offsetX = (canvasSize.width - WORLD_WIDTH * scale) * 0.5f
        val offsetY = (canvasSize.height - WORLD_HEIGHT * scale) * 0.5f
        return ViewportTransform(scale, offsetX, offsetY)
    }

    fun DrawScope.renderWorld(
        state: PhysicsWorldState,
        skin: HeroSkin,
        inkOption: InkColorOption,
        showHint: Boolean,
        screenShakeEnabled: Boolean
    ) {
        val transform = computeTransform(size)
        val shakeMag = if (screenShakeEnabled) state.shakeIntensity else 0f
        val shakeX = if (shakeMag > 0.2f) sin(state.elapsedTime * 75f) * shakeMag else 0f
        val shakeY = if (shakeMag > 0.2f) cos(state.elapsedTime * 62f) * shakeMag else 0f

        drawSkyBackdrop(state.level.skyTheme, state.elapsedTime, size, isFullScreen = true)

        withTransform({
            translate(transform.offsetX + shakeX * transform.scale, transform.offsetY + shakeY * transform.scale)
            scale(transform.scale, transform.scale, Offset.Zero)
        }) {
            drawDistantScenery(state.level.skyTheme, state.elapsedTime)
            drawClouds(state.elapsedTime, state.level.skyTheme)

            // Lava ambient glow behind platforms
            for (lava in state.level.lavaPits) {
                drawLavaAmbientGlow(lava, state.elapsedTime)
            }

            // Platforms
            for (plat in state.level.platforms) {
                drawPlatform(plat, state.level.skyTheme)
            }

            // Springs
            for (spring in state.springs) {
                drawSpring(spring)
            }

            // Hint overlay
            if (showHint && state.level.hintPath.size >= 2 && state.phase == GamePhase.DRAWING) {
                drawHintCurve(state.level.hintPath, state.elapsedTime)
            }

            // Forbidden zone indicators during DRAWING
            if (state.phase == GamePhase.DRAWING) {
                drawForbiddenZones(state)
            }

            // Hero shadow + Hero
            drawHeroShadow(state.hero, state.level.platforms)
            drawHero(
                hero = state.hero,
                skin = skin,
                phase = state.phase,
                elapsed = state.elapsedTime
            )

            // Smooth Quadratic Bezier Ink Shield
            val strokePoints = when {
                state.polylineBody != null -> PhysicsEngine.polylineWorldPoints(state.polylineBody)
                state.rawPoints.isNotEmpty() -> state.rawPoints
                else -> emptyList()
            }
            if (strokePoints.size >= 2) {
                drawInkShield(strokePoints, inkOption, state.isActivelyDrawing, state.elapsedTime)
            } else if (strokePoints.size == 1 && state.isActivelyDrawing) {
                drawCircle(inkOption.strokeColor, radius = LINE_RADIUS, center = Offset(strokePoints[0].x, strokePoints[0].y))
            }

            // Beehives
            state.level.beehives.forEachIndexed { idx, hive ->
                drawBeehive(hive, state.elapsedTime, idx, state.phase == GamePhase.SIMULATING)
            }

            // Boulders
            for (boulder in state.boulders) {
                drawBoulder(boulder)
            }

            // Bees
            for (bee in state.bees) {
                drawBee(bee)
            }

            // Lava pits foreground
            for (lava in state.level.lavaPits) {
                drawLavaPit(lava, state.elapsedTime)
            }

            // Particles (rings, sparks, sweat, confetti, embers)
            drawParticles(state.particles)
        }
    }

    fun DrawScope.drawSkyBackdrop(
        theme: SkyTheme,
        elapsed: Float,
        areaSize: Size,
        isFullScreen: Boolean = false
    ) {
        val colors = when (theme) {
            SkyTheme.SUNNY_MEADOW -> listOf(Color(0xFF38BDF8), Color(0xFF93E2FE), Color(0xFFFFF8E7))
            SkyTheme.VOLCANIC_SUNSET -> listOf(Color(0xFF9A3412), Color(0xFFF97316), Color(0xFFFDE68A))
            SkyTheme.TWILIGHT_SWARM -> listOf(Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFFDE68A))
            SkyTheme.ROCKY_CANYON -> listOf(Color(0xFF0369A1), Color(0xFF38BDF8), Color(0xFFFED7AA))
            SkyTheme.MOLTEN_STORM -> listOf(Color(0xFF450A0A), Color(0xFFDC2626), Color(0xFFFACC15))
        }
        drawRect(
            brush = Brush.verticalGradient(colors = colors),
            size = areaSize
        )
        if (!isFullScreen) return

        val sunCenter = Offset(areaSize.width * 0.80f, areaSize.height * 0.14f)
        val sunCore = when (theme) {
            SkyTheme.TWILIGHT_SWARM -> Color(0xFFFEF9C3)
            SkyTheme.MOLTEN_STORM -> Color(0xFFFDE047)
            else -> Color(0xFFFFFDE7)
        }

        // Soft rotating sunbeams
        val rayRadius = areaSize.minDimension * 0.34f
        rotate(elapsed * 8f, pivot = sunCenter) {
            for (i in 0 until 8) {
                val a = i * (PI.toFloat() / 4f)
                val end = Offset(
                    sunCenter.x + cos(a) * rayRadius,
                    sunCenter.y + sin(a) * rayRadius
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(sunCore.copy(alpha = 0.22f), Color.Transparent),
                        start = sunCenter,
                        end = end
                    ),
                    start = sunCenter,
                    end = end,
                    strokeWidth = areaSize.minDimension * 0.06f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Radial bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sunCore.copy(alpha = 0.72f), sunCore.copy(alpha = 0.18f), Color.Transparent),
                center = sunCenter,
                radius = areaSize.minDimension * 0.26f
            ),
            center = sunCenter,
            radius = areaSize.minDimension * 0.26f
        )

        val pulse = 1f + sin(elapsed * 1.8f) * 0.04f
        drawCircle(
            color = sunCore,
            radius = areaSize.minDimension * 0.068f * pulse,
            center = sunCenter
        )

        // Twinkling stars in twilight/storm themes
        if (theme == SkyTheme.TWILIGHT_SWARM || theme == SkyTheme.MOLTEN_STORM) {
            for (s in 0 until 14) {
                val sx = ((s * 97 + 31) % 100) / 100f * areaSize.width
                val sy = ((s * 53 + 17) % 45) / 100f * areaSize.height
                val twinkle = 0.35f + 0.65f * ((sin(elapsed * 3.2f + s * 1.7f) + 1f) * 0.5f)
                drawCircle(
                    color = Color.White.copy(alpha = twinkle * 0.75f),
                    radius = 2.2f + (s % 2) * 1.2f,
                    center = Offset(sx, sy)
                )
            }
        }
    }

    private fun DrawScope.drawDistantScenery(theme: SkyTheme, elapsed: Float) {
        val farColor = when (theme) {
            SkyTheme.SUNNY_MEADOW -> Color(0xFF7DD3FC).copy(alpha = 0.5f)
            SkyTheme.VOLCANIC_SUNSET -> Color(0xFF7C2D12).copy(alpha = 0.35f)
            SkyTheme.TWILIGHT_SWARM -> Color(0xFF312E81).copy(alpha = 0.45f)
            SkyTheme.ROCKY_CANYON -> Color(0xFF9A3412).copy(alpha = 0.25f)
            SkyTheme.MOLTEN_STORM -> Color(0xFF450A0A).copy(alpha = 0.5f)
        }
        val midColor = when (theme) {
            SkyTheme.SUNNY_MEADOW -> Color(0xFF4ADE80).copy(alpha = 0.52f)
            SkyTheme.VOLCANIC_SUNSET -> Color(0xFF9A3412).copy(alpha = 0.42f)
            SkyTheme.TWILIGHT_SWARM -> Color(0xFF1E1B4B).copy(alpha = 0.42f)
            SkyTheme.ROCKY_CANYON -> Color(0xFFB45309).copy(alpha = 0.34f)
            SkyTheme.MOLTEN_STORM -> Color(0xFF7F1D1D).copy(alpha = 0.48f)
        }

        // Far peaks
        val farPath = Path().apply {
            moveTo(0f, WORLD_HEIGHT)
            lineTo(0f, 520f)
            lineTo(75f, 445f)
            lineTo(155f, 505f)
            lineTo(255f, 420f)
            lineTo(350f, 495f)
            lineTo(WORLD_WIDTH, 455f)
            lineTo(WORLD_WIDTH, WORLD_HEIGHT)
            close()
        }
        drawPath(farPath, farColor)

        // Mid rolling hills
        val midPath = Path().apply {
            moveTo(0f, WORLD_HEIGHT)
            lineTo(0f, 585f)
            cubicTo(95f, 515f, 185f, 615f, 290f, 545f)
            cubicTo(355f, 505f, 395f, 550f, WORLD_WIDTH, 530f)
            lineTo(WORLD_WIDTH, WORLD_HEIGHT)
            close()
        }
        drawPath(midPath, midColor)

        // Stylized foreground trees on meadow theme
        if (theme == SkyTheme.SUNNY_MEADOW) {
            val treePositions = listOf(42f to 570f, 378f to 532f, 128f to 560f)
            for ((tx, ty) in treePositions) {
                val sway = sin(elapsed * 1.6f + tx) * 1.5f
                drawLine(Color(0xFF78350F).copy(alpha = 0.55f), Offset(tx, ty), Offset(tx, ty - 18f), 4.5f)
                drawCircle(Color(0xFF22C55E).copy(alpha = 0.65f), 14f, Offset(tx + sway, ty - 24f))
                drawCircle(Color(0xFF4ADE80).copy(alpha = 0.55f), 9f, Offset(tx - 4f + sway, ty - 28f))
            }
        }
    }

    private fun DrawScope.drawClouds(elapsed: Float, theme: SkyTheme) {
        val tint = when (theme) {
            SkyTheme.VOLCANIC_SUNSET, SkyTheme.MOLTEN_STORM -> Color(0xFFFED7AA)
            SkyTheme.TWILIGHT_SWARM -> Color(0xFFE0E7FF)
            else -> Color.White
        }
        val offsets = listOf(0.12f, 0.52f, 0.88f)
        val ys = listOf(84f, 134f, 68f)
        val speeds = listOf(11f, 7f, 14f)
        val scales = listOf(1.05f, 0.82f, 1.15f)
        for (i in offsets.indices) {
            val x = ((offsets[i] * WORLD_WIDTH + elapsed * speeds[i]) % (WORLD_WIDTH + 160f)) - 80f
            drawFluffyCloud(Offset(x, ys[i]), scales[i], tint)
        }
    }

    private fun DrawScope.drawFluffyCloud(center: Offset, s: Float, tint: Color) {
        val shadow = tint.copy(alpha = 0.55f)
        val main = tint.copy(alpha = 0.88f)
        drawRoundRect(
            color = shadow,
            topLeft = center + Offset(-16f * s, 3f * s),
            size = Size(70f * s, 18f * s),
            cornerRadius = CornerRadius(9f * s, 9f * s)
        )
        drawCircle(main, radius = 17f * s, center = center)
        drawCircle(main, radius = 23f * s, center = center + Offset(18f * s, -6f * s))
        drawCircle(main, radius = 16f * s, center = center + Offset(36f * s, 1f * s))
        drawRoundRect(
            color = main,
            topLeft = center + Offset(-16f * s, 0f),
            size = Size(68f * s, 17f * s),
            cornerRadius = CornerRadius(8.5f * s, 8.5f * s)
        )
    }

    private fun DrawScope.drawPlatform(plat: Platform, theme: SkyTheme) {
        val outline = Color(0xFF1C1917)

        // Drop shadow under platform
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(plat.x + 3f, plat.y + 6f),
            size = Size(plat.width, plat.height),
            cornerRadius = CornerRadius(10f, 10f)
        )

        when (plat.style) {
            PlatformStyle.GRASS -> {
                // Rich soil body gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF9A6132), Color(0xFF6B3E1B)),
                        startY = plat.y,
                        endY = plat.y + plat.height
                    ),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                // Soil pebbles
                if (plat.width > 55f && plat.height > 26f) {
                    val count = (plat.width / 55f).toInt().coerceIn(1, 6)
                    for (i in 0 until count) {
                        val px = plat.x + 22f + i * (plat.width - 44f) / count.coerceAtLeast(1)
                        val py = plat.y + plat.height * 0.65f
                        drawCircle(Color(0xFF543013).copy(alpha = 0.55f), 4f, Offset(px, py))
                        drawCircle(Color(0xFFB47844).copy(alpha = 0.45f), 2.5f, Offset(px + 12f, py - 5f))
                    }
                }
                // Lush overhanging grass cap
                val capH = minOf(18f, plat.height * 0.46f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A)),
                        startY = plat.y,
                        endY = plat.y + capH
                    ),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, capH),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                // Glossy grass highlight
                drawRoundRect(
                    color = Color(0xFF86EFAC).copy(alpha = 0.7f),
                    topLeft = Offset(plat.x + 8f, plat.y + 3f),
                    size = Size((plat.width - 16f).coerceAtLeast(4f), 4f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Little meadow wildflowers
                if (plat.width >= 100f) {
                    val fx = plat.x + plat.width * 0.22f
                    drawCircle(Color(0xFFFEF08A), 3f, Offset(fx, plat.y - 2f))
                    drawCircle(Color(0xFFF472B6), 3f, Offset(plat.x + plat.width * 0.78f, plat.y - 2f))
                }
            }

            PlatformStyle.STONE -> {
                val baseTop = if (theme == SkyTheme.MOLTEN_STORM || theme == SkyTheme.VOLCANIC_SUNSET) {
                    Color(0xFF57534E)
                } else {
                    Color(0xFF788596)
                }
                val baseBot = if (theme == SkyTheme.MOLTEN_STORM || theme == SkyTheme.VOLCANIC_SUNSET) {
                    Color(0xFF292524)
                } else {
                    Color(0xFF475569)
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(baseTop, baseBot),
                        startY = plat.y,
                        endY = plat.y + plat.height
                    ),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                // Beveled top rim highlight
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(plat.x + 4f, plat.y + 3f),
                    size = Size((plat.width - 8f).coerceAtLeast(4f), minOf(7f, plat.height * 0.25f)),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Chiseled block seams
                if (plat.width > 70f) {
                    val step = plat.width / 3f
                    for (i in 1..2) {
                        val sx = plat.x + step * i
                        drawLine(
                            color = Color.Black.copy(alpha = 0.24f),
                            start = Offset(sx, plat.y + 4f),
                            end = Offset(sx - 4f, plat.y + minOf(plat.height - 4f, 22f)),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            PlatformStyle.WOOD_BRIDGE -> {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFD97706), Color(0xFF92400E)),
                        startY = plat.y,
                        endY = plat.y + plat.height
                    ),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = Color(0xFFFBBF24).copy(alpha = 0.48f),
                    topLeft = Offset(plat.x + 4f, plat.y + 3f),
                    size = Size((plat.width - 8f).coerceAtLeast(4f), 5f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                // Wood plank grain lines
                drawLine(
                    color = Color(0xFF78350F).copy(alpha = 0.55f),
                    start = Offset(plat.x + 14f, plat.y + plat.height * 0.55f),
                    end = Offset(plat.x + plat.width - 14f, plat.y + plat.height * 0.55f),
                    strokeWidth = 2f
                )
                // Iron rivets
                drawCircle(Color(0xFF475569), 3.6f, Offset(plat.x + 10f, plat.y + plat.height * 0.5f))
                drawCircle(Color(0xFF94A3B8), 1.5f, Offset(plat.x + 9f, plat.y + plat.height * 0.45f))
                drawCircle(Color(0xFF475569), 3.6f, Offset(plat.x + plat.width - 10f, plat.y + plat.height * 0.5f))
                drawCircle(Color(0xFF94A3B8), 1.5f, Offset(plat.x + plat.width - 11f, plat.y + plat.height * 0.45f))
            }
        }

        drawRoundRect(
            color = outline,
            topLeft = Offset(plat.x, plat.y),
            size = Size(plat.width, plat.height),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2.8f)
        )
    }

    private fun DrawScope.drawLavaAmbientGlow(lava: LavaPit, elapsed: Float) {
        val pulse = 0.25f + 0.08f * sin(elapsed * 4f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFFFF6D00).copy(alpha = pulse)),
                startY = lava.y - 60f,
                endY = lava.y + 10f
            ),
            topLeft = Offset(lava.x, lava.y - 60f),
            size = Size(lava.width, 70f)
        )
    }

    private fun DrawScope.drawLavaPit(lava: LavaPit, elapsed: Float) {
        val topY = lava.y
        val wavePath = Path().apply {
            moveTo(lava.x, topY + lava.height)
            lineTo(lava.x, topY + 6f)
            val segments = 20
            val step = lava.width / segments
            for (i in 0..segments) {
                val px = lava.x + i * step
                val py = topY + 5f + sin(elapsed * 5.2f + i * 0.7f) * 4.2f
                lineTo(px, py)
            }
            lineTo(lava.x + lava.width, topY + lava.height)
            close()
        }
        drawPath(
            path = wavePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF9100), Color(0xFFEF4444), Color(0xFF7F1D1D)),
                startY = topY,
                endY = topY + lava.height
            )
        )
        // Secondary churning wave highlight
        val rimPath = Path().apply {
            val segments = 20
            val step = lava.width / segments
            for (i in 0..segments) {
                val px = lava.x + i * step
                val py = topY + 5f + sin(elapsed * 5.2f + i * 0.7f) * 4.2f
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(
            path = rimPath,
            color = Color(0xFFFFEA00),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    private fun DrawScope.drawSpring(springState: SpringRuntimeState) {
        val sp = springState.spec
        val compressPx = springState.compression * 7f
        val topY = sp.y + compressPx
        val baseBottom = sp.y + sp.height

        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(sp.x + 6f, baseBottom - 5f),
            size = Size(sp.width - 12f, 5f),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        val coilPath = Path().apply {
            moveTo(sp.x + 14f, baseBottom - 5f)
            lineTo(sp.x + sp.width - 14f, (baseBottom + topY) * 0.56f)
            lineTo(sp.x + 14f, topY + 7f)
            lineTo(sp.x + sp.width - 14f, topY + 7f)
        }
        drawPath(coilPath, color = Color(0xFFE2E8F0), style = Stroke(width = 3.5f, cap = StrokeCap.Round))

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF87171), Color(0xFFDC2626)),
                startY = topY,
                endY = topY + 10f
            ),
            topLeft = Offset(sp.x, topY),
            size = Size(sp.width, 10f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(sp.x + 7f, topY + 2f),
            size = Size(sp.width - 14f, 2.5f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(sp.x, topY),
            size = Size(sp.width, 10f),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 2.2f)
        )
    }

    private fun DrawScope.drawBeehive(
        hive: BeehiveSpec,
        elapsed: Float,
        index: Int,
        active: Boolean
    ) {
        val sway = sin(elapsed * (if (active) 9f else 2.4f) + index * 1.4f) * (if (active) 5.5f else 3f)
        val anchorTop = Offset(hive.x, (hive.y - 54f).coerceAtLeast(0f))

        // Branch/support line & leaves
        drawLine(
            color = Color(0xFF78350F),
            start = Offset((hive.x - 32f).coerceAtLeast(0f), anchorTop.y),
            end = Offset((hive.x + 32f).coerceAtMost(WORLD_WIDTH), anchorTop.y + 4f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawOval(
            color = Color(0xFF22C55E),
            topLeft = Offset(hive.x + 12f, anchorTop.y - 6f),
            size = Size(16f, 9f)
        )
        drawLine(
            color = Color(0xFF451A03),
            start = Offset(hive.x, anchorTop.y + 2f),
            end = Offset(hive.x, hive.y - 18f),
            strokeWidth = 3.8f,
            cap = StrokeCap.Round
        )

        rotate(sway, pivot = Offset(hive.x, hive.y - 22f)) {
            val c = Offset(hive.x, hive.y)
            val rings = listOf(
                Triple(-15f, 18f, Color(0xFFFBBF24)),
                Triple(-5f, 24f, Color(0xFFF59E0B)),
                Triple(6f, 22f, Color(0xFFD97706)),
                Triple(16f, 15f, Color(0xFFB45309))
            )
            for ((dy, rx, col) in rings) {
                drawOval(
                    brush = Brush.horizontalGradient(
                        colors = listOf(col.copy(alpha = 0.85f), col, Color(0xFF92400E)),
                        startX = c.x - rx,
                        endX = c.x + rx
                    ),
                    topLeft = Offset(c.x - rx, c.y + dy - 7.5f),
                    size = Size(rx * 2f, 15f)
                )
                drawOval(
                    color = Color(0xFF451A03),
                    topLeft = Offset(c.x - rx, c.y + dy - 7.5f),
                    size = Size(rx * 2f, 15f),
                    style = Stroke(width = 2.2f)
                )
            }
            // Glossy honey drop
            drawOval(
                color = Color(0xFFFDE047),
                topLeft = Offset(c.x - 10f, c.y + 18f),
                size = Size(6f, 9f)
            )
            // Dark entrance hole
            drawCircle(
                color = Color(0xFF1C1917),
                radius = 6.8f,
                center = Offset(c.x, c.y + 8f)
            )
        }
    }

    private fun DrawScope.drawBoulder(boulder: BoulderState) {
        val c = Offset(boulder.x, boulder.y)
        rotate(boulder.angle * (180f / PI.toFloat()), pivot = c) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF334155)),
                    center = c + Offset(-boulder.radius * 0.35f, -boulder.radius * 0.35f),
                    radius = boulder.radius * 1.45f
                ),
                radius = boulder.radius,
                center = c
            )
            // Specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = boulder.radius * 0.25f,
                center = c + Offset(-boulder.radius * 0.35f, -boulder.radius * 0.35f)
            )
            // Rock cracks
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.65f),
                start = c + Offset(-boulder.radius * 0.45f, -boulder.radius * 0.1f),
                end = c + Offset(boulder.radius * 0.25f, boulder.radius * 0.35f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.55f),
                start = c + Offset(boulder.radius * 0.1f, -boulder.radius * 0.4f),
                end = c + Offset(-boulder.radius * 0.15f, boulder.radius * 0.15f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF0F172A),
                radius = boulder.radius,
                center = c,
                style = Stroke(width = 2.8f)
            )
        }
    }

    private fun DrawScope.drawBee(bee: BeeState) {
        val c = Offset(bee.x, bee.y)
        val facingRight = bee.vx >= 0f
        val wingFlap = sin(bee.wingPhase) * 4.5f

        withTransform({
            translate(c.x, c.y)
            rotate(bee.visualAngle, Offset.Zero)
            if (!facingRight) scale(-1f, 1f, Offset.Zero)
        }) {
            // Back wing
            drawOval(
                color = Color(0xFFBAE6FD).copy(alpha = 0.65f),
                topLeft = Offset(-7f, -14f - wingFlap * 0.3f),
                size = Size(8f, 9f)
            )
            // Front wing
            drawOval(
                color = Color.White.copy(alpha = 0.88f),
                topLeft = Offset(-3f, -13.5f + wingFlap * 0.45f),
                size = Size(8.5f, 9.5f)
            )
            drawOval(
                color = Color(0xFF0F172A),
                topLeft = Offset(-3f, -13.5f + wingFlap * 0.45f),
                size = Size(8.5f, 9.5f),
                style = Stroke(width = 1.2f)
            )

            // Stinger
            val stinger = Path().apply {
                moveTo(-9f, 0f)
                lineTo(-14f, -2.2f)
                lineTo(-14f, 2.2f)
                close()
            }
            drawPath(stinger, color = Color(0xFF18181B))

            // Shaded body
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFFACC15), Color(0xFFEAB308)),
                    startY = -7f,
                    endY = 7f
                ),
                topLeft = Offset(-9.5f, -7f),
                size = Size(19f, 14f)
            )
            // Stripes
            drawLine(Color(0xFF18181B), Offset(-3f, -6.5f), Offset(-3f, 6.5f), strokeWidth = 2.8f)
            drawLine(Color(0xFF18181B), Offset(2f, -6.5f), Offset(2f, 6.5f), strokeWidth = 2.5f)
            drawOval(
                color = Color(0xFF18181B),
                topLeft = Offset(-9.5f, -7f),
                size = Size(19f, 14f),
                style = Stroke(width = 1.9f)
            )

            // Antenna
            drawLine(Color(0xFF18181B), Offset(6f, -6f), Offset(9.5f, -10.5f), strokeWidth = 1.5f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF18181B), 1.4f, Offset(9.5f, -10.5f))

            // Angry eye + brow
            drawCircle(Color.White, radius = 2.8f, center = Offset(5.5f, -1.5f))
            drawCircle(Color.Black, radius = 1.3f, center = Offset(6.2f, -1.5f))
            drawLine(
                color = Color(0xFF18181B),
                start = Offset(3.2f, -5.2f),
                end = Offset(8.8f, -3.0f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawForbiddenZones(state: PhysicsWorldState) {
        val pulse = 0.35f + 0.25f * sin(state.elapsedTime * 5f)
        val warnBoost = if (state.forbiddenWarningTimer > 0f) 0.38f else 0f
        val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), -state.elapsedTime * 26f)
        val strokeColor = Color(0xFFEF4444).copy(alpha = (pulse + warnBoost).coerceIn(0f, 0.95f))
        val fillColor = Color(0xFFEF4444).copy(alpha = (0.09f + warnBoost * 0.25f))

        val heroCenter = Offset(state.hero.x, state.hero.y)
        drawCircle(fillColor, radius = HERO_RADIUS + 11f, center = heroCenter)
        drawCircle(
            color = strokeColor,
            radius = HERO_RADIUS + 11f,
            center = heroCenter,
            style = Stroke(width = 2.4f, pathEffect = dash)
        )

        for (hive in state.level.beehives) {
            val hc = Offset(hive.x, hive.y)
            drawCircle(fillColor, radius = hive.forbiddenRadius, center = hc)
            drawCircle(
                color = strokeColor,
                radius = hive.forbiddenRadius,
                center = hc,
                style = Stroke(width = 2.4f, pathEffect = dash)
            )
        }
    }

    private fun DrawScope.drawHintCurve(points: List<Vec2>, elapsed: Float) {
        val path = buildSmoothPath(points)
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), -elapsed * 34f)
        drawPath(
            path = path,
            color = Color(0xFF0EA5E9).copy(alpha = 0.35f),
            style = Stroke(width = 13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.94f),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)
        )
    }

    private fun buildSmoothPath(points: List<Vec2>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }
        for (i in 1 until points.lastIndex) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) * 0.5f
            val midY = (p0.y + p1.y) * 0.5f
            path.quadraticTo(p0.x, p0.y, midX, midY)
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    private fun DrawScope.drawInkShield(
        points: List<Vec2>,
        inkOption: InkColorOption,
        isActivelyDrawing: Boolean,
        elapsed: Float
    ) {
        val path = buildSmoothPath(points)

        // Soft drop shadow beneath drawn shield
        withTransform({ translate(2f, 4.5f) }) {
            drawPath(
                path = path,
                color = Color.Black.copy(alpha = 0.22f),
                style = Stroke(width = LINE_RADIUS * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        // Bold cartoon outline
        drawPath(
            path = path,
            color = inkOption.outlineColor,
            style = Stroke(width = LINE_RADIUS * 2.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Rich core
        drawPath(
            path = path,
            color = inkOption.strokeColor,
            style = Stroke(width = LINE_RADIUS * 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Glossy top specular line
        drawPath(
            path = path,
            color = inkOption.highlightColor.copy(alpha = 0.62f),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (isActivelyDrawing) {
            val tip = points.last()
            val ringR = 11f + sin(elapsed * 14f) * 2f
            drawCircle(
                color = inkOption.highlightColor.copy(alpha = 0.45f),
                radius = ringR,
                center = Offset(tip.x, tip.y)
            )
            drawCircle(
                color = Color.White,
                radius = 4.5f,
                center = Offset(tip.x, tip.y)
            )
        }
    }

    private fun DrawScope.drawHeroShadow(hero: HeroState, platforms: List<Platform>) {
        var nearestFloorY: Float? = null
        for (plat in platforms) {
            if (hero.x >= plat.x - 10f && hero.x <= plat.x + plat.width + 10f && plat.y >= hero.y) {
                if (nearestFloorY == null || plat.y < nearestFloorY) {
                    nearestFloorY = plat.y
                }
            }
        }
        if (nearestFloorY != null) {
            val dist = (nearestFloorY - (hero.y + hero.radius)).coerceAtLeast(0f)
            val scale = (1f - dist / 140f).coerceIn(0.25f, 1f)
            drawOval(
                color = Color.Black.copy(alpha = 0.24f * scale),
                topLeft = Offset(hero.x - hero.radius * 0.85f * scale, nearestFloorY - 3f),
                size = Size(hero.radius * 1.7f * scale, 7f * scale)
            )
        }
    }

    fun DrawScope.drawHero(
        hero: HeroState,
        skin: HeroSkin,
        phase: GamePhase,
        elapsed: Float
    ) {
        val c = Offset(hero.x, hero.y)
        val r = hero.radius
        val wobble = if (hero.scaredLevel > 0.3f && phase == GamePhase.SIMULATING) {
            sin(elapsed * 42f) * 2.5f * hero.scaredLevel
        } else 0f
        val idleBreathY = if (phase == GamePhase.DRAWING) 1f + sin(elapsed * 3.2f) * 0.025f else 1f
        val idleBreathX = 1f / idleBreathY

        withTransform({
            translate(c.x + wobble, c.y)
            scale(hero.squashX * idleBreathX, hero.squashY * idleBreathY, Offset(0f, r))
            if (hero.isHurt) rotate(hero.hurtAngle * 35f, Offset.Zero)
        }) {
            when (skin) {
                HeroSkin.DOGE -> drawDogeBase(r, elapsed)
                HeroSkin.NEON_CAT -> drawNeonCatBase(r, elapsed)
                HeroSkin.ESPRESSO_CUP -> drawEspressoBase(r, elapsed)
                HeroSkin.CROWN_FROG -> drawCrownFrogBase(r)
            }

            drawHeroExpression(
                r = r,
                phase = phase,
                scaredLevel = hero.scaredLevel,
                isHurt = hero.isHurt,
                elapsed = elapsed,
                skin = skin,
                lookX = hero.lookDirX,
                lookY = hero.lookDirY
            )
        }
    }

    private fun DrawScope.drawDogeBase(r: Float, elapsed: Float) {
        val outline = Color(0xFF292524)
        val furLight = Color(0xFFFCD34D)
        val furMid = Color(0xFFF59E0B)
        val cream = Color(0xFFFFF7E6)

        // Wagging fluffy tail on left
        val wag = sin(elapsed * 8f) * 10f
        rotate(wag, pivot = Offset(-r * 0.7f, r * 0.55f)) {
            drawOval(
                color = furMid,
                topLeft = Offset(-r * 1.25f, r * 0.15f),
                size = Size(r * 0.65f, r * 0.45f)
            )
            drawOval(
                color = outline,
                topLeft = Offset(-r * 1.25f, r * 0.15f),
                size = Size(r * 0.65f, r * 0.45f),
                style = Stroke(2.2f)
            )
        }

        // Pointy ears with pink inner
        val leftEar = Path().apply {
            moveTo(-r * 0.72f, -r * 0.42f)
            lineTo(-r * 0.88f, -r * 1.08f)
            lineTo(-r * 0.24f, -r * 0.78f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(r * 0.72f, -r * 0.42f)
            lineTo(r * 0.88f, -r * 1.08f)
            lineTo(r * 0.24f, -r * 0.78f)
            close()
        }
        drawPath(leftEar, furMid)
        drawPath(rightEar, furMid)
        drawCircle(Color(0xFFFDA4AF), 3.5f, Offset(-r * 0.58f, -r * 0.72f))
        drawCircle(Color(0xFFFDA4AF), 3.5f, Offset(r * 0.58f, -r * 0.72f))
        drawPath(leftEar, outline, style = Stroke(2.5f, join = StrokeJoin.Round))
        drawPath(rightEar, outline, style = Stroke(2.5f, join = StrokeJoin.Round))

        // Main shaded head
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(furLight, furMid),
                center = Offset(-r * 0.25f, -r * 0.3f),
                radius = r * 1.35f
            ),
            radius = r,
            center = Offset.Zero
        )
        // Cream muzzle
        drawOval(
            color = cream,
            topLeft = Offset(-r * 0.74f, -r * 0.04f),
            size = Size(r * 1.48f, r * 0.96f)
        )
        // Rosy cheeks
        drawCircle(Color(0xFFFCA5A5).copy(alpha = 0.65f), 4.2f, Offset(-r * 0.58f, 5f))
        drawCircle(Color(0xFFFCA5A5).copy(alpha = 0.65f), 4.2f, Offset(r * 0.58f, 5f))

        drawCircle(outline, radius = r, center = Offset.Zero, style = Stroke(2.8f))
        // Glossy nose
        drawOval(color = Color(0xFF1C1917), topLeft = Offset(-4.5f, 1f), size = Size(9f, 6.5f))
        drawCircle(Color.White.copy(alpha = 0.7f), 1.5f, Offset(-1.5f, 2.5f))
    }

    private fun DrawScope.drawNeonCatBase(r: Float, elapsed: Float) {
        val bodyTop = Color(0xFF4338CA)
        val bodyBot = Color(0xFF1E1B4B)
        val neonCyan = Color(0xFF22D3EE)
        val neonPink = Color(0xFFF472B6)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(neonCyan.copy(alpha = 0.32f + 0.1f * sin(elapsed * 4f)), Color.Transparent),
                center = Offset.Zero,
                radius = r * 1.45f
            ),
            radius = r * 1.45f,
            center = Offset.Zero
        )

        val leftEar = Path().apply {
            moveTo(-r * 0.72f, -r * 0.42f)
            lineTo(-r * 0.90f, -r * 1.10f)
            lineTo(-r * 0.22f, -r * 0.80f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(r * 0.72f, -r * 0.42f)
            lineTo(r * 0.90f, -r * 1.10f)
            lineTo(r * 0.22f, -r * 0.80f)
            close()
        }
        drawPath(leftEar, bodyTop)
        drawPath(rightEar, bodyTop)
        drawPath(leftEar, neonCyan, style = Stroke(2.6f, join = StrokeJoin.Round))
        drawPath(rightEar, neonCyan, style = Stroke(2.6f, join = StrokeJoin.Round))

        drawCircle(
            brush = Brush.verticalGradient(listOf(bodyTop, bodyBot), startY = -r, endY = r),
            radius = r,
            center = Offset.Zero
        )
        drawCircle(neonCyan, radius = r, center = Offset.Zero, style = Stroke(2.8f))

        // Whiskers + glowing nose
        drawLine(neonPink, Offset(-r * 0.96f, 2f), Offset(-r * 0.48f, 4f), 2.2f, StrokeCap.Round)
        drawLine(neonPink, Offset(-r * 0.96f, 9f), Offset(-r * 0.48f, 8f), 2.2f, StrokeCap.Round)
        drawLine(neonPink, Offset(r * 0.96f, 2f), Offset(r * 0.48f, 4f), 2.2f, StrokeCap.Round)
        drawLine(neonPink, Offset(r * 0.96f, 9f), Offset(r * 0.48f, 8f), 2.2f, StrokeCap.Round)
        drawCircle(neonPink, radius = 3.4f, center = Offset(0f, 3f))
    }

    private fun DrawScope.drawEspressoBase(r: Float, elapsed: Float) {
        val outline = Color(0xFF292524)
        for (i in -1..1) {
            val sx = i * 8f + sin(elapsed * 4.2f + i) * 3.5f
            drawLine(
                color = Color.White.copy(alpha = 0.78f),
                start = Offset(sx, -r - 2f),
                end = Offset(sx - 3f, -r - 15f),
                strokeWidth = 2.8f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(Color(0xFFF8FAFC), radius = r * 0.34f, center = Offset(r * 0.92f, 2f), style = Stroke(5.5f))
        drawCircle(outline, radius = r * 0.34f, center = Offset(r * 0.92f, 2f), style = Stroke(2.2f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFE2E8F0)),
                center = Offset(-r * 0.3f, -r * 0.25f),
                radius = r * 1.4f
            ),
            radius = r,
            center = Offset.Zero
        )
        drawOval(
            color = Color(0xFF78350F),
            topLeft = Offset(-r * 0.72f, -r * 0.88f),
            size = Size(r * 1.44f, r * 0.45f)
        )
        drawOval(
            color = Color(0xFFD97706).copy(alpha = 0.65f),
            topLeft = Offset(-r * 0.35f, -r * 0.76f),
            size = Size(r * 0.7f, r * 0.2f)
        )
        drawCircle(outline, radius = r, center = Offset.Zero, style = Stroke(2.8f))
        drawOval(color = Color(0xFFE2E8F0), topLeft = Offset(-r * 1.12f, r * 0.72f), size = Size(r * 2.24f, 8.5f))
        drawOval(color = outline, topLeft = Offset(-r * 1.12f, r * 0.72f), size = Size(r * 2.24f, 8.5f), style = Stroke(2.2f))
    }

    private fun DrawScope.drawCrownFrogBase(r: Float) {
        val outline = Color(0xFF14532D)
        val belly = Color(0xFFDCFCE7)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A)),
                center = Offset(-r * 0.25f, -r * 0.3f),
                radius = r * 1.35f
            ),
            radius = r,
            center = Offset.Zero
        )
        drawOval(color = belly, topLeft = Offset(-r * 0.68f, r * 0.05f), size = Size(r * 1.36f, r * 0.84f))
        drawCircle(outline, radius = r, center = Offset.Zero, style = Stroke(2.8f))

        drawCircle(Color(0xFFFDA4AF).copy(alpha = 0.82f), radius = 5.2f, center = Offset(-r * 0.56f, 6f))
        drawCircle(Color(0xFFFDA4AF).copy(alpha = 0.82f), radius = 5.2f, center = Offset(r * 0.56f, 6f))

        val crown = Path().apply {
            moveTo(-12.5f, -r + 3f)
            lineTo(-14.5f, -r - 12.5f)
            lineTo(-5f, -r - 4f)
            lineTo(0f, -r - 16f)
            lineTo(5f, -r - 4f)
            lineTo(14.5f, -r - 12.5f)
            lineTo(12.5f, -r + 3f)
            close()
        }
        drawPath(crown, Color(0xFFFACC15))
        drawPath(crown, Color(0xFF92400E), style = Stroke(2.2f, join = StrokeJoin.Round))
        drawCircle(Color(0xFFEF4444), 2.2f, Offset(0f, -r - 4f))
        drawCircle(Color(0xFF38BDF8), 1.8f, Offset(-7.5f, -r - 2f))
        drawCircle(Color(0xFF38BDF8), 1.8f, Offset(7.5f, -r - 2f))
    }

    private fun DrawScope.drawHeroExpression(
        r: Float,
        phase: GamePhase,
        scaredLevel: Float,
        isHurt: Boolean,
        elapsed: Float,
        skin: HeroSkin,
        lookX: Float,
        lookY: Float
    ) {
        val eyeY = -r * 0.22f
        val eyeX = r * 0.36f
        val inkColor = if (skin == HeroSkin.NEON_CAT) Color.White else Color(0xFF1C1917)

        if (phase == GamePhase.VICTORY) {
            drawLine(Color(0xFF0F172A), Offset(-r * 0.72f, eyeY), Offset(r * 0.72f, eyeY), strokeWidth = 3.2f)
            drawRoundRect(Color(0xFF0F172A), Offset(-eyeX - 8.5f, eyeY - 6f), Size(17f, 11.5f), CornerRadius(3.5f, 3.5f))
            drawRoundRect(Color(0xFF0F172A), Offset(eyeX - 8.5f, eyeY - 6f), Size(17f, 11.5f), CornerRadius(3.5f, 3.5f))
            drawLine(Color.White, Offset(-eyeX - 4f, eyeY - 3f), Offset(-eyeX + 2.5f, eyeY + 2.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(eyeX - 4f, eyeY - 3f), Offset(eyeX + 2.5f, eyeY + 2.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawArc(
                color = inkColor,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(-9.5f, r * 0.22f),
                size = Size(19f, 12.5f),
                style = Stroke(width = 2.8f, cap = StrokeCap.Round)
            )
            return
        }

        if (isHurt || phase == GamePhase.FAILED) {
            drawCircle(Color(0xFFEF4444), radius = 7.8f, center = Offset(r * 0.35f, -r * 0.92f))
            drawCircle(Color(0xFF7F1D1D), radius = 7.8f, center = Offset(r * 0.35f, -r * 0.92f), style = Stroke(2.2f))

            for (s in 0 until 3) {
                val a = elapsed * 5.2f + s * (2f * PI.toFloat() / 3f)
                val sx = cos(a) * (r * 0.88f)
                val sy = -r * 1.18f + sin(a) * 5.5f
                drawCircle(Color(0xFFFACC15), radius = 3.8f, center = Offset(sx, sy))
            }

            val s = 4.2f
            drawLine(inkColor, Offset(-eyeX - s, eyeY - s), Offset(-eyeX + s, eyeY + s), 2.6f, StrokeCap.Round)
            drawLine(inkColor, Offset(-eyeX + s, eyeY - s), Offset(-eyeX - s, eyeY + s), 2.6f, StrokeCap.Round)
            drawLine(inkColor, Offset(eyeX - s, eyeY - s), Offset(eyeX + s, eyeY + s), 2.6f, StrokeCap.Round)
            drawLine(inkColor, Offset(eyeX + s, eyeY - s), Offset(eyeX - s, eyeY + s), 2.6f, StrokeCap.Round)
            drawCircle(Color(0xFFEF4444), radius = 4.2f, center = Offset(0f, r * 0.42f))
            return
        }

        val pupilOff = Offset(lookX * 2.2f, lookY * 1.8f)

        if (scaredLevel > 0.35f) {
            drawCircle(Color.White, radius = 6.8f, center = Offset(-eyeX, eyeY))
            drawCircle(Color.White, radius = 6.8f, center = Offset(eyeX, eyeY))
            drawCircle(Color(0xFF1C1917), radius = 2.5f, center = Offset(-eyeX, eyeY) + pupilOff)
            drawCircle(Color(0xFF1C1917), radius = 2.5f, center = Offset(eyeX, eyeY) + pupilOff)
            drawCircle(Color.White, radius = 1.0f, center = Offset(-eyeX - 0.8f, eyeY - 1f) + pupilOff)
            drawCircle(Color.White, radius = 1.0f, center = Offset(eyeX - 0.8f, eyeY - 1f) + pupilOff)
            drawCircle(Color(0xFF1C1917), radius = 6.8f, center = Offset(-eyeX, eyeY), style = Stroke(1.8f))
            drawCircle(Color(0xFF1C1917), radius = 6.8f, center = Offset(eyeX, eyeY), style = Stroke(1.8f))

            // Worried eyebrows
            drawLine(inkColor, Offset(-eyeX - 5f, eyeY - 9f), Offset(-eyeX + 3f, eyeY - 7f), 2f, StrokeCap.Round)
            drawLine(inkColor, Offset(eyeX + 5f, eyeY - 9f), Offset(eyeX - 3f, eyeY - 7f), 2f, StrokeCap.Round)

            drawOval(color = inkColor, topLeft = Offset(-4.5f, r * 0.34f), size = Size(9f, 7.2f))
        } else {
            val blink = (elapsed % 3.6f) > 3.45f
            if (blink) {
                drawLine(inkColor, Offset(-eyeX - 4f, eyeY), Offset(-eyeX + 4f, eyeY), 2.5f, StrokeCap.Round)
                drawLine(inkColor, Offset(eyeX - 4f, eyeY), Offset(eyeX + 4f, eyeY), 2.5f, StrokeCap.Round)
            } else {
                drawCircle(Color.White, radius = 5.0f, center = Offset(-eyeX, eyeY))
                drawCircle(Color.White, radius = 5.0f, center = Offset(eyeX, eyeY))
                drawCircle(Color(0xFF1C1917), radius = 2.7f, center = Offset(-eyeX, eyeY) + pupilOff * 0.7f)
                drawCircle(Color(0xFF1C1917), radius = 2.7f, center = Offset(eyeX, eyeY) + pupilOff * 0.7f)
                drawCircle(Color.White, radius = 1.1f, center = Offset(-eyeX - 1f, eyeY - 1.2f) + pupilOff * 0.5f)
                drawCircle(Color.White, radius = 1.1f, center = Offset(eyeX - 1f, eyeY - 1.2f) + pupilOff * 0.5f)
            }

            drawArc(
                color = inkColor,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(-7f, r * 0.25f),
                size = Size(14f, 9f),
                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
            )
        }
    }

    private fun DrawScope.drawParticles(particles: List<Particle>) {
        for (p in particles) {
            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            when (p.kind) {
                ParticleKind.RING -> {
                    val progress = 1f - alpha
                    val ringR = p.size * (0.45f + progress * 1.3f)
                    drawCircle(
                        color = p.color.copy(alpha = alpha * 0.85f),
                        radius = ringR,
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 3.2f * alpha)
                    )
                }
                ParticleKind.CONFETTI -> {
                    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                        drawRect(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(p.x - p.size * 0.5f, p.y - p.size * 0.3f),
                            size = Size(p.size, p.size * 0.6f)
                        )
                    }
                }
                ParticleKind.SWEAT -> {
                    drawOval(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(p.x - p.size * 0.45f, p.y - p.size * 0.7f),
                        size = Size(p.size * 0.9f, p.size * 1.4f)
                    )
                }
                ParticleKind.EMBER -> {
                    drawCircle(
                        color = Color(0xFFFF6D00).copy(alpha = alpha * 0.45f),
                        radius = p.size * 2.1f,
                        center = Offset(p.x, p.y)
                    )
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size * 0.8f,
                        center = Offset(p.x, p.y)
                    )
                }
                else -> {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size * (0.5f + 0.5f * alpha),
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }
    }

    fun DrawScope.drawMenuScene(elapsed: Float, skin: HeroSkin) {
        drawSkyBackdrop(SkyTheme.SUNNY_MEADOW, elapsed, size, isFullScreen = true)
        val s = minOf(size.width / WORLD_WIDTH, size.height / WORLD_HEIGHT)
        val ox = (size.width - WORLD_WIDTH * s) * 0.5f
        val oy = (size.height - WORLD_HEIGHT * s) * 0.5f
        withTransform({
            translate(ox, oy)
            scale(s, s, Offset.Zero)
        }) {
            drawDistantScenery(SkyTheme.SUNNY_MEADOW, elapsed)
            drawClouds(elapsed, SkyTheme.SUNNY_MEADOW)
            drawPlatform(Platform(25f, 610f, 370f, 90f, PlatformStyle.GRASS), SkyTheme.SUNNY_MEADOW)
            drawBeehive(BeehiveSpec(315f, 165f, 6), elapsed, 0, active = true)
            val bobY = sin(elapsed * 2.5f) * 4f
            val hero = HeroState(x = 210f, y = 586f + bobY, scaredLevel = 0f, lookDirX = 0.4f, lookDirY = -0.5f)
            drawHeroShadow(hero, listOf(Platform(25f, 610f, 370f, 90f, PlatformStyle.GRASS)))
            drawHero(
                hero = hero,
                skin = skin,
                phase = GamePhase.DRAWING,
                elapsed = elapsed
            )
            for (i in 0 until 5) {
                val a = elapsed * 1.9f + i * 1.25f
                val bx = 280f + cos(a) * 65f
                val by = 220f + sin(a * 1.3f) * 35f
                val bvx = -sin(a) * 90f
                drawBee(
                    BeeState(
                        id = i,
                        x = bx,
                        y = by,
                        vx = bvx,
                        vy = cos(a * 1.3f) * 45f,
                        wingPhase = elapsed * 52f + i,
                        wanderSeed = i.toFloat(),
                        visualAngle = (bvx / 110f) * 12f
                    )
                )
            }
        }
    }
}
