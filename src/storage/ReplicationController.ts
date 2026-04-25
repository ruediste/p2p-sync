import { type Component, type Components } from "../components.js";
import { StorageRepository } from "./StorageRepository.js";
import { StorageUserData } from "./StorageUserData.js";

export class ReplicationController implements Component {
  private repository: StorageRepository;
  private running = false;
  private timer?: NodeJS.Timeout;

  constructor(
    private components: Pick<
      Components,
      "userController" | "storageRepository" | "nodeConfigController"
    >,
  ) {
    this.repository = components.storageRepository;
  }

  async initialize() {
    this.running = true;
    this.startLoop();
  }

  async stop() {
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
  }

  private startLoop() {
    if (!this.running) return;
    this.timer = setTimeout(async () => {
      try {
        await this.replicate();
      } catch (err) {
        console.error("Replication failed", err);
      } finally {
        this.startLoop();
      }
    }, 10000); // Every 10 seconds
  }

  async replicate() {
    const users = this.components.userController.getUsers();
    for (const user of users) {
      // Trigger WantUserDatas via UserNodeConnectionController
      // (This part needs integration with UserNodeConnectionController/Entry)
      // For now, assume UserNodeConnectionController handles the periodic triggering or we trigger it here
    }
  }

  async handleIncomingUserData(userData: StorageUserData) {
    // 1. Verify signature
    const isValid = await userData.verify(userData.userId);
    if (!isValid) return;

    // 2. Get existing roots
    const existingRoots = await this.repository.listUserDataRoots(
      userData.userId,
    );

    // 3. Check if new one is superseded by any existing
    let isSuperseded = false;
    for (const existing of existingRoots) {
      const comparison = userData.clock.compare(existing.clock);
      if (comparison === "before" || comparison === "equal") {
        isSuperseded = true;
        break;
      }
    }

    if (!isSuperseded) {
      // 4. Save the new root
      await this.repository.saveUserDataRoot(userData);

      // 5. Remove any existing roots superseded by the new one
      for (const existing of existingRoots) {
        if (existing.clock.compare(userData.clock) === "before") {
          await this.repository.deleteUserDataRoot(existing);
        }
      }

      // Check for new node configurations
      await this.components.nodeConfigController.refreshConfigurations();

      // After root is saved, trigger bitswap for missing shards/blocks
      // (Placeholder for bitswap integration)
    }
  }

  async getUserDataRoots(userId: Uint8Array): Promise<StorageUserData[]> {
    return this.repository.listUserDataRoots(userId);
  }
}
