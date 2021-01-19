package xyz.acrylicstyle.connectionListener;

import io.netty.channel.epoll.Epoll;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.acrylicstyle.connectionListener.util.ReflectionUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // private methods are invoked by reflection
public class ConnectionListenerConfig {
    private final List<Method> methods = new ArrayList<>();
    private final Logger logger;
    public final List<ListenerInfo> listeners = new ArrayList<>();
    private Map<String, Object> map = null;

    @SuppressWarnings("unchecked")
    public ConnectionListenerConfig(Logger logger, FileConfiguration config) {
        this.logger = logger;
        @SuppressWarnings("StringBufferReplaceableByString")
        StringBuilder sb = new StringBuilder();
        sb.append("Welcome to ConnectionListener configuration file!\n");
        sb.append("There are some things to configure, and I'll explain what are these values and that does that do.\n");
        sb.append("\n");
        sb.append("These settings will affect whether if ConnectionListener will try to create an extra listener.\n");
        sb.append("If proxy_protocol is disabled, then ConnectionListener will do completely nothing about HAProxy.\n");
        sb.append("Also, enabling proxy_protocol might causes ViaVersion to stop working after /reload.\n");
        sb.append("Please restart the server if you see the error from ViaVersion in console.\n");
        sb.append("\n");
        sb.append("Important notes:\n");
        sb.append(" - You cannot load plugin (by PlugMan or something) AFTER the server is fully started.\n");
        sb.append(" - Breaks ViaVersion when you do /reload\n");
        sb.append("\n");
        sb.append("===== Connection Listeners ===== ('listeners' list)\n");
        sb.append("\n");
        sb.append("proxy_protocol:\n");
        sb.append("    Sets whether enables PROXY protocol for usage from HAProxy etc.\n");
        sb.append("    **HAProxy features will be disabled if it is set to false.**");
        sb.append("    (Default: false)\n");
        sb.append("server_ip:\n");
        sb.append("    Set specific server IP / Hostname when you have multiple NICs.\n");
        sb.append("    (Requires proxy_protocol to work.)\n");
        sb.append("    (Default: 'null')\n");
        sb.append("port:\n");
        sb.append("    Sets port to listen HAProxy.\n");
        sb.append("    Specify -1 to replace main server port with HAProxy-enabled listener.\n");
        sb.append("    If you specify the port other than -1, then the HAProxy-enabled packet\n");
        sb.append("    listener will be created.\n");
        sb.append("    (Requires proxy_protocol to work.)\n");
        sb.append("    (Default: -1)\n");
        sb.append("epoll:\n");
        sb.append("    Whether to enable epoll on linux servers.\n");
        sb.append("    Epoll enables native enhancements to listener, and it is recommend to enable it.\n");
        sb.append("    Please note that epoll is not available on Windows etc, so epoll will not be used\n");
        sb.append("    and the Netty IO will be used.\n");
        sb.append("    (Requires proxy_protocol to work.)\n");
        sb.append("    (Default: true)\n");
        sb.append("\n");
        sb.append("===== EXPERIMENTAL =====\n");
        sb.append("These settings are experimental and might not work, or may behave weird!\n");
        sb.append("Use at your own risk.\n");
        sb.append("\n");
        sb.append("experimental.useReflectionChannelInitializer:\n");
        sb.append("    Whether to use reflection-based channel initializer when creating HAProxy-enabled\n");
        sb.append("    packet listener.\n");
        sb.append("    This setting has no effect when replacing main server port with HAProxy-enabled listener.\n");
        sb.append("    (Requires proxy_protocol to work.)\n");
        sb.append("    (Default: false)\n");
        config.options().header(sb.toString());
        AtomicInteger index = new AtomicInteger();
        List<Map<String, Object>> maps = new ArrayList<>();
        config.getList("listeners", new ArrayList<>()).forEach(o -> {
            if (o instanceof ConfigurationSection) {
                map = ((ConfigurationSection) o).getValues(true);
            } else if (o instanceof Map) {
                map = (Map<String, Object>) o;
            } else {
                map = new HashMap<>();
            }
            logger.info("---------- Listener #" + index.getAndIncrement() + " Settings ----------");
            try {
                boolean doAdd = methods.isEmpty();
                if (doAdd) {
                    for (Method method : Arrays.stream(ConnectionListenerConfig.class.getDeclaredMethods()).sorted(Comparator.comparing(Method::getName)).collect(Collectors.toList())) {
                        if (method.getReturnType() != void.class) continue;
                        if (method.getParameterCount() != 0) continue;
                        if (method.isSynthetic()) continue;
                        if (Modifier.isStatic(method.getModifiers())) continue;
                        methods.add(method);
                        method.invoke(this);
                    }
                } else {
                    for (Method method : methods) {
                        method.invoke(this);
                    }
                }
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException("Failed to initialize ConnectionListenerConfig", ex);
            }
            maps.add(map);
            ListenerInfo info = new ListenerInfo(serverIp, port, proxyProtocol, epoll);
            if (experimental_useReflectionChannelInitializer) info.useReflectionChannelInitializer();
            listeners.add(info);
        });
        config.set("listeners", maps);
        try {
            config.save("./plugins/ConnectionListener/config.yml");
        } catch (IOException e) {
            logger.warning("Failed to save configuration");
            e.printStackTrace();
        }
    }

    public boolean getBoolean(String key, boolean def) { return getBoolean(this.map, key, def); }

    public boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object value = map.get(key);
        if (value == null) return def;
        try {
            return (Boolean) value;
        } catch (ClassCastException ex) {
            try {
                return (boolean) value;
            } catch (ClassCastException ex1) {
                return def;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        } else if (value instanceof ConfigurationSection) {
            return ((ConfigurationSection) value).getValues(true);
        } else {
            return new HashMap<>();
        }
    }

    public int getInt(String key, int def) {
        Object value = map.get(key);
        if (value == null) return def;
        try {
            return (Integer) value;
        } catch (ClassCastException ex) {
            try {
                return (int) value;
            } catch (ClassCastException ex1) {
                return def;
            }
        }
    }

    public String getString(String key, String def) {
        Object value = map.get(key);
        if (value instanceof String) return (String) value;
        return def;
    }

    public void set(String key, Object value) {
        map.put(key, value);
    }

    private void setDefault(String key, Object value) {
        map.putIfAbsent(key, value);
    }

    private boolean proxyProtocol = false;

    private void proxyProtocol() {
        proxyProtocol = getBoolean("proxy_protocol", false);
        set("proxy_protocol", proxyProtocol);
        logger.info("HAProxy support: " + (proxyProtocol ? "Enabled" : "Disabled"));
    }

    private InetAddress serverIp = null;

    private void serverIp() {
        String ip = getString("server_ip", "null");
        if (ip != null && !ip.equals("null")) {
            try {
                serverIp = InetAddress.getByName(ip);
            } catch (UnknownHostException e) {
                logger.warning("Server IP or Hostname is invalid, using default one");
                ip = "null"; // it will be saved
            }
        }
        set("server_ip", ip);
        if (serverIp != null) logger.info("Listening HAProxy server on " + serverIp);
    }

    private int port;

    private void port() {
        port = getInt("port", -1);
        if (port != -1 && (port < 0 || port > 65535)) {
            logger.warning("Port is out of range, using default port");
            port = -1; // it will be saved
        }
        set("port", port);
        if (port == Bukkit.getPort()) {
            port = -1; // it will not be saved
        }
        logger.info("Port: " + (port == -1 ? "Default (" + Bukkit.getPort() + ")" : port));
    }

    private boolean epoll;

    private void epoll() {
        epoll = getBoolean("useEpoll", true);
        set("epoll", epoll);
        if (ReflectionUtil.getServerVersion().equals("v1_8_R1") || !Epoll.isAvailable()) {
            String message = "(Not supported: MC 1.8)";
            if (!Epoll.isAvailable()) {
                String exMessage = Epoll.unavailabilityCause().getMessage();
                if (exMessage == null) exMessage = Epoll.unavailabilityCause().getCause().getMessage();
                message = "(Not supported: " + exMessage + ")";
            }
            logger.info("Using default channel type " + message);
            epoll = false; // it will not be saved
        } else {
            logger.info("Using " + (epoll ? "epoll" : "default") + " channel type");
        }
    }

    private boolean experimental_useReflectionChannelInitializer;

    private void experimental_useReflectionChannelInitializer() {
        Map<String, Object> map = getMap("experimental");
        experimental_useReflectionChannelInitializer = getBoolean(map, "useReflectionChannelInitializer", false);
        map.put("useReflectionChannelInitializer", experimental_useReflectionChannelInitializer);
        set("experimental", map);
        if (experimental_useReflectionChannelInitializer) {
            logger.info("Using experimental (reflection based) NettyVanillaChannelInitializer channel initializer");
        } else {
            logger.info("Using vanilla channel initializer");
        }
    }
}
