import type { Component, Components } from "@/components";
import { UserNodeEntry } from "@/network/UserNodeEntry";
import { type PeerInfo } from "@libp2p/interface";
import { Key } from "interface-datastore";

const NODES_KEY = new Key("/user-nodes");
const PREFIX = "/user-nodes/";

/*
 * Keeps track of all user nodes currently being processed.
 */
export class UserNodeController implements Component {
  nodes: UserNodeEntry[] = [];
  private dirty = false;
  private running = true;

  constructor(
    private components: Pick<
      Components,
      "libp2p" | "userController" | "dataStore"
    >,
  ) {
    // start an async loop, persisting the nodes in the datastore every second (if dirty)
    void this.loop();
  }

  async initialize() {
    await this.init();
  }

  private async init() {
    try {
      // Note: PREFIX query from original seemed incomplete or unused in the sense that it wasn't assigning results
      // await this.components.dataStore.query({prefix: PREFIX});

      const data = await this.components.dataStore.get(NODES_KEY);
      const json = JSON.parse(new TextDecoder().decode(data));
      this.nodes = json.map((p: any) =>
        UserNodeEntry.deserialize(p, this.components.libp2p, this.components),
      );

      // Start processing for each node
      for (const node of this.nodes) {
        void node.process();
      }
    } catch (err: any) {
      if (err.code !== "ERR_NOT_FOUND") {
        console.error("Failed to initialize UserNodeController", err);
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
    const json = this.nodes.map((p) => p.serialize());
    const data = new TextEncoder().encode(JSON.stringify(json));
    await this.components.dataStore.put(NODES_KEY, data);
  }

  stop() {
    this.running = false;
    for (const node of this.nodes) {
      // node doesn't have a stop method yet in the original code but we break its loop if dial fails too many times
    }
  }

  merge(info: PeerInfo) {
    let node = this.nodes.find((p) => p.id.equals(info.id));
    if (!node) {
      node = new UserNodeEntry(
        info.id,
        this.components.libp2p,
        this.components,
      );
      this.nodes.push(node);
      void node.process();
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
