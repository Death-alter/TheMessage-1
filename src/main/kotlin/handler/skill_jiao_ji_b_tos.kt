package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_jiao_ji_b_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_jiao_ji_b_tos : AbstractProtoHandler<skill_jiao_ji_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_jiao_ji_b_tos) {
        val skill = r.findSkill(SkillId.JIAO_JI)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            log.error("卡牌重复${pb.cardIdsList.toTypedArray().contentToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_jiao_ji_b_tos::class.java)
    }
}