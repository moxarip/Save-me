package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class PhysicsStepEvents(
    val bounceCount: Int = 0,
    val springBounce: Boolean = false,
    val urgentTickSecond: Int? = null,
    val justSucceeded: Boolean = false,
    val justFailed: FailReason? = null,
    val closestBeeDistance: Float = 999f,
    val closestLavaDistance: Float = 999f
)

object PhysicsEngine {

    private const val GRAVITY = 520f
    private const val SUB_STEPS = 4

    fun createInitialState(level: LevelData): PhysicsWorldState {
        val pendingBees = mutableListOf<PendingBeeSpawn>()
        var nextBeeId = 0
        level.beehives.forEachIndexed { hiveIndex, hive ->
            for (i in 0 until hive.beeCount) {
                pendingBees += PendingBeeSpawn(
                    hiveIndex = hiveIndex,
                    x = hive.x,
                    y = hive.y + 14f,
                    spawnDelay = 0.08f + (i * 0.045f),
                    beeId = nextBeeId++
                )
            }
        }

        val boulders = level.boulders.map {
            BoulderState(
                x = it.x,
                y = it.y,
                vx = it.initialVx,
                vy = 0f,
                radius = it.radius,
                delayRemaining = it.delaySeconds
            )
        }

        val springs = level.springs.map { SpringRuntimeState(spec = it, compression = 0f) }

        return PhysicsWorldState(
            level = level,
            phase = GamePhase.DRAWING,
            timeRemaining = SIMULATION_DURATION,
            hero = HeroState(x = level.heroStart.x, y = level.heroStart.y),
            pendingBeeSpawns = pendingBees,
            boulders = boulders,
            springs = springs
        )
    }

    fun isInsideForbiddenZone(point: Vec2, state: PhysicsWorldState): Boolean {
        val heroDist = (point - Vec2(state.hero.x, state.hero.y)).length()
        if (heroDist < HERO_RADIUS + 11f) return true

        for (hive in state.level.beehives) {
            if ((point - Vec2(hive.x, hive.y)).length() < hive.forbiddenRadius) {
                return true
            }
        }

        for (plat in state.level.platforms) {
            if (point.x > plat.x + 3f && point.x < plat.x + plat.width - 3f &&
                point.y > plat.y + 3f && point.y < plat.y + plat.height - 3f
            ) {
                return true
            }
        }

        for (lava in state.level.lavaPits) {
            if (point.x > lava.x && point.x < lava.x + lava.width &&
                point.y > lava.y && point.y < lava.y + lava.height
            ) {
                return true
            }
        }
        return false
    }

    private fun clampOutOfForbidden(point: Vec2, prev: Vec2, state: PhysicsWorldState): Vec2 {
        var p = Vec2(
            point.x.coerceIn(LINE_RADIUS, WORLD_WIDTH - LINE_RADIUS),
            point.y.coerceIn(LINE_RADIUS, WORLD_HEIGHT - LINE_RADIUS)
        )

        val heroCenter = Vec2(state.hero.x, state.hero.y)
        val heroSafe = HERO_RADIUS + 12f
        val dHero = (p - heroCenter).length()
        if (dHero < heroSafe) {
            val dir = if (dHero > 0.01f) (p - heroCenter) / dHero else (prev - heroCenter).normalized()
            p = heroCenter + dir * heroSafe
        }

        for (hive in state.level.beehives) {
            val hiveCenter = Vec2(hive.x, hive.y)
            val safe = hive.forbiddenRadius + 2f
            val dHive = (p - hiveCenter).length()
            if (dHive < safe) {
                val dir = if (dHive > 0.01f) (p - hiveCenter) / dHive else (prev - hiveCenter).normalized()
                p = hiveCenter + dir * safe
            }
        }

        for (plat in state.level.platforms) {
            if (p.x > plat.x && p.x < plat.x + plat.width &&
                p.y > plat.y && p.y < plat.y + plat.height
            ) {
                val dLeft = p.x - plat.x
                val dRight = (plat.x + plat.width) - p.x
                val dTop = p.y - plat.y
                val dBottom = (plat.y + plat.height) - p.y
                val minD = minOf(dLeft, dRight, dTop, dBottom)
                p = when (minD) {
                    dTop -> Vec2(p.x, plat.y - 1f)
                    dBottom -> Vec2(p.x, plat.y + plat.height + 1f)
                    dLeft -> Vec2(plat.x - 1f, p.y)
                    else -> Vec2(plat.x + plat.width + 1f, p.y)
                }
            }
        }
        return Vec2(
            p.x.coerceIn(LINE_RADIUS, WORLD_WIDTH - LINE_RADIUS),
            p.y.coerceIn(LINE_RADIUS, WORLD_HEIGHT - LINE_RADIUS)
        )
    }

    fun startStroke(worldPos: Vec2, state: PhysicsWorldState): PhysicsWorldState {
        if (state.phase != GamePhase.DRAWING) return state
        if (isInsideForbiddenZone(worldPos, state)) {
            return state.copy(forbiddenWarningTimer = 0.55f)
        }
        val clamped = Vec2(
            worldPos.x.coerceIn(LINE_RADIUS, WORLD_WIDTH - LINE_RADIUS),
            worldPos.y.coerceIn(LINE_RADIUS, WORLD_HEIGHT - LINE_RADIUS)
        )
        return state.copy(
            rawPoints = listOf(clamped),
            inkUsedLength = 0f,
            isActivelyDrawing = true
        )
    }

    fun extendStroke(worldPos: Vec2, state: PhysicsWorldState): Pair<PhysicsWorldState, Float> {
        if (state.phase != GamePhase.DRAWING) return state to 0f

        if (state.rawPoints.isEmpty()) {
            return if (!isInsideForbiddenZone(worldPos, state)) {
                startStroke(worldPos, state) to 0f
            } else {
                state.copy(forbiddenWarningTimer = 0.4f) to 0f
            }
        }

        val points = state.rawPoints.toMutableList()
        var inkUsed = state.inkUsedLength
        var last = points.last()
        val delta = worldPos - last
        val dist = delta.length()
        if (dist < SAMPLE_STEP) return state to 0f

        val steps = (dist / SAMPLE_STEP).toInt().coerceAtMost(24)
        val stepVec = delta / dist * SAMPLE_STEP
        var addedLength = 0f
        var hitEmpty = false

        for (s in 1..steps) {
            val rawCandidate = if (s == steps) worldPos else last + stepVec
            val candidate = clampOutOfForbidden(rawCandidate, last, state)
            val segLen = (candidate - last).length()
            if (segLen < 2.5f) continue

            if (inkUsed + segLen >= state.level.maxInkLength) {
                val remaining = (state.level.maxInkLength - inkUsed).coerceAtLeast(0f)
                if (remaining > 1.5f) {
                    val finalPt = last + (candidate - last).normalized() * remaining
                    points.add(finalPt)
                    addedLength += remaining
                }
                inkUsed = state.level.maxInkLength
                hitEmpty = true
                break
            } else {
                points.add(candidate)
                inkUsed += segLen
                addedLength += segLen
                last = candidate

                // Real-time jitter smoothing on the penultimate point while drawing
                if (points.size >= 4) {
                    val idx = points.size - 2
                    val avg = Vec2(
                        (points[idx - 1].x + points[idx].x * 2f + points[idx + 1].x) * 0.25f,
                        (points[idx - 1].y + points[idx].y * 2f + points[idx + 1].y) * 0.25f
                    )
                    val smoothed = Vec2(
                        points[idx].x + (avg.x - points[idx].x) * 0.45f,
                        points[idx].y + (avg.y - points[idx].y) * 0.45f
                    )
                    if (!isInsideForbiddenZone(smoothed, state)) {
                        points[idx] = smoothed
                    }
                }
            }
        }

        val updated = state.copy(
            rawPoints = points,
            inkUsedLength = inkUsed,
            isActivelyDrawing = true
        )
        return if (hitEmpty) {
            finalizeStroke(updated) to addedLength
        } else {
            updated to addedLength
        }
    }

    fun smoothQuadraticBezierPath(points: List<Vec2>, subdivisions: Int = 3): List<Vec2> {
        if (points.size < 3) return points

        // Light Laplacian pass on interior points to eliminate micro-kinks before Bezier spline
        val preSmoothed = points.toMutableList()
        if (points.size >= 5) {
            for (i in 1 until points.lastIndex) {
                val pPrev = points[i - 1]
                val pCurr = points[i]
                val pNext = points[i + 1]
                preSmoothed[i] = Vec2(
                    pCurr.x * 0.80f + (pPrev.x + pNext.x) * 0.10f,
                    pCurr.y * 0.80f + (pPrev.y + pNext.y) * 0.10f
                )
            }
        }

        val result = ArrayList<Vec2>(preSmoothed.size * subdivisions)
        result.add(preSmoothed.first())

        val firstMid = (preSmoothed[0] + preSmoothed[1]) * 0.5f
        if ((firstMid - result.last()).length() >= 4f) {
            result.add(firstMid)
        }

        for (i in 1 until preSmoothed.lastIndex) {
            val p0 = (preSmoothed[i - 1] + preSmoothed[i]) * 0.5f
            val ctrl = preSmoothed[i]
            val p2 = (preSmoothed[i] + preSmoothed[i + 1]) * 0.5f

            for (step in 1..subdivisions) {
                val t = step.toFloat() / subdivisions
                val oneMinusT = 1f - t
                // Quadratic Bezier: B(t) = (1-t)^2 * P0 + 2(1-t)t * ctrl + t^2 * P2
                val bx = oneMinusT * oneMinusT * p0.x + 2f * oneMinusT * t * ctrl.x + t * t * p2.x
                val by = oneMinusT * oneMinusT * p0.y + 2f * oneMinusT * t * ctrl.y + t * t * p2.y
                val pt = Vec2(bx, by)
                if ((pt - result.last()).length() >= 4f || (i == preSmoothed.lastIndex - 1 && step == subdivisions)) {
                    result.add(pt)
                }
            }
        }

        val endPt = preSmoothed.last()
        if ((endPt - result.last()).length() >= 2f) {
            result.add(endPt)
        }
        return result
    }

    fun finalizeStroke(state: PhysicsWorldState): PhysicsWorldState {
        if (state.phase != GamePhase.DRAWING) return state
        if (state.rawPoints.size < 2 || state.inkUsedLength < 18f) {
            return state.copy(
                rawPoints = emptyList(),
                inkUsedLength = 0f,
                isActivelyDrawing = false
            )
        }

        val smoothedRaw = smoothQuadraticBezierPath(state.rawPoints, subdivisions = 3)
        val bezierPoints = smoothedRaw.mapIndexed { idx, pt ->
            if (idx == 0 || idx == smoothedRaw.lastIndex) pt
            else if (isInsideForbiddenZone(pt, state)) clampOutOfForbidden(pt, smoothedRaw[idx - 1], state)
            else pt
        }

        val body = buildPolylineBody(bezierPoints, state.inkUsedLength)
        return state.copy(
            phase = GamePhase.SIMULATING,
            rawPoints = bezierPoints,
            polylineBody = body,
            timeRemaining = SIMULATION_DURATION,
            elapsedTime = 0f,
            isActivelyDrawing = false
        )
    }

    fun buildPolylineBody(points: List<Vec2>, totalLength: Float): DrawnPolylineBody {
        var cx = 0f
        var cy = 0f
        for (pt in points) {
            cx += pt.x
            cy += pt.y
        }
        cx /= points.size
        cy /= points.size

        val local = points.map { Vec2(it.x - cx, it.y - cy) }
        val mass = max(1.6f, totalLength * 0.025f)
        var inertia = 0f
        val pointMass = mass / local.size
        for (lp in local) {
            inertia += pointMass * (lp.x * lp.x + lp.y * lp.y)
        }
        inertia = max(900f, inertia)

        return DrawnPolylineBody(
            localPoints = local,
            centerX = cx,
            centerY = cy,
            angle = 0f,
            vx = 0f,
            vy = 0f,
            omega = 0f,
            mass = mass,
            inertia = inertia,
            totalLength = totalLength
        )
    }

    fun polylineWorldPoints(body: DrawnPolylineBody): List<Vec2> {
        val cosA = cos(body.angle)
        val sinA = sin(body.angle)
        return body.localPoints.map { lp ->
            Vec2(
                body.centerX + lp.x * cosA - lp.y * sinA,
                body.centerY + lp.x * sinA + lp.y * cosA
            )
        }
    }

    fun step(state: PhysicsWorldState, rawDt: Float): Pair<PhysicsWorldState, PhysicsStepEvents> {
        val dt = rawDt.coerceIn(0.001f, 0.033f)

        if (state.phase == GamePhase.DRAWING) {
            val nextWarn = (state.forbiddenWarningTimer - dt).coerceAtLeast(0f)
            val nextParticles = updateAmbientParticles(state, dt)
            val penTarget = state.rawPoints.lastOrNull()
            val look = if (penTarget != null) {
                (penTarget - Vec2(state.hero.x, state.hero.y)).normalized()
            } else {
                Vec2(0f, -0.25f)
            }
            val nextHero = state.hero.copy(
                lookDirX = state.hero.lookDirX + (look.x - state.hero.lookDirX) * (dt * 10f).coerceAtMost(1f),
                lookDirY = state.hero.lookDirY + (look.y - state.hero.lookDirY) * (dt * 10f).coerceAtMost(1f)
            )
            val closestLava = computeClosestLavaDistance(state.level.lavaPits, Vec2(nextHero.x, nextHero.y))
            return state.copy(
                elapsedTime = state.elapsedTime + dt,
                forbiddenWarningTimer = nextWarn,
                particles = nextParticles,
                hero = nextHero
            ) to PhysicsStepEvents(closestLavaDistance = closestLava)
        }

        if (state.phase == GamePhase.PAUSED || state.phase == GamePhase.VICTORY) {
            val nextParticles = updateAmbientParticles(state, dt)
            return state.copy(
                elapsedTime = state.elapsedTime + dt,
                particles = nextParticles
            ) to PhysicsStepEvents()
        }

        if (state.phase == GamePhase.FAILED) {
            val nextSlow = (state.slowMoRemaining - dt).coerceAtLeast(0f)
            val nextParticles = updateAmbientParticles(state, dt * 0.35f)
            return state.copy(
                elapsedTime = state.elapsedTime + dt,
                slowMoRemaining = nextSlow,
                shakeIntensity = (state.shakeIntensity - dt * 18f).coerceAtLeast(0f),
                particles = nextParticles
            ) to PhysicsStepEvents()
        }

        // SIMULATING phase
        val prevTime = state.timeRemaining
        val newTime = (prevTime - dt).coerceAtLeast(0f)
        val newElapsed = state.elapsedTime + dt

        var urgentTick: Int? = null
        val prevCeil = kotlin.math.ceil(prevTime.toDouble()).toInt()
        val newCeil = kotlin.math.ceil(newTime.toDouble()).toInt()
        if (newCeil != prevCeil && newCeil in 1..3) {
            urgentTick = newCeil
        }

        // Spawn pending bees
        val spawnedBees = state.bees.toMutableList()
        val stillPending = mutableListOf<PendingBeeSpawn>()
        for (pending in state.pendingBeeSpawns) {
            if (newElapsed >= pending.spawnDelay) {
                val angle = -PI.toFloat() * 0.2f + (pending.beeId % 7 - 3) * 0.38f
                val speed = 130f + (pending.beeId % 4) * 22f
                spawnedBees += BeeState(
                    id = pending.beeId,
                    x = pending.x + (pending.beeId % 5 - 2) * 4f,
                    y = pending.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed + 40f,
                    wingPhase = pending.beeId * 1.3f,
                    wanderSeed = pending.beeId * 2.17f
                )
            } else {
                stillPending += pending
            }
        }

        var poly = state.polylineBody
        var hero = state.hero
        val boulders = state.boulders.toMutableList()
        val springs = state.springs.map {
            it.copy(compression = (it.compression - dt * 3.5f).coerceAtLeast(0f))
        }.toMutableList()
        val particles = updateAmbientParticles(state, dt).toMutableList()

        var bounceCount = 0
        var springTriggered = false
        var failReason: FailReason? = null
        var shake = (state.shakeIntensity - dt * 18f).coerceAtLeast(0f)

        val subDt = dt / SUB_STEPS
        var worldPts = poly?.let { polylineWorldPoints(it) } ?: emptyList()

        for (sub in 0 until SUB_STEPS) {
            // 1. Update Polyline Rigid Body
            val initPoly1 = poly
            if (initPoly1 != null) {
                var pBody: DrawnPolylineBody = initPoly1
                val vx = pBody.vx * (1f - 0.55f * subDt)
                val vy = (pBody.vy + GRAVITY * subDt) * (1f - 0.35f * subDt)
                val omega = pBody.omega * (1f - 1.35f * subDt)
                val cx = pBody.centerX + vx * subDt
                val cy = pBody.centerY + vy * subDt
                val angle = pBody.angle + omega * subDt

                pBody = pBody.copy(centerX = cx, centerY = cy, angle = angle, vx = vx, vy = vy, omega = omega)
                worldPts = polylineWorldPoints(pBody)

                // Collide polyline vertices against static platforms + world walls
                val samples = buildCollisionSamples(worldPts)
                for (pt in samples) {
                    // World left/right/bottom bounds
                    if (pt.x < LINE_RADIUS) {
                        val pen = LINE_RADIUS - pt.x
                        pBody = resolvePolyContact(pBody, pt, Vec2(1f, 0f), pen, 0.1f, 0.5f)
                    } else if (pt.x > WORLD_WIDTH - LINE_RADIUS) {
                        val pen = pt.x - (WORLD_WIDTH - LINE_RADIUS)
                        pBody = resolvePolyContact(pBody, pt, Vec2(-1f, 0f), pen, 0.1f, 0.5f)
                    }
                    if (pt.y < LINE_RADIUS) {
                        val pen = LINE_RADIUS - pt.y
                        pBody = resolvePolyContact(pBody, pt, Vec2(0f, 1f), pen, 0.1f, 0.4f)
                    } else if (pt.y > WORLD_HEIGHT - LINE_RADIUS) {
                        val pen = pt.y - (WORLD_HEIGHT - LINE_RADIUS)
                        pBody = resolvePolyContact(pBody, pt, Vec2(0f, -1f), pen, 0.08f, 0.6f)
                    }

                    for (plat in state.level.platforms) {
                        val contact = circleVsRect(pt, LINE_RADIUS, plat)
                        if (contact != null) {
                            pBody = resolvePolyContact(
                                pBody,
                                pt,
                                contact.first,
                                contact.second,
                                restitution = 0.06f,
                                friction = 0.72f
                            )
                        }
                    }

                    for (sIdx in springs.indices) {
                        val sp = springs[sIdx].spec
                        val spPlat = Platform(sp.x, sp.y, sp.width, sp.height)
                        val contact = circleVsRect(pt, LINE_RADIUS, spPlat)
                        if (contact != null && contact.first.y < -0.5f) {
                            pBody = resolvePolyContact(
                                pBody,
                                pt,
                                contact.first,
                                contact.second,
                                restitution = 0.85f,
                                friction = 0.3f
                            )
                            pBody = pBody.copy(vy = min(pBody.vy, -sp.boostVelocity * 0.58f))
                            springs[sIdx] = springs[sIdx].copy(compression = 1f)
                            springTriggered = true
                        }
                    }
                }
                poly = pBody
                worldPts = polylineWorldPoints(pBody)
            }

            // 2. Update Hero
            var hvx = hero.vx * (1f - 1.2f * subDt)
            var hvy = hero.vy + GRAVITY * subDt
            var hx = hero.x + hvx * subDt
            var hy = hero.y + hvy * subDt

            if (hx < hero.radius) {
                hx = hero.radius
                hvx = abs(hvx) * 0.3f
            } else if (hx > WORLD_WIDTH - hero.radius) {
                hx = WORLD_WIDTH - hero.radius
                hvx = -abs(hvx) * 0.3f
            }
            if (hy < hero.radius) {
                hy = hero.radius
                hvy = abs(hvy) * 0.2f
            }

            // Hero vs Platforms
            for (plat in state.level.platforms) {
                val contact = circleVsRect(Vec2(hx, hy), hero.radius, plat)
                if (contact != null) {
                    val n = contact.first
                    val pen = contact.second
                    hx += n.x * pen
                    hy += n.y * pen
                    val vn = hvx * n.x + hvy * n.y
                    if (vn < 0f) {
                        hvx -= 1.08f * vn * n.x
                        hvy -= 1.08f * vn * n.y
                        if (n.y < -0.5f) {
                            hvx *= (1f - 4.5f * subDt).coerceAtLeast(0f)
                        }
                    }
                }
            }

            // Hero vs Springs
            for (sIdx in springs.indices) {
                val sp = springs[sIdx].spec
                val contact = circleVsRect(Vec2(hx, hy), hero.radius, Platform(sp.x, sp.y, sp.width, sp.height))
                if (contact != null) {
                    val n = contact.first
                    val pen = contact.second
                    hx += n.x * pen
                    hy += n.y * pen
                    if (n.y < -0.4f) {
                        hvy = -sp.boostVelocity
                        springs[sIdx] = springs[sIdx].copy(compression = 1f)
                        springTriggered = true
                    }
                }
            }

            // Hero vs Polyline segments
            val initPolyHero = poly
            if (initPolyHero != null && worldPts.size >= 2) {
                var pHero: DrawnPolylineBody = initPolyHero
                val heroMass = 2.6f
                for (i in 0 until worldPts.lastIndex) {
                    val a = worldPts[i]
                    val b = worldPts[i + 1]
                    val closest = closestPointOnSegment(Vec2(hx, hy), a, b)
                    val diff = Vec2(hx, hy) - closest
                    val dist = diff.length()
                    val minDist = hero.radius + LINE_RADIUS
                    if (dist < minDist) {
                        val n = if (dist > 0.001f) diff / dist else Vec2(0f, -1f)
                        val pen = minDist - dist
                        val totalInvMass = (1f / heroMass) + (1f / pHero.mass)
                        val heroShare = (1f / heroMass) / totalInvMass
                        val polyShare = (1f / pHero.mass) / totalInvMass

                        hx += n.x * pen * heroShare
                        hy += n.y * pen * heroShare
                        pHero = pHero.copy(
                            centerX = pHero.centerX - n.x * pen * polyShare,
                            centerY = pHero.centerY - n.y * pen * polyShare
                        )

                        val r = closest - Vec2(pHero.centerX, pHero.centerY)
                        val polyVelAtContact = Vec2(
                            pHero.vx - pHero.omega * r.y,
                            pHero.vy + pHero.omega * r.x
                        )
                        val relVel = Vec2(hvx, hvy) - polyVelAtContact
                        val vn = relVel.dot(n)
                        if (vn < 0f) {
                            val rCrossN = r.cross(n)
                            val denom = (1f / heroMass) + (1f / pHero.mass) + (rCrossN * rCrossN) / pHero.inertia
                            val j = -1.12f * vn / denom
                            hvx += (j * n.x) / heroMass
                            hvy += (j * n.y) / heroMass
                            pHero = pHero.copy(
                                vx = pHero.vx - (j * n.x) / pHero.mass,
                                vy = pHero.vy - (j * n.y) / pHero.mass,
                                omega = pHero.omega - r.cross(n * j) / pHero.inertia
                            )
                            if (n.y < -0.45f) {
                                hvx *= (1f - 3.5f * subDt).coerceAtLeast(0f)
                            }
                        }
                    }
                }
                poly = pHero
                worldPts = polylineWorldPoints(pHero)
            }

            hero = hero.copy(x = hx, y = hy, vx = hvx, vy = hvy)

            // 3. Update Boulders
            for (bIdx in boulders.indices) {
                var b = boulders[bIdx]
                if (b.delayRemaining > 0f) {
                    boulders[bIdx] = b.copy(delayRemaining = (b.delayRemaining - subDt).coerceAtLeast(0f))
                    continue
                }
                var bvx = b.vx
                var bvy = b.vy + GRAVITY * 0.95f * subDt
                var bx = b.x + bvx * subDt
                var by = b.y + bvy * subDt
                var bangle = b.angle + (bvx / b.radius) * subDt

                if (bx < b.radius) {
                    bx = b.radius
                    bvx = abs(bvx) * 0.5f
                } else if (bx > WORLD_WIDTH - b.radius) {
                    bx = WORLD_WIDTH - b.radius
                    bvx = -abs(bvx) * 0.5f
                }

                for (plat in state.level.platforms) {
                    val contact = circleVsRect(Vec2(bx, by), b.radius, plat)
                    if (contact != null) {
                        val n = contact.first
                        val pen = contact.second
                        bx += n.x * pen
                        by += n.y * pen
                        val vn = bvx * n.x + bvy * n.y
                        if (vn < 0f) {
                            bvx -= 1.35f * vn * n.x
                            bvy -= 1.35f * vn * n.y
                        }
                    }
                }

                for (sIdx in springs.indices) {
                    val sp = springs[sIdx].spec
                    val contact = circleVsRect(Vec2(bx, by), b.radius, Platform(sp.x, sp.y, sp.width, sp.height))
                    if (contact != null && contact.first.y < -0.3f) {
                        val n = contact.first
                        bx += n.x * contact.second
                        by += n.y * contact.second
                        bvy = -sp.boostVelocity * 0.85f
                        springs[sIdx] = springs[sIdx].copy(compression = 1f)
                        springTriggered = true
                    }
                }

                val initPolyBoulder = poly
                if (initPolyBoulder != null && worldPts.size >= 2) {
                    var pBoulder: DrawnPolylineBody = initPolyBoulder
                    val boulderMass = 5.5f
                    for (i in 0 until worldPts.lastIndex) {
                        val segA = worldPts[i]
                        val segB = worldPts[i + 1]
                        val closest = closestPointOnSegment(Vec2(bx, by), segA, segB)
                        val diff = Vec2(bx, by) - closest
                        val dist = diff.length()
                        val minDist = b.radius + LINE_RADIUS
                        if (dist < minDist) {
                            val n = if (dist > 0.001f) diff / dist else Vec2(0f, -1f)
                            val pen = minDist - dist
                            bx += n.x * pen * 0.75f
                            by += n.y * pen * 0.75f
                            pBoulder = pBoulder.copy(
                                centerX = pBoulder.centerX - n.x * pen * 0.25f,
                                centerY = pBoulder.centerY - n.y * pen * 0.25f
                            )

                            val r = closest - Vec2(pBoulder.centerX, pBoulder.centerY)
                            val polyVel = Vec2(pBoulder.vx - pBoulder.omega * r.y, pBoulder.vy + pBoulder.omega * r.x)
                            val vn = (Vec2(bvx, bvy) - polyVel).dot(n)
                            if (vn < 0f) {
                                val rCrossN = r.cross(n)
                                val denom = (1f / boulderMass) + (1f / pBoulder.mass) + (rCrossN * rCrossN) / pBoulder.inertia
                                val j = -1.42f * vn / denom
                                bvx += (j * n.x) / boulderMass
                                bvy += (j * n.y) / boulderMass
                                pBoulder = pBoulder.copy(
                                    vx = pBoulder.vx - (j * n.x) / pBoulder.mass,
                                    vy = pBoulder.vy - (j * n.y) / pBoulder.mass,
                                    omega = pBoulder.omega - r.cross(n * j) / pBoulder.inertia
                                )
                                if (sub == 0 && abs(vn) > 65f) {
                                    bounceCount++
                                    shake = max(shake, 5f)
                                    repeat(4) {
                                        particles += Particle(
                                            x = closest.x,
                                            y = closest.y,
                                            vx = (Random.nextFloat() - 0.5f) * 140f,
                                            vy = (Random.nextFloat() - 0.7f) * 140f,
                                            life = 0.35f,
                                            maxLife = 0.35f,
                                            size = 4f,
                                            color = Color(0xFFD6D3D1),
                                            kind = ParticleKind.DUST
                                        )
                                    }
                                }
                            }
                        }
                    }
                    poly = pBoulder
                    worldPts = polylineWorldPoints(pBoulder)
                }

                boulders[bIdx] = b.copy(x = bx, y = by, vx = bvx, vy = bvy, angle = bangle)

                if ((Vec2(bx, by) - Vec2(hero.x, hero.y)).length() < b.radius + hero.radius - 3f) {
                    failReason = FailReason.BOULDER_CRUSH
                }
            }

            // 4. Update Bees
            val maxBeeSpeed = 205f + state.level.id * 3.5f
            for (i in spawnedBees.indices) {
                var bee = spawnedBees[i]
                val toHero = Vec2(hero.x - bee.x, hero.y - bee.y)
                val distHero = toHero.length()
                val dirHero = if (distHero > 0.01f) toHero / distHero else Vec2(0f, 1f)
                val perp = Vec2(-dirHero.y, dirHero.x)

                val weave = sin(newElapsed * 8.5f + bee.wanderSeed) *
                    (if (distHero > 90f) 0.45f else 0.18f)
                val seekDir = (dirHero + perp * weave).normalized()

                // Bee-bee separation
                var sepX = 0f
                var sepY = 0f
                for (j in spawnedBees.indices) {
                    if (i == j) continue
                    val other = spawnedBees[j]
                    val dx = bee.x - other.x
                    val dy = bee.y - other.y
                    val dSq = dx * dx + dy * dy
                    if (dSq in 0.01f..420f) {
                        val invD = 1f / kotlin.math.sqrt(dSq)
                        sepX += dx * invD * 22f
                        sepY += dy * invD * 22f
                    }
                }

                val accel = 540f
                var bvx = bee.vx + (seekDir.x * accel + sepX * 14f) * subDt
                var bvy = bee.vy + (seekDir.y * accel + sepY * 14f) * subDt
                val speed = kotlin.math.hypot(bvx, bvy)
                if (speed > maxBeeSpeed) {
                    bvx = (bvx / speed) * maxBeeSpeed
                    bvy = (bvy / speed) * maxBeeSpeed
                }

                var bx = (bee.x + bvx * subDt).coerceIn(BEE_RADIUS, WORLD_WIDTH - BEE_RADIUS)
                var by = (bee.y + bvy * subDt).coerceIn(BEE_RADIUS, WORLD_HEIGHT - BEE_RADIUS)
                if (bx <= BEE_RADIUS || bx >= WORLD_WIDTH - BEE_RADIUS) bvx = -bvx * 0.7f
                if (by <= BEE_RADIUS || by >= WORLD_HEIGHT - BEE_RADIUS) bvy = -bvy * 0.7f

                var cd = (bee.bounceCooldown - subDt).coerceAtLeast(0f)

                // Bee vs Platforms
                for (plat in state.level.platforms) {
                    val contact = circleVsRect(Vec2(bx, by), bee.radius, plat)
                    if (contact != null) {
                        val n = contact.first
                        bx += n.x * contact.second
                        by += n.y * contact.second
                        val vn = bvx * n.x + bvy * n.y
                        if (vn < 0f) {
                            bvx -= 1.75f * vn * n.x
                            bvy -= 1.75f * vn * n.y
                        }
                    }
                }

                // Bee vs Springs
                for (sIdx in springs.indices) {
                    val sp = springs[sIdx].spec
                    val contact = circleVsRect(Vec2(bx, by), bee.radius, Platform(sp.x, sp.y, sp.width, sp.height))
                    if (contact != null) {
                        val n = contact.first
                        bx += n.x * contact.second
                        by += n.y * contact.second
                        bvy = -sp.boostVelocity * 0.72f
                        springs[sIdx] = springs[sIdx].copy(compression = 0.7f)
                        springTriggered = true
                    }
                }

                // Bee vs Drawn Polyline (find deepest smooth contact on Quadratic-Bezier shield)
                val initPolyBee = poly
                if (initPolyBee != null && worldPts.size >= 2) {
                    var pBee: DrawnPolylineBody = initPolyBee
                    var bestDist = bee.radius + LINE_RADIUS
                    var bestClosest: Vec2? = null
                    var bestDiff = Vec2(0f, 0f)

                    for (sIdx in 0 until worldPts.lastIndex) {
                        val segA = worldPts[sIdx]
                        val segB = worldPts[sIdx + 1]
                        val closest = closestPointOnSegment(Vec2(bx, by), segA, segB)
                        val diff = Vec2(bx, by) - closest
                        val dist = diff.length()
                        if (dist < bestDist) {
                            bestDist = dist
                            bestClosest = closest
                            bestDiff = diff
                        }
                    }

                    if (bestClosest != null) {
                        val minDist = bee.radius + LINE_RADIUS
                        val n = if (bestDist > 0.001f) bestDiff / bestDist else Vec2(0f, -1f)
                        val pen = minDist - bestDist
                        bx += n.x * pen
                        by += n.y * pen
                        val vn = bvx * n.x + bvy * n.y
                        if (vn < 0f) {
                            val bounce = 1.88f
                            bvx -= bounce * vn * n.x
                            bvy -= bounce * vn * n.y
                            // Smooth tangential glide along curved shield
                            val tangent = Vec2(-n.y, n.x)
                            val jitter = sin(bee.wanderSeed + newElapsed * 15f) * 28f
                            bvx += tangent.x * jitter
                            bvy += tangent.y * jitter

                            // Transfer gentle bee push impulse to polyline
                            val imp = 0.085f * abs(vn)
                            val r = bestClosest - Vec2(pBee.centerX, pBee.centerY)
                            pBee = pBee.copy(
                                vx = pBee.vx - (n.x * imp) / pBee.mass,
                                vy = pBee.vy - (n.y * imp) / pBee.mass,
                                omega = pBee.omega - r.cross(n * (-imp)) / pBee.inertia
                            )
                            poly = pBee

                            if (cd <= 0f) {
                                bounceCount++
                                cd = 0.14f
                                if (particles.size < 110) {
                                    particles += Particle(
                                        x = bestClosest.x,
                                        y = bestClosest.y,
                                        vx = 0f,
                                        vy = 0f,
                                        life = 0.24f,
                                        maxLife = 0.24f,
                                        size = 14f,
                                        color = Color(0xFFFFF59D),
                                        kind = ParticleKind.RING
                                    )
                                    repeat(3) {
                                        particles += Particle(
                                            x = bestClosest.x,
                                            y = bestClosest.y,
                                            vx = n.x * 85f + (Random.nextFloat() - 0.5f) * 85f,
                                            vy = n.y * 85f + (Random.nextFloat() - 0.5f) * 85f,
                                            life = 0.28f,
                                            maxLife = 0.28f,
                                            size = 3.8f,
                                            color = Color(0xFFFFEA00),
                                            kind = ParticleKind.SPARK
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val targetTilt = (bvx / maxBeeSpeed).coerceIn(-1f, 1f) * 18f
                val smoothAngle = bee.visualAngle + (targetTilt - bee.visualAngle) * (subDt * 18f).coerceAtMost(1f)
                bee = bee.copy(
                    x = bx,
                    y = by,
                    vx = bvx,
                    vy = bvy,
                    wingPhase = bee.wingPhase + subDt * 52f,
                    bounceCooldown = cd,
                    visualAngle = smoothAngle
                )
                spawnedBees[i] = bee

                // Bee vs Hero check
                val heroBeeDist = (Vec2(bx, by) - Vec2(hero.x, hero.y)).length()
                if (heroBeeDist < bee.radius + hero.radius - 3.5f) {
                    failReason = FailReason.BEE_STING
                }
            }
        }

        // Check Lava & Out of bounds
        for (lava in state.level.lavaPits) {
            if (circleVsRect(Vec2(hero.x, hero.y), hero.radius - 2f, Platform(lava.x, lava.y + 4f, lava.width, lava.height)) != null) {
                failReason = FailReason.LAVA_BURN
            }
        }
        if (hero.y > WORLD_HEIGHT + 24f) {
            failReason = FailReason.OUT_OF_BOUNDS
        }

        // Closest bee distance for scared facial expression, pupil tracking & dynamic buzz audio
        var closestBee = 999f
        var closestBeePos: Vec2? = null
        for (bee in spawnedBees) {
            val d = (Vec2(bee.x, bee.y) - Vec2(hero.x, hero.y)).length()
            if (d < closestBee) {
                closestBee = d
                closestBeePos = Vec2(bee.x, bee.y)
            }
        }
        val scared = ((135f - closestBee) / 95f).coerceIn(0f, 1f)
        if (scared > 0.45f && Random.nextFloat() < dt * 5f && particles.size < 110) {
            val side = if (Random.nextBoolean()) 1f else -1f
            particles += Particle(
                x = hero.x + side * (hero.radius + 4f),
                y = hero.y - 10f,
                vx = side * (35f + Random.nextFloat() * 35f),
                vy = -45f - Random.nextFloat() * 30f,
                life = 0.5f,
                maxLife = 0.5f,
                size = 4.5f,
                color = Color(0xFF38BDF8),
                kind = ParticleKind.SWEAT
            )
        }
        val lookDir = closestBeePos?.let { (it - Vec2(hero.x, hero.y)).normalized() } ?: Vec2(0f, 0f)
        val velStretch = (abs(hero.vy) / 550f).coerceIn(0f, 0.16f)
        val targetSy = 1f + velStretch - scared * 0.04f
        val targetSx = 1f / targetSy
        val lerpRate = (dt * 14f).coerceAtMost(1f)
        hero = hero.copy(
            scaredLevel = scared,
            squashX = hero.squashX + (targetSx - hero.squashX) * lerpRate,
            squashY = hero.squashY + (targetSy - hero.squashY) * lerpRate,
            lookDirX = hero.lookDirX + (lookDir.x - hero.lookDirX) * lerpRate,
            lookDirY = hero.lookDirY + (lookDir.y - hero.lookDirY) * lerpRate
        )

        val closestLava = computeClosestLavaDistance(state.level.lavaPits, Vec2(hero.x, hero.y))

        if (failReason != null) {
            hero = hero.copy(isHurt = true, hurtAngle = Random.nextFloat() * 0.4f - 0.2f)
            return state.copy(
                phase = GamePhase.FAILED,
                timeRemaining = newTime,
                elapsedTime = newElapsed,
                polylineBody = poly,
                hero = hero,
                bees = spawnedBees,
                pendingBeeSpawns = stillPending,
                boulders = boulders,
                springs = springs,
                particles = particles,
                failReason = failReason,
                slowMoRemaining = 0.65f,
                shakeIntensity = if (failReason == FailReason.BOULDER_CRUSH) 14f else 10f
            ) to PhysicsStepEvents(
                bounceCount = bounceCount,
                springBounce = springTriggered,
                justFailed = failReason,
                closestBeeDistance = closestBee,
                closestLavaDistance = closestLava
            )
        }

        if (newTime <= 0f) {
            val stars = when {
                state.inkRemainingPercent > 70 -> 3
                state.inkRemainingPercent > 40 -> 2
                else -> 1
            }
            val confettiColors = listOf(
                Color(0xFFFFB300),
                Color(0xFF22C55E),
                Color(0xFF38BDF8),
                Color(0xFFEF4444),
                Color(0xFFA855F7)
            )
            repeat(42) { idx ->
                particles += Particle(
                    x = hero.x + (Random.nextFloat() - 0.5f) * 60f,
                    y = hero.y - 10f,
                    vx = (Random.nextFloat() - 0.5f) * 260f,
                    vy = -120f - Random.nextFloat() * 240f,
                    life = 1.8f + Random.nextFloat() * 0.8f,
                    maxLife = 2.6f,
                    size = 6f + Random.nextFloat() * 4f,
                    color = confettiColors[idx % confettiColors.size],
                    kind = ParticleKind.CONFETTI,
                    rotation = Random.nextFloat() * 360f
                )
            }
            return state.copy(
                phase = GamePhase.VICTORY,
                timeRemaining = 0f,
                elapsedTime = newElapsed,
                polylineBody = poly,
                hero = hero.copy(scaredLevel = 0f),
                bees = spawnedBees,
                pendingBeeSpawns = emptyList(),
                boulders = boulders,
                springs = springs,
                particles = particles,
                earnedStars = stars
            ) to PhysicsStepEvents(
                bounceCount = bounceCount,
                springBounce = springTriggered,
                justSucceeded = true,
                closestBeeDistance = closestBee,
                closestLavaDistance = closestLava
            )
        }

        return state.copy(
            timeRemaining = newTime,
            elapsedTime = newElapsed,
            polylineBody = poly,
            hero = hero,
            bees = spawnedBees,
            pendingBeeSpawns = stillPending,
            boulders = boulders,
            springs = springs,
            particles = particles,
            shakeIntensity = shake
        ) to PhysicsStepEvents(
            bounceCount = bounceCount,
            springBounce = springTriggered,
            urgentTickSecond = urgentTick,
            closestBeeDistance = closestBee,
            closestLavaDistance = closestLava
        )
    }

    fun computeClosestLavaDistance(lavaPits: List<LavaPit>, pos: Vec2): Float {
        if (lavaPits.isEmpty()) return 999f
        var best = 999f
        for (lava in lavaPits) {
            val cx = pos.x.coerceIn(lava.x, lava.x + lava.width)
            val cy = pos.y.coerceIn(lava.y, lava.y + lava.height)
            val dx = pos.x - cx
            val dy = pos.y - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < best) best = dist
        }
        return best
    }

    private fun updateAmbientParticles(state: PhysicsWorldState, dt: Float): List<Particle> {
        val next = mutableListOf<Particle>()
        for (p in state.particles) {
            val nl = p.life - dt
            if (nl > 0f) {
                val grav = when (p.kind) {
                    ParticleKind.CONFETTI -> 220f
                    ParticleKind.SWEAT -> 180f
                    ParticleKind.LAVA_BUBBLE -> -35f
                    else -> 60f
                }
                next += p.copy(
                    x = p.x + p.vx * dt,
                    y = p.y + p.vy * dt,
                    vy = p.vy + grav * dt,
                    life = nl,
                    rotation = p.rotation + 180f * dt
                )
            }
        }
        for (lava in state.level.lavaPits) {
            if (Random.nextFloat() < dt * 3.4f && next.size < 105) {
                next += Particle(
                    x = lava.x + 10f + Random.nextFloat() * (lava.width - 20f).coerceAtLeast(10f),
                    y = lava.y + 8f,
                    vx = (Random.nextFloat() - 0.5f) * 14f,
                    vy = -28f - Random.nextFloat() * 22f,
                    life = 0.65f,
                    maxLife = 0.65f,
                    size = 4f + Random.nextFloat() * 3.5f,
                    color = Color(0xFFFFAB40),
                    kind = ParticleKind.LAVA_BUBBLE
                )
            }
        }
        if ((state.level.skyTheme == SkyTheme.VOLCANIC_SUNSET || state.level.skyTheme == SkyTheme.MOLTEN_STORM) &&
            Random.nextFloat() < dt * 4.5f && next.size < 105
        ) {
            next += Particle(
                x = Random.nextFloat() * WORLD_WIDTH,
                y = WORLD_HEIGHT - 80f,
                vx = (Random.nextFloat() - 0.5f) * 26f,
                vy = -55f - Random.nextFloat() * 45f,
                life = 2.4f,
                maxLife = 2.4f,
                size = 2.5f + Random.nextFloat() * 2f,
                color = Color(0xFFFFCC80),
                kind = ParticleKind.EMBER
            )
        }
        return next
    }

    private fun buildCollisionSamples(pts: List<Vec2>): List<Vec2> {
        if (pts.size < 2) return pts
        val result = ArrayList<Vec2>(pts.size * 2)
        for (i in 0 until pts.lastIndex) {
            result.add(pts[i])
            val segLen = (pts[i + 1] - pts[i]).length()
            if (segLen > 12f) {
                result.add((pts[i] + pts[i + 1]) * 0.5f)
            }
        }
        result.add(pts.last())
        return result
    }

    private fun resolvePolyContact(
        body: DrawnPolylineBody,
        contactPt: Vec2,
        normal: Vec2,
        penetration: Float,
        restitution: Float,
        friction: Float
    ): DrawnPolylineBody {
        val corr = penetration * 0.55f
        val newCx = body.centerX + normal.x * corr
        val newCy = body.centerY + normal.y * corr

        val r = contactPt - Vec2(newCx, newCy)
        val vContact = Vec2(body.vx - body.omega * r.y, body.vy + body.omega * r.x)
        val vn = vContact.dot(normal)
        if (vn >= 0f) {
            return body.copy(centerX = newCx, centerY = newCy)
        }

        val rCrossN = r.cross(normal)
        val denom = (1f / body.mass) + (rCrossN * rCrossN) / body.inertia
        val j = -(1f + restitution) * vn / denom

        var nvx = body.vx + (j * normal.x) / body.mass
        var nvy = body.vy + (j * normal.y) / body.mass
        var nOmega = body.omega + r.cross(normal * j) / body.inertia

        // Friction impulse
        val tangent = Vec2(-normal.y, normal.x)
        val vt = vContact.dot(tangent)
        val rCrossT = r.cross(tangent)
        val denomT = (1f / body.mass) + (rCrossT * rCrossT) / body.inertia
        val jt = (-vt / denomT).coerceIn(-friction * j, friction * j)

        nvx += (jt * tangent.x) / body.mass
        nvy += (jt * tangent.y) / body.mass
        nOmega += r.cross(tangent * jt) / body.inertia

        return body.copy(
            centerX = newCx,
            centerY = newCy,
            vx = nvx,
            vy = nvy,
            omega = nOmega.coerceIn(-9f, 9f)
        )
    }

    private fun circleVsRect(center: Vec2, radius: Float, rect: Platform): Pair<Vec2, Float>? {
        val closestX = center.x.coerceIn(rect.x, rect.x + rect.width)
        val closestY = center.y.coerceIn(rect.y, rect.y + rect.height)
        val dx = center.x - closestX
        val dy = center.y - closestY
        val distSq = dx * dx + dy * dy
        if (distSq >= radius * radius) return null

        val dist = kotlin.math.sqrt(distSq)
        return if (dist > 0.0001f) {
            Vec2(dx / dist, dy / dist) to (radius - dist)
        } else {
            // Center is inside rect: push out nearest edge
            val dLeft = center.x - rect.x
            val dRight = (rect.x + rect.width) - center.x
            val dTop = center.y - rect.y
            val dBottom = (rect.y + rect.height) - center.y
            val m = minOf(dLeft, dRight, dTop, dBottom)
            when (m) {
                dTop -> Vec2(0f, -1f) to (radius + dTop)
                dBottom -> Vec2(0f, 1f) to (radius + dBottom)
                dLeft -> Vec2(-1f, 0f) to (radius + dLeft)
                else -> Vec2(1f, 0f) to (radius + dRight)
            }
        }
    }

    private fun closestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
        val ab = b - a
        val lenSq = ab.x * ab.x + ab.y * ab.y
        if (lenSq < 1e-5f) return a
        val t = ((p - a).dot(ab) / lenSq).coerceIn(0f, 1f)
        return a + ab * t
    }
}
