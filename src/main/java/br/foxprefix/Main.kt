package br.foxprefix

import Br.API.GUI.Ex.UIManager
import br.foxprefix.achievement.loadAchievement
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

    override fun onCommand(sender: CommandSender?, command: Command?, label: String?, args: Array<out String>?): Boolean {
        if(sender is Player){
            UIManager.openUI(sender,"FPUI")
        }
        return true
    }

    companion object {
        private lateinit var PLUGIN: Main
        fun getPlugin(): Main = PLUGIN
    }
}
