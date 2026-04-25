import { loadOrCreateNodeKeys } from "./NodeKeysManagement.js";
import { loadOrCreateUserKeys } from "../user/UserKeysManagement.js";
import { StorageRepository } from "../storage/StorageRepository.js";
import { StorageUserData } from "../storage/StorageUserData.js";
import { VectorClock } from "../clock/vector-clock.js";
import { NodeTrustSet } from "../storage/NodeTrustSet.js";

export class BootstrapService {
  constructor(private repository: StorageRepository) {}

  async bootstrap() {
    const nodeKeys = await loadOrCreateNodeKeys();
    const userKeys = await loadOrCreateUserKeys();

    // Check if we already have a root for this user
    const existing = await this.repository.listUserDataRoots(
      userKeys.userPublicKey,
    );
    if (existing.length > 0) return;

    // Create initial StorageUserData
    const clock = new VectorClock();
    const trustSet = new NodeTrustSet();
    // Trust self with reliability 1.0
    // Node ID in this protocol is usually the peerId or node management public key.
    // Using node management public key as per requirements.
    trustSet.addOrUpdate(nodeKeys.publicKey, 1.0, clock);

    const userData = new StorageUserData(
      userKeys.userPublicKey,
      clock,
      [],
      trustSet,
      new Uint8Array(), // empty initial payload
    );

    const signed = await userData.sign(userKeys.userPrivateKey!);
    await this.repository.saveUserDataRoot(signed);
    await this.repository.registerUser(signed.userId);
  }
}
