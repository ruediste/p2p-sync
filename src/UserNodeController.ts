import { create, fromBinary, isMessage, toBinary } from "@bufbuild/protobuf";
import { AnySchema, anyUnpack } from "@bufbuild/protobuf/wkt";
import { type PeerId, type PeerInfo, type Stream } from "@libp2p/interface";
import { peerMap } from "@libp2p/peer-collections";
import { peerIdFromString } from "@libp2p/peer-id";
import { type Multiaddr, multiaddr } from "@multiformats/multiaddr";
import { type Datastore, Key } from "interface-datastore";
import { isUint8ArrayList } from "uint8arraylist";
import type { LibP2PType } from "./createNode";
import { HaveUsersSchema, WantUsersSchema } from "./gen/p2p-sync-protocol_pb";
import { syncProtocolId, syncRegistry } from "./syncProtocol";
import { sleep } from "./utils";
import type { Component, Components } from "./components";

const NODES_KEY = new Key("/user-nodes");
const PREFIX = "/user-nodes/";

/** Keeps track of a single remote user node. An async method keeps track of the state, error handling and retries. 
 * The state is persisted in such a way that processing can continue after a restart. */
class UserNodeEntry {
  /**
   * The multiaddrs a node is listening on
   */
  multiaddrs: MultiaddrEntry[] = [];

  constructor(
    /**
     * The identifier of the remote node
     */
    public id: PeerId,
    private libp2p: LibP2PType,
  ) {}

  dialFail?: {
    nextAttempt: number;
    count: number;
  };

  stream?: Stream;

  async process() {
    while (true) {
      if ((this.dialFail?.count ?? 0) > 5) {
        // stop remove node and stop dialling
      }

      // sleep if still waiting for dial
      if (this.dialFail) {
        const now = Date.now();
        if (this.dialFail.nextAttempt > now)
          await sleep(this.dialFail.nextAttempt - now);
      }

      try {
        // try to dial
        const stream = await this.libp2p.dialProtocol(
          this.multiaddrs.map((x) => x.addr),
          syncProtocolId,
        );
        this.stream = stream;
        this.dialFail = undefined;

        // setup event handler

        this.stream.addEventListener("message", (e) => {
          const data = isUint8ArrayList(e.data) ? e.data.slice() : e.data;
          const msg = anyUnpack(fromBinary(AnySchema, data), syncRegistry);
          if (isMessage(msg, HaveUsersSchema)) {
            msg.userIds
          }
          if (isMessage(msg, WantUsersSchema)) {
            const response=create(HaveUsersSchema,{
              userIds: 
            });
            this.stream?.send;
          }
        });

        // ask node for users
        {
          const msg = create(WantUsersSchema);
          this.stream.send(toBinary(WantUsersSchema, msg));
        }
      } catch (e) {
        console.log("dial failed", e);
        const count = 1 + (this.dialFail?.count ?? 0);
        this.dialFail = {
          nextAttempt: Date.now() + 10 * 1000 * Math.pow(2, count),
          count,
        };
      }
    }
  }

  static deserialize(json: ReturnType<UserNodeEntry["serialize"]>, libp2p: LibP2PType) {
    const result = new UserNodeEntry(peerIdFromString(json.id), libp2p);
    json.multiaddrs.forEach((m: any) =>
      result.multiaddrs.push({
        addr: multiaddr(m.addr),
        lastObserved: m.lastObserved,
      }),
    );
    return result;
  }

  serialize() {
    return {
      id: this.id.toString(),
      multiaddrs: this.multiaddrs.map((m) => ({
        addr: m.addr.toString(),
        lastObserved: m.lastObserved,
      })),
    };
  }
}

interface MultiaddrEntry {
  addr: Multiaddr;
  lastObserved: number;
}

/*
 * Keeps track of all user nodes currently being processed.
 */
export class UserNodeController implements Component{ 
  nodes: UserNodeEntry[] = [];
  private dirty = false;
  private running = true;

  constructor(
    private components: Pick<Components,'libp2p'|'userController'|'dataStore'>
  ) {
    this.init().catch((err) => {
      console.error("Failed to initialize UserNodeController", err);
    });

    // start an async loop, persisting the nodes in the datastore every second (if dirty)
    void this.loop();

    peerMap<string>();
  }

  initialize(){

  }
  private async init() {
    try {
      await this.components.dataStore.query({prefix: PREFIX});
      const data = await this.components.dataStore.get(NODES_KEY);
      const json = JSON.parse(new TextDecoder().decode(data));
      this.nodes = json.map((p: any) => ({
        id: peerIdFromString(p.id),
        multiaddrs: p.multiaddrs.map((m: any) => ({
          addr: multiaddr(m.addr),
          lastObserved: m.lastObserved,
        })),
      }));
    } catch (err: any) {
      if (err.code !== "ERR_NOT_FOUND") {
        throw err;
      }
    }
  }

  private async loop() {
    while (this.running) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      if (this.dirty) {
        try {
          await this.save();
          this.dirty = false;
        } catch (err) {
          console.error("Failed to save user nodes", err);
        }
      }
    }
  }

  private async save() {
    const json = this.nodes.map((p) => ({
      id: p.id.toString(),
      multiaddrs: p.multiaddrs.map((m) => ({
        addr: m.addr.toString(),
        lastObserved: m.lastObserved,
      })),
    }));
    const data = new TextEncoder().encode(JSON.stringify(json));
    await this.components.dataStore.put(NODES_KEY, data);
  }

  stop() {
    this.running = false;
  }

  merge(info: PeerInfo) {
    let node = this.nodes.find((p) => p.id.equals(info.id));
    if (!node) {
      node = { id: info.id, multiaddrs: [] };
      this.nodes.push(node);
    }

    const now = Date.now();
    for (const addr of info.multiaddrs) {
      const existingAddr = node.multiaddrs.find((a) => a.addr.equals(addr));
      if (existingAddr) {
        existingAddr.lastObserved = now;
      } else {
        node.multiaddrs.push({ addr, lastObserved: now });
      }
    }
    this.dirty = true;
  }
}

/**
 * Standalone manager for tracking discovered nodes for a user.
 * Manages persistence of node information to the datastore.
 */
export class UserNodeManager {
  nodes: { id: PeerId; multiaddrs: MultiaddrEntry[] }[] = [];
  private dirty = false;
  private running = true;
  private store: Datastore;

  constructor(store: Datastore) {
    this.store = store;
    this.init().catch((err) => {
      console.error("Failed to initialize UserNodeManager", err);
    });
    void this.loop();
  }

  private async init() {
    try {
      const data = await this.store.get(NODES_KEY);
      const json = JSON.parse(new TextDecoder().decode(data));
      this.nodes = json.map((p: any) => ({
        id: peerIdFromString(p.id),
        multiaddrs: p.multiaddrs.map((m: any) => ({
          addr: multiaddr(m.addr),
          lastObserved: m.lastObserved,
        })),
      }));
    } catch (err: any) {
      if (err.code !== "ERR_NOT_FOUND") {
        throw err;
      }
    }
  }

  private async loop() {
    while (this.running) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      if (this.dirty) {
        try {
          await this.save();
          this.dirty = false;
        } catch (err) {
          console.error("Failed to save user nodes", err);
        }
      }
    }
  }

  private async save() {
    const json = this.nodes.map((p) => ({
      id: p.id.toString(),
      multiaddrs: p.multiaddrs.map((m: any) => ({
        addr: m.addr.toString(),
        lastObserved: m.lastObserved,
      })),
    }));
    const data = new TextEncoder().encode(JSON.stringify(json));
    await this.store.put(NODES_KEY, data);
  }

  stop() {
    this.running = false;
  }

  merge(info: PeerInfo) {
    let node = this.nodes.find((p) => p.id.equals(info.id));
    if (!node) {
      node = { id: info.id, multiaddrs: [] };
      this.nodes.push(node);
    }

    const now = Date.now();
    for (const addr of info.multiaddrs) {
      const existingAddr = node.multiaddrs.find((a) => a.addr.equals(addr));
      if (existingAddr) {
        existingAddr.lastObserved = now;
      } else {
        node.multiaddrs.push({ addr, lastObserved: now });
      }
    }
    this.dirty = true;
  }
}
