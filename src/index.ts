import { noise } from "@chainsafe/libp2p-noise";
import { yamux } from "@chainsafe/libp2p-yamux";
import { dagJson } from "@helia/dag-json";
import { bootstrap } from "@libp2p/bootstrap";
import { circuitRelayTransport } from "@libp2p/circuit-relay-v2";
import { identify } from "@libp2p/identify";
import { type IdentifyResult, type PrivateKey } from "@libp2p/interface";
import { kadDHT } from "@libp2p/kad-dht";
import { mdns } from "@libp2p/mdns";
import { ping } from "@libp2p/ping";
import { tcp } from "@libp2p/tcp";
import { FsBlockstore } from "blockstore-fs";
import { FsDatastore } from "datastore-fs";
import { createHelia } from "helia";
import { Key } from "interface-datastore";
import { createLibp2p } from "libp2p";
import { CID } from "multiformats/cid";

import { autoNATv2 } from "@libp2p/autonat-v2";
import { generateKeyPair, privateKeyFromRaw } from "@libp2p/crypto/keys";
import { multiaddr } from "@multiformats/multiaddr";

const command = process.argv[2] as "add" | "get";
if (command !== "add" && command !== "get") {
  console.error("Please provide a command: 'add' or 'get'");
  process.exit(1);
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

  const dht = kadDHT({
    // clientMode: true,
  });

  const listenPort = command === "add" ? 7743 : 7744;

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
      mdns(),
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
      dht,
      ping: ping(),
      autoNATv2: autoNATv2(),
      // autoNAT: autoNAT(),
      // uPnPNAT: uPnPNAT(),
      myHelper: (components) => {
        setInterval(() => {
          console.log(
            "getAddresses:",
            components.addressManager.getAddresses().map((a) => a.toString()),
          );
          console.log(
            "getObservedAddrs:",
            components.addressManager
              .getObservedAddrs()
              .map((a) => a.toString()),
          );
          console.log(
            "announceAddrs:",
            components.addressManager
              .getAnnounceAddrs()
              .map((a) => a.toString()),
          );
        }, 5000);
        components.events.addEventListener(
          "peer:identify",
          ({ detail: result }: { detail: IdentifyResult }) => {
            // console.log(
            //   "Identified peer:",
            //   result.peerId,
            //   "with observed address:",
            //   result.observedAddr,
            // );
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
                components.addressManager.addObservedAddr(newAddr);
              }
            }
          },
        );
      },
    },
  });

  return await createHelia({
    datastore,
    blockstore,
    libp2p,
  });
}

const node = await createNode();
console.log(
  "Node started with Peer ID:",
  node.libp2p.peerId.toString(),
  node.libp2p.getMultiaddrs().map((a) => a.toString()),
);

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
  const retrieved = await j.get(CID.parse(cid));
  console.log("Fetched object:", retrieved);
  process.exit(0);
  node.stop();
}
