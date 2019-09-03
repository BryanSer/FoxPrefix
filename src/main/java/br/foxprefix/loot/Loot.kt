package br.foxprefix.loot

import Br.API.Utils
import org.bukkit.inventory.ItemStack

class Loot(str: String) {
    val weight: Long
    val item: ItemStack?

    init {
        val s = str.split("\\|".toRegex(), 2)
        weight = s[0].toLong()
        item = if (s[1].equals("null", true)) {
            null
        } else {
            Utils.readItemStack(s[1])
        }
    }
}