package br.foxprefix.achievement

import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

class Item(str: String) {
    val itemId: Int
    val dur: Int

    init {
        val t = str.split(":".toRegex(), 2)
        if (t.size == 1) {
            itemId = t[0].toInt()
            dur = 0
        } else {
            itemId = t[0].toInt()
            dur = if (t[1].equals("*")) {
                -1
            } else {
                t[1].toInt()
            }
        }
    }

    fun isSame(item: ItemStack): Boolean {
        return if(dur == -1){
            item.typeId == itemId
        }else {
            item.typeId == itemId && item.durability.toInt() == dur
        }
    }
    fun isSame(block: Block): Boolean {
        return if(dur == -1){
            block.typeId == itemId
        }else {
            val bs = block.state
            block.typeId == itemId && bs.rawData.toInt() == dur
        }
    }
}