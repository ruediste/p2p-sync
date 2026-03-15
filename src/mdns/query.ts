import type { LoggerOptions, PeerInfo } from "@libp2p/interface";
import { peerIdFromString } from "@libp2p/peer-id";
import { isPrivate } from "@libp2p/utils";
import type { Multiaddr } from "@multiformats/multiaddr";
import { CODE_P2P, multiaddr } from "@multiformats/multiaddr";
import type { RemoteInfo } from "dgram";
import type { Answer, StringAnswer, TxtAnswer, TxtData } from "dns-packet";
import type { MulticastDNS, QueryPacket, ResponsePacket } from "multicast-dns";

export function queryLAN(
  mdns: MulticastDNS,
  localPeerName: string,
  serviceTag: string,
  interval: number,
  options?: LoggerOptions,
): ReturnType<typeof setInterval> {
  const query = (): void => {
    options?.log.trace("query", serviceTag);

    mdns.query({
      questions: [
        {
          name: localPeerName + "." + serviceTag,
          type: "PTR",
        },
      ],
    });
  };

  // Immediately start a query, then do it every interval.
  query();
  return setInterval(query, interval);
}

function txtDataToString(data: TxtData): string {
  if (Buffer.isBuffer(data)) {
    return data.toString();
  }

  if (Array.isArray(data)) {
    return data.map((d) => txtDataToString(d)).join("");
  }

  return data;
}

export function gotResponse(
  rsp: ResponsePacket,
  localPeerName: string,
  serviceTag: string,
  options?: LoggerOptions,
): { peer?: PeerInfo; observed?: { addr: string; family: string } } {
  if (rsp.answers == null) {
    return {};
  }

  let answerPTR: StringAnswer | undefined;
  let oAddr: string | undefined;
  let oFamily: string | undefined;
  let oPeerName: string | undefined;
  const addrAnswers: TxtAnswer[] = [];

  rsp.answers.forEach((answer) => {
    switch (answer.type) {
      case "PTR":
        answerPTR = answer;
        break;
      case "TXT":
        if (answer.name === "addr") addrAnswers.push(answer);
        else if (answer.name === "oAddr") oAddr = txtDataToString(answer.data);
        else if (answer.name === "oFamily")
          oFamily = txtDataToString(answer.data);
        else if (answer.name === "oPeerName")
          oPeerName = txtDataToString(answer.data);
        break;
      default:
        break;
    }
  });

  // according to the spec, peer details should be in the additional records,
  // not the answers though it seems go-libp2p at least ignores this?
  // https://github.com/libp2p/specs/blob/master/discovery/mdns.md#response
  rsp.additionals.forEach((answer) => {
    switch (answer.type) {
      case "TXT":
        if (answer.name === "addr") addrAnswers.push(answer);
        break;
      default:
        break;
    }
  });

  // check if the PTR answer is as expected. Otherwise, this is not a valid response to our query and we should ignore it.
  if (
    answerPTR == null ||
    answerPTR?.name !== serviceTag ||
    // skip own response
    answerPTR.data === localPeerName + "." + serviceTag
  ) {
    return {};
  }

  // check if this is a response to our own query
  if (oPeerName !== localPeerName) {
    return {};
  }

  let peer: PeerInfo | undefined;

  if (addrAnswers.length > 0)
    try {
      const multiaddrs: Multiaddr[] = addrAnswers
        .flatMap((a) => a.data)
        .map((answerData) => multiaddr(txtDataToString(answerData)));

      const peerId = multiaddrs[0]
        .getComponents()
        .findLast((c) => c.code === CODE_P2P)?.value;
      if (peerId == null) {
        throw new Error("Multiaddr doesn't contain PeerId");
      }
      options?.log("peer found %p", peerId);

      peer = {
        id: peerIdFromString(peerId),
        multiaddrs: multiaddrs.map((addr) => addr.decapsulateCode(CODE_P2P)),
      };
    } catch (err) {
      options?.log.error("failed to parse mdns response - %e", err);
    }

  let observed: { addr: string; family: string } | undefined;
  if (oAddr != null && oFamily != null) {
    observed = { addr: oAddr, family: oFamily };
  }

  return { peer, observed };
}

export function gotQuery(
  qry: QueryPacket,
  mdns: MulticastDNS,
  peerName: string,
  multiaddrs: Multiaddr[],
  serviceTag: string,
  rInfo: RemoteInfo,
  options?: LoggerOptions,
): void {
  if (
    qry.questions[0] != null &&
    qry.questions[0].name.endsWith("." + serviceTag)
  ) {
    const oPeerName = qry.questions[0].name.slice(
      0,
      qry.questions[0].name.length - serviceTag.length - 1,
    );
    const answers: Answer[] = [];

    answers.push({
      name: serviceTag,
      type: "PTR",
      class: "IN",
      ttl: 120,
      data: peerName + "." + serviceTag,
    });

    answers.push({
      name: "oPeerName",
      type: "TXT",
      class: "IN",
      ttl: 120,
      data: oPeerName,
    });
    answers.push({
      name: "oAddr",
      type: "TXT",
      class: "IN",
      ttl: 120,
      data: rInfo.address,
    });

    answers.push({
      name: "oFamily",
      type: "TXT",
      class: "IN",
      ttl: 120,
      data: rInfo.family,
    });

    let sizeSum = 0;
    let sizeLimitReached = false;
    multiaddrs
      // mDNS requires link-local addresses only
      // https://github.com/libp2p/specs/blob/master/discovery/mdns.md#issues
      .filter(isLinkLocal)
      .forEach((addr) => {
        // spec mandates multiaddr contains peer id
        if (
          addr.getComponents().findLast((c) => c.code === CODE_P2P)?.value ==
          null
        ) {
          options?.log(
            "multiaddr %a did not have a peer ID so cannot be used in mDNS query response",
            addr,
          );
          return;
        }

        const data = addr.toString();

        // TXT record fields have a max data length of 255 bytes
        // see 6.1 - https://www.ietf.org/rfc/rfc6763.txt
        if (data.length > 255) {
          options?.log(
            "multiaddr %a is too long to use in mDNS query response",
            addr,
          );
          return;
        }

        sizeSum += data.length;

        if (sizeSum > 1000) {
          if (!sizeLimitReached)
            options?.log(
              "total multiaddr data is too long to fit in mDNS query response, skipping %a",
              addr,
            );
          sizeLimitReached = true;
          return;
        }

        answers.push({
          name: "addr",
          type: "TXT",
          class: "IN",
          ttl: 120,
          data,
        });
      });

    options?.log.trace("responding to query");
    mdns.respond(answers);
  }
}

function isLinkLocal(ma: Multiaddr): boolean {
  // match private ip4/ip6 & loopback addresses
  if (isPrivate(ma)) {
    return true;
  }

  return false;
}
