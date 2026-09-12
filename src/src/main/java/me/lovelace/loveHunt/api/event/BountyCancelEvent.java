package me.lovelace.loveHunt.api.event;

import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BountyCancelEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Bounty bounty;
    private boolean cancelled;
    
    public BountyCancelEvent(Bounty bounty) {
        this.bounty = bounty;
    }
    
    public Bounty getBounty() { return bounty; }
    
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}