import { fromBinary } from "@bufbuild/protobuf";
import fs from "node:fs/promises";
import { UserKeysSchema } from "./gen/user_pb";
import { loadOrCreateUserKeys } from "./userKeysManagement";

async function test() {
  const filePath = "userKeys.proto";
  try {
    // 1. Ensure file doesn't exist
    try {
      await fs.unlink(filePath);
    } catch {}

    console.log("Testing creation of new keys...");
    const keys1 = await loadOrCreateUserKeys();
    if (!keys1.userPublicKey || !keys1.storageKey) {
      throw new Error("Keys should be generated");
    }
    console.log("Keys created successfully.");

    // 2. Check if file was created
    const stats = await fs.stat(filePath);
    if (!stats.isFile()) {
      throw new Error("File userKeys.proto should exist");
    }
    console.log("File userKeys.proto created.");

    // 3. Load again and compare
    console.log("Testing loading existing keys...");
    const keys2 = await loadOrCreateUserKeys();

    const data = await fs.readFile(filePath);
    const keysFromFile = fromBinary(UserKeysSchema, data);

    // Deep equal check (simple check for bytes)
    if (keys1.userPublicKey.toString() !== keys2.userPublicKey.toString()) {
      throw new Error("Loaded keys should match created keys");
    }
    if (
      keys2.userPublicKey.toString() !== keysFromFile.userPublicKey.toString()
    ) {
      throw new Error("Keys in file should match loaded keys");
    }
    console.log("Keys loaded and verified successfully.");

    // Cleanup
    await fs.unlink(filePath);
    console.log("Test passed!");
  } catch (error) {
    console.error("Test failed:", error);
    process.exit(1);
  }
}

test();
