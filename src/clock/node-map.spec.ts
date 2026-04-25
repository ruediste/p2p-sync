import { NodeMap } from "./node-map.js";
import { VectorClock } from "./vector-clock.js";

describe("NodeMap", () => {
  const nodeId1 = new Uint8Array([1]);
  const nodeId2 = new Uint8Array([2]);

  it("should allow a node to join", () => {
    let nodeMap = new NodeMap();
    const clock = new VectorClock().increment(0);
    const { nodeNr, nodeMap: newNodeMap } = nodeMap.join(nodeId1, clock);

    expect(nodeNr).toBe(0);
    expect(newNodeMap.entries.length).toBe(1);
    expect(newNodeMap.entries[0].nodeId).toEqual(nodeId1);
    expect(newNodeMap.nextNodeNr).toBe(1);
  });

  it("should mark a node as leaving", () => {
    let nodeMap = new NodeMap();
    const clock1 = new VectorClock().increment(0);
    const { nodeNr, nodeMap: nodeMap1 } = nodeMap.join(nodeId1, clock1);

    const clock2 = clock1.increment(nodeNr);
    const nodeMap2 = nodeMap1.markLeaving(nodeNr, clock2);

    expect(nodeMap2.entries[0].leaving).toBe(true);
  });

  it("should complete leave when all active nodes have seen it", () => {
    let nodeMap = new NodeMap();
    const clock0 = new VectorClock().increment(0);
    const { nodeNr: nr0, nodeMap: nm0 } = nodeMap.join(nodeId1, clock0);
    const { nodeNr: nr1, nodeMap: nm1 } = nm0.join(nodeId2, clock0);

    // Node 0 starts leaving
    const clock0Leave = clock0.increment(nr0);
    const nmLeaving = nm1.markLeaving(nr0, clock0Leave);

    // Node 1 has NOT seen it yet (active clock is still old)
    const activeClocksBefore = [clock0]; 
    const nmStillLeaving = nmLeaving.tryCompleteLeave(activeClocksBefore);
    expect(nmStillLeaving.entries.length).toBe(2);

    // Node 1 sees it
    const clock1SawIt = clock0Leave.increment(nr1);
    const activeClocksAfter = [clock1SawIt];
    const nmCompleted = nmLeaving.tryCompleteLeave(activeClocksAfter);
    expect(nmCompleted.entries.length).toBe(1);
    expect(nmCompleted.entries[0].nodeNr).toBe(nr1);
  });

  it("should merge two node maps and detect deleted entries", () => {
    const clockBase = new VectorClock().increment(0);
    const { nodeNr: nr0, nodeMap: nmA_0 } = new NodeMap().join(nodeId1, clockBase);
    const { nodeNr: nr1, nodeMap: nmA_1 } = nmA_0.join(nodeId2, clockBase);

    // Now nmA_1 has nodes 0 and 1.
    // Sync to B
    let nmB = nmA_1;

    // A removes node 0 (complete leave)
    const clockA_Leave = clockBase.increment(nr0);
    const nmA_Leaving = nmA_1.markLeaving(nr0, clockA_Leave);
    const clockA_Final = clockA_Leave.increment(nr1); // Node 1 sees it
    const nmA_Final = nmA_Leaving.tryCompleteLeave([clockA_Final]);

    expect(nmA_Final.entries.length).toBe(1);

    // B has not seen any changes yet.
    // Merge A into B. 
    // B should see that node 0 was deleted because clock(node 0) <= clockA but node 0 is missing in A.
    const mergedB = nmB.merge(nmA_Final, clockBase, clockA_Final);
    expect(mergedB.entries.length).toBe(1);
    expect(mergedB.entries[0].nodeNr).toBe(nr1);
  });
});
