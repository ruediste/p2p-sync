package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Test;

/**
 * In-memory mock of the node algorithm described in the wiki: active node set
 * (joining, leaving, versioning), operation synchronization (including
 * truncated operations) and log truncation based on the causally stable
 * timestamp.
 */
public class NodeAlgorithmTest {

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    /** Identity and total order of an operation: (lamport clock, node id). */
    static final class OpId implements Comparable<OpId> {
        final long clock;
        final String nodeId;

        OpId(long clock, String nodeId) {
            this.clock = clock;
            this.nodeId = nodeId;
        }

        @Override
        public int compareTo(OpId other) {
            int c = Long.compare(clock, other.clock);
            return c != 0 ? c : nodeId.compareTo(other.nodeId);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof OpId other))
                return false;
            return clock == other.clock && nodeId.equals(other.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clock, nodeId);
        }

        @Override
        public String toString() {
            return nodeId + "@" + clock;
        }
    }

    static final class Operation {
        final OpId id;
        final String payload;

        Operation(OpId id, String payload) {
            this.id = id;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return id + ":" + payload;
        }
    }

    /** One operation (or the tail of one) as returned by a peer. */
    static final class Transfer {
        final Operation operation;
        final int offset;
        final String data;
        final boolean complete;

        Transfer(Operation operation, int offset, String data, boolean complete) {
            this.operation = operation;
            this.offset = offset;
            this.data = data;
            this.complete = complete;
        }
    }

    /** An operation whose data has only partially been retrieved. */
    static final class PartialOperation {
        final Operation operation;
        int receivedLength;

        PartialOperation(Operation operation) {
            this.operation = operation;
        }
    }

    static final class Snapshot {
        final List<Operation> operations;

        Snapshot(List<Operation> operations) {
            this.operations = operations;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Snapshot other))
                return false;
            return operations.equals(other.operations);
        }

        @Override
        public int hashCode() {
            return operations.hashCode();
        }
    }

    // ------------------------------------------------------------------
    // Active node set
    // ------------------------------------------------------------------

    static final class ActiveNodeSetVersion {
        final String nodeId;
        final long clock;

        ActiveNodeSetVersion(String nodeId, long clock) {
            this.nodeId = nodeId;
            this.clock = clock;
        }

        boolean sameAs(ActiveNodeSetVersion other) {
            return other != null && clock == other.clock && nodeId.equals(other.nodeId);
        }
    }

    enum Phase {
        A, B
    }

    static final class LeavingProcess {
        Phase phase;
        String processId;
        String leavingNodeId;
        long lastOperationClock;
        String lastOperationClockSourceNode;
        final Set<String> confirmedBy = new TreeSet<>();

        LeavingProcess copy() {
            var copy = new LeavingProcess();
            copy.phase = phase;
            copy.processId = processId;
            copy.leavingNodeId = leavingNodeId;
            copy.lastOperationClock = lastOperationClock;
            copy.lastOperationClockSourceNode = lastOperationClockSourceNode;
            copy.confirmedBy.addAll(confirmedBy);
            return copy;
        }
    }

    static final class ActiveNodeSet {
        ActiveNodeSetVersion version;
        LeavingProcess leavingProcess;
        final Set<String> nodes = new TreeSet<>();
        final Map<String, Long> lastSeen = new HashMap<>();

        ActiveNodeSet copy() {
            var copy = new ActiveNodeSet();
            copy.version = version;
            copy.leavingProcess = leavingProcess == null ? null : leavingProcess.copy();
            copy.nodes.addAll(nodes);
            copy.lastSeen.putAll(lastSeen);
            return copy;
        }
    }

    static final class ActiveNodeInfo {
        final Map<String, Long> maxOperationClock = new HashMap<>();
        final Map<String, Long> lastSeenTimestamp = new HashMap<>();
    }

    // ------------------------------------------------------------------
    // Mock node
    // ------------------------------------------------------------------

    static final class MockNode {
        final String nodeId;
        long clock;

        /** Operations merged into the file system. */
        final TreeMap<OpId, Operation> operationTree = new TreeMap<>();
        /** Operations retrieved completely, but not yet merged. */
        final TreeMap<OpId, Operation> stagingTree = new TreeMap<>();
        /** At most one incomplete operation per source node. */
        final Map<String, PartialOperation> retrievalTree = new HashMap<>();

        final ActiveNodeInfo info = new ActiveNodeInfo();
        final ActiveNodeSet activeNodeSet = new ActiveNodeSet();
        final List<Snapshot> snapshots = new ArrayList<>();

        boolean frozen;

        MockNode(String nodeId) {
            this.nodeId = nodeId;
            activeNodeSet.nodes.add(nodeId);
            bumpActiveNodeSetVersion();
        }

        // ---------- local modifications ----------

        Operation createOperation(String payload) {
            var op = new Operation(new OpId(++clock, nodeId), payload);
            operationTree.put(op.id, op);
            info.maxOperationClock.merge(nodeId, op.id.clock, Math::max);
            return op;
        }

        /** Everything this node can serve, merged and staged operations. */
        TreeMap<OpId, Operation> allOperations() {
            var all = new TreeMap<>(operationTree);
            all.putAll(stagingTree);
            return all;
        }

        void mergeStaging() {
            operationTree.putAll(stagingTree);
            stagingTree.clear();
        }

        // ---------- versioning ----------

        void bumpActiveNodeSetVersion() {
            activeNodeSet.version = new ActiveNodeSetVersion(nodeId, clock++);
        }

        // ---------- operation synchronization ----------

        /** Peer side: return the next operations for the requested clocks. */
        List<Transfer> serve(Map<String, Long> fromClock, Map<String, Integer> offsets, int maxOperations,
                int byteBudget) {
            var all = allOperations();
            var result = new ArrayList<Transfer>();
            int bytes = 0;
            for (String source : new TreeSet<>(fromClock.keySet())) {
                long from = fromClock.get(source);
                int offset = offsets.getOrDefault(source, 0);
                boolean first = true;
                for (Operation op : all.values()) {
                    if (!op.id.nodeId.equals(source) || op.id.clock <= from)
                        continue;
                    int start = first ? Math.min(offset, op.payload.length()) : 0;
                    first = false;
                    if (result.size() >= maxOperations)
                        return result;
                    int available = byteBudget - bytes;
                    if (op.payload.length() - start > available) {
                        result.add(new Transfer(op, start, op.payload.substring(start, start + available), false));
                        return result;
                    }
                    result.add(new Transfer(op, start, op.payload.substring(start), true));
                    bytes += op.payload.length() - start;
                }
            }
            return result;
        }

        /** Requester side: integrate the response of a peer. */
        void receive(List<Transfer> transfers) {
            for (Transfer transfer : transfers) {
                String source = transfer.operation.id.nodeId;
                if (transfer.complete) {
                    stagingTree.put(transfer.operation.id, transfer.operation);
                    info.maxOperationClock.merge(source, transfer.operation.id.clock, Math::max);
                    clock = Math.max(clock, transfer.operation.id.clock);
                    retrievalTree.remove(source);
                } else {
                    var partial = retrievalTree.get(source);
                    if (partial == null || !partial.operation.id.equals(transfer.operation.id)) {
                        partial = new PartialOperation(transfer.operation);
                        retrievalTree.put(source, partial);
                    }
                    partial.receivedLength = transfer.offset + transfer.data.length();
                }
            }
        }

        void syncFrom(MockNode peer, int maxOperations, int byteBudget) {
            var fromClock = new TreeMap<String, Long>();
            var offsets = new HashMap<String, Integer>();
            var sources = new TreeSet<>(activeNodeSet.nodes);
            sources.addAll(peer.activeNodeSet.nodes);
            sources.remove(nodeId);
            for (String source : sources) {
                fromClock.put(source, info.maxOperationClock.getOrDefault(source, 0L));
                var partial = retrievalTree.get(source);
                if (partial != null)
                    offsets.put(source, partial.receivedLength);
            }
            receive(peer.serve(fromClock, offsets, maxOperations, byteBudget));
        }

        // ---------- active node set merge ----------

        boolean mergeActiveNodeSet(ActiveNodeSet other) {
            if (activeNodeSet.version.sameAs(other.version))
                return false;

            boolean changed = false;
            LeavingProcess mine = activeNodeSet.leavingProcess;
            LeavingProcess theirs = other.leavingProcess;
            if (theirs != null) {
                if (mine == null) {
                    activeNodeSet.leavingProcess = theirs.copy();
                    changed = true;
                } else if (theirs.phase.ordinal() > mine.phase.ordinal()) {
                    activeNodeSet.leavingProcess = theirs.copy();
                    changed = true;
                } else if (theirs.phase == mine.phase && !mine.processId.equals(theirs.processId)) {
                    // concurrently initiated processes: the lower id wins
                    if (mine.processId.compareTo(theirs.processId) > 0) {
                        activeNodeSet.leavingProcess = theirs.copy();
                        changed = true;
                    }
                } else if (theirs.phase == mine.phase) {
                    if (mine.confirmedBy.addAll(theirs.confirmedBy))
                        changed = true;
                    if (theirs.lastOperationClock > mine.lastOperationClock) {
                        mine.lastOperationClock = theirs.lastOperationClock;
                        mine.lastOperationClockSourceNode = theirs.lastOperationClockSourceNode;
                        mine.confirmedBy.clear();
                        mine.confirmedBy.addAll(theirs.confirmedBy);
                        changed = true;
                    }
                }
            }

            boolean strictSuperset = other.nodes.containsAll(activeNodeSet.nodes)
                    && other.nodes.size() > activeNodeSet.nodes.size();
            var process = activeNodeSet.leavingProcess;
            boolean phaseB = process != null && process.phase == Phase.B;
            if (!phaseB && activeNodeSet.nodes.addAll(other.nodes))
                changed = true;
            activeNodeSet.lastSeen.putAll(other.lastSeen);
            if (phaseB && activeNodeSet.nodes.remove(process.leavingNodeId))
                changed = true;

            if (strictSuperset)
                // a superset is adopted together with its version metadata
                activeNodeSet.version = other.version;
            else if (changed)
                bumpActiveNodeSetVersion();
            return changed;
        }

        // ---------- leaving ----------

        void startLeaving(String leavingNodeId, String processId) {
            var existing = activeNodeSet.leavingProcess;
            if (existing != null && existing.processId.compareTo(processId) <= 0)
                return; // existing process has the lower id and wins
            var process = new LeavingProcess();
            process.phase = Phase.A;
            process.processId = processId;
            process.leavingNodeId = leavingNodeId;
            process.lastOperationClock = info.maxOperationClock.getOrDefault(leavingNodeId, 0L);
            process.lastOperationClockSourceNode = leavingNodeId;
            activeNodeSet.leavingProcess = process;
            bumpActiveNodeSetVersion();
        }

        boolean updateLeaving() {
            var process = activeNodeSet.leavingProcess;
            if (process == null || process.leavingNodeId.equals(nodeId))
                return false;

            boolean changed = false;
            // if the source of the maximum clock is gone, fall back to the local clock
            if (!activeNodeSet.nodes.contains(process.lastOperationClockSourceNode)) {
                process.lastOperationClock = info.maxOperationClock.getOrDefault(process.leavingNodeId, 0L);
                process.lastOperationClockSourceNode = nodeId;
                process.confirmedBy.clear();
                changed = true;
            }

            long local = info.maxOperationClock.getOrDefault(process.leavingNodeId, 0L);
            if (local < process.lastOperationClock)
                return changed; // postpone until the operations are retrieved

            if (local > process.lastOperationClock) {
                process.lastOperationClock = local;
                process.confirmedBy.clear();
                changed = true;
            }
            if (process.confirmedBy.add(nodeId))
                changed = true;

            var required = new TreeSet<>(activeNodeSet.nodes);
            required.remove(process.leavingNodeId);
            if (process.confirmedBy.containsAll(required)) {
                if (process.phase == Phase.A) {
                    activeNodeSet.nodes.remove(process.leavingNodeId);
                    process.phase = Phase.B;
                    process.confirmedBy.clear();
                    changed = true;
                } else {
                    activeNodeSet.leavingProcess = null; // back to idle
                    changed = true;
                }
            }
            if (changed)
                bumpActiveNodeSetVersion();
            return changed;
        }

        // ---------- log truncation ----------

        OpId causallyStable() {
            OpId min = null;
            for (String node : activeNodeSet.nodes) {
                var candidate = new OpId(info.maxOperationClock.getOrDefault(node, 0L), node);
                if (min == null || candidate.compareTo(min) < 0)
                    min = candidate;
            }
            return min;
        }

        Snapshot truncate() {
            var stable = causallyStable();
            if (stable == null)
                return null;
            var snapshot = new Snapshot(new ArrayList<>(operationTree.values()));
            operationTree.keySet().removeIf(id -> id.compareTo(stable) < 0);
            snapshots.add(snapshot);
            return snapshot;
        }
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    private void connect(List<MockNode> nodes) {
        var ids = new TreeSet<String>();
        nodes.forEach(n -> ids.add(n.nodeId));
        for (MockNode node : nodes) {
            node.activeNodeSet.nodes.addAll(ids);
            node.bumpActiveNodeSetVersion();
        }
    }

    private void syncAll(List<MockNode> nodes) {
        for (int round = 0; round < 3; round++)
            for (MockNode a : nodes)
                for (MockNode b : nodes)
                    if (a != b)
                        a.syncFrom(b, 100, 100_000);
    }

    private void settleLeaving(List<MockNode> nodes) {
        for (int i = 0; i < 100; i++) {
            boolean changed = false;
            for (MockNode a : nodes)
                for (MockNode b : nodes)
                    if (a != b)
                        changed |= a.mergeActiveNodeSet(b.activeNodeSet);
            for (MockNode node : nodes)
                changed |= node.updateLeaving();
            if (!changed)
                return;
        }
        throw new AssertionError("leaving process did not settle");
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    public void operationsAreOrderedByClockThenNodeId() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        var first = a.createOperation("a1");
        var second = b.createOperation("b1");
        var third = a.createOperation("a2");

        assertEquals(2, first.id.clock);
        assertEquals(2, second.id.clock);
        assertTrue(first.id.compareTo(second.id) < 0); // same clock, "A" < "B"
        assertEquals(3, third.id.clock);
        assertTrue(second.id.compareTo(third.id) < 0);
    }

    @Test
    public void syncRetrievesAllOperationsInOrder() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        connect(List.of(a, b));

        a.createOperation("a1");
        a.createOperation("a2");
        a.createOperation("a3");

        b.syncFrom(a, 100, 100_000);

        assertEquals(Set.of("A@3", "A@4", "A@5"), stagingIds(b));
        assertEquals(5L, (long) b.info.maxOperationClock.get("A"));
        assertTrue(b.retrievalTree.isEmpty());
    }

    @Test
    public void syncDoesNotAdvanceClocksAcrossGaps() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        connect(List.of(a, b));

        a.createOperation("a1");
        a.createOperation("a2");
        a.createOperation("a3");

        // retrieve only two operations per sync
        b.syncFrom(a, 2, 100_000);
        assertEquals(Set.of("A@3", "A@4"), stagingIds(b));
        assertEquals(4L, (long) b.info.maxOperationClock.get("A"));

        b.syncFrom(a, 2, 100_000);
        assertEquals(Set.of("A@3", "A@4", "A@5"), stagingIds(b));
        assertEquals(5L, (long) b.info.maxOperationClock.get("A"));
    }

    @Test
    public void truncatedOperationIsCompletedOnNextSync() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        connect(List.of(a, b));

        String payload = "x".repeat(100);
        a.createOperation(payload);

        // only 30 bytes fit into the response
        b.syncFrom(a, 5, 30);
        assertTrue(b.stagingTree.isEmpty());
        assertEquals(1, b.retrievalTree.size());
        assertEquals(30, b.retrievalTree.get("A").receivedLength);
        assertFalse(b.info.maxOperationClock.containsKey("A"));

        // second sync continues the partial operation
        b.syncFrom(a, 5, 100_000);
        assertTrue(b.retrievalTree.isEmpty());
        assertEquals(Set.of("A@3"), stagingIds(b));
        assertEquals(3L, (long) b.info.maxOperationClock.get("A"));

        b.mergeStaging();
        assertEquals(payload, b.operationTree.get(new OpId(3, "A")).payload);
    }

    @Test
    public void activeNodeSetEqualVersionNeedsNoTransfer() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        var set = new ActiveNodeSet();
        set.nodes.addAll(Set.of("A", "B"));
        set.version = new ActiveNodeSetVersion("A", 5);

        var target = new MockNode("C");
        target.activeNodeSet.nodes.addAll(Set.of("A", "B"));
        target.activeNodeSet.version = new ActiveNodeSetVersion("A", 5);

        assertFalse(target.mergeActiveNodeSet(set));
    }

    @Test
    public void activeNodeSetSupersetIsAdopted() {
        var node = new MockNode("C");
        node.activeNodeSet.nodes.add("A");
        node.activeNodeSet.version = new ActiveNodeSetVersion("C", 1);

        var received = new ActiveNodeSet();
        received.nodes.addAll(Set.of("A", "B", "C"));
        received.version = new ActiveNodeSetVersion("A", 7);

        assertTrue(node.mergeActiveNodeSet(received));
        assertEquals(Set.of("A", "B", "C"), node.activeNodeSet.nodes);
        assertEquals(7, node.activeNodeSet.version.clock);
    }

    @Test
    public void activeNodeSetNonSupersetIsIgnored() {
        var node = new MockNode("C");
        node.activeNodeSet.nodes.addAll(Set.of("A", "B", "C"));
        node.activeNodeSet.version = new ActiveNodeSetVersion("C", 1);

        var received = new ActiveNodeSet();
        received.nodes.addAll(Set.of("A"));
        received.version = new ActiveNodeSetVersion("A", 9);

        // A receives a set that is missing nodes: it must be rejected
        var a = new MockNode("A");
        a.activeNodeSet.nodes.addAll(Set.of("A", "B", "C"));
        a.activeNodeSet.version = new ActiveNodeSetVersion("A", 1);
        assertFalse(a.mergeActiveNodeSet(received));
        assertEquals(Set.of("A", "B", "C"), a.activeNodeSet.nodes);
    }

    @Test
    public void joinDownloadsSnapshotAndOperations() {
        var a = new MockNode("A");
        a.activeNodeSet.nodes.add("A");
        a.createOperation("a1");
        a.createOperation("a2");

        var b = new MockNode("B");
        join(b, a);

        assertTrue(a.activeNodeSet.nodes.contains("B"));
        assertTrue(b.activeNodeSet.nodes.contains("A"));
        assertEquals(Set.of("A@2", "A@3"),
                b.operationTree.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals(b.clock, a.clock);
    }

    private void join(MockNode joiner, MockNode existing) {
        existing.frozen = true;
        joiner.clock = existing.clock;
        joiner.operationTree.clear();
        joiner.operationTree.putAll(existing.operationTree);
        joiner.stagingTree.putAll(existing.stagingTree);
        joiner.info.maxOperationClock.clear();
        joiner.info.maxOperationClock.putAll(existing.info.maxOperationClock);
        joiner.activeNodeSet.nodes.clear();
        joiner.activeNodeSet.nodes.addAll(existing.activeNodeSet.nodes);
        joiner.activeNodeSet.leavingProcess = existing.activeNodeSet.leavingProcess == null ? null
                : existing.activeNodeSet.leavingProcess.copy();
        existing.activeNodeSet.nodes.add(joiner.nodeId);
        joiner.activeNodeSet.nodes.add(joiner.nodeId);
        existing.bumpActiveNodeSetVersion();
        joiner.bumpActiveNodeSetVersion();
        existing.frozen = false;
    }

    @Test
    public void leavingRunsThroughPhaseAAndB() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        var c = new MockNode("C");
        var nodes = List.of(a, b, c);
        connect(nodes);

        a.createOperation("a1");
        b.createOperation("b1");
        c.createOperation("c1");
        syncAll(nodes);

        // C is marked as leaving
        a.startLeaving("C", "process-1");
        settleLeaving(List.of(a, b)); // all active nodes except the leaving one

        assertNull(a.activeNodeSet.leavingProcess);
        assertFalse(a.activeNodeSet.nodes.contains("C"));
        assertFalse(b.activeNodeSet.nodes.contains("C"));
        assertTrue(a.activeNodeSet.nodes.contains("A"));
        assertTrue(a.activeNodeSet.nodes.contains("B"));
    }

    @Test
    public void leavingIsPostponedUntilOperationsAreRetrieved() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        var c = new MockNode("C");
        var nodes = List.of(a, b, c);
        connect(nodes);

        c.createOperation("c1");
        c.createOperation("c2");
        // A knows C, B does not yet know C's operations
        a.syncFrom(c, 100, 100_000);
        b.info.maxOperationClock.put("C", 0L);

        a.startLeaving("C", "process-1");
        settleLeaving(List.of(a, b));

        // B cannot confirm, so the leaving process is still in phase A
        assertNotNull(a.activeNodeSet.leavingProcess);
        assertEquals(Phase.A, a.activeNodeSet.leavingProcess.phase);
        assertTrue(a.activeNodeSet.nodes.contains("C"));

        // B catches up and the process completes
        b.syncFrom(c, 100, 100_000);
        settleLeaving(List.of(a, b));
        assertNull(a.activeNodeSet.leavingProcess);
        assertFalse(a.activeNodeSet.nodes.contains("C"));
    }

    @Test
    public void concurrentlyStartedLeavingProcessesKeepLowerProcessId() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        var c = new MockNode("C");
        connect(List.of(a, b, c));

        a.startLeaving("C", "process-20");
        b.startLeaving("C", "process-10");

        assertTrue(a.mergeActiveNodeSet(b.activeNodeSet));
        assertEquals("process-10", a.activeNodeSet.leavingProcess.processId);
    }

    @Test
    public void causallyStableTimestampIsMinimumAcrossActiveNodes() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        connect(List.of(a, b));

        // A has operations up to clock 3, B up to clock 5
        a.createOperation("a1");
        a.createOperation("a2");
        a.createOperation("a3");
        for (int i = 0; i < 5; i++)
            b.createOperation("b" + i);
        syncAll(List.of(a, b));

        assertEquals(5L, (long) a.info.maxOperationClock.get("A"));
        assertEquals(7L, (long) a.info.maxOperationClock.get("B"));
        assertEquals(new OpId(5, "A"), a.causallyStable());
    }

    @Test
    public void truncateRemovesOperationsOlderThanStableTimestamp() {
        var a = new MockNode("A");
        var b = new MockNode("B");
        connect(List.of(a, b));

        a.createOperation("a1");
        a.createOperation("a2");
        a.createOperation("a3");
        for (int i = 0; i < 5; i++)
            b.createOperation("b" + i);
        syncAll(List.of(a, b));
        a.mergeStaging();

        var stable = a.causallyStable();
        var before = a.operationTree.size();
        assertNotNull(a.truncate());

        assertTrue(a.operationTree.keySet().stream().allMatch(id -> id.compareTo(stable) >= 0));
        assertTrue(a.operationTree.size() < before);
        assertEquals(1, a.snapshots.size());
    }

    // ------------------------------------------------------------------
    // Randomized convergence
    // ------------------------------------------------------------------

    private static final int RANDOM_ITERATIONS = 100;
    private static final int MAX_NODES = 6;

    @Test
    public void randomizedNodesConverge() {
        int operations = 0;
        int joins = 0;
        int leaves = 0;
        int syncs = 0;
        for (int iteration = 0; iteration < RANDOM_ITERATIONS; iteration++) {
            var counts = runRandomizedIteration(7_000 + iteration);
            operations += counts[0];
            joins += counts[1];
            leaves += counts[2];
            syncs += counts[3];
        }
        assertTrue("expected operations to be created", operations > 0);
        assertTrue("expected nodes to join", joins > 0);
        assertTrue("expected nodes to leave", leaves > 0);
        assertTrue("expected nodes to sync", syncs > 0);
    }

    /** Returns counts of [operations, joins, leaves, syncs]. */
    private int[] runRandomizedIteration(long seed) {
        var rnd = new Random(seed);
        int steps = 30 + rnd.nextInt(40);
        int[] counts = new int[4];

        var all = new ArrayList<MockNode>();
        var genesis = new MockNode("N0");
        all.add(genesis);

        int nextId = 1;
        var second = new MockNode("N" + nextId++);
        join(second, genesis);
        all.add(second);

        for (int step = 0; step < steps; step++) {
            int action = rnd.nextInt(12);
            if (action < 6) {
                var candidates = liveNodes(all).stream().filter(n -> !isLeavingTarget(n, all)).toList();
                if (!candidates.isEmpty()) {
                    var node = candidates.get(rnd.nextInt(candidates.size()));
                    node.createOperation(node.nodeId + "-op-" + step + "-" + "x".repeat(rnd.nextInt(20)));
                    counts[0]++;
                }
            } else if (action < 8 && all.size() < MAX_NODES) {
                settle(all);
                var live = liveNodes(all);
                if (!live.isEmpty()) {
                    var existing = live.get(rnd.nextInt(live.size()));
                    var joiner = new MockNode("N" + nextId++);
                    join(joiner, existing);
                    all.add(joiner);
                    counts[1]++;
                }
            } else if (action < 9 && liveNodes(all).size() > 2) {
                settle(all);
                var live = liveNodes(all);
                var leaving = live.get(rnd.nextInt(live.size()));
                var initiator = live.get((live.indexOf(leaving) + 1) % live.size());
                initiator.startLeaving(leaving.nodeId, "leave-" + seed + "-" + step);
                counts[2]++;
            } else {
                var live = liveNodes(all);
                if (live.size() >= 2) {
                    var a = live.get(rnd.nextInt(live.size()));
                    var b = live.get(rnd.nextInt(live.size()));
                    if (a != b) {
                        a.syncFrom(b, 1_000, 1_000_000);
                        counts[3]++;
                    }
                }
            }

            if (step % 5 == 4) {
                settle(all);
                assertConverged(all);
            }
        }

        settle(all);
        assertConverged(all);
        return counts;
    }

    /**
     * Repeatedly syncs all node pairs, merges active node sets and advances any
     * leaving process until the whole network is stable.
     */
    private void settle(List<MockNode> all) {
        for (int round = 0; round < 200; round++) {
            boolean changed = false;
            // a node that is being left never advances its own set, so its active
            // node set must not be merged back in
            var noMerge = leavingTargets(all);
            for (MockNode a : all) {
                for (MockNode b : all) {
                    if (a == b)
                        continue;
                    a.syncFrom(b, 100_000, 10_000_000);
                    if (!noMerge.contains(a.nodeId) && !noMerge.contains(b.nodeId))
                        changed |= a.mergeActiveNodeSet(b.activeNodeSet);
                }
            }
            for (MockNode node : all) {
                if (!noMerge.contains(node.nodeId))
                    changed |= node.updateLeaving();
            }
            if (!changed)
                return;
        }
        throw new AssertionError("randomized network did not settle");
    }

    private Set<String> leavingTargets(List<MockNode> all) {
        var result = new HashSet<String>();
        for (MockNode node : all) {
            var process = node.activeNodeSet.leavingProcess;
            if (process != null)
                result.add(process.leavingNodeId);
        }
        return result;
    }

    private boolean isLeavingTarget(MockNode node, List<MockNode> all) {
        return all.stream().anyMatch(other -> {
            var process = other.activeNodeSet.leavingProcess;
            return process != null && process.leavingNodeId.equals(node.nodeId);
        });
    }

    /** Nodes that some other node still considers active. */
    private List<MockNode> liveNodes(List<MockNode> all) {
        var result = new ArrayList<MockNode>();
        if (all.size() == 1) {
            result.add(all.get(0));
            return result;
        }
        for (MockNode node : all) {
            boolean activeElsewhere = all.stream()
                    .anyMatch(other -> other != node && other.activeNodeSet.nodes.contains(node.nodeId));
            if (activeElsewhere)
                result.add(node);
        }
        if (result.isEmpty()) {
            var active = new HashSet<String>();
            all.forEach(node -> active.addAll(node.activeNodeSet.nodes));
            for (MockNode node : all)
                if (active.contains(node.nodeId))
                    result.add(node);
        }
        return result;
    }

    private void assertConverged(List<MockNode> all) {
        var live = liveNodes(all);
        if (live.size() < 2)
            return;
        var reference = live.get(0);
        assertNull("leaving process must be completed after settling",
                reference.activeNodeSet.leavingProcess);
        var referenceNodes = new TreeSet<>(reference.activeNodeSet.nodes);
        var referenceOperations = operationMap(reference);
        for (MockNode node : live) {
            assertEquals("active node sets must converge", referenceNodes, node.activeNodeSet.nodes);
            assertEquals("operations must converge", referenceOperations, operationMap(node));
        }
    }

    private Map<OpId, String> operationMap(MockNode node) {
        var map = new HashMap<OpId, String>();
        node.allOperations().forEach((id, op) -> map.put(id, op.payload));
        return map;
    }

    private Set<String> stagingIds(MockNode node) {
        var ids = new HashSet<String>();
        node.stagingTree.keySet().forEach(id -> ids.add(id.toString()));
        return ids;
    }
}
