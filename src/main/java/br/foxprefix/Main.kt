package br.foxprefix

import Br.API.GUI.Ex.UIManager
import br.foxprefix.achievement.loadAchievement
import br.foxprefix.prefix.Prefixs
import br.foxprefix.prefix.loadPrefixs
import br.foxprefix.prefix.registerUI
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
        registerUI()
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
        if (args[0].equals("reload", true)) {
            loadAchievement()
            loadPrefixs()
            registerUI()
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
