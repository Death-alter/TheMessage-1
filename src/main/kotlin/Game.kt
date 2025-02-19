package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.card.Card
import com.fengsheng.card.Deck
import com.fengsheng.network.Network
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.TriggeredSkill
import com.google.protobuf.GeneratedMessageV3
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random

class Game private constructor(totalPlayerCount: Int) {
    val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val id: Int = ++increaseId

    @Volatile
    var isStarted = false

    @Volatile
    var isEnd = false
        private set
    var players: Array<Player?>
    var deck = Deck(this)
    var fsm: Fsm? = null
        private set
    val listeningSkills = ArrayList<TriggeredSkill>()

    /**
     * 用于王田香技能禁闭
     */
    var jinBiPlayer: Player? = null

    /**
     * 用于张一挺技能强令
     */
    val qiangLingTypes = HashSet<card_type>()

    init {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        players = arrayOfNulls(totalPlayerCount)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (!isEnd) try {
                val callBack = queue.receive()
                callBack()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (e: Exception) {
                log.error("catch throwable", e)
            }
        }
    }

    /**
     * 玩家进入房间时调用
     */
    fun onPlayerJoinRoom(player: Player, count: PlayerGameCount) {
        val index = players.indexOfFirst { it == null }
        players[index] = player
        player.location = index
        val unready = players.count { it == null }
        val builder = join_room_toc.newBuilder()
        builder.name = player.playerName
        builder.position = player.location
        builder.winCount = count.winCount
        builder.gameCount = count.gameCount
        val msg = builder.build()
        players.forEach { if (it !== player && it is HumanPlayer) it.send(msg) }
        if (unready == 0) {
            log.info("${player.playerName}加入了。已加入${players.size}个人，游戏开始。。。")
            isStarted = true
            GameExecutor.post(this) { start() }
            newInstance()
        } else {
            log.info("${player.playerName}加入了。已加入${players.size - unready}个人，等待${unready}人加入。。。")
        }
    }

    fun start() {
        var identities = ArrayList<color>()
        when (players.size) {
            2 -> identities = when (Random.nextInt(4)) {
                0 -> arrayListOf(color.Red, color.Blue)
                1 -> arrayListOf(color.Red, color.Black)
                2 -> arrayListOf(color.Blue, color.Black)
                else -> arrayListOf(color.Black, color.Black)
            }

            9 -> {
                identities.add(color.Red)
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Black)
            }

            4 -> {
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Black)
            }

            3 -> {
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
            }

            else -> {
                var i = 0
                while (i < (players.size - 1) / 2) {
                    identities.add(color.Red)
                    identities.add(color.Blue)
                    i++
                }
                identities.add(color.Black)
                if (players.size % 2 == 0) identities.add(color.Black)
            }
        }
        identities.shuffle()
        val tasks = listOf(
            secret_task.Killer,
            secret_task.Stealer,
            secret_task.Collector,
            secret_task.Mutator,
            secret_task.Pioneer
        ).shuffled()
        var secretIndex = 0
        for (i in players.indices) {
            val identity = identities[i]
            val task = if (identity == color.Black) tasks[secretIndex++] else secret_task.forNumber(0)
            players[i]!!.identity = identity
            players[i]!!.secretTask = task
            players[i]!!.originIdentity = identity
            players[i]!!.originSecretTask = task
        }
        val roleSkillsDataArray = if (Config.IsGmEnable) RoleCache.getRandomRolesWithSpecific(
            players.size * 2,
            Config.DebugRoles
        ) else RoleCache.getRandomRoles(players.size * 2)
        resolve(WaitForSelectRole(this, roleSkillsDataArray))
    }

    fun end(winners: List<Player?>?) {
        isEnd = true
        GameCache.remove(id)
        var isHumanGame = true
        for (p in players) {
            if (p is HumanPlayer) {
                p.saveRecord()
                deviceCache.remove(p.device)
            } else {
                isHumanGame = false
            }
        }
        if (winners != null && isHumanGame && players.size >= 5) {
            val records = ArrayList<Statistics.Record>(players.size)
            val playerGameResultList = ArrayList<PlayerGameResult>()
            for (p in players) {
                val win = winners.contains(p!!)
                records.add(Statistics.Record(p.role, win, p.originIdentity, p.originSecretTask, players.size))
                if (p is HumanPlayer) playerGameResultList.add(PlayerGameResult(p, win))
            }
            Statistics.add(records)
            Statistics.addPlayerGameCount(playerGameResultList)
        }
        queue.close()
    }

    /**
     * 玩家弃牌
     */
    fun playerDiscardCard(player: Player, vararg cards: Card) {
        if (cards.isEmpty()) return
        player.cards.removeAll(cards.toSet())
        log.info("${player}弃掉了${cards.contentToString()}，剩余手牌${player.cards.size}张")
        deck.discard(*cards)
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = discard_card_toc.newBuilder().setPlayerId(p.getAlternativeLocation(player.location))
                for (card in cards) {
                    builder.addCards(card.toPbCard())
                }
                p.send(builder.build())
            }
        }
    }

    fun playerSetRoleFaceUp(player: Player?, faceUp: Boolean) {
        if (faceUp) {
            log.error(if (player!!.roleFaceUp) "${player}本来就是正面朝上的" else "${player}将角色翻至正面朝上")
            player.roleFaceUp = true
        } else {
            log.error(if (player!!.roleFaceUp) "${player}本来就是背面朝上的" else "${player}将角色翻至背面朝上")
            player.roleFaceUp = false
        }
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = notify_role_update_toc.newBuilder().setPlayerId(
                    p.getAlternativeLocation(player.location)
                )
                builder.role = if (player.roleFaceUp) player.role else role.unknown
                p.send(builder.build())
            }
        }
    }

    fun allPlayerSetRoleFaceUp() {
        for (p in players) {
            if (!p!!.roleFaceUp) playerSetRoleFaceUp(p, true)
        }
    }

    /**
     * 继续处理当前状态机
     */
    fun continueResolve() {
        GameExecutor.post(this) {
            val result = fsm!!.resolve()
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        }
    }

    /**
     * 对于[WaitingFsm]，当收到玩家协议时，继续处理当前状态机
     */
    fun tryContinueResolveProtocol(player: Player, pb: GeneratedMessageV3) {
        GameExecutor.post(this) {
            if (fsm !is WaitingFsm) {
                log.error("时机错误，当前时点为：$fsm")
                return@post
            }
            val result = (fsm as WaitingFsm).resolveProtocol(player, pb)
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        }
    }

    /**
     * 更新一个新的状态机并结算，只能由游戏所在线程调用
     */
    fun resolve(fsm: Fsm?) {
        this.fsm = fsm
        continueResolve()
    }

    /**
     * 增加一个新的需要监听的技能。仅用于接收情报时、使用卡牌时、死亡时的技能
     */
    fun addListeningSkill(skill: TriggeredSkill) {
        listeningSkills.add(skill)
    }

    /**
     * 遍历监听列表，结算技能
     */
    fun dealListeningSkill(): ResolveResult? {
        for (skill in listeningSkills) {
            val result = skill.execute(this)
            if (result != null) return result
        }
        return null
    }

    /**
     * 判断是否仅剩的一个阵营存活
     */
    fun checkOnlyOneAliveIdentityPlayers(): Boolean {
        var identity: color? = null
        val players = players.filterNotNull().filter { !it.lose }
        val alivePlayers = players.filter {
            if (!it.alive) return@filter false
            when (identity) {
                null -> identity = it.identity
                color.Black -> return false
                it.identity -> {}
                else -> return false
            }
            true
        }
        val winner =
            if (alivePlayers.size == 1 && alivePlayers.first()
                    .let { it.identity == color.Black && it.secretTask == secret_task.Stealer }
            ) {
                log.info("只剩簒夺者者存活，无法获胜，游戏失败")
                ArrayList()
            } else if (identity == color.Red || identity == color.Blue) {
                players.filter { identity == it.identity }.toMutableList()
            } else {
                alivePlayers.toMutableList()
            }
        if (winner.any { it.findSkill(SkillId.WEI_SHENG) != null && it.roleFaceUp }) {
            winner.addAll(players.filter { it.identity == color.Has_No_Identity })
        }
        val winners = winner.toTypedArray()
        log.info("只剩下${alivePlayers.toTypedArray().contentToString()}存活，胜利者有${winners.contentToString()}")
        allPlayerSetRoleFaceUp()
        this.players.forEach { it!!.notifyWin(arrayOf(), winners) }
        end(winner)
        return true
    }

    companion object {
        private val log = Logger.getLogger(Game::class.java)
        val playerCache = ConcurrentHashMap<String, HumanPlayer>()
        val GameCache = ConcurrentHashMap<Int, Game>()
        val deviceCache = ConcurrentHashMap<String, HumanPlayer>()
        private var increaseId = 0
        var newGame = Game(Config.TotalPlayerCount)

        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            if (playerCache.put(newPlayer.channel.id().asLongText(), oldPlayer) == null) {
                log.error("channel [id: ${newPlayer.channel.id().asLongText()}] not exists")
            }
        }

        /**
         * 不是线程安全的
         */
        fun newInstance() {
            newGame = Game(max(newGame.players.size, Config.TotalPlayerCount))
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Class.forName("com.fengsheng.skill.RoleCache")
            Statistics.load()
            Network.init()
        }
    }
}