import { clone, create } from "@bufbuild/protobuf";
import {
  VectorClockSchema,
  type VectorClock as VectorClockProto,
} from "../gen/clock_pb.js";

export type CompareResult = "before" | "after" | "equal" | "concurrent";

export class VectorClock {
  private readonly proto: VectorClockProto;

  constructor(proto?: VectorClockProto) {
    this.proto = proto ?? create(VectorClockSchema);
  }

  static fromProto(proto: VectorClockProto): VectorClock {
    return new VectorClock(proto);
  }

  toProto(): VectorClockProto {
    return this.proto;
  }

  get entries(): { [key: number]: number } {
    return this.proto.entries;
  }

  increment(nodeNr: number): VectorClock {
    const newProto = clone(VectorClockSchema, this.proto);
    const current = newProto.entries[nodeNr] ?? 0;
    newProto.entries[nodeNr] = current + 1;
    return new VectorClock(newProto);
  }

  merge(other: VectorClock): VectorClock {
    const newProto = clone(VectorClockSchema, this.proto);
    for (const [nodeNrStr, version] of Object.entries(other.entries)) {
      const nodeNr = parseInt(nodeNrStr);
      const current = newProto.entries[nodeNr] ?? 0;
      if (version > current) {
        newProto.entries[nodeNr] = version;
      }
    }
    // Flag is NOT merged, it's local to the node's current state
    return new VectorClock(newProto);
  }

  compare(other: VectorClock): CompareResult {
    let before = false;
    let after = false;

    const keys1 = Object.keys(this.entries).map((k) => parseInt(k));
    const keys2 = Object.keys(other.entries).map((k) => parseInt(k));
    const allKeys = new Set([...keys1, ...keys2]);

    for (const key of allKeys) {
      const v1 = this.entries[key] ?? 0;
      const v2 = other.entries[key] ?? 0;

      if (v1 < v2) before = true;
      if (v1 > v2) after = true;
    }

    if (before && after) return "concurrent";
    if (before) return "before";
    if (after) return "after";
    return "equal";
  }

  removeNode(nodeNr: number): VectorClock {
    const newProto = clone(VectorClockSchema, this.proto);
    delete newProto.entries[nodeNr];
    return new VectorClock(newProto);
  }
}
