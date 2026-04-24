import { type Datastore } from "interface-datastore";
import type { LibP2PType } from "./createNode";
import type { UserNodeController } from "./UserNodeController";
import type { UserController } from "./UsersController";

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
