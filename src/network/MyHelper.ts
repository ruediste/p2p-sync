import {
  type IdentifyResult,
  type Libp2pEvents,
  type Startable,
  type TypedEventTarget,
} from "@libp2p/interface";
import type {
  AddressManager,
  TransportManager,
} from "@libp2p/interface-internal";
import { multiaddr } from "@multiformats/multiaddr";

const logAddresses = false;

export interface MyHelperComponents {
  addressManager: AddressManager;
  events: TypedEventTarget<Libp2pEvents>;
  transportManager: TransportManager;
}

export class MyHelper implements Startable {
  constructor(
    private components: MyHelperComponents,
    private listenPort: number,
  ) {}
  start(): void | Promise<void> {
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
              multiaddr("/tcp/" + this.listenPort),
            );
            this.components.addressManager.addObservedAddr(newAddr);
          }
        }
      },
    );
  }
  stop(): void | Promise<void> {}
}
