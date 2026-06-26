package me.lovelace.loveHunt.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class HeadUtil {
    private HeadUtil() {
    }

    public static ItemStack playerHead(OfflinePlayer player) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null && player != null) {
            meta.setOwningPlayer(player);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack base64Head(String base64) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isBlank()) {
            return stack;
        }
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> profileConstructor = profileClass.getConstructor(UUID.class, String.class);
            Object profile = profileConstructor.newInstance(UUID.randomUUID(), "LoveHunt");
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            stack.setItemMeta(meta);
        } catch (ReflectiveOperationException ignored) {
            Bukkit.getLogger().fine("Unable to apply base64 head texture through reflection.");
        }
        return stack;
    }
}
