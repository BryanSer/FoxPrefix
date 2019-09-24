package br.foxprefix

import Br.API.GUI.Ex.UIManager
import Br.API.GUI.Ex.kt.KtItem
import Br.API.GUI.Ex.kt.KtUIBuilder
import Br.API.GUI.Ex.kt.get
import Br.API.GUI.Ex.kt.set
import Br.API.ktsuger.ItemBuilder
import Br.API.ktsuger.msg
import br.foxprefix.prefix.PrefixIndex
import br.foxprefix.prefix.replaceVar
import br.foxprefix.quest.Quest
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment

fun registerQUI() {
    val ui = KtUIBuilder.createUI("FPQUI", "§3§l楼楼成就任务列表", 6, false) * { p, map ->
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
            val quest = Quest.quests[ind] ?: return@d null
            val pd = s["Data"] as PlayerData
            if (pd.isComplete(quest)) {
                val item = quest.done.clone()
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1)
                return@d replaceVar(item, p)
            } else {
                return@d replaceVar(quest.undone, p)
            }
        } click c@{ p, s ->
            val page = s["Page"] as Int
            val ind = page * 45 + index
            val quest = Quest.quests[ind] ?: return@c
            val pd = s["Data"] as PlayerData
            if (!pd.isComplete(quest) || quest.infty) {
                for (c in quest.conditions) {
                    if (!c.hasEnough(p)) {
                        p msg "§c§l你没有完成该任务的要求"
                        return@c
                    }
                }
                for (c in quest.conditions) {
                    c.remove(p)
                }
                for ((ac, v) in quest.onDone) {
                    pd.achievementData[ac] = pd.achievementData[ac] ?: 0 - v
                }
                if (pd.questDone == null) {
                    pd.questDone = mutableSetOf()
                }
                pd.questDone?.add(quest.name)
                for (a in quest.award) {
                    a.invoke(p)
                }
                p msg "§6你成功的完成了这个任务"
                DataManager save p

            }
        }
    }

    ui + 49 += KtItem.newItem() display (ItemBuilder.create(Material.EMERALD).name("§6点击进入称号商店")()) click { p, s ->
        UIManager.openUI(p, "FPUI")
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
                pd.equip = if (pd.equip == prefix.name) {
                    null
                } else {
                    prefix.name
                }
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

    ui + 49 += KtItem.newItem() display (ItemBuilder.create(Material.EMERALD).name("§6点击查看成就任务")()) click { p, s ->
        UIManager.openUI(p, "FPQUI")
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