import { noise } from "@chainsafe/libp2p-noise";
import { yamux } from "@chainsafe/libp2p-yamux";
import { dagJson } from "@helia/dag-json";
import { bootstrap } from "@libp2p/bootstrap";
import { circuitRelayTransport } from "@libp2p/circuit-relay-v2";
import { identify } from "@libp2p/identify";
import {
  peerDiscoverySymbol,
  type IdentifyResult,
  type Libp2pEvents,
  type PeerInfo,
  type PrivateKey,
  type Startable,
  type TypedEventTarget,
} from "@libp2p/interface";
import {
  kadDHT,
  removePrivateAddressesMapper,
  removePublicAddressesMapper,
} from "@libp2p/kad-dht";
import { ping } from "@libp2p/ping";
import { tcp } from "@libp2p/tcp";
import { FsBlockstore } from "blockstore-fs";
import { FsDatastore } from "datastore-fs";
import { createHelia } from "helia";
import { Key } from "interface-datastore";
import { createLibp2p } from "libp2p";
import { CID } from "multiformats/cid";
// import { mdns } from "@libp2p/mdns";
import { mdns } from "./mdns";

import { autoNATv2 } from "@libp2p/autonat-v2";
import { generateKeyPair, privateKeyFromRaw } from "@libp2p/crypto/keys";
import type { AddressManager } from "@libp2p/interface-internal";
import { multiaddr } from "@multiformats/multiaddr";

const command = process.argv[2] as "add" | "get";
if (command !== "add" && command !== "get") {
  console.error("Please provide a command: 'add' or 'get'");
  process.exit(1);
}
const useAmino = false;
const listenPort = command === "add" ? 7743 : 7744;

interface MyHelperComponents {
  addressManager: AddressManager;
  events: TypedEventTarget<Libp2pEvents>;
}
class MyHelper implements Startable {
  constructor(private components: MyHelperComponents) {}
  start(): void | Promise<void> {
    // setInterval(() => {
    //   console.log(
    //     "getAddresses:",
    //     components.addressManager.getAddresses().map((a) => a.toString()),
    //   );
    //   console.log(
    //     "getObservedAddrs:",
    //     components.addressManager
    //       .getObservedAddrs()
    //       .map((a) => a.toString()),
    //   );
    //   console.log(
    //     "announceAddrs:",
    //     components.addressManager
    //       .getAnnounceAddrs()
    //       .map((a) => a.toString()),
    //   );
    // }, 5000);
    const components = (this.components as any).components;
    Object.values(components).forEach((c: any) => {
      if (c != null && c[peerDiscoverySymbol] != null) {
        console.log("peer discovery register", c);
        c[peerDiscoverySymbol].addEventListener?.(
          "peer",
          (evt: CustomEvent<PeerInfo>) => {
            console.log("peer", evt.detail);
          },
        );
      }
    });
    this.components.events.addEventListener(
      "peer:identify",
      ({ detail: result }: { detail: IdentifyResult }) => {
        if (result.observedAddr) {
          const addrComponents = result.observedAddr.getComponents();
          if (
            addrComponents.length > 0 &&
            addrComponents[addrComponents.length - 1].name === "tcp"
          ) {
            addrComponents.pop();
            const newAddr = multiaddr(addrComponents).encapsulate(
              multiaddr("/tcp/" + listenPort),
            );
            // console.log("Adding observed address:", newAddr.toString());
            this.components.addressManager.addObservedAddr(newAddr);
          }
        }
      },
    );
  }
  stop(): void | Promise<void> {}
}

async function createNode() {
  // the blockstore is where we store the blocks that make up files
  const blockstore = new FsBlockstore("data/blocks-" + command);

  // application-specific data lives in the datastore
  const datastore = new FsDatastore("data/data-" + command);

  const privateKeyKey = new Key("privateKey");
  let privateKey: PrivateKey;
  if (await datastore.has(privateKeyKey)) {
    privateKey = await privateKeyFromRaw(await datastore.get(privateKeyKey));
    console.log("Found existing private key, using it");
  } else {
    console.log("No existing private key found, generating a new one");
    privateKey = await generateKeyPair("Ed25519");
    await datastore.put(privateKeyKey, privateKey.raw);
  }

  // libp2p is the networking layer that underpins Helia
  const libp2p = await createLibp2p({
    privateKey,
    datastore,
    addresses: {
      listen: ["/ip4/0.0.0.0/tcp/" + listenPort, "/ip6/::/tcp/" + listenPort],
      // announce: [],
    },
    transports: [tcp(), circuitRelayTransport()],
    connectionEncrypters: [noise()],
    streamMuxers: [yamux()],
    peerDiscovery: [
      mdns({ broadcast: true }),
      bootstrap({
        list: [
          "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN",
          "/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa",
          "/dnsaddr/bootstrap.libp2p.io/p2p/QmbLHAnMoJPWSCR5Zhtx6BHJX9KiKNN6tpvbUcqanj75Nb",
          "/dnsaddr/bootstrap.libp2p.io/p2p/QmcZf59bWwK5XFi76CZX8cbJ4BhTzzA3gU1ZjYZcYW3dwt",
        ],
      }),
    ],
    services: {
      identify: identify(),
      lanDHT: kadDHT({
        protocol: "/ipfs/lan/kad/1.0.0",
        peerInfoMapper: removePublicAddressesMapper,
        clientMode: false,
        logPrefix: "libp2p:dht-lan",
        datastorePrefix: "/dht-lan",
        metricsPrefix: "libp2p_dht_lan",
      }),
      ...(useAmino
        ? {
            aminoDHT: kadDHT({
              protocol: "/ipfs/kad/1.0.0",
              peerInfoMapper: removePrivateAddressesMapper,
              logPrefix: "libp2p:dht-amino",
              datastorePrefix: "/dht-amino",
              metricsPrefix: "libp2p_dht_amino",
            }),
          }
        : {}),
      ping: ping(),
      autoNATv2: autoNATv2(),
      myHelper: (components: MyHelperComponents) => new MyHelper(components),
    },
  });

  return await createHelia({
    datastore,
    blockstore,
    libp2p,
  });
}

const node = await createNode();
console.log("Node started with Peer ID:", node.libp2p.peerId.toString());

node.libp2p.addEventListener("peer:discovery", (evt) => {
  //   node.libp2p.dial(evt.detail.multiaddrs); // dial discovered peers
  // console.log(
  //   "found peer: ",
  //   evt.detail.id,
  //   // evt.detail.multiaddrs.map((a) => a.toString()),
  // );
});

if (command === "add") {
  console.log("Adding content...");
  const j = dagJson(node);
  const cid = await j.add({ message: "Hello World 301" });
  node.routing.provide(cid);
  console.log("Added Content:", cid.toString());
} else {
  console.log("Getting content...");
  setInterval(async () => {
    // const foo = node;
    // node.libp2p.getMultiaddrs();
    // console.log(foo);
    // console.log(node.libp2p.getMultiaddrs().map((a) => a.toString()));
  }, 3000);
  const j = dagJson(node);
  const cid = "baguqeerap2d52pc5kg5znbb7yocrp4keqxihhon5wgzeqmtwzcg6qkllaiaa";
  console.log("Fetching content with CID:", cid);
  const retrieved = await j.get(CID.parse(cid));
  console.log("Fetched object:", retrieved);
  process.exit(0);
  node.stop();
}
