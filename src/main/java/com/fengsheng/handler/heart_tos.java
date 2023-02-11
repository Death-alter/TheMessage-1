package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.network.WebSocketServerChannelHandler;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;

public class heart_tos implements ProtoHandler {
    private static final short msgId = WebSocketServerChannelHandler.stringHash("heart_toc");

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        byte[] buf = Fengsheng.heart_toc.newBuilder().setOnlineCount(Game.deviceCache.size()).build().toByteArray();
        player.send(msgId, buf, true);
    }
}
