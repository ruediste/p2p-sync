import { create, clone } from "@bufbuild/protobuf";
import { NodeMapSchema, NodeMapEntrySchema, type NodeMap as NodeMapProto, type NodeMapEntry as NodeMapEntryProto } from "../gen/clock_pb.js";
import { VectorClock } from "./vector-clock.js";

export class NodeMap {
  private readonly proto: NodeMapProto;

  constructor(proto?: NodeMapProto) {
    this.proto = proto ?? create(NodeMapSchema);
  }

  static fromProto(proto: NodeMapProto): NodeMap {
    return new NodeMap(proto);
  }

  toProto(): NodeMapProto {
    return this.proto;
  }

  get entries(): NodeMapEntryProto[] {
    return this.proto.entries;
  }

  get nextNodeNr(): number {
    return this.proto.nextNodeNr;
  }

  get clock(): VectorClock {
    return VectorClock.fromProto(this.proto.clock!);
  }

  join(nodeId: Uint8Array, clock: VectorClock): { nodeNr: number; nodeMap: NodeMap } {
    const nodeNr = this.proto.nextNodeNr;
    const newProto = clone(NodeMapSchema, this.proto);
    
    const entry = create(NodeMapEntrySchema, {
      nodeNr,
      nodeId,
      clock: clock.toProto(),
      leaving: false,
    });
    
    newProto.entries.push(entry);
    newProto.nextNodeNr = nodeNr + 1;
    newProto.clock = clock.toProto();
    
    return { nodeNr, nodeMap: new NodeMap(newProto) };
  }

  markLeaving(nodeNr: number, clock: VectorClock): NodeMap {
    const newProto = clone(NodeMapSchema, this.proto);
    const entry = newProto.entries.find((e) => e.nodeNr === nodeNr);
    if (entry) {
      entry.leaving = true;
      entry.clock = clock.toProto();
    }
    newProto.clock = clock.toProto();
    return new NodeMap(newProto);
  }

  tryCompleteLeave(activeClocks: VectorClock[]): NodeMap {
    const newProto = clone(NodeMapSchema, this.proto);
    newProto.entries = newProto.entries.filter((entry) => {
      if (!entry.leaving) return true;
      
      const entryClock = VectorClock.fromProto(entry.clock!);
      // Leaving completes when all active nodes have seen the leaving mark
      // i.e., entryClock <= activeClock for all activeClocks
      return !activeClocks.every((activeClock) => {
        const cmp = entryClock.compare(activeClock);
        return cmp === "before" || cmp === "equal";
      });
    });
    return new NodeMap(newProto);
  }

  merge(other: NodeMap, localClock: VectorClock, otherClock: VectorClock): NodeMap {
    const newProto = clone(NodeMapSchema, this.proto);
    
    // 1. Detect deleted entries
    // if for a given nodeNr there is only an entry e in a or b 
    // and e.clock<=a.clock && e.clock<=b.clock, the entry has been seen by both 
    // but removed from one.
    
    const allNodeNrs = new Set([
      ...this.proto.entries.map(e => e.nodeNr),
      ...other.proto.entries.map(e => e.nodeNr)
    ]);

    const resultEntries: NodeMapEntryProto[] = [];

    for (const nodeNr of allNodeNrs) {
      const entryA = this.proto.entries.find(e => e.nodeNr === nodeNr);
      const entryB = other.proto.entries.find(e => e.nodeNr === nodeNr);

      if (entryA && entryB) {
        // Both have it, merge entry state
        const mergedEntry = clone(NodeMapEntrySchema, entryA);
        const clockA = VectorClock.fromProto(entryA.clock!);
        const clockB = VectorClock.fromProto(entryB.clock!);
        
        const cmp = clockA.compare(clockB);
        if (cmp === "before") {
          mergedEntry.clock = entryB.clock;
          mergedEntry.leaving = entryB.leaving;
          mergedEntry.nodeId = entryB.nodeId;
        } else if (cmp === "concurrent") {
          // For now, take max clock and leaving if either is leaving
          mergedEntry.clock = clockA.merge(clockB).toProto();
          mergedEntry.leaving = entryA.leaving || entryB.leaving;
        }
        resultEntries.push(mergedEntry);
      } else if (entryA) {
        const clockA = VectorClock.fromProto(entryA.clock!);
        const inB = clockA.compare(otherClock);
        // If not in B and B should have seen it, it was deleted in B
        if (!(inB === "before" || inB === "equal")) {
           resultEntries.push(clone(NodeMapEntrySchema, entryA));
        }
      } else if (entryB) {
        const clockB = VectorClock.fromProto(entryB.clock!);
        const inA = clockB.compare(localClock);
        // If not in A and A should have seen it, it was deleted in A
        if (!(inA === "before" || inA === "equal")) {
           resultEntries.push(clone(NodeMapEntrySchema, entryB));
        }
      }
    }

    newProto.entries = resultEntries;
    newProto.nextNodeNr = Math.max(this.proto.nextNodeNr, other.nextNodeNr);
    newProto.clock = localClock.merge(otherClock).toProto();

    return new NodeMap(newProto);
  }
}
