import { describe, expect, it } from "@jest/globals";
import { StorageBlock } from "./StorageBlock.js";
import { DataShard } from "./DataShard.js";
import { StorageUserData } from "./StorageUserData.js";
import { NodeTrustSet } from "./NodeTrustSet.js";
import { VectorClock } from "../clock/vector-clock.js";
import { generateUserKeys } from "../crypto/index.js";
import { CID } from "multiformats/cid";

describe("Storage Layer", () => {
  describe("StorageBlock", () => {
    it("should create a block with correct CID", async () => {
      const data = new TextEncoder().encode("hello world");
      const block = await StorageBlock.create(data);
      expect(block.data).toEqual(data);
      expect(block.cid).toBeDefined();
      // SHA-256 of "hello world" (raw) is bafkreifzjut3te2nhyekklss27nh3k72ysco7y32koao5eei66wof36n5e
      expect(block.cid.toString()).toBe("bafkreifzjut3te2nhyekklss27nh3k72ysco7y32koao5eei66wof36n5e");
    });
  });

  describe("DataShard", () => {
    it("should detect if it contains a block", async () => {
      const block1 = await StorageBlock.create(new Uint8Array([1]));
      const block2 = await StorageBlock.create(new Uint8Array([2]));
      const shard = new DataShard(new Uint8Array([1]), [block1.cid]);
      expect(shard.containsBlock(block1.cid)).toBe(true);
      expect(shard.containsBlock(block2.cid)).toBe(false);
    });

    it("should union with another shard", async () => {
        const block1 = await StorageBlock.create(new Uint8Array([1]));
        const block2 = await StorageBlock.create(new Uint8Array([2]));
        const shard1 = new DataShard(new Uint8Array([1]), [block1.cid]);
        const shard2 = new DataShard(new Uint8Array([1]), [block2.cid]);
        const combined = shard1.union(shard2);
        expect(combined.blockCids).toHaveLength(2);
        expect(combined.containsBlock(block1.cid)).toBe(true);
        expect(combined.containsBlock(block2.cid)).toBe(true);
    });
  });

  describe("NodeTrustSet", () => {
    it("should add and update entries based on clock", () => {
      const set = new NodeTrustSet();
      const nodeId = new Uint8Array([1, 2, 3]);
      const clock1 = new VectorClock().increment(1);
      const clock2 = clock1.increment(1);

      set.addOrUpdate(nodeId, 0.5, clock1);
      expect(set.entries.size).toBe(1);
      expect(Array.from(set.entries.values())[0].reliability).toBe(0.5);

      set.addOrUpdate(nodeId, 0.8, clock2);
      expect(set.entries.size).toBe(1);
      expect(Array.from(set.entries.values())[0].reliability).toBe(0.8);

      set.addOrUpdate(nodeId, 0.2, clock1); // Older clock should not update
      expect(Array.from(set.entries.values())[0].reliability).toBe(0.8);
    });
  });

  describe("StorageUserData", () => {
    it("should sign and verify", async () => {
      const keys = await generateUserKeys();
      const clock = new VectorClock().increment(1);
      const trustSet = new NodeTrustSet();
      const userData = new StorageUserData(
        keys.userPublicKey,
        clock,
        [],
        trustSet,
        new Uint8Array([1, 2, 3])
      );

      const signed = await userData.sign(keys.userPrivateKey!);
      expect(signed.signature).toBeDefined();

      const isValid = await signed.verify(keys.userPublicKey);
      expect(isValid).toBe(true);
    });

    it("should identify newer versions", () => {
        const keys = new Uint8Array([1]);
        const clock1 = new VectorClock().increment(1);
        const clock2 = clock1.increment(1);
        const trustSet = new NodeTrustSet();
        
        const v1 = new StorageUserData(keys, clock1, [], trustSet, new Uint8Array());
        const v2 = new StorageUserData(keys, clock2, [], trustSet, new Uint8Array());

        expect(v2.isNewerThan(v1)).toBe(true);
        expect(v1.isNewerThan(v2)).toBe(false);
    });
  });
});
