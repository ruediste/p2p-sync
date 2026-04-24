import { CID } from "multiformats";
import { sha256 } from "multiformats/hashes/sha2";
import type {
  Component,
  Components,
  InstanceComponents,
  LifecycleComponents,
} from "./components";
import { createNode } from "./createNode";
import { loadOrCreateUserKeys } from "./userKeysManagement";
import { UserNodeController, UserNodeManager } from "./UserNodeController";
import { UserController } from "./UsersController";
import { sleep } from "./utils";

const userKeys = await loadOrCreateUserKeys();

const [dataStore, blockstore, node] = await createNode();
const libp2p = node.libp2p;
const services = libp2p.services;

console.log("Node started with ID:", node.libp2p.peerId.toString());

services.aminoDHT?.refreshRoutingTable();

// build CID based on sha256 key of user
const userPublicKeyHash = await sha256.digest(userKeys.userPublicKey);
const userCid = CID.createV1(0x72, userPublicKeyHash);

const userNodeManager = new UserNodeManager(dataStore);

const components: Components = {
  libp2p,
  dataStore,
} satisfies InstanceComponents as any;

const lifecycleConstructors: {
  [key in keyof LifecycleComponents]: (
    c: Components,
  ) => LifecycleComponents[key];
} = {
  userController: (c) => new UserController(c),
  userNodeController: (c) => new UserNodeController(c),
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
      for await (const event of libp2p.services.aminoDHT!.provide(userCid)) {
        if (event.name == "PEER_RESPONSE") continue;
        if (event.name == "DIAL_PEER") continue;
        if (event.name == "QUERY_ERROR") continue;

        // console.log("Provide event:", event);
      }
    } catch (e) {
      console.log("Error while publishing node as provider", e);
    }
    await sleep(30 * 1000);
  }
})();

if (true)
  (async () => {
    while (true) {
      try {
        // search kad for other nodes providing this user
        console.log(
          "Searching for providers for user CID:",
          userCid.toString(),
        );
        for await (const event of libp2p.services.aminoDHT!.findProviders(
          userCid,
        )) {
          if (event.name === "DIAL_PEER") continue;

          // console.log("Search event: ", event);
          if (event.name === "PROVIDER") {
            console.log("Found provider:", event, event.providers);
            event.providers.forEach((p) => userNodeManager.merge(p));
          }
        }
      } catch (e) {
        console.log("Error while searching for providers", e);
      }
      console.log("Search: sleep");
      await sleep(30 * 1000);
    }
  })();

libp2p.handle(protocolId, (stream) => {
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

// // the local will dial the remote on the protocol stream
// const stream = await local.dialProtocol(remote.getMultiaddrs(), ECHO_PROTOCOL);

// stream.addEventListener("message", (evt) => {
//   // evt.data is a `Uint8ArrayList` so we must turn it into a `Uint8Array`
//   // before decoding it
//   console.info(
//     `Echoed back to us: "${new TextDecoder().decode(evt.data.subarray())}"`,
//   );
// });

// // the stream input must be bytes
// stream.send(new TextEncoder().encode("hello world"));
