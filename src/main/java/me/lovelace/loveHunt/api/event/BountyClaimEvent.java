package me.lovelace.loveHunt.api.event;

import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BountyClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Bounty bounty;
    private final Player killer;
    private boolean cancelled;
    
    public BountyClaimEvent(Bounty bounty, Player killer) {
        this.bounty = bounty;
        this.killer = killer;
    }
    
    public Bounty getBounty() { return bounty; }
    public Player getKiller() { return killer; }
    
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}