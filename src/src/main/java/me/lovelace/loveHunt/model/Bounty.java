package me.lovelace.loveHunt.model;

import java.util.Objects;
import java.util.UUID;

public final class Bounty {
    private final long id;
    private final BountyType type;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID creatorUuid;
    private final String creatorName;
    private final String clanTag;
    private final RewardItem reward;
    private final long createdAt;
    private final Long expiresAt;
    private final BountyStatus status;
    private final int extendedDays;

    public Bounty(
            long id,
            BountyType type,
            UUID targetUuid,
            String targetName,
            UUID creatorUuid,
            String creatorName,
            String clanTag,
            RewardItem reward,
            long createdAt,
            Long expiresAt,
            BountyStatus status
    ) {
        this(id, type, targetUuid, targetName, creatorUuid, creatorName, clanTag, reward, createdAt, expiresAt, status, 0);
    }

    public Bounty(
            long id,
            BountyType type,
            UUID targetUuid,
            String targetName,
            UUID creatorUuid,
            String creatorName,
            String clanTag,
            RewardItem reward,
            long createdAt,
            Long expiresAt,
            BountyStatus status,
            int extendedDays
    ) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.clanTag = clanTag;
        this.reward = Objects.requireNonNull(reward, "reward");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = Objects.requireNonNull(status, "status");
        this.extendedDays = extendedDays;
    }

    public long id() {
        return id;
    }

    public BountyType type() {
        return type;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public UUID creatorUuid() {
        return creatorUuid;
    }

    public String creatorName() {
        return creatorName;
    }

    public String clanTag() {
        return clanTag;
    }

    public RewardItem reward() {
        return reward;
    }

    public long createdAt() {
        return createdAt;
    }

    public Long expiresAt() {
        return expiresAt;
    }

    public BountyStatus status() {
        return status;
    }

    public int extendedDays() {
        return extendedDays;
    }

    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt <= now;
    }

    public Bounty withStatus(BountyStatus newStatus) {
        return new Bounty(id, type, targetUuid, targetName, creatorUuid, creatorName, clanTag, reward, createdAt, expiresAt, newStatus, extendedDays);
    }

    public Bounty withExtension(RewardItem newReward, long newExpiresAt, int newExtendedDays) {
        return new Bounty(id, type, targetUuid, targetName, creatorUuid, creatorName, clanTag, newReward, createdAt, newExpiresAt, status, newExtendedDays);
    }
}
