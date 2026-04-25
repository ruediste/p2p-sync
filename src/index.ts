import type {
  Component,
  Components,
  InstanceComponents,
  LifecycleComponents,
} from "@/components";
import { createNode } from "@/network/createNode";
import { syncProtocolId } from "@/network/syncProtocol";
import { UserNodeController } from "@/network/UserNodeController";
import { UserPeerManager } from "@/network/UserPeerManager";
import { UserController } from "@/user/UserController";
import { loadOrCreateUserKeys } from "@/user/UserKeysManagement";
import { loadOrCreateNodeKeys } from "@/node/NodeKeysManagement";
import { ReplicationController } from "@/storage/ReplicationController";
import { StorageRepository } from "@/storage/StorageRepository";
import { BootstrapService } from "@/node/BootstrapService";
import { sleep } from "@/util/sleep";
import { CID } from "multiformats";
import { sha256 } from "multiformats/hashes/sha2";

const nodeKeys = await loadOrCreateNodeKeys();
const userKeys = await loadOrCreateUserKeys();

const [dataStore, blockStore, node] = await createNode();
const libp2p = node.libp2p;
const services = libp2p.services;

console.log("Node started with ID:", node.libp2p.peerId.toString());

services.aminoDHT?.refreshRoutingTable();

// build CID based on sha256 key of user
const userPublicKeyHash = await sha256.digest(userKeys.userPublicKey);
const userCid = CID.createV1(0x72, userPublicKeyHash);

const userPeerManager = new UserPeerManager(dataStore);

const components: Components = {
  libp2p,
  dataStore,
  blockStore,
  nodePublicKey: nodeKeys.publicKey,
} satisfies InstanceComponents as any;

const storageRepository = new StorageRepository(components);
const bootstrapService = new BootstrapService(storageRepository);
await bootstrapService.bootstrap();

const lifecycleConstructors: {
  [key in keyof LifecycleComponents]: (
    c: Components,
  ) => LifecycleComponents[key];
} = {
  userController: (c) => new UserController(c),
  userNodeController: (c) => new UserNodeController(c),
  replicationController: (c) => new ReplicationController(c),
};

const lifecycleComponents: Component[] = [];
for (const e of Object.entries(lifecycleConstructors)) {
  const c = e[1](components);
  (components as any)[e[0]] = c;
  lifecycleComponents.push(c);
}

for (const c of lifecycleComponents) {
  await c.initialize();
}

(async () => {
  while (true) {
    try {
      console.log(libp2p.getMultiaddrs());
      while (libp2p.getMultiaddrs().length == 0) {
        await sleep(1000);
      }
      console.log("Own addresses are available: ", libp2p.getMultiaddrs());

      // publish own node as provider for the user
      console.log(
        "Publishing node as provider for user CID:",
        userCid.toString(),
      );
      if (libp2p.services.aminoDHT) {
        for await (const event of libp2p.services.aminoDHT.provide(userCid)) {
          if (event.name == "PEER_RESPONSE") continue;
          if (event.name == "DIAL_PEER") continue;
          if (event.name == "QUERY_ERROR") continue;

          // console.log("Provide event:", event);
        }
      }
    } catch (e) {
      console.log("Error while publishing node as provider", e);
    }
    await sleep(30 * 1000);
  }
})();

(async () => {
  while (true) {
    try {
      // search kad for other nodes providing this user
      console.log("Searching for providers for user CID:", userCid.toString());
      if (libp2p.services.aminoDHT) {
        for await (const event of libp2p.services.aminoDHT.findProviders(
          userCid,
        )) {
          if (event.name === "DIAL_PEER") continue;

          // console.log("Search event: ", event);
          if (event.name === "PROVIDER") {
            console.log("Found provider:", event, event.providers);
            event.providers.forEach((p) => userPeerManager.merge(p));
          }
        }
      }
    } catch (e) {
      console.log("Error while searching for providers", e);
    }
    console.log("Search: sleep");
    await sleep(30 * 1000);
  }
})();

libp2p.handle(syncProtocolId, (stream) => {
  // pipe the stream output back to the stream input
  stream.addEventListener("message", (evt) => {
    stream.send(evt.data);
  });

  // close the incoming writable end when the remote writable end closes
  stream.addEventListener("remoteCloseWrite", () => {
    stream.close();
  });
});

libp2p.addEventListener("peer:identify", (e) => {
  console.log("peer:identify", e.detail, e.detail.protocols);
});
