package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntAPI;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.RewardItem;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LoveHuntAPIImpl implements LoveHuntAPI {

    private final BountyService bountyService;

    public LoveHuntAPIImpl(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    @Override
    public List<Bounty> getAllActiveBounties() {
        return bountyService.allActive();
    }

    @Override
    public List<Bounty> getBountiesByCreator(UUID creatorUuid) {
        return bountyService.byCreator(creatorUuid);
    }

    @Override
    public Bounty getActiveBountyOn(UUID targetUuid) {
        return bountyService.activeOn(targetUuid);
    }

    @Override
    public Bounty getBounty(long id) {
        return bountyService.get(id);
    }

    @Override
    public boolean hasAccepted(UUID hunterUuid, long bountyId) {
        return bountyService.hasAccepted(hunterUuid, bountyId);
    }

    @Override
    public Set<Long> getAcceptedBounties(UUID hunterUuid) {
        return bountyService.acceptedBy(hunterUuid);
    }

    @Override
    public CompletableFuture<Bounty> createPlayerBounty(Player creator, OfflinePlayer target, RewardItem reward) {
        CreateSession session = new CreateSession(target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName(), reward);
        return bountyService.createPlayerBounty(creator, session);
    }

    @Override
    public CompletableFuture<Bounty> createServerBounty(OfflinePlayer target, RewardItem reward) {
        return bountyService.createServerBounty(target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName(), reward);
    }

    @Override
    public CompletableFuture<Bounty> createDefaultServerBountyIfAbsent(OfflinePlayer target) {
        return bountyService.createDefaultServerBountyIfAbsent(target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName());
    }

    @Override
    public void cancelBounty(Bounty bounty) {
        bountyService.cancel(bounty);
    }

    @Override
    public void completeBounty(Bounty bounty, Player hunter) {
        bountyService.complete(bounty, hunter);
    }

    @Override
    public CompletableFuture<Boolean> acceptBounty(Player hunter, Bounty bounty) {
        return bountyService.accept(hunter, bounty);
    }

    @Override
    public Map<UUID, Double> getHunterRatings() {
        Map<UUID, Double> ratings = new HashMap<>();
        bountyService.ratings().snapshot().forEach((uuid, rating) -> ratings.put(uuid, rating.rating()));
        return ratings;
    }
}
