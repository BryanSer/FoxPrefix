package br.foxprefix

import br.foxprefix.prefix.Prefix
import br.foxprefix.prefix.Prefixs
import br.foxprefix.quest.Quest
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.*
import java.sql.Connection

operator fun HikariDataSource.unaryPlus(): Connection = this.connection
operator fun HikariDataSource.minus(conn: Connection): Unit = this.evictConnection(conn)
operator fun Connection.unaryMinus() = DataManager.pool - this

object DataManager : Listener {
    val cacheData: MutableMap<String, PlayerData> = HashMap()
    lateinit var pool: HikariDataSource
    infix fun get(name: String): PlayerData? = cacheData[name]
    infix fun save(p: Player) {
        saveData(p.name, false)
    }


    @EventHandler
    fun onChat(evt: AsyncPlayerChatEvent) {
        val pd = this get evt.player.name ?: return
        if (pd.equip != null) {
            val prefix = Prefixs[pd.equip!!] ?: return
            evt.format = prefix.display + evt.format
        }
    }

    @EventHandler
    fun onJoin(evt: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(Main.getPlugin(), {
            loadData(evt.player.name)
        }, 40)
    }

    @EventHandler
    fun onQuit(evt: PlayerQuitEvent) {
        saveData(evt.player.name, true)
    }

    fun loadData(name: String) {
        selectData(name) {
            if (it != null) {
                cacheData[name] = it
                if(it.questDone == null){
                    it.questDone = mutableSetOf()
                }
            } else if (!cacheData.containsKey(name)) {
                val pd = PlayerData(name)
                cacheData[name] = pd
                insertData(name, pd)
            }
        }
    }

    fun saveData(name: String, remove: Boolean = false) {
        val pd = cacheData[name]
        if (pd != null) {
            saveData(name, pd) {
                if (remove) {
                    cacheData -= name
                }
            }
        }
    }

    fun insertData(name: String, data: PlayerData, callback: (() -> Unit)? = null) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin()) {
            val conn = +pool
            val ps = conn.prepareStatement("INSERT INTO FoxPrefix VALUES (?,?)")
            ps.setString(1, name)
            ps.setBytes(2, data.toByte())
            ps.executeUpdate()
            if (callback != null) {
                Bukkit.getScheduler().runTask(Main.getPlugin()) {
                    callback()
                }
            }
            pool - conn
        }
    }


    fun saveDataSync(name: String, data: PlayerData) {
        val conn = +pool
        val ps = conn.prepareStatement("UPDATE FoxPrefix SET playerdata = ? WHERE name = ? LIMIT 1")
        ps.setBytes(1, data.toByte())
        ps.setString(2, name)
        ps.executeUpdate()
        pool - conn
    }

    fun saveData(name: String, data: PlayerData, callback: (() -> Unit)? = null) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin()) {
            val conn = +pool
            val ps = conn.prepareStatement("UPDATE FoxPrefix SET playerdata = ? WHERE name = ? LIMIT 1")
            ps.setBytes(1, data.toByte())
            ps.setString(2, name)
            ps.executeUpdate()
            if (callback != null) {
                Bukkit.getScheduler().runTask(Main.getPlugin()) {
                    callback()
                }
            }
            pool - conn
        }
    }

    fun selectData(name: String, callback: (PlayerData?) -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin()) {
            val conn = +pool
            val ps = conn.prepareStatement("SELECT * FROM FoxPrefix WHERE name = ? LIMIT 1")
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) {
                val bytes = rs.getBytes("playerdata")
                val data = PlayerData.deserialize(bytes)
                Bukkit.getScheduler().runTask(Main.getPlugin()) {
                    callback(data)
                }
            } else {
                Bukkit.getScheduler().runTask(Main.getPlugin()) {
                    callback(null)
                }
            }
            pool - conn
        }
    }

    fun connectSQL() {
        val f = File(Main.getPlugin().dataFolder, "config.yml")
        if (!f.exists()) {
            Main.getPlugin().saveDefaultConfig()
        }
        val data = YamlConfiguration.loadConfiguration(f)
        val db = data.getConfigurationSection("Mysql")
        val sb = StringBuilder(String.format("jdbc:mysql://%s:%d/%s?user=%s&password=%s",
                db.getString("host"),
                db.getInt("port"),
                db.getString("database"),
                db.getString("user"),
                db.getString("password")
        ))
        for (s in db.getStringList("options")) {
            sb.append('&')
            sb.append(s)
        }
        val config = HikariConfig()
        config.jdbcUrl = sb.toString()
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.idleTimeout = 60000
        config.connectionTimeout = 60000
        config.validationTimeout = 3000
        config.maxLifetime = 60000
        pool = HikariDataSource(config)

        val conn = +pool
        val sta = conn.createStatement()
        sta.execute("CREATE TABLE IF NOT EXISTS FoxPrefix(name VARCHAR(80) NOT NULL PRIMARY KEY, playerdata BLOB NOT NULL) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4")
        sta.close()
        pool - conn
    }
}


data class PlayerData(
        val name: String,
        var achievementData: MutableMap<String, Int> = HashMap(),
        var unlockPrefix: MutableList<String> = ArrayList(),
        var equip: String? = null
) : Serializable {

    @Transient
    var questDone: MutableSet<String>? = mutableSetOf()

    fun copy(): PlayerData {
        val pd = PlayerData(name)
        pd.achievementData = HashMap(achievementData)
        pd.unlockPrefix = ArrayList(unlockPrefix)
        pd.equip = equip
        return pd
    }

    companion object {
        const val serialVersionUID: Long = 0x10C9B9F9A2E0L

        fun deserialize(byte: ByteArray): PlayerData {
            val input = ByteArrayInputStream(byte)
            val io = ObjectInputStream(input)
            val pd = io.readObject() as PlayerData
            try {
                pd.questDone = io.readObject() as MutableSet<String>
            } catch (e: Throwable) {
            } finally {
                if (pd.questDone == null) {
                    pd.questDone = mutableSetOf()
                }
            }
            io.close()
            input.close()
            return pd
        }
    }

    fun isComplete(q: Quest) :Boolean{
        if(this.questDone == null){
            this.questDone = mutableSetOf()
        }
        return this.questDone?.contains(q.name) ?: false
    }

    fun isComplete(q: String) :Boolean{
        if(this.questDone == null){
            this.questDone = mutableSetOf()
        }
        return this.questDone?.contains(q) ?: false
    }

    fun isUnlocked(prefix: Prefix) = unlockPrefix.contains(prefix.name)


    fun toByte(): ByteArray {
        if(questDone == null){
            questDone = mutableSetOf()
        }
        val baos = ByteArrayOutputStream(3000)
        val oo = ObjectOutputStream(baos)
        oo.writeObject(this)
        oo.writeObject(questDone)
        oo.flush()
        val t = baos.toByteArray()
        oo.close()
        baos.close()
        return t
    }
}