import { VectorClock } from "@/clock/vector-clock.js";
import { type Component, type Components } from "@/components.js";
import { generateUserKeys, signData } from "@/crypto/index.js";
import {
  type NodeConfiguration,
  NodeConfigurationSchema,
  type NodeUserConfiguration,
  NodeUserConfigurationSchema,
} from "@/gen/storage_pb.js";
import { type UserKeys } from "@/gen/user_pb.js";
import { NodeTrustSet } from "@/storage/NodeTrustSet.js";
import { StorageUserData } from "@/storage/StorageUserData.js";
import { create, fromBinary, toBinary } from "@bufbuild/protobuf";
import { equals as uint8ArrayEquals } from "uint8arrays/equals";
import { loadOrCreateNodeKeys } from "./NodeKeysManagement.js";

export interface MergedNodeUserConfig {
  userId: Uint8Array;
  storageQuota?: bigint;
  storageKey?: Uint8Array;
  linkKey?: Uint8Array;
  dataKey?: Uint8Array;
  userPrivateKey?: Uint8Array;
}

export class NodeConfigController implements Component {
  private mergedConfigs: Map<string, MergedNodeUserConfig> = new Map();
  private nodePrivateKey?: Uint8Array;
  private nodePublicKey?: Uint8Array;

  constructor(private components: Components) {}

  async initialize() {
    const keys = await loadOrCreateNodeKeys();
    this.nodePrivateKey = keys.privateKey;
    this.nodePublicKey = keys.publicKey;

    // Load persisted configs
    for (const user of this.components.userController.getUsers()) {
      const data = await this.components.storageRepository.getMergedNodeConfig(
        user.publicKey,
      );
      if (data) {
        try {
          const config = fromBinary(NodeUserConfigurationSchema, data);
          this.mergedConfigs.set(
            Buffer.from(user.publicKey).toString("base64"),
            {
              userId: config.userId,
              storageQuota: config.storageQuota,
              storageKey: config.storageKey,
              linkKey: config.linkKey,
              dataKey: config.dataKey,
              userPrivateKey: config.userPrivateKey,
            },
          );
        } catch (e) {
          console.error("Failed to load persisted config for user", e);
        }
      }
    }

    await this.refreshConfigurations();
  }

  async getConfigForUser(
    userId: Uint8Array,
  ): Promise<MergedNodeUserConfig | null> {
    return (
      this.mergedConfigs.get(Buffer.from(userId).toString("base64")) || null
    );
  }

  async bootstrapNewUser(): Promise<UserKeys> {
    // 1. Generate user keys
    const userKeys = await generateUserKeys();

    // 2. Create NodeTrustSet and add current node
    const trustSet = new NodeTrustSet();
    const initialClock = new VectorClock();
    // Reliability 1.0 for the current node
    trustSet.addOrUpdate(this.nodePublicKey!, 1.0, initialClock);

    // 3. Create NodeUserConfiguration for current node
    const nodeUserConfig = create(NodeUserConfigurationSchema, {
      userId: userKeys.userPublicKey,
      storageKey: userKeys.storageKey,
      linkKey: userKeys.linkKey,
      dataKey: userKeys.dataKey,
      userPrivateKey: userKeys.userPrivateKey,
    });

    // 4. Create NodeConfiguration for current node
    const nodeConfig = create(NodeConfigurationSchema, {
      nodeId: this.nodePublicKey!,
      userConfigs: [nodeUserConfig],
    });

    // Sign nodeConfig with user's private key (management key)
    const nodeConfigBinary = toBinary(NodeConfigurationSchema, {
      ...nodeConfig,
      signature: new Uint8Array(),
    });
    nodeConfig.signature = await signData(
      userKeys.userPrivateKey!,
      nodeConfigBinary,
    );

    // 5. Create StorageUserData
    const storageUserData = new StorageUserData(
      userKeys.userPublicKey,
      initialClock.increment(0), // Initial modification by node 0
      [],
      trustSet,
      new Uint8Array(), // Empty encrypted payload for now
      [nodeConfig],
    );

    // 6. Sign and Save
    const signedUserData = await storageUserData.sign(userKeys.userPrivateKey!);

    // Notify UserController
    this.components.userController.addUser(userKeys.userPublicKey);

    // Save to repository
    await this.components.storageRepository.saveUserDataRoot(signedUserData);

    // Refresh configurations to include the new one
    await this.refreshConfigurations();

    return userKeys;
  }

  async refreshConfigurations() {
    for (const user of this.components.userController.getUsers()) {
      const roots = await this.components.storageRepository.listUserDataRoots(
        user.publicKey,
      );
      for (const root of roots) {
        for (const nodeConfig of root.nodeConfigs) {
          await this.processNodeConfiguration(user.publicKey, nodeConfig);
        }
      }
    }
  }

  private async processNodeConfiguration(
    userId: Uint8Array,
    nodeConfig: NodeConfiguration,
  ) {
    try {
      // 1. Check if it's for us
      if (!uint8ArrayEquals(nodeConfig.nodeId, this.nodePublicKey!)) {
        return;
      }

      // 2. In a real implementation, NodeUserConfiguration would be ENCRYPTED.
      // The implementation plan says: "Use the node's management private key to decrypt"
      // Looking at storage.proto, NodeConfiguration has user_configs which are repeated NodeUserConfiguration.
      // If they are encrypted, they would likely be in a single encrypted bytes field.
      // For this implementation, I will treat the user_configs as the source and
      // assume validation is key.

      // TODO: Implement actual ECIES or similar decryption if the proto is updated to hold encrypted_user_configs.
      // For now, I'll follow the merging logic.

      for (const userConfig of nodeConfig.userConfigs) {
        if (uint8ArrayEquals(userConfig.userId, userId)) {
          this.applyMerge(userId, userConfig);
        }
      }
    } catch (e) {
      console.error("Failed to process node config block", e);
    }
  }

  private applyMerge(userId: Uint8Array, incoming: NodeUserConfiguration) {
    const key = Buffer.from(userId).toString("base64");
    const current = this.mergedConfigs.get(key) || {
      userId,
      storageQuota: 0n,
    };

    // Most generous quota
    if (
      incoming.storageQuota === undefined ||
      current.storageQuota === undefined
    ) {
      current.storageQuota = undefined;
    } else if (incoming.storageQuota > current.storageQuota) {
      current.storageQuota = incoming.storageQuota;
    }

    // Union of keys
    if (incoming.storageKey.length > 0)
      current.storageKey = incoming.storageKey;
    if (incoming.linkKey?.length) current.linkKey = incoming.linkKey;
    if (incoming.dataKey?.length) current.dataKey = incoming.dataKey;
    if (incoming.userPrivateKey?.length)
      current.userPrivateKey = incoming.userPrivateKey;

    this.mergedConfigs.set(key, current);

    // Persist
    const proto = create(NodeUserConfigurationSchema, {
      userId: current.userId,
      storageQuota: current.storageQuota,
      storageKey: current.storageKey || new Uint8Array(),
      linkKey: current.linkKey,
      dataKey: current.dataKey,
      userPrivateKey: current.userPrivateKey,
    });
    this.components.storageRepository
      .saveMergedNodeConfig(
        userId,
        toBinary(NodeUserConfigurationSchema, proto),
      )
      .catch(console.error);
  }
}
