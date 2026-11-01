# Relays
This file documents the future relay system. Note: it is not implemented yet.

In today's internet, NAT (Network Address Translation) is commonly used to allow multiple devices on a local network to share a single public IP address. This creates challenges for peer-to-peer communication, as devices behind NAT cannot be directly reached from the outside internet. In practice, most nodes are only reachable if they are located in the same local network. Nodes with public IPs are rare, especially for consumer devices, which this project is targeting. Relays help by acting as intermediaries, forwarding traffic between peers that cannot connect directly.

To avoid the need for individuals to set up their own relay servers, and to avoid the cost of running dedicated relay infrastructure, this project aims to use existing free services provided by the major cloud providers as relays.

To build a system which is flexible in terms of relay service capabilities, it is important to first formalize the various communication/relay patterns.

## Communication Patterns
We focus on the smallest common denominator: A relay can store blobs (typically files) under a chosen name/id. The available blobs can typically be listed. If this is not possible, index files can be used instead.

A relay can be used by multiple nodes with writing access to the relay. Relays can often be read without authentication. (Relays with restricted read access are possible).

The relays introduce significant latency between one node writing data and another node observing the changes (10s of seconds)

Given these constraints, there are two main patterns:

- publish: data is written to the relay, not directed at any specific node; for example: operation logs
- point to point: a bidirectional connection is established between two nodes, each node possibly writing to a different relay. Used for example to transfer sync unit snapshots when a node joins a sync unit