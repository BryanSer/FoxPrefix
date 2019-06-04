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
import Br.API.ktsuger.plusAssign
import Br.API.ktsuger.unaryPlus
import br.foxprefix.DataManager
import br.foxprefix.Main
import br.foxprefix.PlayerData
import br.foxprefix.achievement.DEBUG
import br.foxprefix.achievement.getAchievementValue
import jdk.nashorn.api.scripting.NashornScriptEngine
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
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
            lore[i] = s
        }
        im.lore = lore
        item += im
    }
    return item
}

fun registerUI() {
    val ui = KtUIBuilder.createUI("FPUI", "§3§l楼楼称号商店", 6, false) * { p, map ->
        val pd = DataManager get p.name
        map["Data"] = pd
        map["Page"] = 0
    }
    ui onClose { p, s ->
        DataManager save p
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
                    return@d replaceVar(item, p)
                } else {
                    return@d replaceVar(prefix.ui_unlock, p)
                }
            } else {
                return@d replaceVar(prefix.ui_lock, p)
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
    val nextp = (ItemBuilder.create(Material.ARROW) name "§6下一页")()
    ui + 53 += KtItem.newItem() display { p, s ->
        val page = s["Page"] as Int
        if (page < 5) {
            nextp
        } else {
            null
        }
    } click c@{ p, s ->
        val page = s["Page"] as Int
        if (page >= 5) {
            return@c
        }
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
        val scrr = """
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
        if (DEBUG) {
            p.sendMessage("§b解锁成就 脚本参数列表: $vari")
        }
        return script.invokeFunction("checkUnlock", *vari.toTypedArray()) as Boolean
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