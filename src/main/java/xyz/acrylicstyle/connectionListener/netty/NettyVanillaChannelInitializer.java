package xyz.acrylicstyle.connectionListener.netty;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import xyz.acrylicstyle.connectionListener.ConnectionListenerPlugin;
import xyz.acrylicstyle.connectionListener.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

import static xyz.acrylicstyle.connectionListener.util.ReflectionUtil.getConnectedChannelField;
import static xyz.acrylicstyle.connectionListener.util.ReflectionUtil.newInstance;

public class NettyVanillaChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = Logger.getLogger("NettyVanillaChannelInitializer");
    private final Object minecraftServer;
    private final Object serverConnection;

    public NettyVanillaChannelInitializer(Object minecraftServer, Object serverConnection) {
        this.minecraftServer = minecraftServer;
        this.serverConnection = serverConnection;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void initChannel(SocketChannel channel) {
        try {
            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        } catch (ChannelException ignore) {}
        try {
            channel.pipeline()
                    .addLast("timeout", new ReadTimeoutHandler(30))
                    .addLast("legacy_query", newInstance("LegacyPingHandler", serverConnection))
                    .addLast("splitter", newInstance("PacketSplitter"))
                    .addLast("decoder", newInstance("PacketDecoder", ReflectionUtil.getProtocolDirection("SERVERBOUND")))
                    .addLast("prepender", newInstance("PacketPrepender"))
                    .addLast("encoder", newInstance("PacketEncoder", ReflectionUtil.getProtocolDirection("CLIENTBOUND")));
            Object networkManager = newInstance("NetworkManager", ReflectionUtil.getProtocolDirection("SERVERBOUND"));
            Field f = getConnectedChannelField();
            f.setAccessible(true);
            List connectedChannels = (List) f.get(serverConnection);
            connectedChannels.add(networkManager);
            channel.pipeline().addLast("packet_handler", (ChannelHandler) networkManager);
            Object handshakeListener = Class.forName(ReflectionUtil.nms("HandshakeListener"))
                    .getConstructor(ConnectionListenerPlugin.MinecraftServer, ConnectionListenerPlugin.NetworkManager)
                    .newInstance(minecraftServer, networkManager);
            ConnectionListenerPlugin.NetworkManager
                    .getMethod("setPacketListener", Class.forName(ReflectionUtil.nms("PacketListener")))
                    .invoke(networkManager, handshakeListener);
        } catch (Exception ex) {
            LOGGER.severe("Failed to execute initChannel");
            ex.printStackTrace();
        }
    }
}
