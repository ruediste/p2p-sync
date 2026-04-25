import type { LibP2PType } from "@/network/createNode";
import type { UserNodeConnectionController } from "@/network/UserNodeConnectionController.js";
import type { UserController } from "@/user/UserController";
import { type Blockstore } from "interface-blockstore";
import { type Datastore } from "interface-datastore";
import type { ReplicationController } from "./storage/ReplicationController.js";
import type { StorageRepository } from "./storage/StorageRepository.js";
import type { NodeConfigController } from "./node/NodeConfigController.js";

export interface InstanceComponents {
  libp2p: LibP2PType;
  dataStore: Datastore;
  blockStore: Blockstore;
  nodePublicKey: Uint8Array;
}

export interface LifecycleComponents {
  storageRepository: StorageRepository;
  userController: UserController;
  userNodeConnectionController: UserNodeConnectionController;
  replicationController: ReplicationController;
  nodeConfigController: NodeConfigController;
}

export type Components = InstanceComponents & LifecycleComponents;

export interface Component {
  initialize: () => void | Promise<void>;
}
