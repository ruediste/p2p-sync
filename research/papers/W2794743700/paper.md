# **Designing a Planetary-Scale IMAP Service with Conflict-free Replicated Data Types** 

**Tim Jungnickel**<sup>**1**</sup> **, Lennart Oldenburg**<sup>**2**</sup> **, and Matthias Loibl**<sup>**3**</sup> 

- **1 Technische Universität Berlin, Germany** **`tim.jungnickel@tu-berlin.de`** 

- **2 Technische Universität Berlin, Germany** **`l.oldenburg@mailbox.tu-berlin.de`** 

- **3 Technische Universität Berlin, Germany** **`matthias.loibl@mailbox.tu-berlin.de`** 

### **~~Abstract~~** 

Modern geo-replicated software serving millions of users across the globe faces the consequences of the CAP dilemma, i.e., the inevitable conflicts that arise when multiple nodes accept writes on shared state. The underlying problem is commonly known as fault-tolerant multi-leader replication; actively researched in the distributed systems and database communities. As a more recent theoretical framework, Conflict-free Replicated Data Types (CRDTs) propose a solution to this problem by offering a set of always converging primitives. However, modeling non-trivial system state with CRDT primitives is a challenging and error-prone task. In this work, we propose a solution for a geo-replicated online service with fault-tolerant multi-leader replication based on CRDTs. We chose IMAP as use case due to its prevalence and simplicity. Therefore, we modeled an IMAP-CRDT and verified its correctness with the interactive theorem prover Isabelle/HOL. In order to bridge the gap between theory and practice, we implemented an open-source prototype _pluto_ and an IMAP benchmark for write-intensive workloads. We evaluated our prototype against the standard IMAP server _Dovecot_ on a multi-continent public cloud. The results expose the limitations of _Dovecot_ with respect to response time performance and replication lag. Our prototype was able to leverage its conceptual advantages and outperformed _Dovecot_ . We find that our approach is promising when facing the multitude of potential concurrency bugs in development of systems at planetary scale. 

**1998 ACM Subject Classification** C.2.4 Distributed Systems 

**Keywords and phrases** Geo-Replication, CRDT, Distributed Systems, IMAP, Isabelle/HOL 

**Digital Object Identifier** 10.4230/LIPIcs.OPODIS.2017.23 

## **<mark>1</mark> Introduction** 

When designing and developing a modern online service, researchers and developers are faced with a large and diverse spectrum of challenges. Due to its ever expanding reach, the Internet offers the unique while demanding prospect of connecting potentially billions of users scattered across the planet. The underlying distributed infrastructure requires enormous consideration on its own, but matters get even more involved when applications on top of it have state. While potentially possible, the penalties of declaring one cluster to be the single leader and routing all requests through it, render such a design unfeasible when reliability and responsiveness are of importance. A geographically-distributed system architecture, however, eliminates single points of failure and enables low response times on client requests. 

Unfortunately, though, network and node failures are a given in large-scale infrastructures [2] and thus, our applications have to deal with partitions. To safeguard consistent state 

> © Tim Jungnickel, Lennart Oldenburg, and Matthias Loibl; licensed under Creative Commons License CC-BY 

> 21st International Conference on Principles of Distributed Systems (OPODIS 2017). Editors: James Aspnes, Alysson Bessani, Pascal Felber, and João Leitão; Article No. 23; pp. 23:1–23:17 Leibniz International Proceedings in Informatics Schloss Dagstuhl – Leibniz-Zentrum für Informatik, Dagstuhl Publishing, Germany 

**23:2 Designing an IMAP Service with Conflict-free Replicated Data Types** 

during these, a trade-off between availability and consistency has to be made, commonly known as the CAP dilemma [11]. Choosing consistency requires minority partitions to reject client requests – becoming unavailable – while only a majority partition is allowed to make progress. This has been and still is a viable option, though with the rise of the NoSQL movement, available architectures have gained traction. They choose to always accept client requests even though all other nodes of the system might be temporarily unreachable and try to achieve consistency when connections are re-established. Naturally, in these _eventually consistent_ [26] systems, conflicting requests arise. Much research has gone into efficient conflict resolution or even conflict avoidance. 

More recently, _Conflict-free Replicated Data Types_ (CRDTs) have been proposed as a method for avoiding conflicts [24]. If system state is built either on one of the state-based or operation-based data types, the inherent properties of these ensure that replicated state will always converge. Certain requirements have to be met in order to be able to use them, e.g., do we assume an underlying reliable causal-order network and commuting concurrent updates for the operation-based CRDTs. If we can provide these requirements, CRDTs are an elegant and efficient way to model available and partition-tolerant systems. 

In this work, we set out to model, verify, implement, and evaluate a distributed application with non-trivial state based on CRDTs. As the provided service we chose to design an IMAP server. The _Internet Message Access Protocol_ (IMAP) is the standard way to manage mailbox state and retrieve messages in an email service. IMAP is a simple and rather old standard – its beginnings date back to the mid-1980s – and as part of the email ecosystem is regularly proclaimed dead in favor of some supposedly more efficient communication service. Yet, email remains to be ubiquitous in all our lives and will stay so for the foreseeable future. As an example, Gmail recently crossed the mark of one billion monthly active users [19]. 

Even though the provided CRDT primitives [23] are concise and simple, one can fail in numerous ways when constructing non-trivial system state based on these. We wanted to be sure of the correctness of our model and thus put effort into proving it correct. To this end, we extended the CRDT and network model framework by Gomes et. al. [13, 12] written in the Isabelle/HOL interactive theorem prover to include our IMAP-CRDT. After being assured that state will always be consistent in our model, we adapted our prototype to adhere to the theoretical proof. This way, we achieve provable consistency in practice. 

In order to evaluate the benefits of our proposed IMAP-CRDT, we developed a prototype _pluto_ and built a federated Kubernetes<sup>1</sup> -based test environment on Google’s public container cloud, Google Container Engine<sup>2</sup> . We were primarily interested in two characteristics: _response time performance_ and _replication lag_ [17]. The first one captures the perceived responsiveness by users while the second describes how long it takes one system to synchronize and apply updates with the other nodes. With our self-developed IMAP benchmark for write-intensive workloads, we were able to gather both insights for _pluto_ and _Dovecot_ . We find, that our approach of designing distributed applications at planetary scale yields a straightforward flow of modeling, verifying, implementing, and evaluating software where a proven system model consolidates experimental measurements. The results attest the advantages of our CRDT-based architecture and draw near reproducibility due to our automated and open-sourced deployment infrastructure. 

> 1 `https://kubernetes.io` 

> 2 `https://cloud.google.com/container-engine` 

**T. Jungnickel, L. Oldenburg, and M. Loibl** 

**23:3** 

**Related Work.** Large-scale distributed systems replicating application state in an available and partition-tolerant way have received academic attention since the advent of the Internet. Bayou [25] was one of the first distributed storage systems that enabled users to always submit updates and ensured eventual consistency when network connection was available again. Inspired by the fundamental concepts captured in Amazon’s Dynamo paper [8], a new class of distributed data stores was proposed and developed, such as Cassandra [18] and Riak<sup>3</sup> . Many of these new developments are also based in parts on the ideas of Google’s Bigtable concept [5], which Google itself turned into Spanner [6], its planet-scale strongly consistent and partition-tolerant distributed database. Their solution towards the CAP dilemma is to run Spanner on an expensive and highly sophisticated private network which ensures almost no downtimes [4]. 

Regarding automatic resolution of conflicting writes in any distributed system, the choice is between discarding all but one update or merging all updates into one. The most common technique for the first approach is known as _last write wins_ , where the update with the biggest timestamp is picked as winner and all others are lost. One well-known merge-based resolution strategy is _operational transformation_ [9], though mostly used for collaborative text editing and of decreasing performance with increasing number of operations [1]. 

Conflict-free Replicated Data Types take a different approach as they avoid conflicts altogether due to their construction properties. Apart from the set of basic data types defined in the original report [23], constructing further CRDTs has been an active research interest, for example, sequence CRDTs such as Treedoc [20] and LSEQ [22], and a composable JSON CRDT [16]. However, integration into production software is only progressing slowly, e.g., as part of Riak or AntidoteDB<sup>4</sup> , and we don’t see many approaches for standard IT services such as our IMAP-CRDT. 

As part of this work, we verify the data type we propose within Isabelle/HOL. Formal verification of CRDTs has been done before, e.g., by Zeller et al. for state-based CRDTs [27]. The framework underlying our formal verification efforts was proposed and implemented by Gomes et al. [12], including a realistic network model and verification of the _ObservedRemoved Set_ (OR-Set). We base our IMAP-CRDT on the OR-Set and make the extensions to the framework by Gomes et al. with our data type accessible. 

Considering state replication for the most widely used IMAP server, _Dovecot_ , the _dsync_<sup>5</sup> approach is currently the only application-level support for planet-scale deployments. Unfortunately, _dsync_ is limited to pair-wise replication, forfeiting the advantages of a cloud deployment with nodes all over the world. To our knowledge, we propose the first approach to a truly planetary-scale IMAP service that at the same time achieves low response times. 

Finally, this work is based on previous efforts by us into the direction of an IMAP service based on CRDTs, presented in an earlier workshop paper [14]. 

**Contributions.** Our contributions in this work are as follows: 

We propose an IMAP-CRDT by modeling IMAP commands as operations on a CRDT. (Section 4) 

We verify the convergence of the IMAP-CRDT with Isabelle/HOL. (Section 4.1) 

- We propose an open-source prototype _pluto_ that offers IMAP at planetary scale with multi-leader replication based on CRDTs. (Section 5.1) 

> 3 `http://basho.com/products/riak-kv` 

> 4 `https://syncfree.github.io/antidote/` 

> 5 `https://wiki.dovecot.org/Replication` 

**OPODIS 2017** 

**23:4 Designing an IMAP Service with Conflict-free Replicated Data Types** 

- We introduce a benchmark for IMAP services. (Section 5.2) 

- We propose a Kubernetes-based deployment for planet-scale _Dovecot_ . (Section 6) 

- We explore response time performance and replication lag of planetary-scale IMAP services on public clouds by evaluating the developed prototype _pluto_ against state-of-the-art _Dovecot_ setups. (Section 7) 

## **<mark>2</mark> System Model** 

In a traditional 3-tier system architecture of an IMAP service, a _proxy_ (sometimes called _director_ ) forwards a client’s request to a responsible _backend_ node, where the request is processed. The service state in form of all users’ mailboxes is typically stored on one or more _storage_ nodes, traditionally hosting a strongly-consistent shared file system like NFS or GlusterFS. However, this architecture is no longer feasible in a planetary-scale IMAP system. To illustrate the pitfalls of this approach in a geo-replicated setting, we installed a GlusterFS on two virtual machines in different regions (North America and Europe) of the Google Cloud Platform (GCP). Subsequently, a _Dovecot_ in the recommended setting with one proxy and three backend nodes was deployed on our Kubernetes cluster in Europe. Our benchmark revealed, that the response times of write requests exceed the round-trip time between North America and Europe by almost two orders of magnitude. Hence, applying a naive replication over two continents can be much more costly than routing all request through a single location. 

The only solution to efficiently apply geo-replication is to relax the consistency requirements and allow backends to progress state without prior synchronization with other regions. This approach introduces the need of conflict management, because concurrent writes from multiple regions may be conflicting. The architecture underlying our proposed system enables a more fault-tolerant and responsive geo-replicated online service. In it, a backend node is authoritatively responsible for a particular range of users and asynchronously connected to backends in all other regions that are responsible for the same range of users. Furthermore, a global storage node can be added to the service that might run on single-tenant, high quality hardware for long-term storage. It can further be used as a failover destination if any backend node fails. From this _distributed system_ of backends, we derive our system model which we use in Section 4.1 to reason about the convergence of state. 

We obtain an asynchronous network of backend nodes which can be seen as independent processes. The network can suffer from partitions and can recover after a certain time. The backends continue to operate, even if the backend is temporarily disconnected from parts of the network. If a backend crashes, the state of the backend can be recovered. We assume non-byzantine behavior. 

## **<mark>3</mark> Technical Preliminaries** 

In this section we provide a brief introduction to CRDTs and IMAP. Both will be combined in Section 4 when we introduce our IMAP-CRDT. 

## **3.1 Conflict-free Replicated Data Types** 

The theoretical concept of a Conflict-free Replicated Data Type has been formalized by Shapiro et al. in [24]. In essence, CRDTs enable convergence of replicas without requiring a central coordination server or even a distributed coordination system based on consensus or 

**23:5** 

### **T. Jungnickel, L. Oldenburg, and M. Loibl** 

locking. To achieve this goal, updates on application state based on CRDTs are designed to be conflict-free in the first place. 

CRDTs come in two variants: _Convergent Replicated Data Types_ (CvRDT) and _Commutative Replicated Data Types_ (CmRDT). CvRDTs, often described as state-based CRDTs, ensure convergence by defining a _merge_ function that is applied on two diverged states in order to obtain a consistent state again. The merge function calculates the _least upper bound_ on a _join semi-lattice_ , and therefore must be commutative, idempotent, and associative. A replica can update its local state and send the updated version to all other replicas which individually apply the merge function to regain a consistent state. The order in which the merge function is applied, is irrelevant. 

In this work we focus on the operation-based variant, the CmRDT. In contrast to statebased ones, replicas in this case exchange operations directly, with minimal state information. A reliable causal-order broadcast ensures that operations ordered by _happened-before_ relation on the source replica are received and applied accordingly at all other replicas. Updates that cannot be ordered by _happened-before_ are considered _concurrent_ and required to commute. The design of a CmRDT is a challenging task, fortunately the technical report offers a variety of specifications for counters, sets, graphs, and even lists [23]. 

As mentioned, CmRDTs require a reliable causal-order broadcast to ensure causal consistency. Note, that an implementation of such a broadcast does not require consensus and can be achieved by use of vector clocks. 

With commutativity of concurrent updates and reliable causal-order broadcast, Shapiro et al. showed that any two replicas that have seen the same set of operations have equivalent abstract states and therefore eventually converge. 

## **3.2 IMAP** 

An IMAP service manages mailboxes of registered users. Users are able to interact with their mailboxes by sending IMAP commands to the server. These commands are defined in the _IMAP4rev1_ standard in RFC 3501 [7]. To reduce the complexity of this paper, we focus on the _consistency-critical_ commands, i.e., commands that change the server’s state. The commands _create_ and _delete_ respectively add or remove a mailbox (also called mailbox folder, or simply folder) to or from a user’s account. An _append_ command is used to add a message to a mailbox folder. With _store_ , the message flags, e.g., `Seen` , `Answered` , or `Deleted` , can be altered. The _expunge_ command is used to remove all messages with such a `Deleted` flag from a particular mailbox folder. Note, that the commands _store_ and _expunge_ are only allowed once a particular mailbox folder has been selected with the _select_ command. 

IMAP servers, like _Dovecot_ , support various formats to represent the mailboxes of the users on hard disk. Typical formats are _mbox_ and _Maildir_ . The latter is generally preferred due to its use of individual files per mail message and thus, no locking is required when messages are appended. The messages are given unique file system names that include any potential standard flag. 

## **<mark>4</mark> IMAP-CRDT** 

In our scenario, the main challenge is to model the IMAP commands as operations on a CmRDT. We begin with the decision on the used payload, i.e., the underlying state representation. We identified a map that projects folder names to the content of a folder to be best suited. Therefore, the content of a folder is a combination of metadata (tags) and messages. We model the map as a function _u_ : _N →P_ ( `ID` ) _× P_ ( _M_ ) where _N_ is the set of 

**OPODIS 2017** 

**23:6 Designing an IMAP Service with Conflict-free Replicated Data Types** 

|**Spei**|**cification 1** IMAP-CRDT (payload, _create_, and _delete_).|
|---|---|
|1:|**payload** map _u_:_N →P_(`ID`)_× P_(_M_)<br>_▷{_foldername _f �→_(_{_tag _t}, {_msg _m}_)_, . . . }_|
|2:|initial (_λx._(∅_,_∅))|
|3:|**update** _create_ (foldername _f_)|
|4:|**atSource**|
|5:|let _α_=_unique_()|
|6:|**downstream** (_f, α_)|
|7:|_u_(_f_)_�→_(_u_(_f_)1_∪{α}, u_(_f_)2)|
|8:|**update** _delete_ (foldername _f_)|
|9:|**atSource** (_f_)|
|10:|let _R_1 =_u_(_f_)1|
|11:|let _R_2 =_u_(_f_)2|
|12:|**downstream** (_f, R_1_, R_2)|
|13:|_u_(_f_)_�→_(_u_(_f_)1_\ R_1_, u_(_f_)2_\ R_2)|



foldernames, `ID` is the set of tags, and _M_ is a set of messages. We denote _P_ ( _X_ ) to be the power set of _X_ . 

Because a folder _f_ contains arbitrary items, the result of _u_ ( _f_ ) is a tuple of two sets. The first set, denoted as _u_ ( _f_ )1, is the set of tags that represent metadata that should not be visible to a user. The second set, denoted as _u_ ( _f_ )2, represents the messages in the folder. 

If both sets _u_ ( _f_ )1 and _u_ ( _f_ )2 are empty, the folder is interpreted as non-existent. Note, that we distinguish between a non-existent folder and an empty folder. A folder is empty, if _u_ ( _f_ )2 is empty but _u_ ( _f_ )1 is not empty, i.e., certain metadata is present. Initially, all folders are non-existent. Hence, the initial state can be described as a lambda abstraction that projects the tuple (∅ _,_ ∅) to every folder name in _N_ . 

We present the complete IMAP-CRDT in Spec. 1 and Spec. 2. We adhere to the presentation style that has been introduced by Shapiro et al. in [24]. Next, we define the operations that represent the IMAP commands and begin with _create_ and _delete_ in Spec. 1. 

The desired result of _create_ is to create an empty folder _f_ . Therefore, a fresh and unique tag _t_ is generated on the replica that initiates the operation. This initiation phase of the replica is usually called **atSource** . Thereafter, the tag _t_ is inserted into _u_ ( _f_ )1 and _u_ ( _f_ )2 remains untouched. This part of the operation is usually called **downstream** and is executed at every replica. We denote an update of the map entry as _u_ ( _f_ ) _�→_ ( _X, Y_ ) where _X_ and _Y_ are the new sets that override the existing sets. Note, that the map entries for the other folder names remain unchanged. 

In contrast to _create_ , the desired result of the _delete_ operation is to make the folder non-existent. Hence, the content of _u_ ( _f_ ) is removed at every replica. If we defined the downstream operation to be _u_ ( _f_ ) _�→_ (∅ _,_ ∅), then _create_ and _delete_ would no longer be commutative. Furthermore, the IMAP specification requires any _delete_ ( _f_ ) to be preceded by a _create_ ( _f_ ), aborting on IMAP protocol level if a client tries to remove a non-existing folder. This eliminates consistency issues when _delete_ ( _f_ ) and _create_ ( _f_ ) are issued concurrently. Note, that the definitions of _create_ and _delete_ are very similar to the _add_ and _remove_ operations on the op-based Observed-Remove-Set (OR-set), which has been introduced in [24]. 

The remaining operations _append_ , _expunge_ and _store_ are defined in Spec. 2. The _append_ operation is very similar to the _create_ operation, except that a message _m_ is inserted into _u_ ( _f_ )2 and _u_ ( _f_ )1 remains unchanged. Another important difference is the atSource precondition. The IMAP specification states, that each message is assigned a unique identifier called 

**T. Jungnickel, L. Oldenburg, and M. Loibl** 

**23:7** 

**Specification 2** IMAP-CRDT <u>(</u> _append_ , _expunge_ , and _store_ <u>).</u> 

14: **update** _append_ (foldername _f,_ message _m_ ) 15: **atSource** ( _m_ ) 16: **pre** _m_ is globally unique 17: **downstream** ( _f, m_ ) 18: _u_ ( _f_ ) _�→_ ( _u_ ( _f_ )1 _, u_ ( _f_ )2 _∪{m}_ ) 19: **update** _expunge_ (foldername _f,_ message _m_ ) 20: **atSource** ( _f, m_ ) 21: **pre** _m ∈ u_ ( _f_ )2 22: let _α_ = _unique_ () 23: **downstream** ( _f, m, α_ ) 24: _u_ ( _f_ ) _�→_ ( _u_ ( _f_ )1 _∪{α}, u_ ( _f_ )2 _\ {m}_ ) 25: **update** _store_ (foldername _f,_ message _m_ old _,_ message _m_ new) 26: **atSource** ( _f, mold, mnew_ ) 27: **pre** _m_ old _∈ u_ ( _f_ )2 28: **pre** _m_ new is globally unique 29: **downstream** ( _f, m_ old _, m_ new) 30: _u_ ( _f_ ) _�→_ ( _u_ ( _f_ )1 _,_ ( _u_ ( _f_ )2 _\ {m_ old _}_ ) _∪{m_ new _}_ ) 

`UID` . We use this requirement to assure that no two _identical_ messages are ever appended by different replicas, or even the same replica. Note, that _identical_ is not referring to the message content. In practice, it is still possible to append two messages with identical content, although the `UID` s of the messages are in fact different. 

The operation _store_ is implemented in a similar fashion. The main purpose of _store_ is to change the flags of a message _m_ old. We do not explicitly model the flags of a message. Instead, we insert the message _m_ old with updated flags as a new message _m_ new in _u_ ( _f_ )2 after deleting _m_ old from _u_ ( _f_ )2. 

In contrast to the previous definitions, the _expunge_ operation is rather counter-intuitive. The deletion of a message, which has been marked with a `Deleted` flag, is simply done by removing the message from _u_ ( _f_ )2. However, we decided that an additional tag must be inserted into _u_ ( _f_ )1 to avoid unexpected behavior in combination with a concurrent _delete_ operation. We illustrate this puzzle with the following example. 

Two replicas _r_ 1 and _r_ 2 initially share the following state of a folder: _u_ ( _f_ ) = (∅ _, {m_ 1 _}_ ). The replica _r_ 1 initiates a _delete_ operation, resulting in an update of the local state at _r_ 1 to be _u_ ( _f_ ) = (∅ _,_ ∅) and _f_ is interpreted as non-existent, i.e., the complete folder is deleted. In the meantime, _r_ 2 independently initiates an _expunge_ operation that aims to delete _m_ 1, resulting in the local state to be _u_ ( _f_ ) = ( _{t_ 42 _},_ ∅), i.e., an empty folder. At this point, it is unclear what result is actually desired, after the downstream operations are executed at both replicas. We decided, that the folder should be present as an empty folder at both replicas. Hence, according to the presented definitions the resulting state is _u_ ( _f_ ) = ( _{t_ 42 _},_ ∅). In fact, our definition of the operations gives _create_ , _append_ , _store_ , and _expunge_ a precedence over _delete_ , i.e., when manipulations of the folder _f_ and a _delete_ ( _f_ ) are concurrently executed, the folder is never entirely deleted, only state visible at the initiation time of the _delete_ operation is removed. Hence, we decided to pursue an _add-wins_ semantic. 

**Design Decisions and Discussion.** The proposed _add-wins_ strategy comes at the price of increased metadata that needs to be managed. In the presented definition, we create a new tag for each deleted message of an _expunge_ operation. These tags are currently only removed 

**OPODIS 2017** 

**23:8 Designing an IMAP Service with Conflict-free Replicated Data Types** 

by a _delete_ operation, which is typically not executed as long as the user holds interest in the folder. To overcome this issue, some metadata could be deleted after a certain _stable_ state has been reached. For example, Baquero et al. introduced the notion of log compaction through _causal stability information_ in [3]. An alternative decision would be to give _delete_ precedence over the other operations. In this case, less metadata would be required to process state information. However, the application behavior in the presence of concurrent updates seems undesired. For example, in case of a concurrent _append_ and _delete_ operation on the same folder, the message that was added by the _append_ operation would be deleted with the folder and be lost forever. Note, that our IMAP-CRDT requires causal-order delivery and we omit this precondition in every update operation for the sake of simplicity. 

The commands left to achieve full compliance with RFC 3501 are mostly _read_ commands like _search_ , _fetch_ , or _status_ , and thus not in the scope of this work. Missing _write_ commands like _copy_ and _rename_ bear many parts from the already implemented _write_ commands but require further careful thought. However, modeling those commands as operations on the IMAP-CRDT is an effortful but realizable task. 

## **4.1 Verification** 

To ensure that all required properties are satisfied and that convergence among replicas is achieved, we verified our IMAP-CRDT with the interactive theorem prover Isabelle/HOL. We base our Isabelle/HOL implementation on the recently published CRDT verification framework by Gomes et al. [12]. Our formalization follows the definitions we presented in Spec. 1 and Spec. 2 and is available in the _Archive of Formal Proofs_ for Isabelle/HOL [15]. The only notable difference in the Isabelle/HOL formalization is, that we no longer distinguish between sets `ID` and _M_ and that the generated tags of _create_ and _expunge_ are handled explicitly. This makes the formalization slightly easier, because less type variables are introduced. Ultimately, we show that our IMAP-CRDT achieves _strong eventual consistency_ in our proposed system model, i.e., all replicas converge to an identical abstract state when they share the same history of operations. 

## **<mark>5</mark> Prototype** 

## **5.1 Pluto** 

Our prototypical implementation of the presented IMAP server system model, _pluto_ , and all other software we wrote for this paper, is developed in the Go programming language and available<sup>6</sup> as open-source software under GPLv3 license. The prototype is designed as a distributed IMAP server, internally comprised of multiple _distributor_ nodes, multiple _worker_ (backend) nodes, and one _storage_ node, as set out in Section 2. We put emphasis on application speed, security, reliability, and configurability. 

In any _pluto_ deployment, an IMAP request enters the service at a stateless distributor node. Any request initiated via an unencrypted connection will get dropped, ensuring that authentication credentials transmitted as part of an ordinary IMAP session are only ever sent over a TLS connection. The distributor node handles a session as far as the authentication procedure was successful. For any further request, the worker node responsible for the partition of users the current user is part of, is determined, and all traffic is proxied to this 

> 6 `https://github.com/go-pluto/pluto` 

**23:9** 

### **T. Jungnickel, L. Oldenburg, and M. Loibl** 

node via a gRPC<sup>7</sup> connection. Should the determined worker node be unavailable due to any number of reasons, a _failover_ to the global storage node is performed, which accepts the proxied IMAP traffic in place of the worker node. 

As soon as requests of a regular IMAP session reach a worker or storage node, they potentially change the mailbox state of the respective user. To achieve availability even in case of failures, worker and storage nodes accept these state-changing requests and guarantee that eventual consistency with the other replicas is reached – an inherent feature of CRDTs. We say that worker and storage nodes are stateful because they first alter their local states and afterwards send messages downstream that apply the same operation on all remote states. This makes _pluto_ a multi-leader replication system. 

We verified the correctness of our state replication as part of Section 4 and implemented the two required components, the IMAP-CRDT and reliable causal-order broadcast of update messages, as parts of _pluto_ . For the IMAP-CRDT, we assign each user an OR-Set, called _structure_ , that represents the user’s abstract mailbox state. The main difference to our theoretical model in Spec. 1 is, that the map _u_ ( _f_ ) for mailbox folder _f_ is modeled as a set of _value-tag_ pairs for which the _value_ element is always set to _f_ . As an example, we consider a mailbox folder `uni` , on which an _append_ operation was executed. Assume, that the state according to Spec. 1 looks like _u_ ( `uni` ) _�→_ ( _{α}, {m}_ ). We can infer that the _create_ operation for `uni` created tag _α_ in _u_ ( `uni` )1 and the _append_ operation put _m_ into _u_ ( `uni` )2. In our _structure_ OR-Set this is represented as _{_ ( `uni` _, α_ ) _,_ ( `uni` _, m_ ) _}_ . Thus, in _pluto_ we do not distinguish between metadata and message tags. Any update to _structure_ is followed by a file system `sync` operation on an associated log file on stable storage. This ensures that nodes can precisely reconstruct the internal representation of user mailboxes in case they crash. 

An update on a source replica triggers a message to all downstream replicas in order to reproduce it on their state. In _pluto_ , worker and storage nodes are grouped into subnets that exchange updates for a particular partition of users. Considering a planetary-scale deployment with workers in Europe, the US, and Asia, and the storage in Australia, the subnet for a worker `eu1` in Europe might contain `us1` , `asia1` , and `storage` . Each downstream message from `eu1` is sent to all other nodes from its subnet. As the IMAP-CRDT is based on the operation-based OR-Set, we require these messages to be part of a reliable causal-order broadcast, ensuring that they are delivered to the application exactly once and with no causally-preceding ones missing. To this end, we maintain vector clocks [21, 10] for each subnet. Send queue, receive queue, and vector clock are again `sync` ’ed into associated files on any update. To reduce replication lag, we do not send messages individually but transfer the current send log as a whole in a defined interval. 

IMAP clients will not notice the replication efforts, as they happen asynchronously. This is one of the major advantages of a geographically distributed _pluto_ deployment: requests can be responded to quickly due to authoritative local state on close-by worker and storage nodes while updates will get applied everywhere eventually due to CRDTs and reliable causal-order broadcast, thus achieving consistent state. We guide these claims with structured logging, metrics exposure to Prometheus, and tests for important packages. Further work might go into file checksum checking for ascertaining data integrity, deeper performance profiling, and increased RFC 3501 [7] compliance. We welcome contributions from the community. 

> 7 `https://grpc.io` 

**OPODIS 2017** 

**23:10 Designing an IMAP Service with Conflict-free Replicated Data Types** 

## **5.2 Benchmark** 

In order to evaluate _pluto_ and _Dovecot_ in Section 7, we needed a way to apply a large amount of state-changing IMAP commands to our deployments. We are interested in the state-changing (“write”) commands of RFC 3501 because only these manipulate mailbox state and trigger downstream messages that need to be applied at other replicas. “Read” commands in turn are answered authoritatively on the replica they are received on, without replica communication. Only state-changing commands potentially unearth consistency issues by generating edge cases. Thus, we required an IMAP benchmark that is able to generate large write-intensive workloads involving the write commands that are implemented in both services: _create_ , _delete_ , _append_ , _expunge_ , and _store_ . 

We could not find such tool or data set available, and thus implemented an IMAP benchmark ourselves<sup>8</sup> that generates arbitrary amounts of random data, write-intensive workloads. Each workload is composed of small and randomly generated sequences of IMAP commands, called _sessions_ . One session always contains well-matched IMAP commands, e.g., a mailbox folder is created before a message is appended to it. Session generation is deterministic and can be reproduced by configuring a benchmark with the same seed. 

Before a session is executed, a user is chosen randomly from a provided users file and logged in. Next, the session commands are applied one after another, a successive one as soon as the current one has finished and the time between sending it and receiving a complete answer – the command’s _response time_ – has been stored. The workload’s degree of parallelism, that is, the number of concurrent active users, can be configured as well. The results are written to disk and optionally uploaded to a Google Cloud Storage (GCS) bucket. 

## **5.3 Maildir Tools** 

With the benchmark ready we had almost everything in place required for putting our system model to a test. One more component was needed, though, for gaining insight into the replication performance. While the IMAP benchmark provides response time measurements, replication lag data is at least as important because it tells us how well a service is able to disseminate and apply updates among its replicas. It complements the user-centric response time metrics by making visible the asynchronous replication part. Due to different replication mechanisms in _pluto_ and _Dovecot_ , though, we had to fall back to observing the Maildir file system in order to see when updates were applied. 

We implemented a small utility<sup>9</sup> that periodically performs a disk usage calculation of a configured subset of the Maildirs present on a node (by running `’du -s’` ). The results are logged to disk and uploaded to a GCS bucket at the end of the tool’s run. For continuous monitoring, a duration histogram is exposed to Prometheus. The idea is to integrate one Maildir dumper into each stateful node deployed in a service to be evaluated. After having run an IMAP benchmark, timestamped disk usage reports can be collected and the time difference between the points in time when two observed Maildirs report the same size in bytes can be calculated. We consider this measure the replication lag. Please note, that the calculated time differences have to be taken as estimations rather than precise durations as we rely on synchronized clocks for timestamp elicitation. In Section 7, we will see that the clock synchronization in Google’s data centers has negligible influence on our results. 

> 8 `https://github.com/go-pluto/benchmark` 

> 9 `https://github.com/go-pluto/maildir_tools` 

**T. Jungnickel, L. Oldenburg, and M. Loibl** 

**23:11** 

## **<mark>6</mark> Infrastructure** 

We now introduce the infrastructure setup used in our experiments later on. As guiding principle, we have chosen a _Cloud Native_ approach, featuring the most advanced cloud technologies available at the time of developing our prototype. Our infrastructure is mainly based on two products: Kubernetes, an orchestration platform for containerized applications, and Prometheus, a powerful monitoring tool. 

We provisioned two identical Kubernetes clusters in the `us-east1-b` and `europe-west1-b` regions of the Google Cloud Platform. Each cluster consisted of six `n1-standard` nodes (1 vCPU, 3.75GB memory). We combined both clusters into a Kubernetes cloud federation, enabling cross-cluster service discovery and resource synchronization. For persisting data, we always allocated 100GB SSD volumes. In the following, we will write `us` or `europe` in reference to the respective regions. 

We decided to publish our configurations in our infrastructure repository<sup>10</sup> , so that our setup can easily be re-created and re-used for further experiments. Hence, all resources, including the container images of all evaluated systems, are publicly available. 

## **<mark>7</mark> Evaluation** 

To evaluate our approach, we conducted a set of experiments. We started by defining our _baseline_ , i.e., a reference experiment where we used a standard configuration of _Dovecot_ without any replication. Thereafter, we conducted two experiments where we compared _pluto_ against _Dovecot_ with enabled replication. The results of these experiments we will discuss in the end of this section. 

## **7.1 Baseline Experiment** 

As introduced in Section 2, we used a _Dovecot_ in a traditional 3-tier architecture as reference setup. For the storage layer, we deployed a _GlusterFS_ with a replicated volume on two `n1-standard` nodes with 100GB SSDs in the `europe` region. The remaining _Dovecot_ components, i.e., a proxy and three backends, were installed on our Kubernetes cluster in the same region as _GlusterFS_ . We used three backend nodes to illustrate the possibility of partitioning (also known as _sharding_ ). In this and all later experiments we maintained a total number of 120 active users in three static user partitions and the proxy was configured to redirect users to the backend that was responsible for their partition. In this setup, no replication was introduced besides the synchronized volume in the _GlusterFS_ cluster. 

We configured our IMAP benchmark (see Section 5.2) to execute 5000 IMAP sessions with a session length between 15 and 40 commands. The degree of parallelism, i.e., the number of users that are concurrently executing sessions, was set to 20. These 20 concurrent users were identified to be best suited for our experiments, because the reference setup reached the best resource utilization at reasonable response times. 

We executed our benchmark on our Kubernetes cluster in the `us` region to simulate a write-intensive load from a distant location. In other words, we used a workload that required geo-replication on a system that was not replicated. Thus, high response times were expected but no replication lag. 

> 10 `https://github.com/go-pluto/infrastructure` 

**OPODIS 2017** 

**23:12 Designing an IMAP Service with Conflict-free Replicated Data Types** 



<!-- Start of picture text -->
Dovecot dsync (5000 sessions, 20 concurrent users) pluto (5000 sessions, 20 concurrent users)<br>us-east1-b (src) us-east1-b (src)<br>europe-west1-b europe-west1-b<br>4000 4000<br>3000 3000<br>2000 2000<br>1000 1000<br>0 0<br>0 100 200 300 400 0 100 200 300 400<br>Experiment time (seconds) Experiment time (seconds)<br>Maildir size (kilobytes) Maildir size (kilobytes)<br><!-- End of picture text -->

**Figure 1** Replication lag diagram for _Dovecot dsync_ (left) and our prototype _pluto_ (right) for requests from `us` to `europe` . 

We call this experiment our _baseline_ , because all geo-replicated setups must be able to outperform it. Otherwise, the effort of geo-replication and the introduction of a replication lag is pointless. 

**Results.** We show the measured response times in the _baseline_ column of Table 1. The average and median response times in milliseconds are grouped by IMAP command. We judge the measured values as realistic for this setup. In fact, our findings in [14] confirm the authenticity of the presented values. 

## **7.2 Experiment 1: Single-Cluster Benchmark** 

In the remaining experiments, we focused on the systems that offer multi-leader replication, namely _dsync_ and _pluto_ . We deployed a setup of one proxy (or director) and three backends (or workers) in the `europe` and `us` Kubernetes clusters. Both setups were connected over a Kubernetes federation and communicated over public IP addresses and TLS-encrypted channels. In the first experiments, we replayed the settings from our _baseline_ experiment, except that the traffic from the `us` region was now directed to the respective proxy in the same region. In this scenario, the expected behavior is that both systems replicate the updated application state from `us` to `europe` asynchronously. During the run, we collected the response times and additionally tracked the size of the mailboxes for six selected sample users in both regions with our Maildir tools (see Section 5.3). The tracking interval was set to one second, which we found to be the best trade-off between additional overhead by the `du` commands and unavoidable loss of precision. With the chosen interval, a possible _micro clock drift_ between `europe` and `us` has no significant influence to our results. Based on the collected values, we identified the replication lag for both systems. We compare the results for _dsync_ and _pluto_ in the following two paragraphs. 

**Results: Dovecot dsync.** The measured response times are given in the _dsync_ column of Table 1. We judge the response times and the resulting throughput, i.e., the processed IMAP commands per second, as optimal for this setup. _Dovecot_ is – not for nothing – the state-of-the-art IMAP server software. 

For analysis of the replication lag, we compare the growth of the mailboxes in both regions for the selected sample users. In the left side of Figure 1, we illustrate the average 

**T. Jungnickel, L. Oldenburg, and M. Loibl** 

**23:13** 



<!-- Start of picture text -->
Dovecot dsync (2500 sessions, 20 concurrent users) pluto (2500 sessions, 20 concurrent users)<br>3000<br>europe-west1-b (src) europe-west1-b (src)<br>us-east1-b us-east1-b<br>2500 us-east1-b (src) 2500 us-east1-b (src)<br>europe-west1-b europe-west1-b<br>2000 2000<br>1500 1500<br>1000 1000<br>500 500<br>0 0<br>0 50 100 150 200 250 300 350 0 50 100 150 200 250 300 350<br>Experiment time (seconds) Experiment time (seconds)<br>Maildir size (kilobytes) Maildir size (kilobytes)<br><!-- End of picture text -->

**Figure 2** Replication lag for _dsync_ (left) and _pluto_ (right). The red areas represent the replication from `europe` to `us` while the blue areas represent the opposite direction. 

growth for the selected sample users in what we call a _replication lag diagram_ . On the x-axis we see the relative time of the experiment in seconds. The y-axis represents the size of the users’ mailboxes in kilobytes. The red line represents the growth of the mailboxes in `us` , i.e., the region where the traffic was injected. The green line represents the growth of the replicated mailboxes in `europe` . In this replication lag diagram, a distance between both curves parallel to the x-axis represents the replication lag in seconds, i.e., the time until the `europe` replica catches up. A distance between both curves parallel to the y-axis represents the replication lag in kilobytes<sup>11</sup> . In order to quantify the replication lag, we think that it is feasible to compute the size of the red area between both curves. The computed area in _megabyte*second_ , alongside with the average and median replication lag in kilobytes, is presented in the last 3 rows of Table 1. 

**Results: pluto.** For the _pluto_ setup, we additionally deployed the _storage_ node (see Section 5.1) in a third region ( `europe-west2-b` ). Because we cannot directly compare the storage node to any _Dovecot_ component, we used a more powerful node ( `n1-standard-4` , 4vCPU, 15GB Memory) and set the resolution of our Maildir tool for this node to 3 seconds to avoid any negative impact. The remaining parts of the _pluto_ setup is almost identical to _dsync_ , i.e., we have one director and three worker nodes with 100GB SSDs in each region. 

The measured response times are stated in the _pluto_ column of Table 1. We note that the response times are significantly higher than _Dovecot_ ’s, which we discuss in the end of this section. 

The replication lag diagram is shown in the right part of Figure 1. We see that the difference between the curves is almost invisible, which indicates a very small replication lag. The quantified replication lag is shown in Table 1. 

## **7.3 Experiment 2: Double-Cluster Benchmark** 

For our final experiment, we split the workload from the previous experiments and used our benchmark from both regions `us` and `europe` , i.e., we executed 2500 sessions from each region to simulate a workload that, in fact, requires geo-replication. The measured response times are stated in the _dsync_<sup>2</sup> and _pluto_<sup>2</sup> columns of Table 1. 

> 11 We note, that these diagrams require a monotone growth of the mailboxes to be meaningful. Our benchmark generates _mostly_ monotone growth, because _create_ and _append_ commands are more likely than _delete_ . With the chosen resolution of our Maildir tools, a declining mailbox size is almost invisible. 

**OPODIS 2017** 

**23:14 Designing an IMAP Service with Conflict-free Replicated Data Types** 

**Table 1** The combined results of Experiment 1 and 2, showing the response time performance in milliseconds and the throughput in IMAP commands per second. The average and median replication lag is stated in kilobytes and the replication lag area is stated in megabyte*second. 

||||_baseline_|_dsync_|_pluto_|_dsy_|_nc_<sup>2</sup>|_plu_|_to_<sup>2</sup>|
|---|---|---|---|---|---|---|---|---|---|
|||||||`us`|`eu`|`us`|`eu`|
|ce||Average|251.36|16.24|47.77|18.47|23.24|47.56|75.20|
|an|`CREATE`|Median|224.50|12.52|28.25|14.17|20.33|28.94|29.83|
|rm||Average|602.05|30.81|48.85|32.46|37.03|47.12|74.61|
|rfo|`DELETE`|Median|539.38|27.84|28.30|29.46|34.31|29.16|29.89|
|Pe||Average|437.26|43.02|91.96|46.39|55.36|87.23|131.79|
|me|`APPEND`|Median|400.87|38.37|57.15|42.08|50.34|55.72|58.66|
|Ti||Average|112.91|13.87|42.74|15.59|21.16|40.94|62.72|
|se|`EXPUNGE`|Median|97.05|**6.18**|25.61|**9.44**|**18.91**|22.56|23.19|
|pon||Average|184.16|15.72|52.04|17.48|21.79|46.09|72.84|
|es|`STORE`|Median|166.66|**11.93**|31.80|**13.83**|**19.64**|29.73|31.53|
|R|Throu|ghput|47.17|480.67|256.03|447.87|367.94|256.26|171.49|
|||Average||734.61|39.10|592.87|657.76|18.61|44.98|
|R|eplication<br>Lag|Median||729.10|**34.44**|217.83|322.10|**6.1**|**34.33**|
|||Area||279.89|**17.13**|97.92|209.83|**5.83**|**14.32**|



In order to measure the replication lag, we also split the sample users and configured our benchmark in a way that the mailboxes of the first half of the users are only accessed by the `us` benchmark, and the second half by the `europe` benchmark. The mailboxes of the remaining 114 users receive commands from both regions. We present the replication lag diagram for both systems in Figure 2. The red areas represent the replication lag for synchronizing state from `europe` to `us` , and the blue areas represent the replication lag in the opposite direction. 

## **7.4 Discussion** 

The _baseline_ experiment revealed that the absence of geo-replication can be costly with respect to response time and throughput, when the application is faced with traffic from distant regions. As we have seen with both compared systems, using multi-leader replication for traffic from different continents is convincing and necessary. The price for the introduced replication is relaxation of consistency guarantees and presence of a replication lag. 

By comparing the response times of both systems, and in extension to that, the achieved throughput, we clearly see that our prototype cannot keep up with _Dovecot_ and that further optimizations are necessary. We acknowledge, that throughput often is a performance metric that is placed emphasis on in large-scale services and _pluto_ needs to improve in that direction. However, because _pluto_ is a research prototype with much less development time compared to the standard IMAP server _Dovecot_ , we nevertheless are satisfied with its response time performance. We think, that optimizations of the used index structures and file management can lead to improved response times and throughput. 

With respect to the replication lag, our prototype clearly outperforms _dsync_ and we judge our approach as successful. Replication based on the used op-based CRDT is cheap compared to the costly replication of _dsync_ . An operation from one replica can almost instantly be delivered and applied on the other replicas without complex tracking of state information. The fact that our approach can be applied with an arbitrary number of replicas makes it even more interesting than _dsync_ , where only a pair-wise replication is possible. 

**T. Jungnickel, L. Oldenburg, and M. Loibl** 

**23:15** 

We note, that our experiments only focus on write-intensive workloads and we purposely omitted the evaluation of read commands. Building an IMAP server that is able to compete with _Dovecot_ in all facets is a challenging task, and is, at least for now, not our primary focus. In our opinion, the improvement of our IMAP-CRDT and exploration of further standard IT services that can be modeled with CRDTs, is a promising direction for future work. 

We would like to point out, that we chose IMAP as the protocol to model with a custom CRDT not because it is better suited for this purpose than other protocols. We chose IMAP because of its widespread use and fundamental importance in everyday life – and, because its relative simplicity allowed for completing work on time. We judge the fact that application state of an IMAP server is based on relatively simple structures, namely its tree-like mailbox structure, as particularly advantageous for modeling the commands with operations on a CRDT. Hence, as long as the structural complexity of application state to model is manageable, our approach is promising. We expect, that with the recently introduced JSON CRDT [16], the modeling of more IT services with CRDTs will become even easier. However, a machine-checked verification of the JSON CRDT is still to be done. 

## **<mark>8</mark> Conclusion** 

The initial exploration of the feasibility of using CRDTs in the multi-leader replication of an IMAP service can be considered successful. We have made two important contributions: a verified IMAP-CRDT design and the evaluation of our prototype, where we showed that the replication lag can be significantly reduced compared to _dsync_ , the replication tool of the de-facto standard IMAP server _Dovecot_ . 

In our work, we consider IMAP as the example to show the benefits of modeling standard IT services with CRDTs. Offering multi-leader replication without the need of manual conflict resolution enables not only the possibility of planet-scale distributed applications, but also more reliability in the presence of failures. To emphasize this further, this work convinced us that really any stateful IT service should be examined for applicability of multi-leader replication. Relying on strongly-consistent operations and fault-free infrastructure can get risky as state becomes ever more shared and clients distributed. CRDTs combined with formal verification offer the means to achieve confidence in relaxed consistency. Thus, considering this approach when designing and even upgrading large-scale IT services can be a matter of securing viability of a particular service – even in a single data center deployment. 

Our approach, where we began with the system design and verification followed by the implementation and evaluation, turned out to be successful in this regard. The resulting prototype combines _theory_ and _practice_ by leveraging CRDTs in a standard IT service and is able to play off its conceptual advantages. We encourage fellow system designers to follow in this path and consider CRDTs for modeling application state. 

Future work includes the exploration of CRDTs for other everyday IT services and further improvement of our prototype. 

**Acknowledgements.** We would like to thank the Software Technology Group at TU Kaiserslautern, Georges Younes, Vitor Enes, and the anonymous reviewers for their valuable comments and feedback. Furthermore, we thank the German Research Foundation (DFG) and the graduate school SOAMED for supporting this work. 

**OPODIS 2017** 

**23:16 Designing an IMAP Service with Conflict-free Replicated Data Types** 

### **~~References~~** 

- **1** Mehdi Ahmed-Nacer, Claudia-Lavinia Ignat, Gérald Oster, Hyun-Gul Roh, and Pascal Urso. Evaluating CRDTs for Real-time Document Editing. In _ACM Symposium on Document Engineering_ , DocEng’11, pages 103–112, 2011. 

- **2** Peter Bailis and Kyle Kingsbury. The Network is Reliable. _Commun. ACM_ , 57(9):48–55, 2014. 

- **3** Carlos Baquero, Paulo Sérgio Almeida, and Ali Shoker. Making Operation-Based CRDTs Operation-Based. In _Distributed Applications and Interoperable Systems_ , DAIS’14, pages 126–140, 2014. 

- **4** Eric Brewer. Spanner, TrueTime and the CAP Theorem. Technical report, Google, 2017. **5** Fay Chang, Jeffrey Dean, Sanjay Ghemawat, et al. Bigtable: A Distributed Storage System for Structured Data. In _USENIX Symposium on Operating Systems Design and Implementation_ , OSDI’06, pages 15–15, 2006. 

- **6** James C. Corbett, Jeffrey Dean, Michael Epstein, et al. Spanner: Google’s Globallydistributed Database. In _USENIX Conference on Operating Systems Design and Implementation_ , OSDI’12, pages 251–264, 2012. 

- **7** Mark R. Crispin. INTERNET MESSAGE ACCESS PROTOCOL - VERSION 4rev1. RFC 3501, University of Washington, 2003. URL: `https://www.rfc-editor.org/rfc/rfc3501. txt` . 

- **8** Giuseppe DeCandia, Deniz Hastorun, Madan Jampani, et al. Dynamo: Amazon’s Highly Available Key-value Store. In _ACM Symposium on Operating Systems Principles_ , SOSP’07, pages 205–220, 2007. 

- **9** C. A. Ellis and S. J. Gibbs. Concurrency Control in Groupware Systems. _SIGMOD Rec._ , 18(2):399–407, 1989. 

- **10** Colin J. Fidge. Timestamps in Message-Passing Systems That Preserve the Partial Ordering. In _Proceedings of the 11th Australian Computer Science Conference_ . Australian National University. Department of Computer Science, 1987. 

- **11** Seth Gilbert and Nancy Lynch. Brewer’s Conjecture and the Feasibility of Consistent, Available, Partition-tolerant Web Services. _SIGACT News_ , 33(2):51–59, 2002. 

- **12** Victor B. F. Gomes, Martin Kleppmann, Dominic P. Mulligan, and Alastair R. Beresford. A framework for establishing Strong Eventual Consistency for Conflict-free Replicated Datatypes. _Archive of Formal Proofs_ , 2017. `http://isa-afp.org/entries/CRDT.html` . 

- **13** Victor B. F. Gomes, Martin Kleppmann, Dominic P. Mulligan, and Alastair R. Beresford. Verifying Strong Eventual Consistency in Distributed Systems. _Proc. ACM Program. Lang._ , 1(109):1–28, 2017. 

- **14** Tim Jungnickel and Lennart Oldenburg. Pluto: The CRDT-Driven IMAP Server. In _International Workshop on Principles and Practice of Consistency for Distributed Data_ , PaPoC’17, pages 1–5, 2017. 

- **15** Tim Jungnickel, Lennart Oldenburg, and Matthias Loibl. The IMAP CmRDT. _Archive of Formal Proofs_ , 2017. `http://isa-afp.org/entries/IMAP-CRDT.html` . 

- **16** M. Kleppmann and A. R. Beresford. A Conflict-Free Replicated JSON Datatype. _IEEE Transactions on Parallel and Distributed Systems_ , 28(10):2733–2746, 2017. 

- **17** Martin Kleppmann. _Designing Data-Intensive Applications: The Big Ideas Behind Reliable, Scalable, and Maintainable Systems_ . O’Reilly Media, Inc., 2017. 

- **18** Avinash Lakshman and Prashant Malik. Cassandra: A Decentralized Structured Storage System. _SIGOPS Oper. Syst. Rev._ , 44(2):35–40, 2010. 

- **19** Frederic Lardinois. Gmail Now Has More Than 1B Monthly Active Users. _techcrunch.com_ , 2016. [Online; posted February 1, 2016]. URL: `https://tcrn.ch/1nJbAAe` . 

**23:17** 

### **T. Jungnickel, L. Oldenburg, and M. Loibl** 

- **20** Mihai Letia, Nuno M. Preguiça, and Marc Shapiro. CRDTs: Consistency without concurrency control. _Operating Systems Review_ , 44(2):29–34, 2010. `doi:10.1145/1773912. 1773921` . 

- **21** Friedemann Mattern. Virtual Time and Global States of Distributed Systems. _International Workshop on Parallel and Distributed Algorithms_ , pages 215–226, 1989. 

- **22** Brice Nédelec, Pascal Molli, Achour Mostefaoui, and Emmanuel Desmontils. LSEQ: an Adaptive Structure for Sequences in Distributed Collaborative Editing. In _ACM Symposium on Document Engineering_ , DocEng’13, pages 37–46, 2013. 

- **23** Marc Shapiro, Nuno Preguiça, Carlos Baquero, and Marek Zawirski. A comprehensive study of Convergent and Commutative Replicated Data Types. Technical report, Inria, 2011. URL: `https://hal.inria.fr/inria-00555588/file/techreport.pdf` . 

- **24** Marc Shapiro, Nuno Preguiça, Carlos Baquero, and Marek Zawirski. Conflict-Free Replicated Data Types. In _International Symposium on Stabilization, Safety, and Security of Distributed Systems_ , SSS’11, pages 386–400, 2011. 

- **25** D. B. Terry, M. M. Theimer, Karin Petersen, A. J. Demers, M. J. Spreitzer, and C. H. Hauser. Managing Update Conflicts in Bayou, a Weakly Connected Replicated Storage System. In _ACM Symposium on Operating Systems Principles_ , SOSP’95, pages 172–182, 1995. 

- **26** Werner Vogels. Eventually Consistent. _Commun. ACM_ , 52(1):40–44, 2009. 

- **27** Peter Zeller, Annette Bieniusa, and Arnd Poetzsch-Heffter. Formal Specification and Verification of CRDTs. In _Formal Techniques for Distributed Objects_ , FORTE’14, pages 33–48, 2014. 

**OPODIS 2017** 


