package br.foxprefix.quest

import Br.API.Utils
import br.foxprefix.DataManager
import br.foxprefix.Main
import br.foxprefix.achievement.DEBUG
import br.foxprefix.achievement.getAchievementValue
import br.foxprefix.prefix.compile
import jdk.nashorn.api.scripting.NashornScriptEngine
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

class Quest(
        config: ConfigurationSection
) {
    val name = config.name
    val index = config.getInt("UI.Index")
    val undone = Utils.readItemStack(config.getString("UI.Undone"))
    val done = Utils.readItemStack(config.getString("UI.Done"))
    val conditions = config.getStringList("Condition").map(::Condition)
    val award = config.getStringList("Award").map {
        val s = it.split(":".toRegex(), 2)
        when (s[0]) {
            "p" -> { p: Player ->
                Bukkit.dispatchCommand(p, s[1].replace("%player%", p.name))
                Unit
            }
            "c" -> { p: Player ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s[1].replace("%player%", p.name))
                Unit
            }
            "op" -> { p: Player ->
                val op = p.isOp
                try {
                    p.isOp = true
                    Bukkit.dispatchCommand(p, s[1].replace("%player%", p.name))
                } finally {
                    p.isOp = op
                }
                Unit
            }
            else -> { p: Player -> Unit }
        }
    }

    companion object {
        val quests = mutableMapOf<Int, Quest>()
        fun init() {
            val f = File(Main.getPlugin().dataFolder, "quest.yml")
            if (!f.exists()) {
                Utils.saveResource(Main.getPlugin(), "quest.yml", null)
            }
            val config = YamlConfiguration.loadConfiguration(f)
            quests.clear()
            for (key in config.getKeys(false)) {
                val q = Quest(config.getConfigurationSection(key))
                quests[q.index] = q
            }
        }
    }
}

class Condition(str: String) {
    val hasEnough: (Player) -> Boolean
    val remove: (Player) -> Unit

    init {
        val s = str.split(":".toRegex(), 2)
        when (s[0]) {
            "task" -> {
                val task = s[1]
                hasEnough = {
                    val pd = DataManager.get(it.name)
                    if (pd != null) {
                        pd.isComplete(task)
                    } else {
                        false
                    }
                }
                remove = {}
            }
            "item" -> {
                val item = Utils.readItemStack(s[1])
                hasEnough = {
                    Utils.hasEnoughItems(it, item.clone())
                }
                remove = {
                    Utils.removeItem(it, item.clone())
                }
            }
            "money" -> {
                val money = s[1].toDouble()
                hasEnough = {
                    Utils.getEconomy().has(it, money)
                }
                remove = {
                    Utils.getEconomy().withdrawPlayer(it, money)
                }
            }
            "var" -> {
                val vars = mutableMapOf<String, String>()
                val value = s[1]
                val matcher = compile.matcher(value)
                var index = 0
                while (matcher.find()) {
                    val pattern = matcher.group("pattern")
                    val name = matcher.group("name")
                    if (!vars.containsKey(pattern)) {
                        vars[pattern] = "var$index"
                        index++
                    }
                }
                var scr = value
                var args = ""
                for ((p, v) in vars) {
                    scr = scr.replace(p, v)
                    if (args.isNotEmpty()) {
                        args += ","
                    }
                    args += v
                }
                val scrr = """
                function checkUnlock($args){
                    return $scr;
                }
                """.trimIndent()
                val t = jdk.nashorn.api.scripting.NashornScriptEngineFactory()
                val e = t.getScriptEngine() as NashornScriptEngine
                e.eval(scrr)
                hasEnough = {
                    val vari = mutableListOf<Int>()
                    for ((par, _) in vars) {
                        vari += getAchievementValue(par, it)
                    }
                    e.invokeFunction("checkUnlock", *vari.toTypedArray()) as Boolean
                }
                remove = {}
            }
            else -> throw IllegalArgumentException("条件类型错误")
        }
    }
}