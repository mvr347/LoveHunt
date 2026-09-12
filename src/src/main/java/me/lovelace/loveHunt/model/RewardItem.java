package me.lovelace.loveHunt.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record RewardItem(Material material, int amount, ItemStack prototype, String displayName) {

    public RewardItem withAmount(int newAmount) {
        ItemStack copy = prototype == null ? new ItemStack(material, 1) : prototype.clone();
        copy.setAmount(Math.max(1, Math.min(copy.getMaxStackSize(), newAmount)));
        return new RewardItem(material, newAmount, copy, displayName);
    }

    public ItemStack stack() {
        ItemStack stack = prototype == null ? new ItemStack(material) : prototype.clone();
        stack.setAmount(amount);
        return stack;
    }

    public ItemStack singlePrototype() {
        ItemStack stack = prototype == null ? new ItemStack(material) : prototype.clone();
        stack.setAmount(1);
        return stack;
    }
}
