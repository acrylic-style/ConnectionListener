package xyz.acrylicstyle.connectionListener;

import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;

public class ListenerInfo {
    private final InetAddress serverIp;
    private final int port;
    private final boolean proxyProtocol;
    private final boolean epoll;
    private boolean useReflectionChannelInitializer = false;

    public ListenerInfo(InetAddress serverIp, int port, boolean proxyProtocol, boolean epoll) {
        this.serverIp = serverIp;
        this.port = port;
        this.proxyProtocol = proxyProtocol;
        this.epoll = epoll;
    }

    public void useReflectionChannelInitializer() {
        useReflectionChannelInitializer = true;
    }

    @Nullable
    public InetAddress getServerIp() { return serverIp; }

    public int getPort() { return port; }

    public boolean isProxyProtocol() { return proxyProtocol; }

    public boolean isEpoll() { return epoll; }

    public boolean isUseReflectionChannelInitializer() { return useReflectionChannelInitializer; }
}
