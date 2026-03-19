package com.wut.screenmsgtx.Netty;

import com.wut.screenmsgtx.Service.UdpRealtimeDataService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@ChannelHandler.Sharable
@ConditionalOnProperty(prefix = "msg.udp", name = "enabled", havingValue = "true")
public class UdpRealtimeHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final UdpRealtimeDataService udpRealtimeDataService;
    private final Executor msgTransmitTaskAsyncPool;

    @Value("${msg.udp.ack-enabled:false}")
    private boolean ackEnabled;

    @Value("${msg.udp.ack-message:ACK}")
    private String ackMessage;

    public UdpRealtimeHandler(UdpRealtimeDataService udpRealtimeDataService, @Qualifier("msgTransmitTaskAsyncPool") Executor msgTransmitTaskAsyncPool) {
        this.udpRealtimeDataService = udpRealtimeDataService;
        this.msgTransmitTaskAsyncPool = msgTransmitTaskAsyncPool;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf content = packet.content();
        if (content.readableBytes() == 0) {
            return;
        }
        byte[] body = new byte[content.readableBytes()];
        content.readBytes(body);

        CompletableFuture.runAsync(() -> udpRealtimeDataService.handleUdpPayload(body), msgTransmitTaskAsyncPool)
                .exceptionally(ex -> {
                    log.error("UDP payload process failed", ex);
                    return null;
                });

        if (ackEnabled) {
            ByteBuf response = Unpooled.copiedBuffer(ackMessage, StandardCharsets.UTF_8);
            ctx.writeAndFlush(new DatagramPacket(response, packet.sender()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("UDP handler error", cause);
    }
}
