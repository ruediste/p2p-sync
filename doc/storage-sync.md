# Storage and Synchronization

## Main Concepts

### User

At its core, a user is an identity, represented by a cryptographic key pair. The public key is used as the unique identifier for the user, while the private key is used to sign modifications to the user data. The user data contains all the information about the user, such as their files, messages, and contacts. The user data is stored in a `StorageUserRoot` block on the storage layer, which is encrypted and replicated across nodes.

### Device and Node

The system lives on devices, which are the physical hardware that runs the software. A device can be a laptop, a phone, or a home server. Each device runs a node, which is the software that manages the storage and replication of the user data. Similar to a user, a node is also identified by a key pair. Typically a node also allows the user to interact with the data, for example by providing a file system interface or a messaging interface.

A node can be configured to replicate data for one or more users, and each user can have multiple nodes replicating their data. The nodes communicate with each other to synchronize the data and ensure that it is available even if some nodes go offline.

During bootstrap, a node generates a node management key. This key can be used by users to encrypt and sign a `NodeUserConfiguration`, instructing the node to replicate the data of a user, optionally restricted by a quota. Updating the quotas is part of the normal user-data merge flow, ensuring that all participating nodes converge on the same allocation. When multiple users configure quotas for the same user, the most generous quotas take effect.

The nodes periodically publish storage claims, containing information about the data blocks they have stored. This information is used, together with the node trusts of the respective user, to control data replication.


## Data Storage
The system uses a custom storage system designed to provide crash protection and snapshot features. The whole system is designed around a single writer/multiple readers model. The system follows a copy-on-write approach, conceptually heavily inspired by btrfs. The main primitive used is a B+ tree with dynamic value size and shadowing.

The following main concepts are used:
- `FreeSpaceTree`s: There are separate free space trees for data, metadata, and system blocks. They keep track of the free space in the respective area. 
- `RefCountTree`: Keeps track of the reference counts for each block, ensuring that blocks are not prematurely freed while still in use.

### Data Areas
The storage is organized into three different areas: system, meta and data. Each area has its own block size and its own free space tree. When an area runs out of free blocks, the data file is extended and the new blocks are added to the respective free space tree.

 ### Data File and Root Structures
All data is stored in a single data file. It starts with the superblock:

```C
// padded to 4KB, located at the beginning of the data file
struct Superblock {
    byte magic[16]; // 0xd61b52d56dc67e4ffd40c6d8f5ee03db
    uint64_t version; // Version of the file format
    uint64_t rootBlock;   // offset of the root block within the file
};
```

The root block contains pointers to the various trees and the info block. A new root block is created whenever a transaction is commited. The commit happens at the moment the superblock is updated with the offset of the new root block.

```C
// system space
struct RootBlock {
    uint64_t dataFreeSpaceTree; 
    uint64_t dataFreeList; 
    uint64_t dataReservedList; 

    uint64_t metaFreeSpaceTree;  
    uint64_t metaFreeList; 
    uint64_t metaReservedList; 

    uint64_t systemFreeSpaceTree;  
    uint64_t systemFreeList; 
    uint64_t systemReservedList; 
    
    uint64_t systemKvTree;  
    uint64_t refCountTree;    
    uint64_t infoBlock;

    // various data per sub system
};
```

The info block is separated from the root block as it does not change frequently. It contains core metadata about the file system.
```C
// system space
struct InfoBlock {
    uint32_t dataBlockSize; // typically 4KB
    uint32_t metaBlockSize; // typically 16KB
    uint32_t systemBlockSize; // typically 4KB

    // various data per sub system
};
```

### Free Space Trees
The free space trees simply keep track of the free areas within the data file. Free space is allocated in chunks. The space managed by a single free space tree is split into equal sized blocks to avoid fragmentation issues. Separate free space trees are maintained different purposes, allowing appropriate block sizes to be used and to group related data.

```C
struct FreeSpaceKey {
    uint64_t offset; // offset of the free block within the data file
};
struct FreeSpaceEntry {
    uint64_t size;   // size of the free block, in bytes. Multiple of the block size managed by this free space tree.
};
```

When allocating space, the space is allocated from the beginning of the tree. When blocks are freed, neighboring free blocks are merged to form larger contiguous free areas, reducing the number of entries in the free space tree. 

### Transaction and Block Management
Changes to the data file are managed within transactions. During a transaction, blocks used by the previous transaction are never modified. All changes are written to newly allocated blocks. For efficient and correct block management, a few in-memory structures are maintained:

- `ModifiedBlocks`: A hash map keeping track of all blocks modified during the current transaction. The key is the block offset, and the value contains the modified block data. It acts as a write cache which ensures that blocks which are only used temporarily are never written to the disk. 
- `FreeBlocks`: To enable efficient block allocation, and to avoid repeatedly modifying the free space trees, a set of free blocks is maintained in memory. When the set becomes empty, a new batch of free blocks is fetched from the free space tree.

  The block offset of the highest fetched block is kept (fetch offset), and block fetching always continues after the fetch offset. As no blocks after the fetch offset can be allocated in a transaction, all blocks in the free tree after the fetch offset are guaranteed to be free. The tree is not modified when fetching the blocks. 

- `AllocatedBlocks`: When a block is allocated, it is removed from the `FreeBlocks` set and added to the `AllocatedBlocks` set. When a block is no longer needed in a transaction (unallocated), it is removed from the `AllocatedBlocks` set and added back to the `FreeBlocks` set. During commit, the `AllocatedBlocks` are removed from the free space tree.

- `MetaReservedBlocks`: 
All free space trees are stored in the meta area. When modifying the meta area free space tree, new blocks from within the meta area are required, which in turn would require modifying the meta area free space tree. To break this cycle, blocks for the meta area free space tree are allocated using the `MetaReservedBlocks`. This is a set of reserved blocks is maintained in memory. The blocks are allocated from the `FreeBlocks` set.

  The `MetaReservedBlocks` are kept in memory across transactions commits. This implies that while filling the `FreeBlocks` set from the free blocks tree, the reserved blocks are not considered available for allocation.

  On commit, the reserved block list is stored as linked list. This happens very late in the transaction process and uses some additional reserved blocks. Before writing the list, it is ensured that there are enough free blocks available to complete the operation by growing the meta area.

  When the number of reserved blocks reaches a threshold, block allocation switches to a new set of reserved blocks. The old reserved blocks are then removed from the free space tree. (while doing so, the old reserved blocks still count as non-free for allocation purposes)

  This mechanism is only used for the meta free space tree.
- `UnallocatedBlocks`: When a block is no longer referenced (refcount hits 0), it can be unallocated. However, care has to be taken not to reuse a block used in the previous transaction in the current transaction, as the block is still used by the last committed transaction. 

  Therefore, unallocated blocks are tracked in a separate list (persisted on disk). The list is committed as well. When a transaction starts, first all unallocated blocks from the last transaction are added back into the corresponding free tree.

To ensure correctness of all of these structures and operations, it is helpful to note that when adding blocks form the free space tree to the `FreeBlocks`, they are "pending". These blocks can then be passed on to the `AllocatedBlocks` or the `ReservedBlocks` as needed. It has to be ensured, that before definitely using these "pending" blocks, they are properly removed from the free space tree.

### Reference Count Tree
The reference count tree simply keeps track of the reference counts of blocks within the data file. Each entry in the tree corresponds to a block and stores the number of references to that block.
```C
struct RefCountKey {
    uint64_t offset; // offset of the block within the data file
};
struct RefCountEntry {
    uint64_t refCount; // reference count of the block
};
```

### Blob Data Storage
For various purposes, data blobs need to be stored efficiently, either as inlined data for small blobs or in external blocks for larger blobs. This is accomplished by using keys with the following structure
```C
struct BlobDataKey {
    uint8_t id[];    // some identifier, depending on the containing tree
    uint8_t type;     
    uint32_t offset;  // byte offset within the blob
};
```

Then there are two types defined: an `INLINE` and a `EXTENT` type. The exact values and names depend again on tree and purpose.

For short data, the blob content can be stored directly within the B-tree leaf as inlined data, avoiding the need for external block references. The size of the inlined data is captured by the B-tree item itself.

```C
// max 4KB
struct InlineData {
    byte data[];    // the file content
};
```

Multiple inline data items can exist for a single blob, using the offset in the key to indicate the byte position within the blob.

For data larger blobs (typically above 16KB), the blob content is stored in external blocks, referenced through extent items.

```C
struct ExtentData {
    uint64_t blockOffsets[]; // array of offsets to the data blocks containing the file content
};
```

The number of block offsets is derived from the size of the data stored in the B-tree item. The end of the blob in the last block is determined by some metadata.

### System KV Tree
The system key value tree is a blob store for various system information. Typically, the values are JSON serialized. It is intended for relatively small values, loaded into memory in one batch. 

The keys of all entries have the same size and structure:

```C
// meta space
struct SystemKvTreeKey {
    uint32_t id;    // blob id
    uint8_t type;     // type of the entry, see FsTreeEntryType
    uint32_t offset;  // depends on the type of the entry
};

enum SystemKvTreeEntryType {
    BLOB_HEADER,  
    INLINE_DATA, // inlined data of a blob
    EXTENT_DATA,    // reference to blocks containing the blob data
};
```

Every blob has exactly one header (`BLOB_HEADER`, offset 0) storing the basic metadata.
```C
struct BlobHeader {
    uint64_t size;       // size of the file in bytes
};
```

The data is stored in `INLINE_DATA` and `EXTENT_DATA` entries.



## Globally Unique Identifiers (GUID)
When creating a new file or directory a globally unique identifier (GUID) has to be generated. We use the SHA-256 (32 bytes) hash of the public key of the node creating the entity, followed by a per node counter (64 bits, 8 bytes). This is the full GUID, consisting of 40 bytes in total.

To avoid storing the full SHA-256 repeatedly, each node maintains a private table (GUID Mapping Tree), mapping the node's public key hash to a 32 bit identifier (4 bytes). This reduces the short GUID size to 12 bytes (4 bytes for the node identifier and 8 bytes for the counter). Note that the short GUID is only meaningful within the context of a single node.
    
When synchronizing operations between nodes, the full GUID is used for simplicity. When exchanging a full file system snapshot, the short GUIDs may be used, together with the mapping table.

```C
struct FullGUID {
    byte nodeHash[32]; // SHA-256 hash of the node's public key
    uint64_t counter;  // per node counter
};

struct ShortGUID {
    uint32_t nodeId;   // 32 bit identifier for the node
    uint64_t counter;  // per node counter
};
```

The GUID mapping tree is a B+ tree that maps the full node hash to the 32 bit node identifier used in short GUIDs. It is private to a node and not replicated.

```C
struct RootBlock {
    ...
    uint64_t nextGuidCounter;
    uint64_t guidMappingTree;
};

// system space
struct GuidMappingKey {
    byte nodeHash[32]; // SHA-256 hash of the node's public key
};

struct GuidMappingValue {
    uint32_t nodeId;   // 32 bit identifier for the node
};
```

When a node first encounters the hash of another node (directly, or through a short GUID received in a full snapshot), it allocates a new `nodeId` (max existing nodeId plus 1). Note that the node entries are never removed (they might live indefinitely in file or directory ids), thus the mapping table grows indefinitely.

## Distributed Clock
The nodes maintain a 64 bit distributed lamport clock to order events across the system. Each node increments its local clock for every operation it performs and includes the clock value in the operation metadata, combined with the node key hash, used as tie breaker. The short node identifiers cannot be used here, as they would change the ordering of events which would lead to inconsistencies.

```C
struct RootBlock {
    ...
    uint64_t distributedClock;
};

struct OperationId {
    uint64_t lamportClock;
    uint8_t nodeHash[32]; // SHA-256 hash of the node's public key
};
```

Whenever a node receives data from another peer, the lamport clock value of the peer is also received, and the local node uses the maximum value of the local clock and the peer clock.

## Synchronization Algorithm
We use Kleppmanns approach for managing concurrent modifications and ensuring consistency across distributed nodes. All updates to the file system are represented in operations, which are totally ordered using the distributed lamport clock combined with the node key hash as a tie breaker. Each node seeks to obtain all operations from all other nodes. When all nodes have obtained all opertions, they converge to the same state.

### Active Nodes
For various purposes, the system keeps track of the set of active nodes. This set includes all nodes that are currently participating in the network and are expected to contribute to the synchronization process. Note that this does not mean the nodes are currently operating or reachable. A node can go offline for days or weeks and still be considered active.

Each node stores the set of active nodes. For each node, some additional data is maintained (see below). The active node sets are exchanged and merged between nodes to maintain a consistent view of the network.

The active node set is stored in the system KV tree.

```ts
type ActiveNodeSet = {
    version: {
        nodeId: string // SHA256 of node public key
        clock: number // distributed clock value
    },
    leavingProcess:{
        phase: 'A' | 'B' | 'I'; // current phase of the leaving process
        processId: uint8_t[32]; // unique identifier for the leaving process
        leavingNodeId: uint8_t[32]; // SHA256 of the leaving node's public key
        lastOperationClock: number; // clock value of the latest known operation from this node
        lastOperationClockSourceNode: string; // node which observed the maximum operation clock
        confirmedBy: Set<string>; // nodes that have confirmed the leaving status of this node
    },
    nodes: Record<string, // SHA256 of node public key
    {
        lastSeen: timestamp; // timestamp when the node has last been seen
    }
}
```

The last seen timestamps are stored in the `ActiveNodeInfo`. It contains local information for the active nodes. The last seen timestamps are copided into the `ActiveNodeSet` when the `lastSeen` there is one day older than the local last seen timestamp;

```ts
// stored in the system KV tree
type ActiveNodeInfo = {
    [key: string]:{ // SHA256 of node public key
        lastSeenTimestamp: timestamp; // timestamp when the node has last been seen
        maximumOperationClock: number; // maximum operation clock observed for this node
    }
}
```

#### Versioning
The node sets to not change frequently. To avoid repeated transfer between nodes, whenever a set changed, the distributed clock is incremented and included in the metadata of the set, toghether with the node identifier. If a node receives a superset of it's local set, it replaces it's own set with the received set, including clock and node identifier metadata. Before transferring the whole sets, the clock and node identifiers of the sets are compared. If they are equal, no transfer is necessary.

#### Joining
When a node wants to join the network, it first contacts an existing node and requests a the start of a join transaction. The existing node will stop updating its active node set, operation retrieval, and will not perform any log truncation until the join process is completed. When the joining node has completed downloading the earliest snapshot and all operations from the existing node, the existing node places the joining node in it's active node set and finish the joining transaction.

#### Leaving
To track the leaving process of a node, a three phase approach is used. Initially, all nodes are in phase I (idle). When a node is marked leaving manually or automatic (for example due to prolonged inactivity), some other node marks the node as leaving in its active node set. This starts phase A. In this phase, all nodes confirm the reception of the leaving status for that node and make sure, they have received all operations from that node. Once a node has accumulated all confirmations, that node starts phase B. In phase B, all nodes remove the leaving node from their active node set and confirm the reception of phase B. Again, once a node has collected all confirmations, it starts phase I, in which any node can initiate a new phase A.

This protocol ensures that nodes are leaving in lockstep. When a node starts phase A, a random process ID is generated. When two phase A processes are initiated concurrently, the process with the higher process ID is aborted. 

When merging an active node set, each node checks the last operation locally available for the leaving node. If it matches the value in the leaving entry, the node confirms the reception of the leaving status for that node. If the local clock for the node is below the clock in the leaving entry, confirmation is postponed until the corresponding operations have been retrieved. If the local value is above the clock in the leaving entry, the clock in the entry is updated and the existing confirmations are cleared. 

If the last operation clock source node is leaving, the confirmations are cleared as well and the local clock is used. This avoids stalling the leaving process, if an operation is only available on a node that became inaccessible.

Once all nodes have acknowledged the leaving status, the B phase is started, in which all nodes remove the leaving node from their active node set. Once a node has accumulated all phase B confirmations, the I phase is started, in which a new process can be initiated.

### Operation Synchronization
The goal of each node is to retrieve all operations of all other nodes. Operations are always retrieved in-order per peer node (ordered by the distributed clock), without gaps. Thus to keep track of the progress, a node simply has to keep track of the maximum operation retrieved per node. (Stored in the active node info)

To request operations from a peer, a node sends a list of node ids / clock pairs. The peer responds with the next operations (per node) which are stored on the peer.

More concretely, each node maintains an `OperationStagingTree`, structured exactly like the `OperationTree` (see corresponding section).
```C
struct RootBlock {
    ...
    uint64_t operationStagingTree;
    uint64_t operationRetrievalTree;
};
```

It contains all operations which have been retrieved completely, but not yet merged into the file system. The staging tree is merged with the file system periodically.

When a node requests operations from a peer, the peer responds with a list of operations. All operations but the last have to be complete. The data of the last operation may be truncated. All complete operations are added directly to the `OperationStagingTree` and their corresponding maximum retrieved clock values are updated in the `ActiveNodeInfo`. 

Incomplete operations are stored in the `OperationRetrievalTree`. It contains at most one incomplete operation per node. Additional requests are sent to the same or other peers, requesting the remaining data of the incomplete operation. Note that no additional operations from a node are retrieved until its incomplete operation has been fully retrieved.

### Log Truncation
For log truncation, we need to determine the causally stable timestamp. This can easily be achieved by creating operation timestamps from the `maximumOperationClock` of the active node info (combined with the corresponding node id) and taking the minimum across all active nodes. Then a snapshot is created for that timestamp, and all operations older than the causally stable timestamp can be safely truncated.

## File System and Operation Storage
This section describes how the file system state and the file system operations are managed and stored. The following data structures are used:

- `FileSystemTree`: Represents the hierarchical structure of the file system, keeping track of directories and files within the data file.
- `OperationTree`: Keeps track of the operations performed on the filesystem, allowing concurrent modifications by multiple nodes based on the Kleppmann tree move approach.
- `SnapshotTree`: References a file system snapshot after a certain number of operations in order to enable quick operation revert/redo.

### Root Block Information
```C
struct RootBlock{
    ...
    uint64_t fileSystemTree;  
    uint64_t operationTree;  
    uint64_t snapshotTree;  
    ...
}

struct InfoBlock {
    ...
    uint32_t inlineDataThreshold; // typically 16KB
    uint32_t maxExtentDataBlockSize; // typically 4KB
    ...
};
```

### File System Tree
A file system tree contains a full representation of the file system's hierarchical structure, including files and directories. It contains different types of entries. The keys of all entries have the same size and structure:

```C
struct FsTreeKey {
    uint8_t guid[12]; // short GUID of the file or directory
    uint8_t type;     // type of the entry, see FsTreeEntryType
    uint32_t offset;  // depends on the type of the entry
};

enum FsTreeEntryType {
    FILE_HEADER,  // header of a file or directory
    DIR_HEADER,   // header of a directory
    DIR_ITEM,    // name lookup entry of a directory
    DIR_INDEX,   // sequential entry of a directory
    INLINE_DATA, // inlined data of a file
    EXTENT_DATA,    // reference to blocks containing the file data
};
```

Every file and directory is uniquely identified by its GUID, which serves as the `guid` component of the key. All entries belonging to the same file or directory are clustered together in the tree, sorted by `type` and `offset`.

#### File Representation

Every file has exactly one header (`FILE_HEADER`, offset 0) storing the basic metadata.
```C
struct FileHeader {
    uint8_t parentId[12];      // short GUID of the parent directory of the file
    uint64_t size;       // size of the file in bytes
    uint64_t creationTime;      // creation time
    uint64_t modificationTime;      // last modification time
};
```
The data is stored using `INLINE_DATA` entries for short data and `EXTENT_DATA` entries for longer data.

#### Directory Representation
Every directory has exactly one header (`DIR_HEADER`, offset 0) storing the basic metadata.
```C
struct DirHeader {
    uint8_t parentId[12];      // short GUID of the parent directory; For the root, the GUID of the root itself (loop)
    uint64_t creationTime;      // creation time
    uint64_t modificationTime;      // last modification time
};
```

Every entry in a directory is represented by two entries, one for the name lookup (`DIR_ITEM`) and one for the sequential index (`DIR_INDEX`).

For the `DIR_ITEM` keys, the `offset` contains a CRC32c hash of the entry name, enabling fast name lookups. Do handle hash collisions, the value contains a list of names and file/dir references. 

```C
// total size limited to 4KB, otherwise spill tree is used and count is set to 0
struct DirItem {
    uint64_t spillTreeRoot; // offset to the spill tree containing additional name entries if the inline array is full. Set to 0 if not used. 
    uint8_t nameCount; 
    NameEntry nameEntries[];   // array of name entries
};

struct NameEntry {
    uint8_t locationGuid[12]; // short GUID of the referenced file or directory
    uint8_t locationType;     // type of the referenced inode, `FILE_HEADER` or `DIR_HEADER`
    uint8_t nameLength;       // length of the entry name
    uint8_t name[];           // the entry name
};
```

The name spill tree uses a SHA-256 hash of the entry name as the key, combined with an incrementing key (32-bit, incrementing the max key seen so far for this particular hash). The long hash value prevents CRC32 hash collision attacks combined with birthday attacks.

```C
struct NameSpillTreeKey {
    uint8_t hash[32]; // full SHA-256 hash of the entry name (256-bit)
    uint32_t incrementingKey; // incrementing key for this particular hash
};

struct NameSpillTreeValue {
    uint8_t locationGuid[12]; // short GUID of the referenced file or directory
    uint8_t locationType;     // type of the referenced inode, `FILE_HEADER` or `DIR_HEADER`
    uint8_t name[];           // the entry name
};
```

The length of the name is implicitly determined by the size of the tree item.

For the `DIR_INDEX` keys, the `offset` contains a sequential counter based on the creation order of the entries, enabling efficient sequential reads. The offsets are monotonically increasing and derived from the last `DIR_INDEX` entry already present. They allow efficient iteration over the directory entries in the order they were created, improving data locality when accessing the referenced files or directories.

```C
struct DirIndex {
    uint8_t locationGuid[12]; // short GUID of the referenced file or directory
    uint8_t locationType;     // type of the referenced inode, `FILE_HEADER` or `DIR_HEADER`
    uint8_t name[];           // the entry name
};
```
The length of the name is implicitly determined by the size of the tree item.

### Operation Tree
The operations applied to the file system are stored in the operation tree. The key is the operation id followed by an entry type, ensuring that operations are ordered correctly according to the distributed Lamport clock and node identifier.

```C
struct OperationKey {
    uint8_t operationId[40]; // the operation id
    uint8_t entryType;    // the entry type
    uint64_t offset;        // depending on the entry type
};

enum OperationEntryType {
    CREATE_FILE, // create a new file, including its initial content
    MODIFY_FILE, // modify the metadata of an existing file
    WRITE_FILE, // write data to an existing file

    CREATE_DIR, // create a new directory
    MODIFY_DIR, // modify the metadata of an existing directory

    MOVE, // move a file or directory to a new location
    RENAME, // rename a file or directory

    DELETE, // delete a file or directory
    INLINE_DATA, // store inline data for an operation
    EXTENT_DATA, // store data in external blocks for an operation
};
```

#### Create File Operation
```C
struct CreateFile {
    FILE_HEADER fileHeader;   // the file header containing metadata (size, timestamps, ...)
    uint8_t name[];         // the file name
};
```
The name length is derived from the value length of the B-tree entry storing the operation. The parent directory GUID is stored in the `fileHeader` of the file.

The data of the file is stored in `INLINE_DATA` or `EXTENT_DATA` entries.

#### Modify File Operation
```C
struct ModifyFile {
    uint8_t id[12]; // short GUID of the file to be modified
    FILE_HEADER fileHeader;   // the updated file header containing metadata (size, timestamps, ...)
};
```

#### Write File Operation
```C
struct WriteFile {
    uint8_t id[12]; // short GUID of the file to be written
    uint64_t offset; // the offset within the file where the data should be written
    uint64_t length; // the length of the data to be written
};
```
The data to be written is stored in `INLINE_DATA` or `EXTENT_DATA` entries.

#### Move Operation
```C
struct Move {
    uint8_t id[12]; // short GUID of the file or directory to be moved
    uint8_t newParentGuid[12]; // short GUID of the new parent directory
};
```

#### Rename Operation
```C
struct Rename {
    uint8_t id[12]; // short GUID of the file or directory to be renamed
    uint8_t newName[];     // the new name
};
```

The name length is derived from the value length of the B-tree entry storing the operation.

#### Delete Operation 
```C
struct Delete {
    uint8_t id[12]; // short GUID of the file or directory to be deleted
};
```

### Snapshot Tree
In order to incorporate operations received from other nodes, the state of the file system has to be reversed to a state before the new operations are applied. This is achieved by periodically keeping snapshots of the file system.

The key of each entry is an operation id. The value is the root block offset of the file system root block, representing the state of the file system AFTER applying the operation. 

When a fresh file system is created, the lamport clock is set to 1, and an initial snapshot at operation 0 of the empty file system is created. This ensures that there is always a baseline snapshot to revert to when incorporating new operations.

```C
struct SnapshotKey {
    uint8_t operationId[40]; // the operation id corresponding to this snapshot
};

struct SnapshotValue {
    uint64_t rootBlockOffset; // the root block offset of the file system root block
};
```
