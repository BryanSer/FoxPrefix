package br.foxprefix

import Br.API.GUI.Ex.UIManager
import br.foxprefix.achievement.loadAchievement
import br.foxprefix.loot.LootGroup
import br.foxprefix.prefix.Prefixs
import br.foxprefix.prefix.loadPrefixs
import br.foxprefix.quest.Quest
import me.clip.placeholderapi.external.EZPlaceholderHook
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

open class Main : JavaPlugin() {

    override fun onEnable() {
        PLUGIN = this
        DataManager.connectSQL()
        Bukkit.getPluginManager().registerEvents(DataManager, this)
        loadAchievement()
        loadPrefixs()
        Quest.init()
        registerUI()
        registerQUI()
        RankManager.init()
        LootGroup.init()
        object : EZPlaceholderHook(this,"foxprefix") {
            override fun onPlaceholderRequest(p0: Player, p1: String): String {
                when(p1){
                    "prefix"-> {
                        val pd = DataManager get p0.name ?: return ""
                        if(pd.equip == null){
                            return ""
                        }
                        return Prefixs[pd.equip!!]?.display ?: ""
                    }
                }
                return ""
            }
        }.hook()
    }

    override fun onDisable() {
        for ((n, m) in DataManager.cacheData) {
            DataManager.saveDataSync(n, m)
        }
        DataManager.cacheData.clear()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && args.isEmpty()) {
            UIManager.openUI(sender, "FPUI")
            return true
        }
        if (args.isEmpty() || args[0].equals("help", false)) {
            return false
        }
        if(sender is Player && args[0].equals("q",true)){
            UIManager.openUI(sender, "FPQUI")
            return true
        }
        if (!sender.isOp) {
            return true
        }
        if (args[0].equals("delach", true) && args.size >= 3) {
            val p = Bukkit.getPlayerExact(args[1])
            if (p == null || !p.isOnline) {
                sender.sendMessage("§c找不到玩家或玩家不在线")
                return true
            }
            val pd = DataManager get p.name
            if (pd != null) {
                pd.achievementData[args[2]] = 0
                sender.sendMessage("§6处理完成")
                DataManager save p
            } else {
                sender.sendMessage("§c处理失败 数据错误")
            }
            return true
        }
        if (args[0].equals("reload", true)) {
            loadAchievement()
            loadPrefixs()
            Quest.init()
            registerUI()
            registerQUI()
            LootGroup.init()
            sender.sendMessage("§6重载完成")
            return true
        }
        if (args[0].equals("unlock", true) && args.size >= 3) {
            val p = Bukkit.getPlayerExact(args[1])
            if (p == null || !p.isOnline) {
                sender.sendMessage("§c找不到玩家或玩家不在线")
                return true
            }
            val value = Prefixs[args[2]]
            if (value == null) {
                sender.sendMessage("§c找不到称号")
                return true
            }
            val pd = DataManager get p.name
            if (pd != null) {
                pd.unlockPrefix.add(value.name)
                sender.sendMessage("§6处理完成")
                p.sendMessage("§6你解锁了新称号")
                DataManager save p
                return true
            }
            sender.sendMessage("§c解锁失败 数据错误")
            return true
        }
        return false
    }

    companion object {
        private lateinit var PLUGIN: Main
        fun getPlugin(): Main = PLUGIN
    }
}
