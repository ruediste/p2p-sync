package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

class ActiveNodeSetTest {

    private final AtomicLong clock = new AtomicLong(100);

    private LongSupplier nextClock() {
        return clock::incrementAndGet;
    }

    private static ActiveNodeSet setWith(String versionNode, long versionClock, String... nodeIds) {
        var set = new ActiveNodeSet();
        set.setVersion(versionNode, versionClock);
        set.nodes.addAll(List.of(nodeIds));
        return set;
    }

    private static void startProcess(ActiveNodeSet set, ActiveNodeSet.Phase phase, String processId,
            String leavingNodeId, String clockSource, long lastOperationClock, String... confirmedBy) {
        var process = new ActiveNodeSet.LeavingProcess();
        process.phase = phase;
        process.processId = processId;
        process.leavingNodeId = leavingNodeId;
        process.lastOperationClock = lastOperationClock;
        process.lastOperationClockSourceNode = clockSource;
        process.confirmedBy.addAll(List.of(confirmedBy));
        set.leavingProcess = process;
    }

    /** Verifies that an identical version short-circuits before any content is merged. */
    @Test
    void equalVersionSkipsMerge() {
        var local = setWith("A", 5, "A", "B");
        var remote = setWith("A", 5, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "C", 3);

        assertFalse(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B"), local.nodes);
        assertNull(local.leavingProcess);
        assertEquals("A", local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /** Verifies that a strict superset is merged and its version is adopted. */
    @Test
    void strictSupersetAdoptsNodesAndVersion() {
        var local = setWith("A", 5, "A", "B");
        var remote = setWith("B", 9, "A", "B", "C");

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B", "C"), local.nodes);
        assertEquals("B", local.version.nodeId);
        assertEquals(9, local.version.clock);
    }

    /** Verifies that a partial set adds nodes and stamps a new local version. */
    @Test
    void partialSetAddsNodesAndBumpsLocalVersion() {
        var local = setWith("A", 5, "A", "X");
        var remote = setWith("B", 9, "A", "B");

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B", "X"), local.nodes);
        assertEquals("A", local.version.nodeId);
        assertEquals(101, local.version.clock);
    }

    /** Verifies that a set with fewer nodes cannot remove local nodes. */
    @Test
    void nonSupersetDoesNotRemoveNodes() {
        var local = setWith("A", 5, "A", "B", "C");
        var remote = setWith("B", 9, "A");

        assertFalse(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B", "C"), local.nodes);
        assertEquals("A", local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /** Verifies that the lower process id wins when two peers start leaving concurrently. */
    @Test
    void lowerProcessIdWinsForConcurrentProcesses() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "process-20", "C", "C", 3);
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "process-10", "C", "C", 3);

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals("process-10", local.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);
    }

    /** Verifies that a phase-B record advances an in-flight phase-A process. */
    @Test
    void higherPhaseWinsForSameProcess() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "C", 3);
        var remote = setWith("B", 9, "A", "B");
        startProcess(remote, ActiveNodeSet.Phase.B, "p", "C", "C", 3);

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(ActiveNodeSet.Phase.B, local.leavingProcess.phase);
        assertEquals(Set.of("A", "B"), local.nodes);
    }

    /** Verifies that phase B neither adds nodes nor adopts an unrelated remote version. */
    @Test
    void phaseBBlocksNodeAdditions() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.B, "p", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "D");
        startProcess(remote, ActiveNodeSet.Phase.B, "p", "C", "A", 3);

        assertFalse(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B"), local.nodes);
        assertEquals("A", local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /** Verifies that a completed leave is not regressed by a stale phase-A record. */
    @Test
    void completedProcessIsNotRegressedByStaleRecord() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.I, "p", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "C", 3);

        local.mergeFrom(remote, "A", nextClock());
        assertEquals(ActiveNodeSet.Phase.I, local.leavingProcess.phase);
        assertEquals(Set.of("A", "B"), local.nodes);
        assertEquals("A", local.version.nodeId);
        assertEquals(101, local.version.clock);
    }

    /** Verifies that a new leave process replaces the completed tombstone. */
    @Test
    void newProcessReplacesCompletedProcess() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.I, "old", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "D");
        startProcess(remote, ActiveNodeSet.Phase.A, "new", "D", "D", 3);

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals("new", local.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);
        assertEquals(Set.of("A", "B", "D"), local.nodes);
    }

    /** Verifies that the completed tombstone cannot cancel a newly started process. */
    @Test
    void completedProcessDoesNotCancelNewProcess() {
        var local = setWith("A", 5, "A", "B", "D");
        startProcess(local, ActiveNodeSet.Phase.A, "new", "D", "D", 3);
        var remote = setWith("B", 9, "A", "B");
        startProcess(remote, ActiveNodeSet.Phase.I, "old", "C", "A", 3);

        assertFalse(local.mergeFrom(remote, "A", nextClock()));
        assertEquals("new", local.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);

        assertTrue(remote.mergeFrom(local, "B", nextClock()));
        assertEquals("new", remote.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, remote.leavingProcess.phase);
    }

    /** Verifies that confirmations of the same process are united. */
    @Test
    void confirmationsAreUnitedForSameProcess() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "C", 7, "A");
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "C", 7, "B");

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(Set.of("A", "B"), local.leavingProcess.confirmedBy);
    }

    /** Verifies that a higher process clock replaces both clock source and confirmations. */
    @Test
    void higherProcessClockReplacesConfirmations() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "X", 3, "A");
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "Y", 8, "B");

        assertTrue(local.mergeFrom(remote, "A", nextClock()));
        assertEquals(8, local.leavingProcess.lastOperationClock);
        assertEquals("Y", local.leavingProcess.lastOperationClockSourceNode);
        assertEquals(Set.of("B"), local.leavingProcess.confirmedBy);
    }

    /** Verifies that startLeaving initializes phase A from the leaving node's clock. */
    @Test
    void startLeavingInitializesProcessAndBumpsVersion() {
        var set = setWith("A", 5, "A", "B", "C");

        assertTrue(set.startLeaving("C", "p", Map.of("C", 42L), "A", nextClock()));
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
        assertEquals("p", set.leavingProcess.processId);
        assertEquals("C", set.leavingProcess.leavingNodeId);
        assertEquals(42, set.leavingProcess.lastOperationClock);
        assertEquals("C", set.leavingProcess.lastOperationClockSourceNode);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals("A", set.version.nodeId);
        assertEquals(101, set.version.clock);
    }

    /** Verifies that an equal or higher process id cannot replace an in-flight process. */
    @Test
    void startLeavingRejectsEqualOrHigherProcessId() {
        var set = setWith("A", 5, "A", "B", "C");
        set.startLeaving("C", "b", Map.of(), "A", nextClock());
        long versionClock = set.version.clock;

        assertFalse(set.startLeaving("C", "b", Map.of(), "A", nextClock()));
        assertFalse(set.startLeaving("C", "c", Map.of(), "A", nextClock()));
        assertEquals("b", set.leavingProcess.processId);
        assertEquals(versionClock, set.version.clock);
    }

    /** Verifies that a lower process id replaces an in-flight process. */
    @Test
    void startLeavingAllowsLowerProcessId() {
        var set = setWith("A", 5, "A", "B", "C");
        set.startLeaving("C", "b", Map.of(), "A", nextClock());

        assertTrue(set.startLeaving("C", "a", Map.of(), "A", nextClock()));
        assertEquals("a", set.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
    }

    /** Verifies that a new leave process may start once the previous one reached idle. */
    @Test
    void startLeavingAllowsNewProcessAfterCompletion() {
        var set = setWith("A", 5, "A", "B");
        startProcess(set, ActiveNodeSet.Phase.I, "z", "C", "A", 3);

        assertTrue(set.startLeaving("B", "a", Map.of(), "A", nextClock()));
        assertEquals("a", set.leavingProcess.processId);
        assertEquals("B", set.leavingProcess.leavingNodeId);
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
    }

    /** Verifies that updateLeaving ignores missing, completed, and own leave processes. */
    @Test
    void updateLeavingIgnoresInactiveProcesses() {
        var noProcess = setWith("A", 5, "A", "B");
        assertFalse(noProcess.updateLeaving("A", Map.of(), nextClock()));

        var leavingNode = setWith("A", 5, "A", "B");
        startProcess(leavingNode, ActiveNodeSet.Phase.A, "p", "A", "A", 3);
        assertFalse(leavingNode.updateLeaving("A", Map.of("A", 3L), nextClock()));

        var completed = setWith("A", 5, "A", "B");
        startProcess(completed, ActiveNodeSet.Phase.I, "p", "C", "A", 3);
        assertFalse(completed.updateLeaving("A", Map.of("C", 3L), nextClock()));
        assertEquals(5, completed.version.clock);
    }

    /** Verifies that a confirmation is postponed while the local clock is behind. */
    @Test
    void updateLeavingPostponesWhileBehind() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 10);

        assertFalse(set.updateLeaving("A", Map.of("C", 4L), nextClock()));
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals(5, set.version.clock);
    }

    /** Verifies that a higher local clock resets the confirmations before confirming. */
    @Test
    void updateLeavingRaisesClockAndClearsConfirmations() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 3, "B");

        assertTrue(set.updateLeaving("A", Map.of("C", 9L), nextClock()));
        assertEquals(9, set.leavingProcess.lastOperationClock);
        assertEquals(Set.of("A"), set.leavingProcess.confirmedBy);
    }

    /** Verifies that a vanished clock source falls back to the local node. */
    @Test
    void updateLeavingFallsBackWhenClockSourceIsGone() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "D", 10);

        assertTrue(set.updateLeaving("A", Map.of("C", 6L), nextClock()));
        assertEquals(6, set.leavingProcess.lastOperationClock);
        assertEquals("A", set.leavingProcess.lastOperationClockSourceNode);
        assertEquals(Set.of("A"), set.leavingProcess.confirmedBy);
        assertEquals(101, set.version.clock);
    }

    /** Verifies that phase A completes when all remaining nodes confirmed the leave. */
    @Test
    void updateLeavingAdvancesAToBWhenAllPeersConfirm() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 5);

        assertTrue(set.updateLeaving("A", Map.of("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
        assertTrue(set.nodes.contains("C"));

        assertTrue(set.updateLeaving("B", Map.of("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.B, set.leavingProcess.phase);
        assertEquals(Set.of("A", "B"), set.nodes);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals(102, set.version.clock);
    }

    /** Verifies that phase B reaches idle when all remaining nodes confirmed the removal. */
    @Test
    void updateLeavingAdvancesBToIWhenAllPeersConfirm() {
        var set = setWith("A", 5, "A", "B");
        startProcess(set, ActiveNodeSet.Phase.B, "p", "C", "C", 5);

        assertTrue(set.updateLeaving("A", Map.of("C", 5L), nextClock()));
        assertEquals("A", set.leavingProcess.lastOperationClockSourceNode);
        assertEquals(Set.of("A"), set.leavingProcess.confirmedBy);

        assertTrue(set.updateLeaving("B", Map.of("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.I, set.leavingProcess.phase);
        assertEquals(Set.of("A", "B"), set.nodes);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertFalse(set.updateLeaving("A", Map.of("C", 5L), nextClock()));
    }

    /** Verifies that a copy owns its nodes, lastSeen entries, and leaving process. */
    @Test
    void copyIsIndependent() {
        var original = setWith("A", 5, "A", "B");
        original.lastSeen.put("A", 1L);
        startProcess(original, ActiveNodeSet.Phase.A, "p", "B", "B", 5, "A");

        var copy = original.copy();
        copy.nodes.add("C");
        copy.leavingProcess.confirmedBy.add("B");
        copy.lastSeen.put("B", 2L);

        assertEquals(Set.of("A", "B"), original.nodes);
        assertEquals(Set.of("A"), original.leavingProcess.confirmedBy);
        assertFalse(original.lastSeen.containsKey("B"));
    }

    /** Verifies that the persisted version, nodes, lastSeen, and leave process round-trip. */
    @Test
    void persistedStateRoundTrips() {
        var original = setWith("A", 5, "A", "B", "C");
        original.lastSeen.put("A", 11L);
        startProcess(original, ActiveNodeSet.Phase.B, "p", "C", "A", 9, "A", "B");

        var loaded = new ActiveNodeSet();
        Node.loadActiveNodeSet(Node.persist(original), loaded);

        assertEquals("A", loaded.version.nodeId);
        assertEquals(5, loaded.version.clock);
        assertEquals(Set.of("A", "B", "C"), loaded.nodes);
        assertEquals(11L, loaded.lastSeen.get("A"));
        assertEquals(ActiveNodeSet.Phase.B, loaded.leavingProcess.phase);
        assertEquals("p", loaded.leavingProcess.processId);
        assertEquals("C", loaded.leavingProcess.leavingNodeId);
        assertEquals(9, loaded.leavingProcess.lastOperationClock);
        assertEquals("A", loaded.leavingProcess.lastOperationClockSourceNode);
        assertEquals(Set.of("A", "B"), loaded.leavingProcess.confirmedBy);
    }
}
