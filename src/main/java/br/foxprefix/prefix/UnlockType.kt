package br.foxprefix.prefix

import Br.API.Utils
import Br.API.ktsuger.msg
import Br.API.ktsuger.plusAssign
import Br.API.ktsuger.unaryPlus
import br.foxprefix.AchieveData
import br.foxprefix.Main
import br.foxprefix.RankManager
import br.foxprefix.achievement.DEBUG
import br.foxprefix.achievement.getAchievementValue
import jdk.nashorn.api.scripting.NashornScriptEngine
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

enum class UnlockType(val clasz: Class<out Unlock>) {
    MONEY(MoneyUnlock::class.java),
    COMMAND(CommandUnlock::class.java),
    VARIABLE(VariableUnlock::class.java)
}

val PrefixIndex = mutableMapOf<Int, Prefix>()
val Prefixs = mutableMapOf<String, Prefix>()
fun loadPrefixs() {
    Prefixs.clear()
    PrefixIndex.clear()
    val f = File(Main.getPlugin().dataFolder, "prefix.yml")
    if (!f.exists()) {
        Main.getPlugin().saveResource("prefix.yml", false)
    }
    val config = YamlConfiguration.loadConfiguration(f)
    for (key in config.getKeys(false)) {
        val cs = config.getConfigurationSection(key)
        val p = Prefix(cs)
        PrefixIndex[p.ui_index] = p
        Prefixs[p.name] = p
    }
}

val rankPattern = Pattern.compile("(?<pattern>\\\$(?<name>[^\$]*)\\\$)")
fun replaceVar(item: ItemStack, p: Player): ItemStack {
    val item = item.clone()
    val im = +item
    val lore = im.lore
    if (lore != null) {
        for (i in 0 until lore.size) {
            var s = lore[i]
            val matcher = compile.matcher(s)
            val finded = HashSet<String>()
            while (matcher.find()) {
                val pattern = matcher.group("pattern")
                val name = matcher.group("name")
                if (finded.contains(name)) {
                    continue
                }
                finded += name
                s = s.replace(pattern, getAchievementValue(pattern, p).toString())
            }
            finded.clear()
            val mat = rankPattern.matcher(s)
            while (mat.find()) {
                val pattern = mat.group("pattern")
                val name = mat.group("name")
                if (finded.contains(name)) {
                    continue
                }
                finded += name
                s = s.replace(pattern, RankManager.readRank(pattern))
            }
            lore[i] = s
        }
        im.lore = lore
        item += im
    }
    return item
}

fun readUnlock(config: ConfigurationSection): Unlock {
    val type = config.getString("Type")
    for (ut in UnlockType.values()) {
        if (type.equals(ut.name, true)) {
            val c = ut.clasz.getConstructor(String::class.java)
            return c.newInstance(config.getString("Value"))
        }
    }
    return CommandUnlock(null)
}


val compile = Pattern.compile("(?<pattern>%(?<name>[^%]*)%)")

class VariableUnlock(value: String) : Unlock(value, UnlockType.VARIABLE) {
    val script: NashornScriptEngine
    val asyncScript: NashornScriptEngine

    val vars = mutableMapOf<String, String>()

    init {
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
            if (!args.isEmpty()) {
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
        script = t.getScriptEngine() as NashornScriptEngine
        script.eval(scrr)
        asyncScript = t.getScriptEngine() as NashornScriptEngine
        asyncScript.eval(scrr)
        //ScriptLoader.eval(Main.getPlugin(), scrr)
    }

    override fun isUnlock(p: Player): Boolean {
        val vari = mutableListOf<Int>()
        for ((par, _) in vars) {
            vari += getAchievementValue(par, p)
        }
        if (DEBUG) {
            p.sendMessage("§b解锁成就 脚本参数列表: $vari")
        }
        return script.invokeFunction("checkUnlock", *vari.toTypedArray()) as Boolean
    }

    fun isUnlock_Async(p: AchieveData): Boolean {
        val vari = mutableListOf<Int>()
        for ((par, _) in vars) {
            var key = par.replace("%", "").replace(" ", "_")
            vari += p.data[key] ?: 0
        }
        return asyncScript.invokeFunction("checkUnlock", *vari.toTypedArray()) as Boolean
    }
}

class CommandUnlock(value: String?) : Unlock("", UnlockType.COMMAND) {
    override fun isUnlock(p: Player): Boolean {
        return false
    }

}

class MoneyUnlock(value: String) : Unlock(value, UnlockType.MONEY) {
    val money: Double

    init {
        money = value.toDouble()
    }

    override fun isUnlock(p: Player): Boolean {
        val balance = Utils.getEconomy().getBalance(p.name)
        if (balance < money) {
            p msg "§c你的金钱不足以解锁这个称号"
            return false
        }
        Utils.getEconomy().withdrawPlayer(p.name, money)
        return true
    }

}

abstract class Unlock(value: String, val type: UnlockType) {
    abstract fun isUnlock(p: Player): Boolean
}