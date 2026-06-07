package io.thegamingmahi.picomc.config

import io.thegamingmahi.picomc.PicoMC
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class ConfigManager(private val plugin: PicoMC) {

    private val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
    private val flagFile   = File(serverRoot, ".picomc-configured")

    private val FULL_REPLACE_FILES = listOf(
        "bukkit.yml",
        "spigot.yml",
        "server.properties",
        "config/paper-world-defaults.yml",
        "config/paper-global.yml",
        "config/gale-world-defaults.yml",
        "config/gale-global.yml",
        "config/leaf-global.yml"
    )

    private val PURPUR_PATCHES = mapOf(
        "world-settings.default.mobs.villager.lobotomize.enabled"                  to true,
        "world-settings.default.mobs.villager.lobotomize.check-interval"           to 100,
        "world-settings.default.mobs.villager.can-breed"                           to false,
        "world-settings.default.mobs.villager.search-radius.acquire-poi"           to 8,
        "world-settings.default.mobs.villager.search-radius.nearest-bed-sensor"    to 8,
        "world-settings.default.gameplay-mechanics.mob-spawning.wandering-traders" to false,
        "world-settings.default.gameplay-mechanics.mob-spawning.phantoms"          to false,
        "world-settings.default.gameplay-mechanics.mob-spawning.village-sieges"    to false,
        "world-settings.default.gameplay-mechanics.tick-fluids"                    to false,
        "world-settings.default.gameplay-mechanics.entity-lifespan"                to 6000,
        "world-settings.default.gameplay-mechanics.daylight-cycle-ticks.nighttime" to 8000,
        "world-settings.default.gameplay-mechanics.armorstand.can-movement-tick"              to false,
        "world-settings.default.gameplay-mechanics.armorstand.can-move-in-water"              to false,
        "world-settings.default.gameplay-mechanics.armorstand.can-move-in-water-over-fence"   to false,
        "settings.tps-catchup"       to false,
        "settings.lagging-threshold" to 15.0
    )

    fun backupAndApply() {
        plugin.dataFolder.mkdirs()

        val firstTime = !flagFile.exists()

        if (firstTime) {
            // First time only — backup originals to server root next to the yml files
            val backupDir = File(serverRoot, "picomc-backup-${timestamp()}")
            backupDir.mkdirs()
            plugin.logger.info("First run — backing up original configs to: ${backupDir.name}/")

            for (relativePath in FULL_REPLACE_FILES) {
                val target = File(serverRoot, relativePath)
                if (target.exists()) {
                    val backupFile = File(backupDir, relativePath.replace("/", "_"))
                    target.copyTo(backupFile, overwrite = true)
                }
            }

            // Backup purpur too
            val purpur = File(serverRoot, "purpur.yml")
            if (purpur.exists()) purpur.copyTo(File(backupDir, "purpur.yml"), overwrite = true)

            plugin.logger.info("Backup complete — restore from ${backupDir.name}/ if needed")
        } else {
            plugin.logger.info("Not first run — skipping backup, applying configs directly")
        }

        // Apply optimized configs
        for (relativePath in FULL_REPLACE_FILES) {
            applyFile(relativePath)
        }

        // Patch purpur
        patchPurpur()

        // Mark as configured so we don't backup again
        if (firstTime) {
            flagFile.createNewFile()
        }

        plugin.logger.info("All configs applied successfully!")
    }

    private fun applyFile(relativePath: String) {
        val target       = File(serverRoot, relativePath)
        val resourcePath = "optimized-configs/${relativePath.replace("config/", "")}"
        val resource     = plugin.getResource(resourcePath)

        if (resource != null) {
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> resource.copyTo(out) }
            plugin.logger.info("  ✓ $relativePath")
        } else {
            plugin.logger.warning("  ✗ Missing resource: $resourcePath — skipping")
        }
    }

    private fun patchPurpur() {
        val purpurFile = File(serverRoot, "purpur.yml")
        if (!purpurFile.exists()) {
            plugin.logger.warning("purpur.yml not found — skipping purpur patches")
            return
        }

        val config = YamlConfiguration.loadConfiguration(purpurFile)
        for ((path, value) in PURPUR_PATCHES) config.set(path, value)
        config.save(purpurFile)
        plugin.logger.info("  ✓ purpur.yml (${PURPUR_PATCHES.size} patches)")
    }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
}
