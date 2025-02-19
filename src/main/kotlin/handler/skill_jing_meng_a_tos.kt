package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_jing_meng_a_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_jing_meng_a_tos : AbstractProtoHandler<skill_jing_meng_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_jing_meng_a_tos) {
        val skill = r.findSkill(SkillId.JING_MENG)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_jing_meng_a_tos::class.java)
    }
}