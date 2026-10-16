# Data Storage Implementation Plan

Scope: the B+ tree and single-file storage engine in `storage/BTree.java` and `storage/DataStorage.java`, compared with the **Data Storage** sections of `doc/storage-sync.md`. Blob storage and higher-level synchronization structures are consumers of this layer and are out of scope here.

## Comparison

- **B+ tree:** Core copy-on-write tree behavior, variable-sized values, iteration, and reference callbacks are implemented. A tree root can represent a snapshot, but the storage API does not yet define snapshot lifetime or reader ownership.
- **File format:** Formatting, opening, validation of basic header fields, root serialization, committed file length, and tail truncation are implemented. Documentation does not match the current encoding: block sizes live in the superblock; `InfoBlock` currently stores `inlineDataThreshold`; and the serialized root has subsystem pointers, file length, and deferred-list heads not shown in the documented structure.
- **Free-space allocation:** Three area trees, batched allocation, a cursor, extent splitting/coalescing, META reservations, and file extension are implemented. The documentation's simpler “allocate from the beginning” description misses the actual cursor/batch behavior and reservation lifecycle.
- **Reference counts and deferred freeing:** The basic adjustment flow and per-area deferred-free lists are implemented. Deferred reference-count recovery is unfinished: `loadDeferredRefCountAdjustmentList()` is a stub; the persistence routine currently iterates the META reserved-block set instead of the adjustment records; and deferred reserved-list deletion contains a null callback TODO.
- **Commit safety:** The implementation writes updated blocks and then overwrites the superblock root pointer in place. Tests cover rollback of uncommitted writes, but not interruption during commit, torn root publication, or durable write ordering. This does not yet establish the documented atomic-commit guarantee, especially for power loss.
- **Concurrency:** The documentation promises a single writer and multiple readers, but `DataStorage` has no reader snapshot API or writer coordination.
- **Implementation comments:** The `DataStorage` class comment describes `allocatorTreeMode` and `freeSpace` maps that do not exist in the current implementation; update it along with the design documentation.

## Plan

### 1. Align The Format Contract

- Document the exact current superblock, root-block, and info-block fields in serialization order, including block sizes, file length, and deferred-list pointers.
- Describe current allocation behavior and transaction-local sets accurately: refill batch and cursor, ordinary allocated blocks, current/old META reservations, modified blocks, and deferred frees.
- State invariants separately for transaction-local and committed state. In particular, specify when reserved META blocks are still represented in a free extent and when they must be removed.
- Remove stale allocator descriptions from `DataStorage` comments. Do not change the on-disk encoding as part of documentation cleanup.

**Complete when:** every serialized field and allocator invariant has one unambiguous description matching the implementation.

### 2. Finish Deferred Ref-Count Persistence

- Specify a restart-safe record format for each deferred adjustment. Persist only durable data; do not attempt to serialize Java callbacks. Define how the area and deferred deletion action are restored or derived.
- Implement list loading with range/count checks, duplicate detection, cycle detection, and validation of record contents.
- Fix persistence to serialize the adjustment map (not `deferredMetaFreeTreeRemoveBlocks`) and size/roll over blocks based on the actual record format.
- Complete deletion of old deferred lists and their referenced blocks with valid ownership/deletion behavior.
- Define publication and retirement order so a crash cannot lose, replay twice, or prematurely reclaim adjustments.

**Complete when:** tests cover empty, one-record, exact-capacity, multi-block, reopen/replay, malformed, and retired lists; repeated commits neither lose nor double-apply adjustments.

### 3. Validate Allocator And Ref-Count Invariants

- Add transaction-level tests for allocate/cancel, free/reallocate across commits, adjacent extent merge, reserved META rotation, and deferred-free rollover in each area.
- Verify each allocation is removed exactly once from its persistent extent and that no block is simultaneously free, allocated, reserved, or deferred-free.
- Add small-model randomized operation sequences, reopening at commit boundaries and comparing free extents and reference counts with the model.
- Validate all persisted offsets, ranges, alignments, and counts before reading or changing referenced blocks; reject malformed files deterministically.

**Complete when:** focused storage tests and model-based sequences pass through repeated commit/reopen cycles, including forced rollover and small block sizes.

### 4. Establish And Test Commit Recovery

- Define whether commit guarantees process-crash consistency only or power-loss durability as well.
- Make the protocol meet that guarantee. In-place superblock replacement alone does not prove atomic publication; use a recoverable publication scheme (such as redundant validated superblocks or a journal) if required, with explicit write ordering and synchronization.
- Add fault injection around block/tree writes, deferred-list writes, root serialization, and superblock publication. Reopen after every injected failure and verify recovery selects a complete committed state and does not reuse reachable blocks.
- If the encoding must change, increment `FORMAT_VERSION` and define compatibility behavior for existing files.

**Complete when:** every commit interruption point has a recovery test, and the durability claim in `storage-sync.md` matches the actual protocol.

### 5. Define Reader Snapshots And Writer Ownership

- Provide a read-only snapshot handle that captures a committed B-tree/root state and defines how its references are retained and released.
- Enforce one writer and prevent readers from observing mutable transaction-local state. Decide whether writer exclusion must cover multiple processes as well as threads.
- Test that readers retain their original view across writer commits and that blocks are reclaimed only after the last reader releases its snapshot.

**Complete when:** the single-writer/multiple-reader contract is an explicit API guarantee with overlapping-reader/commit tests.

## Validation Gates

- Run focused `BTree` and `DataStorage` tests after each phase, then `mvn test` before considering the storage layer complete.
- Keep format validation, allocator/reference-count invariants, and crash recovery as distinct test groups so failures identify the broken contract.
- Update `doc/storage-sync.md` in the same change as each storage behavior; keep higher-level blob and synchronization formats marked as consumers/design until separately implemented.
