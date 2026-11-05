package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.identity.NodeId;

class ActiveNodeSetTest {

    private final AtomicLong clock = new AtomicLong(100);

    private LongSupplier nextClock() {
        return clock::incrementAndGet;
    }

    private static ActiveNodeSet setWith(String versionNode, long versionClock, String... nodeIds) {
        var set = new ActiveNodeSet();
        set.setVersion(id(versionNode), versionClock);
        for (String nodeId : nodeIds)
            set.nodes.add(id(nodeId));
        return set;
    }

    private static void startProcess(ActiveNodeSet set, ActiveNodeSet.Phase phase, String processId,
            String leavingNodeId, String clockSource, long lastOperationClock, String... confirmedBy) {
        var process = new ActiveNodeSet.LeavingProcess();
        process.phase = phase;
        process.processId = processId;
        process.leavingNodeId = id(leavingNodeId);
        process.lastOperationClock = lastOperationClock;
        process.lastOperationClockSourceNode = id(clockSource);
        for (String nodeId : confirmedBy)
            process.confirmedBy.add(id(nodeId));
        set.leavingProcess = process;
    }

    private static NodeId id(String value) {
        byte[] bytes = new byte[NodeId.LENGTH];
        bytes[bytes.length - 1] = (byte) value.charAt(0);
        return NodeId.fromBytes(bytes);
    }

    private static Set<NodeId> ids(String... values) {
        return java.util.Arrays.stream(values).map(ActiveNodeSetTest::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Map<String, Long> clock(String nodeId, long value) {
        return Map.of(id(nodeId).hex(), value);
    }

    /**
     * Verifies that an identical version short-circuits before any content is
     * merged.
     */
    @Test
    void equalVersionSkipsMerge() {
        var local = setWith("A", 5, "A", "B");
        var remote = setWith("A", 5, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "C", 3);

        assertFalse(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B"), local.nodes);
        assertNull(local.leavingProcess);
        assertEquals(id("A"), local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /** Verifies that a strict superset is merged and its version is adopted. */
    @Test
    void strictSupersetAdoptsNodesAndVersion() {
        var local = setWith("A", 5, "A", "B");
        var remote = setWith("B", 9, "A", "B", "C");

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B", "C"), local.nodes);
        assertEquals(id("B"), local.version.nodeId);
        assertEquals(9, local.version.clock);
    }

    /** Verifies that a partial set adds nodes and stamps a new local version. */
    @Test
    void partialSetAddsNodesAndBumpsLocalVersion() {
        var local = setWith("A", 5, "A", "X");
        var remote = setWith("B", 9, "A", "B");

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B", "X"), local.nodes);
        assertEquals(id("A"), local.version.nodeId);
        assertEquals(101, local.version.clock);
    }

    /** Verifies that a set with fewer nodes cannot remove local nodes. */
    @Test
    void nonSupersetDoesNotRemoveNodes() {
        var local = setWith("A", 5, "A", "B", "C");
        var remote = setWith("B", 9, "A");

        assertFalse(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B", "C"), local.nodes);
        assertEquals(id("A"), local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /**
     * Verifies that the lower process id wins when two peers start leaving
     * concurrently.
     */
    @Test
    void lowerProcessIdWinsForConcurrentProcesses() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "process-20", "C", "C", 3);
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "process-10", "C", "C", 3);

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
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

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ActiveNodeSet.Phase.B, local.leavingProcess.phase);
        assertEquals(ids("A", "B"), local.nodes);
    }

    /**
     * Verifies that phase B neither adds nodes nor adopts an unrelated remote
     * version.
     */
    @Test
    void phaseBBlocksNodeAdditions() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.B, "p", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "D");
        startProcess(remote, ActiveNodeSet.Phase.B, "p", "C", "A", 3);

        assertFalse(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B"), local.nodes);
        assertEquals(id("A"), local.version.nodeId);
        assertEquals(5, local.version.clock);
    }

    /**
     * Verifies that a completed leave is not regressed by a stale phase-A record.
     */
    @Test
    void completedProcessIsNotRegressedByStaleRecord() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.I, "p", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "C", 3);

        local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock());
        assertEquals(ActiveNodeSet.Phase.I, local.leavingProcess.phase);
        assertEquals(ids("A", "B"), local.nodes);
        assertEquals(id("A"), local.version.nodeId);
        assertEquals(101, local.version.clock);
    }

    /** Verifies that a new leave process replaces the completed tombstone. */
    @Test
    void newProcessReplacesCompletedProcess() {
        var local = setWith("A", 5, "A", "B");
        startProcess(local, ActiveNodeSet.Phase.I, "old", "C", "A", 3);
        var remote = setWith("B", 9, "A", "B", "D");
        startProcess(remote, ActiveNodeSet.Phase.A, "new", "D", "D", 3);

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals("new", local.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);
        assertEquals(ids("A", "B", "D"), local.nodes);
    }

    /**
     * Verifies that the completed tombstone cannot cancel a newly started process.
     */
    @Test
    void completedProcessDoesNotCancelNewProcess() {
        var local = setWith("A", 5, "A", "B", "D");
        startProcess(local, ActiveNodeSet.Phase.A, "new", "D", "D", 3);
        var remote = setWith("B", 9, "A", "B");
        startProcess(remote, ActiveNodeSet.Phase.I, "old", "C", "A", 3);

        assertFalse(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals("new", local.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);

        assertTrue(remote.mergeFrom(local, id("B"), local.version.nodeId, nextClock()));
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

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(ids("A", "B"), local.leavingProcess.confirmedBy);
    }

    /**
     * Verifies that a higher process clock replaces both clock source and
     * confirmations.
     */
    @Test
    void higherProcessClockReplacesConfirmations() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "X", 3, "A");
        var remote = setWith("B", 9, "A", "B", "C");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "C", "Y", 8, "B");

        assertTrue(local.mergeFrom(remote, id("A"), remote.version.nodeId, nextClock()));
        assertEquals(8, local.leavingProcess.lastOperationClock);
        assertEquals(id("Y"), local.leavingProcess.lastOperationClockSourceNode);
        assertEquals(ids("B"), local.leavingProcess.confirmedBy);
    }

    /**
     * Verifies that startLeaving initializes phase A from the leaving node's clock.
     */
    @Test
    void startLeavingInitializesProcessAndBumpsVersion() {
        var set = setWith("A", 5, "A", "B", "C");

        assertTrue(set.startLeaving(id("C"), "p", clock("C", 42L), id("A"), nextClock()));
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
        assertEquals("p", set.leavingProcess.processId);
        assertEquals(id("C"), set.leavingProcess.leavingNodeId);
        assertEquals(42, set.leavingProcess.lastOperationClock);
        assertEquals(id("C"), set.leavingProcess.lastOperationClockSourceNode);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals(id("A"), set.version.nodeId);
        assertEquals(101, set.version.clock);
    }

    /**
     * Verifies that an equal or higher process id cannot replace an in-flight
     * process.
     */
    @Test
    void startLeavingRejectsEqualOrHigherProcessId() {
        var set = setWith("A", 5, "A", "B", "C");
        set.startLeaving(id("C"), "b", Map.of(), id("A"), nextClock());
        long versionClock = set.version.clock;

        assertFalse(set.startLeaving(id("C"), "b", Map.of(), id("A"), nextClock()));
        assertFalse(set.startLeaving(id("C"), "c", Map.of(), id("A"), nextClock()));
        assertEquals("b", set.leavingProcess.processId);
        assertEquals(versionClock, set.version.clock);
    }

    /** Verifies that a lower process id replaces an in-flight process. */
    @Test
    void startLeavingAllowsLowerProcessId() {
        var set = setWith("A", 5, "A", "B", "C");
        set.startLeaving(id("C"), "b", Map.of(), id("A"), nextClock());

        assertTrue(set.startLeaving(id("C"), "a", Map.of(), id("A"), nextClock()));
        assertEquals("a", set.leavingProcess.processId);
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
    }

    /**
     * Verifies that a new leave process may start once the previous one reached
     * idle.
     */
    @Test
    void startLeavingAllowsNewProcessAfterCompletion() {
        var set = setWith("A", 5, "A", "B");
        startProcess(set, ActiveNodeSet.Phase.I, "z", "C", "A", 3);

        assertTrue(set.startLeaving(id("B"), "a", Map.of(), id("A"), nextClock()));
        assertEquals("a", set.leavingProcess.processId);
        assertEquals(id("B"), set.leavingProcess.leavingNodeId);
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
    }

    /** Verifies that updateLeaving ignores missing and own leave processes. */
    @Test
    void updateLeavingIgnoresInactiveProcesses() {
        var noProcess = setWith("A", 5, "A", "B");
        assertFalse(noProcess.updateLeaving(id("A"), Map.of(), nextClock()));

        var leavingNode = setWith("A", 5, "A", "B");
        startProcess(leavingNode, ActiveNodeSet.Phase.A, "p", "A", "A", 3);
        assertFalse(leavingNode.updateLeaving(id("A"), clock("A", 3L), nextClock()));
    }

    /** Verifies that a completed leave process returns the set to idle. */
    @Test
    void completedProcessReturnsToIdle() {
        var set = setWith("A", 5, "A", "B");
        startProcess(set, ActiveNodeSet.Phase.I, "p", "C", "A", 3);

        assertTrue(set.updateLeaving(id("A"), clock("C", 3L), nextClock()));
        assertNull(set.leavingProcess);
        assertEquals(id("A"), set.version.nodeId);
        assertEquals(101, set.version.clock);
        assertFalse(set.updateLeaving(id("A"), clock("C", 3L), nextClock()));
    }

    /**
     * Verifies that a confirmation is postponed while the local clock is behind.
     */
    @Test
    void updateLeavingPostponesWhileBehind() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 10);

        assertFalse(set.updateLeaving(id("A"), clock("C", 4L), nextClock()));
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals(5, set.version.clock);
    }

    /**
     * Verifies that a higher local clock resets the confirmations before
     * confirming.
     */
    @Test
    void updateLeavingRaisesClockAndClearsConfirmations() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 3, "B");

        assertTrue(set.updateLeaving(id("A"), clock("C", 9L), nextClock()));
        assertEquals(9, set.leavingProcess.lastOperationClock);
        assertEquals(ids("A"), set.leavingProcess.confirmedBy);
    }

    /** Verifies that a vanished clock source falls back to the local node. */
    @Test
    void updateLeavingFallsBackWhenClockSourceIsGone() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "D", 10);

        assertTrue(set.updateLeaving(id("A"), clock("C", 6L), nextClock()));
        assertEquals(6, set.leavingProcess.lastOperationClock);
        assertEquals(id("A"), set.leavingProcess.lastOperationClockSourceNode);
        assertEquals(ids("A"), set.leavingProcess.confirmedBy);
        assertEquals(101, set.version.clock);
    }

    /**
     * Verifies that phase A completes when all remaining nodes confirmed the leave.
     */
    @Test
    void updateLeavingAdvancesAToBWhenAllPeersConfirm() {
        var set = setWith("A", 5, "A", "B", "C");
        startProcess(set, ActiveNodeSet.Phase.A, "p", "C", "C", 5);

        assertTrue(set.updateLeaving(id("A"), clock("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.A, set.leavingProcess.phase);
        assertTrue(set.nodes.contains(id("C")));

        assertTrue(set.updateLeaving(id("B"), clock("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.B, set.leavingProcess.phase);
        assertEquals(ids("A", "B"), set.nodes);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());
        assertEquals(102, set.version.clock);
    }

    /**
     * Verifies that phase B reaches idle when all remaining nodes confirmed the
     * removal.
     */
    @Test
    void updateLeavingAdvancesBToIWhenAllPeersConfirm() {
        var set = setWith("A", 5, "A", "B");
        startProcess(set, ActiveNodeSet.Phase.B, "p", "C", "C", 5);

        assertTrue(set.updateLeaving(id("A"), clock("C", 5L), nextClock()));
        assertEquals(id("A"), set.leavingProcess.lastOperationClockSourceNode);
        assertEquals(ids("A"), set.leavingProcess.confirmedBy);

        assertTrue(set.updateLeaving(id("B"), clock("C", 5L), nextClock()));
        assertEquals(ActiveNodeSet.Phase.I, set.leavingProcess.phase);
        assertEquals(ids("A", "B"), set.nodes);
        assertTrue(set.leavingProcess.confirmedBy.isEmpty());

        // The next update returns the completed process to idle.
        assertTrue(set.updateLeaving(id("A"), clock("C", 5L), nextClock()));
        assertNull(set.leavingProcess);
    }

    /**
     * Verifies that a node removed by a leave process is not re-added by a stale
     * peer.
     */
    @Test
    void removedNodeCannotBeReAdded() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "C", 3);
        local.updateLeaving(id("A"), clock("C", 3L), nextClock());
        local.updateLeaving(id("B"), clock("C", 3L), nextClock());
        assertEquals(ActiveNodeSet.Phase.B, local.leavingProcess.phase);
        assertFalse(local.nodes.contains(id("C")));

        var remote = setWith("B", 9, "A", "B", "C");
        assertFalse(local.mergeFrom(remote, id("A"), id("B"), nextClock()));
        assertFalse(local.nodes.contains(id("C")));
        assertTrue(local.removedNodes.contains(id("C")));
    }

    /**
     * Verifies that a set can only be merged when its sender belongs to the node
     * set.
     */
    @Test
    void senderMustBelongToNodeSet() {
        var local = setWith("A", 5, "A");
        var remote = setWith("B", 9, "B", "C");

        assertFalse(local.mergeFrom(remote, id("A"), id("X"), nextClock()));
        assertEquals(ids("A"), local.nodes);
        assertEquals(5, local.version.clock);

        assertTrue(local.mergeFrom(remote, id("A"), id("B"), nextClock()));
        assertEquals(ids("A", "B", "C"), local.nodes);
    }

    /** Verifies that an inconsistent leave process is dropped instead of copied. */
    @Test
    void inconsistentLeavingProcessIsIgnored() {
        var local = setWith("A", 5, "A");
        var remote = setWith("B", 9, "A", "B");
        startProcess(remote, ActiveNodeSet.Phase.A, "p", "Z", "Z", 3);

        assertTrue(local.mergeFrom(remote, id("A"), id("B"), nextClock()));
        assertEquals(ids("A", "B"), local.nodes);
        assertNull(local.leavingProcess);
    }

    /**
     * Verifies that a peer cannot advance a leave process past the locally known
     * clock.
     */
    @Test
    void higherPhaseCannotAdvancePastLocalClock() {
        var local = setWith("A", 5, "A", "B", "C");
        startProcess(local, ActiveNodeSet.Phase.A, "p", "C", "C", 10);
        var remote = setWith("B", 9, "A", "B");
        startProcess(remote, ActiveNodeSet.Phase.B, "p", "C", "C", 4);

        assertFalse(local.mergeFrom(remote, id("A"), id("B"), nextClock()));
        assertEquals(ActiveNodeSet.Phase.A, local.leavingProcess.phase);
        assertTrue(local.nodes.contains(id("C")));
    }

    /** Verifies that a copy is independent from its source. */
    @Test
    void copyIsIndependent() {
        var original = setWith("A", 5, "A", "B");
        startProcess(original, ActiveNodeSet.Phase.A, "p", "B", "B", 5, "A");

        var copy = original.copy();
        copy.nodes.add(id("C"));
        copy.leavingProcess.confirmedBy.add(id("B"));

        assertEquals(ids("A", "B"), original.nodes);
        assertEquals(ids("A"), original.leavingProcess.confirmedBy);
    }

    /** Verifies that the persisted version, nodes, and leave process round-trip. */
    @Test
    void persistedStateRoundTrips() {
        var original = setWith("A", 5, "A", "B", "C");
        original.removedNodes.add(id("D"));
        startProcess(original, ActiveNodeSet.Phase.B, "p", "C", "A", 9, "A", "B");

        var loaded = new ActiveNodeSet();
        Node.loadActiveNodeSet(Node.persist(original), loaded);

        assertEquals(ids("D"), loaded.removedNodes);
        assertEquals(id("A"), loaded.version.nodeId);
        assertEquals(5, loaded.version.clock);
        assertEquals(ids("A", "B", "C"), loaded.nodes);
        assertEquals(ActiveNodeSet.Phase.B, loaded.leavingProcess.phase);
        assertEquals("p", loaded.leavingProcess.processId);
        assertEquals(id("C"), loaded.leavingProcess.leavingNodeId);
        assertEquals(9, loaded.leavingProcess.lastOperationClock);
        assertEquals(id("A"), loaded.leavingProcess.lastOperationClockSourceNode);
        assertEquals(ids("A", "B"), loaded.leavingProcess.confirmedBy);
    }
}
