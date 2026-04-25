import type { LibP2PType } from "@/network/createNode";
import type { UserNodeController } from "@/network/UserNodeController";
import type { UserController } from "@/user/UserController";
import type { ReplicationController } from "./storage/ReplicationController.js";
import { type Datastore } from "interface-datastore";
import { type Blockstore } from "interface-blockstore";

export interface InstanceComponents {
  libp2p: LibP2PType;
  dataStore: Datastore;
  blockStore: Blockstore;
  nodePublicKey: Uint8Array;
}

export interface LifecycleComponents {
  userController: UserController;
  userNodeController: UserNodeController;
  replicationController: ReplicationController;
}

export type Components = InstanceComponents & LifecycleComponents;

export interface Component {
  initialize: () => void | Promise<void>;
}
