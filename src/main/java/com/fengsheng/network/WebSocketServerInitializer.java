package com.fengsheng.network;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        var pipeline = ch.pipeline();
        pipeline.addLast("httpServerCodec", new HttpServerCodec());
        pipeline.addLast("http-aggregator", new HttpObjectAggregator(65535));
        pipeline.addLast("ws-handler", new WebSocketServerProtocolHandler("/ws"));
        pipeline.addLast("webSocketServerHandler", new WebSocketServerChannelHandler());
    }
}