import type { LibP2PType } from "@/network/createNode";
import { type PeerId, type Stream } from "@libp2p/interface";
import { peerIdFromString } from "@libp2p/peer-id";
import { type Multiaddr, multiaddr } from "@multiformats/multiaddr";
import { isUint8ArrayList } from "uint8arraylist";
import type { Components } from "../components.js";
import { sleep } from "../util/sleep.js";
import { SyncMessageHandler } from "./SyncMessageHandler.js";
import { syncProtocolId } from "./syncProtocol.js";

export interface MultiaddrEntry {
  addr: Multiaddr;
  lastObserved: number;
}

/** Keeps track of a single remote user node. An async method keeps track of the state, error handling and retries.
 * The state is persisted in such a way that processing can continue after a restart. */
export class UserNodeConnectionEntry {
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
    private components: Pick<
      Components,
      "userController" | "replicationController"
    >,
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
        break;
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

        const handler = new SyncMessageHandler(this.stream, this.components);

        this.stream.addEventListener("message", (e) => {
          const data = isUint8ArrayList(e.data) ? e.data.slice() : e.data;
          void handler.handleMessage(data);
        });

        // ask node for users
        await handler.sendWantUsers();
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

  static deserialize(
    json: any,
    libp2p: LibP2PType,
    components: Pick<Components, "userController" | "replicationController">,
  ) {
    const result = new UserNodeConnectionEntry(
      peerIdFromString(json.id),
      libp2p,
      components,
    );
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
