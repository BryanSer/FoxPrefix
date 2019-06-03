package br.foxprefix.achievement

import br.foxprefix.Main
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

val Achievements = mutableMapOf<String, Achievement>()

enum class AchievementType(val clasz: Class<out Achievement>) {
    KILL(KillAchievement::class.java),
    DIG(DigAchievement::class.java),
    PLACE(PlaceAchievement::class.java),
    SMELT(SmeltAchievement::class.java),
    FISH(FishAchievement::class.java),
    ONLINE(OnlineAchievement::class.java);

    val list = mutableListOf<Achievement>()

    fun construct(config: ConfigurationSection): Achievement {
        val constructor = clasz.getConstructor(ConfigurationSection::class.java)
        val a = constructor.newInstance(config)
        Achievements[a.name] = a
        list += a
        return a
    }
}

fun getAchievementValue(str:String,p: Player):Int{
    val str = str.replace("%", "")
    val a = Achievements[str] ?: return 0
    return a.getValue(p)
}

fun loadAchievement() {
    Achievements.clear()
    AchievementType.values().forEach { it.list.clear() }
    val f = File(Main.getPlugin().dataFolder,"achievement.yml")
    if(!f.exists()){
        Main.getPlugin().saveResource("achievement.yml",false)
    }
    val config = YamlConfiguration.loadConfiguration(f)
    for(key in config.getKeys(false)){
        val cs = config.getConfigurationSection(key)
        readAchievement(cs)
    }
}

fun readAchievement(config: ConfigurationSection): Achievement? {
    val type = config.getString("Type")
    for (at in AchievementType.values()) {
        if (type.equals(at.name, true)) {
            return at.construct(config)
        }
    }
    return null
}