# Implementation of Basic Data Sharing

The goal of this phase is to get some basic data sharing running. The data is a piece of text. If conflicting edits occur, the texts are concatenated, the text with the lower peer id first.

## Persistence

All data is persisted into the `data` folder (add to `.gitignore`). The data is stored as files. The all persisted data structures are serialized protobuf messages. Create two repository base classes `DataRepository` and `SingletonDataRepository`. When writing, first write into a temporary file in the same directory, then do an atomic move. The repositories provide protobuf serialization/deserialization.

SingletonDataRepository: write to a file (single file per repo)

DataRepository: The documents are stored by key. The key is a binary sequence. Hash the key and store the file in a two layer directory tree, each layer having 256 entries. It should be possible to iterate over all existing documents, but not in key order (due to the hashing used during storage). Each DataRepository has it's own subclass.

Add a qualifier to the path in the data folder which can be specified when starting the application (`--node a`), such that multiple instances can be run concurrently. The default qualifier is `default`.

## Simple NodeUserConfiguration

To get started, each node gets a `NodeUserConfiguration` (singleton). Add a `SingletonDataRepository`. The management of the nodes by the users is not yet in scope. Initialize all keys when the configuration does not exist yet.

## StorageBlock Repository

Add a storage block repository. Key: hash of block.

## StorageUserRoot

Add a repo for `StorageUserRoot`. Key: userId. Initialize if missing (with 5 shards) for the single user. Create an empty `DataUserRoot`

## Communication

Add a data synchronization p2p protocol. Whenever connecting to a new node, the initiating node sends the local user id. If the user ids do not match, the connection is closed. Otherwise, the responding node initiates a synchronization cycle.

A cycle is always performed by the initiating node, but the responding node can ask the initiating node to start a cycle. After asking for a cycle, the asking node is guaranteed that a cycle will start sometime in the future, after the current cycle is completed.

A cycle performed by the initiating node has the following steps:

- send the local clock to the responding node
- responding node

## Future extension points (explicitly not part of this plan)

- Kademlia DHT, `identify` protocol, connection gating/backoff, `AddressBook`
  persistence, DNS multiaddr resolution.
- Relay/circuit-v2, AutoNAT, DCUtR (hole punching) — mentioned in the project wiki's long-term
  P2P mechanism list but out of scope until basic connectivity is solid.
