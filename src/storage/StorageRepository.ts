import { fromBinary } from "@bufbuild/protobuf";
import { Key } from "interface-datastore";
import { CID } from "multiformats/cid";
import { sha256 } from "multiformats/hashes/sha2";
import { toString as uint8ArrayToString } from "uint8arrays/to-string";
import { type Component, type Components } from "../components.js";
import { StorageUserDataSchema } from "../gen/storage_pb.js";
import { StorageBlock } from "./StorageBlock.js";
import { StorageUserData } from "./StorageUserData.js";

export class StorageRepository implements Component {
  constructor(
    private components: Pick<Components, "dataStore" | "blockStore">,
  ) {}

  async initialize() {}

  async saveNodeKeys(publicKey: Uint8Array, privateKey: Uint8Array) {
    const batch = this.components.dataStore.batch();
    batch.put(new Key("/node/keys/public"), publicKey);
    batch.put(new Key("/node/keys/private"), privateKey);
    await batch.commit();
  }

  async saveUserDataRoot(userData: StorageUserData) {
    const userIdStr = uint8ArrayToString(userData.userId, "base64url");
    const binary = userData.toBinary();
    const hash = await sha256.digest(binary);
    const hashStr = uint8ArrayToString(hash.bytes, "base64url");
    const key = new Key(`/storage/user/${userIdStr}/root/${hashStr}`);

    await this.components.dataStore.put(key, binary);
  }

  async listUserDataRoots(userId: Uint8Array): Promise<StorageUserData[]> {
    const userIdStr = uint8ArrayToString(userId, "base64url");
    const prefix = `/storage/user/${userIdStr}/root/`;
    const results: StorageUserData[] = [];
    for await (const { value } of this.components.dataStore.query({
      prefix,
    })) {
      try {
        const proto = fromBinary(StorageUserDataSchema, value);
        results.push(StorageUserData.fromProto(proto));
      } catch (e) {
        console.error("Failed to parse user data root", e);
      }
    }
    return results;
  }

  async deleteUserDataRoot(userData: StorageUserData) {
    const userIdStr = uint8ArrayToString(userData.userId, "base64url");
    const binary = userData.toBinary();
    const hash = await sha256.digest(binary);
    const hashStr = uint8ArrayToString(hash.bytes, "base64url");
    const key = new Key(`/storage/user/${userIdStr}/root/${hashStr}`);
    await this.components.dataStore.delete(key);
  }

  // --- Blocks (Blocks are stored in blockStore) ---
  async saveBlock(block: StorageBlock) {
    await this.components.blockStore.put(block.cid, block.data);
  }

  async getBlock(cid: CID): Promise<Uint8Array | null> {
    try {
      const parts = [];
      for await (const chunk of this.components.blockStore.get(cid)) {
        parts.push(chunk);
      }

      let size = 0;
      for (const part of parts) {
        size += part.byteLength;
      }

      const res = new Uint8Array(size);
      let offset = 0;
      for (const part of parts) {
        res.set(part, offset);
        offset += part.byteLength;
      }

      return res;
    } catch (e: any) {
      if (e.code === "ERR_NOT_FOUND") return null;
      throw e;
    }
  }
}
