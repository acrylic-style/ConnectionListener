package xyz.acrylicstyle.connectionListener.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import xyz.acrylicstyle.connectionListener.ConnectionListenerPlugin;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

public class NettyHAProxyChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = Logger.getLogger("NettyHAProxyChannelInitializer");
    private final ChannelInitializer<SocketChannel> vanillaHandler;
    private final Method method;

    public NettyHAProxyChannelInitializer(ChannelInitializer<SocketChannel> vanillaHandler) {
        this.vanillaHandler = vanillaHandler;
        try {
            method = this.vanillaHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
            method.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeVanillaHandler(SocketChannel channel) throws Exception {
        if (vanillaHandler instanceof NettyVanillaChannelInitializer) {
            ((NettyVanillaChannelInitializer) vanillaHandler).initChannel(channel);
        } else {
            this.method.invoke(this.vanillaHandler, channel);
        }
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        try {
            invokeVanillaHandler(channel);
            channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
            channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", newHAProxyMessageHandler(channel));
        } catch (Exception ex) {
            LOGGER.severe("Failed to execute initChannel");
            ex.printStackTrace();
        }
    }

    public static ChannelInboundHandlerAdapter newHAProxyMessageHandler(Channel channel) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof HAProxyMessage) {
                    try {
                        HAProxyMessage message = (HAProxyMessage) msg;
                        SocketAddress address = new InetSocketAddress(message.sourceAddress(), message.sourcePort());
                        io.netty.channel.ChannelHandler handler = channel.pipeline().get("packet_handler");
                        try {
                            ConnectionListenerPlugin.NetworkManager.getField("socketAddress").set(handler, address);
                        } catch (NoSuchFieldException ex) {
                            ConnectionListenerPlugin.NetworkManager.getField("l").set(handler, address);
                        }
                        LOGGER.info("Set remote address to " + address + " via PROXY protocol");
                    } catch (Exception ex) {
                        LOGGER.severe("Failed to process HAProxy message");
                        ex.printStackTrace();
                    }
                } else {
                    super.channelRead(ctx, msg);
                }
            }
        };
    }
}
