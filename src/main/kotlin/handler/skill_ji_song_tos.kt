package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_ji_song_tos
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_ji_song_tos : AbstractProtoHandler<skill_ji_song_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_ji_song_tos) {
        val skill = r.findSkill(SkillId.JI_SONG) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            log.error("卡牌重复${pb.cardIdsList.toTypedArray().contentToString()}")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_ji_song_tos::class.java)
    }
}