package br.foxprefix

import br.foxprefix.achievement.loadAchievement
import org.bukkit.plugin.java.JavaPlugin

open class Main : JavaPlugin() {

    override fun onEnable() {
        PLUGIN = this
        loadAchievement()
    }

    override fun onDisable() {
    }
    companion object {
        private lateinit var PLUGIN: Main
        fun getPlugin(): Main = PLUGIN
    }
}
