import type {
  ComponentLogger,
  Logger,
  PeerDiscovery,
  PeerDiscoveryEvents,
  PeerId,
  PeerInfo,
  PeerStore,
  Startable,
} from "@libp2p/interface";
import { peerDiscoverySymbol, serviceCapabilities } from "@libp2p/interface";
import type {
  AddressManager,
  ConnectionManager,
} from "@libp2p/interface-internal";
import { multiaddr } from "@multiformats/multiaddr";
import type { RemoteInfo } from "dgram";
import { TypedEventEmitter } from "main-event";
import multicastDNS from "multicast-dns";
import * as query from "./query.js";
import { stringGen } from "./utils.js";

export interface MulticastDNSInit {
  broadcast?: boolean;
  interval?: number;
  serviceTag?: string;
  peerName?: string;
  port?: number;
  ip?: string;
  listenPort: number;
}

export interface MulticastDNSComponents {
  peerId: PeerId;
  addressManager: AddressManager;
  logger: ComponentLogger;
  peerStore: PeerStore;
  connectionManager: ConnectionManager;
}

export class MulticastDNS
  extends TypedEventEmitter<PeerDiscoveryEvents>
  implements PeerDiscovery, Startable
{
  public mdns?: multicastDNS.MulticastDNS;

  private readonly log: Logger;
  private readonly broadcast: boolean;
  private readonly interval: number;
  private readonly serviceTag: string;
  private readonly peerName: string;
  private readonly port: number;
  private readonly listenPort: number;
  private readonly ip: string;
  private _queryInterval: ReturnType<typeof setInterval> | null;
  private readonly components: MulticastDNSComponents;

  constructor(components: MulticastDNSComponents, init: MulticastDNSInit) {
    super();

    this.log = components.logger.forComponent("libp2p:mdns2");
    this.broadcast = init.broadcast !== false;
    this.interval = init.interval ?? 1e3 * 10;
    this.serviceTag = init.serviceTag ?? "_p2p2._udp.local";
    this.ip = init.ip ?? "224.0.0.251";
    this.listenPort = init.listenPort;
    this.peerName = init.peerName ?? stringGen(63);
    // 63 is dns label limit
    if (this.peerName.length >= 64) {
      throw new Error("Peer name should be less than 64 chars long");
    }
    this.port = init.port ?? 5353;
    this.components = components;
    this._queryInterval = null;
    this._onMdnsQuery = this._onMdnsQuery.bind(this);
    this._onMdnsResponse = this._onMdnsResponse.bind(this);
    this._onMdnsWarning = this._onMdnsWarning.bind(this);
    this._onMdnsError = this._onMdnsError.bind(this);
  }

  readonly [peerDiscoverySymbol] = this;

  readonly [Symbol.toStringTag] = "@libp2p/mdns";

  readonly [serviceCapabilities]: string[] = ["@libp2p/peer-discovery"];

  isStarted(): boolean {
    return Boolean(this.mdns);
  }

  /**
   * Start sending queries to the LAN.
   *
   * @returns {void}
   */
  async start(): Promise<void> {
    if (this.mdns != null) {
      return;
    }

    this.mdns = multicastDNS({ port: this.port, ip: this.ip });
    this.mdns.on("query", this._onMdnsQuery);
    this.mdns.on("response", this._onMdnsResponse);
    this.mdns.on("warning", this._onMdnsWarning);
    this.mdns.on("error", this._onMdnsError);

    this._queryInterval = query.queryLAN(
      this.mdns,
      this.peerName,
      this.serviceTag,
      this.interval,
      {
        log: this.log,
      },
    );
  }

  _onMdnsQuery(event: multicastDNS.QueryPacket, rInfo: RemoteInfo): void {
    if (this.mdns == null) {
      return;
    }

    this.log.trace(
      "received incoming mDNS query from " + rInfo.address + ":" + rInfo.port,
    );
    query.gotQuery(
      event,
      this.mdns,
      this.peerName,
      this.components.addressManager.getAddresses(),
      this.serviceTag,
      rInfo,
      {
        log: this.log,
      },
    );
  }

  _onMdnsResponse(event: multicastDNS.ResponsePacket, rinfo: RemoteInfo): void {
    this.log.trace(
      "received incoming mDNS response from " +
        rinfo.address +
        ":" +
        rinfo.port,
    );

    try {
      const { peer: foundPeer, observed } = query.gotResponse(
        event,
        this.peerName,
        this.serviceTag,
        {
          log: this.log,
        },
      );

      if (foundPeer != null) {
        this.log("discovered peer in mDNS query response %p", foundPeer.id);

        this.dispatchEvent(
          new CustomEvent<PeerInfo>("peer", {
            detail: foundPeer,
          }),
        );
        if (this.components.connectionManager.getConnectionsMap().size < 3) {
          this.components.connectionManager.openConnection(foundPeer.id, {
            priority: 10,
          });
        }
      }
      if (observed != null) {
        this.log("observed address in mDNS query response %s", observed);
        var addr = multiaddr(
          "/ip4/" + observed.addr + "/tcp/" + this.listenPort,
        );
        addr = addr.encapsulate("/p2p/" + this.components.peerId.toString());

        this.components.addressManager.addObservedAddr(addr);
        this.components.addressManager.confirmObservedAddr(addr, {
          type: "observed",
        });
      }
    } catch (err) {
      this.log.error("error processing peer response - %e", err);
    }
  }

  _onMdnsWarning(err: Error): void {
    this.log.error("mdns warning - %e", err);
  }

  _onMdnsError(err: Error): void {
    this.log.error("mdns error - %e", err);
  }

  /**
   * Stop sending queries to the LAN.
   *
   * @returns {Promise}
   */
  async stop(): Promise<void> {
    if (this.mdns == null) {
      return;
    }

    this.mdns.removeListener("query", this._onMdnsQuery);
    this.mdns.removeListener("response", this._onMdnsResponse);
    this.mdns.removeListener("warning", this._onMdnsWarning);
    this.mdns.removeListener("error", this._onMdnsError);

    if (this._queryInterval != null) {
      clearInterval(this._queryInterval);
      this._queryInterval = null;
    }

    await new Promise<void>((resolve) => {
      if (this.mdns != null) {
        this.mdns.destroy(resolve);
      } else {
        resolve();
      }
    });

    this.mdns = undefined;
  }
}
