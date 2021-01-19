package xyz.acrylicstyle.connectionListener.util;

import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.acrylicstyle.connectionListener.ConnectionListenerPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtil {
    /**
     * Get server version for reflection.
     * @return Server version like v1_15_R1
     */
    public static String getServerVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
    }

    /**
     * Get org.bukkit.craftbukkit package.
     * @return org.bukkit.craftbukkit package
     */
    @SuppressWarnings("unused")
    public static String getCraftBukkitPackage() { return "org.bukkit.craftbukkit." + getServerVersion(); }

    /**
     * Get net.minecraft.server package.
     * @return net.minecraft.server package
     */
    public static String getNMSPackage() { return "net.minecraft.server." + getServerVersion(); }

    public static String nms(String clazz) { return getNMSPackage() + "." + clazz; }

    public static Field getListeningChannelField() throws ReflectiveOperationException {
        String s = getServerVersion();
        switch (s) {
            case "v1_8_R2":
            case "v1_8_R3":
            case "v1_9_R1":
            case "v1_9_R2":
            case "v1_10_R1":
            case "v1_11_R1":
            case "v1_11_R2":
            case "v1_12_R1":
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("g");
            case "v1_8_R1":
            case "v1_13_R1":
            case "v1_13_R2":
            case "v1_14_R1":
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("f");
            case "v1_15_R1":
                try {
                    ConnectionListenerPlugin.ServerConnection.getDeclaredField("connectedChannels");
                    return ConnectionListenerPlugin.ServerConnection.getDeclaredField("listeningChannels");
                } catch (NoSuchFieldException ex) {
                    return ConnectionListenerPlugin.ServerConnection.getDeclaredField("f");
                }
            default:
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("listeningChannels");
        }
    }

    public static Field getConnectedChannelField() throws ReflectiveOperationException {
        String s = getServerVersion();
        switch (s) {
            case "v1_8_R2":
            case "v1_8_R3":
            case "v1_9_R1":
            case "v1_9_R2":
            case "v1_10_R1":
            case "v1_11_R1":
            case "v1_11_R2":
            case "v1_12_R1":
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("h");
            case "v1_8_R1":
            case "v1_13_R1":
            case "v1_13_R2":
            case "v1_14_R1":
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("g");
            default:
                return ConnectionListenerPlugin.ServerConnection.getDeclaredField("connectedChannels");
        }
    }

    /**
     * Find field in class.
     * @param clazz Class that will find field on
     * @param fieldName Field name
     * @return Field if found, null otherwise
     */
    @Nullable
    public static <T> Field findField(@NotNull Class<? extends T> clazz, @NotNull String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Set value in field in class.
     * @param clazz Class that will get field on
     * @param instance Can be empty if field is static
     * @param fieldName Field name
     * @param value Value
     * @throws NoSuchFieldException If couldn't find field
     * @throws IllegalAccessException If the operation isn't allowed
     */
    public static <T> void setField(@NotNull Class<? extends T> clazz, @Nullable T instance, @NotNull String fieldName, @Nullable Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = findField(clazz, fieldName);
        if (field == null) throw new NoSuchFieldException();
        field.setAccessible(true);
        // UNSAFE
        if (Modifier.isFinal(field.getModifiers())) {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.set(field, field.getModifiers() & ~Modifier.FINAL);
        }
        field.set(instance, value);
    }

    /**
     * Get value in field in class.
     * @param clazz Class that will get field on
     * @param instance Can be empty if field is static
     * @param fieldName Field name
     * @return Value of field
     * @throws NoSuchFieldException If couldn't find field
     * @throws IllegalAccessException If the operation isn't allowed
     */
    @NotNull
    public static <T> Object getField(@NotNull Class<? extends T> clazz, @Nullable T instance, @NotNull String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = findField(clazz, fieldName);
        if (field == null) throw new NoSuchFieldException();
        field.setAccessible(true);
        return field.get(instance);
    }

    /**
     * Get value in field in class.
     * @param clazz Class that will get field on
     * @param instance Can be empty if field is static
     * @param fieldName Field name
     * @return Value of field if success, null otherwise
     */
    @Nullable
    public static <T> Object getFieldWithoutException(@NotNull Class<? extends T> clazz, @Nullable T instance, @NotNull String fieldName) {
        try {
            return getField(clazz, instance, fieldName);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    public static LazyInitVar<NioEventLoopGroup> getNioEventLoopGroup() {
        try {
            return new LazyInitVar<>(ConnectionListenerPlugin.ServerConnection.getField("a").get(null));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    // does not exist in 1.8, but exist in 1.8.3+
    // 1.8 will use Netty IO, since epoll is not supported at that point (returns LocalEventLoopGroup by getting "b" field in 1.8)
    @Nullable
    public static LazyInitVar<EpollEventLoopGroup> getEpollEventLoopGroup() {
        try {
            if (!getServerVersion().equals("v1_8_R1")) {
                return new LazyInitVar<>(ConnectionListenerPlugin.ServerConnection.getField("b").get(null));
            } else {
                return null;
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> @NotNull T newInstance(String clazz, Object... args) throws ReflectiveOperationException {
        List<Class<?>> classes = new ArrayList<>();
        for (Object arg : args) classes.add(arg.getClass());
        Constructor<?> constructor = Class.forName(nms(clazz)).getDeclaredConstructor(classes.toArray(new Class[0]));
        return (T) constructor.newInstance(args);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Enum<?> getProtocolDirection(String name) throws ClassNotFoundException {
        return Enum.valueOf((Class<Enum>) Class.forName(nms("EnumProtocolDirection")), name);
    }

    public static int getReloadCount() {
        try {
            return (int) Bukkit.getServer().getClass().getField("reloadCount").get(Bukkit.getServer());
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }
}
