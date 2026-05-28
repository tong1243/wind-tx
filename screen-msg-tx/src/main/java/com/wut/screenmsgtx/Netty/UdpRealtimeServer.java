package com.wut.screenmsgtx.Netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "msg.udp", name = "enabled", havingValue = "true")
public class UdpRealtimeServer implements ApplicationListener<ApplicationStartedEvent> {
    private static final Logger log = LoggerFactory.getLogger(UdpRealtimeServer.class);
    private final UdpRealtimeHandler udpRealtimeHandler;
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    @Value("${msg.udp.host:0.0.0.0}")
    private String host;

    @Value("${msg.udp.port:7777}")
    private int port;

    @Value("${msg.udp.wind-port:0}")
    private int windPort;

    @Value("${msg.udp.max-datagram-bytes:65535}")
    private int maxDatagramBytes;

    private final Map<Integer, Channel> channels = new ConcurrentHashMap<>();

    public UdpRealtimeServer(UdpRealtimeHandler udpRealtimeHandler) {
        this.udpRealtimeHandler = udpRealtimeHandler;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        try {
            bindPort(port);
            if (windPort > 0 && windPort != port) {
                bindPort(windPort);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("UDP realtime server startup interrupted", e);
        } catch (Exception e) {
            log.error("UDP realtime server startup failed", e);
        }
    }

    private void bindPort(int bindPort) throws InterruptedException {
        int receiveDatagramBytes = Math.max(2048, Math.min(maxDatagramBytes, 65535));
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_RCVBUF, 1024 * 1024 * 10)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 10)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(receiveDatagramBytes))
                .handler(udpRealtimeHandler);

        ChannelFuture channelFuture = bootstrap.bind(host, bindPort).sync();
        if (channelFuture.isSuccess()) {
            channels.put(bindPort, channelFuture.channel());
            log.info("UDP realtime server started on {}:{}, datagramBufferBytes={}", host, bindPort, receiveDatagramBytes);
            return;
        }
        log.error("UDP realtime server failed to start on {}:{}", host, bindPort);
    }

    @PreDestroy
    public void destroy() {
        try {
            for (Channel channel : channels.values()) {
                if (channel != null) {
                    channel.close();
                }
            }
            channels.clear();
            eventLoopGroup.shutdownGracefully();
        } catch (Exception e) {
            log.error("UDP realtime server shutdown failed", e);
        }
    }
}
