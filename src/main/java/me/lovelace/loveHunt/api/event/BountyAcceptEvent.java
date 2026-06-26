package me.lovelace.loveHunt.api.event;

import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BountyAcceptEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player hunter;
    private final Bounty bounty;
    private boolean cancelled;
    
    public BountyAcceptEvent(Player hunter, Bounty bounty, boolean async) {
        super(async);
        this.hunter = hunter;
        this.bounty = bounty;
    }
    
    public Player getHunter() { return hunter; }
    public Bounty getBounty() { return bounty; }
    
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}