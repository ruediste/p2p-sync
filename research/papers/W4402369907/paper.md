arXiv is now an independent nonprofit! [Learn more] × 

 [ Back to arXiv ] [Why HTML?] [ Report Issue] [ Back to Abstract ] [ Download PDF] [ ] [ ] 
- [Abstract.] 
- [1 Introduction] 
- [2 From sequential data types to CRDTs] 
- [2.1 From sequential to concurrent data abstractions] 
- [Data abstractions] 
- [Sequential data types] 
- [Concurrent data types] 
- [Lock-free and wait-free data structures] 
- [2.2 Strongly consistent replication] 
- [Linearizability] 
- [Replicated state machines] 
- [2.3 The CAP theorem and consistency models] 
- [The CAP theorem] 
- [CP vs AP] 
- [Consistency models] 
- [Causal Consistency] 
- [Convergence] 
- [Spatial scalability] 
- [2.4 Optimistic replication and CRDTs] 
- [Optimistic replication] 
- [Conflict-free Replicated Data Types] 
- [ADTs vs objects] 
- [Operation-based vs state-based approaches] 
- [3 Operation-based CRDTs] 
- [3.1 Execution model and concurrency semantics] 
- [Standard execution model of operation-based CRDTs] 
- [Reusing sequential data types with commutative operations] 
- [CRDTs with non-commutative operations] 
- [Defining concurrent semantics] 
- [3.2 Observed-cancel CRDTs] 
- [Observed-cancel semantics] 
- [Observed-remove set] 
- [3.3 Beyond sequential semantics and API] 
- [Lack of equivalence to sequential executions] 
- [Multi-value register] 
- [4 Pure operation-based CRDTs] 
- [Pure op-based CRDTs] 
- [Pure implementations of commutative data types] 
- [4.1 Tagged Causal Broadcast and Causal Stability] 
- [Tagged Causal Broadcast (TCB)] 
- [Causal Stability] 
- [The many faces of stability] 
- [Distributed algorithm for pure op-based CRDTs] 
- [4.2 Resorting to a partially ordered Log] 
- [Naive PO-Log based implementations] 
- [PO-Log based observed-remove (add-wins) set] 
- [Semantically based PO-Log compaction] 
- [PO-Log based observed-remove set with PO-Log compaction] 

 

 [ License: arXiv.org perpetual non-exclusive license ] 

 arXiv:2310.18220v2 [cs.DC] 09 Sep 2024 

 

# Approaches to Conflict-free Replicated Data Types

 CCS: Computing methodologies Distributed computing methodologiesCCS: Theory of computation Distributed algorithmsCCS: Computer systems organization AvailabilityCCS: Software and its engineering Data types and structuresCCS: Information systems Data replication tools 

 Paulo Sérgio Almeida [] email: [psa@di.uminho.pt] Affiliation: INESC TEC & University of Minho, Portugal 

###### Abstract.

 

Conflict-free Replicated Data Types (CRDTs) allow optimistic replication in a principled way. Different replicas can proceed independently, being available even under network partitions, and always converging deterministically: replicas that have received the same updates will have equivalent state, even if received in different orders. After a historical tour of the evolution from sequential data types to CRDTs, we present in detail the two main approaches to CRDTs, operation-based and state-based, including two important variations, the pure operation-based and the delta-state based. Intended for prospective CRDT researchers and designers, this paper provides solid coverage of the essential concepts, clarifying some misconceptions which frequently occur, but also presents some novel insights gained from considerable experience in designing both specific CRDTs and approaches to CRDTs.

 

 

###### Keywords: 

Eventual Consistency, Replicated Data Types, CRDT 

 

## 1. Introduction

 

 

Classic distributed systems aim for strong consistency (e.g., linearizability DBLP:journals/toplas/HerlihyW90), through the state-machine replication approach, proposed by DBLP:journals/cacm/Lamport78. But while they can achieve performance (throughput), achieving strong consistency in systems with large spatial spans comes at the cost of high response time and the loss of availability under network partitions, as expressed by the CAP theorem DBLP:conf/podc/Brewer00; DBLP:journals/sigact/GilbertL02.

 

 

The importance of always-on availability for real businesses motivated relaxing consistency. A seminal work, which popularized the NoSQL movement, was Amazon’s Dynamo DBLP:conf/sosp/DeCandiaHJKLPSVV07, which stresses the importance of availability: “even the slightest outage has significant financial consequences and impacts customer trust”. But programming NoSQL data stores is difficult and error prone, given a low level read-write based API, and the need for ad hoc reconciliation of concurrent updates.

 

 

Since their appearance DBLP:conf/sss/ShapiroPBZ11, Conflict-free Replicated Data Types (CRDTs), soon became very popular. The essential concept is 1) providing a higher level API, as in classic data types, but for distributed, replicated objects, while 2) achieving availability through relaxing consistency and allowing immediate local replica updates and queries, with asynchronous communication to make replicas converge, 3) with data type specific concurrency semantics and synchronization specified as built-in, freeing programmers from writing ad hoc reconciliation code.

 

 

CRDTs are difficult to design, prone to subtle bugs, but allow the majority of distributed application programmers to use them, from some library, with little effort, while only a minority of expert CRDT designers need to go through the intricate process of creating new CRDTs over time. This paper is mostly aimed at prospective CRDT researchers or designers, but every CRDT user gains from having some knowledge about how they work. It describes the two main approaches to CRDTs, based on propagating operations or on propagating state, while also presenting two variants. The most important, delta-state CRDTs DBLP:journals/jpdc/AlmeidaSB18, aim to achieve, in a way, “the best of both worlds”. Pure operation-based CRDTs DBLP:conf/dais/BaqueroAS14; DBLP:journals/corr/abs-1710-04469 are a relevant point in the design space that makes clear the role of specifications over a partially ordered set of operations (a partially ordered log) in the definition of the CRDT and of *causal stability* in achieving a small state, not achievable otherwise.

 

 

This paper clarifies misconceptions regarding CRDTs, which frequently occur, in papers or presentations, and shows novel depictions, e.g., to provide intuition about joining causal state-based CRDTs. One common misconception is regarding commutativity: “CRDTs are types with commutative operations”, which is not true. Indeed, the more significant improvement over classic optimistic replication, like Lazy Replication DBLP:journals/tocs/LadinLSG92 is the support for data types with non-commutative operations. We clarify the role of commutativity, by presenting a better (than the usual) diagram showing the execution model of operation-based CRDTs. Another example is regarding monotonicity in state-based CRDTs: “mutators must be monotonic functions”, when in fact they must be *inflations*, to result in the monotonic evolution of state. The paper discusses the role of commutativity, idempotence and inflations in the ability to reuse sequential data types for both operation- and state-based CRDT designs. The classic requirement of *prepare* in operation-based CRDTs to be side-effect free is also addressed. We point out that if we distinguish the abstract state used in queries from the full CRDT concrete state, then the requirement can be relaxed, leading to better designs, and present a novel *observed-remove set* which is better than any prior design.

 

 

After a historical tour showing the evolution from sequential data types to CRDTs, the subsequent sections present: operation-based CRDTs, pure operation-based CRDTs, state-based CRDTs, and delta-state based CRDTs. This is followed by a comparison of the approaches, a discussion of identity management towards scalability, and a presentation of practical applications. Along the paper we use classic examples (counters, registers, sets), which are relatively simple to explain and understand, while having enough subtlety to allow comparing approaches, avoiding more complex data types, such as lists (e.g., Treedoc DBLP:conf/icdcs/PreguicaMSL09, RGA DBLP:journals/jpdc/RohJKL11), which need considerably more involved algorithms. Collaborative Editing, and the List data type are discussed in Section , Practical Applications.

 

 

## 2. From sequential data types to CRDTs

 

 

### 2.1. From sequential to concurrent data abstractions

 

 

#### Data abstractions

 

 

Abstraction is essential to tame complexity and scale. Two main types are functional and data abstraction. Functional abstractions (functions and procedures) were introduced first, roughly at the same time (1958) in Fortran, Lisp and Algol. Data abstractions involved a longer evolution over time, with the two main variants being abstract data types and objects.

 

 

The ingredients to obtain data types are normally thought of as: procedures; the concept of record, introduced in the AED-1 language DBLP:conf/dac/RossF64 (then named *plexes*) and adopted for Algol by DBLP:journals/cacm/WirthH66; the combination of procedures and records. What actually happened DBLP:journals/sigplan/NygaardD78 was the generalization of Algol blocks to lifetime not restricted to stack allocation in Simula DBLP:journals/cacm/DahlN66 processes (and later classes). The final ingredient was hiding the representation from client code, in CLU DBLP:journals/sigplan/LiskovZ74, leading to abstract data types (ADTs). (Simula had some information hiding capability, through inner blocks, which was not adequate.)

 

 

#### Sequential data types

 

 

In imperative languages, sequential data types became the most common abstraction in libraries. The availability of types such as Set and Map, serving as “Swiss Army knifes” in solving many problems diminished the number of times programmers had to “reinvent the wheel” and end up with slow and buggy implementations. The research effort in efficient imperative implementations of such data types has been immensely useful for the “real world”.

 

 

For sequential data types proving correctness is relatively easy (when no aliasing is involved), made possible with the introduction of axiomatic reasoning by DBLP:journals/cacm/Hoare69, involving the notions of preconditions, postconditions and invariants, and its adaptation to data types DBLP:journals/acta/Hoare72. The pervading occurrence of aliasing poses a problem; languages like Rust, aim to avoiding mutable state sharing.

 

 

#### Concurrent data types

 

 

It was only natural to extend sequential data types to shared memory concurrency, to obtain objects usable by concurrent threads. Inspired by Simula, 10.5555/540365 introduced monitors, with the construct of *shared classes*, and DBLP:journals/cacm/Hoare74 introduced a slight variant. Monitors enforce mutual-exclusion during operation execution, allowing the concept of *atomic objects*, which are easy to reason about using the same concepts of pre-/post-conditions and invariants, as no concurrency occurs during operation execution. Monitors also allow blocking mid-operation, via the await primitive (Hansen), or condition variables (Hoare). This is something useful and essential for inter-process synchronization, to achieve cooperation through shared abstractions, the most well known being the *bounded-buffer*. Unfortunately, it complicates reasoning. Blocking abstractions, common in shared-memory concurrency, are less common in distributed systems (an example being a distributed lock), and do not suit CRDTs which aim to be always available locally.

 

 

#### Lock-free and wait-free data structures

 

 

Towards achieving performance and fault tolerance in shared-memory multiprocessors, lock-free data structures were proposed. These do no use locks but resort directly to low level atomic operations, such as *compare-and-swap*, provided by hardware. This allows several data type operations in progress concurrently, not in mutual exclusion (as in monitors). Moreover, if wait-free DBLP:journals/toplas/Herlihy91, it guarantees that an operation completes in a finite number of steps, regardless of what other processes do. Their emphasis is on multiprocessors, not distributed systems, not being of further concern here, except for one question they arise: how to express correctness criteria when several actions are happening concurrently. This issue is relevant for distributed systems.

 

 

### 2.2. Strongly consistent replication

 

 

#### Linearizability

 

 

Linearizability DBLP:journals/toplas/HerlihyW90 is the more widely used correctness criteria when aiming for implementations that, even allowing concurrent execution of operations, mimic the behavior exposed by a sequential data type. An execution is linearizable, roughly, if 1) it is equivalent to some sequential execution; 2) it respects “real time” for non-overlapping operations.

 

 

Consider a register object (Figure [1]) being accessed by two processes. The read from must return 0 if the register provides linearizability, even though w(2) was the last operation to return, before the read. The reason is that because the read from returned 2, the write from must have taken effect before it, in the interval shaded in blue, being ordered before r():2 and w(0) from .

 Figure 1. A register object with read and write operations. To be linearizable, last read must return 0. 

 

#### Replicated state machines

 

 

Obtaining distributed implementations of data types that comply with linearizability can be done through the state-machine replication approach. This was proposed by Lamport DBLP:journals/cacm/Lamport78 and consists essentially of:

 
- (1) 

 

Replicate state on several nodes;

 
- (2) 

 

Same deterministic state machine at each node, , where the next state is a function of the current one and the input;

 
- (3) 

 

Agree on a global total order of inputs from all nodes;

 
- (4) 

 

Apply totally ordered inputs at each node.

 

 

The result is that the replicated system behaves as if it were a single machine. The most difficult part, specially to be fault tolerant, is the agreement DBLP:journals/jacm/PeaseSL80. The extreme case of tolerating byzantine faults is applied in the now popular blockchains, many of which use some variant of the PBFT algorithm DBLP:conf/osdi/CastroL99. Using a sequential data type for the state machine results in a replicated data type. One could think then that the problem of obtaining distributed implementations of data types is solved and there is nothing more that needs to be done.

 

 

### 2.3. The CAP theorem and consistency models

 

 

#### The CAP theorem

 

 

In a keynote at the PODC 2000 conference, DBLP:conf/podc/Brewer00 stated a conjecture that in a distributed system we can only achieve, simultaneously, at most two of the three guarantees: strong Consistency, Availability, Partition tolerance. This conjecture was proved DBLP:journals/sigact/GilbertL02, more concretely, interpreting strong consistency as linearizability, and became known as the CAP theorem. This is commonly expressed as a trilemma, in which we can only pick two out of the three properties. But because network partitions may always occur, and cannot be avoided, we can design either AP or CP systems: achieve either availability or strong consistency (linearizability).

 

 

#### CP vs AP

 

 

The CAP theorem implies an important design choice for distributed systems. Whether to achieve linearizability (CP) or availability (AP). But even when there are no network partitions, the design choice has also implication on response times. As a rough summary, CP systems:

 
- • 

 

aim for linearizability;

 
- • 

 

may become unavailable under network partitions;

 
- • 

 

have high response times in wide area.

 

In AP systems:

 
- • 

 

operations can remain available, even when there are partitions;

 
- • 

 

response times can be low even in wide area.

 

 

Forgoing linearizability, an important question is: what consistency model to aim for?

 

 

#### Consistency models

 

 

The definition of consistency models has been an important and complex research topic going over many decades. It typically involves tradeoffs regarding consequences to relevant actors (e.g., hardware, compiler, programmers), in matters such as efficient use of “the machine”, ease of implementability, ease of reasoning, or useful guarantees. DBLP:journals/csur/ViottiV16 present over 40 models. Of these, causal consistency DBLP:journals/dc/AhamadNBKH95 is of special importance, being (broadly) the strongest achievable while not losing availability MADahlin2011.

 

 

#### Causal Consistency

 

 

Causal Consistency (CC) allows processes to see different orders, as long as they are consistent with a global *happens-before* partial order. Happens-before () contains the session order , which relates operations from each process, and the *visibility* order DBLP:journals/ftpl/Burckhardt14 (operation is visible to , written , if the effect of is visible to the process performing ), being the transitive closure of their union:

 

and causal consistency essentially means that:

 

i.e., all operations from the causal past are visible, with no missing updates. Processes may possibly arbitrate operations in different total orders, each compatible with the global visibility partial order, not necessarily converging (causal consistency itself does not ensure convergence).

 

 

#### Convergence

 

 

The strongest guarantees that AP systems aim for are causal consistency together with what has become known as *strong eventual consistency* (SEC) DBLP:conf/sss/ShapiroPBZ11, which guarantees that all updates will eventually become visible everywhere (eventual visibility/delivery) and that processes that see the same set of updates have equivalent state, regardless of the order in which they become visible (*strong convergence*).

 

 

We remark that the term SEC was an unfortunate choice of terminology, and the source of some confusion, due to the use of the word *strong*, usually associated with strong consistency models, such as sequential consistency and linearizability. Moreover, *Eventual Consistency* (EC), as originally introduced by DBLP:conf/pdis/TerryDPSTW94 already includes the SEC guarantees. As described in that paper, EC systems should include mechanisms to ensure two properties, on which EC relies:

 
- • 

 

*total propagation*: each update is eventually propagated everywhere, by some anti-entropy mechanism (i.e., eventual delivery),

 
- • 

 

*consistent ordering*: non-commutative updates are applied in the same order everywhere, which implies the *strong convergence* property of SEC.

 

These two properties mean, as described by DBLP:conf/sosp/TerryTPDSH95, also addressing EC, that “all servers eventually receive all Writes via the pair-wise anti-entropy process and that two servers holding the same set of Writes will have the same data contents”. So, originally EC meant SEC, and a similar definition of EC was also used by DBLP:journals/jpdc/RohJKL11. The informal meaning of EC “eventual convergence when updates stop being issued”, since it became popular due to DBLP:journals/cacm/Vogels09, is just a consequence of the above properties which EC systems ensure. As originally introduced, what is “eventual” in EC is operation visibility (delivery); replicas that have delivered the same set of updates have converged.

 

 

#### Spatial scalability

 

 

More than just being available (AP), EC systems aiming for CC and convergence, can be performant, and usable at large spatial scales (very wide area), with low operation response time. This is an essential property for interactive systems, sometimes forgotten when thinking only of global throughput as a measure of success, often done when evaluating CP systems.

 

 

Causal consistency can be achieved while being as fast as possible in terms of physical limits (speed of light). We can say that these systems exhibit “mechanical sympathy” regarding our universe, i.e., the model suits the “machine”, even at large spatial scales. (The term *mechanical sympathy* was introduced in software by Martin Thomsom, to refer to software being built with the understanding of how hardware works, so that it can suit the hardware, and run efficiently in the underlying machine.)

 

 

On the contrary, linearizability can be said to be *contra naturam*. There is a wide mismatch between model (what it aims to guarantee) and “machine” (our universe). It would suit an Aristotelian universe, with infinite light speed. In our universe, we must slow things down (delay responses) to achieve it. The response time degrades with spatial span, becoming impractical for very wide distances; e.g., linearizability would be completely unsuitable for a system encompassing Earth and a future Mars colony. Large spatial spans require specialized techniques or protocols, which motivated research on DTNs (delay-tolerant networks) and OppNets (opportunistic networks), for which CRDTs can be suitable. DBLP:journals/ppna/GuidecMN23 investigate the implementation of CRDTs for OppNets.

 

 

### 2.4. Optimistic replication and CRDTs

 

 

#### Optimistic replication

 

 

Well before the CAP theorem was introduced, systems relaxing coordination and providing availability were developed. This was the *optimistic replication* DBLP:journals/csur/SaitoS05 approach. The main ingredients were:

 
- • 

 

replicas are locally available for queries and updates;

 
- • 

 

updates are propagated asynchronously, in the background, opportunistically.

 

 

Thus, a total order of operations was not attempted: updates could become known in different orders by different replicas. This approach achieves both availability and low latency, but it poses a problem: replicas may diverge, possibly forever. This problem was addressed using two approaches, in two relevant early works:

 
- • 

 

In Lazy replication DBLP:journals/tocs/LadinLSG92, relax ordering, but only for commutative operations.

 
- • 

 

In Bayou DBLP:conf/sosp/TerryTPDSH95, have conflict detection, client-provided merge procedures, tentative writes for availability, but the same order at all replicas for committed writes (possibly undoing and reapplying if necessary) for eventual consistency.

 

 

#### Conflict-free Replicated Data Types

 

 

The introduction of Conflict-free Replicated Data Types (CRDTs) DBLP:conf/sss/ShapiroPBZ11 was an important step over previous approaches to optimistic replication. A CRDT is exposed as a standard data type, providing operations. Each data type object is replicated and accessed locally by two kinds of operations:

 
- • 

 

mutator operations update state;

 
- • 

 

query operations look at the state and return a result.

 

Operations are always available, not depending on synchronization. A CRDT object is highly available, even under partitions, with essentially zero operation response time (only local computation).

 

 

As previous optimistic replication approaches, information is propagated asynchronously. The main novelty is that conflicts are dealt with semantically, making a replication-aware concurrent specification part of the data type definition. This specification expresses how conflicts are solved and, contrary to previous approaches, is not limited to commutative operations. Conflict resolution is encapsulated by the data type, freeing programmers from having to write application-specific ad hoc conflict resolution code. (The effort becomes choosing the appropriate CRDTs.)

 

 

#### ADTs vs objects

 

 

Evolution of object-oriented languages involved many concepts, like abstract data type, parametric polymorphism, object, class, inheritance and subtyping, as described in the classic by DBLP:journals/csur/CardelliW85, with tools such as existential and bounded universal quantification. As well summarized by DBLP:conf/oopsla/Cook09, there are two main kinds of data abstractions: ADTs and objects.

 

 

ADTs can be modeled as existential types, can access multiples instances, have efficient binary operations, and different implementations cannot mix. Objects can be modeled with recursive higher order functions, access only the self state, binary operations are “slow” or even impossible, and multiple implementations can be used together.

 

 

Most of the above is irrelevant to this paper, except for one aspect: ADT implementations can have access to multiple instances (e.g., binary operations, like multiply), while objects can only access the self state, with other objects being only accessible through their interfaces.

 

 

For distributed systems, this means that only with full replication would replicated ADTs be viable. In general, with only a subset of objects being replicated in each node, we cannot rely on being able to access several specific objects together. This is why CRDTs do not provide binary operations involving two (or more) instances, but only operations on the “self” object. Therefore, CRDTs normally are really “Conflict-free Replicated Objects”, which would have been a more suitable name.

 

 

#### Operation-based vs state-based approaches

 

 

Concerning CRDT implementation (both the data type itself and the propagation mechanism), there are two main approaches: operation-based and state-based.

 

 

Operation-based approaches propagate information about operations to other replicas, using a reliable messaging algorithm for propagation. This normally needs some ordering guarantees, but weaker than a total order. Normally causal delivery is chosen, to achieve the goal of having causal consistency. A special case is the *pure* operation-based approach.

 

 

State-based approaches propagate replica states, as opposed to propagating operations. A merge function is defined to be able to reconcile replica states. State propagation is opportunistic, by “background” communication, typically much less frequent than per-operation, to amortize the cost of propagating full states. An important variant, to make the propagation more incremental, is the delta-state based approach, partially combining the advantages of both approaches.

 

 

## 3. Operation-based CRDTs

 

 

The core concept of op-based (for short) CRDTs is to send operations, not state, to other replicas, towards replica convergence. So, when an update operation is invoked, in addition to being applied to the replica where it was invoked, it is sent to all other replicas, asynchronously, upon which they are applied at those replicas, when they arrive. Query operations (that do not cause state changes) can make use of the local state and be responded to immediately, causing no inter-replica messaging.

 

 

Because operations are not, in general, idempotent, it is essential that an exactly-once messaging mechanism is used. For some CRDTs no ordering guarantees at all would be needed for convergence. But towards ensuring, in addition to convergence, causal consistency – the strongest possible consistency model while remaining available under partitions – a causal broadcast DBLP:journals/tocs/BirmanJ87 mechanism is normally adopted, making causally dependent operations become visible in the correct order.

 

 

The essential improvement over previous attempts at optimistic replication is the treatment of non-commutative operations. Two concurrently invoked non-commutative operations could arrive and be applied in different orders in different replicas, which would lead to divergence. This is dealt with by sending more than just the operation when “broadcasting operations” to other replicas.

 

 

### 3.1. Execution model and concurrency semantics

 

 

#### Standard execution model of operation-based CRDTs

 

 

To achieve convergence even for data types containing non-commutative operations, the execution model for op-based CRDTs, presented in Figure [2], divides the execution of an update operation in two phases: *prepare* and *effect*.

 Figure 2. Execution model for op-based CRDTs, stressing the commutativity of effect for concurrently invoked operations. 

 
- (1) 

 

When an update operation is invoked, prepare is performed locally:

 
- • 

 

it looks at the state and the operation;

 
- • 

 

it must have no side effects (on the abstract state);

 
- • 

 

the result from prepare is disseminated with reliable causal broadcast.

 
- (2) 

 

Upon message delivery at each replica, effect is applied:

 
- • 

 

takes message (result from prepare) and state, and produces new state;

 
- • 

 

it is designed to be commutative for concurrently invoked operations;

 
- • 

 

it assumes immediate self-delivery on sender replica.

 

 

Immediate self-delivery is important to ensure “Read Your Writes” DBLP:conf/pdis/TerryDPSTW94, making the state-change immediately reflected locally and visible to subsequent query operations that follow the update.

 

 

#### Reusing sequential data types with commutative operations

 

 

Data types that only have commutative operations can be implemented as op-based CRDTs trivially: the state is the same as for the sequential data type; prepare returns the operation identifier and arguments; effect invokes the corresponding sequential data type operation. These data types respect the *Principle of Permutation Equivalence* DBLP:conf/wdag/BieniusaZPSBBD12: if all sequential permutations of updates lead to the same state, then concurrent execution of those operations should converge to that same state. For such data types, not even FIFO order is needed for convergence, just exactly-once delivery. Causal delivery is normally used to achieve causal consistency. Three examples are illustrated in Figure [3]: GCounter, with only an increment update operation; a PNCounter, which may be negative, having both increment and decrement; GSet, a grow-only set having just an add update operation.

 

 

 Figure 3. Simple CRDTs with only commutative operations: GCounter, PNCounter and GSet. State and effect the same as for a sequential data type. 

 

#### CRDTs with non-commutative operations

 

 

Where the CRDT approach becomes more interesting is for data types with non-commutative operations. An example is a set data type, having add and remove operations. These are not commutative, as:

 

 

If the state were the same as for the sequential data type, and effect defined as simply applying the corresponding operation, the possibility of different delivery orders for concurrently invoked add and remove of the same element would lead to divergence.

 

 

Therefore, the state must be more involved, and effect must be defined such that it is commutative for concurrently invoked operations. But not only convergence is relevant. How can we define what is supposed to happen given two concurrently invoked add and remove? I.e., how can we define the data type semantics for such CRDTs?

 

 

#### Defining concurrent semantics

 

 

A first design criteria for CRDT semantics is preserving the sequential semantics of the original data type. I.e., under a sequential execution the CRDT should produce the same outcome as the corresponding sequential data type. Then, we must define how to handle conflicts for concurrently invoked operations. In the set example, given concurrent add and remove of the same element, we want to define which will “win”. We have several possibilities:

 
- • 

 

add wins;

 
- • 

 

remove wins;

 
- • 

 

last-writer-wins (LWW), using a totally ordered arbitration.

 

 

For CRDTs, in general, the outcome may not be equivalent to some sequential execution. Data type semantics are usually defined resorting to the causal past, i.e., the result from a query depends on the set of update operations that are visible to it, and their partial order under *happens-before*.

 

 

### 3.2. Observed-cancel CRDTs

 

 

#### Observed-cancel semantics

 

 

We may define different CRDTs, with different semantics, for each sequential data type (e.g., for a set). A particularly interesting concept, for choosing in which way a conflict between two operations is handled, is what can be called *observed-cancel* semantics. Essentially, “cancel observed (visible) operations, as if they were never issued”.

 

 

Using causal delivery, as usual for op-based CRDTs, this means that an operation which cancels another will cancel the operations in its causal past, but not the ones concurrently issued. This is the most appealing choice because it makes an operation act upon a closed, well defined set of other operations which have already taken effect on the state. It will not “blindly discard” updates not yet seen, and that could not have been accounted for yet. (Such updates will eventually become visible and can always be canceled subsequently.) This prevents undesired “lost updates” which CRDTs aim to avoid. Two examples are the observed-remove set DBLP:journals/corr/abs-1210-3368:

 
- • 

 

has add and remove operations;

 
- • 

 

remove only cancels the adds visible to it;

 
- • 

 

concurrent adds will not be canceled and will “win”;

 

and the observed-reset counter DBLP:conf/eurosys/WeidnerA22:

 
- • 

 

has increment and reset;

 
- • 

 

a reset cancels the observed increments.

 

 

The appeal of observed-cancel semantics can be seen in the observed-reset counter: it allows a sample-and-reset pattern, where a process periodically samples the counter value and resets it. This allows grouping increments in a sequence, without losing any increment, even the ones concurrently issued, which will be accounted for in the next sample-and-reset. An alternative semantics where reset cancels concurrent increments would not allow accurate accounting to be achieved.

 

 

#### Observed-remove set

 

 

A well known example is precisely the observed-remove set, also called add-wins set, because adds win over concurrent removes. Several different implementations were presented in the literature, from very naive, to “optimized” DBLP:journals/corr/abs-1210-3368. An even more optimized version, not previously published, is presented in Figure [5].

 

 

Many CRDTs start indeed from a naive version, being further optimized along time. The observed-remove set is a good example of possible improvements. A vanilla, naive, version is shown in Figure [4]. In this CRDT, the state is a set of pairs and an element is considered to belong to the set if there is a pair , for some unique identifier , in the set of pairs. This version has some open issues and several possible improvements.

 

 

The first issue is that it assumes the generation of unique ids, but does not specify how to do so. This is a frequent need, and can be achieved by using unique replica ids and a counter per replica, incremented at each operation. Unique ids are obtained as pairs (replica id, counter); this is what we call a “dot” (from Dotted Version Vectors DBLP:journals/corr/abs-1011-5808). The second issue is that, being the state a set of pairs, most operations need set traversal, which is inefficient. The solution is to use a map from elements to sets of ids. A third issue is that adds keep accumulating state, adding a new pair to the ones from previous adds of the same element. An improvement is to replace the current pairs, for the given element, with a single pair for the new unique id. A fourth issue is that prepare for remove sends the element being removed repeated in each pair. The improvement is to collect the set of ids separately.

 Figure 4. Op-based observed-remove set, ORSet, naive implementation. 

 

 

CRDT state: 

 

 query 

 return 

 query 

 return 

 update 

 prepare 

 let 

 return 

 effect 

 

 update 

 prepare 

 let 

 return 

 effect 

 

 

 Figure 5. Op-based observed-remove set ORSet, optimized implementation. Algorithm for replica . 

 

 

types: 

 , set of replica identifiers 

 parameters: 

 , replica identifier 

 CRDT state: 

 

 , auxiliary state 

 query 

 return 

 query 

 return 

 update 

 prepare 

 

 return 

 effect 

 

 update 

 prepare 

 return 

 effect 

 

 

 

 

The optimized implementation, in Figure [5], contains all the above improvements. It assumes a unique replica identifier in the set of possible replica identifiers, and assumes the standard op-based execution model, using causal delivery. Each replica has a counter, incremented per add, which allows, together with the replica id, to generate unique ids. It must be noticed that this counter is auxiliary state, not part of the CRDT state used in queries or effect. This auxiliary state does not converge and it can be updated in prepare. This allows not respecting the classic rule that prepare must be free from side-effects, and obtaining a better CRDT implementation. Currently published versions which do not make this distinction are less elegant.

 

 

The state is a map from elements in the set to sets of operation identifiers. Here we assume that the map stores only non-empty sets, implicitly returning for unmapped keys. Prepare returns a tuple with operation, argument, unique id in the case of an add, and the set of ids which the map holds, for the given element. When effect is applied for remove, it subtracts the set of ids sent from the ones in the map, for the corresponding element. This has the desired outcome of removing the adds that have been observed at the replica where the remove was invoked, at the time it was invoked. Concurrently issued adds will “survive”. For add, effect adds the newly generated id and subtracts the set of ids present when the add was issued (similar to remove), as the new id makes the others redundant.

 

 

This CRDT is more optimized than the one previously published DBLP:journals/corr/abs-1210-3368: it avoids computation cost by organizing entries in a map; avoids sending the element repeatedly in a remove; and removes all observed identifiers for the element in an add, while the previously published only discards entries of previous adds from the same source. This exemplifies how, even for a relatively simple CRDT, many different versions with subtle variations may exist. This example also shows the role of commutativity: not only the operations themselves (add/remove) are not commutative, but the effect of add/remove is also not commutative: only the effect of concurrently issued add/remove is so.

 

 

### 3.3. Beyond sequential semantics and API

 

 

#### Lack of equivalence to sequential executions

 

 

CRDTs aim to preserve the sequential semantics for sequential executions, but what concerns concurrent invocations not always is it possible to achieve such equivalence. In some cases the CRDT:

 
- • 

 

has behavior not possible by any sequential execution;

 
- • 

 

the interface itself is different from the sequential data type.

 

 

The observed-remove set allows executions which are not equivalent to any sequential execution, considering the corresponding sequential data type semantics (of a set). This is easily seen by a run with two replicas, involving two elements and , where concurrently each replica adds an element and removes the other, i.e., . By the observed-remove set semantics, upon convergence both elements will be in the set, which is impossible in a sequential execution, as some remove will be the last operation.

 

 

Moreover, there are CRDTs for which even the interface itself was changed from the corresponding sequential data type. The most well known example with a modified interface is the multi-value register, made popular by the Dynamo DBLP:conf/sosp/DeCandiaHJKLPSVV07 key-value store from Amazon.

 

 

#### Multi-value register

 

 

The multi-value register keeps the set of the most recent concurrent writes. A read returns that set of values, and a write overwrites that set, in the current replica into a singleton. A multi-value register implementation, using the same technique of generating unique ids as for the observed-remove set is presented in Figure [6].

 Figure 6. Op-based multi-value register, MVReg. Algorithm for replica . 

 

 

types: 

 , set of replica identifiers 

 parameters: 

 , replica identifier 

 CRDT state: 

 

 , auxiliary state 

 query 

 

 update 

 prepare 

 

 let 

 

 effect 

 

 

 

 

The state is a set of pairs, each with the written value and corresponding unique id. Prepare returns a tuple with: the operation, a pair with value and unique id, and the set of ids in the state. When effect is applied, it keeps the pairs in the state with id not present in the set of ids in the message, and adds the new pair. This has the desired outcome of removing writes made obsolete, only preserving the most recent concurrent writes. At the replica where the write is invoked only a singleton will remain.

 

 

## 4. Pure operation-based CRDTs

 

 

#### Pure op-based CRDTs

 

 

Pure op-based CRDTs DBLP:conf/dais/BaqueroAS14 are a subset of general op-based CRDTs, restricted to the essence of “send only operations”. Pure op-based CRDTs use the same prepare-effect execution model. What defines them is the restriction that:

 
- • 

 

Prepare simply returns the operation (including arguments), ignoring current state.

 

Given operation and state :

 

 

This is unlike the general op-based approach, which can depart too much from the op-based spirit, allowing implementations that are, for all practical purposes, state-based: We can pick any state-based CRDT, define prepare as applying the operation and returning the resulting state, and define effect as merging states. However, pure op-based CRDTs may be less efficient than the general op-based approach. The pure-op based approach is relevant as an important point in the design space.

 

 

#### Pure implementations of commutative data types

 

 

For data types with only commutative operations, where for any operations and , and state :

 

operations can be applied in any order, producing the same result. As discussed in the general op-based model, the CRDT specification can be based on the sequential specification, with a trivial implementation, defining effect as simply applying the operation:

 

This applies to CRDTs such as GCounter, PNCounter, and GSet, described before, which fit the pure-op model. Again, the challenge lies on non-commutative operations: how can we obtain pure op-based implementations for non-commutative data types? The solution is to use an augmented form of causal broadcast, called *tagged causal broadcast*, which provides knowledge about happens-before and about *causal stability*, which we describe below.

 

 

### 4.1. Tagged Causal Broadcast and Causal Stability

 

 

#### Tagged Causal Broadcast (TCB)

 

 

Causal broadcast middleware already manages causality information, but normal APIs do not expose it to clients. *Tagged Causal Broadcast* DBLP:conf/dais/BaqueroAS14 provides:

 
- • 

 

a partial order on messages, in terms of an end-to-end happens-before;

 
- • 

 

information about *causal stability* of messages, as we define below.

 

 

The TCB API defines delivery as providing, together with the message itself, a timestamp corresponding to a partially ordered logical clock value which reflects *happens-before*. This timestamp can be used in the implementation of the CRDT to distinguish between causally related and concurrently invoked operations, and implement the desired semantics.

 

 

#### Causal Stability

 

 

We define causal stability as: *message with timestamp is *causally stable* at node when all messages subsequently delivered at will have timestamp *. So, a message is causally stable when no more concurrent messages will be delivered. This is different from classic multicast/message stability DBLP:journals/tocs/BirmanSS91.

 

 

A multicast is stable when it has been received by all nodes. Multicast stability is used internally by the messaging middleware, being useful for garbage collection when ensuring fault tolerance: once a message has been delivered at some node and becomes stable, that node can discard it.

 

 

While classic multicast stability regards messages being received, being an implementation aspect, causal stability is a property involving delivery, visible to TCB clients. Also, while multicast stability is a global property, causal stability is a per-node property: some message may be causally stable in some node but not in others. Causal stability is stronger: a message only becomes causally stable in some node when it has become stable.

 

 

#### The many faces of stability

 

 

“Stability” has been a much overloaded expression in distributed systems. Table [1] shows some terms where the word stability is used, and their rough meaning. Causal stability has not been properly recognized, being sometimes confused with message stability. Although the concept itself was not new when pure op-based CRDTs were introduced, coining the term “causal stability” will help avoid some confusion, specially in contexts where both may coexist, such as when describing a system involving pure op-based CRDTs, which rely on causal stability, making use of a TCB middleware, whose implementation may resort to multicast/message stability.

 Table 1. Some usages of “stability” and their meaning. Term Provenance Meaning Self stabilization DBLP:journals/cacm/Dijkstra74 returns to valid state Stable storage lampson1979crash durable storage, survives crashes Multicast stability DBLP:journals/tocs/BirmanSS91 message was received by all nodes Write stability DBLP:conf/sosp/TerryTPDSH95 a tentative write commits Causal stability DBLP:conf/dais/BaqueroAS14 concurrent messages were delivered 

 

#### Distributed algorithm for pure op-based CRDTs

 

 

The distributed algorithm for pure op-based CRDTs becomes simply reacting to the TCB middleware callbacks and invoking either prepare, effect, or a function to make use of causal stability information, as shown in Figure [7].

 Figure 7. Distributed algorithm for pure op-based CRDTs, making use of a TCB middleware. 

 

state: 

 

 on : 

 

 on : 

 

 on : 

 

 

 

### 4.2. Resorting to a partially ordered Log

 

 

#### Naive PO-Log based implementations

 

 

A starting point for implementing pure op-based CRDTs is making the state a *PO-Log *: partially ordered log of operations. The PO-Log can be implemented as a map from timestamps to operations. Effect simply adds an entry delivered by TCB to the PO-Log. This makes both prepare and effect to have a universal definition. Only queries are data type dependent, being defined over the PO-Log. This approach is shown in Figure [8]. It is very naive, but a starting point for subsequent optimization.

 [data type specific query function over PO-Log ] Figure 8. PO-Log based implementation for pure op-based CRDTs. 

 

#### PO-Log based observed-remove (add-wins) set

 

 

An example of a PO-Log based CRDT, an observed-remove set is shown in Figure [9]. Prepare and effect have the universal definition shown before. Only query is data type specific. The query mimics the specification over the partial order of operations: an element is considered to be in the set if there exists an add for that element not canceled by a remove in its causal future. Essentially, this implementation is a naive runnable specification. But it shows the role of partial-order based concurrent specifications in the design of CRDTs burckhardt2013understanding; DBLP:conf/popl/BurckhardtGYZ14, and how it fits directly the pure op-based model, as opposed to the totally ordered history of operations used for sequential specifications.

 Figure 9. PO-Log based observed-remove (add-wins) set CRDT. 

 

#### Semantically based PO-Log compaction

 

 

PO-Log based CRDTs as described would be very inefficient, and need optimizations to be actually usable. The idea is to avoid PO-Log growth, making it compact, by keeping the smallest number of items that produce equivalent results when queries are performed. The compaction is data type specific, according to the specification, and makes use of the causality related data provided by the TCB middleware:

 
- • 

 

Causality: to prune the PO-Log after effect is performed, i.e., after operation delivery;

 
- • 

 

Causal stability: to discard timestamps for operations that become causally stable and in some cases to remove operations that become redundant after causal stability.

 

 

Causality information can be exploited to achieve PO-Log compaction by redefining to use a data type specific relation between timestamps:

 

When a new pair is delivered, discards from the PO-Log all elements such that holds. Moreover, the delivered pair is only inserted into the PO-Log if it is not itself redundant, i.e., if is false for any in the PO-Log. The relation is not restricted to being a partial-order to allow, e.g., a newly arrived operation to discard others in the PO-Log without necessarily being itself added.

 

 

Regarding exploiting causal stability, the basic improvement is to replace timestamps by , when they becomes causally stable, as they will be compared as in the past of new operations that will arrive, saving space. For some data types we can also remove some causally stable operations themselves, if they become redundant considering the data type semantics. We thus define as:

 

where is a data type specific function which discards operations from the PO-Log that become redundant upon causal stability.

 

 

Commonly, is the identity function, but it can be quite involved. A detailed description of how causal stability can be exploited is beyond the scope of this paper. Details can be found in DBLP:conf/dais/BaqueroAS14; DBLP:journals/corr/abs-1710-04469. Just to give an example, in a remove-wins set, a remove wins over a concurrent add of the same element and must be kept in the PO-Log for some time, in case a concurrent add shows up. But once the remove becomes causally stable it can be discarded in some cases.

 

 

#### PO-Log based observed-remove set with PO-Log compaction

 

 

To exemplify how causality information can be used to perform PO-Log compaction, for an ORSet it is true that:

 
- • 

 

a subsequent obsoletes a previous of the same value;

 
- • 

 

a subsequent obsoletes a previous of the same value;

 
- • 

 

a is made obsolete by any other timestamped operation: after having made other operations obsolete, the itself becomes redundant.

 

 

So, the relation for the ORSet can be defined as:

 

 Experimental support, please [view the build logs] for errors. Generated by [ L A T E xml ]. 

 

## Instructions for reporting errors

 

We are continuing to improve HTML versions of papers, and your feedback helps enhance accessibility and mobile support. To report errors in the HTML that will help us improve conversion and rendering, choose any of the methods listed below:

 
- Click the "Report Issue" () button, located in the page header. 

**Tip:** You can select the relevant text first, to include it in your report.

 

Our team has already identified [the following issues]. We appreciate your time reviewing and reporting rendering errors we may not have found yet. Your efforts will help us improve the HTML versions for all readers, because disability should not be a barrier to accessing research. Thank you for your continued support in championing open access for all.

 

Have a free development cycle? Help support accessibility at arXiv! Our collaborators at LaTeXML maintain a [list of packages that need conversion], and welcome [developer contributions].

 

 

 

 We gratefully acknowledge support from our **major funders**, [**member institutions**], ****, and all contributors. [About] · [Help] · [Contact] · [Subscribe] · [Copyright] · [Privacy] · [Accessibility] · [Operational Status (opens in new tab)] 

 

Major funding support from 

 [ ] [ ] [ ] 

 [ ]