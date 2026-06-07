package io.thegamingmahi.picomc.performance

import io.thegamingmahi.picomc.PicoMC
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

class TPSMonitor(private val plugin: PicoMC) {

    private var task: BukkitTask? = null

    var currentTPS: Double = 20.0
        private set

    // Rolling average of last 6 readings (every 10s = last 60 seconds)
    // Responsive enough to catch real lag, smooth enough to ignore brief spikes
    private val recentReadings = ArrayDeque<Double>(6)
    private val READING_COUNT = 6

    fun start() {
        // Check every 10 seconds (200 ticks) on main thread
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateTPS()
        }, 200L, 200L)

        plugin.logger.info("TPS Monitor started — 60 second rolling average, checked every 10s")
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun updateTPS() {
        val rawTPS = Bukkit.getServer().tps[0].coerceAtMost(20.0).coerceAtLeast(0.0)

        if (recentReadings.size >= READING_COUNT) {
            recentReadings.removeFirst()
        }
        recentReadings.addLast(rawTPS)

        currentTPS = recentReadings.average()
    }
}
