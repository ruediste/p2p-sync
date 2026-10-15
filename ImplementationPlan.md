# DataStorage Implementation Plan

## Goal and Current State

Complete `storage.DataStorage` to meet the storage and transaction guarantees in `doc/storage-sync.md`: a single-file, copy-on-write store with distinct SYSTEM, META, and DATA areas; persistent free-space and reference-count trees; safe deferred reuse; and a superblock update as the commit point.

The current implementation is an incomplete prototype. The focused `DataStorageTest` suite currently has seven failing cases: construction throws a `NullPointerException` because unallocated-list buffers read `superBlock.systemBlockSize` before `superBlock` is initialized. Other major gaps include the unimplemented free-tree allocator, missing transaction recovery and persistence for allocator state, incomplete reference-count handling, and a commit path that publishes the superblock without flushing modified blocks.

Implement the milestones in order. Keep each milestone independently testable and preserve the on-disk format version unless a deliberate format change is required.

## Milestones

### 1. Establish valid construction, format, and reopen behavior

- Remove field-initialization dependence on `superBlock`; initialize per-area transaction buffers after the block sizes are known.
- Make `format` initialize every in-memory structure consistently, including the initial free extents, free-space tree roots, reference-count tree, and transaction state.
- Make `open` reconstruct all persistent and in-memory allocator state from the selected root, and validate magic, version, block sizes, and root offsets before using them.
- Ensure repeated `open`/`format` use does not retain stale maps, trees, buffers, or adjustments from an earlier file.

**Exit checks:** `DataStorageTest` initialization/format and reopen cases pass; malformed or unsupported superblocks fail clearly rather than being interpreted as valid storage.

### 2. Implement area-aware allocation and extension

- Define and enforce the allocator states from the documentation: fetched/pending free blocks, allocated blocks, and meta-reserved blocks. No block may be simultaneously available in incompatible states.
- Implement deterministic free-space-tree iteration from the per-area fetch offset. Refill the in-memory batch from extents, respecting each area's block size and advancing the cursor without revisiting prior extents.
- On exhaustion, extend the file on an area/block-size boundary and add the new extent to that area's free-space tree; then allocate from it.
- Track every normal allocation in `allocatedBlocks` and every meta-tree allocation in `metaReservedBlocks`. Ensure meta-reserved allocations are removed from the ordinary free-block pool and cannot be returned twice.
- Before a fetched block is committed as allocated, remove it from the persistent free-space tree. Coalesce adjacent extents when returning blocks, without merging across area boundaries.
- Reject invalid area sizes, misaligned extents, exhausted/invalid tree state, and arithmetic overflow rather than returning overlapping or out-of-range blocks.

**Exit checks:** tests prove allocations are unique and correctly aligned in all areas, cross the batch boundary, extend storage when exhausted, and are not reused before their allocation is persisted.

### 3. Complete deferred free and crash-recovery state

- Persist all root fields required to find the deferred-freed list and meta-reserved list; the current root serialization omits `unallocatedBlockList` and `metaReservedBlockList`.
- Define the linked-list encoding and ownership for blocks freed during a transaction, including count bounds, buffer rollover, area identity, and empty-list representation.
- At transaction start/open, reclaim only blocks recorded by the previously committed transaction into the correct free-space trees, then reset that prior list. Blocks freed during the current transaction remain unavailable until a later transaction, as required by the design.
- Persist and reload the meta-reserved block list. Preserve reserved blocks across commits, and implement the documented reserve-set rollover/threshold behavior without exposing old reserved blocks early.

**Exit checks:** reopen tests verify allocator state survives restart; blocks freed in transaction N are unavailable through its commit and become reusable only at the documented later boundary; list rollover and multiple areas round-trip correctly.

### 4. Make reference counting complete and transaction-safe

- Specify ownership for every persisted root pointer and tree child. Increment/decrement references consistently when roots or tree nodes are copied, replaced, removed, and retired; do not leave subsystem roots outside the ownership rules.
- Validate area consistency for each block's adjustments and handle increments/decrements that net to zero, newly allocated blocks freed in the same transaction, and last-reference deletion.
- Process refcount changes without mutating the map being iterated. Tree updates and delete callbacks can themselves allocate, free, and enqueue adjustments, so drain work in a controlled fixed-point loop or equivalent staged process.
- Keep the refcount tree's own storage and free-space-tree metadata from causing unbounded recursive refcount updates. Implement the structural-block recovery/accounting strategy explicitly and make it consistent on both format and open.
- Ensure freeing a B-tree node recursively releases child references exactly once; ensure callbacks can read the node before its storage becomes reusable.

**Exit checks:** tests cover shared references, copy-on-write replacement, recursive B-tree deletion, zero/net-zero deltas, newly allocated then discarded blocks, and reopened refcounts.

### 5. Implement a durable atomic commit boundary

- Rework `commit` into explicit stages: finalize pending frees and allocations; update allocator/refcount metadata; serialize all required list and root state; write all dirty data and metadata blocks; flush them to durable storage; write the new root block; then publish and flush the superblock pointer last.
- Do not clear modified-block state or make freed blocks reusable until the new superblock is durably published. `flushModified` currently is not called, and writing a root pointer before its referenced blocks reach disk violates the documented commit guarantee.
- Ensure failures before superblock publication leave the previous root authoritative. Treat failures during/after publication consistently and avoid mutating the live in-memory transaction into an ambiguous half-committed state.
- After a successful commit, establish a clean next-transaction state from the new root while retaining only the documented meta reservations and deferred frees.

**Exit checks:** reopen after a successful commit sees every referenced block; injected failures at each write/flush stage reopen to either the old complete root or the new complete root, never a mixed state.

### 6. Align tests and expose storage invariants

- Extend `DataStorageTest` in place with focused cases for allocation, free-space coalescing, deferred reuse, list persistence, reference counts, and crash recovery; keep tests small and deterministic.
- Add package-private invariant checks or test helpers only where needed to assert that free, pending, allocated, reserved, and referenced sets do not overlap illegally.
- Correct tests to exercise the documented API semantics: freeing should flow through reference-count changes, and persistence assertions should reopen the file rather than inspect only in-memory tree state.
- Cover boundary sizes and custom block sizes, including list capacity rollover and area extension. Keep format compatibility tests for the current version.

**Completion criteria:** `mvn test` passes, storage recovery/atomicity tests pass against reopened files, and each invariant above has a targeted regression test. Run `mvn test -Pit` only if the storage changes affect shared test wiring or integration behavior.

## Key Risks and Decisions

- The source comment describes an `allocatorTreeMode`, but no such mode currently exists. Choose and document the actual strategy for allocator metadata/refcount recursion before implementing milestone 4; do not rely on comments as proof that structural blocks are recoverable.
- The documentation describes list and ownership semantics more completely than the current on-disk structures. Lock down exact encodings and commit/recovery ordering before changing serialization. If persisted layout changes, decide whether to bump `FORMAT_VERSION` or add an explicit migration path.
- `unallocateBlock` currently writes freed offsets into buffers, but no demonstrated commit/reopen path connects those buffers to `RootBlock.freedBlockList`. Tests must verify actual on-disk recovery, not just the in-memory call.
- The minimum useful first slice is milestone 1 followed by the allocator tests in milestone 2. Do not build higher-level filesystem/synchronization features on this storage layer until allocation, refcounts, and crash recovery satisfy their exit checks.
