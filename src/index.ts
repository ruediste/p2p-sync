import { noise } from "@chainsafe/libp2p-noise";
import { yamux } from "@chainsafe/libp2p-yamux";
import { dagJson } from "@helia/dag-json";
import { ipns } from "@helia/ipns";
import { bootstrap } from "@libp2p/bootstrap";
import {
  circuitRelayServer,
  circuitRelayTransport,
} from "@libp2p/circuit-relay-v2";
import { identify, identifyPush } from "@libp2p/identify";
import {
  type IdentifyResult,
  type Libp2pEvents,
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
import { ipnsSelector } from "ipns/selector";
import { ipnsValidator } from "ipns/validator";
import { createLibp2p } from "libp2p";
import { mdns } from "./mdns";

import { autoNATv2 } from "@libp2p/autonat-v2";
import { generateKeyPair, privateKeyFromRaw } from "@libp2p/crypto/keys";
import type {
  AddressManager,
  TransportManager,
} from "@libp2p/interface-internal";
import { keychain } from "@libp2p/keychain";
import { multiaddr } from "@multiformats/multiaddr";
import { CID } from "multiformats";

const command = process.argv[2] as "add" | "get";
if (command !== "add" && command !== "get") {
  console.error("Please provide a command: 'add' or 'get'");
  process.exit(1);
}
const useAmino = false;
const useLan = true;
const listenPort = command === "add" ? 7743 : 7744;

interface MyHelperComponents {
  addressManager: AddressManager;
  events: TypedEventTarget<Libp2pEvents>;
  transportManager: TransportManager;
}
class MyHelper implements Startable {
  constructor(private components: MyHelperComponents) {}
  start(): void | Promise<void> {
    // If a system has many network interfaces, such as when using docker, it makes little sense to announce them. Instead, wait for another peer to
    // observe us and then add the observed address, for example via identify or with the modified mdns.
    try {
      const transport = this.components.transportManager;
      var orig = transport.getAddrs;
      transport.getAddrs = () => {
        return orig.call(transport).filter((addr) => {
          const components = addr.getComponents();
          if (
            components.length === 2 &&
            (components[0].name === "ip4" || components[0].name === "ip6") &&
            components[1].name === "tcp"
          ) {
            return false;
          }
          return true;
        });
      };
    } catch (e) {
      console.error(
        "Failed to monkey-patch transport getAddrs, observed address functionality may not work:",
        e,
      );
    }

    setInterval(() => {
      console.log(
        "getAddresses:",
        this.components.addressManager.getAddresses().map((a) => a.toString()),
      );
      console.log(
        "getObservedAddrs:",
        this.components.addressManager
          .getObservedAddrs()
          .map((a) => a.toString()),
      );
      console.log(
        "announceAddrs:",
        this.components.addressManager
          .getAnnounceAddrs()
          .map((a) => a.toString()),
      );
    }, 5000);

    // When identifying a peer, it tells us the observed address. Combine this with the listen port to get an address to announce.
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

function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
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
    },
    transports: [tcp(), circuitRelayTransport()],
    connectionEncrypters: [noise()],
    streamMuxers: [yamux()],
    peerDiscovery: [
      ...(useLan ? [mdns({ listenPort })] : []),
      ...(useAmino
        ? [
            bootstrap({
              list: [
                "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN",
                "/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa",
                "/dnsaddr/bootstrap.libp2p.io/p2p/QmbLHAnMoJPWSCR5Zhtx6BHJX9KiKNN6tpvbUcqanj75Nb",
                "/dnsaddr/bootstrap.libp2p.io/p2p/QmcZf59bWwK5XFi76CZX8cbJ4BhTzzA3gU1ZjYZcYW3dwt",
              ],
            }),
          ]
        : []),
    ],
    services: {
      identify: identify(),
      identifyPush: identifyPush(),
      circuitRelay: circuitRelayServer(),
      ping: ping(),
      ...(useLan
        ? {
            lanDHT: kadDHT({
              protocol: "/ipfs/lan/kad/1.0.0",
              peerInfoMapper: removePublicAddressesMapper,
              clientMode: false,
              logPrefix: "libp2p:dht-lan",
              datastorePrefix: "/dht-lan",
              metricsPrefix: "libp2p_dht_lan",
              validators: {
                ipns: ipnsValidator,
              },
              selectors: {
                ipns: ipnsSelector,
              },
            }),
          }
        : {}),
      ...(useAmino
        ? {
            aminoDHT: kadDHT({
              protocol: "/ipfs/kad/1.0.0",
              peerInfoMapper: removePrivateAddressesMapper,
              logPrefix: "libp2p:dht-amino",
              datastorePrefix: "/dht-amino",
              metricsPrefix: "libp2p_dht_amino",
              validators: {
                ipns: ipnsValidator,
              },
              selectors: {
                ipns: ipnsSelector,
              },
            }),
          }
        : {}),
      autoNATv2: autoNATv2(),
      myHelper: (components: MyHelperComponents) => new MyHelper(components),
      keychain: keychain(),
    },
  });

  return await createHelia({
    datastore,
    blockstore,
    libp2p,
  });
}

const node = await createNode();
const libp2p = node.libp2p;
const services = libp2p.services;

// services.lanDHT?.refreshRoutingTable();

console.log("Node started with Peer ID:", node.libp2p.peerId.toString());
const name = ipns(node, {});

if (command === "add") {
  console.log("Adding content...");
  const j = dagJson(node);

  setInterval(async () => {
    if (node.libp2p.getMultiaddrs().length == 0) {
      console.log("No addresses to announce yet, skipping provide");
      return;
    }
    const cid = await j.add({ message: "Hello World " + new Date() });
    console.log("content CID: " + cid);
    await node.routing.provide(cid);
    await name.publish("key1", cid);
    console.log(
      "key: " +
        Buffer.from((await services.keychain.exportKey("key1")).raw).toString(
          "base64",
        ),
    );
    console.log(
      "ipns cid: " +
        (await services.keychain.exportKey("key1")).publicKey
          .toCID()
          .toString(),
    );
  }, 10 * 1000);
} else {
  await sleep(1000);
  console.log("Getting content...");
  setInterval(async () => {
    // const foo = node;
    // node.libp2p.getMultiaddrs();
    // console.log(foo);
    // console.log(node.libp2p.getMultiaddrs().map((a) => a.toString()));
    // services.lanDHT?.refreshRoutingTable();
  }, 3000);
  // const keyRaw =
  //   "+TrILfsyvu3b/L/toeU4SINrMi6EJSROJ8wAS4Eoc2lmFfM7DrS/AwwnYTJaBnT+C+Sxjyr7exhZoa+rCwPVTw==";
  // services.keychain.importKey(
  //   "key1",
  //   privateKeyFromRaw(Buffer.from(keyRaw, "base64")),
  // );

  const j = dagJson(node);
  setInterval(async () => {
    const cid = CID.parse(
      "bafzaajaiaejcb4m2eec7wlf2w2k2koe4healdpsojjb3nvcu37ns23sfmjikgktl",
    );

    console.log("resolving ipns...");
    try {
      const result = await name.resolve(cid as any, { nocache: true });
      console.log(result.record);

      const contentCid = result.record.value;
      console.log("Fetching content with CID:", contentCid);
      const retrieved = await j.get(
        CID.parse(contentCid.substring("/ipfs/".length)),
      );
      console.log("Fetched object:", retrieved);
    } catch (e) {
      console.log("resolving failed: ", e);
    }
  }, 3000);

  // const cid = "baguqeeraifakbvsuuoaw3hm5eumekugcgu4umkogsekwohsy6kfy6sp3s3eq";
  // console.log("Fetching content with CID:", cid);
  // const retrieved = await j.get(CID.parse(cid));
  // console.log("Fetched object:", retrieved);

  // name.resolve("key1");

  // process.exit(0);
  // node.stop();
  while (true) {
    await sleep(1000);
  }
}
