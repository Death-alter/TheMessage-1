package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng.use_jie_huo_tos
import org.apache.log4j.Logger

class use_jie_huo_tos : AbstractProtoHandler<use_jie_huo_tos>() {
    override fun handle0(r: HumanPlayer, pb: use_jie_huo_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        if (card.type != card_type.Jie_Huo) {
            log.error("这张牌不是截获，而是$card")
            return
        }
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            card.execute(r.game!!, r)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_jie_huo_tos::class.java)
    }
}