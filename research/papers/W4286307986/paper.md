# An Efficient Approach to Move Elements in a Distributed Geo-Replicated Tree<sup>_∗_</sup> 

Parwat Singh Anjana<sup>_†_</sup> , Adithya Rajesh Chandrassery<sup>_‡_</sup> , and Sathya Peri<sup>_†_</sup> 

> _†_ Department of Computer Science and Engineering, Indian Institute of Technology, Hyderabad, India 

> _‡_ Department of Computer Science and Engineering, National Institute of Technology Karnataka, Surathkal, India cs17resch11004@iith.ac.in, adithyarajesh.191cs203@nitk.edu.in, sathya_p@cse.iith.ac.in 

### **Abstract** 

Replicated tree data structures are extensively used in collaborative applications and distributed file systems, where clients often perform move operations. Local move operations at different replicas may be safe. However, remote move operations may not be safe. When clients perform arbitrary move operations concurrently on different replicas, it could result in various bugs, making this operation challenging to implement. Previous work has revealed bugs such as data duplication and cycling in replicated trees. In this paper, we present an efficient algorithm to perform move operations on the distributed replicated tree while ensuring eventual consistency. The proposed technique is primarily concerned with resolving conflicts efficiently, requires no interaction between replicas, and works well with network partitions. We use the last write win semantics for conflict resolution based on globally unique timestamps of operations. The proposed solution requires only one compensation operation to avoid cycles being formed when move operations are applied. The proposed approach achieves an effective speedup of 68.19 _×_ over the state-of-the-art approach in a geo-replicated setting on Microsoft Azure standard instances at three different continents. 

### **Index Terms** 

Conflict-free Replicated Data Types, Eventual Consistency, Distributed File Systems, Replicated Tree 

## I. INTRODUCTION 

Modern distributed systems ensure high uptime and availability which is commonly achieved by replicating data onto multiple systems. When clients perform concurrent operations, data replication at different replicas may cause consistency issues. Various consistency models have been implemented in the literature to ensure the mutability of replicated data. These consistency models are classified into different classes based on the consistency guarantees they provide, such as strong consistency, eventual consistency [1], and causal consistency [2]. The mutation occurs instantly across replicas in the strong consistency model; this is the strongest condition in an ideal setting. However, replicas may diverge when the network is partitioned; consequently, strong consistency is not easy to achieve with network partitions without sacrificing availability. Further, strong consistency suffers from performance overhead due to high synchronization costs when the network is reliable [3]. 

Strong consistency is the strongest form of consistency for any replicated system; unfortunately, it comes with a considerable performance penalty. As a result, it may not be suitable for replicated systems that require high availability, scalability with concurrent updates and convergence guarantee to a consistent state. As a result, systems designed based on weaker consistency models such as eventual consistency have become popular [2], [4]. In the eventual consistency model, replicas may diverge for various reasons; however, they eventually converge to the same state if no new updates are performed at any replica [4]–[6]. 

Concurrent updates to various replicas make it very difficult to converge and has been extensively studied in the literature. Numerous approaches have been developed to overcome this problem in several ways. The most prevalent techniques are _operational transformation_ (OT) [7]–[9] and _conflict-free replicated data types_ (CRDTs) [10]–[12]. OT requires a centralized server and an active server connection to modify the replicated file collaboratively. In contrast, CRDTs do not require a centralized server and allow peer-to-peer editing. CRDTs have become an indispensable component of many modern distributed applications that guarantee some form of eventual consistency [13]. Clients update their replicas concurrently without coordination to provide high availability even when the network is partitioned. It allows users to operate locally with no lag, even if they are not connected to other replicas. The system eventually becomes consistent when a user synchronizes with other users and devices. 

Popular distributed file systems such as Dropbox and Google Drive optimistically replicate data using a replicated tree data model. Clients interactively operate on the tree to perform various operations, such as updating, renaming, moving, deleting, and adding new files or directories. An interior node in the tree represents a directory, while a leaf node represents a file. This distributed file system runs a daemon on the client’s machine that keeps track of changes by monitoring the designated directory [13], [14]. Clients can read and update files locally on their systems, which can then be synchronized with other replicas. Collaborative text editing and graphical editors are examples of distributed systems that often use the replicated tree data model. 

> _∗_ Author sequence follows lexical order of last names. The proposal of this paper has been accepted in CCGrid 2022. 

1 



<!-- Start of picture text -->
move ( a  to be<br>a child of  b )<br>move ( b  to be<br>a child of  a )<br>Network Communication<br><!-- End of picture text -->

**Fig. 1:** Difficulty with the move operation on a replicated tree: let us assume a tree structure _t_ rooted at _r_ , and two clients _c_ 1 and _c_ 2, concurrently operating on _t_ in their local replicas. Let say _c_ 1 move _(a to be a child of b)_ concurrently with client _c_ 2 moving _(b to be a child of a)_ in their local version of _t_ without any coordination. Later, when these replicas are synchronized by propagating local operations, it may produce a cycle _a ↔ b_ disconnected from the root _r_ . 

The clients can read and update the files offline on their local system, which can later be synchronized with other replicas. Moving nodes is a common operation in such tree-based collaborative applications. In the file system example, the move operation moves files or directories to a new location within the tree. In a collaborative text editor that stores data using an XML or JSON data model, changing a paragraph to bullet points generates a new list and bullet point node. It then moves the paragraph nodes under the bullet point node. Another example is a collaborative graphical editor (Figma [15]) where grouping two objects lead to adding a new node in the tree [13]. 

This paper focuses on the move operation in the replicated tree CRDT due to its usefulness. The _move operation_ moves a sub-tree within the tree. This operation is difficult to implement because concurrent operations by multiple clients may result in cycles; additionally, the tree structure may be broken [13], [14], [16]. Due to the concurrent operations, a concurrency control mechanism is required to ensure the data structure’s correctness. Further, ensuring correctness while providing low latency, high throughput, and maintaining high availability can be very challenging. 

An example in Figure 1 shows the difficulty associated with the move operation in the replicated setting. The tree structure is replicated on multiple systems. Different clients can perform concurrent operations, leading to various malformations in the tree, such as a cycle, duplication, and detachment from the parent node. Concurrent move operations on the same tree node cause the data duplication problem. Providing support for a concurrent move operation for the replicated tree that does not require continuous synchronization or centralized coordination is problematic because two operations that are individually safe at their local replicas, when combined, might produce a cycle. Prior works by Nair et al. [14] and Kleppmann et al. [13] have shown that Dropbox suffers from duplication, and Google Drive results in errors due to the formation of cycles. 

We present an efficient protocol to perform move operations while maintaining the distributed replicated tree structure and ensuring that replicas are eventually consistent. The proposed approach does not require cross-replica coordination and hence is highly available even when the network is partitioned. In case of conflicting operations, we follow the last write win approach based on timestamps computed using the Lamport clock [17], however hybrid logical clock (HLC) [18] can also be used. The proposed protocol requires one compensation operation to undo the last moved node that causes the cycle. Essentially, we address the conflict resolution problem of the replicated tree by minimizing the number of undo and redo operations required to resolve conflicts. 

The significant contributions are as follows: 

- We propose a novel move operation approach on the replicated tree that is coordination-free, computationally efficient, and offers low latency operation. The proposed approach supports optimistic replication, which allows replicas to temporarily diverge during updates but always converges to a consistent state in the absence of new updates (see §IV). 

- The proposed algorithm guarantees eventual consistency; correctness proofs are provided in §V to show the _convergence_ of the replicas and the maintenance of the tree structure. 

- The performance of the proposed approach is compared against the Kleppmann et al. [13]. The experiment results show that the proposed approach achieves an effective speedup of 68.19 _×_ over Kleppmann’s approach for the remote move operation (see §VI). 

2 

A brief overview of the related work aligned with the proposed approach is discussed in §II, while the system model is given in §III. §VII conclude with some future research directions. 

## II. RELATED WORK 

This section briefly discusses the related work that has been done in line with the proposed approach. 

Algorithms on replicated data structures are classified into two classes: operational transformation (OT) [7]–[9], [19] and conflict-free replicated data types (CRDTs) [10]–[12]. Many of the proposed approaches mainly support two operations: insert and delete. Furthermore, the approaches based on OT require a central server and an active network connection between the client and the central server. It means it does not work when the replica is offline. In comparison, CRDT mitigates this issue by allowing asynchronous peer-to-peer communication between replicas using optimistic replication. In the presence of faults to reduce operation response time and increase availability, the optimistic replication [20] allows replicas to diverge temporarily. 

The CRDTs are mainly categorized into two types: state-based [11] and operation-based [11], [21]. The former, alternatively referred to as convergent replicated data types, is more straightforward to design and implement. However, a significant disadvantage is that it requires the transmission of the entire state to every other replica. On the other hand, later, also referred to as commutative replicated data types, transmit only update operations to each replica, thus requiring less network bandwidth. Nonetheless, it is built on the assumption of a reliable communication network, which means that no operations are dropped or duplicated. The data structures supported by these CRDTs are counters, lists, registers, sets, graphs, trees, etc. The proposed protocol implements efficient move operation on operation-based tree CRDT. 

Extensive research has been done to implement geo-replicated distributed tree data structures, which are used in a variety of distributed applications, including Google Drive, Dropbox (file systems), Google Docs, Apple’s Notes App (collaborative text editing), and Figma (collaborative graphical editor). For replicated tree CRDTs, numerous algorithms have been proposed. Several of these algorithms support only insert and delete operations and have a long response time or latency. 

Martin et al. [22] proposed tree CRDT for XML data to support insert and delete operation. While supporting these two operations in JSON data format is proposed by Kleppmann et al. [23]. Insert and delete operations can be used to implement a move operation; however, this could lead to the data duplication problem and increase the number of computation steps. A replicated file system using tree CRDT is implemented in [24], [25]. These solutions result in data duplication (tree node duplication). When replicas perform operations concurrently, data duplication occurs, resulting in irreversible divergence between replicas or the need for manual intervention to restore replicas to a consistent state. 

Data duplication is a severe issue, and for large systems, manually handling this problem is very difficult. On the other hand, some techniques require extensive metadata exchanges between replicas to mitigate these issues, which increases network bandwidth requirements. The solution proposed in [26] results in a directed acyclic graph on concurrent moves. The approach proposed in [14] requires causal delivery of operations that may not be possible when a replica crash fails, or the network is unreliable. 

As discussed earlier in §I, move operations are difficult to implement because two concurrent moves can produce a cycle and separate the node from its parent or ancestor. Moving a node in its descendent tree may produce a cycle and break the tree structure. Local move operations on a replica may or may not result in cycles. However, remote move operations may cause cycles that must be handled properly to preserve consistency. Hence, an efficient approach must be proposed to move the nodes and their subtrees to another location in the replicated tree. 

The most recent work is proposed by Kleppmann et al. [13]. This approach is computation-intensive, requires many compensation operations to avoid the cycles in the replicated tree, and relies on making a total global ordering of operations to ensure strong eventual consistency. In Kleppmann’s approach, before applying any remote operation (operation received from remote replica), they first undo all operations applied with a higher timestamp than the received operation, then apply the received operation, finally, redo all those undone operations. They maintain total global order between the operations and ensure strong eventual consistency. Unlike their approach, which requires multiple undo and redo operations per remote move operation, the proposed approach requires only one undo and compensation operation per conflicting operation and avoids multiple undo and redo operations for non-conflicting move operations. 

The proposed approach ensures eventual consistency. It avoids re-computation for non-conflicting changes to the tree by identifying which changes might cause problems to arise. In our approach for a remote operation that creates a problem (cycle), we undo one operation and send that as a compensation operation to all other replicas. By doing this, we save the time of re-computation for non-conflicting operations, as well many operations that need to be undone and redone can be avoided. Essentially, the number of compensation operations is just 1 per cycle and 0 for safe operations. We observed that such a simple approach improves the performances significantly. Additionally, there is no data duplication in the proposed approach and does not result in directed acyclic graph on concurrent move operation that may lead to inconsistency or divergence between the replicas. The additional metadata, causal delivery, and strict global total order while applying the operations for consistency are not required. So, we propose a novel coordination-free efficient move operation on replicated tree CRDT to support low latency and high availability operations. 

3 

## III. SYSTEM MODEL 

Following system model in [13], there are _n replicas_ ( _r_ 1 _, ..., rn_ ) communicate with each other in a completely asynchronous in a peer-to-peer fashion. We assume that replicas can go offline, crash, or fail unexpectedly. Each replica is associated with a _client_ . Each client performs operations on their local replicas. Each operation is then communicated to all other replicas asynchronously via messages. A message may suffer an arbitrary network delay or be delivered out of order. Clients can read and update data on their local replica even when the network is partitioned or their replica is offline. 

We consider a replicated tree structure _t_ rooted at _root_ to which clients add new nodes, delete nodes and move the nodes to the new location within the tree. In a file system, an internal node of the tree represents directories, while a leaf node represents files. In collaborative text editing, different sections, paragraphs, sentences, words, etc., in the document can be represented as tree nodes. 

We propose an efficient algorithm to maintain the replicated tree structure. The proposed algorithm is executed on each replica _ri_ without any distributed shared memory to operate on tree _t_ . Clients generate the operations, apply them on their local replica, and communicate them asynchronously via the network to all other replicas. On receiving an operation, remote replicas apply them using the same algorithm. The proposed algorithm supports three operations on the tree: 

- _Inserting_ a new node in the tree. 

- _Deleting_ a node from the tree. 

- _Moving_ a node along with a sub-tree to a child of a new parent in the tree. 

All three operations can be implemented as a move operation. The _move_ operation is a tuple consisting of timestamp ‘ts’, node ‘n’, and new parent ‘p’, i.e., _move⟨ts, n, p⟩_ . The timestamp ‘ts’ is unique and generated using Lamport timestamps [17], the node ‘n’ is the tree node being moved, and the parent ‘p’ is the location of the tree node to which it will be moved. We represent node timestamp as ‘node.ts’, and operation timestamp as ‘o.ts’. 

For example, _movex⟨tsi, nj, pk⟩_ means that a node with id _nj_ is moved as a child of a parent _pk_ in the tree _t_ at time _tsi_ in replica _rx_ . The additional information about the old parents of the node being moved is also logged in the _present_ _<u>log</u>_ used to undo the cyclic operations when cycles are formed due to the move of the _nj_ by the clients at different replicas. The move operation removes _nj_ from the current parent and moves it under the new parent _pk_ along with the sub-tree of _nj_ ; however, if _nj_ does not exist in the tree, a new node with _nj_ is created as a child of _pk_ . 

We implement _insert_ and _delete_ operations as a _move_ operation. _Insert_ is implemented as a move operation, where the node being moved ( _nj_ in the above example) does not already exist in the tree. For _delete_ , we use a special node called _trash−_ a child of root and the parent of all deleted nodes. When a delete on a node _nj_ is invoked, it is moved to the sub-tree of _trash_ . We explain further details in §IV. 

The proposed algorithm satisfies the consistency property _convergence−_ two replicas are said to converge or be in the same state if both of them have seen and applied the same set of operations. The replicas may apply the operations in any order due to reordering of messages and delays. This implies operations must be commutative. The formal proof is provided in §V. 

## IV. PROPOSED ALGORITHM 

This section describes the proposed algorithm for performing efficient move operations on the replicated tree. Each replica is modeled as a state machine that transitions from one state to the next by performing an operation. There is no shared memory between replicas, and the algorithm operates autonomously. The proposed protocol requires no central server or consensus mechanism for replica coordination, requires minimal metadata, and satisfies _eventual consistency_ . A client generates and applies operations locally with Algorithm 5, then sends them asynchronously over the network to all other replicas with Algorithm 2. We have described the main idea while all the details are explained as pseudocode in the various algorithms. 

The proposed algorithm supports _insert, delete_ and _move_ operations on a replicated tree. It can be shown that inserting and deleting involve changing various nodes’ parents. _insert_ can be viewed as the creation of a new node that is to be moved to be the child of a specified parent. For a _delete_ operation, the node is moved to be a child of a special node denoted as the _trash_ . Thus, the move operation can be used to implement the other two operations. Hence, in this discussion, we only consider an efficient way of moving nodes. 

Each move operation takes as arguments: the node to move and the new parent. Further, each tree node also maintains the timestamp (ts) of the last operation applied which is passed to the move operation. A move operation is formally defined as: _move⟨ts, n, p⟩_ . Here _ts_ is the timestamp of the move operation, _n_ is the node to be moved and made as a child of _p_ . The data structures are shown in Listing 1. 

Algorithm 1 is used to initialize the system. A user generates operations and applies them using Algorithm 5 locally, and then sends them asynchronously over the network to all other replicas using Algorithm 2. An operation (local or remote) is applied using Algorithm 5 and Algorithm 6. Algorithm 6 first compares the operation timestamp (i.e., _o.ts_ ) with the timestamp of the node to be moved (i.e., _node.ts_ ). It applies the operation only if the _o.ts_ is greater than the _node.ts_ . We prove convergence by showing that all the tree nodes across the replicas will get attached to the parent with the latest _ts_ . Next, we explain how the cycle is prevented in the proposed approach. 

4 

**Listing 1:** Data Structures 

**1** move { **2** clock ts; //Unique timestamp using Lamport clock. **3 int** n; //Tree node being moved. **4 int** p; //New parent. **5** }; **6** treeNode { **7 int** id; // Unique id of the tree node. **8** clock ts; //Timestamp of the last operation applied on the node. **9 int** parent; //Parent node id. **10** }; **11** lc_time - Lamport timestamp of a replica. **12** root - Original starting point of the tree. **13** present_log - Stores m unique previous parents of each node in the adjacency list form. **14** ts - Timestamp. **15** ch[] - Array of channels (size equal to the number of replicas). 

## **Algorithm 1:** _init_ <u>():</u> Initialize the system and create threads. 

**1 Procedure** _init_ () **:** 

// Create number of thread equals to the number of replicas, these thread call send function and in loop. **2** lc <u>time</u> _←_ 0; // For time stamping **3** _present_ _<u>log</u> ←{}_ ; // Adjacency list to store previous parents. **4 for** _i = 0 to numReplicas_ **do** // Create ‘numReplicas’ threads to send local operations to remote replicas. **5** thread(send, ch, i); // Create one thread to generate and apply local operations. **6** thread(localGenerator); // Create ‘numReplicas’ receiver threads to receive and apply remote operations. **7 for** _i = 0 to numReplicas_ **do 8** thread(applyRemote, j); 

## **Algorithm 2:** send(channel ch, i): send local operations to other replicas. 

**16 Procedure** send( _channel ch, i_ ) **: 17 while** _true_ **do 18** move _⟨_ ts, n, p _⟩←_ ch.get(i); **19 if** _ts == -1_ **then 20** break; // thread will join when this condition becomes true. **21** RPC.send(move _⟨_ ts, n, p _⟩_ ); 

## **Algorithm 3:** checkCycle( _ni_ , _nj_ ): detect cycle between two nodes in the tree ‘t’. 

**22 Procedure** checkCycle( _ni, nj_ ) **: 23 while** _nj̸_ = root **do 24 if** _nj == ni_ **then 25 return** _TRUE_ ; **26** _nj ←_ get <u>node(</u> _root_ , _nj.parent_ ); **27 return** _FALSE_ ; 

5 

|**Al**|**gorithm 4:** findLast(_ni, nj_): find node with highest timestamp in cycle.|
|---|---|
|**28 P**|**rocedure** findLast(_ni, nj_)**:**<br>|
|**29**|maxTS _←ni.ts_;|
|**30**|undoNode _←ni_;|
|**31**|**while** _nj̸_ = _ni_ **do**|
|**32**|**if** _nj.ts > maxTS_ **then**|
|**33**|undoNode _←nj_;|
|**34**|maxTS _←nj.ts_;|
|**35**|_nj ←_get<br>node(_root_, _nj.parent_);|
|**36**<br>**Al**|**return** undoNode;<br>**gorithm 5:** applyLocal(_n_, _p_): apply local operations and send them to other replicas.|
|**37 P**|**rocedure** applyLocal(_n, p_)**:**|
|**38**|_noden ←_get<br>node(_root_, n); // Gets reference of the node with id _n_ in _t_.|
|**39**|_nodep ←_get<br>node(_root_, p); // Gets reference of the node with id _p_ in _t_.|
|**40**|Lock();// Get lock, so that at a time only one operation will be applied by<br>threads (local thread or receiver thread) on the tree ‘t’.|
|**41**|ts _←_++lc<br>time;<br>// _checkCycle_(): checks for the cycle and returns _true_ if cycle found.|
|**42**|**if** _!checkCycle(noden, nodep)_ **then**|
|**43**|_present_<br>_log_[_noden_.id].add(_noden_.parent); // Update the current parent of _noden_ in the<br>_present_log_.|
|**44**|_noden_.parent _←nodep_.id;|
|**45**|_noden_.ts _←_ts;|
|**46**|Unlock();|
||// Send move operation to other replicas.|
|**47**|**for** _j = 0 to numReplicas_ **do**|
|**48**|ch[j].add(move_⟨_ts, n, p_⟩_);|



**Preventing Cycles:** Recall from §I that a cycle is formed when an ancestor tree node becomes a child of its descendant tree node. Preventing cycles is difficult because concurrent move operations on different replicas may be safe independently. However, a cycle may be formed when move operations from different replicas are merged. To avoid cycles, the proposed algorithm uses timestamps and compensation operations. We check for cycles prior to performing any operation by determining whether the node to be moved is an ancestor of the new parent. We check for each operation to avoid the formation of a cycle and maintain the tree structure during concurrent moves. Another check identifies the node with the latest timestamp when a cycle is detected (using Algorithm 3). As a result, the node with the most recent timestamp is returned to its previous parent, which is safe. A previous parent is said to be safe if it is not in the sub-tree of the node to be moved in the move operation. 

Algorithm 5 and Algorithm 6 ensure that all operations applied will not form a cycle. Algorithm 5 applies the local move operations, while Algorithm 6 applies remote move operations.<sup>1</sup> The procedure followed to apply a remote move operation by Algorithm 6 is explained here. Before applying a move operation (i.e., _move⟨ts, n, p⟩_ ) Algorithm 6 checks if the operation’s timestamp is greater than the previous move operation’s timestamp applied on _noden_ at Line 57. If the timestamp of the operation to be applied is smaller, it ignores the operation at Line 58. The following steps occur when a cycle is detected. The algorithm finds the node with the highest timestamp in the cycle and assigns it to _undoNode_ at Line 62. Then find a safe previous parent for the _undoNode_ in the while loop from Line 63. If there are no more previous parents left, then keep the previous parent as a _conflict node_ at Line 65. Then move the _undoNode_ to be a child of safe previous parent _undoParent_ using Algorithm 5 at Line 70. This internally updates the Lamport timestamp, which will be used for the undo operation at Line 41. Then the operation is applied in the local replica at Line 42 and Line 45. After that, it send the compensation operation to other replicas at Line 72. If _undoNode_ is the same as _noden_ at Line 73 then return at Line 75. Since it already applied an operation with a higher timestamp on _noden_ as the undo operation. Otherwise, apply the operation to change the parent of _noden_ at Lines 76 and 77. 

> 1We implemented separate processes for performing local and remote move operations on a replica. We employ the Lock() and Unlock() methods in Algorithm 5 at Line 40, 46, and at Line 56, 78 in Algorithm 6 to synchronize these processes. 

6 

## **Algorithm 6:** applyRemote(): receives and applies remote move operations. 

|**49 Pro**|**cedure** applyRemote()**:**<br>|
|---|---|
|**50**|**while** _true_ **do**|
|**51**|move_⟨_ts, n, p_⟩←_Stream.Receive(); // Receive remote move operation. If it returns<br>_End_, receiver threads will stop.|
|**52**|**if** _End_ **then**<br>|
|**53**|break;|
|**54**|_noden ←_get<br>node(_root_, n); // Gives reference of the node with id _n_ in _t_.|
|**55**|_nodep ←_get<br>node(_root_, p); // Gives reference of the node with id _p_ in _t_.|
|**56**|Lock(); // Get lock, so that at a time only one operation will be applied by<br>threads (local thread or receiver thread) on the tree ‘t’.|
||// Already applied an operation with higher timestamp on n node then ignore<br>the received operation with smaller timestamp since node timestamp will<br>be higher.|
|**57**<br>**58**|**if** _ts < noden.ts_ **then**<br>**return**;|
|**59**|lc<br>time _←_max(ts, lc<br>time);|
|**60**|_present_<br>_log_[_noden_.id].add(_noden_.parent); // Update the current parent of _noden_ in the<br>_present_log_.<br>// _checkCycle_(): checks for the cycle and returns _true_ if cycle found.|
|**61**|**if** _checkCycle(noden, nodep)_ **then**|
||// Find the node between _noden_ - _nodep_ with highest timestamp.|
|**62**|_undoNode ←_findLast(_noden_, _nodep_);<br>// Undo (move back) to a previous parent, not in the sub-tree of _noden_.|
||Keep searching till it gets a suitable node to undo; if not found safe|
||previous parent, then move undoNode under _conflict node_.|
|**63**|**while** _true_ **do**|
|**64**<br>**65**|**if** _present_<br>_log[undoNode.id] == NULL_ **then**<br>_undoParent ←conflict node_;|
|**66**|**else**|
||// Get and delete the previous parent from the _present_log_ for<br>undoNode.|
|**67**|_undoParent ←present_<br>_log_[_undoNode_.id].pop();<br>// Check if a cycle exists between _n_ and _undoParent_; if no cycle, then<br>found a safe node to move back that breaks the cycle between _n_ and<br>_p_|
|**68**<br>**69**|.<br>**if** _!checkCycle(noden, undoParent)_ **then**<br>break;|
|**70**|applyLocal(_undoNode_, _undoParent_);|
||// Send the compensation operation to other replicas|
|**71**|**for**_j=0tonumReplicas_**do**|
|**72**|<br>ch[j].add(move_⟨_ts, n, p_⟩_);|
||// Already applied a higher timestamp operation.|
|**73**|**if** _undoNode == noden_ **then**|
|**74**|Unlock();|
|**75**|**return**;|
|**76**|_noden_.parent _←nodep_.id;|
|**77**|_noden_.ts _←_ts;|
|**78**|Unlock();|



7 



<!-- Start of picture text -->
Network Communication<br><!-- End of picture text -->

**Fig. 2:** Preventing cycles in proposed approach. 



<!-- Start of picture text -->
move a  to be a child of x ,  ts:9<br>move a  to be a child of x ,  ts:9<br><!-- End of picture text -->



<!-- Start of picture text -->
move a  to be a child of x ,  ts:9<br><!-- End of picture text -->

**Fig. 3:** Remote move operation handling at a replica. 

8 

**Working Examples:** From Figure 1, let us assume that initially, both clients (replicas) consist of the same tree with timestamp as shown in Figure 2 (sub-figure (i) and (ii)). Each operation is assigned unique timestamp. Client _c_ 1 generates and applies the local operation _move_ 1 _⟨ts:6, n:a, p:b⟩_ , i.e., _move a to be a child of b_ with timestamp _move_ 1 _.ts_ : 6 as shown in (iii). Similarly, client _c_ 2 generates and applies the local operation _move_ 2 _⟨7, b, a⟩_ , i.e., _move b to be a child of a_ with timestamp _move_ 2 _.ts_ : 7 as shown in (iv). As shown in Figure 2 (v) when client _c_ 1 receives the _move_ 2 _⟨7, b, a⟩_ operation from _c_ 2, it executes the following steps: 

- 1) Operation timestamp ( _move_ 2 _.ts_ : 7) is not less then move node timestamp ( _b.ts_ : 2). 

- 2) Change move node timestamp to operation timestamp, i.e., _b.ts_ : 7. 

- 3) Cycle is detected involving ( _a_ , _b_ ). 

- 4) The node in the cycle with the highest timestamp is _b_ that is found using Algorithm 4. 

- 5) The node _b_ is moved to its previous parent which is _r_ . 

- 6) The compensation operation is propagated to other replicas to ensure that every replica has seen and applied the same set of operations. 

- 7) Since _noden_ (i.e., node received in move operation) is same as node to be moved back, so algorithm returns. 

Similar steps are followed when operation _move_ 1 _⟨6, a, b⟩_ from _c_ 1 is received at _c_ 2 (see Figure 2 (vi)). Except for case (7), where _noden_ is not the same _undoNode_ . Operation is applied on node _b_ . In summary, node _b_ is moved back to its previous parent _r_ using information stored in the _present_ _<u>log</u>_ , then received operation is applied, i.e., _a_ is moved as a child of _b_ , and the _present_ _<u>log</u>_ is updated. 

Note that when a cycle is detected, the algorithm identifies the node with the latest timestamp and move that node back (e.g., _b_ in Figure 2 (vi)) to the previous parent, where it is safe. In the Algorithm 6: Line 63 _−_ Line 69 tries to identify the previous parent of the node (e.g., _b_ in Figure 2 (vi)) where it can be moved back safely, and cycle can be broken. We are storing ‘m’ (a constant number) previous parent for tree node in an adjacency list in the _present log_ . 

Let us consider another example, as shown in Figure 3 (i), when a replica (i.e., _Client ci_ ) receive an operation to _move a to be a child of x_ . As shown in Figure 3 (ii), since _a_ is an ancestor of _x_ it will be detected that this operation can form a cycle. So algorithm finds the node with the highest timestamp in the potential cycle between _a_ and _x_ . Here, _y_ has the highest timestamp; therefore, _y_ can be moved to one of its previous parents to break the cycle. The algorithm, checks if any of its previous parents are safe (i.e., the previous parent is not a descendant of _a_ ). The check fails for _n_ and _z_ but _c_ is a safe previous parent. Hence move _y_ to be a child of _c_ , as shown in Figure 3 (iii). As a result of this, _x_ is no longer a descendant of _a_ . So apply the original operation to move _a_ to be a child of _x_ . 

Storing fixed ‘m’ previous parents for each node will be adequate by considering storage; moreover, increasing the value of ‘m’ increases the search time. For current experiments, ‘m’ is fixed to 5. Identifying the optimal number of previous parents (i.e., ‘m’) is left as future work. If previous parents in _present log_ are too small for the node to be moved to break the cycle, it is moved under a special node known as the _conflict node_ (a child of the root) to break the cycle. The _conflict node_ is special node that cannot be moved, it ensures that it will always be free from cycles. In case if the number of previous parents (or ‘m’) for a node to be moved is deleted (by moving under _trash_ ), we still can move the node under the previous parent (deleted node in this case). Next, the clients have the choice to change location again as the nodes attached to _trash_ have not been deleted permanently. Even when all previous parents are permanently deleted or _present logx_ for a node _x_ is empty, we can still move that node under _conflict node_ to break the cycle. 

The following important question is, how do we know which tree nodes are part of the cycle? As previously mentioned, the cycle is formed when the node moves to an ancestor of its new parent. Hence the cycle will lie between the new parent and the node to be moved. Since operations are applied one by one, an operation can almost form only one cycle. When a user sees nodes attached to _conflict node_ , they understand it resulted in the formation of a cycle and had to be resolved. A valid question could be why not just prevent the last operation for each replica that results in the cycle instead of looking for the global last operation. It is possible that since operations are applied across replicas in different orders, they will result in each replica moving a different tree node to _conflict node_ / _previous parent_ . This means that a large number of operations could be ignored. However, by looking for the global last operation, only the effect of one operation gets ignored. 

**Globally Unique Timestamps:** We use Lamport clock [17] on each replica for timestamping operations. However, this alone will not make it globally unique. Hence, we use the _replica id_ for tiebreakers when the Lamport timestamps are equal; together, they globally unique. As an alternative to the Lamport clock, the hybrid logical clock [18] can be used that provides the unique timestamps. 

**Difficulties:** _Trash_ can grow indefinitely. There can be a permanent _delete_ that recursively deletes from the leaf nodes to maintain consistency. Similar to the _rm -r_ command in Linux. However, this can lead to the case where a permanently deleted node is in the log of previous parents of other nodes. We will have to skip that parent and continue to the next previous parent in such cases. If none of the previous parents are safe or are permanently deleted, we move the node to be the child of _conflict node_ . As we are storing only the last _m_ previous parents and then moving to _conflict node_ , we are missing out 

9 

on moving to the older positions if none are safe. A sound argument can be made that instead of moving a node to ancient locations, it is better to move to the _conflict node_ to notify the user that the _move_ operation on the following node was unsafe. Users can find another suitable location to move it to. Implementing permanent delete is left as future work. 

Another important point is that, in the case of a cycle due to remote operations, the proposed approach requires propagating the undo operation to other replicas (requires one undo operation per remote move operation) that may be an additional message cost. However, it decreases many undo and redo operations to just one compensation operation at each replica, compensating for additional message costs. To summarize, we provide a replicated tree that can support efficient and highly available move operation. 

## V. PROOF OF CORRECTNESS 

This section provides the formal proof that all the replicas eventually converge to the same tree (state) and maintain the tree structure through optimistic replication. All our operations ( _insert, delete, move_ ) are just changing the parent to which the node is attached. 

_Lemma 1 (Duplication): A tree node will never be located at multiple different positions._ 

**Proofsketch.** For every node, we only store a single value for its parent. It must have more than one parent to be duplicated at multiple positions. However, that is not possible in our approach since we only store a single parent based on the last written win semantic. 

_Lemma 2 (Cycle): No operation will result in the formation of a cycle._ 

**Proofsketch.** Before applying any operation, our algorithm tries to detect if it will form a cycle. Assume we have an operation of the form _⟨ts, n, p⟩_ where _ts_ is a timestamp, _n_ is the tree node to be moved, _p_ is the parent. As previously stated, the operation will only form a cycle if _n_ is an ancestor of _p_ . 

We traverse all the way up from _p_ to _root_ , and in case we do not find _n_ in that path, it implies _n_ is not an ancestor of _p_ . Hence the operation is safe to apply and will not form a cycle. If we find _n_ is an ancestor of _p_ , we will apply an alternate compensation operation. Hence we never apply an operation where _n_ is the ancestor of _p_ . 

So now, for a cycle to exist, it needs to be formed from an operation where _n_ is not the ancestor of _p_ . How to prove that if _n_ is not an ancestor, it will not form a cycle? 

If _n_ and _p_ need to form a cycle, there should be a path from _n_ to _p_ and _p_ to n. However, applying the operation will only create a path from _n_ to _p_ . The path from _p_ to _n_ needs to exist before, and such a path will only happen if _n_ is an ancestor of _p_ . 

_Lemma 3 (Forest): The tree will never be split into multiple forests._ 

**Proofsketch.** We always maintain a parent for every tree node other than _root_ , and we do not allow operations that have the same parent and tree node value. The tree could be split into forests if there is some node without a parent or a cycle. We have already shown that cycles cannot be formed, and our algorithms always maintain a parent for each tree node other than _root_ . 

_Theorem 4 (Safe): A previous parent is said to be safe if it is not in the sub-tree of the node to be moved in the move operation._ 

**Proofsketch.** Since the node with the highest timestamp in the cycle is moved back to the previous parent, i.e., the previous parent must not be in the sub-tree of the node to be moved in a move operation, moreover, when there is no previous parent such that it is not in the sub-tree, then _conflict node_ (special child of the _root_ that can not be moved) is assigned as the previous parent. So one of the nodes in the cycle is always moved back to a node which is not in the sub-tree of the node to moved. As a result of this the new parent of the node to be moved will no longer be in its sub-tree. So there is no ancestor-descendant relation between the node to be moved and the new parent and the operation is safe to be applied. 

Having explained the lemmas and theorem, we now explain the main theorem. 

_Theorem 5 (Convergence): All the replicas that have seen and applied the same set of operations will converge to the same tree._ 

**Proofsketch.** Say two replicas _r_ 1 and _r_ 2 have seen the same set of operations. They will have the same parent for each node as the operation with the latest timestamp taken as the parent. 

From Lemmas 1, 2, 3, and Theorem 4, we get that the tree structure is maintained and there will be no cycles in the tree. Next, assume that replicas _r_ 1 and _r_ 2 have seen the same set of updates but have different parents for a key ( _k_ ). Suppose the replica _r_ 1 for _k_ has timestamp _ts_ 1 and parent _p_ 1. The replica _r_ 2 for _k_ has timestamp _ts_ 2 and parent _p_ 2. We know _ts_ 1 _̸_ = _ts_ 2 since we are using globally unique timestamps (ties are broken by replica id), and if they were equal, then parents would 

10 

**TABLE I:** Network latency (ms) between different replicas 

||**US East**|**West Europe**|**Southeast Asia**|
|---|---|---|---|
|**US East**|0|41|111|
|**West Europe**|41|0|79|
|**Southeast Asia**|111|79|0|



have been the same. This implies that either _ts_ 1 _< ts_ 2 or _ts_ 1 _> ts_ 2. This means that one of the replicas has not applied the latest timestamp. However, according to correctness of our algorithm, it was supposed to do that. Hence this is not possible. It means the initial assumption was wrong that the replicas have different parents for the same key or have seen the same set of updates. 

## VI. PERFORMANCE EVALUATION 

This section presents the implementation details (§VI-A) and performance comparison (§VI-B) of the proposed approach with the state-of-the-art approach by Kleppmann et al. [13] in a geo-replicated setting at three different continents to demonstrate the usefulness of the proposed approach. 

## _A. Implementation_ 

We have implemented the proposed algorithm in Golang [27] and wrapped it in gRPC [28] network service to deploy at three different geo-location (Western Europe, Southeast Asia, and East US) on Microsoft Azure Standard E2s <u>v3</u> VM instances, each consisting of 2 vCPU(s), 16 GiB of memory, and 32 GiB of temporary storage running Ubuntu 20.04 operating system and Intel Xeon Platinum 8272CL processor.<sup>2</sup> 

The network latency from US East to Southeast Asia is 111 ms, the maximum, and West Europe to US East is 41 ms which is the minimum. Table I shows the network latency’s between different geo-locations chosen for the experiments. However, network latency’s are not considered in the final results because the _time to apply_ a move operation (local or remote) is computed at each replica. We ran the experiments 7 times. The first two runs are considered warm-up runs, hence each point in the plot is averaged over five and across the different replicas. The synthetic workload is used for the experiments consisting of insert, delete, and move operations. Initially, the tree is empty and based on the experiment, the number of nodes varies in the tree. The node is identified by _key_ or _node id_ an integer. The Lamport clock [17] is used to timestamp each operation. 

Each replica generates and applies local operations, subsequently asynchronously propagating and receiving the operations to/from the other two replicas. Replica generates the move operation by selecting tree nodes uniformly at random from the tree size. Each replica generates (<sup><u>1</u></sup> 3<sup>)</sup><sup>_rd_ofthetotalnumberofoperationsappliedandreceives(</sup> 3<sup><u>2</u>)</sup><sup>_rd_oftheoperationsfrom</sup> the other two replicas. When a replica receives a remote operation, it applies, and in case of any undo operation due to cycle, identifies the appropriate previous parent (for the node with the higher timestamp in the cycle). Once the appropriate parent is identified, the node is moved as a child and the undo operation is sent to other replicas as per the protocol. Note that our experimental workload is more conservative and contains more conflict than the real-time workload. Further, move operations conflict only with other move or delete operations; they do not conflict with other operations (such as updating a value at a node in the tree or inserting a new subtree). 

## _B. Results and Analysis_ 

We performed two kinds of experiments as shown in Figure 4 and 5. In Figure 4, we show experiments to evaluate the performance when the number of tree nodes is fixed to 500 while the number of operations that a single replica is issuing per second is varying. The x-axis is the _operations per second_ varied from 250 to 5000 while the y-axis is the time taken to apply an operation (local or remote) at a replica. 

In Figure 5, the experiment is a function of nodes in the tree to conflicting operations when operations are fixed to 15K (5K operations per replica) and fixed to 500 operations per second on three different geo-locations. This experiment shows the number of undos and redos operations in the proposed approach at different replicas. 

Having explained the high-level overview of the experiments, we now go into the details. Figure 4 depicts the average time to apply a local and remote move operation. In this experiment, the number of operations per second is varied from 250 to 5K, while the number of nodes in the tree is kept constant at 500. As illustrated in Figure 4(a), the average time to apply a local move operation at a replica is consistent and not so significant between both approaches when the number of operations increases. The apply time for a local move operation drops as the number of operations increases. Kleppmann et al. [13] also observed similar trends for local operations. However, the time to apply a remote move operation is almost constant in the proposed approach, while Kleppmann’s approach has the opposite trend; the time increases with operations per second for remote move operations as shown in Figure 4(b). There is a significant performance gap for the remote operation in both the 

> 2Source code: https://github.com/anonymous1474 

11 



<!-- Start of picture text -->
8 KleppmannProposed et al. [13] 950 KleppmannProposed et al. [13]<br>800<br>6<br>600<br>4 400<br>200<br>2<br>0<br>0<br>250 1000 2000 5000 250 1000 2000 5000<br>operations per second operations per second<br>(a) Local move operation apply time (b) Remote move operation apply time<br>805 .<br>595 .<br>505 .<br>507 . 368 . 363 .<br>427 .<br>386 .<br>343 .<br>317 .<br>93353 .<br>31357 .<br>23026 .<br>1146 .<br>8169 .<br>558 . 473 . 431 . 403 . 397 .<br>TimeApplyAverageto LocalOperation(s)a µ TimeApplyAverageto Operation(s)Remotea µ<br><!-- End of picture text -->

**Fig. 4:** Average time to apply a move operation. 



<!-- Start of picture text -->
500<br>Western Europe<br>Southeast Asia<br>427<br>400 394 East US<br>332 # operations: 5000<br>300<br>Fixed 500 operations per sec.<br>200<br>100 47<br>48<br>44<br>0<br>200 500 1000 2000<br>Tree size (# nodes)<br>ConflictsAverage#<br>(undo-redooperations)<br><!-- End of picture text -->

**Fig. 5:** Average number of conflicts (undo and redo operations) at different replicas in proposed approach. 

approaches; this is because the number of compensation operations (undo/redo) by Kleppmann’s approach is _≈_ 200 undo and redo operations for every remote operation a replica receives while in our case it is only 1 that too whenever there is a cycle (conflict). 

As shown in Figure 4(b), Kleppmann’s approach attains a maximum apply time of 933.53 _µ_ s over a remote move operation at 5K operations per second. In comparison, the minimum is 81.69 _µ_ s at 250 operations per second; the minimum time is _≈_ 14.63 _×_ higher than the maximum time of 5.58 _µ_ s at 250 operations by the proposed approach. It can be seen that the proposed approach achieves, on average, a speedup of 1.34 _×_ for the local move operation, while 68.19 _×_ speedup for the remote move operation over Kleppmann’s approach. Hence, the proposed approach is much faster in applying remote operations, and the difference in time only increases with an increase in the rate of operations per second. This shows the performance benefits of the proposed approach. In Kleppmann’s approach, as the rate of operation generation increases, the number of operations in flight also increases. As a result, the compensation cost will be high, i.e., a more significant number of undo and redo operations. In contrast, even if the rate increases in the proposed approach, the compensation cost remains the same in the proposed approach. Next, we explain the reason behind this performance gain. 

In Figure 5, a line chart depicts the average number of conflicts (undo and redo operations) at different replicas in the proposed approach. In this experiment, we fixed the number of local operations to 5K per replica (total 15K operations) and the operation interval to 10 milliseconds while varying the number of nodes in the tree from 200 to 2K. This experiment is performed to demonstrate the number of undos and redos operations performed by each replica and the system performance when the tree size is changed. The maximum number of conflicts is _≈_ 427 when the number of nodes in the tree is 200, but it drops to _≈_ 47 when the number of nodes in the tree is 2K, as shown in the Figure 5. These numbers are much smaller than the number of undos and redos by Kleppmann’s approach, which roughly equals 2M (million) for 10K remote move operations at a replica in the worst case. Since the average number of undos and redos per remote move operation in Kleppmann’s approach 

12 

is _≈_ 200 [13]; as a result, we can see that the proposed approach significantly improves the remote move operations apply time than Kleppmann’s approach. 

## VII. CONCLUSION AND FUTURE DIRECTIONS 

We proposed a novel algorithm for efficient move operations on a replicated tree structure. The proposed technique ensures that replicas that have viewed and applied the same set of operations will eventually converge to the same state. Moreover, it does not require active cross-replica communication, making it highly accessible even during network partitions. We have followed a last write win scheme on globally unique timestamps. The proposed technique requires a single compensating operation to undo the effect of the cyclic operation. It achieves an average speedup of _≈_ 68.19 _×_ over the state-of-the-art approach. We have stored a constant number of the previous parents for every node. Identifying the optimal number of previous parents is left as future work. Implementing an efficient move operation on other replicated data structures could be an exciting area to explore. Also, performing operations on a range of elements in the list and tree CRDTs, or applying operations in a group from the same replica, is another potential direction to pursue. 

## REFERENCES 

- [1] W. Vogels, “Eventually consistent: Building reliable distributed systems at a worldwide scale demands trade-offs? between consistency and availability,” _Queue_ , vol. 6, no. 6, p. 14–19, Oct. 2008. [Online]. Available: https://doi.org/10.1145/1466443.1466448 

- [2] M. Roohitavafa, M. Demirbas, and S. Kulkarni, “Causalspartan: Causal consistency for distributed data stores using hybrid logical clocks.” in _2017 IEEE 36th Symposium on Reliable Distributed Systems (SRDS), pp. 184-193._ , ser. SRDS’17. IEEE, 2017. 

- [3] D. Abadi, “Consistency tradeoffs in modern distributed database system design: Cap is only part of the story,” _Computer_ , vol. 45, no. 2, pp. 37–42, 2012. 

- [4] P. Bailis and A. Ghodsi, “Eventual consistency today: Limitations, extensions, and beyond: How can applications be built on eventually consistent infrastructure given no guarantee of safety?” _Queue_ , vol. 11, no. 3, p. 20–32, mar 2013. [Online]. Available: https://doi.org/10.1145/2460276.2462076 

- [5] W. Vogels, “Eventually consistent,” _Commun. ACM_ , vol. 52, no. 1, p. 40–44, jan 2009. [Online]. Available: https://doi.org/10.1145/1435417.1435432 [6] S. Burckhardt, “Principles of eventual consistency,” _Foundations and Trends in Programming Languages_ , vol. 1, no. 1-2, pp. 1–150, 2014. [Online]. Available: http://dx.doi.org/10.1561/2500000011 

- [7] C. A. Ellis and S. J. Gibbs, “Concurrency control in groupware systems,” in _Proceedings of the 1989 ACM SIGMOD international conference on Management of data_ , 1989, pp. 399–407. 

- [8] D. A. Nichols, P. Curtis, M. Dixon, and J. Lamping, “High-latency, low-bandwidth windowing in the jupiter collaboration system,” in _Proceedings of the 8th annual ACM symposium on User interface and software technology_ , 1995, pp. 111–120. 

- [9] T. Seifried, C. Rendl, M. Haller, and S. Scott, “Regional undo/redo techniques for large interactive surfaces,” in _Proceedings of the SIGCHI Conference on Human Factors in Computing Systems_ , 2012, pp. 2855–2864. 

- [10] N. Preguic¸a, “Conflict-free replicated data types: An overview,” _arXiv preprint arXiv:1806.10254_ , 2018. [11] M. Shapiro, N. Preguic¸a, C. Baquero, and M. Zawirski, “A comprehensive study of convergent and commutative replicated data types,” Ph.D. dissertation, Inria–Centre Paris-Rocquencourt; INRIA, 2011. 

- [12] M. Shapiro, N. Preguic¸a, C. Baquero, and M. Zawirski, “Conflict-free replicated data types,” in _Symposium on Self-Stabilizing Systems_ . Springer, 2011, pp. 386–400. 

- [13] M. Kleppmann, D. P. Mulligan, V. B. F. Gomes, and A. Beresford, “A highly-available move operation for replicated trees,” _IEEE Transactions on Parallel and Distributed Systems_ , pp. 1–1, 2021. 

- [14] S. Nair, F. Meirim, M. Pereira, C. Ferreira, and M. Shapiro, “A coordination-free, convergent, and safe replicated tree,” _arXiv preprint arXiv:2103.04828_ , 2021. 

- [15] Figma, “Figma: the collaborative interface design tool,” https://www.figma.com/, [Online; accessed 27-07-2021]. [16] N. Bjørner, _Models and software model checking of a distributed file replication system_ . Springer, 2007, pp. 1–23. [17] L. Lamport, “Time, clocks, and the ordering of events in a distributed system,” _Commun. ACM_ , vol. 21, no. 7, p. 558–565, Jul. 1978. [Online]. Available: https://doi.org/10.1145/359545.359563 

- [18] S. S. Kulkarni, M. Demirbas, D. Madappa, B. Avva, and M. Leone, “Logical physical clocks.” in _International Conference on Principles of Distributed Systems, pp. 17-32. Springer, Cham_ , ser. OPODIS’14. Springer, Cham, 2014. 

- [19] T. Jungnickel and T. Herb, “Simultaneous editing of json objects via operational transformation,” in _Proceedings of the 31st Annual ACM Symposium on Applied Computing_ , 2016, pp. 812–815. 

- [20] Y. Saito and M. Shapiro, “Optimistic replication,” _ACM Comput. Surv._ , vol. 37, no. 1, p. 42–81, Mar. 2005. [21] C. Baquero, P. S. Almeida, and A. Shoker, “Making operation-based crdts operation-based,” in _IFIP International Conference on Distributed Applications and Interoperable Systems_ . Springer, 2014, pp. 126–140. 

- [22] S. Martin, P. Urso, and S. Weiss, “Scalable xml collaborative editing with undo,” in _OTM Confederated International Conferences On the Move to Meaningful Internet Systems_ . Springer, 2010, pp. 507–514. 

- [23] M. Kleppmann and A. R. Beresford, “A conflict-free replicated json datatype,” _IEEE Transactions on Parallel and Distributed Systems_ , vol. 28, no. 10, pp. 2733–2746, 2017. 

- [24] M. Najafzadeh, “The analysis and co-design of weakly-consistent applications,” Ph.D. dissertation, Universit´e Pierre et Marie Curie-Paris VI, 2016. [25] M. Najafzadeh, M. Shapiro, and P. Eugster, “Co-design and verification of an available file system,” in _Verification, Model Checking, and Abstract Interpretation_ . Cham: Springer International Publishing, 2018, pp. 358–381. 

- [26] V. Tao, M. Shapiro, and V. Rancurel, “Merging semantics for conflict updates in geo-distributed file systems,” in _Proceedings of the 8th ACM International Systems and Storage Conference_ , ser. SYSTOR ’15. New York, NY, USA: ACM, 2015. 

- [27] “Go programming language,” https://golang.org/, [Online; accessed 27-07-2021]. 

- [28] “grpc: a high performance, open source universal rpc framework,” https://grpc.io/, [Online; accessed 27-07-2021]. 

13 

