import { create, toBinary } from "@bufbuild/protobuf";
import { NodeTrustSchema, NodeTrustSetSchema } from "../gen/storage_pb.js";
import { VectorClock } from "../clock/vector-clock.js";
import { toString as uint8ArrayToString } from "uint8arrays/to-string";

export interface NodeTrustEntry {
  nodeId: Uint8Array;
  reliability: number;
  clock: VectorClock;
}

export class NodeTrustSet {
  constructor(public readonly entries: Map<string, NodeTrustEntry> = new Map()) {}

  addOrUpdate(nodeId: Uint8Array, reliability: number, clock: VectorClock) {
    const key = uint8ArrayToString(nodeId, "base64");
    const existing = this.entries.get(key);
    if (
      !existing ||
      clock.compare(existing.clock) === "after" ||
      clock.compare(existing.clock) === "concurrent"
    ) {
      // Last-writer-wins based on clock
      this.entries.set(key, { nodeId, reliability, clock });
    }
  }

  merge(other: NodeTrustSet) {
    for (const entry of other.entries.values()) {
      this.addOrUpdate(entry.nodeId, entry.reliability, entry.clock);
    }
  }

  toBinary(): Uint8Array {
    const message = create(NodeTrustSetSchema, {
      entries: Array.from(this.entries.values()).map((e) =>
        create(NodeTrustSchema, {
          nodeId: e.nodeId,
          reliability: e.reliability,
          clock: e.clock.toProto(),
        }),
      ),
    });
    return toBinary(NodeTrustSetSchema, message);
  }

  static fromProto(proto: any): NodeTrustSet {
    const set = new NodeTrustSet();
    for (const entry of proto.entries) {
      set.addOrUpdate(
        entry.nodeId,
        entry.reliability,
        VectorClock.fromProto(entry.clock),
      );
    }
    return set;
  }
}
