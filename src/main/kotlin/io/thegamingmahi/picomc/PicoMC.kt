package io.thegamingmahi.picomc

import io.thegamingmahi.picomc.ai.MobAttributeOptimizer
import io.thegamingmahi.picomc.commands.PicoCommand
import io.thegamingmahi.picomc.config.ConfigManager
import io.thegamingmahi.picomc.performance.DynamicAdjuster
import io.thegamingmahi.picomc.performance.TPSMonitor
import org.bukkit.plugin.java.JavaPlugin

class PicoMC : JavaPlugin() {

    lateinit var configManager: ConfigManager
    lateinit var tpsMonitor: TPSMonitor
    lateinit var dynamicAdjuster: DynamicAdjuster
    lateinit var mobAttributeOptimizer: MobAttributeOptimizer

    companion object {
        lateinit var instance: PicoMC
            private set
    }

    override fun onEnable() {
        instance = this

        logger.info("  ____  _          __  __  ____ ")
        logger.info(" |  _ \\(_) ___ ___|  \\/  |/ ___|")
        logger.info(" | |_) | |/ __/ _ \\ |\\/| | |    ")
        logger.info(" |  __/| | (_|  __/ |  | | |___ ")
        logger.info(" |_|   |_|\\___\\___|_|  |_|\\____|")
        logger.info(" Low footprint Minecraft — v${description.version}")
        logger.info(" By TheGamingMahi")
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        logger.info("[1/4] Applying optimized configurations...")
        configManager = ConfigManager(this)
        configManager.backupAndApply()

        logger.info("[2/4] Starting TPS monitor...")
        tpsMonitor = TPSMonitor(this)
        tpsMonitor.start()

        logger.info("[3/4] Starting dynamic performance adjuster...")
        dynamicAdjuster = DynamicAdjuster(this, tpsMonitor)
        dynamicAdjuster.start()

        logger.info("[4/4] Registering mob AI optimizer...")
        mobAttributeOptimizer = MobAttributeOptimizer(this)
        server.pluginManager.registerEvents(mobAttributeOptimizer, this)

        getCommand("pico")?.setExecutor(PicoCommand(this))

        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.info("PicoMC v${description.version} enabled!")
        logger.info("Target: 512mb-1gb RAM, 1-5 players on Leaf 1.21.11")
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    override fun onDisable() {
        if (::tpsMonitor.isInitialized) tpsMonitor.stop()
        if (::dynamicAdjuster.isInitialized) dynamicAdjuster.stop()
        if (::mobAttributeOptimizer.isInitialized) mobAttributeOptimizer.stop()
        logger.info("PicoMC disabled. Goodbye!")
    }
}
