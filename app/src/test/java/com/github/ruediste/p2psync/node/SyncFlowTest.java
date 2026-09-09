package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.node.Node.Network;

public class SyncFlowTest {

    private Network network;
    private Node nodeA;
    private Node nodeB;
    private NodeUserHandle handleA;

    @Before
    public void before() {
        network = new Network();
        nodeA = new Node(network);
        nodeB = new Node(network);
        handleA = nodeA.createNewUser();
    }

    @Test
    public void sync_no_conflict() {
        var handleB = nodeB.join(handleA.data.userId(), handleA.data.userKey);

        handleA.modify(root -> root.userStatusMessage = "foo");
        handleB.syncFrom(nodeA.peerId);
        assertEquals("foo", handleB.read(r -> r.userStatusMessage));

        handleB.modify(root -> root.userStatusMessage = "bar");
        handleA.syncFrom(nodeB.peerId);
        assertEquals("bar", handleA.read(r -> r.userStatusMessage));

    }
}
