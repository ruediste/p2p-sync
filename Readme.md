```
DEBUG="*libp2p:*" node dist/index.mjs add
DEBUG="*libp2p:*" node dist/index.mjs get
```

https://ipfs.io/ipfs/baguqeerap2d52pc5kg5znbb7yocrp4keqxihhon5wgzeqmtwzcg6qkllaiaa

https://check.ipfs.network/?cid=baguqeerap2d52pc5kg5znbb7yocrp4keqxihhon5wgzeqmtwzcg6qkllaiaa&multiaddr=&ipniIndexer=https%3A%2F%2Fcid.contact&timeoutSeconds=30&httpRetrieval=on

```
npm run build && DEBUG="*libp2p:identify*,:trace" npm run start-get 2>&1 | tee log.txt
npm run build && DEBUG="*libp2p:auto-nat-v2:client*,:trace" npm run start-get 2>&1 | tee log.txt
npm run build && DEBUG="*libp2p:mdns*,:trace" npm run start-get 2>&1 | tee log-get.txt
npm run build && DEBUG="*libp2p:mdns*,:trace" npm run start-add 2>&1 | tee log-add.txt
npm run build && DEBUG="libp2p:dht-amino*" npm run start-add 2>&1 | tee log-add.txt
npm run build && DEBUG="libp2p:circuit-relay:transport*,:trace" npm run start-add 2>&1 | tee log-add.txt
npm run build && DEBUG="" npm run start-get 2>&1 | tee log-get.txt

```

```
2026-03-07T15:49:04.263Z libp2p:auto-nat-v2:client:error could not verify addresses - InvalidParametersError: Multiaddr / was not an IPv4, IPv6, DNS, DNS4, DNS6 or DNSADDR address
    at getNetConfig (file:///home/ruedi/git/js-libp2p/packages/utils/dist/src/multiaddr/get-net-config.js:47:19)
    at file:///home/ruedi/git/js-libp2p/packages/protocol-autonat-v2/dist/src/client.js:291:20
    at Array.some (<anonymous>)
    at AutoNATv2Client.verifyExternalAddresses (file:///home/ruedi/git/js-libp2p/packages/protocol-autonat-v2/dist/src/client.js:290:45);
```
