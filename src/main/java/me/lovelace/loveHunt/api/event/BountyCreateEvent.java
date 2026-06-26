package me.lovelace.loveHunt.api.event;

import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BountyCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Bounty bounty;
    
    public BountyCreateEvent(Bounty bounty, boolean async) {
        super(async);
        this.bounty = bounty;
    }
    
    public Bounty getBounty() { return bounty; }
    
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}