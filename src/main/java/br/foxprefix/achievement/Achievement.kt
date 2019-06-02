package br.foxprefix.achievement

import Br.API.Utils
import br.foxprefix.DataManager
import br.foxprefix.Main
import org.bukkit.Bukkit
import org.bukkit.block.Furnace
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerFishEvent



abstract class Achievement(config: ConfigurationSection) : Listener {
    lateinit var type: AchievementType
    val name: String

    init {
        name = config.name
    }

    init {
        Bukkit.getPluginManager().registerEvents(this, Main.getPlugin())
    }

    fun add(p: Player, value: Int = 1) {
        val pd = DataManager get p.name
        val i = (pd.achievementData[name] ?: 0) + value
        pd.achievementData[name] = i
    }
}

class FishAchievement(config: ConfigurationSection) : Achievement(config) {
    @EventHandler
    fun onFish(evt: PlayerFishEvent) {
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
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), this, 1200L,1200L)
    }
}

class SmeltAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: Int

    init {
        type = AchievementType.SMELT
        id = config.getInt("ID")
    }

    @EventHandler
    fun onSmelt(evt: FurnaceSmeltEvent) {
        val furnace = evt.furnace.state as? Furnace ?: return
        val result = furnace.inventory.result ?: return
        if (result.typeId != id) {
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
    private val id: Int

    init {
        type = AchievementType.PLACE
        id = config.getInt("ID")
    }

    @EventHandler
    fun onPlace(evt: BlockPlaceEvent) {
        if (evt.block.typeId == id) {
            add(evt.player)
        }
    }
}

class DigAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: Int

    init {
        type = AchievementType.DIG
        id = config.getInt("ID")
    }

    @EventHandler
    fun onDig(evt: BlockBreakEvent) {
        if (evt.block.typeId == id) {
            add(evt.player)
        }
    }
}

class KillAchievement(config: ConfigurationSection) : Achievement(config) {
    private val id: Short

    init {
        type = AchievementType.KILL
        id = config.getInt("ID").toShort()
    }

    @EventHandler
    fun onEntityDeath(evt: EntityDeathEvent) {
        if (evt.entity.type.typeId == id && evt.entity.killer != null) {
            add(evt.entity.killer)
        }
    }
}