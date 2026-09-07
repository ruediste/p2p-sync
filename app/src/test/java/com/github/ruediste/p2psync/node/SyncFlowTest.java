package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Node.Network;

public class SyncFlowTest {

    private Network network;
    private Node nodeA;
    private Node nodeB;
    private PubKey userId;

    @Before
    public void before() {
        network = new Network();
        nodeA = new Node(network);
        nodeB = new Node(network);
        userId = nodeA.createNewUser();
    }

    @Test
    public void sync_no_conflict() {
        nodeB.join(userId);

        nodeA.modify(userId, root -> root.userStatusMessage = "foo");
        nodeB.syncFrom(nodeA.peerId, userId);
        assertEquals("foo", nodeB.read(userId, r -> r.userStatusMessage));

        nodeB.modify(userId, root -> root.userStatusMessage = "bar");
        nodeA.syncFrom(nodeB.peerId, userId);
        assertEquals("bar", nodeA.read(userId, r -> r.userStatusMessage));

    }
}
