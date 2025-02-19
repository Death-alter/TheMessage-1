package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

/**
 * 判断是否有人胜利
 *  * 只有接收阶段正常接收情报才会进入 [ReceivePhaseSenderSkill]
 *  * 其它情况均为置入情报区，一律进入这里。
 */
data class CheckWin(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 接收第三张黑色情报的顺序
     */
    val receiveOrder: ReceiveOrder,
    /**
     * 濒死结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : Fsm {
    constructor(whoseTurn: Player, afterDieResolve: Fsm) : this(whoseTurn, ReceiveOrder(), afterDieResolve)

    override fun resolve(): ResolveResult {
        val game = whoseTurn.game!!
        val players = game.players.filterNotNull().filter { !it.lose }
        val stealer = players.find { it.identity == color.Black && it.secretTask == secret_task.Stealer } // 簒夺者
        val mutator = players.find { it.alive && it.identity == color.Black && it.secretTask == secret_task.Mutator }
        val redPlayers = players.filter { it.identity == color.Red }
        val bluePlayers = players.filter { it.identity == color.Blue }
        var declareWinner = ArrayList<Player>()
        var winner = ArrayList<Player>()
        var redWin = false
        var blueWin = false
        var mutatorMayWin = false
        players.forEach { player ->
            val red = player.messageCards.count { it.colors.contains(color.Red) }
            val blue = player.messageCards.count { it.colors.contains(color.Blue) }
            if (red >= 3 || blue >= 3) {
                mutatorMayWin = true
            }
            when (player.identity) {
                color.Black -> {
                    if (player.secretTask == secret_task.Collector && (red >= 3 || blue >= 3)) {
                        declareWinner.add(player)
                        winner.add(player)
                    }
                }

                color.Red -> {
                    if (red >= 3) {
                        declareWinner.add(player)
                        redWin = true
                    }
                }

                color.Blue -> {
                    if (blue >= 3) {
                        declareWinner.add(player)
                        blueWin = true
                    }
                }

                else -> {}
            }
        }
        if (redWin) {
            winner.addAll(redPlayers)
            if (game.players.size == 4) winner.addAll(bluePlayers) // 四人局潜伏和军情会同时获胜
        }
        if (blueWin) {
            winner.addAll(bluePlayers)
            if (game.players.size == 4) winner.addAll(redPlayers) // 四人局潜伏和军情会同时获胜
        }
        if (declareWinner.isEmpty() && mutator != null && mutatorMayWin) {
            declareWinner.add(mutator)
            winner.add(mutator)
        }
        if (declareWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declareWinner = arrayListOf(stealer)
            winner = ArrayList(declareWinner)
        }
        if (declareWinner.isNotEmpty()) {
            if (winner.any { it.findSkill(SkillId.WEI_SHENG) != null && it.roleFaceUp }) {
                winner.addAll(players.filter { it.identity == color.Has_No_Identity })
            }
            val declareWinners = declareWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info("${declareWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
            game.allPlayerSetRoleFaceUp()
            for (p in game.players) p!!.notifyWin(declareWinners, winners)
            whoseTurn.game!!.end(winner)
            return ResolveResult(null, false)
        }
        return ResolveResult(StartWaitForChengQing(whoseTurn, receiveOrder, afterDieResolve), true)
    }

    companion object {
        private val log = Logger.getLogger(CheckWin::class.java)
    }
}