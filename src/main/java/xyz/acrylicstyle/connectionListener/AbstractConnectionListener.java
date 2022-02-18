package xyz.acrylicstyle.connectionListener;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.acrylicstyle.connectionListener.netty.NettyHAProxyChannelInitializer;
import xyz.acrylicstyle.connectionListener.netty.NettyVanillaChannelInitializer;
import xyz.acrylicstyle.connectionListener.util.LazyInitVar;
import xyz.acrylicstyle.connectionListener.util.ReflectionUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Adds HAProxy related methods
public abstract class AbstractConnectionListener extends JavaPlugin {
    public static final Class<?> MinecraftServer;
    public static final Class<?> ServerConnection;
    public static final Class<?> NetworkManager;
    public static final Method MinecraftServer_getServer;
    public static final Method MinecraftServer_getServerConnection;
    protected final List<Channel> channels = new ArrayList<>();

    static {
        Class<?> class1;
        Class<?> class2;
        Class<?> class3;
        Method method1;
        Method method2 = null;
        try {
            class1 = Class.forName(ReflectionUtil.nms("MinecraftServer"));
            class2 = Class.forName(ReflectionUtil.nms("ServerConnection"));
            class3 = Class.forName(ReflectionUtil.nms("NetworkManager"));
            method1 = class1.getDeclaredMethod("getServer");
            for (Method method : class1.getMethods()) {
                if (method.getReturnType() != class2) continue;
                if (method.getParameterCount() != 0) continue;
                method2 = method;
                break;
            }
            if (method2 == null) throw new RuntimeException("Could not find getServerConnection method");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        MinecraftServer = class1;
        ServerConnection = class2;
        NetworkManager = class3;
        MinecraftServer_getServer = method1;
        MinecraftServer_getServerConnection = method2;
    }

    public final ConnectionListenerConfig connectionListenerConfig;

    {
        ConnectionListenerConfig connectionListenerConfig1;
        File listenersConfigFile = new File("./plugins/ConnectionListener/config.yml");
        getLogger().info("Loading configuration file " + listenersConfigFile.getAbsolutePath());
        try {
            connectionListenerConfig1 = new ConnectionListenerConfig(getLogger(), YamlConfiguration.loadConfiguration(new FileReader(listenersConfigFile)));
        } catch (FileNotFoundException e) {
            connectionListenerConfig1 = new ConnectionListenerConfig(getLogger(), new YamlConfiguration());
        }
        connectionListenerConfig = connectionListenerConfig1;
        if (!connectionListenerConfig.listeners.isEmpty()) {
            boolean alreadyLoaded = false;
            if (Bukkit.getOnlinePlayers().size() > 1) alreadyLoaded = true;
            // ConnectionListener loads BEFORE ViaVersion, so ViaVersion cannot be enabled before us.
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) alreadyLoaded = true;
            // If the reload count is > 0, then it is absolutely a reload or the server is already loaded.
            if (ReflectionUtil.getReloadCount() > 0) alreadyLoaded = true;
            if (alreadyLoaded) {
                getLogger().severe("Server is already loaded! Disabling plugin.");
                getLogger().severe("Please reboot the server to fix this.");
            } else {
                try {
                    connectionListenerConfig.listeners.forEach(this::setupListeners);
                } catch (RuntimeException ex) {
                    getLogger().severe("Could not initialize listener");
                    ex.printStackTrace();
                    closeListeners();
                }
            }
        }
    }

    protected void closeListeners() {
        if (!this.channels.isEmpty()) {
            this.channels.forEach(channel -> {
                try {
                    getLogger().info("Closing listener " + channel);
                    channel.close();
                } catch (RuntimeException ex) {
                    getLogger().warning("Failed to close listener " + channel);
                    ex.printStackTrace();
                }
            });
        }
    }

    void setupListeners(ListenerInfo info) {
        if (info.getPort() == -1) {
            injectListener(info);
        } else {
            addListener(info);
        }
    }

    @SuppressWarnings("unchecked")
    void addListener(ListenerInfo info) {
        getLogger().info("Initializing HAProxy listener on port " + info.getPort());
        try {
            Object minecraftServer = MinecraftServer_getServer.invoke(null);
            Object serverConnection = MinecraftServer_getServerConnection.invoke(minecraftServer);
            Field field = ReflectionUtil.getListeningChannelField();
            field.setAccessible(true);
            List<ChannelFuture> futures = (List<ChannelFuture>) field.get(serverConnection);
            Class<? extends ServerSocketChannel> clazz;
            LazyInitVar<? extends EventLoopGroup> lazyInitVar;
            if (Epoll.isAvailable() && info.isEpoll()) {
                clazz = EpollServerSocketChannel.class;
                lazyInitVar = ReflectionUtil.getEpollEventLoopGroup();
                getLogger().info("Using epoll channel type for " + info.getPort() + ": " + Objects.requireNonNull(lazyInitVar).get());
            } else {
                clazz = NioServerSocketChannel.class;
                lazyInitVar = ReflectionUtil.getNioEventLoopGroup();
                getLogger().info("Using default channel type for " + info.getPort() + ": " + lazyInitVar.get());
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (futures) {
                final ChannelInitializer<SocketChannel> vanillaInitializer;
                if (info.isUseReflectionChannelInitializer()) {
                    vanillaInitializer = new NettyVanillaChannelInitializer(minecraftServer, serverConnection);
                } else {
                    Channel channel = futures.get(0).channel();
                    ChannelPipeline pipeline = channel.pipeline();
                    io.netty.channel.ChannelHandler handler = pipeline.first();
                    vanillaInitializer = (ChannelInitializer<SocketChannel>) ReflectionUtil.getFieldWithoutException(handler.getClass(), handler, "childHandler");
                }
                ChannelInitializer<SocketChannel> initializer = vanillaInitializer;
                if (info.isProxyProtocol()) initializer = new NettyHAProxyChannelInitializer(initializer);
                ChannelFuture future = new ServerBootstrap().channel(clazz)
                        .childHandler(initializer)
                        .group(lazyInitVar.get())
                        .localAddress(info.getServerIp(), info.getPort())
                        .option(ChannelOption.AUTO_READ, true)
                        .bind()
                        .syncUninterruptibly();
                futures.add(future);
                Channel channel = future.channel();
                this.channels.add(channel);
                getLogger().info("Initialized HAProxy listener " + channel + ". Please ensure this listener is properly firewalled.");
            }
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Failed to initialize HAProxy listener");
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    void injectListener(ListenerInfo info) {
        try {
            Object minecraftServer = MinecraftServer_getServer.invoke(null);
            Object serverConnection = MinecraftServer_getServerConnection.invoke(minecraftServer);
            Field field = ReflectionUtil.getListeningChannelField();
            field.setAccessible(true);
            List<ChannelFuture> futures = (List<ChannelFuture>) field.get(serverConnection);
            if (futures.isEmpty()) throw new AssertionError("ChannelFuture list is empty");
            final Channel channel;
            if (info.isUseReflectionChannelInitializer()) {
                Class<? extends ServerSocketChannel> clazz;
                LazyInitVar<? extends EventLoopGroup> lazyInitVar;
                if (Epoll.isAvailable() && info.isEpoll()) {
                    clazz = EpollServerSocketChannel.class;
                    lazyInitVar = ReflectionUtil.getEpollEventLoopGroup();
                    getLogger().info("Using epoll channel type for " + Bukkit.getPort() + ": " + Objects.requireNonNull(lazyInitVar).get());
                } else {
                    clazz = NioServerSocketChannel.class;
                    lazyInitVar = ReflectionUtil.getNioEventLoopGroup();
                    getLogger().info("Using default channel type for " + Bukkit.getPort() + ": " + lazyInitVar.get());
                }
                ChannelInitializer<SocketChannel> initializer = new NettyVanillaChannelInitializer(minecraftServer, serverConnection);
                if (info.isProxyProtocol()) initializer = new NettyHAProxyChannelInitializer(initializer);
                futures.get(0).channel().close(); // close old channel
                ChannelFuture future = new ServerBootstrap().channel(clazz)
                        .childHandler(initializer)
                        .group(lazyInitVar.get())
                        .localAddress(info.getServerIp(), Bukkit.getPort())
                        .option(ChannelOption.AUTO_READ, true)
                        .bind()
                        .syncUninterruptibly();
                futures.set(0, future);
                channel = future.channel();
            } else {
                ChannelFuture future = futures.get(0);
                channel = future.channel();
                ChannelPipeline pipeline = channel.pipeline();
                io.netty.channel.ChannelHandler handler = pipeline.first();
                ChannelInitializer<SocketChannel> vanillaInitializer = (ChannelInitializer<SocketChannel>) ReflectionUtil.getFieldWithoutException(handler.getClass(), handler, "childHandler");
                try {
                    ReflectionUtil.setField(handler.getClass(), handler, "childHandler", new NettyHAProxyChannelInitializer(vanillaInitializer));
                } catch (ReflectiveOperationException ex) {
                    getLogger().warning("Failed to initialize listener for " + channel + ", please set experimental.useReflectionChannelInitializer to true.");
                    ex.printStackTrace();
                    return;
                }
            }
            getLogger().info("Initialized HAProxy support for " + channel + ". Please ensure this port is properly firewalled.");
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Failed to initialize HAProxy support");
            ex.printStackTrace();
        }
    }
}
