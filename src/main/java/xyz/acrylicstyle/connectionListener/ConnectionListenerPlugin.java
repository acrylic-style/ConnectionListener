package xyz.acrylicstyle.connectionListener;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ConnectionListenerPlugin extends AbstractConnectionListener {
    private static ConnectionListenerPlugin plugin;

    @Deprecated
    public ConnectionListenerPlugin() { ConnectionListenerPlugin.plugin = this; }

    @Deprecated
    @Override
    public void onDisable() {
        closeListeners();
    }

    @SuppressWarnings("unused")
    @Contract(pure = true)
    @NotNull
    public static ConnectionListenerPlugin getPlugin() { return plugin; }
}
