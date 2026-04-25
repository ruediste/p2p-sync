import type { LibP2PType } from "@/network/createNode";
import type { UserNodeController } from "@/network/UserNodeController";
import type { UserController } from "@/user/UserController";
import { type Datastore } from "interface-datastore";

export interface InstanceComponents {
  libp2p: LibP2PType;
  dataStore: Datastore;
}

export interface LifecycleComponents {
  userController: UserController;
  userNodeController: UserNodeController;
}

export type Components = InstanceComponents & LifecycleComponents;

export interface Component {
  initialize: () => void | Promise<void>;
}
