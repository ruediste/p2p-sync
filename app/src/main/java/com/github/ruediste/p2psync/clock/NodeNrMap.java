package com.github.ruediste.p2psync.clock;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.node.JoinId;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * The bidirectional map between {@code nodeNr}s and node ids embedded in the
 * {@code DataUserRoot}, see wiki section "Dynamic Vector Clocks". Allows vector
 * clocks to be represented compactly using small integer node numbers, while
 * nodes can join and leave at any time.
 */
public final class NodeNrMap {
    private static SecureRandom random = new SecureRandom();
    private Map<Integer, NodeNrEntry> entries;
    private VectorClock clock;
    private int nextNodeNr;

    public NodeNrMap() {
        this(new HashMap<>(), VectorClock.empty(), 0);
    }

    /**
     * Creates a map from the given immutable snapshot; used to reconstruct
     * persisted roots.
     */
    public NodeNrMap(Map<Integer, NodeNrEntry> entries, VectorClock clock, int nextNodeNr) {
        this.entries = entries;
        this.clock = clock;
        this.nextNodeNr = nextNodeNr;
    }

    public static NodeNrMap from(Sync.NodeNrMap proto) {
        Map<Integer, NodeNrEntry> entries = new HashMap<>();
        for (Sync.NodeNrEntry entryProto : proto.getEntriesList()) {
            entries.put(entryProto.getNodeNr(), NodeNrEntry.from(entryProto));
        }
        return new NodeNrMap(entries, VectorClock.from(proto.getClock()), proto.getNextNodeNr());
    }

    public Sync.NodeNrMap toProto() {
        return Sync.NodeNrMap.newBuilder()
                .setNextNodeNr(nextNodeNr)
                .setClock(clock.toProto())
                .addAllEntries(entries.values().stream().map(NodeNrEntry::toProto).collect(Collectors.toList()))
                .build();
    }

    /** Clock of the map itself. */
    public VectorClock getClock() {
        return clock;
    }

    public int getNextNodeNr() {
        return nextNodeNr;
    }

    public NodeNrEntry get(int nr) {
        return entries.get(nr);
    }

    /** The node number assigned to {@code peerId}, or {@code null} if unknown. */
    public Integer nrOf(PeerId peerId) {
        for (Map.Entry<Integer, NodeNrEntry> e : entries.entrySet()) {
            if (e.getValue().peerId.equals(peerId.getBytes())) {
                return e.getKey();
            }
        }
        return null;
    }

    public Map<Integer, NodeNrEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /** Node numbers of all active (non-leaving) nodes. */
    public List<Integer> activeNrs() {
        List<Integer> result = new ArrayList<>();
        entries.forEach((nr, e) -> {
            if (!e.leavingSince.isPresent()) {
                result.add(nr);
            }
        });
        return result;
    }

    public JoinId join(PeerId peerId) {
        var joinId = new JoinId(new byte[16]);
        random.nextBytes(joinId.bytes());
        var nr = nextNodeNr++;
        var entry = new NodeNrEntry(nr, joinId, peerId, Optional.empty());
        entries.put(nr, entry);
        return joinId;
    }

    /**
     * Marks the entry {@code nr} as leaving (wiki: "the leaving flag of the
     * respective nodeNr map entry is set (and the clock updated)"). The entry clock
     * becomes the maximum of its current clock and the given modification clock.
     */
    public void markLeaving(int nr, VectorClock modificationClock) {
        NodeNrEntry entry = entries.get(nr);
        if (entry == null) {
            throw new IllegalArgumentException("no entry for nodeNr " + nr);
        }
        if (entry.leavingSince.isPresent()) {
            return;
        }
        entry.leavingSince = Optional.of(
                modificationClock.clone());
    }

    /**
     * True if the leaving process of {@code nr} is complete (wiki: "the vector
     * clock of a leaving entry is less than the clock of the DataUserRoot for all
     * active (non-leaving) nodes"), i.e. the entry clock is dominated by the root
     * clock for every component of an active (non-leaving) node. The leaving
     * node's own component is ignored; this guarantees completion without
     * interaction of the node which left.
     */
    public boolean isLeaveComplete(int nr, VectorClock rootClock) {
        NodeNrEntry entry = entries.get(nr);
        if (entry == null) {
            return false;
        }
        for (int activeNr : activeNrs()) {
            if (entry.leavingSince.get().get(activeNr) > rootClock.get(activeNr)) {
                return false;
            }
        }
        return true;
    }

    /** Node numbers of {@code leaving} entries whose leave process is complete. */
    public List<Integer> completedLeaves(VectorClock rootClock) {
        List<Integer> result = new ArrayList<>();
        entries.forEach((nr, e) -> {
            if (e.leavingSince.isPresent() && isLeaveComplete(nr, rootClock)) {
                result.add(nr);
            }
        });
        return result;
    }

    /**
     * Returns a map with all leaving entries whose leave process is complete
     * (w.r.t. {@code rootClock}) removed. From this point on, entries for the
     * unknown node numbers are discarded when updating vector clocks.
     */
    public NodeNrMap withLeavesCompleted(VectorClock rootClock) {
        List<Integer> completed = completedLeaves(rootClock);
        if (completed.isEmpty()) {
            return this;
        }
        TreeMap<Integer, NodeNrEntry> newEntries = new TreeMap<>(entries);
        completed.forEach(newEntries::remove);
        return new NodeNrMap(newEntries, clock, nextNodeNr);
    }

    @Override
    public String toString() {
        return "NodeNrMap{entries=" + entries + ", clock=" + clock + ", nextNodeNr=" + nextNodeNr + "}";
    }

    public Optional<Integer> getNr(JoinId joinId) {
        return entries.values().stream().filter(x -> x.joinId.equals(joinId)).findAny().map(e -> e.nodeNr);
    }

    public static NodeNrMap from(byte[] bytes) {
        try {
            return from(Sync.NodeNrMap.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
