package com.wut.screenmsgtx.Netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
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

    private Channel channel;

    public UdpRealtimeServer(UdpRealtimeHandler udpRealtimeHandler) {
        this.udpRealtimeHandler = udpRealtimeHandler;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024 * 10)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 10)
                    .handler(udpRealtimeHandler);

            ChannelFuture channelFuture = bootstrap.bind(host, port).sync();
            if (channelFuture.isSuccess()) {
                channel = channelFuture.channel();
                log.info("UDP realtime server started on {}:{}", host, port);
            } else {
                log.error("UDP realtime server failed to start on {}:{}", host, port);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("UDP realtime server startup interrupted", e);
        } catch (Exception e) {
            log.error("UDP realtime server startup failed", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (channel != null) {
                channel.close();
            }
            eventLoopGroup.shutdownGracefully();
        } catch (Exception e) {
            log.error("UDP realtime server shutdown failed", e);
        }
    }
}
