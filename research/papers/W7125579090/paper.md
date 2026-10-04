# **Collaborative Text Editing with Eg-walker: Better, Faster, Smaller** 

Joseph Gentle 

me@josephg.com Independent Melbourne, Australia 

## **Abstract** 

Collaborative text editing algorithms allow several users to concurrently modify a text file, and automatically merge concurrent edits into a consistent state. Existing algorithms fall in two categories: Operational Transformation (OT) algorithms are slow to merge files that have diverged substantially due to offline editing; CRDTs are slow to load and consume a lot of memory. We introduce Eg-walker, a collaboration algorithm for text that avoids these weaknesses. Compared to existing CRDTs, it consumes an order of magnitude less memory in the steady state, and loading a document from disk is orders of magnitude faster. Compared to OT, merging long-running branches is orders of magnitude faster. In the worst case, the merging performance of Eg-walker is comparable with existing CRDT algorithms. Eg-walker can be used everywhere CRDTs are used, including peer-to-peer systems without a central server. By offering performance that is competitive with centralised algorithms, our result paves the way towards the widespread adoption of peer-topeer collaboration software. 

**_CCS Concepts:_** • **Applied computing** → **Text editing** ; • **Human-centered computing** → **Computer supported cooperative work** ; • **Information systems** → _Asynchronous editors_ ; • **Computing methodologies** → _Distributed algorithms_ . 

**_Keywords:_** collaborative text editing, CRDTs, operational transformation, strong eventual consistency 

#### **ACM Reference Format:** 

Joseph Gentle and Martin Kleppmann. 2025. Collaborative Text Editing with Eg-walker: Better, Faster, Smaller. In _Twentieth European Conference on Computer Systems (EuroSys ’25), March 30-April 3, 2025, Rotterdam, Netherlands._ ACM, New York, NY, USA, 25 pages. https://doi.org/10.1145/3689031.3696076 

This work is licensed under a Creative Commons Attribution 4.0 International License. 

_EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands_ © 2025 Copyright held by the owner/author(s). ACM ISBN 979-8-4007-1196-1/25/03 https://doi.org/10.1145/3689031.3696076 

Martin Kleppmann 

|martin.kleppmann@cst.cam.ac.uk<br>University of Cambridge<br>Cambridge, United Kingdom|
|---|
|User 1:<br>User 2:<br>Helo<br>Helo|
|Hello<br>Hello!<br>Helo!<br>Hello!<br>_Insert_(3_,_“l”)<br>_Insert_(4_,_“!”)<br>_Insert_(5_,_“!”)<br>_Insert_(3_,_“l”)|



**Figure 1.** Two concurrent insertions into a text document. 

## **1 Introduction** 

Real-time collaboration has become an essential feature for many types of software, including document editors such as Google Docs, Microsoft Word, or Overleaf, and graphics software such as Figma. In such software, each user’s device locally maintains a copy of the shared file (e.g. in a tab of their web browser). A user’s edits are immediately applied to their own local copy, without waiting for a network roundtrip, so that the user interface is responsive regardless of network latency. Different users may therefore make edits concurrently; the software must merge such concurrent edits in a way that maintains the integrity of the document, and ensures that all devices converge to the same state. 

For example, in Figure 1, two users initially have the same document “Helo”. User 1 inserts a second letter “l” at index 3, while concurrently user 2 inserts an exclamation mark at index 4. When user 2 receives the operation _Insert_ (3 _,_ “l”) they can apply it to obtain “Hello!”, but when user 1 receives _Insert_ (4 _,_ “!”) they cannot apply that operation as-is, since that would result in the state “Hell!o”, which would be inconsistent with the other user’s state and the intended insertion position. Due to the concurrent insertion at an earlier index, user 1 must insert the exclamation mark at index 5. 

One way of solving this problem is to use _Operational Transformation_ (OT): when user 1 receives _Insert_ (4 _,_ “!”) that operation is transformed with regard to the concurrent insertion at index 3, which increments the index at which the exclamation mark is inserted. OT is an old and widely-used technique: it was introduced in 1989 [18], and the OT algorithm Jupiter [44] is used in Google Docs [17]. 

OT is simple and fast in the case of Figure 1, where each user performed only one operation since the last version they had in common. In general, if the users each performed 

1 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

_𝑛_ operations since their last common version, merging their states using OT has a cost of at least _𝑂_ ( _𝑛_<sup>2</sup> ), since each of one user’s operations must be transformed with respect to all of the other user’s operations. Some OT algorithms’ merge complexity is cubic or even slower [39, 52, 57]. This is acceptable for online collaboration where _𝑛_ is typically small, but for larger _𝑛_ an algorithm with complexity _𝑂_ ( _𝑛_<sup>2</sup> ) can become impracticably slow. In Section 4 we show a real-life example document that takes one hour to merge using OT. 

Larger divergence occurs if users may edit a document offline, or if the software supports explicit branching and merging workflows. In version control systems like Git, used mostly for software development, offline working and explicit branching are already the norm. Recent research indicates that such workflows would also be valuable for writing prose [40, 42], but OT-based collaborative editors struggle to offer such features because of the cost of merging substantially diverged branches. 

_Conflict-free Replicated Data Types_ (CRDTs) have been proposed as an alternative to OT. The first CRDT for collaborative text editing appeared in 2006 [47], and over a dozen text CRDTs have been published since [35]. These algorithms work by maintaining additional metadata: they give each character a unique identifier, and use those IDs instead of integer indexes to identify the position of insertions and deletions. This avoids having to transform operations, since IDs are not affected by concurrent operations. 

Unfortunately, these IDs need to be loaded from disk when a document is opened, and held in memory while a document is being edited. Some CRDT algorithms also need to retain IDs of deleted characters ( _tombstones_ ). Early CRDT algorithms were very inefficient, using hundreds of bytes of memory for each character of text, making them impractical for long documents. Recent CRDT implementations have reduced this overhead considerably, but as we show in Section 4, even the best CRDTs available today use more than 10 times as much memory as OT to view and edit a document. For this reason, popular apps like Google Docs [17], Microsoft Office, and Overleaf [48] use OT. Existing algorithms therefore present a trade-off: either use OT and accept that offline editing and long-running branches are slow, or pick a CRDT and accept a much higher memory use. 

In this paper we propose _Event Graph Walker_ (Eg-walker), a collaborative editing algorithm that overcomes this tradeoff. Like OT, Eg-walker uses integer indexes to identify insertion and deletion positions, and transforms those indexes to merge concurrent operations. When two users concurrently perform _𝑛_ operations each, Eg-walker can merge them at a cost of _𝑂_ ( _𝑛_ log _𝑛_ ), much faster than OT’s cost of _𝑂_ ( _𝑛_<sup>2</sup> ) or worse. The example document that takes 1 hour to merge using OT is merged in just 24 ms using Eg-walker (Figure 8). 

Eg-walker merges concurrent edits using a CRDT algorithm we designed. Unlike existing algorithms, we invoke the CRDT only to perform merges of concurrent operations, 

and we discard its state as soon as the merge is complete. We never write the CRDT state to disk and never send it over the network. While a document is being edited, we only hold the document text in memory, but no CRDT metadata. Most of the time, Eg-walker therefore uses 1–2 orders of magnitude less memory than the best CRDTs. During merging, when Eg-walker temporarily uses more memory, its peak memory use is comparable to the best known CRDT implementations. 

Eg-walker assumes no central server, so it can be used over a peer-to-peer network. Although all existing CRDTs and a few OT algorithms can be used peer-to-peer, most of them have poor performance compared to the centralised OT commonly used in production software. In contrast, Eg-walker’s performance matches or surpasses that of centralised algorithms. It therefore paves the way towards more collaboration software working peer-to-peer, for example in environments where co-located devices can communicate via local radio links, but not reach the Internet or any cloud services. This setting is important e.g. for devices onboard the same aircraft [50], in a military context [19], or for scientists conducting fieldwork in remote locations [15]. 

This paper focuses on collaborative editing of plain text files. We believe that our approach can be generalised to other file types such as rich text, spreadsheets, graphics, presentations, CAD drawings, and more in the future. More generally, Eg-walker provides a framework for efficient coordinationfree distributed systems, in which nodes can always make progress independently, but converge eventually [29]. 

This paper makes the following contributions: 

- We introduce Eg-walker, a hybrid CRDT/OT algorithm for text that is faster and has a vastly smaller memory footprint than existing CRDTs (Section 3). 

- Since there is no established benchmark for collaborative text editing, we are also publishing a suite of editing traces of text files for benchmarking. They are derived from real documents and demonstrate various patterns of sequential and concurrent editing. 

- In Section 4 we use those editing traces to evaluate the performance of our implementation of Eg-walker, comparing it to selected CRDTs and an OT implementation. We measure CPU time to load a document, CPU time to merge edits from a remote replica, memory usage, and file size. Eg-walker improves the state of the art by orders of magnitude in the best cases, and is only slightly slower in the worst cases. 

- We prove the correctness of Eg-walker in Appendix C. 

## **2 Background** 

We consider a collaborative plain text editor whose state is a linear sequence of characters, which may be edited by inserting or deleting characters at any position. Such an edit is captured as an _operation_ ; the operation _Insert_ ( _𝑖,𝑐_ ) 

2 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

inserts character _𝑐_ at index _𝑖_ , and _Delete_ ( _𝑖_ ) deletes the character at index _𝑖_ (indexes are zero-based). Our implementation compresses runs of consecutive insertions or deletions, but for simplicity we describe the algorithm in terms of singlecharacter operations. 

### **2.1 System model** 

Each device on which a user edits a document is a _replica_ , and each replica stores the full editing history of the document. When a user makes an insertion or deletion, that operation is immediately applied to the user’s local replica, and then asynchronously sent over the network to any other replicas that have a copy of the same document. Users can also edit their local copy while offline; the corresponding operations are then enqueued and sent when the device is next online. 

Our algorithm assumes a reliable broadcast protocol that detects and retransmits lost messages, but makes no other assumptions about the network. For example, a relay server could store and forward messages from one replica to the others, or replicas could use a peer-to-peer gossip protocol. We make no timing assumptions and tolerate arbitrary network delay, but we assume replicas are non-Byzantine. 

Our algorithm ensures _convergence_ : any two replicas that have seen the same operations have the same document state (i.e., a text consisting of the same sequence of characters), even if the operations arrived in a different order at each replica. If the underlying broadcast protocol ensures that every non-crashed replica eventually receives every operation, the algorithm achieves _strong eventual consistency_ [53]. 



**Figure 2.** The event graph corresponding to Figure 1. 

their event graphs by taking the union of their sets of events. Events in the graph are immutable; they always represents the operation as originally generated, and not as a result of any transformation. The graph grows monotonically (we never remove events), and a new event is always a child of existing events (we never add a parent to an existing event). 

For example, Figure 2 shows the event graph corresponding to Figure 1. The events _𝑒_ 5 and _𝑒_ 6 are concurrent, and the frontier of this graph is the set of events { _𝑒_ 5 _,𝑒_ 6}. 

The event graph for a substantial document, such as a research paper, may contain hundreds of thousands of events. It can nevertheless be stored in a very compact form by exploiting the typical editing patterns of humans writing text: characters tend to be inserted or deleted in consecutive runs. Many portions of a typical event graph are linear, with each event having one parent and one child. We describe the storage format in more detail in Section 3.8. 

### **2.2 Event graphs** 

We represent the editing history of a document as an _event graph_ : a directed acyclic graph (DAG) in which every node is an _event_ consisting of an operation (insert/delete a character), a unique ID, and the set of IDs of its _parent events_ . When _𝑎_ is a _parent_ of _𝑏_ , we also say _𝑏_ is a _child_ of _𝑎_ , and the graph contains an edge from _𝑎_ to _𝑏_ . We construct events such that the graph is transitively reduced (i.e., it contains no redundant edges). When there is a directed path from _𝑎_ to _𝑏_ we say that _𝑎 happened before 𝑏_ , and write _𝑎_ → _𝑏_ as per Lamport [38]. The → relation is a strict partial order. We say that events _𝑎_ and _𝑏_ are _concurrent_ , written _𝑎_ ∥ _𝑏_ , if both events are in the graph, _𝑎_ ≠ _𝑏_ , and neither happened before the other: _𝑎_ ↛ _𝑏_ ∧ _𝑏_ ↛ _𝑎_ . 

The _frontier_ is the set of events with no children. Whenever a user performs an operation, a new event containing that operation is added to the graph, and the previous frontier in the replica’s local copy of the graph becomes the new event’s parents. The new event is then broadcast over the network, and each replica adds it to its copy of the graph. If any parents are missing (i.e., a parent ID in the event does not resolve to a known event), the replica waits for them to arrive before adding them to the graph; the result is a simple causal broadcast protocol [11, 13]. Two replicas can merge 

### **2.3 Document versions** 

Let _𝐺_ be an event graph, represented as a set of events. Due to convergence, any two replicas that have the same set of events must be in the same state. Therefore, the document state (sequence of characters) resulting from _𝐺_ must be replay( _𝐺_ ), where replay is some pure (deterministic and non-mutating) function. In principle, any pure function of the set of events results in convergence, although a replay function that is useful for text editing must satisfy additional criteria (see Section 3.1). 

Consider the event _Delete_ ( _𝑖_ ), which deletes the character at position _𝑖_ in the document. In order to correctly interpret this event, we need to determine which character was at index _𝑖_ at the time when the operation was generated. 

More generally, let _𝑒𝑖_ be some event. The document state when _𝑒𝑖_ was generated must be replay( _𝐺𝑖_ ), where _𝐺𝑖_ is the set of events that were known to the generating replica at the time when _𝑒𝑖_ was generated (not including _𝑒𝑖_ itself). By definition, the parents of _𝑒𝑖_ are the frontier of _𝐺𝑖_ , and thus _𝐺𝑖_ is the set of all events that happened before _𝑒𝑖_ , i.e., _𝑒𝑖_ ’s parents and all of their ancestors. Therefore, the parents of _𝑒𝑖_ unambiguously define the document state in which _𝑒𝑖_ must be interpreted. 

3 

Joseph Gentle and Martin Kleppmann 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

To formalise this, given an event graph (set of events) _𝐺_ , we define the _version_ of _𝐺_ to be its frontier set: 



Given some version _𝑉_ , the corresponding set of events can be reconstructed as follows: 



Since an event graph grows only by adding events that are concurrent to or children of existing events (we never change the parents of an existing event), there is a one-toone correspondence between an event graph and its version. For all valid event graphs _𝐺_ , Events(Version( _𝐺_ )) = _𝐺_ . 

The set of parents of an event in the graph is the version of the document in which that operation must be interpreted. The version can hence be seen as a _logical clock_ , describing the point in time at which a replica knows about the exact set of events in _𝐺_ . Even if the event graph is large and there are many collaborators, a version rarely consists of more than two events in practice: a version with _𝑛_ events occurs only if _𝑛_ mutually concurrent events are merged with no new operations being generated in the intervening time. 

### **2.4 Replaying editing history** 

Collaborative editing algorithms are usually defined in terms of sending and receiving messages over a network. The abstraction of an event graph allows us to reframe these algorithms in a simpler way: a collaborative text editing algorithm is a pure function replay( _𝐺_ ) of an event graph _𝐺_ . This function can use the parent-child relationships to partially order events, but concurrent events could be processed in any order. This allows us to separate the process of replicating the event graph from the algorithm that ensures convergence. In fact, this is how _pure operation-based CRDTs_ [9] are formulated, as discussed in Section 5. 

In addition to determining the document state from an entire event graph, we need an _incremental update_ function. Say we have an existing event graph _𝐺_ and corresponding document state _doc_ = replay( _𝐺_ ). Then an event _𝑒_ from a remote replica is added to the graph. We could rerun the function to obtain _doc_<sup>′</sup> = replay( _𝐺_ ∪{ _𝑒_ }), but it would be inefficient to process the entire graph again. Instead, we need to efficiently compute the operation to apply to _doc_ in order to obtain _doc_<sup>′</sup> . For text documents, this incremental update is also described as an insertion or deletion at a particular index; however, the index may differ from that in the original event due to the effects of concurrent operations, and a deletion may turn into a no-op if the same character has also been deleted by a concurrent operation. 

Both OT and CRDT algorithms focus on this incremental update. If none of the events in _𝐺_ are concurrent with _𝑒_ , OT is straightforward: the incremental update is identical to the operation in _𝑒_ , as no transformation takes place. If there is 

|_𝑒_A1<br>_𝑒_A2<br>_𝑒_A3<br>_𝑒_A4<br>_𝑒_A5<br>_𝑒_A6<br>_𝑒_B1<br>_𝑒_B2<br>_𝑒_B3<br>_𝑒_B4<br>_𝑒_C1<br>_𝑒_C2<br>_𝑒_C3|_𝑒_A1<br>_𝑒_A2<br>_𝑒_A3<br>_𝑒_A4<br>_𝑒_B1<br>_𝑒_B2<br>_𝑒_B3<br>_𝑒_B4<br>_𝑒_C1<br>_𝑒_C2<br>_𝑒_C3<br>_𝑒_A5<br>_𝑒_A6|
|---|---|



**Figure 3.** An event graph (left) and one possible topologically sorted order of that graph (right). 

concurrency, OT must transform each new event with regard to each existing event that is concurrent to it. 

In CRDTs, each event is first translated into operations that use unique IDs instead of indexes, and then these operations are applied to a data structure that reflects all of the operations seen so far (both concurrent operations and those that happened before). In order to update the text editor, these updates to the CRDT’s internal structure need to be translated back into index-based insertions and deletions. Many CRDT papers elide this translation from unique IDs back to indexes, but it is important for practical applications. 

Regardless of whether the OT or the CRDT approach is used, a collaborative editing algorithm can be boiled down to an incremental update to an event graph: given an event to be added to an existing event graph, return the (index-based) operation that must be applied to the current document state so that the resulting document is identical to replaying the entire event graph including the new event. 

### **2.5 Implementing OT using a CRDT** 

One way of implementing such a replay algorithm would be to simulate a network of CRDT replicas in a single process. For each branch in the event graph there is a separate simulated replica, which takes operations in their original index-based form and generates a corresponding ID-based CRDT operation. Another simulated replica receives every operation generated by the other replicas and applies them in some topologically sorted order, as illustrated in Figure 3. 

For example, the history in Figure 3 could be replayed using one simulated replica for _𝑒_ A1 _..._ A6, a second for _𝑒_ B1 _..._ B4, and a third for _𝑒_ C1 _..._ C3. Every time an event’s parent is an event generated on another simulated replica, the corresponding network communication is simulated, and the remote operations are merged using a CRDT algorithm. For example, before the replica for _𝑒_ B1 _..._ B4 can generate _𝑒_ B3 it must first merge _𝑒_ A2 and _𝑒_ A3. Each simulated replica thus tracks the document version in which the indexes of insertions and deletions should be interpreted. The simulated replica that applies all operations then converts the ID-based operation 

4 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

back into an index based on its document version. This indexbased operation then allows an incremental update of the document state. 

The process of translating an index-based operation into an ID-based one on one simulated replica, and translating it back into an index-based operation on another, is effectively an operational transformation algorithm: it updates the index to reflect the effects of concurrent operations (which have been applied to the second simulated replica but not the first). However, the algorithm is fairly slow because it incurs the overhead of updating multiple simulated replicas and running the CRDT algorithm even at times when there is no concurrency in the event graph. It also uses a lot of memory because it needs a separate copy of the CRDT state for every concurrent branch in the event graph. 

Our Eg-walker algorithm, described in the next section, modifies this approach to use only two simulated replicas: one on which operations are generated, and the other on which all operations are applied (and in fact, both are stored in the same data structure). To deal with event graphs that are not totally ordered, the algorithm allows events on one branch to be _retreated_ when switching to another branch, and _advanced_ again when those branches are merged. Retreating an event updates the replica state to behave as if that event had not yet happened, and advancing makes the event take effect again. 

For example, in Figure 3, after applying _𝑒_ A4 we would retreat _𝑒_ A4, _𝑒_ A3, and _𝑒_ A2 before applying _𝑒_ B1, since those events are concurrent with _𝑒_ B1. Before applying _𝑒_ B3 we would advance _𝑒_ A2 and _𝑒_ A3 again, since they are ancestors of _𝑒_ B3. Retreating and advancing takes some additional CPU time on highly concurrent event graphs, but as we show in Section 4, the optimisations this approach enables result in excellent performance overall. 

## **3 The Event Graph Walker algorithm** 

Eg-walker is a collaborative text editing algorithm based on the idea of event graph replay. The algorithm builds on a replication layer that ensures that whenever a replica adds an event to the graph, all non-crashed replicas eventually receive it. The state of each replica consists of three parts: 

1. **Event graph:** Each replica stores a copy of the event graph on disk, in a format described in Section 3.8. 

2. **Document state:** The current sequence of characters in the document with no further metadata. On disk this is simply a plain text file; in memory it may be represented as a rope [12], piece table [41], or similar structure to support efficient insertions and deletions. 

3. **Internal state:** A temporary CRDT structure that Eg-walker uses to merge concurrent edits. It is not persisted or replicated, and it is discarded when the algorithm finishes running. 

Eg-walker can reconstruct the document state by replaying the entire event graph. It first performs a topological sort, as illustrated in Figure 3. Then each event is transformed so that the transformed insertions and deletions can be applied in topologically sorted order, starting with an empty document, to obtain the document state. In Git parlance, this process “rebases” a DAG of operations into a linear operation history with the same effect. The input of the algorithm is the event graph, and the output is this topologically sorted sequence of transformed operations. While OT transforms one operation with respect to one other, Eg-walker uses the internal state to transform sets of operations efficiently. 

In graphs with concurrent operations there are multiple possible sort orders. Eg-walker guarantees that the final document state is the same, regardless which of these orders is chosen. However, the choice of sort order may affect the performance of the algorithm, as discussed in Section 3.7. 

For example, the graph in Figure 2 has two possible sort orders; Eg-walker either first inserts “l” at index 3 and then “!” at index 5 (like User 1 in Figure 1), or it first inserts “!” at index 4 followed by “l” at index 3 (like User 2 in Figure 1). The final document state is “Hello!” either way. 

Event graph replay easily extends to incremental updates for real-time collaboration: when a new event is added to the graph, it becomes the next element of the topologically sorted sequence. We can transform each new event in the same way as during replay, and apply the transformed operation to the current document state. 

### **3.1 Characteristics of Eg-walker** 

Eg-walker ensures that the resulting document state is consistent with Attiya et al.’s _strong list specification_ [8] (in essence, replicas converge to the same state and apply operations in the right place), and it is _maximally non-interleaving_ [60] (i.e., concurrent sequences of insertions at the same position are placed one after another, and not interleaved). 

When generating new events, or when adding an event to the graph that happened after all existing events, Eg-walker only needs the current document state. Most of the time, the event graph can thus remain on disk without using any space in memory or any CPU time, and the internal state can be discarded entirely. The event graph and internal state are only required when handling concurrency, and even then we only have to replay the portion of the graph since the last ancestor that the concurrent operations had in common. In portions of the event graph that have no concurrency (which, in many editing histories, is the vast majority of events), events do not need to be transformed at all. 

In contrast, existing CRDTs require every replica to persist the internal state and send it over the network. They also require that state to be loaded into memory to generate and receive operations, even when there is no concurrency. This uses several times more memory and makes documents slow to load. 

5 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

OT algorithms avoid this internal state; similarly to Egwalker, they only need to persist the latest document state and the history of operations that are concurrent to operations that may arrive in the future. In both Eg-walker and OT, the event graph can be discarded if we know that no event we may receive in the future will be concurrent with any existing event. However, OT algorithms are very slow to merge long-running branches (see Section 4). Eg-walker handles arbitrary event DAGs, whereas some OT algorithms are only able to handle restricted forms of event graphs (server-based OT corresponds to event graphs with one main branch representing the server’s view; all other branches may merge to and from the main branch, but not with each other). 



**Figure 4.** An event graph. Starting with document “hi”, one user changes “hi” to “hey”, while concurrently another user capitalises the “H”. After merging to the state “Hey”, one of them appends an exclamation mark to produce “Hey!”. 

### **3.2 Walking the event graph** 

For the sake of clarity we first explain a simplified version of Eg-walker that replays the entire event graph without discarding its internal state along the way. This approach incurs some CRDT overhead even for non-concurrent operations. We give pseudocode for this simplified algorithm in Appendix B. In Section 3.6 we show how the algorithm can be optimised to replay only a part of the event graph. 

First, we topologically sort the event graph in a way that keeps events on the same branch consecutive as much as possible: for example, in Figure 3 we first visit _𝑒_ A1 _. . .𝑒_ A4, then _𝑒_ B1 _. . .𝑒_ B4. We avoid alternating between branches, such as _𝑒_ A1 _,𝑒_ B1 _,𝑒_ A2 _,𝑒_ B2 _. . ._ , even though that would also be a valid topological sort. For this we use a standard textbook algorithm [16]: perform a depth-first traversal starting from the oldest event, and build up the topologically sorted list in the order that events are visited. When a node has multiple children in the graph, we choose their order based on a heuristic so that branches with fewer events tend to appear before branches with more events in the sorted order; this can improve performance (see Section 3.7) but is not essential. We estimate the size of a branch by counting the number of events that happened after each event. 

The algorithm then processes the events one at a time in topologically sorted order, updating the internal state and outputting a transformed operation for each event. The internal state simultaneously captures the document at two versions: the version in which an event was generated (which we call the _prepare_ version), and the version in which all events seen so far have been applied (which we call the _effect_ version). These correspond to the two simulated replicas mentioned in Section 2.5. If the prepare and effect versions are the same, the transformed operation is identical to the original one. In general, the prepare version represents a subset of the events of the effect version. 

The internal state can be updated with three methods, each of which takes an event as argument: 

- apply( _𝑒_ ) updates the prepare version and the effect version to include _𝑒_ , assuming that the current prepare 

version equals _𝑒.parents_ , and that _𝑒_ has not yet been applied. This method interprets _𝑒_ in the context of the prepare version, and outputs the operation representing how the effect version has been updated. 

- retreat( _𝑒_ ) updates the prepare version to remove _𝑒_ , assuming the prepare version previously included _𝑒_ . 

- advance( _𝑒_ ) updates the prepare version to add _𝑒_ , assuming that the prepare version previously did not include _𝑒_ , but the effect version did. 

The effect version only moves forwards in time (through apply), whereas the prepare version can move both forwards and backwards. Consider the example in Figure 4, and assume that the events _𝑒_ 1 _. . .𝑒_ 8 are traversed in order of their subscript. These events can be processed as follows: 

1. Start in the empty state, then call apply( _𝑒_ 1), apply( _𝑒_ 2), apply( _𝑒_ 3), and apply( _𝑒_ 4). This is valid because each event’s parent version is the previously applied event. 

2. Before we can apply _𝑒_ 5 we must rewind the prepare version to be { _𝑒_ 2}, which is the parent of _𝑒_ 5. We can do this by calling retreat( _𝑒_ 4) and retreat( _𝑒_ 3). 

3. Now we can call apply( _𝑒_ 5), apply( _𝑒_ 6), and apply( _𝑒_ 7). 

4. The parents of _𝑒_ 8 are { _𝑒_ 4 _,𝑒_ 7}; before we can apply _𝑒_ 8 we must therefore add _𝑒_ 3 and _𝑒_ 4 to the prepare state again by calling advance( _𝑒_ 3) and advance( _𝑒_ 4). We do not retreat _𝑒_ 5 _..._ 7 because _𝑒_ 3 and _𝑒_ 4 have already been applied in Step 1; now we are _advancing 𝑒_ 3 and _𝑒_ 4, which does not require retreating concurrent events. 

5. Finally, we can call apply( _𝑒_ 8). 

In complex event graphs such as the one in Figure 3 the same event may have to be retreated and advanced several times, but we can process arbitrary DAGs this way. In general, before applying the next event _𝑒_ in topologically sorted order, compute _𝐺_ old = Events( _𝑉𝑝_ ) where _𝑉𝑝_ is the current prepare version, and _𝐺_ new = Events( _𝑒.parents_ ). We then call retreat on each event in _𝐺_ old − _𝐺_ new (in reverse topological sort order), and call advance on each event in _𝐺_ new − _𝐺_ old (in topological sort order) before calling apply( _𝑒_ ). 

6 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

The following algorithm efficiently computes the events to retreat and advance when moving the prepare version from _𝑉𝑝_ to _𝑉𝑝_<sup>′.Foreacheventin</sup><sup>_𝑉𝑝_and</sup><sup>_𝑉_</sup> _𝑝_<sup>′weinserttheindex</sup> of that event in the topological sort order into a priority queue, along with a tag indicating whether the event is in the old or the new prepare version. We then repeatedly pop the event with the greatest index off the priority queue, and enqueue the indexes of its parents along with the same tag. We stop the traversal when all entries in the priority queue are common ancestors of both _𝑉𝑝_ and _𝑉𝑝_<sup>′. Any events that</sup> were traversed from only one of the versions need to be retreated or advanced respectively. 

### **3.3 Representing prepare and effect versions** 

The internal state implements the apply, retreat, and advance methods by maintaining a CRDT data structure. This structure consists of a linear sequence of records, one per character in the document, including tombstones for deleted characters. Runs of characters with consecutive IDs and the same properties can be run-length encoded to save memory. A record is inserted into this sequence by apply( _𝑒𝑖_ ) for an insertion event _𝑒𝑖_ . Subsequent deletion events and retreat/advance calls may modify properties of the record, but records in the sequence are not removed or reordered once they have been inserted. 

When the event graph contains concurrent insertions, we use a CRDT to ensure that all replicas place the records in this sequence in the same order, regardless of the order in which the event graph is traversed. For example, RGA [52] or YATA [45] could be used for this purpose. Our implementation of Eg-walker uses a variant of the Yjs algorithm [30], itself based on YATA, that we conjecture to be maximally noninterleaving. We leave a detailed analysis of this algorithm to future work, since it is not core to this paper. 

Each record in this sequence contains: 

- the ID of the event that inserted the character; 

- _𝑠𝑝_ ∈{NotInsertedYet _,_ Ins _,_ Del 1 _,_ Del 2 _, . . ._ }, the character’s state in the prepare version; 

- _𝑠𝑒_ ∈{Ins _,_ Del}, the state in the effect version; 

- and any other fields required by the CRDT to determine the order of concurrent insertions. 

The rules for updating _𝑠𝑝_ and _𝑠𝑒_ are: 

- When a record is first inserted by apply( _𝑒𝑖_ ) with an insertion event _𝑒𝑖_ , it is initialised with _𝑠𝑝_ = _𝑠𝑒_ = Ins. 

- If apply( _𝑒𝑑_ ) is called with a deletion event _𝑒𝑑_ , we set _𝑠𝑒_ = Del in the record representing the deleted character. In the same record, if _𝑠𝑝_ = Ins we update it to Del 1, and if _𝑠𝑝_ = Del _𝑛_ it advances to Del( _𝑛_ + 1), as shown in Figure 5. 

- If retreat( _𝑒𝑖_ ) is called with insertion event _𝑒𝑖_ , we must have _𝑠𝑝_ = Ins in the record affected by the event, and we update it to _𝑠𝑝_ = NotInsertedYet. Conversely, advance( _𝑒𝑖_ ) moves _𝑠𝑝_ from NotInsertedYet to Ins. 



<!-- Start of picture text -->
advance: Insert Delete Delete Delete<br>NIY Ins Del 1 Del 2 · · ·<br>retreat: Insert Delete Delete Delete<br><!-- End of picture text -->

**Figure 5.** State machine for internal state variable _𝑠𝑝_ . 



**Figure 6.** Left: the internal state after applying _𝑒_ 1 _...𝑒_ 4 from Figure 4. Right: after retreat( _𝑒_ 4) and retreat( _𝑒_ 3), the prepare state is updated to mark “H” as NotInsertedYet, and the deletion of “h” is undone. The effect state is unchanged. 

|“H”||“h”|“e”||“y”||“!”|“i”|
|---|---|---|---|---|---|---|---|---|
|_id_ :|3|_id_ : 1|_id_ :|6|_id_ :|7|_id_ : 8|_id_ : 2|
|_𝑠𝑝_:|Ins|_𝑠𝑝_:Del 1|_𝑠𝑝_:|Ins|_𝑠𝑝_|:Ins|_𝑠𝑝_:Ins|_𝑠𝑝_:Del 1|
|_𝑠𝑒_:|Ins|_𝑠𝑒_:Del|_𝑠𝑒_:|Ins|_𝑠𝑒_:|Ins|_𝑠𝑒_:Ins|_𝑠𝑒_:Del|



**Figure 7.** The internal Eg-walker state after replaying all of the events in Figure 4. 

- If retreat( _𝑒𝑑_ ) is called with a deletion event _𝑒𝑑_ , we must have _𝑠𝑝_ = Del _𝑛_ in the affected record, and we update it to Del( _𝑛_ − 1) if _𝑛 >_ 1, or to Ins if _𝑛_ = 1. Calling advance( _𝑒𝑑_ ) performs the opposite. 

As a result, _𝑠𝑝_ and _𝑠𝑒_ are Ins if the character is visible (inserted but not deleted) in the prepare and effect version respectively; _𝑠𝑝_ = Del _𝑛_ indicates that the character has been deleted by _𝑛_ concurrent delete events in the prepare version; and _𝑠𝑝_ = NotInsertedYet indicates that the insertion of the character has been retreated in the prepare version. _𝑠𝑒_ does not count the number of deletions and does not have a NotInsertedYet state since we never remove the effect of an operation from the effect version. 

For example, Figure 6 shows the internal state after applying _𝑒_ 1 _. . .𝑒_ 4 from Figure 4, and how that state is updated by retreating _𝑒_ 4 and _𝑒_ 3 before _𝑒_ 5 is applied. In the effect state, the lowercase “h” is marked as deleted, while the uppercase “H” and the “i” are visible. In the prepare state, by retreating _𝑒_ 4 and _𝑒_ 3 the “H” is marked as NotInsertedYet, and the deletion of “h” is undone ( _𝑠𝑝_ = Ins). 

Figure 7 shows the state after replaying all of the events in Figure 4: “i” is also deleted, the characters “e” and “y” are inserted immediately after the “h”, _𝑒_ 3 and _𝑒_ 4 are advanced again, and finally “!” is inserted after the “y”. The figures include the character for the sake of readability, but Eg-walker actually does not store text content in its internal state. 

7 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

### **3.4 Mapping indexes to character IDs** 

In the event graph, insertion and deletion operations specify the index at which they apply. In order to update Eg-walker’s internal state, we need to map these indexes to the correct record in the sequence, based on the prepare state _𝑠𝑝_ . To produce the transformed operations, we need to map the positions of these internal records back to indexes again – this time based on the effect state _𝑠𝑒_ . 

A simple but inefficient algorithm would be: to apply a _Delete_ ( _𝑖_ ) operation we iterate over the sequence of records and pick the _𝑖_ th record with a prepare state of _𝑠𝑝_ = Ins (i.e., the _𝑖_ th among the characters that are visible in the prepare state, which is the document state in which the operation should be interpreted). Similarly, to apply _Insert_ ( _𝑖,𝑐_ ) we skip over _𝑖_ − 1 records with _𝑠𝑝_ = Ins and insert the new record after the last skipped record (if there have been concurrent insertions at the same position, we may also need to skip over some records with _𝑠𝑝_ = NotInsertedYet, as determined by the list CRDT’s insertion ordering). 

To reduce the cost of this algorithm from _𝑂_ ( _𝑛_ ) to _𝑂_ (log _𝑛_ ), where _𝑛_ is the number of characters in the document, we construct a B-tree whose leaves, from left to right, contain the sequence of records representing characters. We extend the tree into an _order statistic tree_ [16] (also known as _ranked B-tree_ ) by adding two integers to each node: the number of records with _𝑠𝑝_ = Ins contained within that subtree, and the number of records with _𝑠𝑒_ = Ins in that subtree. Every time _𝑠𝑝_ or _𝑠𝑒_ are updated, we also update those numbers on the path from the updated record to the root. As the tree is balanced, this update takes _𝑂_ (log _𝑛_ ). 

Now we can find the _𝑖_ th record with _𝑠𝑝_ = Ins in logarithmic time by starting at the root of the tree, and adding up the values in the subtrees that have been skipped. Moreover, once we have a record in the sequence we can efficiently determine its index in the effect state by going in the opposite direction: working upwards in the tree towards the root, and summing the numbers of records with _𝑠𝑒_ = Ins that lie in subtrees to the left of the starting record. This allows us to efficiently transform the index of an operation from the prepare version into the effect version. If the character was already deleted in the effect version ( _𝑠𝑒_ = Del), the transformed operation is a no-op. 

The above process makes apply( _𝑒𝑖_ ) efficient. We also need to efficiently perform retreat( _𝑒𝑖_ ) and advance( _𝑒𝑖_ ), which modify the prepare state _𝑠𝑝_ of the record inserted or deleted by _𝑒𝑖_ . While advancing/retreating we cannot look up a target record by its index. Instead, we maintain a second B-tree, mapping from each event’s ID to the target record. The mapping stores a value depending on the type of the event: 

- For delete events, we store the ID of the character deleted by the event. 

- For insert events, we store a pointer to the leaf node in the first B-tree that contains the corresponding record. 

When nodes in the first B-tree are split, we update the pointers in the second B-tree accordingly. 

On every apply( _𝑒_ ), after updating the sequence as above, we update this mapping. When we later call retreat( _𝑒_ ) or advance( _𝑒_ ), that event _𝑒_ must have already been applied, and hence _𝑒.id_ must appear in this mapping. This map allows us to advance or retreat in logarithmic time. 

### **3.5 Clearing the internal state** 

As described so far, the algorithm retains every insertion since document creation forever in its internal state, consuming a lot of memory, and requiring the entire event graph to be replayed in order to restore the internal state. We now introduce a further optimisation that allows Eg-walker to completely discard its internal state from time to time, and replay only a subset of the event graph. 

We define a version _𝑉_ ⊆ _𝐺_ to be a _critical version_ in an event graph _𝐺_ iff it partitions the graph into two subsets of events _𝐺_ 1 = Events( _𝑉_ ) and _𝐺_ 2 = _𝐺_ − _𝐺_ 1 such that all events in _𝐺_ 1 happened before all events in _𝐺_ 2: 



Equivalently, _𝑉_ is a critical version iff every event in the graph is either in _𝑉_ , or an ancestor of some event in _𝑉_ , or happened after _all_ of the events in _𝑉_ : 



A critical version might not remain critical forever; it is possible for a critical version to become non-critical because a concurrent event is added to the graph. 

A key insight in the design of Eg-walker is that critical versions partition the event graph into sections that can be processed independently. Events that happened at or before a critical version do not affect how any event after the critical version is transformed. This observation enables two important optimisations: 

- Any time the version of the event graph processed so far is critical, we can discard the internal state (including both B-trees and all _𝑠𝑝_ and _𝑠𝑒_ values), and replace it with a placeholder as explained in Section 3.6. 

- If both an event’s version and its parent version are critical versions, there is no need to traverse the B- trees and update the CRDT state, since we would immediately discard that state anyway. In this case, the transformed event is identical to the original event, so the event can simply be emitted as-is. 

These optimisations make it very fast to process documents that are mostly edited sequentially (e.g., because the authors took turns and did not write concurrently, or because there is only a single author), since most of the event graph of such a document is a linear chain of critical versions. 

The internal state can be discarded once replay is complete, although it is also possible to retain the internal state for transforming future events. If a replica receives events 

8 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

that are concurrent with existing events in its graph, but the replica has already discarded its internal state resulting from those events, it needs to rebuild some of that state. It can do this by identifying the most recent critical version that happened before the new events, replaying the existing events that happened after that critical version, and finally applying the new events. Events from before that critical version are not replayed. Since most editing histories have critical versions from time to time, this means that usually only a small subset of the event graph is replayed. In the worst case, this algorithm replays the entire event graph. 

### **3.6 Partial event graph replay** 

Assume that we want to add event _𝑒_ new to the event graph _𝐺_ , that _𝑉_ curr = Version( _𝐺_ ) is the current document version reflecting all events except _𝑒_ new, and that _𝑉_ crit ≠ _𝑉_ curr is the latest critical version in _𝐺_ ∪{ _𝑒_ new} that happened before both _𝑒_ new and _𝑉_ curr. Further assume that we have discarded the internal state, so the only information we have is the latest document state at _𝑉_ curr and the event graph; in particular, without replaying the entire event graph we do not know the document state at _𝑉_ crit. 

Luckily, the exact internal state at _𝑉_ crit is not needed. All we need is enough state to transform _𝑒_ new and rebase it onto the document at _𝑉_ curr. This internal state can be obtained by replaying the events since _𝑉_ crit, that is, _𝐺_ − Events( _𝑉_ crit), in topologically sorted order: 

1. We initialise a new internal state corresponding to version _𝑉_ crit. Since we do not know the the document state at this version, we start with a single placeholder record representing the unknown document content. 

2. We update the internal state by replaying events from _𝑉_ crit to _𝑉_ curr, but we do not output transformed operations during this stage. 

3. Finally, we apply the new event _𝑒_ new and output the transformed operation. If we received a batch of new events, we apply them in topologically sorted order. 

The placeholder record we start with in step 1 represents the range of indexes [0 _,_ ∞] of the document state at _𝑉_ crit (we do not know the length of the document at that version, but we can still have a placeholder for arbitrarily many indexes). Placeholders are counted as the number of characters they represent in the order statistic tree construction, and they have the same length in both the prepare and the effect versions. We then apply events as follows: 

- Applying an insertion at index _𝑖_ creates a record with _𝑠𝑝_ = _𝑠𝑒_ = Ins and the ID of the insertion event. We map the index to a record in the sequence using the prepare state as usual; if _𝑖_ falls within a placeholder for range [ _𝑗,𝑘_ ], we split it into a placeholder for [ _𝑗,𝑖_ − 1], followed by the new record, followed by a placeholder for [ _𝑖,𝑘_ ]. Placeholders for empty ranges are omitted. 

- Applying a deletion at index _𝑖_ : if the deleted character was inserted prior to _𝑉_ crit, the index must fall within a placeholder with some range [ _𝑗,𝑘_ ]. We split it into a placeholder for [ _𝑗,𝑖_ −1], followed by a new record with _𝑠𝑝_ = Del 1 and _𝑠𝑒_ = Del, followed by a placeholder for [ _𝑖_ + 1 _,𝑘_ ]. The new record has a placeholder ID that only needs to be unique within the local replica, and need not be consistent across replicas. 

- Applying a deletion of a character inserted since _𝑉_ crit updates the record created by the insertion. 

Before applying an event we retreat and advance as usual. The algorithm never needs to retreat or advance an event that happened before _𝑉_ crit, therefore every retreated or advanced event ID must exist in second B-tree. 

If there are concurrent insertions at the same position, we invoke the CRDT algorithm to place them in a consistent order as discussed in Section 3.3. Since all concurrent events must be after _𝑉_ crit, they are included in the replay. When we are seeking for the insertion position, we never need to seek past a placeholder, since the placeholder represents characters that were inserted before _𝑉_ crit. 

### **3.7 Algorithm complexity** 

Say we have two users who have been working offline, generating _𝑘_ and _𝑚_ events respectively. When they come online and merge their event graphs, the latest critical version is immediately prior to the branching point. If the branch of _𝑘_ events comes first in the topological sort, the replay algorithm first applies _𝑘_ events, then retreats _𝑘_ events, applies _𝑚_ events, and finally advances _𝑘_ events again. Asymptotically, _𝑂_ ( _𝑘_ + _𝑚_ ) calls to apply/retreat/advance are required regardless of the order of traversal, although in practice the algorithm is faster if _𝑘 < 𝑚_ since we don’t need to retreat/advance on the branch that is visited last. 

Each apply/retreat/advance requires one or two traversals of first B-tree, and at most one traversal of the second B- tree. The upper bound on the number of entries in each tree (including placeholders) is 2( _𝑘_ + _𝑚_ ) + 1, since each event generates at most one new record and one placeholder split. Since the trees are balanced, the cost of each traversal is _𝑂_ (log( _𝑘_ + _𝑚_ )). Overall, the cost of merging branches with _𝑘_ and _𝑚_ events is therefore _𝑂_ (( _𝑘_ + _𝑚_ ) log( _𝑘_ + _𝑚_ )). 

We can also give an upper bound on the complexity of replaying an arbitrary event graph with _𝑛_ events. Each event is applied exactly once, and before each event we retreat or advance each prior event at most once, at _𝑂_ (log _𝑛_ ) cost. The worst-case complexity of the algorithm is therefore _𝑂_ ( _𝑛_<sup>2</sup> log _𝑛_ ), but this case is unlikely to occur in practice. 

### **3.8 Storing the event graph** 

To store the event graph compactly on disk, we developed a compression technique that takes advantage of how people typically write text documents: namely, they tend to insert 

9 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

or delete consecutive sequences of characters, and less frequently hit backspace or move the cursor to a new location. Eg-walker’s event graph storage format is inspired by the Automerge CRDT library [27, 32], which in turn uses ideas from column-oriented databases [6, 55]. We also borrow some bit-packing tricks from the Yjs CRDT library [30]. 

We first topologically sort the events in the graph. Different replicas may sort the graph differently, but locally to one replica we can identify an event by its index in this sorted order. Then we store different properties of events in separate byte sequences called _columns_ , which are then combined into one file with a simple header. Each column stores different fields of the event data. The columns are: 

- _Event type, start position, and run length._ For example, “the first 23 events are insertions at consecutive indexes starting from index 0, the next 10 events are deletions at consecutive indexes starting from index 7,” and so on. We encode this using a variable-length binary encoding of integers, which represents small numbers in one byte, larger numbers in two bytes, etc. 

- _Inserted content._ An insertion event contains exactly one character (a Unicode scalar value), and a deletion does not. We concatenate the UTF-8 encoding of the characters for insertion events in the same order as they appear in the first column, and LZ4-compress. 

- _Parents._ By default we assume that every event has exactly one parent, namely its predecessor in the topological sort. Any events for which this is not true are listed explicitly, for example: “the first event has zero parents; the 153rd event has two parents, namely events 31 and 152;” and so on. 

- _Event IDs._ Each event is uniquely identified by a pair of a replica ID and a per-replica sequence number. This column stores runs of event IDs, for example: “the first 1085 events are from replica _𝐴_ , starting with sequence number 0; the next 595 events are from replica _𝐵_ , starting with sequence number 0;” and so on. 

Replicas can optionally also store a copy of the final document state reflecting all events. This allows documents to be loaded from disk without replaying the event graph. 

We send the same data format over the network when replicating the entire event graph. When sending a subset of events over the network (e.g., a single event during realtime collaboration), references to parent events outside of that subset need to be encoded using event IDs of the form ( _replicaID, seqNo_ ), but otherwise the encoding is similar. 

## **4 Evaluation** 

We created a TypeScript implementation of Eg-walker optimised for simplicity and readability [24], and a productionready Rust implementation optimised for performance [25]. The TypeScript version omits the run-length encoding of internal state, B-trees, and topological sorting heuristics. 

To evaluate the correctness of Eg-walker we proved that the algorithm complies with Attiya et al.’s _strong list specification_ [8] (see Appendix C). We also performed randomised property testing on the implementations, including checking that our implementations converge to the same result. 

### **4.1 Editing traces** 

As there is no established benchmark for collaborative text editing, we collected a set of editing traces from real documents and made them freely available [23]. Statistics for these traces are given in Table 1. The traces represent the editing history of the following documents: 

- **Sequential Traces:** These traces have no concurrency. Trace S1 is the LaTeX source of a journal paper [33, 34], S2 is an 8,800-word blog post [22], and S3 is the text of this paper that you are currently reading. S2 has one author; S1 and S3 have two authors who took turns. 

- **Concurrent Traces:** Trace C1 is two users collaboratively writing a reflection on TV series they have just watched. C2 is two users collaboratively reflecting on going to clown school together. We added 1 sec (C1) or 0.5 sec (C2) artificial latency between the users to increase the incidence of concurrent operations. 

- **Asynchronous Traces:** We reconstructed the editing trace of some files in Git repositories. The event graph mirrors the branching/merging of Git commits. Since Git does not record individual keystrokes, we generated the minimal edit operations necessary to perform each commit’s diff. Trace A1 is src/node.cc from the Git repository for Node.js [4], and A2 is Makefile from the Git repository for Git itself [3]. 

Even though the sequential traces do not exercise the merging algorithm, they are important to include in the benchmark. Anecdotal evidence suggests that the majority of documents in practice are sequentially edited – that is, they either have a single author, or multiple authors who take turns to write. The concurrent traces have many shortlived branches (see the _graph runs_ column in Table 1). The asynchronous traces have a small number of long-running branches, which occur in the context of offline working or editors that support explicit branching and merging [40, 42]. 

We recorded the sequential and concurrent traces ourselves, collaborating with friends or colleagues, using an instrumented text editor that recorded keystroke-granularity editing events. All contributors to the traces have given their consent for their recorded keystroke data to be made publicly available and to be used for benchmarking purposes. The asynchronous traces are derived from public data on GitHub. 

The recorded editing traces originally varied a great deal in length. To allow easier comparison of measurements between traces, we have normalised the length of the traces to contain approximately 500k inserted characters (except for S3, which is approximately twice this size). We did this by 

10 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

**Table 1.** The text editing traces used in our evaluation. _Repeats_ : number of times the original trace was repeated to normalise its length relative to the other traces. _Events_ : total number of editing events, in thousands, including repeats. Each inserted or deleted character counts as one event. _Average concurrency_ : mean number of concurrent branches per event in the trace. _Graph runs_ : number of sequential runs of events (linear event sequences without branching/merging). _Authors_ : number of users who added at least one event. _Chars remaining_ : percentage of inserted characters that remain in the document (i.e., are never deleted) after all events have been merged. _Final size_ : resulting document size in kilobytes after all events have been merged. 

|**Name**|**Type**|**Repeats**|**Events (k)**|**Avg Concurrency**|**Graph runs**|**Authors**|**Chars remaining (%)**|**Final size (kB)**|
|---|---|---|---|---|---|---|---|---|
|S1|sequential|3|779|0.00|1|2|57.5|307.2|
|S2|sequential|3|1105|0.00|1|1|26.7|166.3|
|S3|sequential|1|2339|0.00|1|2|9.9|119.5|
|C1|concurrent|25|652|0.43|92101|2|90.1|521.5|
|C2|concurrent|25|608|0.44|133626|2|93.0|516.3|
|A1|asynchronous|1|947|0.10|101|194|7.8|37.2|
|A2|asynchronous|2|698|6.11|2430|299|49.6|222.0|



repeating the original S1 and S2 traces 3 times, the original C1 and C2 traces 25 times, and the original A2 trace twice. The statistics given in Table 1 are after repetition. 

### **4.2 Experimental approach** 

To evaluate the performance of Eg-walker, we compare our Rust implementation with two popular CRDT libraries: Automerge v0.5.9 [1] (Rust) and Yjs v13.6.10 [30] (JavaScript).<sup>1</sup> We only test their collaborative text datatypes, and not the other features they support. However, the performance of these libraries varies widely. In an effort to distinguish between implementation differences and algorithmic differences, we have also implemented our own performanceoptimised reference CRDT library. This library shares most of its code with our Rust Eg-walker implementation, enabling a more like-to-like comparison between the traditional CRDT approach and Eg-walker. Our reference CRDT outperforms both Yjs and Automerge. 

We have also implemented a simple OT library using the TTF algorithm [46]. We do not use the server-based Jupiter algorithm [44] or the popular OT library ShareDB [21] because they do not support the branching and merging patterns that occur in our asynchronous traces. 

We compare these implementations along 3 dimensions:<sup>2</sup> 

**Speed:** The CPU time to load a document into memory, and to merge a set of updates from a remote replica. 

> 1We also tested Yrs [58], the Rust rewrite of Yjs by the original authors. At the time of our experiments it performed worse than Yjs, so we omitted it from our results. 

> 2Experimental setup: We ran the benchmarks on a Ryzen 7950x CPU running Linux 6.5.0-28 and 64GB of RAM. We compiled Rust code with rustc v1.78.0 in release mode with “-C target-cpu=native”. Rust code was pinned to a single CPU core to reduce variance across runs. For JavaScript (Yjs) we used Node.js v22.2.0. All reported time measurements are the mean of at least 100 test iterations (except for the case where OT takes an hour to merge trace A2, which we ran 10 times). The standard deviation for all benchmark results was less than 1.2% of the mean, except for the Yjs measurements, which had a stddev of less than 6%. Error bars on our graphs are too small to be visible. 

**Memory usage:** The RAM used while a document is loaded and while merging remote updates. 

**Storage size:** The number of bytes needed to persistently store a document or send it over the network. 

By design, our experiments do not run over a network, but focus on single-node CPU and memory use (along with storage size, which is also a measure of network bandwidth used). We made this choice because memory use and loading time are the biggest challenges with CRDTs, and CPU time on long-running branches is the biggest challenge with OT. We delegate replication to the reliable broadcast protocol, which is beyond the scope of this paper. Moreover, the results in a distributed setup would depend highly on the online/offline pattern we assume; running on a single node allows us to more directly compare the algorithms. 

### **4.3 Time taken to load and merge changes** 

The slowest operations in many collaborative editors are: 

- merging a large set of edits from a remote replica into the local state (e.g. reconnecting after working offline); 

- loading a document from disk into memory so that it can be displayed and edited. 

To simulate a worst-case merge, we start with an empty document and then merge an entire editing trace into it. In the case of Eg-walker this means replaying the full trace. Figure 8 shows the merge time for each implementation. Such a large merge occurs in practice when a node has been offline for a long time and needs to catch up on most of the editing history, or when replaying a subset of the editing history in order to reconstruct a historical version of the document (for history visualisation). Moreover, in the CRDT implementations we tested, loading a document from disk takes the same CPU time as merging all of the events. Smaller merges, which occur during online collaboration, are faster. 

After merging the entire trace, we save the resulting local replica state to disk and measure the CPU time to load it back into memory. Since loading and merging take the same 

11 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 



<!-- Start of picture text -->
S1 1.8 ms<br>S2 2.7 ms<br>S3 3.6 ms<br>Eg-walker C1 56.1 ms<br>(merge) C2A1 8.9 ms 82.6 ms<br>A2 23.5 ms<br>S1 0.07 ms<br>S2 0.04 ms<br>S3 0.03 ms<br>Eg-walker C1 0.12 ms<br>(cached load) C2A1 0.11 ms0.01 ms<br>A2 0.05 ms<br>S1 2.4 ms<br>S2 2.8 ms<br>OT C1S3 3.8 ms 365 ms<br>(merge) C2A1 378 ms 6.3 sec<br>A2 61.1 min<br>S1 0.07 ms<br>S2 0.04 ms<br>OT C1S3 0.03 ms0.12 ms<br>(cached load) C2A1 0.11 ms0.01 ms<br>A2 0.05 ms<br>S1 17.9 ms<br>S2 19.1 ms<br>Ref CRDT C1S3 26.9 ms52.5 ms<br>C2 64.2 ms<br>A1 42.7 ms<br>A2 26.2 ms<br>S1 620 ms<br>S2 747 ms<br>S3 1.4 sec<br>Automerge C1 11.8 sec<br>C2 24.6 sec<br>A1 485 ms<br>A2 520 ms<br>S1 57.4 ms<br>S2 85.2 ms<br>S3 79.9 ms<br>Yjs C1 84.1 ms<br>C2 55.2 ms<br>A1 88.4 ms<br>A2 74.2 ms<br>10 100 1k 10k 100k 1M 10M<br>Time taken (in milliseconds) to merge and reload changes. Log scale. (Less is better) →<br><!-- End of picture text -->

**Figure 8.** The CPU time taken by each algorithm to merge all events in each trace (as received from a remote replica), or to reload the resulting document from disk. The CRDT implementations (Ref CRDT, Automerge and Yjs) take the same amount of time to merge changes as they do to subsequently load the document. The red line at 16 ms indicates the time budget available to an application that wants to show the results of an operation by the next frame, assuming a display with a 60 Hz refresh rate. 

time in the CRDTs we tested, we do not show their loading times separately in Figure 8. In these algorithms, the CRDT metadata needs to be in memory for the user to be able to edit the document, or to apply any updates received from other replicas (even when there is no concurrency). In contrast, OT and Eg-walker can load documents orders of magnitude faster than CRDTs by caching the final document state on disk, and loading just this data (essentially a plain text file). Eg-walker and OT only need to load the event graph when merging concurrent changes or to reconstruct old document versions. Document edits by the local user or applying nonconcurrent remote events do not need the event graph. 

We can see in Figure 8 that Eg-walker and OT are very fast to merge the sequential traces (S1, S2, S3), since they simply apply the operations with no transformation. However, OT performance degrades dramatically on the asynchronous traces (6 seconds for A1, and 1 hour for A2) due to the quadratic complexity of the algorithm, whereas Eg-walker remains fast (160,000× faster in the case of A2). 



<!-- Start of picture text -->
S1 1.8 ms<br>S2 2.7 ms<br>S3 3.6 ms<br>Opt enabled C1C2 56.1 ms 82.6 ms<br>A1 8.9 ms<br>A2 23.5 ms<br>S1 9.8 ms<br>S2 17.1 ms<br>S3 24.4 ms<br>Opt disabled C1C2 69.8 ms 95.4 ms<br>A1 23.9 ms<br>A2 23.7 ms<br>0 20 40 60 80 100<br>Time taken to merge all events, in milliseconds. (Less is better) →<br><!-- End of picture text -->

**Figure 9.** Time taken for Eg-walker to merge all events in a trace, with and without the optimisations from Section 3.5. 

On the concurrent traces (C1, C2) and asynchronous trace A2, the merge time of Eg-walker is similar to that of our reference CRDT, since they perform similar work. Both are significantly faster than the state-of-the-art Yjs and Automerge CRDT libraries; this is due to implementation differences and not fundamental algorithmic reasons. 

On the sequential traces Eg-walker outperforms our reference CRDT by a factor of 7–10×, and on trace A1 (which contains large sequential sections) Eg-walker is 5× faster. Comparing to Yjs or Automerge, this speedup is greater still. This is due to Eg-walker’s ability to clear its internal state and skip all of the internal state manipulation on critical versions (Section 3.5). To quantify this effect, Figure 9 compares the time taken to replay all our traces with this optimisation enabled and disabled. We see that the optimisation is effective for S1, S2, S3, and A1, whereas for C1, C2, and A2 it makes little difference (A2 contains no critical versions). 

When merging an event graph with very high concurrency (like A2), the performance of Eg-walker is highly dependent on the order in which events are traversed. A poorly chosen traversal order can make this trace as much as 8× slower to merge. Our topological sort algorithm (Section 3.2) tries to avoid such pathological cases. 

### **4.4 RAM usage** 

Figure 10 shows the memory footprint (retained heap size) of each algorithm. For Eg-walker and OT it shows both peak usage (while replaying the entire editing trace) and “steady state” memory usage (after temporary data and Eg-walker’s internal state are discarded and the event graph is written out to disk). For the CRDTs the figure shows steady state memory usage; peak usage is up to 25% higher. 

Eg-walker’s peak memory use is similar to our reference CRDT’s steady state: slightly lower on the sequential traces, and approximately double for the concurrent traces. However, the steady-state memory use of Eg-walker is 1–2 orders of magnitude lower than the best CRDT. This is a significant result, since the steady state is what matters during normal operation while a document is being edited. Memory usage reaches this peak only when replaying the entire trace; it is lower when merging a small branch. Yjs has up 

12 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 



<!-- Start of picture text -->
S1 4.7 MiB<br>S2 7.4 MiB<br>S3 14.9 MiB<br>Eg-walker C1C2 68.5 MiB79.5 MiB<br>(peak) A1 7.7 MiB<br>A2 8 MiB<br>S1 597 KiB<br>S2 324 KiB<br>S3 233 KiB<br>Eg-walker C1C2 1 MiB1 MiB<br>(steady) A1 72.9 KiB<br>A2 432 KiB<br>S1 49 MiB<br>S2 24.8 MiB<br>S3 25.3 MiB<br>OT C1 337 MiB<br>C2 338 MiB<br>(peak) A1 34.9 MiB<br>A2 6.8 GiB<br>S1 597 KiB<br>S2 324 KiB<br>S3 233 KiB<br>OT C1 1 MiB<br>C2 1 MiB<br>(steady) A1 72.9 KiB<br>A2 429 KiB<br>S1 11.7 MiB<br>S2 8.5 MiB<br>S3 13 MiB<br>Ref CRDT C1 30.9 MiB<br>C2 34 MiB<br>A1 10.3 MiB<br>A2 6.5 MiB<br>S1 19.5 MiB<br>S2 25.7 MiB<br>S3 30.3 MiB<br>Yjs C1C2 19.8 MiB27 MiB<br>A1 30.2 MiB<br>A2 24.9 MiB<br>S1 294 MiB<br>S2 426 MiB<br>S3 848 MiB<br>Automerge C1C2 462 MiB511 MiB<br>A1 241 MiB<br>A2 271 MiB<br>100 KiB 1 MiB 10 MiB 100 MiB 1 GiB 10 GiB<br>RAM used, log scale. (Less is better) →<br><!-- End of picture text -->

**Figure 10.** RAM used while merging an editing trace received from another replica. Eg-walker and OT only retain the current document text in the steady state, but need additional RAM at peak while merging concurrent changes. 

to a 3× greater memory use than our reference CRDT, and Automerge an order of magnitude greater. 

OT has the same memory use as Eg-walker in the steady state, but significantly higher peak memory use on the C1, C2, and A2 traces (6.8 GiB for A2). The reason is that our OT implementation memoizes intermediate transformed operations to improve performance. This memory use could be reduced at the cost of increased merge times. The computer we used for benchmarking had enough RAM to prevent swapping in all cases. 

### **4.5 Storage size** 

Our binary encoding of event graphs (Section 3.8) results in smaller files than the equivalent internal CRDT state persisted by Automerge, and in many cases, Yjs. To ensure a like-for-like comparison we have disabled Eg-walker’s builtin LZ4 and Automerge’s built-in gzip compression. Enabling this compression further reduces the file sizes. 

Automerge stores the full editing history of a document, and Figure 11 shows the resulting file sizes relative to the raw concatenated text content of all insertions, with and without a cached copy of the final document state (to enable fast loads). 

In contrast, Yjs only stores the resulting document text, and any data needed to merge changes. Yjs does not store 



<!-- Start of picture text -->
S1 611 KiB<br>S2 753 KiB<br>S3 1.4 MiB<br>Eg-walker C1 1 MiB<br>C2 1.2 MiB<br>A1 602 KiB<br>A2 561 KiB<br>S1 925 KiB<br>S2 923 KiB<br>S3 1.5 MiB<br>Eg-walker C1 1.6 MiB<br>+ cached C2 1.8 MiB<br>final doc A1A2 640 KiB789 KiB<br>S1 878 KiB<br>S2 1.2 MiB<br>S3 1.9 MiB<br>Automerge C1 1.6 MiB<br>C2 1.7 MiB<br>A1 1.4 MiB<br>A2 1.1 MiB<br>0 B 500 KiB 1 MiB 1.5 MiB 2 MiB<br>Encoded size (lower is better) →<br><!-- End of picture text -->

**Figure 11.** File size storing edit traces using Eg-walker’s event graph encoding (with and without final document caching) compared to Automerge. The lightly shaded region in each bar shows the concatenated length of all stored text. This acts as lower bound on the file size. 



<!-- Start of picture text -->
S1 378 KiB<br>S2 285 KiB<br>Eg-walker C1S3 268 KiB 981 KiB<br>final doc text C2 1.2 MiB<br>only A1A2 151 KiB 330 KiB<br>S1 480 KiB<br>S2 406 KiB<br>Yjs C1S3 318 KiB 845 KiB<br>C2 726 KiB<br>A1 308 KiB<br>A2 506 KiB<br>0 B 200 KiB 400 KiB 600 KiB 800 KiB 1 MiB 1.2 MiB<br>Encoded size (lower is better) →<br><!-- End of picture text -->

**Figure 12.** File size storing edit traces in which deleted text content has been omitted, as is the case with Yjs. The lightly shaded region in each bar is the size of the final document, which is a lower bound on the file size. 

deleted characters or the happened-before relationship between events. Figure 12 compares Yjs to the equivalent event graph encoding in which we only store the final document text and operation metadata. Our encoding is smaller than Yjs on the sequential and async traces, but larger for the concurrent traces, where the edges in the event graph take more space. The overhead of storing the event graph is between 20% and 3× the final plain text file size. 

## **5 Related Work** 

Eg-walker is an example of a _pure operation-based CRDT_ [9], which is a family of algorithms that capture a DAG (or partially ordered log) of operations in the form they were generated, and define the current state as a query over that log. However, existing publications on pure operation-based CRDTs [7, 10] present only datatypes such as maps, sets, and registers; Eg-walker adds a list/text datatype to this family. 

MRDTs [54] are similarly based on a DAG, and use a threeway merge function to combine two branches since their 

13 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

lowest common ancestor; if the LCA is not unique, a recursive merge is used. MRDTs for various datatypes have been defined, but so far none offers text with arbitrary insertion and deletion. 

Toomim’s _time machines_ approach [59] shares a conceptual foundation with Eg-walker: both are based on traversing an event graph, with operations being transformed from their original form into a form that can be applied in topologically sorted order. Toomim also points out that CRDTs can implement this transformation. Eg-walker is a concrete, optimised implementation of the time machine approach; novel contributions of Eg-walker include updating the prepare version by retreating and advancing, as well as the details of internal state clearing and partial event graph replay. 

Eg-walker is also an _operational transformation_ (OT) algorithm [18]. OT has a long lineage of research going back to the 1990s [44, 51, 56]. To our knowledge, all existing OT algorithms consist of a set of _transformation functions_ that transform one operation with regard to one other operation, and a _control algorithm_ that traverses an editing history and invokes the necessary transformations. A problem with this architecture is that when two replicas have diverged and each performed _𝑛_ operations, merging their states unavoidably has a cost of at least _𝑂_ ( _𝑛_<sup>2</sup> ); in some OT algorithms the cost is cubic or even worse [39, 52, 57]. Eg-walker departs from the transformation function/control algorithm architecture and instead performs transformations using an internal CRDT state, which reduces the merging cost to _𝑂_ ( _𝑛_ log _𝑛_ ) in most cases; the upper bound of _𝑂_ ( _𝑛_<sup>2</sup> log _𝑛_ ) is unlikely to occur in practical editing histories. 

Other collaborative text editing algorithms [49, 52, 60, 61] belong to the family of _conflict-free replicated data types_ (CRDTs) [53]. To our knowledge, all existing CRDTs for text work by assigning each character a unique ID, and translating index-based insertions and deletions into ID-based ones. These unique IDs need to be held in memory when a document is being edited, persisted for the lifetime of the document, and sent to all replicas. In contrast, Eg-walker uses unique IDs only transiently during replay but does not persist or replicate them, and it can free its internal state whenever a critical version is reached. Eg-walker needs to store the event graph as long as concurrent operations may arrive, but this takes less space than CRDT state, and it only needs to be in-memory while merging concurrent operations. Most of the time the event graph can remain on disk. 

Gu et al.’s mark & retrace method [28] builds a CRDT-like structure containing the entire editing history, not only the parts being merged. Differential synchronization [20] relies on heuristics such as similarity-matching of text to perform merges, which is not guaranteed to converge. 

line-based (good for code), whereas Eg-walker is characterbased (which is better for prose). Git uses a three-way merge, which is not reliable on files containing substantial repeated text [31]. Merges in Darcs have worst-case exponential complexity [37], and Pijul merges using a CRDT that assigns a unique ID to every line [5]. 

## **6 Conclusion** 

Eg-walker is a new approach to collaborative text editing that has characteristics of both CRDTs and OT. It is orders of magnitude faster than existing algorithms in the best cases, and competitive with the fastest existing implementations in the worst cases. Compared to existing CRDTs, it uses orders of magnitude less memory in the steady state, files are vastly faster to load for editing, and in documents with largely sequential editing edits from other users are merged much faster. Compared to OT, large merges (e.g., when two users each did a significant amount of work while offline) are much faster, and Eg-walker supports arbitrary branching/merging patterns (e.g., in peer-to-peer collaboration). 

Since Eg-walker stores a fine-grained editing history of a document, it allows applications to show that history to the user, and to restore arbitrary past versions of a document by replaying subsets of the graph. The underlying event graph is not specific to the Eg-walker algorithm, so we expect that the same data format will be able to support future collaborative editing algorithms as well. The core idea of Eg-walker is not specific to plain text; we believe it can be extended to other file types such as rich text, graphics, or spreadsheets. 

Until now, many applications have been implemented using centralised server-based OT to avoid the overheads of CRDTs. Eg-walker is the first CRDT to match OT’s memory use and performance on sequential editing histories (which are common in practice), while avoiding the quadratic merge complexity that makes OT impractical for longrunning branches. By requiring no server, Eg-walker makes it possible for decentralised, local-first software [36] to become competitive with traditional cloud software. 

## **Acknowledgments** 

This work was made possible by generous support from Michael Toomim, the Braid community and the Invisible College. None of this would have happened without their help. Thank you for the endless conversations we have shared about collaborative editing. Martin Kleppmann gratefully acknowledges his crowdfunding supporters including Mintter and SoftwareMill. Thank you to Matthew Weidner, Joe Hellerstein, and our shepherd Diogo Behrens for feedback that helped improve this paper. 

Version control systems such as Git [14], Pijul [43], and Darcs [2] also track the editing history of text files. However, they do not support real-time collaboration, and they are 

14 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

## **References** 

- [1] [n. d.]. _Automerge CRDT_ . https://automerge.org/ 

- [2] [n. d.]. _Darcs_ . https://darcs.net/ 

- [3] [n. d.]. _Makefile for Git_ . https://github.com/git/git/blob/master/ Makefile 

- [4] [n. d.]. _Node.js source code: src/node.cc_ . https://github.com/nodejs/ node/blob/main/src/node.cc 

- [5] [n. d.]. _The Pijul manual: Theory_ . https://pijul.org/manual/theory.html Archived at https://perma.cc/NAU4-SMYZ. 

- [6] Daniel J Abadi, Peter Boncz, Stavros Harizopoulos, Stratos Idreos, and Samuel Madden. 2013. The Design and Implementation of Modern Column-Oriented Database Systems. _Foundations and Trends in Databases_ 5, 3 (2013), 197–280. https://doi.org/10.1561/1900000024 

- [7] Paulo Sérgio Almeida. 2023. Approaches to Conflict-free Replicated Data Types. (Oct. 2023). arXiv:2310.18220 https://arxiv.org/abs/2310. 18220 

- [8] Hagit Attiya, Sebastian Burckhardt, Alexey Gotsman, Adam Morrison, Hongseok Yang, and Marek Zawirski. 2016. Specification and Complexity of Collaborative Text Editing. In _ACM Symposium on Principles of Distributed Computing (PODC 2016)_ . 259–268. https: //doi.org/10.1145/2933057.2933090 

- [9] Carlos Baquero, Paulo Sergio Almeida, and Ali Shoker. 2017. Pure Operation-Based Replicated Data Types. (2017). https://arxiv.org/abs/ 1710.04469 

- [10] Jim Bauwens and Elisa Gonzalez Boix. 2023. Nested Pure OperationBased CRDTs. In _37th European Conference on Object-Oriented Programming (ECOOP 2023)_ . Schloss Dagstuhl, 2:1–2:26. https://doi.org/ 10.4230/LIPIcs.ECOOP.2023.2 

- [11] Kenneth Birman, André Schiper, and Pat Stephenson. 1991. Lightweight causal and atomic group multicast. _ACM Transactions on Computer Systems_ 9, 3 (Aug. 1991), 272–314. https://doi.org/10.1145/128738. 128742 

- [12] Hans-J. Boehm, Russ Atkinson, and Michael Plass. 1995. Ropes: An alternative to strings. _Software Prac. Experience_ 25, 12 (1995), 1315– 1330. https://doi.org/10.1002/spe.4380251203 

- [13] Christian Cachin, Rachid Guerraoui, and Luís Rodrigues. 2011. _Introduction to Reliable and Secure Distributed Programming_ (second ed.). Springer. 

- [14] James Coglan. 2019. _Building Git_ . https://shop.jcoglan.com/buildinggit/ 

- [15] Paul Coldren. 2024. _Engineering for Slow Internet: How to minimize user frustration in Antarctica_ . https://brr.fyi/posts/engineering-forslow-internet Archived at https://perma.cc/7Q9B-TUJV. 

- [16] Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, and Clifford Stein. 2009. _Introduction to Algorithms_ (third ed.). MIT Press. 

- [17] John Day-Richter. 2010. _What’s different about the new Google Docs: Making collaboration fast_ . https://drive.googleblog.com/2010/09/ whats-different-about-new-google-docs.html Archived at https: //perma.cc/5FVM-542B. 

- [18] C A Ellis and S J Gibbs. 1989. Concurrency control in groupware systems. In _ACM International Conference on Management of Data (SIGMOD 1989)_ . 399–407. https://doi.org/10.1145/67544.66963 

- [19] Aaron Fabbri. 2024. _How Ditto Empowers the U.S. Navy’s Unmanned Vehicle Operations, Keeping America Safe_ . https://ditto.live/blog/howditto-empowers-the-u-s-navy-s-unmanned-vehicle-operationskeeping-america-safe Archived at https://perma.cc/UAM9-JX5C. 

- [20] Neil Fraser. 2009. Differential synchronization. In _9th ACM Symposium on Document Engineering (DocEng 2009)_ . ACM, 13–20. https://doi.org/ 10.1145/1600193.1600198 

- [21] Joseph Gentle. 2014. _ShareDB_ . https://github.com/share/sharedb 

- [22] Joseph Gentle. 2021. _5000x faster CRDTs: An Adventure in Optimization_ . https://josephg.com/blog/crdts-go-brrr/ 

- [23] Joseph Gentle. 2023. _Editing Traces (github repository)_ . https://github. com/josephg/editing-traces 

- [24] Joseph Gentle. 2023. _Reference Eg-walker implementation in Typescript_ . https://github.com/josephg/eg-walker-reference 

- [25] Joseph Gentle. 2024. _Diamond Types: A fully featured realtime editing library_ . https://github.com/josephg/diamond-types 

- [26] Victor B F Gomes, Martin Kleppmann, Dominic P Mulligan, and Alastair R Beresford. 2017. Verifying strong eventual consistency in distributed systems. _Proceedings of the ACM on Programming Languages (PACMPL)_ 1, OOPSLA (Oct. 2017). https://doi.org/10.1145/3133933 arXiv:1707.01747 

- [27] Alex Good and Andrew Jeffery. [n. d.]. _Automerge Binary Document Format_ . https://automerge.org/automerge-binary-format-spec/ Archived at https://perma.cc/XV25-RA4U. 

- [28] Ning Gu, Jiangming Yang, and Qiwei Zhang. 2005. Consistency maintenance based on the mark & retrace technique in groupware systems. In _ACM International Conference on Supporting Group Work (GROUP 2005)_ . ACM, 264–273. https://doi.org/10.1145/1099203.1099250 

- [29] Joseph M Hellerstein. 2010. The Declarative Imperative: Experiences and Conjectures in Distributed Logic. _ACM SIGMOD Record_ 39, 1 (Sept. 2010), 5–19. https://doi.org/10.1145/1860702.1860704 

- [30] Kevin Jahns. [n. d.]. _Yjs Shared Editing_ . https://yjs.dev/ 

- [31] Sanjeev Khanna, Keshav Kunal, and Benjamin C Pierce. 2007. A Formal Investigation of Diff3. In _27th International Conference on Foundations of Software Technology and Theoretical Computer Science (FSTTCS 2007)_ . Springer, 485–496. https://doi.org/10.1007/978-3-540-77050-3_40 

- [32] Martin Kleppmann. 2019. _Experiment: columnar data encoding for Automerge_ . https://github.com/automerge/automerge-perf/blob/master/ columnar/README.md Archived at https://perma.cc/57KC-PP4Y. 

- [33] Martin Kleppmann. 2020. _Benchmarking resources for Automerge_ . https: //github.com/automerge/automerge-perf 

- [34] Martin Kleppmann and Alastair R Beresford. 2017. A Conflict-Free Replicated JSON Datatype. _IEEE Transactions on Parallel and Distributed Systems_ 28, 10 (April 2017), 2733–2746. https://doi.org/10. 1109/TPDS.2017.2697382 arXiv:1608.03960 

- [35] Martin Kleppmann, Annette Bieniusa, and Marc Shapiro. [n. d.]. _CRDT Papers_ . https://crdt.tech/papers.html 

- [36] Martin Kleppmann, Adam Wiggins, Peter van Hardenberg, and Mark McGranaghan. 2019. Local-First Software: You own your data, in spite of the cloud. In _ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software (Onward! 2019)_ . ACM, 154–178. https://doi.org/10.1145/3359591.3359737 

- [37] Eric Kow. [n. d.]. _Understanding Darcs_ . https://en.wikibooks.org/wiki/ Understanding_Darcs/Print_Version Archived at https://perma.cc/ 3VZF-8J65. 

- [38] Leslie Lamport. 1978. Time, clocks, and the ordering of events in a distributed system. _Commun. ACM_ 21, 7 (1978), 558–565. https: //doi.org/10.1145/359545.359563 

- [39] Du Li and Rui Li. 2006. A performance study of group editing algorithms. In _12th International Conference on Parallel and Distributed Systems (ICPADS 2006)_ . https://doi.org/10.1109/icpads.2006.18 

- [40] Geoffrey Litt, Paul Sonnentag, Max Schöning, Adam Wiggins, Peter van Hardenberg, and Orion Henry. 2024. _Patchwork lab notebook: Version control for everything_ . Technical Report. Ink & Switch. https: //www.inkandswitch.com/patchwork/notebook/ Archived at https: //perma.cc/VJM7-YPJB. 

- [41] Peng Lyu. 2018. _Text Buffer Reimplementation_ . https://code. visualstudio.com/blogs/2018/03/23/text-buffer-reimplementation Archived at https://perma.cc/V695-K7EL. 

- [42] Karissa Rae McKelvey, Scott Jenson, Eileen Wagner, Blaine Cook, and Martin Kleppmann. 2023. _Upwelling: Combining real-time collaboration with version control for writers_ . Technical Report. Ink & Switch. https: //www.inkandswitch.com/upwelling/ Archived at https://perma.cc/ 7TT8-X8S9. 

- [43] Pierre-Étienne Meunier and Florent Becker. [n. d.]. _Pijul_ . https://pijul. org/ 

15 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

- [44] David A Nichols, Pavel Curtis, Michael Dixon, and John Lamping. 1995. High-latency, low-bandwidth windowing in the Jupiter collaboration system. In _8th Annual ACM Symposium on User Interface and Software Technology (UIST 1995)_ . 111–120. https://doi.org/10.1145/ 215585.215706 

- [45] Petru Nicolaescu, Kevin Jahns, Michael Derntl, and Ralf Klamma. 2016. Near Real-Time Peer-to-Peer Shared Editing on Extensible Data Types. In _19th International Conference on Supporting Group Work (GROUP 2016)_ . ACM, 39–49. https://doi.org/10.1145/2957276.2957310 

- [46] Gérald Oster, Pascal Molli, Pascal Urso, and Abdessamad Imine. 2006. Tombstone Transformation Functions for Ensuring Consistency in Collaborative Editing Systems. In _9th IEEE International Conference on Collaborative Computing (CollaborateCom 2006)_ . https://doi.org/10. 1109/colcom.2006.361867 

   - [58] Bartosz Sypytkowski, Kevin Jahns, and John Waidhofer. [n. d.]. _Y CRDT: Rust port of Yjs_ . https://github.com/y-crdt/y-crdt 

   - [59] Michael Toomim. 2024. CRDT and OT generalize as Time Machines. (2024). https://braid.org/time-machines Archived at https://perma. cc/VND4-ACEK. 

   - [60] Matthew Weidner and Martin Kleppmann. 2023. The Art of the Fugue: Minimizing Interleaving in Collaborative Text Editing. (2023). https: //arxiv.org/abs/2305.00583 

   - [61] Stéphane Weiss, Pascal Urso, and Pascal Molli. 2010. Logoot-Undo: Distributed Collaborative Editing System on P2P Networks. _IEEE Transactions on Parallel and Distributed Systems_ 21, 8 (2010), 1162– 1174. https://doi.org/10.1109/tpds.2009.173 

- [47] Gérald Oster, Pascal Urso, Pascal Molli, and Abdessamad Imine. 2006. Data consistency for P2P collaborative editing. In _ACM Conference on Computer Supported Cooperative Work (CSCW 2006)_ . 259–268. https: //doi.org/10.1145/1180875.1180916 

- [48] Overleaf. [n. d.]. _Can multiple authors edit the same file at the same time?_ https://www.overleaf.com/learn/how-to/Can_multiple_ authors_edit_the_same_file_at_the_same_time%3F Archived at https: //perma.cc/P8TH-YRMX. 

- [49] Nuno Preguiça, Joan Manuel Marques, Marc Shapiro, and Mihai Letia. 2009. A Commutative Replicated Data Type for Cooperative Editing. In _29th IEEE International Conference on Distributed Computing Systems (ICDCS 2009)_ . 395–403. https://doi.org/10.1109/icdcs.2009.20 

- [50] Ryan Ratner. 2024. _ANA Elevates Onboard Passenger Experience with Ditto_ . https://ditto.live/blog/ana-elevates-onboard-passengerexperience-with-ditto Archived at https://perma.cc/M6NG-AWLM. 

- [51] Matthias Ressel, Doris Nitsche-Ruhland, and Rul Gunzenhäuser. 1996. An integrating, transformation-oriented approach to concurrency control and undo in group editors. In _ACM Conference on Computer Supported Cooperative Work (CSCW 1996)_ . 288–297. https: //doi.org/10.1145/240080.240305 

- [52] Hyun-Gul Roh, Myeongjae Jeon, Jin-Soo Kim, and Joonwon Lee. 2011. Replicated Abstract Data Types: Building Blocks for Collaborative Applications. _J. Parallel and Distrib. Comput._ 71, 3 (March 2011), 354– 368. https://doi.org/10.1016/j.jpdc.2010.12.006 

- [53] Marc Shapiro, Nuno Preguiça, Carlos Baquero, and Marek Zawirski. 2011. Conflict-free Replicated Data Types. In _13th International Conference on Stabilization, Safety, and Security of Distributed Systems (SSS 2011)_ . 386–400. https://doi.org/10.1007/978-3-642-24550-3_29 

- [54] Vimala Soundarapandian, Adharsh Kamath, Kartik Nagar, and KC Sivaramakrishnan. 2022. Certified mergeable replicated data types. In _43rd ACM SIGPLAN International Conference on Programming Language Design and Implementation (PLDI 2022)_ . ACM, 332–347. https://doi. org/10.1145/3519939.3523735 

- [55] Michael Stonebraker, Daniel J Abadi, Adam Batkin, Xuedong Chen, Mitch Cherniack, Miguel Ferreira, Edmond Lau, Amerson Lin, Samuel Madden, Elizabeth O’Neil, Patrick O’Neil, Alexander Rasin, Nga Tran, and Stanley Zdonik. 2005. C-Store: A Column-oriented DBMS. In _31st International Conference on Very Large Data Bases (VLDB 2005)_ . 553–564. 

- [56] Chengzheng Sun, Xiaohua Jia, Yanchun Zhang, Yun Yang, and David Chen. 1998. Achieving convergence, causality preservation, and intention preservation in real-time cooperative editing systems. _ACM Transactions on Computer-Human Interaction_ 5, 1 (March 1998), 63–108. https://doi.org/10.1145/274444.274447 

- [57] David Sun, Chengzheng Sun, Agustina Ng, and Weiwei Cai. 2020. Real Differences between OT and CRDT in Correctness and Complexity for Consistency Maintenance in Co-Editors. _Proceedings of the ACM on Human-Computer Interaction_ 4, CSCW1, Article 21 (May 2020), 30 pages. https://doi.org/10.1145/3392825 

16 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

## **A Artifact Appendix** 

### **A.1 Abstract** 

Our artifact is an implementation of the Eg-walker algorithm, together with the datasets and benchmarking tools required to run the experiments described in the paper. Specifically, it contains the following items: 

- Editing traces of text documents that we use for benchmarking (see Table 1), in our own binary format and as JSON 

- Source code for the following tools: 

- Our optimised Eg-walker implementation, written in Rust 

- Our reference CRDT implementation 

- Our reference OT implementation 

- Tools to convert our editing traces to JSON, and to the Yjs and Automerge file formats 

- Benchmarking tools to reproduce all experiments 

- The tools to generate the figures in this paper 

### **A.2 Description & Requirements** 

**A.2.1 How to access.** All code and data of the artifact is publicly available in the following GitHub repository: 

https://github.com/josephg/egwalker-paper A snapshot of this repository is archived at: https://zenodo.org/records/13823409 doi:10.5281/zenodo.13823409 

The README file in this repository contains detailed instructions to configure & run our code locally. 

**A.2.2 Hardware dependencies.** To run our experiments, you need the following: 

- A computer running Linux. Our software should work on a number of other systems including Windows and MacOS, but we have not tested on other platforms. 

- At least 8GB of RAM, but 16GB is recommended. This is mainly required for the OT/A2 benchmark, which at peak uses approximately 7GB of RAM. 

- Plenty of disk space. The Rust compiler produces a lot of temporary files – 44GB on our system. 

**A.2.3 Software dependencies.** You need a recent compiler and runtime for the Rust programming language. We have tested with Rust 1.78; newer versions should also work. 

You also need NodeJS installed in order to run the Yjs benchmark and to generate the charts showing our results. We used version 21. 

Other software dependencies are managed through Cargo and npm respectively, and they are automatically installed as part of the build process. 

**A.2.4 Benchmarks.** The datasets used by our benchmarks are included in the datasets/ folder of our artifact repository, and the code to run the benchmarks is in the tools/ 

folder. Each of these folders contains a README.md file that documents its contents in more detail. 

### **A.3 Set-up** 

Install Rust (the easiest way is via rustup) and NodeJS. 

### **A.4 Evaluation workflow** 

Please see the README.md file in the artifact repository for a detailed description of the process. 

### **A.4.1 Major Claims.** 

- **C1.** Eg-walker is competitive with existing state-of-theart collaborative editing systems in terms of file size, memory usage, and CPU time taken to merge changes. That is, on all our benchmarks, Eg-walker has at most ≈ 2× the cost of the other systems we test, and in some cases dramatically lower cost. Experiments E1, E2, E3 and Figures 8–12 support this claim. 

- **C2.** On editing traces with long-running branches, the merge performance of Eg-walker is several orders of magnitude faster than OT, and slightly faster than the best CRDT implementations. This claim is supported by Experiment E3 and the data in Figure 8. 

- **C3.** On sequential editing traces, Eg-walker is as fast as OT, and about an order of magnitude faster than our reference CRDT (which in turn is the fastest among the CRDTs we tested). This is supported by Experiment E3 and the data in Figure 8. This is in large part due to the optimisations in Section 3.5, as shown in Figure 9. 

- **C4.** The time to load a document from disk (to view and edit) with Eg-walker is the same as with OT, which is several orders of magnitude faster than all CRDTs we tested. Experiment E3 and Figure 8 also show this. 

- **C5.** The steady-state memory consumption of Eg-walker is at least an order of magnitude lower than that of CRDTs, and the same as OT. The peak memory consumption of Eg-walker during merging is similar to the steady state of CRDTs, and lower than OT. This claim is supported by Experiment E2 and the data in Figure 10. 

**A.4.2 Experiment E1: File sizes (4 compute-hours).** Converts the raw editing traces in the datasets/raw directory into the Yjs, Automerge, and Eg-walker file formats (datasets/*.{yjs,am,dt}). Some of the traces are repeated several times so that they have a similar size (see Section 4.1). The sizes of the resulting files are reported in Figure 11 and Figure 12. The files also form an input to the subsequent experiments. The output files are included with the artifact so that the later experiments can be run without having to run this one. 

To run this experiment: 

rm datasets/* ./step1-prepare.sh 

17 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

The resulting files should be byte-for-byte identical to those in the artifact. This experiment is slow because we have not made much effort to optimise it. 

To generate the figures: 

node collect.js 

cd svg-plot 

npm i # only needed once to install dependencies node render.js 

which writes Figure 11 to diagrams/filesize_full.svg and Figure 12 to diagrams/filesize_smol.svg. 

**A.4.3 Experiment E2: Memory use (1 compute-hour).** This experiment measures memory use – both the peak memory use while replaying each editing trace, and the “steady state” memory use once the replay is complete (keeping in memory the structures that are needed to display and edit a document, but freeing the structures that are needed only for replay and merging concurrent edits). We test with the following algorithms: 

- Diamond-types (DT): our optimised Eg-walker implementation 

- DT-CRDT: our reference CRDT implementation 

- Automerge [1] 

- Yjs [30] 

- Yrs, the port of Yjs to Rust by the original authors [58] 

- OT: our reference OT implementation 

To run this experiment: 

./step2a-memusage.sh 

Then use collect.js and render.js like in E1 to generate Figure 10 (diagrams/memusage.svg). The data appears in results/*_memusage.json. 

Note: Our OT implementation takes 1 hour to replay the A2 editing trace. 

**A.4.4 Experiment E3: Merge time (12 compute-hours).** This experiment measures the CPU time taken to merge the entire editing trace into an empty document, as if it had been received from a remote peer. The benchmarks cover the same algorithms as Experiment E2. To run this experiment: 

./step2b-benchmarks.sh 

Then use collect.js and render.js like in E1 to generate the charts for Figure 8 (diagrams/timings.svg) and Figure 9 (diagrams/ff.svg). The script writes summary data to results/timings.json. 

### **A.5 Notes on Reusability** 

As part of this work, we have also created several editing traces of real text documents, as described in Section 4.1. The intention is that these traces can be used in future research for benchmarking collaborative text editing systems. The datasets/ directory of the artifact contains those traces in JSON format. Additional traces that we may collect in the future will be added to the following repository: 

https://github.com/josephg/editing-traces 

That repository also contains documentation of the JSONbased file format that we use to encode the editing traces. 

If you want to benchmark a CRDT using these editing traces, you need to convert them to your CRDT’s local format. We do this by simulating (in memory) a set of collaborating peers. The peers fork and merge their changes. The tools/crdt-converter directory of our artifact contains code to perform this process using Automerge and Yjs (Yrs). We believe this algorithm could be adapted to support most existing CRDT formats and systems. 

### **A.6 General Notes** 

The performance of collaborative editing systems varies by orders of magnitude depending on how the implementation has been optimised. For example, early CRDTs were widely thought to be impractical in real systems because they were so slow and memory-inefficient, taking gigabytes of RAM and hard disk space to process editing traces smaller than the ones we present in this paper. And yet, the CRDTs we benchmark here, like Yjs and Automerge, can process large documents with very reasonable computational resources. 

These improvements have come through a mixture of improved data structures and algorithms, and improved implementation techniques such as reduced memory allocation and better memory layout. Unfortunately, the techniques that these implementations use to achieve their performance are not well documented in either the academic literature or the documentation of those projects. It is difficult to determine which combination of factors is responsible for a performance improvement. 

As a result, it is difficult to fairly compare algorithms that are implemented by different developers and use different implementation techniques. We have attempted to address this issue by writing our own reference CRDT and OT implementations that use a similar implementation style to our optimised Eg-walker implementation, enabling as much as possible an apples-for-apples comparison. We hope that future research will be able to develop more rigorous approaches to evaluating collaborative editing systems. 

While our artifact contains a snapshot of our Eg-walker implementation at the time of this paper was written, the ongoing development of the implementation is part of the Diamond Types project in the following repository: 

https://github.com/josephg/diamond-types 

Diamond Types is freely available under the ISC license. 

18 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

## **B Algorithm Pseudocode** 

Listing 1 and Listing 2 provide simplified pseudocode for the core Eg-walker algorithm as described in Section 3.2 and 3.3. For brevity, the pseudocode does not include the B-trees from Section 3.4, the state clearing optimisation from Section 3.5, and the partial replay optimisation from Section 3.6. 

**let** events: **EventStorage** // Assumed to contain all events 

**enum PREPARE_STATE** { NOT_YET_INSERTED = 0 INSERTED = 1 // Any state 2+ means the item has been concurrently deleted n-1 times. } 

- // Each of these corresponds to a single inserted character. **type AugmentedCRDTItem** { 

// The fields from the CRDT that determines insertion order id, originLeft, originRight, 

- // State at effect version. Either inserted or inserted-and-subsequently-deleted. ever_deleted: **bool** , 

// State at prepare version (affected by retreat / advance) prepare_state: **uint** , } 

**fn** space_in_prepare_state(item: **AugmentedCRDTItem** ) { 

**if** item.prepare_state == INSERTED { **return** 1 } **else** { **return** 0 } 

- } 

**fn** space_in_effect_state(item: **AugmentedCRDTItem** ) { 

**if** !item.ever_deleted { **return** 1 } **else** { **return** 0 } 

} 

- // We have an efficient algorithm for this in our code. See diff() in causal-graph.ts. **fn** diff(v1, v2) -> (only_in_v1, only_in_v2) { 

   - // This function considers the transitive expansion of the versions v1 and v2. // We return the set difference between the transitive expansions. **let** all_events_v1 = {set of all events **in** v1 + all events which happened-before any event **in** v1} **let** all_events_v2 = {set of all events **in** v2 + all events which happened-before any event **in** v2} **return** ( 

set_subtract(all_events_v1 - all_events_v2), set_subtract(all_events_v2 - all_events_v1) ) 

} 

**Listing 1.** Pseudocode for the Eg-walker algorithm (continued in Listing 2). 

19 

Joseph Gentle and Martin Kleppmann 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

**fn** generateDocument(events) { **let** cur_version = {} // Frontier version // List of AugmentedCRDTItems. This could equally be an RGA tree or some other data structure. **let** crdt = [] // Resulting document text **let** resulting_doc = "" // Some traversal obeying partial order relationship between events. **for** e **in** events.iter_in_causal_order() { // Step 1: Prepare **let** (a, b) = diff(cur_version, e.parent_version) **for** e **in** a { // Retreat **let** item = crdt.find_item_by_id(e.id) item.prepare_state -= 1 } **for** e **in** b { // Advance **let** item = crdt.find_item_by_id(e.id) item.prepare_state += 1 } // Step 2: Apply **if** e. **type** == Insert { // We find the insertion position in the crdt using the prepare_state variables. **let** ins_pos = idx_of(crdt, e.pos, PREPARE_STATE) // Then insert here using the underlying CRDT's rules. **let** origin_left = prev_item(ins_pos).id or START // Origin_right is the ID of the first item after ins_pos where prepare_state >= 1. **let** origin_right = next_item(crdt, ins_pos, item => item.prepare_state >= INSERTED).id or END // Use an existing CRDT to determine the order of concurrent insertions at the same position crdt_integrate(crdt, { id: **e** .id, origin_left, origin_right, ever_deleted: **false** , prepare_state: 1 }) **let** effect_pos = crdt[0..ins_pos].map(space_in_effect_state).sum() resulting_doc.splice_in(effect_pos, e.contents) } **else** { // Delete **let** idx = idx_of(crdt, e.pos, PREPARE_STATE) // But this time skip any items which aren't in the inserted state. **while** crdt[idx].prepare_state != INSERTED { idx += 1 } // Mark as deleted. crdt[idx].ever_deleted = **true** crdt[idx].prepare_state += 1 **let** effect_pos = crdt[0..idx].map(space_in_effect_state).sum() resulting_doc.delete_at(effect_pos) } cur_version = {e.id} } **return** resulting_doc } 

**Listing 2.** Continuation of the pseudocode in Listing 1. 

20 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

## **C Proof of Correctness** 

We now demonstrate that Eg-walker is correct by showing that it satisfies the _strong list specification_ proposed by Attiya et al. [8], a formal specification of collaborative text editing. Informally speaking, this specification requires that replicas converge to the same document state, that this state contains exactly those characters that were inserted but not deleted, and that inserted characters appear in the correct place relative to the characters that surrounded it at the time it was inserted. Assuming network partitions are eventually repaired, this is a stronger specification than _strong eventual consistency_ [53], which is a standard correctness criterion for CRDTs [26]. 

With a suitable algorithm for ordering concurrent insertions at the same position, Eg-walker is also able to achieve maximal non-interleaving [60], which is a further strengthening of the strong list specification. However, since that algorithm is out of scope of this paper, we also leave the proof of non-interleaving out of scope. 

### **C.1 Definitions** 

Let Char be the set of characters that can be inserted in a document. Let Op = { _Insert_ ( _𝑖,𝑐_ ) | _𝑖_ ∈ N ∧ _𝑐_ ∈ Char} ∪ { _Delete_ ( _𝑖_ ) | _𝑖_ ∈ N} be the set of possible operations. Let ID be the set of unique event identifiers, and let Evt = ID × P(ID) × Op be the set of possible events consisting of a unique ID, a set of parent event IDs, and an operation. When _𝑒_ ∈ _𝐺_ and _𝑒_ = ( _𝑖, 𝑝,𝑜_ ) we also use the notation _𝑒.id_ = _𝑖_ , _𝑒.parents_ = _𝑝_ , and _𝑒.op_ = _𝑜_ . 

### **Definition C.1.** An event graph _𝐺_ ⊆ Evt is _valid_ if: 

1. every event _𝑒_ ∈ _𝐺_ has an ID _𝑒.id_ that is unique in _𝐺_ ; 

2. for every event _𝑒_ ∈ _𝐺_ , every parent ID _𝑝_ ∈ _𝑒.parents_ is the ID of some other event in _𝐺_ ; 

3. the graph is acyclic, i.e. there is no subset of events { _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑛_ } ⊆ _𝐺_ such that _𝑒_ 1 is a parent of _𝑒_ 2, _𝑒_ 2 is a parent of _𝑒_ 3, ..., and _𝑒𝑛_ is a parent of _𝑒_ 1; and 

4. for every event _𝑒_ ∈ _𝐺_ , the index at which _𝑒.op_ inserts or deletes is an index that exists (is not beyond the end of the document) in the document version defined by the parents _𝑒.parents_ . 

Since event graphs grow monotonically and we never remove events, it is easy to ensure that the graph remains valid whenever a new event is added to it. 

Attiya et al. make a simplifying assumption that every insertion operation has a unique character. We use a slightly stronger version of the specification that avoids this assumption. We also simplify the specification by using our event graph definition instead of the original abstract execution definition (containing message broadcast/receive events and a visibility relation). These changes do not affect the substance of the proof: each node of our event graph corresponds to a _do_ event in the original strong list specification, and the 

transitive closure of our event graph is equivalent to the visibility relation. 

For a given event graph _𝐺_ we define a replay function replay( _𝐺_ ) as introduced in Section 2.4, based on the Egwalker algorithm. It iterates over the events in _𝐺_ in some topologically sorted order, transforming the operation in each event as described in Section 3, and then applying the transformed operation to the document state resulting from the operations applied so far (starting with the empty document). In a real implementation, replay returns the final document state as a concatenated sequence of characters. For the sake of this proof, we define replay to instead return a sequence of ( _id,𝑐_ ) pairs, where _id_ is the unique ID of the event that inserted the character _𝑐_ . This allows us to distinguish between different occurrences of the same character. The text of the document can be recovered by simply ignoring the _id_ of each pair and concatenating the characters. 

We can now state our modified definition of the strong list specification: 

**Definition C.2.** A collaborative text editing algorithm with a replay function replay( _𝐺_ ) satisfies the _strong list specification_ if for every valid event graph _𝐺_ ⊂ Evt there exists a relation _lo_ ⊂ ID × ID called the _list order_ , such that: 

1. For event _𝑒_ ∈ _𝐺_ , let _𝐺𝑒_ = { _𝑒_ } ∪ Events( _𝑒.parents_ ) be the set of all events that happened before _𝑒_ and _𝑒_ itself. Let _doc𝑒_ = replay( _𝐺𝑒_ ) = ⟨( _id_ 0 _,𝑐_ 0) _, . . . ,_ ( _id𝑛_ −1 _,𝑐𝑛_ −1)⟩ be the document state immediately after locally generating _𝑒_ , where _𝑐𝑖_ ∈ Char and _id𝑖_ ∈ ID. Then: 

a. _doc𝑒_ contains exactly the elements that have been inserted but not deleted in _𝐺𝑒_ : 

- (∃ _𝑖_ ∈[0 _,𝑛_ − 1] : _doc𝑒_ [ _𝑖_ ] = ( _id,𝑐_ )) ⇐⇒ 

- (∃ _𝑎_ ∈ _𝐺𝑒, 𝑗_ ∈ N : _𝑎.id_ = _id_ ∧ _𝑎.op_ = _Insert_ ( _𝑗,𝑐_ )) ∧ 

      - (� _𝑏_ ∈ _𝐺𝑒,𝑘_ ∈ N : _𝑏.op_ = _Delete_ ( _𝑘_ ) ∧ replay(Events( _𝑏.parents_ ))[ _𝑘_ ] = ( _id,𝑐_ )) _._ 

- b. The order of the elements in _doc𝑒_ is consistent with the list order: 

   - ∀ _𝑖, 𝑗_ ∈[0 _,𝑛_ − 1] : _𝑖 < 𝑗_ =⇒( _id𝑖, id 𝑗_ ) ∈ _lo._ 

- c. Elements are inserted at the specified position: 

   - ∀ _𝑖,𝑐_ : _𝑒.op_ = _Insert_ ( _𝑖,𝑐_ ) =⇒ _doc𝑒_ [ _𝑖_ ] = ( _𝑒.id,𝑐_ ) _._ 

2. The list order _lo_ is transitive, irreflexive, and total, and thus determines the order of all insert operations in the event graph. 

### **C.2 Proving Convergence** 

**Lemma C.3.** _Let 𝑒 be an event in a valid event graph such that 𝑒.op_ = _Delete_ ( _𝑖_ ) _. In the internal state immediately before applying 𝑒 (in which all events that happened before 𝑒 have been advanced and all others have been retreated), either the record that 𝑒 will update has 𝑠𝑝_ = Ins _, or it is part of a placeholder (which behaves like a sequence of 𝑠𝑝_ = Ins _records)._ 

21 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

_Proof._ If we had _𝑠𝑝_ = NotInsertedYet, that would imply that we retreated the insertion of the character deleted by _𝑒_ , which contradicts the fact that the insertion of a character must happen before any deletion of the same character. Furthermore, if we had _𝑠𝑝_ = Del _𝑘_ for some _𝑘_ , that would imply that an event that happened before _𝑒_ already deleted the same character, in which case it would not be possible to generate _𝑒_ . This leaves _𝑠𝑝_ = Ins or placeholder as the only options that do not result in a contradiction. □ 

**Lemma C.4.** _Let 𝑆_ 0 _be some internal Eg-walker state, and let 𝑎 and 𝑏 be two concurrent events. Let 𝑆_ 1 _be the internal state resulting from updating 𝑆_ 0 _with retreat and advance calls so that the prepare version of 𝑆_ 1 _equals the parents of 𝑏. Let 𝑆_ 2 _be the internal state resulting from first replaying 𝑎 on top of 𝑆_ 0 _, and then retreating and advancing so that the prepare version of 𝑆_ 2 _equals the parents of 𝑏. Then the only difference between 𝑆_ 1 _and 𝑆_ 2 _is in the record inserted or updated by 𝑎 (and possibly the split of a placeholder that this record falls within); the rest of 𝑆_ 1 _and 𝑆_ 2 _is the same._ 

_Proof._ Since _𝑆_ 0 is produced by Eg-walker, it contains records for all characters that have been inserted or deleted by events since the last critical version prior to _𝑎_ and _𝑏_ , it contains placeholders for any characters inserted but not deleted prior to that critical version, and it does not contain anything for characters that were deleted prior to that critical version. By the definition of critical version, any event _𝑒_ that is concurrent with _𝑎_ or _𝑏_ must be after the critical version, and therefore the record that is updated by _𝑒_ must exist in _𝑆_ 0. 

_𝑆_ 1 has the same record sequence and the same _𝑠𝑒_ in each record as _𝑆_ 0, since retreating and advancing do not change those things. The _𝑠𝑝_ values in _𝑆_ 1 are set so that every record inserted by an event that is concurrent with _𝑏_ has _𝑠𝑝_ = NotInsertedYet, every record whose insertion happened before _𝑏_ but which was not deleted before _𝑏_ has _𝑠𝑝_ = Ins, and every record that was deleted by _𝑘 >_ 0 separate events before _𝑏_ has _𝑠𝑝_ = Del _𝑘_ . To achieve this it is sufficient to consider events that happened after the last critical version. Thus, the _𝑠𝑝_ values in _𝑆_ 1 do not depend on the _𝑠𝑝_ values in _𝑆_ 0, and they do not depend on any events that are concurrent with _𝑏_ . 

Replaying _𝑎_ on top of _𝑆_ 0 involves first updating the _𝑠𝑝_ values to set the prepare version to the parents of _𝑎_ (which may differ from the parents of _𝑏_ ), and then applying _𝑎_ , which either inserts or updates a record in the internal state, and possibly splits a placeholder to accommodate this record. _𝑆_ 2 is then produced by updating all of the _𝑠𝑝_ values in the same way as for _𝑆_ 1. As these _𝑠𝑝_ values depend only on _𝑏.parents_ and not on _𝑎_ , _𝑆_ 2 is identical to _𝑆_ 1 except for the record inserted or updated by _𝑎_ . □ 

**Lemma C.5.** _Let 𝑎 and 𝑏 be two concurrent events such that 𝑎.op_ = _Insert_ ( _𝑖,𝑐𝑖_ ) _and 𝑏.op_ = _Insert_ ( _𝑗,𝑐 𝑗_ ) _. If we start with some internal state and document state and then replay 𝑎_ 

_followed by 𝑏, the resulting internal state and document state are the same as if we had replayed 𝑏 followed by 𝑎._ 

_Proof._ To replay _𝑎_ followed by _𝑏_ , we first retreat/advance so that the prepare state corresponds to _𝑎.parents_ , then apply _𝑎_ , then retreat _𝑎_ , then retreat/advance so that the prepare state corresponds to _𝑏.parents_ , then apply _𝑏_ . Applying _𝑎_ inserts a record into the internal state, and after retreating _𝑎_ this record has _𝑠𝑝_ = NotInsertedYet and _𝑠𝑒_ = Ins. Since _𝑏_ is concurrent to _𝑎_ , _𝑎_ cannot be a critical version, and therefore the internal state is not cleared after applying _𝑎_ . When _𝑏_ is applied, the presence of the record inserted by _𝑎_ is the only difference between the internal state when applying _𝑏_ after _𝑎_ compared to applying _𝑏_ without applying _𝑎_ first (by Lemma C.4). When determining the insertion position in the internal state for _𝑏_ ’s record based on _𝑏_ ’s index _𝑗_ , the record inserted by _𝑎_ does not count since it has _𝑠𝑝_ = NotInsertedYet. Therefore, _𝑏_ ’s record is inserted into the internal state at the same position relative to its neighbours, regardless of whether _𝑎_ has been applied previously. By similar argument the same holds for _𝑎_ ’s record. 

As explained in Section 3.3, the internal state uses a CRDT algorithm to place the records in the internal state in a consistent order, regardless of the order in which the events are applied. The details of that algorithm go beyond the scope of this paper. The key property of that algorithm is that the final sequence of internal state records is the same, regardless of whether we apply first _𝑎_ and then _𝑏_ , or vice versa. For example, if we first apply _𝑎_ then _𝑏_ , and if the final position of _𝑏_ ’s record in the internal state is after _𝑎_ ’s record, then the CRDT algorithm has to skip over _𝑎_ ’s record (and potentially other, concurrently inserted records) when determining the insertion position for _𝑏_ ’s record. This process never needs to skip over a placeholder, since placeholders represent characters that were inserted before the last critical version. It only ever needs to skip over records for insertions that are concurrent with _𝑎_ or _𝑏_ ; by the definition of critical versions, all such insertion events appear after the last critical version (and hence after the last internal state clearing) in the topological sort, and therefore they are represented by explicit internal state records, not placeholders. 

Now we consider the document state. WLOG assume that the record inserted by _𝑎_ appears at an earlier position in the internal state than the record inserted by _𝑏_ (regardless of the order of applying _𝑎_ and _𝑏_ ). Let _𝑖_<sup>′</sup> be the transformed index of _𝑎.op_ when _𝑎_ is applied first, and let _𝑗_<sup>′</sup> be the transformed index of _𝑏.op_ when _𝑏_ is applied first. 

Say we replay _𝑎_ before _𝑏_ . When computing the transformed index for _𝑏_ , the internal state record for _𝑎_ has _𝑠𝑝_ = NotInsertedYet, and hence it is not counted when mapping _𝑏.op_ ’s index _𝑗_ to _𝑏_ ’s internal state record. However, _𝑎_ ’s record _is_ counted when mapping _𝑏_ ’s internal state record back to an index, since _𝑎_ ’s record has _𝑠𝑒_ = Ins and it appears before _𝑏_ ’s record. Therefore the transformed index for _𝑏.op_ is 

22 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

_𝑗_<sup>′</sup> + 1 when applied after _𝑎_ . On the other hand, if we replay _𝑏_ before _𝑎_ , the record for _𝑏_ appears after the record for _𝑎_ in the internal state, so the transformed index for _𝑎_ is _𝑖_<sup>′</sup> , unaffected by _𝑏_ . Thus, we have the situation as shown in Figure 1, and the effect of the two insertions _𝑎_ and _𝑏_ on the document state is the same regardless of their order. □ 

**Lemma C.6.** _Let 𝑎 and 𝑏 be two concurrent events such that 𝑎.op_ = _Insert_ ( _𝑖,𝑐_ ) _and 𝑏.op_ = _Delete_ ( _𝑗_ ) _. If we start with some internal state and document state and then replay 𝑎 followed by 𝑏, the resulting internal state and document state are the same as if we had replayed 𝑏 followed by 𝑎._ 

_Proof._ Since _𝑎_ and _𝑏_ are concurrent, the character being deleted by _𝑏_ cannot be the character inserted by _𝑎_ . We therefore only need to consider two cases: (1) the record inserted by _𝑎_ has an earlier position in the internal state than the record updated by _𝑏_ ; or (2) vice versa. 

Case (1): If we replay _𝑎_ before _𝑏_ , we first apply _𝑎_ , then retreat _𝑎_ , then apply _𝑏_ (and also retreat/advance other events before applying, like in Lemma C.5). Applying _𝑎_ inserts a record into the internal state, and after retreating _𝑎_ this record has _𝑠𝑝_ = NotInsertedYet and _𝑠𝑒_ = Ins. When subsequently applying _𝑏_ we update an internal state record at a later position. The record inserted by _𝑎_ is not counted when mapping _𝑏_ ’s index to an internal record, but it is counted when mapping the internal record back to a transformed index, resulting in _𝑏_ ’s transformed index being one greater than it would have been without earlier applying _𝑎_ (by Lemma C.4). On the other hand, if we replay _𝑏_ before _𝑎_ , the record updated by _𝑏_ appears after _𝑎_ ’s record in the internal state, so the transformation of _𝑎_ is not affected by _𝑏_ . The transformed operations therefore converge. 

Case (2): If we replay _𝑏_ before _𝑎_ , we first apply _𝑏_ , then retreat _𝑏_ , then apply _𝑎_ (plus other retreats/advances). Applying _𝑏_ updates an existing record in the internal state (possibly splitting a placeholder in the process). Before applying _𝑏_ this record must have _𝑠𝑝_ = Ins (by Lemma C.3), and it can have either _𝑠𝑒_ = Ins (in which case, the transformed operation for _𝑏_ is _Delete_ ( _𝑗_<sup>′</sup> ) for some transformed index _𝑗_<sup>′</sup> ) or _𝑠𝑒_ = Del (in which case, _𝑏_ is transformed into a no-op). After applying and retreating _𝑏_ this record has _𝑠𝑝_ = Ins and _𝑠𝑒_ = Del in any case. We next apply _𝑎_ , which by assumption inserts a record into the internal state at a later position than _𝑏_ ’s record. If we had _𝑠𝑒_ = Del before applying _𝑏_ , the process of applying and retreating _𝑏_ did not change the internal state, so the transformed operation for _𝑎_ is the same as if _𝑏_ had not been applied, which is consistent with the fact that _𝑏_ was transformed into a no-op. If we had _𝑠𝑒_ = Ins before applying _𝑏_ , _𝑏_ ’s record is counted when mapping _𝑎_ ’s index to an internal record position, but not counted when mapping the internal record back to a transformed index, resulting in _𝑎_ ’s transformed index being one less than it would have been without earlier applying _𝑏_ (by Lemma C.4), as required given that _𝑏_ has deleted an earlier character. On the other hand, if we 

replay _𝑎_ before _𝑏_ , the record inserted by _𝑎_ appears after _𝑏_ ’s record in the internal state, so the transformation of _𝑏_ is not affected by _𝑎_ , and the transformed operations converge. □ 

**Lemma C.7.** _Let 𝑎 and 𝑏 be two concurrent events such that 𝑎.op_ = _Delete_ ( _𝑖_ ) _and 𝑏.op_ = _Delete_ ( _𝑗_ ) _. If we start with some internal state and document state and then replay 𝑎 followed by 𝑏, the resulting internal state and document state are the same as if we had replayed 𝑏 followed by 𝑎._ 

_Proof._ WLOG we need to consider two cases: (1) the record updated by _𝑎_ has an earlier position in the internal state than the record updated by _𝑏_ ; or (2) _𝑎_ and _𝑏_ update the same internal state record. The case where _𝑎_ ’s record has a later position than _𝑏_ ’s record is symmetric to (1). 

Case (1): We further consider two sub-cases: (1a) the record that _𝑎_ will update has _𝑠𝑒_ = Ins prior to applying _𝑎_ ; or (1b) the record has _𝑠𝑒_ = Del. 

Case (1a): Say we replay _𝑎_ before _𝑏_ . Before applying _𝑎_ , the record that _𝑎_ will update must have _𝑠𝑝_ = Ins (by Lemma C.3). After applying and retreating _𝑎_ , the record updated by _𝑎_ has _𝑠𝑝_ = Ins and _𝑠𝑒_ = Del, and the transformed operation for _𝑎_ is _Delete_ ( _𝑖_<sup>′</sup> ) for some transformed index _𝑖_<sup>′</sup> . We subsequently apply _𝑏_ , which by assumption updates an internal state record that is later than _𝑎_ ’s. _𝑎_ ’s record is therefore counted when mapping the index of _𝑏.op_ to an internal record position, but not counted when mapping the internal record back to a transformed index. If _𝑎_ had not been replayed previously, it would have been counted during both mappings (by Lemma C.4). Thus, if the record updated by _𝑏_ has _𝑠𝑒_ = Ins, the transformed operation for _𝑏_ is _Delete_ ( _𝑗_<sup>′</sup> −1), where _𝑗_<sup>′</sup> is the transformed index of _𝑏_ ’s operation if _𝑎_ had not been replayed previously, and _𝑗_<sup>′</sup> − 1 ≥ _𝑖_<sup>′</sup> , as required. If _𝑏_ ’s record previously has _𝑠𝑒_ = Del, it is transformed into a no-op. On the other hand, if we replay _𝑏_ before _𝑎_ , the record updated by _𝑏_ appears later than _𝑎_ ’s record in the internal state, so the transformation of _𝑎_ is not affected by _𝑏_ . 

Case (1b): Say we replay _𝑎_ before _𝑏_ . Before applying _𝑎_ , the record that _𝑎_ will update must have _𝑠𝑝_ = Ins (by Lemma C.3), and _𝑠𝑒_ = Del by assumption. After applying and retreating _𝑎_ , the record updated by _𝑎_ remains in the same state ( _𝑠𝑝_ = Ins, _𝑠𝑒_ = Del), and the transformed operation for _𝑎_ is a no-op. When we subsequently apply _𝑏_ , the transformed operation is therefore the same as if _𝑎_ had not been applied, as required. On the other hand, if we replay _𝑏_ before _𝑎_ , the record updated by _𝑏_ appears later than _𝑎_ ’s record in the internal state, so the transformation of _𝑎_ is not affected by _𝑏_ . 

Case (2): Before replaying both of the events, the record that both events update may have _𝑠𝑒_ = Ins or _𝑠𝑒_ = Del, but after applying the first event it definitely has _𝑠𝑒_ = Del. The second event will therefore be transformed into a no-op. The same happens regardless of whether _𝑎_ or _𝑏_ is replayed first, so the result does not depend on the order of replay of the two events. □ 

23 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Joseph Gentle and Martin Kleppmann 

**Lemma C.8.** _Given a valid event graph 𝐺,_ replay( _𝐺_ ) _is a deterministic function. In other words, any two replicas that have the same event graph converge to the same document state and the same internal state._ 

_Proof._ The algorithms to transform an operation and to apply a transformed operation to the document state are by definition deterministic. This leaves as the only source of nondeterminism the choice of topologically sorted order ( _𝐺_ is valid and hence acyclic, thus at least one such order exists, but there may be several topologically sorted orders if _𝐺_ contains concurrent events). We show that all sort orders result in the same final document state. 

Let _𝐸_ = ⟨ _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑛_ ⟩ and _𝐸_<sup>′</sup> = ⟨ _𝑒_ 1<sup>′</sup><sup>_,𝑒_</sup> 2<sup>′</sup><sup>_, . . . ,𝑒_</sup> _𝑛_<sup>′⟩betwo</sup> topological sort orders of _𝐺_ = { _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑛_ }. Then _𝐸_<sup>′</sup> must be a permutation of _𝐸_ . Both sequences are in some causal order, that is: if _𝑒𝑖_ → _𝑒 𝑗_ ( _𝑒𝑖_ happens before _𝑒 𝑗_ , as defined in Section 2.2), then _𝑒𝑖_ must appear before _𝑒 𝑗_ in both _𝐸_ and _𝐸_<sup>′</sup> . If _𝑒𝑖_ ∥ _𝑒 𝑗_ (they are concurrent), the events could appear in either order. Therefore, it is possible to transform _𝐸_ into _𝐸_<sup>′</sup> by repeatedly swapping two concurrent events that are adjacent in the sequence. We show that at each such swap we maintain the invariant that the document state and the internal state resulting from replaying the events in the order before the swap are equal to the states resulting from replaying the events in the order after the swap. Therefore, the document state and the internal state resulting from replaying _𝐸_ are equal to those resulting from _𝐸_<sup>′</sup> . 

Let ⟨ _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑖,𝑒𝑖_ +1 _, . . . ,𝑒𝑛_ ⟩ be the sequence of events prior to one of these swaps, and _𝑒𝑖_ , _𝑒𝑖_ +1 are the events to be swapped. Replaying the events in the prefix ⟨ _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑖_ −1⟩ is a deterministic algorithm resulting in some document state and some internal state. Next, we replay either _𝑒𝑖_ followed by _𝑒𝑖_ +1, or _𝑒𝑖_ +1 followed by _𝑒𝑖_ . Since _𝑒𝑖_ and _𝑒𝑖_ +1 are concurrent, it is not possible for only one of the two to be contained in a critical version, and therefore no state clearing will take place between applying these two events. If _𝑒𝑖_ and _𝑒𝑖_ +1 are both insertions, the resulting states in either order are the same by Lemma C.5. If one of _𝑒𝑖_ and _𝑒𝑖_ +1 is an insertion and the other is a deletion, we use Lemma C.6. If both _𝑒𝑖_ and _𝑒𝑖_ +1 are deletions, we use Lemma C.7. Finally, replaying the suffix ⟨ _𝑒𝑖_ +2 _, . . . ,𝑒𝑛_ ⟩ is a deterministic algorithm. This shows that concurrent operations commute. □ 

### **C.3 Satisfying the Strong List Specification** 

**Lemma C.9.** _Let 𝐺 be a valid event graph, let doc_ = replay( _𝐺_ ) _be the document state resulting from replaying 𝐺, and let 𝑆 be the internal state after replaying 𝐺. Then the 𝑖th element in doc corresponds to the 𝑖th record with 𝑠𝑒_ = Ins _in the internal state (counting placeholders as having 𝑠𝑒_ = Ins _, and not counting records with 𝑠𝑒_ = Del _). Moreover, the set of elements in doc is exactly the elements that have been inserted but not deleted in 𝐺:_ 







_Proof._ Let _𝐸_ = ⟨ _𝑒_ 1 _,𝑒_ 2 _, . . . ,𝑒𝑛_ ⟩ be some topological sort of _𝐺_ , and assume that we replay _𝐺_ in this order. By Lemma C.8 it does not matter which of the possible orders we choose. We then prove the thesis by induction over _𝑛_ , the number of events in _𝐺_ . The base case is trivial: _𝐺_ = {}, _doc_ = ⟨⟩, so there are no events, no records in the internal state, and no elements in the document state. 

Inductive step: Let _𝐸𝑘_ = ⟨ _𝑒_ 1 _,𝑒_ 2 _, ...,𝑒𝑘_ ⟩ with _𝑘 < 𝑛_ be a prefix of _𝐸_ . Since the set of events in _𝐸𝑘_ also forms a valid event graph, we can assume the inductive hypothesis, namely that replaying _𝐸𝑘_ results in a document corresponding to the records with _𝑠𝑒_ = Ins in the resulting internal state, and the document contains exactly those elements that have been inserted but not deleted by an operation in _𝐸𝑘_ . We now add _𝑒𝑘_ +1, the next event in the sequence _𝐸_ , to the replay. We do this by transforming _𝑒𝑘_ +1 using the internal state obtained by replaying _𝐸𝑘_ , and applying the transformed operation to the document state from _𝐸𝑘_ . We need to show that the invariant is still preserved in the following two cases: either (1) _𝑒𝑘_ +1 _.op_ = _Insert_ ( _𝑗,𝑐_ ) for some _𝑗_ , _𝑐_ , or (2) _𝑒𝑘_ +1 _.op_ = _Delete_ ( _𝑗_ ) for some _𝑗_ . We also have to consider the case where the internal state is cleared, but we begin with the case where no state clearing occurs. 

Case (1): The set of elements that have been inserted but not deleted grows by ( _𝑒𝑘_ +1 _.id,𝑐_ ) and otherwise stays unchanged. The transformation of an insertion operation is always another insertion operation. The document state is thus updated by inserting the same element ( _𝑒𝑘_ +1 _.id,𝑐_ ), and otherwise remains unchanged. Moreover, the transformed index of that insertion is computed by counting the number of internal state records with _𝑠𝑒_ = Ins that appear before the new record in the internal state, and the new record also has _𝑠𝑒_ = Ins, and the _𝑠𝑒_ property of no other record is updated, so the correspondence between internal state records and document state is preserved. 

Case (2): The element being deleted is located at index _𝑗_ in the document at the time _𝑒𝑘_ +1 was generated, which is replay(Events( _𝑒𝑘_ +1 _.parents_ )). We compute this element by retreating and advancing events until the prepare version equals _𝑒𝑘_ +1 _.parents_ , and then finding the _𝑗_ th (zero-indexed) record that has _𝑠𝑝_ = Ins in the internal state. The records with _𝑠𝑝_ = Ins are those that have been inserted but not deleted in events that happened before _𝑒𝑘_ +1, and thus the _𝑗_ th such record corresponds to replay(Events( _𝑒𝑘_ +1 _.parents_ ))[ _𝑗_ ]. Before applying _𝑒𝑘_ +1, this record may have either _𝑠𝑒_ = Ins or _𝑠𝑒_ = Del. If _𝑠𝑒_ = Ins, we update it to _𝑠𝑒_ = Del, and transform _𝑒𝑘_ +1 into a deletion whose index is the number of _𝑠𝑒_ = Ins to the left of the target record in the internal state; 

24 

EuroSys ’25, March 30-April 3, 2025, Rotterdam, Netherlands 

Collaborative Text Editing with Eg-walker: Better, Faster, Smaller 

by the inductive hypothesis, this is the correct document element to be deleted. If _𝑠𝑒_ = Del before applying _𝑒𝑘_ +1, that event is transformed into a no-op, since another operation in _𝐸𝑘_ has already deleted the element in question from the document state. In either case, we preserve the invariants of the induction. 

If _𝑒𝑘_ +1 is a critical version, we clear the internal state and replace it with a placeholder. By the definition of critical version, every event in _𝐸𝑘_ and _𝑒𝑘_ +1 happened before every event in the rest of _𝐸_ . Therefore, after retreating and advancing any event after _𝑒𝑘_ +1, any internal state record with _𝑠𝑒_ = Del will also have _𝑠𝑝_ = Del _𝑘_ for some _𝑘 >_ 0, and any internal state record with _𝑠𝑒_ = Ins will also have _𝑠𝑝_ = Ins unless it is deleted by an event after _𝑒𝑘_ +1. Since an internal state with _𝑠𝑒_ = Del can never move to state _𝑠𝑒_ = Ins, this means that any records with _𝑠𝑒_ = Del as of the critical version can be discarded, since they will never again be needed for transforming the index of an operation after _𝑒𝑘_ +1. Moreover, since all of the remaining records have _𝑠𝑒_ = _𝑠𝑝_ = Ins as of the critical version, and since the replay of the remaining events in _𝐸_ will never need to advance or retreat an event prior to the critical version, all of the records in the internal state can all be replaced by a single placeholder while still preserving the invariants of the induction. □ 

**Theorem C.10.** _The Eg-walker algorithm satisfies the strong list specification (Definition C.2)._ 

_Proof._ Given a valid event graph _𝐺_ , let replay( _𝐺_ ) be the replay function based on Eg-walker, as introduced earlier. We must show that there exists a list order _lo_ ⊂ ID × ID that satisfies the conditions given in Definition C.2. We claim that this list order corresponds exactly to the sequence of records and placeholders in the internal state after replaying the entire event graph _𝐺_ . By Lemma C.8, this internal state exists and is unique. This correspondence is more apparent if we assume a variant of Eg-walker that does not clear the internal state on critical versions, but we also claim that performing the optimisations in Section 3.5 preserves this property. 

conditions in Definition C.1, so it is also valid. Let _doc𝑒_ = replay( _𝐺𝑒_ ) = ⟨( _id_ 0 _,𝑐_ 0) _, . . . ,_ ( _id𝑛_ −1 _,𝑐𝑛_ −1)⟩ be the document state immediately after locally generating _𝑒_ . Since replay is deterministic (Lemma C.8), _doc𝑒_ exists and is unique. 

By Lemma C.9, _doc𝑒_ contains exactly the elements that have been inserted but not deleted in _𝐺𝑒_ , which is requirement (1a) of Definition C.2. Also by Lemma C.9, the _𝑖_ th element in _doc𝑒_ corresponds to the _𝑖_ th record with _𝑠𝑒_ = Ins in the internal state obtained by replaying _𝐺𝑒_ . Since any pair of IDs that are ordered by the internal state derived from _𝐺𝑒_ retain the same ordering in the internal state derived from _𝐺_ , we know that the ordering of elements in _doc𝑒_ is consistent with the list order _lo_ , satisfying requirement (1b) of Definition C.2. 

Finally, to demonstrate requirement (1c) of Definition C.2 we assume that _𝑒.op_ = _Insert_ ( _𝑖,𝑐_ ), and we need to show that _doc𝑒_ [ _𝑖_ ] = ( _𝑒.id,𝑐_ ). Since _𝐺𝑒_ contains only _𝑒_ and events that happened before _𝑒_ , but no events concurrent with _𝑒_ , we know that immediately before applying _𝑒_ , every record in the internal state will have _𝑠𝑝_ = Ins if and only if it has _𝑠𝑒_ = Ins (because there are no events that are reflected in the effect version but not in the prepare version _𝑒.parents_ ). Therefore, the set of records that are counted while mapping the original insertion index _𝑖_ to an internal state record equals the set of records that are counted while mapping the internal record back to a transformed index. Thus, the transformed index of the insertion is also _𝑖_ , and therefore the new element is inserted at index _𝑖_ of the document as required. This completes the proof that Eg-walker satisfies the strong list specification. □ 

To begin, note that the internal state is a totally ordered sequence of records, and that (aside from clearing the internal state) we only ever modify this sequence by inserting records or by updating the _𝑠𝑝_ and _𝑠𝑒_ properties of existing records. Thus, if a record with ID _id𝑖_ appears before a record with ID _id 𝑗_ at some point in the replay, the order of those IDs remains unchanged for the rest of the replay. We define the list order _lo_ to be the ordering relation among IDs in the internal state after replaying _𝐺_ using a Eg-walker variant that does not clear the internal state. This order exists, is unique (Lemma C.8), and is by definition transitive, irreflexive, and total, so it meets requirement (2) of Definition C.2. 

Let _𝑒_ ∈ _𝐺_ be any event in the graph, and let _𝐺𝑒_ = { _𝑒_ } ∪ Events( _𝑒.parents_ ) be the subset of _𝐺_ consisting of _𝑒_ and all events that happened before _𝑒_ . Note that _𝐺𝑒_ satisfies the 

25 

