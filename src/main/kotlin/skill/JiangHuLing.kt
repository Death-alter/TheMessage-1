package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * 王富贵技能【江湖令】：你传出情报后，可以宣言一个颜色。本回合中，当情报被接收后，你可以从接收者的情报区弃置一张被宣言颜色的情报，若弃置的是黑色情报，则你摸一张牌。
 */
class JiangHuLing : TriggeredSkill {
    override val skillId = SkillId.JIANG_HU_LING

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnSendCard) {
            val r: Player = fsm.whoseTurn
            if (r.findSkill(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 1) return null
            r.addSkillUseCount(skillId)
            return ResolveResult(executeJiangHuLingA(fsm, r), true)
        }
        return null
    }

    private data class executeJiangHuLingA(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_a_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq2)) {
                                    val builder2 = skill_jiang_hu_ling_a_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    player.game!!.tryContinueResolveProtocol(player, builder2.build())
                                }
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val colors = arrayOf(color.Black, color.Red, color.Blue)
                    val color = colors[ThreadLocalRandom.current().nextInt(colors.size)]
                    val builder = skill_jiang_hu_ling_a_tos.newBuilder()
                    builder.enable = true
                    builder.color = color
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is skill_jiang_hu_ling_a_tos) {
                log.error("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.color == color.UNRECOGNIZED) {
                log.error("未知的颜色类型")
                return null
            }
            r.incrSeq()
            val skill = JiangHuLing2(message.color)
            skill.init(r.game!!)
            r.skills = arrayOf(*r.skills, skill)
            log.info("${r}发动了[江湖令]，宣言了${message.color}")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiang_hu_ling_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.color = message.color
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJiangHuLingA::class.java)
        }
    }

    private class JiangHuLing2(val color: color) : TriggeredSkill {
        override val skillId = SkillId.JIANG_HU_LING2

        override fun execute(g: Game): ResolveResult? {
            val fsm = g.fsm
            if (fsm is ReceivePhaseSenderSkill) {
                val r = fsm.whoseTurn
                if (r.findSkill(skillId) == null) return null
                if (r.getSkillUseCount(skillId) >= 1) return null
                var containsColor = false
                for (card in fsm.inFrontOfWhom.messageCards) {
                    if (card.colors.contains(color)) {
                        containsColor = true
                        break
                    }
                }
                if (!containsColor) return null
                r.addSkillUseCount(skillId)
                return ResolveResult(executeJiangHuLingB(fsm, color), true)
            }
            return null
        }
    }

    private data class executeJiangHuLingB(val fsm: ReceivePhaseSenderSkill, val color: color) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.whoseTurn.location)
                    builder.color = color
                    builder.waitingSecond = 15
                    if (p === fsm.whoseTurn) {
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(
                            p.game!!,
                            {
                                if (p.checkSeq(seq2)) {
                                    val builder2 = end_receive_phase_tos.newBuilder()
                                    builder2.seq = seq2
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            },
                            p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    p.send(builder.build())
                }
            }
            val p = fsm.whoseTurn
            if (p is RobotPlayer) {
                val target = fsm.inFrontOfWhom
                if (target.alive) {
                    for (card in target.messageCards) {
                        if (card.colors.contains(color) && !(p === target && color != Common.color.Black && card.colors.size == 1)) {
                            GameExecutor.post(
                                p.game!!,
                                {
                                    val builder = skill_jiang_hu_ling_b_tos.newBuilder()
                                    builder.cardId = card.id
                                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                                },
                                2,
                                TimeUnit.SECONDS
                            )
                            return null
                        }
                    }
                }
                GameExecutor.TimeWheel.newTimeout({
                    p.game!!.tryContinueResolveProtocol(p, end_receive_phase_tos.getDefaultInstance())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jiang_hu_ling_b_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.whoseTurn
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            val target = fsm.inFrontOfWhom
            if (!target.alive) {
                log.error("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (!card.colors.contains(color)) {
                log.error("你选择的情报不是宣言的颜色")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[江湖令]，弃掉了${target}面前的$card")
            target.deleteMessageCard(card.id)
            r.game!!.deck.discard(card)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiang_hu_ling_b_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            if (card.colors.contains(Common.color.Black)) r.draw(1)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJiangHuLingB::class.java)
        }
    }

    companion object {
        fun resetJiangHuLing(game: Game) {
            for (p in game.players) {
                val skills = p!!.skills
                var containsJiangHuLing = false
                for (skill in skills) {
                    if (skill.skillId == SkillId.JIANG_HU_LING2) {
                        containsJiangHuLing = true
                        break
                    }
                }
                if (containsJiangHuLing) {
                    p.skills = skills.filterNot { it.skillId == SkillId.JIANG_HU_LING2 }.toTypedArray()
                    val listeningSkills = game.listeningSkills
                    listeningSkills.removeAt(listeningSkills.indexOfLast { it is JiangHuLing2 })
                }
            }
        }
    }
}