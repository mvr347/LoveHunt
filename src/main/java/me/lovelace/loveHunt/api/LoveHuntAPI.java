package me.lovelace.loveHunt.api;

import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.RewardItem;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main API for LoveHunt.
 */
public interface LoveHuntAPI {

    /**
     * Retrieves a list of all active bounties.
     * @return an unmodifiable list of active bounties.
     */
    List<Bounty> getAllActiveBounties();

    /**
     * Retrieves all active bounties created by the specified player.
     * @param creatorUuid UUID of the creator.
     * @return an unmodifiable list of bounties.
     */
    List<Bounty> getBountiesByCreator(UUID creatorUuid);

    /**
     * Retrieves the active bounty placed on the specified target.
     * @param targetUuid UUID of the target player.
     * @return the active bounty, or null if none exists.
     */
    Bounty getActiveBountyOn(UUID targetUuid);

    /**
     * Retrieves a bounty by its unique ID.
     * @param id The ID of the bounty.
     * @return the bounty, or null if not found.
     */
    Bounty getBounty(long id);

    /**
     * Checks if a player has accepted a specific bounty.
     * @param hunterUuid UUID of the hunting player.
     * @param bountyId The ID of the bounty.
     * @return true if accepted, false otherwise.
     */
    boolean hasAccepted(UUID hunterUuid, long bountyId);

    /**
     * Retrieves all bounty IDs accepted by a specific player.
     * @param hunterUuid UUID of the hunting player.
     * @return a set of accepted bounty IDs.
     */
    Set<Long> getAcceptedBounties(UUID hunterUuid);

    /**
     * Creates a new player bounty (Requires the creator to have the items in their inventory).
     * @param creator The player creating the bounty.
     * @param target The target player.
     * @param reward The reward item and amount.
     * @return a future completing with the newly created bounty.
     */
    CompletableFuture<Bounty> createPlayerBounty(Player creator, OfflinePlayer target, RewardItem reward);

    /**
     * Creates a new server bounty (No items taken, completely handled by server).
     * @param target The target player.
     * @param reward The reward item and amount.
     * @return a future completing with the newly created bounty.
     */
    CompletableFuture<Bounty> createServerBounty(OfflinePlayer target, RewardItem reward);

    /**
     * Cancels a bounty.
     * @param bounty The bounty to cancel.
     */
    void cancelBounty(Bounty bounty);

    /**
     * Forces the completion of a bounty by a specific player.
     * @param bounty The bounty to complete.
     * @param hunter The player who completed it.
     */
    void completeBounty(Bounty bounty, Player hunter);

    /**
     * Forces a player to accept a bounty.
     * @param hunter The player accepting the bounty.
     * @param bounty The bounty to accept.
     * @return a future completing with true if successfully accepted.
     */
    CompletableFuture<Boolean> acceptBounty(Player hunter, Bounty bounty);
}
