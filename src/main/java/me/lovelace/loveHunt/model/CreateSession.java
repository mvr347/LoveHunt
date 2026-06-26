package me.lovelace.loveHunt.model;

import java.util.UUID;

public final class CreateSession {
    private final UUID targetUuid;
    private final String targetName;
    private final RewardItem reward;

    public CreateSession(UUID targetUuid, String targetName, RewardItem reward) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.reward = reward;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public RewardItem reward() {
        return reward;
    }
}
