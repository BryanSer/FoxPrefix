package br.foxprefix.quest

import Br.API.Utils
import br.foxprefix.DataManager
import br.foxprefix.Main
import br.foxprefix.achievement.getAchievementValue
import br.foxprefix.prefix.compile
import jdk.nashorn.api.scripting.NashornScriptEngine
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.File
import java.util.*

class Quest(
        config: ConfigurationSection
) {
    val name = config.name
    val index = config.getInt("UI.Index")
    val undone = Utils.readItemStack(config.getString("UI.Undone"))
    val done = Utils.readItemStack(config.getString("UI.Done"))
    val conditions = config.getStringList("Condition").map(::Condition)
    val infty = config.getBoolean("Infty", false)
    val onDone = mutableListOf<Pair<String, Int>>()

    init {
        if (config.contains("Ondone")) {
            val cs = config.getConfigurationSection("Ondone")
            if (cs != null) {
                for (key in cs.getKeys(false)) {
                    onDone += key to cs.getInt(key)
                }
            }
        }
    }

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
                    hasEnoughItems(it, mutableListOf(item.clone()))
                }
                remove = {
                    removeItem(it, mutableListOf(item.clone()))
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


fun hasEnoughItems(p: Player, items: List<ItemStack>): Boolean {
    val map = HashMap<Item, Int>()
    items.forEach { `is`: ItemStack -> checkItem(map, `is`) }
    for (`is` in p.inventory.contents) {
        if (`is` == null || `is`.amount == 0 || `is`.type == Material.AIR) {
            continue
        }
        for (item in map.keys) {
            if (item.isSame(`is`)) {
                map.put(item, map.get(item)!! - `is`.amount)
                break
            }
        }
    }
    return map.values.stream().noneMatch({ a -> a > 0 })
}

fun removeItem(p: Player, items: List<ItemStack>) {
    val map = HashMap<Item, Int>()
    items.forEach { `is` -> checkItem(map, `is`) }
    checkItem(p, map)
}

private class Item(`is`: ItemStack) {

    internal var ID: Int = 0
    internal var Durability: Short = 0
    internal var meta: ItemMeta

    init {
        this.ID = `is`.typeId
        this.Durability = `is`.durability
        this.meta = `is`.itemMeta.clone()
    }

    fun isSame(item: ItemStack): Boolean {
        if (item.typeId == this.ID && item.durability == this.Durability) {
            if (Bukkit.getItemFactory().equals(item.itemMeta, this.meta)) {
                return true
            }
            if (item.itemMeta.hasLore() && meta.hasLore()) {
                val lore = item.itemMeta.lore
                val olore = meta.lore
                val it = lore.iterator()
                val oit = olore.iterator()
                while (it.hasNext() && oit.hasNext()) {
                    val str = it.next()
                    val ostr = oit.next()
                    if (str != ostr) {
                        if (str.contains("灵魂绑定")) {
                            it.next()
                            continue
                        }
                        return false
                    }
                }

                return true
            }

        }
        return false
    }

    override fun hashCode(): Int {
        var hash = 5
        hash = 61 * hash + this.ID
        hash = 61 * hash + this.Durability
        hash = 61 * hash + Objects.hashCode(this.meta)
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as Item?
        if (this.ID != other!!.ID) {
            return false
        }
        return if (!Bukkit.getItemFactory().equals(other.meta, this.meta)) {
            false
        } else true
    }

}

private fun checkItem(map: MutableMap<Item, Int>, `is`: ItemStack) {
    val i = Item(`is`)
    if (map.containsKey(i)) {
        map[i] = map[i]!! + `is`.amount
    } else {
        map[i] = `is`.amount
    }
}

private fun checkItem(p: Player, map: Map<Item, Int>) {
    outter@ for (e in map.entries) {
        val cl = e.key
        var amount = e.value
        for (i in 0 until p.inventory.size) {
            val item = p.inventory.getItem(i)
            if (amount <= 0) {
                continue@outter
            }
            if (item == null) {
                continue
            }
            if (cl.isSame(item)) {
                if (amount - item.amount < 0) {
                    item.amount = item.amount - amount
                    p.inventory.setItem(i, item)
                    continue@outter
                }
                if (amount == item.amount) {
                    p.inventory.setItem(i, null)
                    continue@outter
                }
                if (amount > item.amount) {
                    amount -= item.amount
                    p.inventory.setItem(i, null)
                }
            }
        }
    }
}