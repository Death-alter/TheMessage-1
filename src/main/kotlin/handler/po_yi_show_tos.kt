package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.po_yi_show_tos

import org.apache.log4j.Logger

class po_yi_show_tos : AbstractProtoHandler<po_yi_show_tos>() {
    override fun handle0(r: HumanPlayer, pb: po_yi_show_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(po_yi_show_tos::class.java)
    }
}