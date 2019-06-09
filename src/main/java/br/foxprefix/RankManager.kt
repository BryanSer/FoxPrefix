package br.foxprefix

import br.foxprefix.achievement.Achievements
import br.foxprefix.prefix.PrefixIndex
import br.foxprefix.prefix.VariableUnlock
import org.bukkit.Bukkit


object RankManager {
    var achieveTop = mutableListOf<String>()
    var rankTop = mutableMapOf<String, List<String>>()
    var achieveData = mutableMapOf<String, AchieveData>()

    fun readRank(key: String): String {
        val key = key.replace("$", "")
        var arr = key.split("-".toRegex())
        var isAmount = arr.last() == "amount"
        if (arr[0] == "achieve") {
            val name = achieveTop.getOrNull(arr[1].toInt() - 1) ?: ""
            if (isAmount && name != "") {
                return achieveData[name]?.achieveCount.toString()
            }
            return name
        }
        if (arr[0] == "rank") {
            val list = rankTop[arr[1]] ?: return ""
            val name = list.getOrNull(arr[2].toInt() - 1) ?: ""
            if (isAmount && name != "") {
                return achieveData[name]?.data!![arr[1]].toString()
            }
            return name
        }
        return ""
    }

    val cacheAllPlayerData = mutableMapOf<String, PlayerData>()
    fun init() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getPlugin(), {
            syncData()
            Bukkit.getScheduler().runTask(Main.getPlugin()) {
                val cached = DataManager.cacheData.keys.toList()
                Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin()) {
                    cacheData(cached, ::calcAchieve)
                }
            }
        }, 50, 12000)
    }

    fun syncData() {
        for ((k, v) in DataManager.cacheData) {
            DataManager.saveData(k, false)
        }
    }

    fun cacheData(cached: List<String>, callback: () -> Unit) {
        cacheAllPlayerData.clear()
        val syncLoad = mutableListOf<String>()
        val conn = +DataManager.pool
        val ps = conn.prepareStatement("SELECT * FROM FoxPrefix")
        val rs = ps.executeQuery()
        while (rs.next()) {
            val name = rs.getString("name")
            if (!cached.contains(name)) {
                val bytes = rs.getBytes("playerdata")
                val data = PlayerData.deserialize(bytes)
                cacheAllPlayerData[name] = data
            } else {
                syncLoad += name
            }
        }
        -conn
        if (syncLoad.isEmpty()) {
            callback()
        } else {
            Bukkit.getScheduler().runTask(Main.getPlugin()) {
                val data = mutableListOf<PlayerData>()
                for (name in syncLoad) {
                    val pd = DataManager.cacheData[name] ?: continue
                    data += (pd.copy())
                }
                Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin()) {
                    for (pd in data) {
                        cacheAllPlayerData[pd.name] = pd
                    }
                    callback()
                }
            }
        }
    }

    fun calcAchieve() {
        val data = mutableMapOf<String, AchieveData>()
        val sort = mutableListOf<String>()
        for ((n, pd) in cacheAllPlayerData) {
            data[n] = AchieveData(pd)
            sort += n
        }
        val rank = mutableMapOf<String, List<String>>()
        for (a in Achievements.keys) {
            val tsort = ArrayList(sort)
            tsort.sortBy {
                val ad = data[it]
                -(ad?.data!![a] ?: 0)
            }
            rank[a] = tsort
        }
        for (un in PrefixIndex.values) {
            val uu = un.unlock_value
            if (uu is VariableUnlock) {
                for (pd in data.values) {
                    if (uu.isUnlock_Async(pd)) {
                        pd.achieveCount++
                    }
                }
            }
        }
        sort.sortBy {
            val t = data[it]
            -(t?.achieveCount ?: 0)
        }
        achieveData = data
        achieveTop = sort
        rankTop = rank
    }


}

data class AchieveData(
        val playerData: PlayerData,
        val data: MutableMap<String, Int> = mutableMapOf(),
        var achieveCount: Int = 0
) {
    init {
        for (n in Achievements.keys) {
            data[n] = playerData.achievementData[n] ?: 0
        }
    }
}