package me.lovelace.loveHunt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwitchCyclingTest {

    @Test
    void sortModeCyclesBothWays() {
        assertEquals(SortMode.REWARD, SortMode.NAME.next());
        assertEquals(SortMode.NAME, SortMode.POPULAR.next());
        assertEquals(SortMode.POPULAR, SortMode.NAME.previous());
        for (SortMode mode : SortMode.values()) assertEquals(mode, mode.next().previous());
    }

    @Test
    void typeFilterCyclesBothWays() {
        assertEquals(TypeFilter.PLAYER, TypeFilter.ALL.next());
        assertEquals(TypeFilter.ALL, TypeFilter.SERVER.next());
        assertEquals(TypeFilter.SERVER, TypeFilter.ALL.previous());
        for (TypeFilter filter : TypeFilter.values()) assertEquals(filter, filter.previous().next());
    }

    @Test
    void clanOnlineCyclesAllOptionsWithClan() {
        assertEquals(ClanOnlineMode.CLAN, ClanOnlineMode.OFF.next(true));
        assertEquals(ClanOnlineMode.ONLINE, ClanOnlineMode.CLAN.next(true));
        assertEquals(ClanOnlineMode.BOTH, ClanOnlineMode.ONLINE.next(true));
        assertEquals(ClanOnlineMode.OFF, ClanOnlineMode.BOTH.next(true));
        assertEquals(ClanOnlineMode.BOTH, ClanOnlineMode.OFF.previous(true));
        assertEquals(ClanOnlineMode.CLAN, ClanOnlineMode.ONLINE.previous(true));
    }

    @Test
    void clanOnlineSkipsClanOptionsWithoutClan() {
        assertEquals(ClanOnlineMode.ONLINE, ClanOnlineMode.OFF.next(false));
        assertEquals(ClanOnlineMode.OFF, ClanOnlineMode.ONLINE.next(false));
        assertEquals(ClanOnlineMode.ONLINE, ClanOnlineMode.OFF.previous(false));
        assertEquals(ClanOnlineMode.OFF, ClanOnlineMode.ONLINE.previous(false));
        // A stale clan state (player left the clan) moves to a valid option.
        assertEquals(ClanOnlineMode.ONLINE, ClanOnlineMode.CLAN.next(false));
        assertEquals(ClanOnlineMode.OFF, ClanOnlineMode.BOTH.next(false));
    }

    @Test
    void ofMapsFlags() {
        for (ClanOnlineMode mode : ClanOnlineMode.values()) {
            assertEquals(mode, ClanOnlineMode.of(mode.onlyMyClan(), mode.onlineOnly()));
        }
    }
}
