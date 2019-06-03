package br.foxprefix.prefix

import Br.API.GUI.Ex.UIManager
import Br.API.GUI.Ex.kt.KtItem
import Br.API.GUI.Ex.kt.KtUIBuilder
import Br.API.GUI.Ex.kt.get
import Br.API.GUI.Ex.kt.set
import Br.API.Scripts.ScriptLoader
import Br.API.Utils
import Br.API.ktsuger.ItemBuilder
import Br.API.ktsuger.msg
import br.foxprefix.DataManager
import br.foxprefix.Main
import br.foxprefix.PlayerData
import br.foxprefix.achievement.getAchievementValue
import jdk.nashorn.api.scripting.NashornScriptEngine
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
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

fun registerUI() {
    val ui = KtUIBuilder.createUI("FPUI", "§6称号", 6, false) * { p, map ->
        val pd = DataManager get p.name
        map["Data"] = pd
        map["Page"] = 0
    }
    for (i in 0..44) {
        val index = i
        ui + index += KtItem.newItem() display d@{ p, s ->
            val page = s["Page"] as Int
            val ind = page * 45 + index
            val prefix = PrefixIndex[ind] ?: return@d null
            val pd = s["Data"] as PlayerData
            if (pd.isUnlocked(prefix)) {
                if (pd.equip == prefix.name) {
                    val item = prefix.ui_unlock.clone()
                    item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1)
                    return@d item
                } else {
                    return@d prefix.ui_unlock
                }
            } else {
                return@d prefix.ui_lock
            }
        } click c@{ p, s ->
            val page = s["Page"] as Int
            val ind = page * 45 + index
            val prefix = PrefixIndex[ind] ?: return@c
            val pd = s["Data"] as PlayerData
            if (pd.isUnlocked(prefix)) {
                pd.equip = prefix.name
            } else {
                if (prefix.unlock_value.isUnlock(p)) {
                    p msg "§6你成功的解锁了这个称号"
                    pd.unlockPrefix.add(prefix.name)
                    pd.equip = prefix.name
                } else {
                    p msg "§c你无法解锁这个称号"
                }
            }
        }
    }

    val prepage = (ItemBuilder.create(Material.ARROW) name "§6上一页")()
    ui + 45 += KtItem.newItem() display { p, s ->
        val page = s["Page"] as Int
        if (page > 0) prepage else null
    } click { p, s ->
        val page = s["Page"] as Int
        if (page > 0) {
            s["Page"] = page - 1
        }
    }
    ui + 53 += KtItem.newItem() display (ItemBuilder.create(Material.ARROW) name "§6下一页")() click {p,s->
        val page = s["Page"] as Int
        s["Page"] = page + 1
    }
    UIManager.RegisterUI(ui.build())
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
        val scrr =
                """
            function checkUnlock($args){
                return $scr;
            }
        """.trimIndent()
        Logger.getLogger(VariableUnlock::class.java.name).log(Level.SEVERE, "自动化脚本: $scrr")
        val t = jdk.nashorn.api.scripting.NashornScriptEngineFactory()
        script = t.getScriptEngine() as NashornScriptEngine
        script.eval(scrr)
        //ScriptLoader.eval(Main.getPlugin(), scrr)
    }

    override fun isUnlock(p: Player): Boolean {
        val vari = mutableListOf<Int>()
        for ((par, _) in vars) {
            vari += getAchievementValue(par, p)
        }
        return script.invokeFunction("checkUnlock", vari.toIntArray()) as? Boolean ?: false
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