package me.lovelace.loveHunt.model;

import java.util.UUID;

public record PlayerLock(UUID uuid, boolean createBlocked, boolean acceptBlocked) {
}
