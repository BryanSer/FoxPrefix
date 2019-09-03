package br.foxprefix.loot

import Br.API.Utils
import br.foxprefix.Main
import br.foxprefix.achievement.AchievementType
import br.foxprefix.achievement.Item
import br.foxprefix.achievement.contains
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import java.io.File
import kotlin.random.Random

val random = Random.Default

class LootGroup(config: ConfigurationSection) {
    lateinit var loot: List<Loot>
    var totalWeight: Long = 0
    var listener: Listener? = null

    init {
        if (config.getBoolean("Enable", false)) {
            listener = when (config.getString("Type")) {
                "Dig" -> DigListener(config.getString("ID"))
                "Kill" -> KillListener(config.getString("ID"))
                "KillPlayer" -> KillPlayerListener()
                else -> null
            }
            loot = config.getStringList("Loots").map(::Loot)
            totalWeight = loot.stream().mapToLong(Loot::weight::get).sum()
            Bukkit.getPluginManager().registerEvents(listener,Main.getPlugin())
        }
    }

    fun random(): ItemStack? {
        var r = random.nextLong(totalWeight)
        for (lt in loot) {
            r -= lt.weight
            if (r < 0) {
                return lt.item?.clone()
            }
        }
        return null
    }

    fun drop(loc: Location) {
        val item = random() ?: return
        loc.world.dropItem(loc, item)
    }

    inner class DigListener(str: String) : Listener {
        private val id: List<Item> = str.split(",".toRegex()).map(::Item).toList()

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onDig(evt: BlockBreakEvent) {
            if (id.contains(evt.block)) {
                drop(evt.block.location)
            }
        }
    }

    inner class KillListener(str: String) : Listener {
        private val id: List<Short> = str.split(",".toRegex()).map { it.toShort() }.toList()

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onEntityDeath(evt: EntityDeathEvent) {
            if (evt.entity.killer != null && id.contains(evt.entity.type.typeId)) {
                drop(evt.entity.location)
            }
        }
    }

    inner class KillPlayerListener : Listener {

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onEntityDeath(evt: PlayerDeathEvent) {
            if (evt.entity.killer != null) {
                drop(evt.entity.location)
            }
        }
    }

    companion object {
        val loots = mutableListOf<LootGroup>()
        fun init(){
            for(lt in loots){
                if(lt.listener != null){
                    HandlerList.unregisterAll(lt.listener)
                }
            }
            loots.clear()
            val f = File(Main.getPlugin().dataFolder,"loot.yml")
            if(!f.exists()){
                Utils.saveResource(Main.getPlugin(),"loot.yml",null)
            }
            val config = YamlConfiguration.loadConfiguration(f)
            for(key in config.getKeys(false)){
                val lt = LootGroup(config.getConfigurationSection(key))
                loots += lt
            }
        }
    }
}