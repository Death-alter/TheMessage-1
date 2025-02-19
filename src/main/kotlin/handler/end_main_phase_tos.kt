package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Fengsheng.end_main_phase_tos

import org.apache.log4j.Logger

class end_main_phase_tos : AbstractProtoHandler<end_main_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: end_main_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val fsm = r.game!!.fsm as? MainPhaseIdle
        if (r !== fsm?.player) {
            log.error("不是你的回合的出牌阶段")
            return
        }
        r.incrSeq()
        r.game!!.resolve(SendPhaseStart(fsm.player))
    }

    companion object {
        private val log = Logger.getLogger(end_main_phase_tos::class.java)
    }
}