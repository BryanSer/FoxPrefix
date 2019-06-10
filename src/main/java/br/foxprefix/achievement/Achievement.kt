package br.foxprefix.achievement

import Br.API.Utils
import br.foxprefix.DataManager
import br.foxprefix.Main
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.block.Block
import org.bukkit.block.Furnace
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerStatisticIncrementEvent
import org.bukkit.inventory.ItemStack


abstract class Achievement(config: ConfigurationSection) : Listener {
    lateinit var type: AchievementType
    val name: String


    init {
        name = config.name
        Bukkit.getPluginManager().registerEvents(this, Main.getPlugin())
    }

    fun add(p: Player, value: Int = 1) {
        val pd = DataManager get p.name ?: return
        val i = (pd.achievementData[name] ?: 0) + value
        pd.achievementData[name] = i
    }

    fun getValue(p: Player): Int {
        val pd = DataManager get p.name ?: return 0
        return (pd.achievementData[name] ?: 0)
    }
}

class FishAchievement(config: ConfigurationSection) : Achievement(config) {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onFish(evt: PlayerFishEvent) {
        if(evt.state == PlayerFishEvent.State.CAUGHT_FISH)
            add(evt.player)
    }
}

class OnlineAchievement(config: ConfigurationSection) : Achievement(config), Runnable {
    override fun run() {
        for (p in Utils.getOnlinePlayers()) {
            add(p)
        }
    }

    init {
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), this, 1200L, 1200L)
    }
}

inline fun List<Item>.contains(item: ItemStack): Boolean {
    for (e in this) {
        if (e.isSame(item)) {
            return true
        }
    }
    return false
}

inline fun List<Item>.contains(b: Block): Boolean {
    for (e in this) {
        if (e.isSame(b)) {
            return true
        }
    }
    return false
}

class SmeltAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: List<Item>

    init {
        type = AchievementType.SMELT
        id = config.getString("ID").split(",".toRegex()).map(::Item).toList()
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onSmelt(evt: FurnaceSmeltEvent) {
        val furnace = evt.furnace.state as? Furnace ?: return
        val result = furnace.inventory.result ?: return
        if (!id.contains(result)) {
            return
        }
        val v = furnace.inventory.viewers
        if (v.isNotEmpty()) {
            for (p in v) {
                if (p is Player) {
                    add(p)
                }
            }
        }
    }

}

class PlaceAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: List<Item>

    init {
        type = AchievementType.PLACE
        id = config.getString("ID").split(",".toRegex()).map(::Item).toList()
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlace(evt: BlockPlaceEvent) {
        if (id.contains(evt.block)) {
            add(evt.player)
        }
    }
}

val DEBUG = false

class DigAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: List<Item>

    init {
        type = AchievementType.DIG
        id = config.getString("ID").split(",".toRegex()).map(::Item).toList()
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onDig(evt: BlockBreakEvent) {
        if (id.contains(evt.block)) {
            add(evt.player)
        }
    }
}

class KillPlayerAchievement(config: ConfigurationSection) : Achievement(config) {

    init {
        type = AchievementType.KILL
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityDeath(evt: PlayerDeathEvent) {
        add(evt.entity.killer ?: return)
    }
}

class KillAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: List<Short>

    init {
        type = AchievementType.KILL
        id = config.getString("ID").split(",".toRegex()).map { it.toShort() }.toList()
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityDeath(evt: EntityDeathEvent) {
        if (evt.entity.killer != null && id.contains(evt.entity.type.typeId)) {
            add(evt.entity.killer)
        }
    }
}