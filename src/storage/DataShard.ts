import { create, toBinary } from "@bufbuild/protobuf";
import { DataShardSchema } from "../gen/storage_pb.js";
import { CID } from 'multiformats/cid'

export class DataShard {
  constructor(
    public readonly shardId: Uint8Array,
    public readonly blockCids: CID[]
  ) {}

  static create(shardId: Uint8Array, blockCids: CID[]): DataShard {
    return new DataShard(shardId, blockCids);
  }

  containsBlock(cid: CID): boolean {
    return this.blockCids.some(c => c.equals(cid));
  }

  /**
   * Merges another shard's blocks into this one (union).
   * Assumes shardId is the same.
   */
  union(other: DataShard): DataShard {
    const cids = new Map<string, CID>();
    this.blockCids.forEach(c => cids.set(c.toString(), c));
    other.blockCids.forEach(c => cids.set(c.toString(), c));
    return new DataShard(this.shardId, Array.from(cids.values()));
  }

  static fromProto(proto: any): DataShard {
    return new DataShard(
      proto.shardId,
      proto.blockCids.map((bytes: Uint8Array) => CID.decode(bytes)),
    );
  }

  toBinary(): Uint8Array {
    const message = create(DataShardSchema, {
      shardId: this.shardId,
      blockCids: this.blockCids.map(c => c.bytes)
    });
    return toBinary(DataShardSchema, message);
  }
}
