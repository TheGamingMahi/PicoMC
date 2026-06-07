package io.thegamingmahi.picomc.performance

import io.thegamingmahi.picomc.PicoMC
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

class DynamicAdjuster(
    private val plugin: PicoMC,
    private val tpsMonitor: TPSMonitor
) {

    private var task: BukkitTask? = null

    private val VIEW_DISTANCE_MIN   = 2
    private val VIEW_DISTANCE_MAX   = 10
    private val VIEW_DISTANCE_START = 5

    private val TPS_DROP_THRESHOLD = 19.0
    private val TPS_STABLE         = 19.8

    // 15 minutes: 90 checks × 10 seconds
    private val STABLE_CHECKS_REQUIRED = 90
    // 30 seconds between reductions: 3 checks × 10 seconds
    private val REDUCE_COOLDOWN_CHECKS = 3
    // 5 minute cooldown after change: 30 checks × 10 seconds
    private val CHANGE_COOLDOWN_CHECKS = 30

    private var currentViewDistance = VIEW_DISTANCE_START
    private var stableCheckCount    = 0
    private var cooldownCount       = 0
    private var reduceCooldown      = 0
    private var inCooldown          = false

    private val simDistance get() = (currentViewDistance - 2).coerceAtLeast(2)

    private val MONSTER_LIMIT = 15
    private val ANIMAL_LIMIT  = 5
    private val WATER_LIMIT   = 2

    fun start() {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            applyViewDistance(currentViewDistance)
            applyMobLimits()
            plugin.logger.info("Starting at view distance $currentViewDistance chunks — self-tuning to maintain 19.0+ TPS")
        })

        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tune()
        }, 200L, 200L)

        plugin.logger.info("Self-tuning adjuster started — increases after 15min stable, decreases within 30sec of lag")
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun tune() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            stableCheckCount = 0
            return
        }

        val tps = tpsMonitor.currentTPS

        if (inCooldown) {
            cooldownCount++
            if (cooldownCount >= CHANGE_COOLDOWN_CHECKS) {
                inCooldown = false
                cooldownCount = 0
                plugin.logger.info("Cooldown ended — resuming TPS monitoring")
            }
            if (tps < 15.0) emergencyReduce(tps)
            return
        }

        when {
            tps < TPS_DROP_THRESHOLD -> {
                stableCheckCount = 0
                if (reduceCooldown > 0) {
                    reduceCooldown--
                    return
                }
                if (currentViewDistance > VIEW_DISTANCE_MIN) {
                    val old = currentViewDistance
                    currentViewDistance--
                    applyViewDistance(currentViewDistance)
                    reduceCooldown = REDUCE_COOLDOWN_CHECKS
                    plugin.logger.warning("TPS dropped to ${fmt(tps)} — reducing view distance $old → $currentViewDistance chunks")
                } else {
                    if (tps < 15.0) clearExcessEntities()
                    if (tps < 10.0) clearItems()
                }
            }

            tps >= TPS_STABLE -> {
                reduceCooldown = 0
                stableCheckCount++

                val minutesStable = (stableCheckCount * 10) / 60
                if (stableCheckCount % 18 == 0) {
                    plugin.logger.info("TPS stable at ${fmt(tps)} for $minutesStable minutes ($stableCheckCount/$STABLE_CHECKS_REQUIRED checks)")
                }

                if (stableCheckCount >= STABLE_CHECKS_REQUIRED && currentViewDistance < VIEW_DISTANCE_MAX) {
                    val old = currentViewDistance
                    currentViewDistance++
                    applyViewDistance(currentViewDistance)
                    stableCheckCount = 0
                    inCooldown = true
                    cooldownCount = 0
                    plugin.logger.info("TPS stable for 15 minutes — trying view distance $old → $currentViewDistance chunks (5 min cooldown)")
                }
            }

            else -> {
                reduceCooldown = 0
                stableCheckCount = 0
            }
        }
    }

    private fun emergencyReduce(tps: Double) {
        if (currentViewDistance > VIEW_DISTANCE_MIN) {
            val old = currentViewDistance
            currentViewDistance--
            applyViewDistance(currentViewDistance)
            plugin.logger.warning("Emergency: TPS critically low at ${fmt(tps)} — reducing view distance $old → $currentViewDistance")
        }
        if (tps < 10.0) clearItems()
        clearExcessEntities()
    }

    private fun applyViewDistance(view: Int) {
        val sim = (view - 2).coerceAtLeast(2)
        for (player in Bukkit.getOnlinePlayers()) {
            player.viewDistance = view
            player.simulationDistance = sim
        }
        for (world in Bukkit.getWorlds()) {
            world.viewDistance = view
            world.simulationDistance = sim
        }
    }

    private fun applyMobLimits() {
        for (world in Bukkit.getWorlds()) {
            world.monsterSpawnLimit      = MONSTER_LIMIT
            world.animalSpawnLimit       = ANIMAL_LIMIT
            world.waterAnimalSpawnLimit  = WATER_LIMIT
            world.ambientSpawnLimit      = 0
            world.waterAmbientSpawnLimit = 0
        }
    }

    private fun clearExcessEntities() {
        for (world in Bukkit.getWorlds()) {
            val monsters = world.entities.filter {
                it is org.bukkit.entity.Monster && it.customName() == null
            }
            if (monsters.size > MONSTER_LIMIT) {
                val toRemove = monsters
                    .sortedByDescending { monster ->
                        Bukkit.getOnlinePlayers().minOfOrNull { player ->
                            monster.location.distanceSquared(player.location)
                        } ?: Double.MAX_VALUE
                    }
                    .take(monsters.size - MONSTER_LIMIT)
                toRemove.forEach { it.remove() }
                if (toRemove.isNotEmpty()) {
                    plugin.logger.warning("Removed ${toRemove.size} excess monsters due to low TPS")
                }
            }
        }
    }

    private fun clearItems() {
        for (world in Bukkit.getWorlds()) {
            val old = world.entities
                .filterIsInstance<org.bukkit.entity.Item>()
                .filter { it.ticksLived > 1200 }
            old.forEach { it.remove() }
            if (old.isNotEmpty()) {
                plugin.logger.warning("Removed ${old.size} old ground items due to critical TPS")
            }
        }
    }

    fun getStatusLines(): List<String> {
        val tpsValue   = tpsMonitor.currentTPS
        val players    = Bukkit.getOnlinePlayers().size
        val runtime    = Runtime.getRuntime()
        val gameRamMB  = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxRamMB   = runtime.maxMemory() / 1024 / 1024
        val ramPercent = gameRamMB.toDouble() / maxRamMB.toDouble() * 100

        var totalEntities = 0
        for (world in Bukkit.getWorlds()) totalEntities += world.entityCount

        val tpsColor = when {
            tpsValue >= 19.5 -> "§a"
            tpsValue >= 17.0 -> "§e"
            else             -> "§c"
        }

        val ramColor = when {
            ramPercent < 60 -> "§a"
            ramPercent < 80 -> "§e"
            else            -> "§c"
        }

        val entityColor = when {
            totalEntities < MONSTER_LIMIT * 2 -> "§a"
            totalEntities < MONSTER_LIMIT * 4 -> "§e"
            else                              -> "§c"
        }

        val tuningColor = when {
            inCooldown                               -> "§e"
            tpsValue < TPS_DROP_THRESHOLD            -> "§c"
            currentViewDistance >= VIEW_DISTANCE_MAX -> "§a"
            else                                     -> "§a"
        }

        val tuningText = when {
            inCooldown                               -> "Cooldown $cooldownCount/$CHANGE_COOLDOWN_CHECKS"
            currentViewDistance >= VIEW_DISTANCE_MAX -> "At maximum"
            tpsValue < TPS_DROP_THRESHOLD            -> "Reducing — $currentViewDistance chunks"
            else                                     -> "Stable $stableCheckCount/$STABLE_CHECKS_REQUIRED"
        }

        return listOf(
            "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "§e PicoMC",
            "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "§7 TPS      ${tpsColor}${fmt(tpsValue)} §7/ 19.0",
            "§7 Players  §f$players",
            "§7 View     §f$currentViewDistance §7chunks §8(sim $simDistance§8)",
            "§7 Entities ${entityColor}$totalEntities",
            "§7 RAM      ${ramColor}${gameRamMB}MB §7/ ${ramColor}${maxRamMB}MB",
            "§7 Tuning   ${tuningColor}$tuningText",
            "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        )
    }

    private fun fmt(d: Double) = String.format("%.1f", d)
}
