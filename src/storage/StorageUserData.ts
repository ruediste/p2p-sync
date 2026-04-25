import { create, fromBinary, toBinary } from "@bufbuild/protobuf";
import { VectorClock } from "../clock/vector-clock.js";
import { signData, verifySignature } from "../crypto/index.js";
import {
  NodeTrustSetSchema,
  StorageUserDataSchema,
  type NodeConfiguration,
} from "../gen/storage_pb.js";
import { NodeTrustSet } from "./NodeTrustSet.js";

export class StorageUserData {
  constructor(
    public readonly userId: Uint8Array,
    public readonly clock: VectorClock,
    public readonly shardIds: Uint8Array[],
    public readonly trustSet: NodeTrustSet,
    public readonly encryptedPayload: Uint8Array,
    public readonly nodeConfigs: NodeConfiguration[] = [],
    public readonly signature?: Uint8Array,
  ) {}

  async sign(privateKey: Uint8Array): Promise<StorageUserData> {
    const dataToSign = this.toBinary(false); // Exclude signature
    const signature = await signData(privateKey, dataToSign);
    return new StorageUserData(
      this.userId,
      this.clock,
      this.shardIds,
      this.trustSet,
      this.encryptedPayload,
      this.nodeConfigs,
      signature,
    );
  }

  async verify(publicKey: Uint8Array): Promise<boolean> {
    if (!this.signature) return false;
    const dataToVerify = this.toBinary(false);
    return verifySignature(publicKey, dataToVerify, this.signature);
  }

  isNewerThan(other: StorageUserData): boolean {
    const comparison = this.clock.compare(other.clock);
    return comparison === "after";
  }

  toBinary(includeSignature: boolean = true): Uint8Array {
    const message = create(StorageUserDataSchema, {
      userId: this.userId,
      clock: this.clock.toProto(),
      shardIds: this.shardIds,
      trustSet: fromBinary(NodeTrustSetSchema, this.trustSet.toBinary()),
      encryptedPayload: this.encryptedPayload,
      nodeConfigs: this.nodeConfigs,
      signature: includeSignature ? this.signature : new Uint8Array(),
    });
    return toBinary(StorageUserDataSchema, message);
  }

  static fromProto(proto: any): StorageUserData {
    return new StorageUserData(
      proto.userId,
      VectorClock.fromProto(proto.clock),
      proto.shardIds,
      NodeTrustSet.fromProto(proto.trustSet),
      proto.encryptedPayload,
      proto.nodeConfigs,
      proto.signature,
    );
  }
}
