import { noise } from "@chainsafe/libp2p-noise";
import { yamux } from "@chainsafe/libp2p-yamux";
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

const useAmino = true;
const useLan = false;
const logAddresses = false;
const listenPort = parseInt(process.argv[2]);

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
    if (logAddresses)
      setInterval(() => {
        console.log(
          "getAddresses:",
          this.components.addressManager
            .getAddresses()
            .map((a) => a.toString()),
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

export type LibP2PType = Awaited<ReturnType<typeof createNode>>[2]["libp2p"];

export async function createNode() {
  // the blockstore is where we store the blocks that make up files
  const blockstore = new FsBlockstore("blocks");

  // application-specific data lives in the datastore
  const datastore = new FsDatastore("data");

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
      listen: [
        "/ip4/0.0.0.0/tcp/" + listenPort,
        "/ip6/::/tcp/" + listenPort,
        "/p2p-circuit",
      ],
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
              networkDialTimeout: { maxTimeout: 1000, interval: 900 },
              incomingMessageTimeout: 1000,
              pingNewContactTimeout: { maxTimeout: 1000, interval: 900 },
              pingOldContactTimeout: { maxTimeout: 1000, interval: 900 },
              onPeerConnectTimeout: 1000,
            }),
          }
        : {}),
      autoNATv2: autoNATv2(),
      myHelper: (components: MyHelperComponents) => new MyHelper(components),
      keychain: keychain(),
    },
  });

  return [
    datastore,
    blockstore,
    await createHelia({
      datastore,
      blockstore,
      libp2p,
    }),
  ] as const;
}
