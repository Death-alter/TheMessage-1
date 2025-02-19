package com.fengsheng.network

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.net.URISyntaxException
import java.util.function.Function

class HttpServerChannelHandler : SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpRequest) {
            if (msg.method() !== HttpMethod.GET) {
                val byteBuf = Unpooled.copiedBuffer("{\"error\": \"invalid method\"}", CharsetUtil.UTF_8)
                val response: FullHttpResponse =
                    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED, byteBuf)
                response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                ctx.writeAndFlush(response)
            } else {
                try {
                    val uri = URI(msg.uri())
                    val form: MutableMap<String, String> = HashMap()
                    val query = uri.query
                    if (query != null) {
                        for (s in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                            val arr = s.split("=".toRegex(), limit = 2).toTypedArray()
                            form.putIfAbsent(arr[0], if (arr.size >= 2) arr[1] else "")
                        }
                    }
                    val name = uri.path.replace("/", "")
                    val cls = this.javaClass.classLoader.loadClass("com.fengsheng.gm.$name")

                    @Suppress("UNCHECKED_CAST")
                    val handler = cls.getDeclaredConstructor().newInstance() as Function<Map<String, String?>, String>
                    val byteBuf = Unpooled.copiedBuffer(handler.apply(form), CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: URISyntaxException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"parse form failed\"}", CharsetUtil.UTF_8)
                    val response =
                        DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: ClassNotFoundException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: InvocationTargetException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: InstantiationException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: IllegalAccessException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                } catch (e: NoSuchMethodException) {
                    val byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8)
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf)
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
                    ctx.writeAndFlush(response)
                }
            }
        }
    }
}