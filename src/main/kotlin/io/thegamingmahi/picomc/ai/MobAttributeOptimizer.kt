package io.thegamingmahi.picomc.ai

import io.thegamingmahi.picomc.PicoMC
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.scheduler.BukkitTask

class MobAttributeOptimizer(private val plugin: PicoMC) : Listener {

    private var awarenessTask: BukkitTask? = null

    // Detection ranges
    private val HOSTILE_FOLLOW_RANGE  = 16.0
    private val PASSIVE_FOLLOW_RANGE  = 6.0
    private val VILLAGER_FOLLOW_RANGE = 4.0
    private val AQUATIC_FOLLOW_RANGE  = 4.0
    private val GHAST_FOLLOW_RANGE    = 24.0

    // Awareness range — mobs within this become aware, outside become unaware
    private val AWARENESS_RANGE       = 16.0
    private val AWARENESS_RANGE_SQ    = AWARENESS_RANGE * AWARENESS_RANGE

    // Mobs blocked from natural spawning
    private val BLOCKED_NATURAL_SPAWNS = setOf(
        EntityType.TROPICAL_FISH,
        EntityType.PUFFERFISH,
        EntityType.COD,
        EntityType.SALMON,
        EntityType.GLOW_SQUID,
        EntityType.DOLPHIN,
        EntityType.TURTLE,
        EntityType.POLAR_BEAR,
        EntityType.PANDA,
        EntityType.FOX,
        EntityType.GOAT,
        EntityType.BEE,
        EntityType.ARMADILLO,
        EntityType.SNIFFER,
        EntityType.CAMEL,
        EntityType.PHANTOM,
        EntityType.WANDERING_TRADER,
        EntityType.BAT,
        EntityType.HOGLIN,
        EntityType.ZOGLIN
    )

    init {
        startAwarenessLoop()
    }

    // ----------------------------------------
    // SPAWN EVENT — apply attributes + block unwanted
    // ----------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        val reason = event.spawnReason

        // Block natural spawning of decorative mobs
        if (entity.type in BLOCKED_NATURAL_SPAWNS) {
            val isNatural = reason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                    reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN ||
                    reason == CreatureSpawnEvent.SpawnReason.DEFAULT
            if (isNatural) {
                event.isCancelled = true
                return
            }
        }

        // Apply dumb AI attributes to everything that spawns
        applyDumbAI(entity)

        // Start all mobs as unaware — awareness loop will activate them when players are close
        if (entity is Mob) {
            entity.isAware = false
        }
    }

    // ----------------------------------------
    // AWARENESS LOOP — two state AI system
    // Runs every 2 seconds (40 ticks)
    // ----------------------------------------
    private fun startAwarenessLoop() {
        awarenessTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            updateAwareness()
        }, 40L, 40L)
    }

    private fun updateAwareness() {
        val players = plugin.server.onlinePlayers
        if (players.isEmpty()) return

        for (world in plugin.server.worlds) {
            for (entity in world.entities) {
                if (entity !is Mob) continue
                if (entity is Player) continue

                // Check if any player is within awareness range
                val playerNearby = players.any { player ->
                    player.world == world &&
                    entity.location.distanceSquared(player.location) <= AWARENESS_RANGE_SQ
                }

                // Two state system:
                // playerNearby = true  → aware, full AI, targeting
                // playerNearby = false → unaware, wandering, nearly free
                if (entity.isAware != playerNearby) {
                    entity.isAware = playerNearby
                }
            }
        }
    }

    // ----------------------------------------
    // ATTRIBUTE OPTIMIZER
    // ----------------------------------------
    private fun applyDumbAI(entity: LivingEntity) {
        when (entity) {
            is Ghast -> {
                setFollowRange(entity, GHAST_FOLLOW_RANGE)
            }
            is Monster -> {
                setFollowRange(entity, HOSTILE_FOLLOW_RANGE)
                setMovementSpeed(entity, getSlowedSpeed(entity))
            }
            is Villager -> {
                setFollowRange(entity, VILLAGER_FOLLOW_RANGE)
            }
            is WaterMob, is Squid -> {
                setFollowRange(entity, AQUATIC_FOLLOW_RANGE)
            }
            is Animals -> {
                setFollowRange(entity, PASSIVE_FOLLOW_RANGE)
            }
        }
    }

    private fun setFollowRange(entity: LivingEntity, range: Double) {
        try {
            val attr = entity.getAttribute(Attribute.FOLLOW_RANGE) ?: return
            attr.baseValue = range
        } catch (e: Exception) { }
    }

    private fun setMovementSpeed(entity: LivingEntity, speed: Double) {
        try {
            val attr = entity.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
            attr.baseValue = speed.coerceAtLeast(0.15)
        } catch (e: Exception) { }
    }

    private fun getSlowedSpeed(entity: LivingEntity): Double {
        return when (entity) {
            is Zombie   -> 0.23
            is Skeleton -> 0.23
            is Creeper  -> 0.22
            is Spider   -> 0.26
            is Enderman -> 0.30
            else        -> 0.23
        }
    }

    fun stop() {
        awarenessTask?.cancel()
        awarenessTask = null
    }
}
