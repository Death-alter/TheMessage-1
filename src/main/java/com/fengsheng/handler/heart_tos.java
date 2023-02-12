package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;

public class heart_tos implements ProtoHandler {
    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        byte[] buf = Fengsheng.heart_toc.newBuilder().setOnlineCount(Game.deviceCache.size()).build().toByteArray();
        player.send("heart_toc", buf, true);
    }
}
