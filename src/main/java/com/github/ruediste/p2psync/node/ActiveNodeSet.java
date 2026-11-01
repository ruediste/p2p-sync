package com.github.ruediste.p2psync.node;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;

final class ActiveNodeSet {
    enum Phase {
        A, B, I
    }

    static final class Version {
        final String nodeId;
        final long clock;

        Version(String nodeId, long clock) {
            this.nodeId = nodeId;
            this.clock = clock;
        }

        boolean sameAs(Version other) {
            return other != null && clock == other.clock && nodeId.equals(other.nodeId);
        }
    }

    static final class LeavingProcess {
        Phase phase;
        String processId;
        String leavingNodeId;
        long lastOperationClock;
        String lastOperationClockSourceNode;
        final Set<String> confirmedBy = new TreeSet<>();

        LeavingProcess copy() {
            var result = new LeavingProcess();
            result.phase = phase;
            result.processId = processId;
            result.leavingNodeId = leavingNodeId;
            result.lastOperationClock = lastOperationClock;
            result.lastOperationClockSourceNode = lastOperationClockSourceNode;
            result.confirmedBy.addAll(confirmedBy);
            return result;
        }
    }

    Version version;
    LeavingProcess leavingProcess;
    final Set<String> nodes = new TreeSet<>();
    final Map<String, Long> lastSeen = new HashMap<>();

    ActiveNodeSet copy() {
        var result = new ActiveNodeSet();
        result.version = version;
        result.leavingProcess = leavingProcess == null ? null : leavingProcess.copy();
        result.nodes.addAll(nodes);
        result.lastSeen.putAll(lastSeen);
        return result;
    }

    void setVersion(String nodeId, long clock) {
        version = new Version(nodeId, clock);
    }

    boolean mergeFrom(ActiveNodeSet other, String localNodeId, LongSupplier nextClock) {
        if (version != null && version.sameAs(other.version))
            return false;

        boolean changed = false;
        LeavingProcess mine = leavingProcess;
        LeavingProcess theirs = other.leavingProcess;
        if (theirs != null) {
            if (mine == null) {
                leavingProcess = theirs.copy();
                changed = true;
            } else if (!mine.processId.equals(theirs.processId)) {
                boolean newProcessSupersedesTombstone = mine.phase == Phase.A && theirs.phase == Phase.I;
                if (mine.phase == Phase.I && theirs.phase == Phase.A) {
                    leavingProcess = theirs.copy();
                    changed = true;
                } else if (!newProcessSupersedesTombstone && theirs.phase.ordinal() > mine.phase.ordinal()) {
                    leavingProcess = theirs.copy();
                    changed = true;
                } else if (theirs.phase == mine.phase && mine.processId.compareTo(theirs.processId) > 0) {
                    leavingProcess = theirs.copy();
                    changed = true;
                }
            } else if (theirs.phase.ordinal() > mine.phase.ordinal()) {
                leavingProcess = theirs.copy();
                changed = true;
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

        boolean strictSuperset = other.nodes.containsAll(nodes) && other.nodes.size() > nodes.size();
        LeavingProcess process = leavingProcess;
        boolean phaseB = process != null && process.phase == Phase.B;
        if (!phaseB && nodes.addAll(other.nodes))
            changed = true;
        lastSeen.putAll(other.lastSeen);
        if (process != null && process.phase != Phase.A && nodes.remove(process.leavingNodeId))
            changed = true;

        if (changed) {
            if (strictSuperset && other.version != null && nodes.equals(other.nodes)
                    && sameProcess(leavingProcess, other.leavingProcess))
                version = other.version;
            else
                setVersion(localNodeId, nextClock.getAsLong());
        }
        return changed;
    }

    private static boolean sameProcess(LeavingProcess left, LeavingProcess right) {
        if (left == null || right == null)
            return left == right;
        return left.phase == right.phase
                && Objects.equals(left.processId, right.processId)
                && Objects.equals(left.leavingNodeId, right.leavingNodeId)
                && left.lastOperationClock == right.lastOperationClock
                && Objects.equals(left.lastOperationClockSourceNode, right.lastOperationClockSourceNode)
                && left.confirmedBy.equals(right.confirmedBy);
    }

    boolean startLeaving(String leavingNodeId, String processId, Map<String, Long> maxOperationClock,
            String localNodeId, LongSupplier nextClock) {
        var existing = leavingProcess;
        if (existing != null && existing.phase != Phase.I && existing.processId.compareTo(processId) <= 0)
            return false;
        var process = new LeavingProcess();
        process.phase = Phase.A;
        process.processId = processId;
        process.leavingNodeId = leavingNodeId;
        process.lastOperationClock = maxOperationClock.getOrDefault(leavingNodeId, 0L);
        process.lastOperationClockSourceNode = leavingNodeId;
        leavingProcess = process;
        setVersion(localNodeId, nextClock.getAsLong());
        return true;
    }

    boolean updateLeaving(String localNodeId, Map<String, Long> maxOperationClock, LongSupplier nextClock) {
        var process = leavingProcess;
        if (process == null || process.leavingNodeId.equals(localNodeId))
            return false;
        if (process.phase == Phase.I)
            return false;

        boolean changed = false;
        if (!nodes.contains(process.lastOperationClockSourceNode)) {
            process.lastOperationClock = maxOperationClock.getOrDefault(process.leavingNodeId, 0L);
            process.lastOperationClockSourceNode = localNodeId;
            process.confirmedBy.clear();
            changed = true;
        }

        long localClock = maxOperationClock.getOrDefault(process.leavingNodeId, 0L);
        if (localClock < process.lastOperationClock) {
            if (changed)
                setVersion(localNodeId, nextClock.getAsLong());
            return changed;
        }
        if (localClock > process.lastOperationClock) {
            process.lastOperationClock = localClock;
            process.confirmedBy.clear();
            changed = true;
        }
        if (process.confirmedBy.add(localNodeId))
            changed = true;

        var required = new TreeSet<>(nodes);
        required.remove(process.leavingNodeId);
        if (process.confirmedBy.containsAll(required)) {
            if (process.phase == Phase.A) {
                nodes.remove(process.leavingNodeId);
                process.phase = Phase.B;
                process.confirmedBy.clear();
                changed = true;
            } else if (process.phase == Phase.B) {
                process.phase = Phase.I;
                process.confirmedBy.clear();
                changed = true;
            }
        }
        if (changed)
            setVersion(localNodeId, nextClock.getAsLong());
        return changed;
    }
}