package me.lovelace.loveHunt.model;

import java.util.UUID;

public record PendingReward(long id, UUID uuid, RewardItem reward) {
}
