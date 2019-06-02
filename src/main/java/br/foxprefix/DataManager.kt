package br.foxprefix

import java.io.Serializable

object DataManager {
    infix fun get(name:String):PlayerData{
        TODO()
    }
}

data class PlayerData(
        val name: String,
        val achievementData: MutableMap<String, Int> = HashMap()
) : Serializable {
}