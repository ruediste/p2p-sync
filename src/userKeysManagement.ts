import { create, fromBinary, toBinary } from "@bufbuild/protobuf";
import { generateKeyPair } from "@libp2p/crypto/keys";
import webcrypto from "@libp2p/crypto/webcrypto";
import fs from "node:fs/promises";
import { UserKeysSchema } from "./gen/user_pb";

const USER_KEYS_FILE = "userKeys.proto";

const subtle = webcrypto.get().subtle;

async function createUserKeys() {
  const userKeys = create(UserKeysSchema);
  async function generateAesGcmKey() {
    return new Uint8Array(
      await subtle.exportKey(
        "raw",
        await subtle.generateKey(
          {
            name: "AES-GCM",
            length: 256,
          },
          true,
          ["encrypt", "decrypt"],
        ),
      ),
    );
  }
  userKeys.storageKey = await generateAesGcmKey();
  userKeys.linkKey = await generateAesGcmKey();
  userKeys.dataKey = await generateAesGcmKey();

  const userKey = await generateKeyPair("Ed25519");
  userKeys.userPrivateKey = userKey.raw;
  userKeys.userPublicKey = userKey.publicKey.raw;
  return userKeys;
}

export async function loadOrCreateUserKeys() {
  try {
    const data = await fs.readFile(USER_KEYS_FILE);
    return fromBinary(UserKeysSchema, data);
  } catch (e) {
    if (e instanceof Error && (e as any).code === "ENOENT") {
      const userKeys = await createUserKeys();
      await fs.writeFile(USER_KEYS_FILE, toBinary(UserKeysSchema, userKeys));
      return userKeys;
    }
    throw e;
  }
}
