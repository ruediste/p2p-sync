import { NodeKeysSchema } from "../gen/node_pb.js";
import { create, fromBinary, toBinary } from "@bufbuild/protobuf";
import { generateKeyPair } from "@libp2p/crypto/keys";
import fs from "node:fs/promises";

const NODE_KEYS_FILE = "nodeKeys.proto";

async function createNodeKeys() {
  const nodeKeys = create(NodeKeysSchema);
  const keyPair = await generateKeyPair("Ed25519");
  nodeKeys.privateKey = keyPair.raw;
  nodeKeys.publicKey = keyPair.publicKey.raw;
  return nodeKeys;
}

export async function loadOrCreateNodeKeys() {
  try {
    const data = await fs.readFile(NODE_KEYS_FILE);
    return fromBinary(NodeKeysSchema, data);
  } catch (e) {
    if (e instanceof Error && (e as any).code === "ENOENT") {
      const nodeKeys = await createNodeKeys();
      await fs.writeFile(NODE_KEYS_FILE, toBinary(NodeKeysSchema, nodeKeys));
      return nodeKeys;
    }
    throw e;
  }
}
