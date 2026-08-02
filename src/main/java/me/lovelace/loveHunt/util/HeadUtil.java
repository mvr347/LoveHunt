package me.lovelace.loveHunt.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeadUtil {
    private static final Pattern URL_PATTERN = Pattern.compile("http://textures\\.minecraft\\.net/texture/[a-f0-9]+");

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

    public static ItemStack base64Head(String input) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (input == null || input.isBlank()) {
            return stack;
        }
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        try {
            String urlString = null;
            if (input.startsWith("http://") || input.startsWith("https://")) {
                urlString = input;
            } else {
                String decoded = new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
                Matcher matcher = URL_PATTERN.matcher(decoded);
                if (matcher.find()) {
                    urlString = matcher.group();
                }
            }

            if (urlString != null) {
                URL textureUrl = URI.create(urlString).toURL();
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(textureUrl);
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                stack.setItemMeta(meta);
                return stack;
            }
        } catch (Exception ignored) {
        }
        return stack;
    }
}
