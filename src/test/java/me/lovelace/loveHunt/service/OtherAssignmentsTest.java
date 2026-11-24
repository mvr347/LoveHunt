package me.lovelace.loveHunt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OtherAssignmentsTest {

    /** Without a server or without LoveContracts the answer must be "no": a missing plugin never blocks a bounty. */
    @Test
    void missingServerOrPluginNeverBlocks() {
        assertFalse(OtherAssignments.hasContractsQuest(null));
    }
}
