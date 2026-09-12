package me.lovelace.loveHunt.util;

import me.lovelace.loveHunt.model.RewardItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static String readableName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "AIR";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return readableName(stack.getType());
    }

    public static String readableName(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    public static boolean hasSimilar(Player player, RewardItem reward) {
        return countSimilar(player.getInventory(), reward.singlePrototype()) >= reward.amount();
    }

    public static boolean removeSimilar(Player player, RewardItem reward) {
        ItemStack prototype = reward.singlePrototype();
        int left = reward.amount();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || !current.isSimilar(prototype)) {
                continue;
            }
            int take = Math.min(left, current.getAmount());
            current.setAmount(current.getAmount() - take);
            if (current.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, current);
            }
            left -= take;
            if (left <= 0) {
                return true;
            }
        }
        return false;
    }

    private static int countSimilar(PlayerInventory inventory, ItemStack prototype) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.isSimilar(prototype)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(output)) {
            ItemStack copy = stack.clone();
            copy.setAmount(1);
            objectOutput.writeObject(copy);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize item", exception);
        }
    }

    public static ItemStack deserialize(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return new ItemStack(fallback, 1);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(value));
             BukkitObjectInputStream objectInput = new BukkitObjectInputStream(input)) {
            Object object = objectInput.readObject();
            if (object instanceof ItemStack stack) {
                stack.setAmount(1);
                return stack;
            }
            return new ItemStack(fallback, 1);
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            return new ItemStack(fallback, 1);
        }
    }
}
