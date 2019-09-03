package br.foxprefix.prefix

import Br.API.GUI.Ex.kt.KtItem
import Br.API.GUI.Ex.kt.get
import Br.API.Utils
import Br.API.ktsuger.msg
import br.foxprefix.PlayerData
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

open class Prefix(config: ConfigurationSection) {
    val name: String = config.name
    val display: String
    val ui_index: Int
    val ui_lock: ItemStack
    val ui_unlock: ItemStack
    val unlock_type: UnlockType
    val unlock_value: Unlock

    init {
        display = ChatColor.translateAlternateColorCodes('&', config.getString("Display"))
        ui_index = config.getInt("UI.Index")
        ui_lock = Utils.readItemStack(config.getString("UI.ItemLock"))
        ui_unlock = Utils.readItemStack(config.getString("UI.ItemUnlock"))
        unlock_value = readUnlock(config.getConfigurationSection("Unlock"))
        unlock_type = unlock_value.type
    }

//    operator fun invoke(): KtItem = KtItem.newItem() display { p, s ->
//        val pd = s["Data"] as PlayerData
//        if (pd.isUnlocked(this)) {
//            if (pd.equip == this.name) {
//                val item = ui_unlock.clone()
//                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1)
//                item
//            } else {
//                ui_unlock
//            }
//        } else {
//            ui_lock
//        }
//    } click c@{ p, s ->
//        val pd = s["Data"] as PlayerData
//        if (pd.isUnlocked(this)) {
//            pd.equip = this.name
//        } else {
//            if (this.unlock_value.isUnlock(p)) {
//                p msg "§6你成功的解锁了这个称号"
//                pd.unlockPrefix.add(this.name)
//                pd.equip = this.name
//            } else {
//                p msg "§c你无法解锁这个称号"
//            }
//        }
//    }
}