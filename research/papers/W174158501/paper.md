

# **Communication Timestamps for File System Synchronization** 

**The Harvard community has made this article openly available.  Please share how this access benefits you. Your story matters** 

|Citation|Cox, Russ and William Josephson. 2001. Communication<br>Timestamps for File System Synchronization. Harvard Computer<br>Science Group Technical Report TR-01-01.|
|---|---|
|Citable link|http://nrs.harvard.edu/urn-3:HUL.InstRepos:23017121|
|Terms of Use|This article was downloaded from Harvard University’s DASH<br>repository, and is made available under the terms and conditions<br>applicable to Other Posted Material, as set forth athttp://<br>nrs.harvard.edu/urn-3:HUL.InstRepos:dash.current.terms-of-<br>use#LAA|



## **Communication Timestamps for File System Synchronization** 

Russ Cox and 

William Josephson 

TR-01-01 

Computer Science Group Harvard University Cambridge, Massachusetts 

### **Communication Timestamps for File System Synchronization** 

_Russ Cox_ 

_William Josephson_ 

_rsc,wkj@eecs.harvard.edu_ 

#### _ABSTRACT_ 

The problem of detecting various kinds of update conflicts in file system synchronization following a network partition is well-known. All systems of which we are aware use the version vectors of Parker _et al._ These require _O_ ( _R_<sup>.</sup> _F_ ) storage space for _F_ files shared among _R_ replicas. We propose a number of different methods, the most space-efficient of which uses _O_ ( _R_<sup>.</sup> _F_ ) space in the worst case, but _O_ ( _R_ + _F_ ) in the expected case. 

To gain experience with the various methods, we implemented a file synchronization tool called Tra. Based on this experience, we discuss the advantages and disadvantages of each particular method. 

Tra itself turns out to be useful for a variety of tasks, including home directory maintenance, operating system installation, and managing offline work. We discuss some of these uses. 

#### **Introduction** 

Anyone who uses more than one computer system is aware of the data management problem posed by doing so: sharing files between systems requires propagation of changes to the other systems by some synchronization method. One solution is to avoid the need for synchronization, keeping all files on one system and accessing them via a network file service or remote login sessions. This is unsatisfactory, because it assumes continuous connectivity to the central system. The ability to operate in the face of network partition, whether intentional (as in the case of mobile computing) or unintentional (as in the case of failures), is highly desirable. Another solution is to synchronize manually, moving files by hand as necessary. This solution too is unsatisfactory, despite its apparently widespread usage: manual data synchronization is tedious, time-consuming, and error-prone. 

In response to these problems, network file systems such as AFS [4] and Coda [3] provide support for disconnected operation, in which a local cache provides file service and then is synchronized with the central server when the network connection is reestablished. This works well for the case of members of a workgroup taking files with them on a laptop when they go home or on business trips, but the overhead of 

maintaining a central server makes the approach unappealing for personal use. It is also sometimes awkward or impossible to provide all machines with connectivity to a central server. For example, consider someone whose only connection between his desktop machines at home and at work is a laptop carried back and forth. While in this case the laptop could conceivably be made the central server, in more complicated scenarios there is no candidate at all for a central server. 

The Rumor system [2] is one attempt to address such scenarios. In the Rumor model, files are replicated among a network of systems, with no one system being the central point of truth for any given file. Rumor does not assume that each machine can directly communicate with every other machine, but rather that between any pair of machines, there is some perhaps indirect path along which changes can propagate. Systems such as Unison [1] and Microsoft’s Briefcase use a peer-to-peer model but only allow a single pair of replicas. All three systems have the added benefit of being implemented without additional support from the operating system, making them more portable. In particular, Rumor is available for Linux and FreeBSD; a Windows port is rumored to exist. 

- 2 - 

Unfortunately, the difficult part of synchronization is the detection of independent changes to copies of the same file on different replicas and the subsequent resolution of these conflicts. The frequency of conflicts has been measured to be quite small [3], so we take the attitude that most resolution can be left to the user, concerning ourselves with the problem of detecting conflicts. The most common detection method for a system of _F_ files shared among _R_ replicas requires _O_ ( _R_<sup>.</sup> _F_ ) space per replica. We propose a new method that requires _O_ ( _R_<sup>.</sup> _F_ ) space in the worst case, but only _O_ ( _R_ + _F_ ) space in the expected case. Our method also enables a novel approach to the detection of conflicts involving deleted files. We then examine a number of hybrids of the two methods. 

To test the various methods, we wrote a file synchronization tool called Tra. We hope to employ Tra in our day-to-day use of a handful of systems running a handful of operating systems. 

In what follows, we present two related formalizations of the conflict detection problem and prove their equivalence. We present both formalisms because we expect that one of the two will be significantly more intuitive depending on the reader’s background. We also give a summary of the most common method, due to Parker _et al._ , along with its proof of correctness. Then we present our method and its proof of correctness. Finally, we describe the architecture of Tra and reflect on the various methods’ pros and cons, closing with a discussion of future applications of both the theory and the software. 

time is fine enough that no two files will have the same creation time). Origin points are unique in the sense that two perhaps differing files are derived from a common earlier version if and only if they have a common origin point. A _name conflict_ occurs when two replicas contain files with the same name but different origin points: these files have been independently created, so it is not safe simply to copy one over the other. 

The second problem is that of identifying simultaneous updates to a file by multiple replicas. For example, if _A_ and _B_ change their copies of the file independently, a conflict should be detected when they try to synchronize. On the other hand, if _A_ changes its copy, _A_ and _B_ synchronize, _B_ changes its copy, and then _A_ and _B_ synchronize again, there should be no conflict even though the file contents at the time of the second synchronization may be identical to those in the synchronization in the last example. Precise definition of these conflicts requires more terminology. 

A _modification id_ is a unique identifier for a modification to a file at some point during its life. The _modification history_ associated with a version of a file is the ordered list of modification ids for all modifications made to the file. It is safe to replace one version of a file with another if the first version’s modification history is a prefix of the second’s since the second must necessarily be the result of further modifications to the first. 

#### **Partition Graphs** 

#### **A Theory of Conflicts** 

Conflict detection reduces to answering the question, ‘‘here are files from two replicas; is it safe to synchronize by simply copying one onto the other?’’. We summarize the theory of conflicts used by Parker _et al._ [5]; our own modifications to the theory are noted as such. It is assumed that nothing is known about the semantics of the file contents. In some cases, such knowledge will enable simpler approaches, but this theory is interested in handling the general case. 

The first problem lies in naming among the distributed replicas. Specifically, the simultaneous renaming or creation of files in multiple replicas can create situations in which files on different replicas share a name but are unrelated. As a solution, Parker proposes the use of _origin points_ , which serve as unique identifiers for files and last for the entire lifetime of the system. A candidate for such an origin point might be the name of the creating replica along with the time of creation (assuming the granularity of 

Parker _et al._ present conflicts in terms of characteristic graphs called _partition graphs;_ such a graph, denoted _G_ ( _f_ ) for a file _f_ , is a directed acyclic graph in which each node represents a synchronized partition at a given time. The arrows, or edges of the graph, connect partitions sharing participants. Since nodes represent partitions, each replica listed in a node must be in exactly one parent and one child node. The exceptions, of course, are that the source has no parent and the sink no children. The represented series of communications must be serializable, in the sense that there exists an equivalent sequence of sequential synchronizations. 

- 3 - 



<!-- Start of picture text -->
AB<br>CD<br>m AB CD<br>m A u,m BC D<br>u BCD<br>x ABCD<br><!-- End of picture text -->

Remember that the graph _G_ ( _f_ ) is for a particular file, not the entire shared set of files. A small ‘‘m’’ next to a node indicates the file was modified in that partition, a ‘‘u’’ indicates an update without conflict, and an ‘‘x’’ indicates that a conflict needs to be resolved as part of the synchronization that created the partition.<sup>1</sup> Parker _et al._ ’s system assumes synchronized partitions of arbitrary size which break and recombine. In this example drawn from their paper, the file is modified in the _AB_ -only partition, the new _AB_ -only version is propagated by _B_ to the _BC_ -only partition, and then modified again. Meanwhile, _A_ has taken the _AB_ -only version and modified it independently. The _BC_ change propagates to _D_ in the _BCD_ partition, and when _A_ and _BCD_ attempt to re-synchronize, a conflict is detected due to the earlier independent modifications. 

As the example suggests, in a partition graph, a conflict must be reconciled at node _P_ if and only if (a) _P_ has two distinct ancestor nodes _P_ 1 and _P_ 2 at which modifications were made,<sup>2</sup> and (b) _P_ 1 and _P_ 2 have no common descendant that is also an ancestor of _P_ . The proof is simple. 

1. If there exist no distinct ancestors _P_ 1 and _P_ 2 with modifications, the histories of the parents would be identical and there would be no conflict. That is, if (a) does not hold, there is no conflict. 

2. If for every pair of _P_ ’s ancestors _P_ 1 and _P_ 2 with modifications, there is a common descendant of theirs that is an ancestor of _P_ , then one of _P_ ’s parents would also be a descendant of both _P_ 1 and _P_ 2, but that parent’s modification history would contain both of the earlier modifications, so those modifications would not cause a conflict at _P_ . That is, if (b) does not hold, there is no conflict. 

3. Since _P_ 1 and _P_ 2 are distinct, they have 

> 1 Parker _et al._ use a small ‘‘+’’ for both ‘‘m’’ and ‘‘x’’; we feel our notation is clearer, and distinguishing these cases has advantages noted later. 

> 2 The reader familiar with Parker _et al._ ’s paper will at this moment be wondering why we have written ‘‘modifications’’ rather than ‘‘modifications and/or reconciliations,’’ as is used therein. To that reader, we say ‘‘Hold that thought’’. 

incompatible modification histories. In particular, the history at _P_ 1 contains the modification ( _P_ 1 , _t_ 1 ) but not ( _P_ 2 , _t_ 2 ), while the history at _P_ 2 contains ( _P_ 2 , _t_ 2 ) but not ( _P_ 1 , _t_ 1 ). Since _P_ 1 and _P_ 2 have no common descendant that is also an ancestor of _P_ , neither of _P_ ’s parents’ histories can contain both ( _P_ 1 , _t_ 1 ) and ( _P_ 2 , _t_ 2 ): each contains one, and thus the parent’s histories are incompatible, so there is a conflict at _P_ . That is, if (a) and (b) hold, then there is a conflict. 

Thus, there is a conflict if and only if both (a) and (b) hold. 

#### **Partial Orders** 

Another way to think about conflicts is as a problem of partially ordered sets. The ‘‘is a prefix of’’ relation on modification histories imposes a partial order (and hence a lattice structure) on the set of modification histories of files in the system. We will write _P_ `f` _Q_ when _P_ ’s modification history<sup>3</sup> is a prefix of _Q_ ’s and will write _P_ = _Q_ to denote equality. For a node _P_ with two parents _P_ 1 and _P_ 2, there is a conflict if neither _P_ 1 `f` _P_ 2 nor _P_ 2 `f` _P_ 1. That is, if the two partitions are incomparable under the relation, neither associated history can be a prefix of the other. When _P_ 1 `f` _P_ 2, the _P_ 2 copy should be chosen, and vice versa. 

Moreover, the relation defining the partial order can be derived from the partition graph of the preceding section: whenever one node in that graph is a parent of another node, it means the file propagated from child to parent, so the parent’s modification history must be a prefix of the child’s and so it follows that _P_ `f` _Q_ . If a file passed from node _P_ to _Q_ and was not changed at _Q_ , then the modification history at _P_ is the same as the modification history at _Q_ and so we must have _P_ = _Q_ . Conversely, if a file passes from _P_ to _Q_ and _Q_ modifies it, _P_ `f` _Q_ , but the converse fails to hold. 

If _P_ has parents _Q_ 1 and _Q_ 2, and a conflict occurs at _P_ , this means that neither _Q_ 1 `f` _Q_ 2 nor _Q_ 2 `f` _Q_ 1, _i.e., Q_ 1 and _Q_ 2 are incomparable under the partial order. Each history must contain a modification not in the other. More formally, there must exist modifying nodes _P_ 1 and _P_ 2 such that _P_ 1 `f` _Q_ 1 and _P_ 2 `f` _Q_ 2. but neither _P_ 1 `f` _Q_ 2 nor _P_ 2 `f` _Q_ 1. These conditions are exactly those laid out in the partition graph definition of conflict. 

> 3 More precisely, the modification history of the version of the file at node _P_ , but that’s a bit long-winded. 

- 4 - 

#### **A Digression on Conflict Resolution** 

The graphs we have been using readily capture a time-ordered sequence of modifications and conflictfree synchronizations, with the result that we can determine whether a conflict occurs at a given later node. If we introduce resolved conflicts into the Parker graphs, the meaning of the graphs becomes ambiguous. For example, consider the partition graph labeled (0): 



<!-- Start of picture text -->
AB AB<br>CD CD<br>m AB m CD m AB m CD<br>............<br>m A x BC m D m A x BC m D<br>? AB ? CD u AB x CD<br>(0) (i)<br>AB AB<br>CD CD<br>m AB m CD m AB m CD<br>............<br>m A x BC m D m A x,m BC m D<br>x AB u CD x AB x CD<br>(ii) (iii)<br><!-- End of picture text -->

Is there a conflict at each of the bottommost nodes? The answers depend on the resolution at _BC_ . If the conflict at _BC_ is resolved simply by using the copy from the upper _AB_ , then there should not be a conflict at the lower _AB_ , since in effect the upper _CD_ modification has been ignored by _BC_ , as in the graph labeled (i). There should, of course, still be a conflict at the lower _CD_ . If we choose the _CD_ copy, the answer is reversed, as in the graph labeled (ii). If we merge the _AB_ and _CD_ copies at _BC_ , replacing them with a new copy, then there are conflicts in both bottommost nodes, as in the graph labeled (iii). By conflating ‘‘m’’ with ‘‘x’’, Parker’s graphs always assume case (iii). 

This example illustrates the subtleties that must be taken into account when dealing with synchronization scenarios. Part of the problem here is that the modification history definition of the partial order breaks down when conflicts are resolved: if _BC_ chooses to merge the _AB_ and _CD_ version somehow, we would like to arrange that the resulting version of the file has the properties that _AB_ `f` _BC_ and _CD_ `f` _BC_ , but this would mean that _BC_ ’s modification history needs to have both _AB_ ’s and _CD_ ’s as prefixes, which is impossible since neither _AB_ ’s nor _CD_ ’s history is a prefix of the other’s. Parker _et al._ ’s version vector method and 

our communication vector method differ in how they approach this problem. 

#### **Version Vectors** 

As we have presented it, the crux of the filesystems synchronization problem is the detection of synchronization conflicts, which we have defined in terms of the comparison of the files’ modification histories. Thus to detect conflicts, it suffices to store along with each file its entire modification history. Such a method grows unwieldy very quickly. Hopefully a more compact representation would be less costly to store and easier to manipulate. In fact, all the methods we shall present, including Parker _et al._ ’s version vector approach, begin with the problem of storing entire modification histories and then apply various assumptions to reduce the necessary storage. Let us begin by considering the version vector solution. 

Suppose we use as our modification ids the replica name along with a per-file sequence number as the modification id, so the fifth change to a particular file by replica _A_ is denoted by _A_<sup>5</sup> . Note first that modification histories are never reordered: if a modification history of a file on one replica is the list [ _A_<sup>1</sup> , _B_<sup>1</sup> , _A_<sup>2</sup> , _A_<sup>3</sup> , _B_<sup>2</sup> ], another version of the same file will never be encountered that has a modification history that begins [ _B_<sup>1</sup> , _A_<sup>1</sup> , _A_<sup>2</sup> ]. Thus, it is permissible to treat the list as a set. Now the partial ordering is given by the ‘‘is a subset of’’ relation rather than the ‘‘is a prefix of’’ relation. Next note that modification histories are ‘‘locally backwards closed’’: if _A_<sup>3</sup> is in a history, the history is sure to contain _A_<sup>1</sup> and _A_<sup>2</sup> as well. Parker _et al._ ’s version vectors take advantage of these two properties to condense the history into a vector containing a single time for each replica. For instance, the example history above would be represented by the vector ( _A_<sup>3</sup> , _B_<sup>2</sup> ). 

Two version vectors are compatible if one dominates the other: that is, if every entry in one vector is greater than or equal to the corresponding entry in the other vector. This vector domination relation exactly encodes our partial ordering from before. Two version vectors are incompatible, causing a conflict, if neither dominates the other. 

Notice that the change to a set-based modification history solves the synchronization representation problem mentioned in the previous section: to indicate that a version at node _P_ is the resolution of two conflicting versions at nodes _Q_ 1 and _Q_ 2 we set _P_ ’s modification history to the union of the histories of _Q_ 1 and _Q_ 2. In terms of vectors, we set _P_ ’s version vector to the elementwise maximum of the two conflicting 

- 5 - 

version vectors. This produces the desired effect that _Q_ 1 `f` _P_ and _Q_ 2 `f` _P_ . 

This solution is not completely general. In terms of the three possible resolutions described in the previous section, the union of the histories corresponds to the last solution, in which a new version is created that merges both previous versions. There is no way, using version vectors, to express both that we chose one version over the other and that we resolved the conflict. Consider the graph labeled (0): 



<!-- Start of picture text -->
AB AB<br>CD CD<br>m AB m CD m AB m CD<br>............<br>m A x BC D m A x BC D<br>? AB ? CD u AB x CD<br>(0) (i)<br>AB<br>CD<br>m AB m CD<br>m A x BC D<br>x AB u CD<br>(ii)<br><!-- End of picture text -->

(This is the same example as above except that node _D_ no longer modifies the file.) Note that in all cases, the modification histories of the upper _AB_ and _A_ and the upper _CD_ and _D_ are the same: that is, _AB_ = _A_ and _CD_ = _D_ . Suppose the upper _AB_ version is chosen over the upper _CD_ version. If to express this we set the modification history _BC_ = _AB_ (which, thinking strictly in terms of file versions, is clearly the case), then we correctly avoid the conflict at the lower _AB_ , but we repeat the just-resolved conflict at the lower _CD_ (see (i)). If, on the other hand, we choose as our history _BC_ = _AB_ ∪ _CD_ , we correctly avoid the conflict at the lower _CD_ but now induce a false conflict at the lower _AB_ (see (ii)). 

Put more succinctly, if we choose the upper _AB_ ’s history as the modification history for _BC_ , we will ‘‘re-conflict’’ at the lower _CD_ . If we add anything to the upper _AB_ ’s history to create _BC_ ’s history, we will falsely conflict at the lower _AB_ . Modification histories alone are not sufficient to keep track both of conflicts and of previous resolutions. 

#### **Communication Vectors** 

The first step in our proposed solution is the use of _communication vectors_ to address the problems with version vectors and modification histories in general. A communication vector is like a version vector but notes the currency of the _information flow_ from a given replica rather than the currency of the file modifications. 

Note that in the absence of reconciliations, modification histories are ‘‘globally backwards closed’’: if the modification history of one version of a file is [ _A_<sup>1</sup> , _B_<sup>1</sup> , _A_<sup>2</sup> , _A_<sup>3</sup> , _B_<sup>2</sup> ] and another history contains _B_<sup>2</sup> , that other history will also contain _A_<sup>1</sup> , _B_<sup>1</sup> , _A_<sup>2</sup> , and _A_<sup>3</sup> . As a result, to test whether one history is a prefix of another, it suffices to test whether the most recent entry in the one history exists in the other. This does not mean that we can store only the last entry of each history, since the larger history must be able to answer to containing any of its modification ids. 

For the moment, consider our communication vectors as condensing the history in the same manner as version vectors. In addition to this vector, we store the id of the most recent modification to our version. To check whether a file with last modification id _m_ 1 and communication vector _c_ 1 is an older version of a file with last modification id _m_ 2 and communication vector _c_ 2, we need only check whether _m_ 1 is contained in the history represented by the communication vector _c_ 2. Consider the example that so troubled the version vectors: 



<!-- Start of picture text -->
AB<br>CD<br>m  =  A 1 m  =  D 1<br>m AB m CD<br>c  =  ( A 1 , B 0 , C 0 , D 0 ) c  =  ( A 0 , B 0 , C 0 , D 1 )<br>m  =  A 2 m  =  D 1<br>m A x BC D<br>c  =  ( A 2 , B 0 , C 0 , D 0 ) c  =  ( A 0 , B 0 , C 0 , D 1 )<br>? AB ? CD<br>(0)<br>AB<br>CD<br>m AB m CD<br>............<br>m A x BC D<br>u AB u CD<br>(i)<br><!-- End of picture text -->

(Here, we assume that although _A_ and _B_ were continuously synchronized in the upper _AB_ pair, it was _A_ that made the modification; similarly, _D_ made the modification in the upper _CD_ pair.) Suppose again that we want to resolve the conflict at _BC_ by using _AB_ ’s 

- 6 - 

version as it is. We can set the communication vector to ( _A_<sup>1</sup> , _B_<sup>0</sup> , _C_<sup>0</sup> , _D_<sup>1</sup> ) but leave the last modification id as _A_<sup>1</sup> . The addition of _D_<sup>1</sup> to the communication vector indicates that _BC_ is aware of that modification, although _BC_ has chosen not to apply the modification to its version of the file. It is a ‘‘non-modification’’ id in the history. Now _BC_ `f` _A_ since _BC_ ’s _m_ `f` _A_ ’s _c_ : _A_<sup>1</sup> is in the communication history described by ( _A_<sup>1</sup> , _B_<sup>0</sup> , _C_<sup>0</sup> , _D_<sup>0</sup> ). So there is no conflict at the lower _AB_ and the _A_ version of the file wins, as it should. Similarly, _D_ `f` _BC_ , so the _BC_ version of the file wins without conflict at the lower _CD_ . If we think of _D_ ’s modification counter as a sort of local clock, storing _D_<sup>1</sup> in all the vectors is equivalent to noting, ‘‘I know all about this file as it existed on _D_ at _D_ ’s time 1.’’ This allows us to conclude, when we encounter at the lower _CD_ a version of the file last modified by _D_ at time 1, that it contains nothing but old news and can be ignored. We have successfully encoded the conflict resolutions without introducing false conflicts. 

Communication vectors are attractive for another reason: they lend themselves to very high compression rates when used for a set of files. Suppose that on a replica with _F_ files, we use a per-replica modification id counter rather than a per-file counter. That is, if replica _A_ changes one file, then another, then the first again, the corresponding modification ids would be _A_<sup>1</sup> , _A_<sup>2</sup> , and _A_<sup>3</sup> where before they were _A_<sup>1</sup> , _A_<sup>1</sup> , and _A_<sup>2</sup> . After _A_ synchronizes its full set of files with _B_ , the _B_ element of all of _A_ ’s communication vector entries can safely be set to _B_ ’s largest modification id. The file last modified by _B_ will already have that id in its vector. Changing the other vectors effectively have a sequence of non-modification ids to the histories. This does not pose problems because _B_ uses a single counter for all modification ids, so we will never encounter these as real modification ids. Now the ‘‘local time’’ interpretation of the vector makes even more sense: if we think of each change to a file on _B_ as a local _B_ time step, updating all the _B_ elements of _A_ ’s communication vectors records that for all the files, ‘‘I know all about this file as it existed on _B_ at _B_ ’s time _t_ .’’ This propagation is transitive: if _B_ ’s set of files is up-to-date with respect to machine _C_ at time _t C_ , then after _A_ synchronizes with _B_ , _A_ ’s set of files is also up-to-date with respect to _C_ at time _t C_ . 

In a system with _R_ replicas, each vector is of length _O_ ( _R_ ). In a version vector method, most vectors are unique, so storing a set of _F_ files requires _O_ ( _R_<sup>.</sup> _F_ ) space for the vectors. In our method, most of the time there will only be a small number of unique vectors. (In the case where the entire tree is always synchronized, there is only one unique vector.) We can store a list of vectors and use indices into this list. 

If there are _d_ distinct vectors, the list requires _O_ ( _R_<sup>.</sup> _d_ ) space and the indices for a set of _F_ files requires _O_ ( _F_ log _d_ ) space. We also need to store the last modification id for each file, _O_ ( _F_ ) space. Thus we have a _O_ ( _R_<sup>.</sup> _d_ + _F_<sup>.</sup> (1 + log _d_ )) total space requirement. When _d_ = 1, this reduces to _O_ ( _R_ + _F_ ). 

#### **Deletion Conflicts (Whom ya gonna call?)** 

If one replica deletes its version of the file, that deletion should propagate to other replicas so that eventually there is no record of the file remaining: it really is deleted. This is complicated by the possibility that one replica could delete a file while another independently modifies it. When those two replicas synchronize, it is not correct to delete the updated file, nor is it correct to recreate the deleted file: a _deletion conflict_ conflict must be reported. If we consider deletion as a final modification, then the methods applicable to modification histories continue to apply.<sup>4</sup> In order to detect deletion conflicts, we need to keep the modification history after the file is deleted. Data left over even after the deletion of a file is commonly called a _ghost_ . The central problem in handling deletion is ‘‘ghostbusting.’’ If we leave ghosts in our system even after all replicas have deleted the associated files, eventually we will unnecessarily run out of storage space. On the other hand, if we bust ghosts too early, we may not detect some deletion conflicts or may, instead of propagating a deletion, imagine a creation, resulting in files coming back to life without explanation. 

Rumor, which uses version vectors, employs a distributed two-phase garbage collection for ghostbusting. Using communication vectors enables a much simpler approach to ghostbusting. When a file is deleted, we leave its communication vector as a ghost. If we need to determine whether a given file is a version of the one we deleted, we can compare its creation time to the ghost communication vector: a time older than the vector indicates that we deleted that file, while a time newer than the vector indicates a different file. Similarly, comparing the last modification id with the communication vector distinguishes between a simple delete propagation and a deletion conflict. Let us define that the communication vector associated with a directory is the elementwise minimum of the communication vectors of its children. Once the communication vector of a parent directory dominates the vector of a ghost, that ghost can be removed: if it is needed, the parent vector can be used 

> 4 Similarly, origin points can be eliminated by treating creation as an initial modification. 

- 7 - 

equivalently. The progress of ghostbusting depends only on the rest of the directory being eventually synchronized. If the whole tree is checked at each synchronization, ghosts never exist at all: since all communication vectors are the same and thus all directory vectors dominate all their children’s vectors, and ghosts are busted immediately. 

#### **Implementation** 

We have implemented the ideas discussed here in a system called Tra. The system only synchronizes in one direction at a time, providing a convenient way to bootstrap new machines, do backups, or insulate one server from changes on another. In order accommodate the asymmetric synchronizations, the partition graph model changes somewhat: an edge need not have a common replica on its endpoints anymore. The partial order and information flow models apply without change. 

The implementation of Tra is quite simple. A central synchronization program ( `sync` ) coordinates the synchronization between a ‘‘from’’ replica server and a ‘‘to’’ replica server ( `srvs` ). 



<!-- Start of picture text -->
sync<br>srv srv<br>from to<br><!-- End of picture text -->

The `srv` programs are charged with maintaining a _database_ of communication and modification vectors, which the `sync` program queries and modifies throughout the synchronization. The database also typically contains signatures of the files, used locally by the `srv` programs to detect when a file changes. The signatures are system-dependent: Unix and Windows systems confident in the monotonicity of system time can use `mtime` , while Plan 9 systems can use the file system-provided qids. More paranoid systems might use MD5 hashes of the file contents. 

Because of this architecture, the `sync` program and the two `srv` programs may all run on different systems: keeping the `srv` file system sweeps local is a large win, as is being able to run `sync` on either the 

‘‘from’’ or the ‘‘to’’ system. (Indeed, `sync` could be run on a third system, but the utility of such an arrangement is questionable.) In fact, the replica names provided to `sync` are expected to be executables, usually shell scripts, that take care of establishing a connection to the desired machine and invoking `srv` . Thus the connection protocol is left unspecified, and could be `ssh` , local execution, or something else entirely. 

`Sync` walks both trees simultaneously, using the synchronization and modification times to decide when files are out of date and when conflicts arise, as previously described. 

Conflicts are reported by printing the names two files for the user to compare. Once the user has resolved the conflict, the resolution choice will be automatically detected at the next sweep. Signatures of the two choices are sufficient to distinguish between choosing one, choosing the other, and merging the two. 

#### **A Review of Assumptions and Implications** 

The implementation and debugging of Tra pinpointed a number of assumptions latent in the analysis thus far, as well as some shortcomings in the communication vector method. We present a sequence of various assumptions that can be made, examine the effects of each, and discuss whether each is reasonable in practice. Understanding these tradeoffs is helpful in understanding the hybrid method adopted for Tra’s current implementation. 

- **Assumption: Local Backwards Closure of Modification Histories** . _If we have a modification from replica R at local time t for a given file, then we also have all the modifications made to that file on replica R before local time t._ 

**Effects:** It is precisely from this assumption that the method of Parker _et al._ falls out. The modification history for a file can be compressed by storing only the last modification from each replica. In effect, Parker is storing the last modification vector time and vector domination corresponds directly to one history being the prefix of another (just storing the last modification would not be sufficient). However, Parker’s vectors do not compress especially well. In particular, each file will typically have a different modification time, so one must store a vector for each file. 

**Practicality:** This assumption is entirely reasonable: since there is only one copy of file (or directory) on a given replica, changes must ultimately be ordered by local time. 

**Assumption: Total Synchronization** . _The entire file_ 

- 8 - 

_tree is synchronized each time. Partial subtree synchronizations do not happen._ 

**Effects:** Total synchronization enables the next assumption, which helps speed up synchronizations considerably. **Practicality:** The total synchronization assumption is, in practice, not reasonable. It is conceivable that a user might want only to synchronize a subtree, perhaps not wanting to deal with changes made in other subtrees yet. Further, the total synchronization assumption disallows the storage of anything less than the entire replica. It becomes impossible to have, say, a laptop with a stripped-down replica that contains binaries but no source tree. 

- **Assumption: Local Backwards Closure of** **_Replica_ Histories** . _If we have a modification from replica R at local time t for a given file, then we also have all the modifications made to any file on replica R before local time t. (Follows from total synchronization and local backwards closure for files)._ 

**Effects:** Under this assumption, vector modification times provide enough information to prune the search for modified files. Let the modification time of a directory be the ‘‘union’’ of the modification times of its children ( _i.e._ , the element-wise maximum). If the modification time of a directory on replica _R_ 1 dominates that for the directory on replica _R_ 2, then _R_ 1 has all of the changes from _R_ 2. Therefore, synchronization from _R_ 2 to _R_ 1 for the subtree rooted at the directory is complete. It is important to note, however, that without the total synchronization assumption, this scenario will break down. As an example, suppose that we have already synchronized `/usr/bob/quux` , but have not yet synchronized `/usr/ken/quux` , and both have modifications from time _t_ = 42 on the same replica. If we then do a full sync, we might think that we do not need to worry about `/usr` on the basis of having a recent copy of `/usr/bob/quux` , when in fact we have an out of date copy of `/usr/ken/quux` . 

**Practicality:** Since total synchronization is not a reasonable assumption, local backwards closure of replica histories is also not a reasonable assumption. 

#### **Assumption: Global Backwards Closure of Indi-** 

- **vidual File Histories** . _For any file, at any time a total order can be imposed on the union of modification histories held by the replica. Further, each history in the system is backwards closed with respect to this order. Put another way, each history in the system has an unambiguous last modification._ 

**Effects:** Under this assumption, we can keep just the 

replica and local time of the last modification to a file, rather than storing the entire modification vector (as distinct from communication vectors). This information is just as strong as the entire vector under the assumption. That is, it suffices to detect all conflicts and to encode past synchronization decisions. This is the assumption that allowed us to reduce the modification vectors to a single element in the communication vector scheme. 

The assumption makes ghostbusting easier, as described earlier. Once the synchronization time of a directory dominates the deletion time of a file, that file’s ghost may safely be removed. If the deletion time is needed later, the synchronization time on the directory will suffice in its stead. If applied naively, however, it does not provide support for pruning the search for out-of-date files, as Parker’s did. We might try to reconstruct the per-directory modification vectors by taking the maximum of all the modification times for all the directory’s descendants. Since the ghostbusting procedure destroys modification times, this maximum of the modification histories for a tree cannot be calculated accurately: it will miss times for busted ghosts. This shortcoming means that synchronizations must walk the entire tree even if there are no changes, while in Parker’s method we were able to detect this condition without walking past the root. 

**Practicality:** This assumption does not hold for directories, and one can imagine situations where it would not hold for ordinary files. In the case of directories, consider the deletion of two files _A_ and _B_ from a directory at the same vector time on the same host. If we wish either ordering of the deletions to be treated in the same manner, then there is no unambiguous ‘‘last deletion’’. A similar situation can arise in the case of plain files. Consider, for instance, a Unix mail spool file. The entire file is analogous to a directory and the individual messages to plain files; the same problem arises as before. 

#### **Implementation, II** 

For the purposes of making Tra a useful tool, we chose to assume global backwards closure of file histories but not total synchronization nor its consequent local backwards closure of replica histories. 

To address the shortcoming of the global backwards closure of file histories assumption, we keep full vector modification times for each directory. While this negates some of the asymptotic storage benefits of the communication vector scheme, it lets directories hold modification times when ghosts are busted. This in turn enables the pleasant property that we can prune the search for modified files, avoiding a 

- 9 - 

#### walk of the entire tree. 

The Rumor system, because it keeps only modification times, can only prune the search under the total synchronization assumption. We believe that Rumor does make this assumption, explaining the difficulties the Rumor team encountered trying to add support for partial synchronizations. We have examined the various design documents that come with the Rumor distribution, but they are fragmentary at best, not providing a definitive account of the methods used. We have been hesitant to examine the fairly large seven megabyte source tree. 

#### **Applications** 

We have used Tra to initialize a Plan 9 notebook from scratch. The machine is booted from a network file server or a CD-ROM, the file system is reamed, and then a single Tra command populates the file system from another replica. This replaces a complex installation program and the personalization that usually follows. At this point, the notebook provides an identical interface and set of files as the copied replica, and changes made on the notebook can be propagated to the original replica (or other replicas) as desired. 

One issue in initializing a new system is marking files that should be copied at the beginning of time but then left alone. For example, one might want to start with a copy of the replica’s `/etc/passwd` file, but then not propagate changes to it. This can be achieved by setting the communication time of the file to be the infinity vector. If the file does not exist on the target, it will be copied, but once copied, the synchronization algorithm will interpret the infinity vector as evidence that the file does not need any modifications other systems might have to offer. 

The convenience of having a single home directory shared among multiple computers cannot be overstated. Spring cleaning of files on one replica propagates automatically to the others, and the set of replicas provides mutual backup for each other. 

#### **Future Additions** 

We have considered allowing parts of the replica tree to be synthesized by user-level ‘‘file servers,’’ allowing structured files to be presented as directory trees so that Tra can handle them without change. For example, a Unix mail spool file could be presented as a directory of messages, so that mailboxes on multiple systems could be synchronized despite the arrival of mail on one or both. Here the user-level file server allows us to counter the restrictions of the global backwards closure assumption, because the 

assumption applies only to files. We sidestep the assumption by presenting the file as a directory. 

Such user-level file servers could also provide special semantics for ordinary files. For example, when initializing a notebook from a replica, one wants to create empty log files on the notebook rather than copying the replica’s logs in full. Further, when synchronizing, one does not want log file modifications to propagate. A user-level file server that always presented the appearance of zero-length logs elegantly solves both these problems. 

The Plan 9 local file system requires that after the `/adm/users` file (a combination of the Unix `passwd` and `group` files) is rewritten, the file server be notified with a control message before the newly added users or groups can be mentioned in operations such as _chown_ or _chgrp_ . A user-level file server could handle writes to just that file, making sure to notify the local file system of changes. 

#### **Conclusions** 

Version vectors, introduced by Parker _et al._ in 1983, have been the _de facto_ standard for addressing file system synchronization problems. However, they bring with them a number of restrictions. The most notable are the inability to encode past conflict resolutions and the need for the total synchronization assumption in order to prune synchronizations. Version vectors in file synchronization have been compared to vector clocks, which find a wide variety of applications in distributed systems [6]. 

Communication vectors allow the lifting of the restrictions associated with version vectors. We believe that communication vectors are a truer analogue to vector clocks. At the least, we believe those using or considering the use of version vectors should be more aware of the various implicit assumptions and tradeoffs regarding version vectors, communication vectors, and hybrids of the two. 

On a more practical note, the file system synchronization capabilities provided by Tra should not be ignored. They make the sharing of data between multiple computer systems bearable and even pleasant. 

#### **References** 

- [1] S. Balasubramaniam and Benjamin C. Pierce. ‘‘What is a file synchronizer?’’, _Fourth Annual ACM/IEEE International Conference on_ Mobile Computing and Networking (MobiCom ’98) (October 1998). 

- [2] Richard Guy, Peter Reicher, Davd Ratner, Michial Gunter, Wilkie Ma, and Gerald Popek. ‘‘Rumor: 

- 10 - 

mobile data access through optimistic peer-to-peer replication’’, _Proceedings: ER’98 Workshop on Mobile Data Access_ , 1998. 

- [3] James J. Kistler and M. Satyanarayanan. ‘‘Disconnected Operation in the Coda file system’’ , _ACM Transactions on Computer Systems_ , Vol. 6, No. 1, pp. 1ߝ25 (February 1992). 

- [4] L. B. Huston and P. Honeyman. ‘‘Disconnected operation for AFS’’, _Proceedings of the USENIX Mobile and Location-Independent Computing Symposium_ , August 1993. 

- [5] D. Stott Parker Jr., Gerald J. Popek, Gerard Rudisin, Allen Stoughton, Bruce J. Walker, Evelyn Walton, Johanna M. Chow, David Edwards, Stephen Kiser, and Charles Kline. ‘‘Detection of mutual inconsistency in distributed systems’’, _IEEE Transactions on Software Engineering_ , Vol. 9, No. 3 (May 1983), pp. 240ߝ247. 

- [6] Reinhard Schwarz and Friedemann Mattern. ‘‘Detecting causal relationships in distributed computations: In search of the holy grail’’, Distributed Computing, Vol. 7, No. 3 (1994), pp. 149ߝ174. 


