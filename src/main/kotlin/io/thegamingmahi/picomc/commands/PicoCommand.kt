package io.thegamingmahi.picomc.commands

import io.thegamingmahi.picomc.PicoMC
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class PicoCommand(private val plugin: PicoMC) : CommandExecutor, TabCompleter {

    // Track pregeneration state
    private var pregenerating = false
    private var pregenProgress = 0
    private var pregenTotal = 0

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("picomc.admin")) {
            sender.sendMessage("§cYou don't have permission to use PicoMC commands.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "status"      -> handleStatus(sender)
            "reload"      -> handleReload(sender)
            "pregenerate" -> handlePregenerate(sender, args)
            "help", null  -> handleHelp(sender)
            else -> sender.sendMessage("§cUnknown command. Use §f/pico help§c for help.")
        }

        return true
    }

    private fun handleStatus(sender: CommandSender) {
        plugin.dynamicAdjuster.getStatusLines().forEach { sender.sendMessage(it) }
    }

    private fun handleReload(sender: CommandSender) {
        sender.sendMessage("§eReapplying optimized configurations...")
        try {
            plugin.configManager.backupAndApply()
            sender.sendMessage("§aConfigs reapplied! Some changes require a full restart.")
        } catch (e: Exception) {
            sender.sendMessage("§cFailed: ${e.message}")
        }
    }

    private fun handlePregenerate(sender: CommandSender, args: Array<out String>) {
        if (pregenerating) {
            sender.sendMessage("§ePregeneration already running — §f$pregenProgress§e/§f$pregenTotal §echunks done")
            return
        }

        val radius = args.getOrNull(1)?.toIntOrNull()
        if (radius == null || radius < 1 || radius > 10000) {
            sender.sendMessage("§cUsage: /pico pregenerate <radius>")
            sender.sendMessage("§7Radius is in blocks (1-10000). Example: §f/pico pregenerate 1000")
            return
        }

        val world = if (sender is org.bukkit.entity.Player) sender.world
                    else Bukkit.getWorlds()[0]

        val chunkRadius = (radius / 16) + 1
        val chunksToGen = mutableListOf<Pair<Int, Int>>()

        // Build list of all chunk coordinates to generate
        val centerChunkX = world.spawnLocation.blockX shr 4
        val centerChunkZ = world.spawnLocation.blockZ shr 4

        for (x in -chunkRadius..chunkRadius) {
            for (z in -chunkRadius..chunkRadius) {
                if (x * x + z * z <= chunkRadius * chunkRadius) {
                    chunksToGen.add(Pair(centerChunkX + x, centerChunkZ + z))
                }
            }
        }

        pregenTotal    = chunksToGen.size
        pregenProgress = 0
        pregenerating  = true

        sender.sendMessage("§aStarting pregeneration of §f$pregenTotal §achunks in a §f${radius}§a block radius...")
        sender.sendMessage("§7This runs in the background. Use §f/pico pregenerate§7 to check progress.")

        // Process chunks in batches asynchronously
        // Load 5 chunks per tick to keep server responsive
        val BATCH_SIZE = 5
        var index = 0

        plugin.server.scheduler.runTaskTimer(plugin, { task ->
            if (!pregenerating) {
                task.cancel()
                return@runTaskTimer
            }

            val end = minOf(index + BATCH_SIZE, chunksToGen.size)
            for (i in index until end) {
                val (cx, cz) = chunksToGen[i]
                if (!world.isChunkGenerated(cx, cz)) {
                    world.getChunkAt(cx, cz) // generates if not exists
                }
                pregenProgress++
            }
            index = end

            // Log progress every 10%
            val percent = (pregenProgress * 100) / pregenTotal
            if (pregenProgress % (pregenTotal / 10).coerceAtLeast(1) == 0) {
                plugin.logger.info("Pregeneration: $percent% ($pregenProgress/$pregenTotal chunks)")
                Bukkit.getOnlinePlayers().forEach {
                    it.sendMessage("§7[PicoMC] Pregeneration: §f$percent%§7 ($pregenProgress/$pregenTotal chunks)")
                }
            }

            // Done
            if (index >= chunksToGen.size) {
                pregenerating = false
                task.cancel()
                plugin.logger.info("Pregeneration complete! $pregenTotal chunks generated.")
                Bukkit.getOnlinePlayers().forEach {
                    it.sendMessage("§a[PicoMC] Pregeneration complete! §f$pregenTotal§a chunks ready.")
                }
            }
        }, 1L, 1L)
    }

    private fun handleHelp(sender: CommandSender) {
        sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§e PicoMC Commands")
        sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§f/pico status §7— Show TPS, RAM, entity counts")
        sender.sendMessage("§f/pico pregenerate <radius> §7— Pre-generate chunks")
        sender.sendMessage("§f/pico reload §7— Reapply optimized configs")
        sender.sendMessage("§f/pico help §7— Show this message")
        sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("status", "pregenerate", "reload", "help")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() == "pregenerate") {
            return listOf("500", "1000", "2000", "5000")
        }
        return emptyList()
    }
}
