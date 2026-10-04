mat is a File Synchronizer? 



S. Balmubramaniam Vidam Communications smdar@vidm. com 

## Abstract 

Nloblle computing devices intended for disconnected operation, such as laptops and personal organizers, must employ optimistic replication strategi~ for user files. Unlike traditional distributed systems. such devices do not attempt to present a “single filesystem” semanti~ users are aware that their fles are replicated, and that updates to one rephca till not be seen in another until some point of synchronization is reached (often under the user’s exphcit control). A variety of tools, collectively called file synchronizers, support this mode of operation. 

Unfortunately, present-day synchronizers seldom give the user enough information to predict how they will behave under all circumstances. Simple slogans fike “Non-confecting updates are propagated to other replicas” ignore numerous subtletim—e.g., Precisely what constitutes a confict be @een updates in different replicas? What does the synchronizer do if updatw confict? What happens when fles are renamed? What if the directory structure is reorganized in one replica? Our god is to offer a simple, concrete, and precise frame work for describing the behavior of file synchronizers. To this end, n?edivide the synchronization task into two conceptually distinct phasm **_update detection_** and **_Reconciliation. We dEcuss_** each phase in detail and develop a straightforn’ard specification of each. We sketch our on prototype implementation of these specifications and discuss how they apply to some existing synchronization tools. 

## 1 Introduction 

The grotih of mobile computing has brought to fore novel issues in data management, in particular data reification under disconnected operation. Support for rephcation can be provided either transparently (tith flesystem or database support for cfient-side caching, transaction logs, etc.) or by user-visible tools for exThcit rephca management. In this paper we investigate one class of user-visible tools—commonly called file syrtchTonizeTs-w”hich allow, updates in different repficas to be reconciled at the user’s request. 

Permission **to make digital or hard copies ofali or part of this \vork for personal or classroom use is ~nted without fee provided that copies are not made or dis~.buted for prolit or commercial advantage and that copies bear this notice and the full citation on the first page.** To copy **othenvise. to republish, to post on servers or to redistribute to lists, requires prior specific permission anflor a fee.** MOBICOM 9S DallasTexas USA CopyrightACM 19981-58113435-.ti98/1O...00.00 

Benjamin C. Pierce University of Pennsylvania bcpierce@cis .upenn. edu 

The overall god of a tie syndronizer is easy to state: it must **_detect_ conflicting** **_updates_** and **_pTopagate non-_ con~icting** **_updates._** However, a good synchronizer is quite tricky to implement. Subtle misunderstandings of the se manti~ of fleystem operations can cause data to be lost or overwritten. k~oreover, the concept of “user update” itself is open to varying interpretiations, Ieadtng to significant differences in the results of synchronization. Unfortunately, the documentation provided for syntionizers typically makes it difficult to get a clear understanding of what they \villdo under dl circumstances: either there is no description at all or else the description is phrased in terms of low-leveI mechanisms that do not match the user’s intuitive view of the flesystem. In view of the serious damage that can be done by a synchronizer with unintended or un~xpected behavior. we w~ouldlike to estabhsh a concise and rigorous fratne~vork in which synchronization can be described and discussed, using terms that both users and implementors can understand. 

We concentrate on file synchronization in this paper and only briefly touch upon the finer-grained notion of **_data syn-_** chronization offered by newer took [Puma, DDD+94, etc.], but most of the fundamental issues are the same for file and data synchronization. These issues are dso closely related to reification and recovery after partitions in mainstream distributed systems [DGMSS5, Kis96, GPJ93, DPS+94, etc.]. Ultimately, we may hope to exnend our specification to encompass a tider range of reification mechanisms, horn data syntionizers to distributed filesystems and databases. In our model, a tie syn&onizer is invoked explicitly by an action of the user (issuing a synchronization command, dropping a PDA into a doding madle, etc.). For purposes of discussion, n’e ident@ t~vo cleanly separated phases of the fle synchronizer’s task: update detection— i.e., recognizing where updates have been made to the separate replicas since the last point of synchronization-and reconciliate ion—combining updates to yield the new, synchronized state of eah repfica. 

The update detector for each rephca S computes a predicate ditiys that summarizes the updates that have been made to S. (It is dlow’ed to err on the side of safety, indicating possible updates where none have occurred, but dl actual updates must be reported.) The reconciler uses these predlcat~ to decide n’hich reptica contains the most up-t~ date copy of each file or duectory. The contract betwreenthe 

**_98_** 

.-_ 

_.. _ .__. 

.,, 

two components is e\Trwsed by the requirement 

for dl paths p, **_~dirtys ~) *_** 

**_current Contents s@) =_** otiginalcontentss b), 

which the update detector must guarantee and on which the 



<!-- Start of picture text -->
reconciler reties. The whole synchronization process may<br>then be pictured as follows<br>Reification<br>n o~ no<br>Y z User User Y \<br>,’ lUpdatw uP*t=l ‘\.<br>/<br>\<br>A B<br>h<br>d’ / \<br>Update Update<br>Detector nDetector<br>di~h di~~<br>1<br>I i /~ ‘Y,\ Reconctier I<br>A A’ h B’<br>u<br><!-- End of picture text -->

The flesystems in both repticas start out with the same contents O. Updates by the user in one or both repficas lead to divergent states **_A_** and **_B_** at the time when the synchronizer is invoked. The update detectors for the two rephcaa check the current states of the flesystems (perhaps using some information from O that was stored earfier) and compute update predicates dirty~ and dirtyB. The reconciler usw thwe predicat= and the current states **_A_** and B to compute new states **_A’_** and **_B’, which_** should coincide ud=s there were confecting updates. The specification of the update detector is a relation that must hold between O, **_A,_** and dirty~ md between O, **_B,_** ad dirtyB; similarly, the behavior of the reconciler is specified as a relation between **_A, B, dirty~ , dirtyB , A’,_** and **_B’._** 

The remainder of the paper is organized as follows. We start with some preltilnary defitions in Section 2. Then, in Sections 3 and 4, we consider update detection and reconciliation in turn. For update detection, we describe several possible implementation strategia with ~erent performance characteristi~. For reconcihation, we first develop a very simple, declarative specification a small set of natural rules that describe the behavior of a typicti synchronizer. We then argue that these rules completely characterize the behavior of any synchronizer satis~lng them, and fidly show how they can be implemented by a straightforward r~ cursive algorithm. Section 5 sketch= our own synchronizer implementation, including the dwign choices we made in our update detector. Section 6 discusses some etisting synchronizers and evaluat= how accurately they are described by our specification. Section 7 describ~ some possible extensions. 

Niost of our development is independent of the featura of particular operating systems and the semantim of their filesystem operations; the one exception is in the implementation of update detectors (Section 3.2), which are neces- 

sarily system-specifiq our discussion there is bl~ed toward Unti. For the sake of brevity, proofs are omitted. 

## 2 Basic Defititiom 

To be rigorous about what a synchronizer do= to the tiesysterns it manipulates, the first thing we need is a,precise **_way_** of tdklng about the flesystems themselves. 

We use the metavariables z and y to range over a set ~ of filenames. P is the set of **_pathfinite_** sequent= of names separated by dots. (The dots between path components can be read = slashes by Unk users, backslashes by Windows users, and colons by Mac users.) The metavariables **_P, q,_** and r range over paths. The empty path is written c. The concatenation of paths p and **_q_** is written **_p.q._** We write 1P! for the length of path ~i.e., Ie] = O and **_Iq.zl = Iql + 1._** We write **_q S p_** if **_q is_** a prefi of p, i.e., if p = **_q.r_** for some path r. We write **_q < p_** if **_q is a proper_** prefi of p, i.e., q S **_P_** andq #p. 

For the purposw of th~ paper, there is no need to be specific about the contents of individud fles. We simply assume that we are given some set ~ whose elements are the possible contents of flea-for mample, % could be the set **_of_** dl strings of bytes. 

For modehng flesystems, there are many poasibihties. Most obviously, we could use the famihar recursive datatype: 

That is, a “flesystem node” is either a He or a duectory, where a fle is some ~ G Z and a directory is a tilte partial function mapping names to nodes of the same form. For mample, the flwystem 



<!-- Start of picture text -->
m<br>d<br>I<br>DR<br>a b<br>~<br>A<br><!-- End of picture text -->

whose root is a directory contairdng one subdirectory named d, which contains two flea u (with contents f) and b (with contents g), would be represented by the function 

**_F={dti D, n * L_** for dl other names n}, 

where 1 marks positions where **_F_** is undehed and **_D_** is the function 

**_D={a*j, b*g, n * L_** for dl other names n}. 

For purposes of specification, however, it seemore convenient to use a “flat” representation, where a flesystem is a function mapping whole **_paths_** to their contents. Formally, we say that a filesystem is an element of the set 

of finite partial functions from paths to either fles or subfilesystems. The constraint on the second fine guaanteea that we only consider functions corresponding to tree 

99 

structures-i. e., ones where Iooklng up the contents of a composite path **_p.q_** yields the same rmult as fist Iooklng up p and then looking up q in the resulting sub-flesystem (where the application ~~pression (S@))(g) is defined to yield L if Sk) is either 1 or a tie). 

Under this representation, the example flesystem above corresponds to the function 

**_F={e~F, d~D, d.atif, d.bti g, p s 1_** for dl other paths p}, 

where **_D_** is the function 

**_D={~~D,a~~,b~g,_** 

**_p * L_** for dl other paths p}. 

The metavariablw O, S, T, **_A, B, C,_** and **_D_** range over filesystems. 

When S is a filesy;tem, we write ISI for the length of the longest path p such that S@) #1. We write **_chizdren* ~)_** for the set of names denoting immediate &lldren of path p in filesystem A—that is, 

**_children~ @) = {q I g = p.z_** for some z A **_A(q) # 1}. We_** write **_children~,~ ~)_** for **_children~ ~)_** U **_children~ ~)._** We write isdir~ @) to mean that p refers to a directory (i.e., not a file and not nothing) in the Nesystem **_A._** We write **_isdir~,B @) iff_** both **_isdir~ @)_** and **_isdirB ~)._** To lighten the notation in what follows, we make some simplifying assumptions. First, we assume that, during synchronization, the fdesystems are not being modified except by the synchronizer itself. This means that they can be treated as static functions (from paths to contents), w far as the synchronizer is concerned. Second, we assume that, at the end of the previous syn~onization, the two flesystems were identicd. Third, we hande ody two replicas. Finally, we ignore links (both h~d and syrnbohc)l fie permissions, etc. Section 7 d)scussm how our development can be refined to relax thae restrictions. 

## 3 Update Detection 

With these basic detiltions in hand, we now turn to the synchronization task itself. This section focuses on update detection, leaving reconciliation for Section 4. 

## 3.1 Specification 

We first recapitulate the specification of the update detector sketched in the introduction: 

3.1.1 Definition: Suppose O and S are flesystems. Then a predicate **_dirtys_** is said to **_(safely) estimate_** the update horn O to S if =dirtys@) impfies Ok) = S@), for dl paths P. 

Among other things, this defiltion immediately tells us that, if a given path p is not dirty in either replica, then the two replicas have the same contents at p. 

3.1.2 Fact: If A, **_B,_** and O are filesystems and di~yA and **_dirtyB_** estimate the updats from O to **_A_** and O to **_B,_** then =dirty~ @) and =dirtyB ~) together imply **_A@) = B@)._** 

One other fact will prove useful in what follows. 

3.1.3 Fact: For any filesystem S, dirtys is up-closed i.e., if P < q and dirtgs **_(q),_** then ditiys ~). We shall use this fact to streamline the specification of reconciliation below. 

## 3.2 Implementation Strategies 

Update detectors satis~lng the above specification can be implemented in many different ways; this section outlines a few and discusses their pragmatic advantagw and disadvantag~. The discussion is specific to Unix fdesystems, but most of the strategiw we describe would work with other operating systems too. 

## 3.2.1 Trivial Update Detector 

The simplwt possible implementation is given by the constantly **_tne_** predicate, which simply marks every fle as dirty, with the rault that the reconciler must then regard every tie (except the ones that happen to be identicd in the two flesystems) m a confict. In some situations, this may actually be an acceptable update detection strategy. On one hand, the fact that the reconciler must actually compwe the current contents of dl the fles in the two flesysterns may not be a major issue if the filesystems are small enough and the fink between them is fast enough. On the other hand, the fact that dl updat~ lead to conficts may not be a problem in practice if there are only a few of them. The whole file synchronizer, in th~ case, degenerates to a kind of recursive remote cliff. 

## 3.2.2 Exact Update Detector 

On the other end of the spectrum is an update detector that computa the dirty predicate exactly, for example by keeping a copy of the whole flesystem when it was lwt synchronized and comparing this state with the current one (i.e., replacing the remote cliff in the previous case with two Iocd difi). Detecting updatw exactly is expensive, both in terms of disk space and-more importantly-in the time that it takes to compute the Merence of the current contents with the saved copiw of the fdesystem. On the other hand, this strategy may perform well in situations where it is run off-line (in the middle of the night), or where the link between the two computers h= very low bandwidth, so that minimizing communication due to false conficts is critical. 

## 3.2.3 Simple Modtime Update Detector 

A much cheaper, but less accurate, update detection strategy involves using the “last modified time” provided by operating systems ~ie Unix. With this strategy, just one due is saved between synchronizations in each replica the time of the previous synchronization (according to the local clock). To detect updates, eati fle’s last-modified time is compared with this tiue; if it is older, then the file is not dirty. 

Unfortunately, the most naive version of this simple strategy turns out to be wrong. The problem is that, in Unix, renaming a me does not update its modtime, but rather updatw the modtime of the directory containing the file: names are a property of duectoriw, not N=. For aYarnple, suppose we have two ties, a and **_b,_** and that we move a to **_b_** (overwriting **_b)_** in one replica. If we examine just the modtime of the path **_b, we_** will conclude that it is not dirty, and, in the other rep~ca, a will be deleted without **_b_** being changed. 

Similarly, it is not enough to look at a file’s modtime and its directory’s, since the directory itself could have been moved, leaving its modtime done but changing its parent directory’s modtime. To avoid the problem completely, we 

**100** 

-.-, -;, 

—., - ,--- 

—.— 

–-—.—: 

.–. 

must judge a fle as dirty if any of its ancestors (back to the root of the Hesystem) has a modtime more recent than the last synchronization. Unfortunately, this makes the simple modtime detector nearly useless in practice, since any update (fle creation, etc.) near the root of the tree leads to large subtrew being marked dirty. 

## 3.2.4 Modtim=Inode Update Detector 

A better strate~ for update detection under Unix refia on both modtimes and inode numbers. We remember not just the last synchronization time, but also the inode number of every fle in each replica. The update detector judges a path as dirty if either (1) its inode number is not the same as the stored one or (2) its modtime is later than the last synbonization time. There is no need to look at the modtim~ of any containing director=. For example, if we move a on top of b, as above, then the new contents of that replica at the path b will be a fle with a dfierent inode number than what w= there before. Both a and b till be marked w dirty, leading (correctly) to a delete and an update in the other repfica. We have also experimented with a thwd variant, where inode numbers are stored only for directories, not for each indlvidud file. ThE uses much less storage than remembering inode numbers for dl fles, but is not m accurate. Our own experience indicat~ that storing dl the inode numbers is a better tradeoff, on the whole. 

## 3.2.5 On-Line Update Detector 

A different kind of update detector+ne that is difficult to implement at user level under Unix but possible under some other operating systems such m Wmdows—requir= the ability to observe the complete trace of actions that the user mak~s to the filwystem. This detector will judge a fle to be modified whenever the user has done anything to it (even if the net effect of the user’s actions was to return the fle to its original state), so it does not, in general, give the same results m the react update detector. But it will normally get close, and may be cheaper to implement than the exact detector. 

On-line upate detection presuppos= the abihty to track dl user actions that fiect the fl=ystem; th~ placw it closer to the domain of tradition distributed tiwystems (cf., for example, Coda [Kls96, Kum94], Ficus m+94, PJG+97], Bayou [TTP+95, PST+97], and LittleWorks [~95]). 

## 4 Reconciliation 

We now turn our attention to the other major component of the synchronizer, the **_reconciler._** We begin by developing a set of simple requirements that any implementation should satisfy (Section 4.1). Then we give a recursive dg~ rithm (Section 4.2) and argue (a) that it satisfies the given requirements, and (b) that the requirements determine its behavior completely, i.e., that any other synchronization d- algorithmthat dso satisfies the requirements must be behaviorally indistinguishable from this one (Section 4.3). 

## 4.1 Specification 

Suppose that A and **_B are_** the current stat= of two flwystems replicating a common dwectory structure, and that we have calculated predicatm dirtyA -d dirtyB, estimating the 

updates in **_A_** and **_B_** since the last time they were synchr~ nized. Running the reconciler with thwe inputs will yield new flesystem states C and D. hformdly, the behavioral requirements on the synchronizer can be expressed by a pair of slogans: (1) **_propagate all non-inflicting updates,_ and** (2) **_if updates wn~ict, do nothing._** 

(Of course, an actual synchronization tool will typically try to do better than “do nothing” in the face of coticting updatw: it may, for example, apply additiond heuristics based on the types of flea involved, ask the user for advice, or allow manual editing on the spot. Such cleanup actions can be incorporated in our model by viewing them as if they had occurred just **_before_** the synchronizer began its red work.) 

We are &eady committed to a particular formtilzation of the notion of **_update_** (cf. Section 3): a path is updated in A if its due in A is different from its original due at the time of last synchronization. We can formfllze the notion of **_wnfiicting updates_** in an equally straightforward way updat~ in **_A_** and **_B are_** con%cting if the contents of **_A and B_** rwulting from the updates are dfierent. If **_A_** and **_B are_** both updated but their new contents happen to agree, these updates will be regarded m non-confecting. (Another alternative would be to say that overlapping updatw always confict. But th~ will lead to more false positives in confict detection.) 

Our specification of the reconciler can be stated as a set of conditions that should hold between the starting states, **_A_** and **_B,_** and the reconciled states, C and **_D,_** for every path p. Inforrndly: 

1. If p is not dirty in **_A,_** then we know that the entire subtree rooted at p has not been changed in **_A,_** and any updates in the corresponding subtree in **_B_** should be propagated to both sid~ that is, C@) (the subtree rooted at p in C) and **_D@)_** should be identicd to **_B@);_** 

2. Conversely, if p is not dirty in **_B,_** then we should have C@) = **_D@)= A@)._** 

3. Ifp refers to a directory in both **_A_** and **_B,_** then it should dso refer to a directory in C and **_D._** (Note that this requirement mak~ sense whether or not p is dirty in **_A_** or **_B.)_** 

4. If p is dirty in both **_A_** and **_B_** and refers to something other th~ a directory (i.e., it is either a file or 1) in at least one of **_A_** and **_B,_** then we have potentially confecting updates. In this case, we should leave things as they are C@) = **_Ah)_** and **_D@) = B@)._** (Note that leaving things as they are is the right behavior even in the c~e where the updat~ were not actually confecting-i. e., where it happens that **_A@) = B@).)_** 

A few exampl~ should clarify the consequence of these requirements. Suppose the original state O of the fl~ystems was 



<!-- Start of picture text -->
o=~<br>d<br>[<br>D~<br>u b<br>Af ~<br><!-- End of picture text -->

**101** 

..-. . 

-—.. . . . ..— 

.-. —..- 

it could not tell whether a was deleted in **_B_** or new in A. The ditiy predlcatm provided by the update detector rwolve the ambiguity: c is duty only in **_A,_** while a is duty only in **_B._** (Note that a less accurate update detector might dso mark c dirty in **_B_** or a dirty in **_A._** The effect would then be a confict reported by the reconciler and no changw to the filwystems-i.e., the specification requires that synchr~ nization “fail safely.”) 

and that we have obtained the current stats A and **_B_** by modi~lng the contents of **_d.a_** in A and **_d.b_** in **_B._** Suppose, furthermore (for the sake of simplicity), that we are using an exact update detector, so that ditiyd is tme for the paths da, **_d,_** and e and **_false_** otherwise, while ditiyB is **_tme_** for **_d.b, d,_** and ~. Then, according to the requirements, the resulting 

states of the two flwystems should be C and **_D as_** shown. 

Similarly, suppose the fle d.a is renamed, in A, to d.c, and that **_d.b_** is deleted in **_B._** In A, the paths marked ditiy are **_da, d.c, d,_** and c. In **_B,_** the dirty paths are **_d.b, d,_** and c. So, reconciliation will result in states C and **_D as_** shown. 



<!-- Start of picture text -->
B=m Similarly, suppose the fle d.a is renamed, in A, to<br>and that d.b is deleted in B. In A, the paths marked ditiy<br>d<br>are da, d.c, d, and c. In B, the dirty paths are d.b, d,<br>1 I<br>c. So, reconciliation will result in states C and D as shown.<br>DIR<br>A=~ ‘=~<br>u b<br>d d<br>Af ~’ I 1<br>c=~ D=~<br>d d<br><!-- End of picture text -->

The update in d.a in A h= propagated to **_B_** and the update in **_d.b_** to A, making the find stat= identicd. 

Suppose, instead, that the new flesystems A and **_B are_** obttined from O by adding a fle in **_A_** and deleting one in **_B:_** 



<!-- Start of picture text -->
A=m<br>A. ‘=(<br><!-- End of picture text -->

This is an instance of the cl~sic **_inseti/delete_** ambigu- **_ity_** [Fh,1S2, GP.J93, PST+97] faced by any synchronization mechanism: if the reconciler could see only the current states A and **_B,_** there would be no way for it to know that c had been added in A, as opposed to having etisted on both sides originally and having been deleted from **_B;_** symmetricrdly, 

On the other hand, suppose that d.a is modified in **_A_** and deleted in **_B,_** and that **_d.b_** is updated only in **_B._** The dirty paths in **_A are da, d,_** and c; in **_B_** they are da, **_d.b, d,_** and 6. The find clause above thus applies to da, leaving it unmodified in C and **_D, wMe_** the update to **_d.b_** is propagated **to** **_A as_ USUd.** 



<!-- Start of picture text -->
A=~<br>d<br>c=~<br>A ‘={<br><!-- End of picture text -->

**102** 

-.:.——- 

.-. 

.,- 

,-.. 

One small refinement is needed to complete the specification of reconciliation. h what we’ve said so far, we’ve considered arbitrary paths p. This is actually slightly too permissive, Ieadlng to cases where two of the requirements above make conflicting predictions about the results of synchronization. Suppose, for example, that, A and B are obtained by delete the whole directory **_d_** on one side and cr~ sting a new fde **_d.c_** within d on the other: 

(Of course, a concrete rediation of this algorithm would return no results, performing its task by sid~effecting the two flesystems in-place. It should be obvious how to derive such an implementation horn the dwcription we give here.) k the definition, we use the following notation for overwriting part of one Hasystem with the cent ents of the other. Let S and **_T_** be functions on paths and p be a path. We write **_T & S_** for the function formed by replacing the sub tree rooted at p in T with S, dehed formdy w fo~ows: 

**_T ~ S = Aq._** if **_p < q_** then **_S(q) eke T(q)._** 

**4.2.1** Definition ~econcihation Algorithm]: Given predicates **_dirtyA_** and **_dirtyB,_** the algorithm **_recon is_** defined as follow 

The contents of D(d.c) should clearly be h after synchronization But what should be the contents of C(d.c)? On the one hand, we have **_dirty~ (d)_** and dirtyB(d) and =iSdirA,B(d), so according to the find rule we should have C(d) = **_A(d) = 1, which_** imphes C(d.c) = 1. But, on the other hand, we have = **_dirtyA (d. c), so_** according to the fist rule, we shotid have C(d.C) = B(d.c) = h. 

ThE is a case of a genuine confecting update, and we believe the b-t tiue for C(d.c) here is 1 (the authors of at least one commercial synchronizer would disagre~f. Section 6.1). We can r=olve the ambiguity by stopping at the first hint of cotict-i.e., by considering ordy paths p where dl the ancestors of p in both **_A_** and **_B_** refer to directories (and hence do not confict): 

4.1.1 Definition: Let **_A_** and **_B_** be flwystems. A path p is said to be **_relevant_** in **_(A, B) W Vq < p. isdirA,B(q)._** 

t~ith this refinement, we are ready to state the formal specification of the reconciler. 

4.1.2 Definition [Requirements]: The pair of new filesysterns (C, D) is said to be a synchronization of a pair of original flesysterns **_(A, B)_** with respect to predicates dirty~ and **_ditiyB_** if, for each relevant path p in **_(A, B),_** the following conditions are satisfied 

- **_recan(A, B,p) = 1)_** if **_YdirtyA@)_** A YditiyB @) then **_(A, B)_** 

- **_2) eke_** if iSdirA,B@) then let @l, p2, . . . ,pn} = chitd~enA,B@) (in lticographic order) 

- in let **_(Ao, Bo) = (A, B)_** let **_(Ai+I, Bi+l) =_ recan(Ai, Bijpi+l)** for O~i<n 

- in **_(An, Bn)_** 

**_3) else_** if **lditiyA@)** then **_(A ~ B, B) 4) eke if -ditiyB @)_** then **_(A, B g A) 5) eke (A, B)._** 

That is, recon takes a pair of filesystems **_A_** and **_B_** and a path p, and returns a pair of filesystems (C, D) in which the subtrees rooted at p have been synchronized. 

An easy induction on m=(lAl, **_IBI) – lpl_** shows that **_remn_** terminates for dl Hmystems **_A_** and **_B_** and paths p. Ako, ob serve that updates to the flesystems **_A_** and **_B are_** performed only through the recursive calls and the grafting function defined above; th~ ensurw that **_recan(A, B, p) Ieavas_** unaffected W parts of **_A_** and **_B_** that are outside the subtree rooted at p. 

## 4.3 Properties 

## 4.2 Algorithm 

Having specified the reconciler precisely, we can eqlore some properties of the specification. b particular, we would Eke to know that it is **_complete,_** in the sense that it answers dl possible questions about how a reconciler should behave, and that it is **_implementable_** by a concrete algorithm that terminat~ on all inputs. We addr~s the latter point fist. For ease of comparison with the abstract requirements above, we present the algorithm in “purely functional” styl+as a function taking a pair of filesystems as an argument and returning a fresh pair of flmystems as a result. 

It remains, now, to veri~ some propertiw of the require ments specification and the algorithm. In particular, **we** can show that (1) the requirements in Definition 4.1.2 tily characterize the behavior of the reconcile and that (2) the reconciliation algorithm is sound with respect to the specification, i.e., it satisfies the requirements in Defiition 4.1.2. It is an immediate consequence of the latter fact that the requirements themselvw are consistent, in the sense that, for each **_A, B, dirtyA,_** and dirtyB, there are some C and **_D_** such that (C, **_D) is a_** synchronization of **_(A, B)_** with rwpect tO ditiyA Wd ditiyB. 

To facilitate the correctness arguments, we first intro duce a refinement of the original requirements that allows us to focus our attention on a specific region of the two filesystems. 

**4.3.1** Definition: The pair of new flmystems (C, **_D)_** is said to be a synchronization afier p of a pair of original 

**103** 

.— 

.. -..-. . .-~-=—, . - 

,. 

filesystems **_(A, B)_** if p is a relevant path in **_(A, B)_** and the following conditions are satisfied for each relevant path p.q in **_(A, B):_** 

**ldirtyA** @q) - **_c~.q) = D@.q) = B@.g) ~dirtyB @q) * C@.q) = D@.q) = A@.q) isdir~,B @q) ~ iSdirC,D @q) dirty~ @q)_ A** **_dirty~ @q)_ A** **_viSdirA,B@.q) = C@.q) = A@.q)_ A** **_D@.q) = B@.q)_** 

Note that Definition 4.1.2 is just the special cwe where p = E. 

4.3.2 Definition: Paths p and **_q are incomparable_** if neither is a prefix of the other—i.e., if p $ **_q_ A** **_q ~ p._** 

4.3.3 Definition: We write **_syn~(C, D, A, B)_** if 

1. (C, **_D)_** is a synchronization of **_(A, B)_** after p, 

2. for all paths **_q,_** if p and **_q are_** incomparable then **_C(q) = A(q)_** and **_D(q) = B(q),_** and 

3. q ~ p A iSdird,B (q) imph~ isdirC,D(q) 

The requirements we have placed on the reconciler are complete in the sense that they uniquely capture its behavio~ given two fil~yst ems which were synchronized at some point in the past, there is at most one pair of new tiesystems satis~lng the requirements. 

4.3.4 Proposition [Uniqueness]: Let **_A, B,_** and O be filesystems and suppose that dirtyd and **_dirtyB_** estimate the updates horn O to **_A_** and **_B_** rapectively. Let p be a relevant path in **_(A, B)._** If (Cl, Dl) and (C2, D2) are both synchronizations of (A, **_B)_** after p, then Cl ~) = C2~) and Dl@) = D2@). 

Furthermore, the requirements are satisfied by the alg~ rithm. 

4.3.5 Proposition [Soundness]: Let **_A, B,_** and O be filesystems and suppose that dirtyA and dirtyB atimate the updates horn O to **_A_** and **_B_** respectively. Then **_recon(A, B, p) = (C, D)_** imphes syn~(C, **_D, A, B)_** for any relevant path p in **_(A, B)._** 

Together, propositions 4.3.5 and 4.3.4 show that d- algorithmrecon is actually **_equivalent_** to the requirements given in Definition 4.1.2. On the one hand, if (C, **_D) = recon(A, B, c),_** then by soundness we know that (C, D) is a synchronization of **_A_** and **_B._** On the other hand, suppose (C, D) is a synchronization of **_A_** and **_B._** Since the algorithm is total, it must yield **_recon(A, B, e) = (C’, D’)_** for some C’ and **_D’._** But then by uniqueness, we have C = C’ and **_D = D’._** 

## 5 Our Implementation 

Our main god has been to understand the synchronization task clearly, not to produce a full-featured synchronizer ourselvw. However, we have found it helpful (as well as usefil, for our own day to day mobile computing) to experiment 

with a prototype implementation that straightforwardly embodies the specification we have described. 

Our fle synchronizer is mitten in Java, using Java’s **_Remote Method_** Invocation for networking. The dmigrt **is** intended to perform well over both highand mediumbandwidth links (e.g., ethernet or PPP). To avoid long startup delays, it uses a modtime-inode strategy (cf. Section 3.2.4) for update detection, requiring ordy minimal summary information to be stored between synchronizations. It operat m entirely at user level, without transaction logs or monitor daemons. It currently handes only two repticas at a time and is targeted towmd UnL~ flesystems (though dl but the update detector could be used with any operating system, and new update detection modulm shotid be fairly e~y to write). The user interface (see Figure 1) displays dl the flm in which updates have occurred, using a tre~browser tidget; selecting a fle from this tree displays its status in a detail didog at the right and offers a menu of reconciliation options. In the common case where a tie has been updated in only one replica, an appropriatee action is selected by de fault and the tree hsting shows an arrow indicating which dwection the update til be propagated. If both repficas are updated, the tree view displays a question mmk, indicating that the user must make some exTlicit choice. When the user is satisfied, a single button press &es dl the selected actions. 

Internally, the implementation closely follows the reconciliation algorithm in Section 4.2 (see Figure 2). At the end of every synchronization, a summary of each replica is stored on the disk. The saved information includes the time when each fle in the rephca was last synchronized and its inode number at that time. At the beginning of the n~x%synchronization, each update detector reads its summary and traverses the fle system to detect updat=. A file is marked dirty if its ctimel or inode number has changed since the Iwt synchronization. The reconciler then traversw the two replicas in parallel, examining the fles for which updates have been detected on either side and posting appropriate records to a tree of pending actions maintained by the user interface. 

## 6 Examples 

To explore the utifity of our specification, we now discuss some existing synchronizers in terms of the specification framework that we have developed. We do not attempt to provide a complete survey, just a few reprwentative examples. 

# **6.1** Briefcase 

Microsoft’s **_Btiejcase_** synchronizer [Bri98, Sch96] is part of Windows 95/NT. Its fundamental gods seem to match those embodied in our specification (“propagate updatm unless they confict, in which case do nothing by default” )—indeed, even its user interface is fairly similar to our prototype. However, **_some_** simple experiments revealed several cases where Briefcase’s behavior does not match what is predicted by our specification (or any similar specification that we can think of). 

**1In Unix, a file’s ctime gets changed if the contents or the attributes (such as permission bits) of the file are changed.** 

**104** 

.—— 

— .—— — 



<!-- Start of picture text -->
Control Tracing Actions<br>:.<br>.-i remote tfinner/Stackjava<br>L . . c[ien~iava<br>-<br>.-j ? inngr<br>acce5s<br>-L TesLjava<br>? TesLjava<br>FSTestjava (Norecommendedactlod<br>Stackjava copyfrom\ocalto shwelnm$.~.lndiaoaedu<br>[’native -<br>.-l ? net COPY from shovelnw%~.indlanaedu to Iml<br>~ .-,-’ ?-[ examples? echose~er Ignore conflict<br>~ ? EchoServer.java Proceed with selectad,action fw this file only<br>t timeClient<br>Show diffaren~s<br>L - -- doc README<br>. doc<br>.—<br>{dot unsafe /safe ‘“- ,-<br>econ for ,/dot,, done +<br>reconciling,/README.,, I<br>{README safe/ safe<br>econ for ,/README,., done !<br>econ for.,.. done<br>:Iick ‘Go’ in root nodeto prweed with selected changes<br>j-t?<br><!-- End of picture text -->

Fignre 1: User interface of our syntionizer 

The strangwt example that we encountered runs as follows. (Since it involvw- two successive synchronizations, it should be compared with the refined requirements discussed in Section 7.1.) Suppose we have a synchronized flesystem containing a duectory (folder) a, a subd~ectory a.b, and a fle **_a.b.f. Now,_** in one repfica, we delete a and dl its contents; in the other we modi~ the contents of **_a.b.f_** and add a new subduectory a.q then we syntionize. At this point, Briefcase reports that no updates are needed. (Strictly speakiig, this behavior is correct, since it Ieavw both repticas unchanged, but a confict should probably have been reported.) Now, in the second repfica, we create a new fle **_a.b.g,_** and synchronize again. This time, the synchr~ nizer do= propagate some ~angw: it recreatw a in the fist replica, adds subdirectories **_a.b_** and a.c, and copies a.b.g— but not **_a.b. f._** Success is reported, but the two filesystems are not identicd at the end. 

## 6.2 PowerMerge 

According to the manufacturer’s advertising [Pow98], the **_Powerlferge_** synchronizer from Leader Technologies is “used by virtually every large Macintosh organization and is the highwt rated file synchronization program on the market today.” \Vetwted the “bght” version of the program, which is freely downloadable for etiuation. 

Although the dwcription of the program’s behavior in the user manual again seems to agree with the intentions embodied in our specification, we were unable to make the program behave as documented. For example, deleting a tie on one side and then rwynchronizing would lead to the file being recreated, not deleted. Also, when both copies of a 

tie have been modified, the most recent copy is propagated, discwding the update in the other copy. 

## **6.3** Rumor 

UCLA’s Rumor project ~ei97, RPG+96] has built a userIevel fle synchronizer for Unix tiesystems-probably the closest cousin to our own implementation. Although its capabihties go beyond what our specification can describe, Rumor (nearly) satisfies our specification in the tw~repfica case. (Rumor’s model of syntionization originatw from the Ficus replicated flwystem; mu& of our discussion regarding Rumor dso applies to the synchronization mechanisms of Ficus [RPG+96, MR+94, GPJ93].) 

In Rumor, reconciliation is performed by a local proc~ in each repfica, which works to ensure that the most recent updates to each fle in other repficas are eventually reflected in the Iocd state of thw replica. For each file in the rep~ca, Rumor maintains a version vector reflecting the known updat= in dl replicas. During reconcihation, this version vector is compared with that of another rephca (chosen by the user or determined by availablfity) to determine whid has the latest updata. If the remote copy dominates, then the Iocd copy is modified to reflect the updates; if the Iocd copy dominates, then nothing more is done. (In wsence, reconciliation in Rumor uses a “pull mode~’: it is a on~way process.) If there is a confict, Rumor invokw a resolver based on the type of the filq for instance, updates to Unix duectories are handled by a “merge resolver” -+94]. Updatw eventually get propagated to all replicm by repeated “gossiping” between pairs of replicas. 

The update detection strategy in Rumor is a variant of 

**105** 

=-—- 

-= — 

,-.— 

=. 

.-. ..— 



<!-- Start of picture text -->
LOCAL REMOTE<br>w-”- -~~~T ‘- “ “--------------i<br>\ x N I<br>\ . E T<br>\<br>Update ~ m SYNC. Iv T Update — SYNC.<br>Detector SERVER Detwtor SERVER<br>0<br>R<br>K<br>I I<br>~E SYSTEM ~E SYS~M<br>Fi~e 2: btern& of our synchronizer<br><!-- End of picture text -->

the modtime-inode strategy described in Section 3.2.4. Rumor’s reconciliation process is more general than that de scribed by our specification. However, it does appear to satisfy our specification if we consider the fo~owing special case. (1) There are exactly two Rumor replicas. (2) Both replicas are reconciled at the same time, each treating the other as the source for reconciliation. (3) Overlapping UP dates are handled by a simple equdlty check for files (by de fault, Rumor considers updat~ to the same fle in ditferent repticas u a confict, even if they result in equal contents) and a recursive merge resolver for directories. 

## 6.4 Distributed Filesystems 

Not surprisingly, our model of synchronization has some strong similarities to the rephcation modeh underlying mainstream distributed flwystems such as Coda ~i96, Kum94], Ficus ~+94, PJG+97], and Bayou ~PS+94, TTP+95]. Related concepts dso have a long history in distributed databases (e.g., [Dav84]). 

Thwe systems Mer horn user-level fle synchronizers— and from each other—along numerous dimensions... continuous reconciliation vs. discrete points of synchronization, distinguishing or not between client and server mtilnes, eager vs. lazy reconciliation, use of transaction logs vs. immediate update propagation, etc. Since exphcit points of synchronization are not part of the user’s conceptual model of these systems, our specification framework is not duectly applicable. On the other hand, their underlying concepts of optimistic replication and reconciliation are fundamentally very similar to ours. The intention of synchronization— whenever and however it happens-is (eventually) to propagate nonconflicting updatw and to detect and repair confecting updates. Our specification can therefore be viewed as a fist step toward a more general framework in which such systems can be described and compared. 

One exception is the system dacribed by Mazer and Tardo [hIT94]. Their approach is quite similar to ours in that it includes explicit, user-invoked points of synchronization. Apart horn the asymmetry in their setting between clients and servers, our framework cotid be used to model their system. 

## 6.5 Data Synchronizers 

Much of the engineering effort in commercial synchronization tools goes into facihties for **_data synchronization—_** merging updatm to the same fle in dflerent replicas using specific knowledge of the structure of the He based on its type (address book, calendar, etc.). Related approaches have long been pursued in distributed database systems ~av84]) and has resulted in products like Oracle’s Symmetric Reification ~DD+94]. 

Surprisingly, at le~t some of these tools can be described vw ~ectly ~ ou fi~ework. For example, Puma Technology’s popular **_Intellisync_** [Puma, Pumb] can synchronize many **_kinds_** of databases between handheld PDAs, laptop computers, tid workstations. It requires that one or more **_key fields_** be chosen for each type of database to be synchronized. (For example, in an address book the key fields might be the first and last name; in a calendar database they could be the date, time, and description of an appointment.) These key fields correspond to the name of a me in our model. Changing the key fields is hke moving the flq changing information in other fields is ~ie changing the contents of the fle. 

To describe Intellisync in our framework, we just need to generalize the notion of flesystem paths to include names for individud records within fdes by allowing combinations of key-field dues as flenarne components (e.g., p = **_usr.bcp.phonebook. {lastname=Smith, firstname=John})._** The behavior described in the Intelfisync manual then follows our specification quite closely. h fact, if we consider the operation of Intelhsync just on a single database, then we may drop the clausa of our specification that ded with directories and describe its behavior even more succinctly: 

YdirtyA@) 



6.6 Version Control Systems 

Another class of systems with some striking similarities to fle synchronizers is version control or source **_contTol_** systems hke CVS. Such systems include numerous features (version 

**106** 

—. 

.._, . _ 

,. 

histories, alternative branches, etc.) that fdl outside the scope of our specification, but their core behavior includw commands like “check in dl chang~ in th~ group of Nes, except in cases where the changes cofict with changes that have aheady been checked in by another project member?’ Our requirements might be a useful starting point for full specifications of such systems. 

## 7 Extensions 

We close by skettilng some extensions of our framework. 

## 7.1 Partially Successful Synchronization 

If it recognizw confecting updatw, the synchronizer may hdt without having made the flesysterns identicd. Then, the next time the synchronizer runs, there will not be one ori@nti flesystem, but two. In general, particular regions of the fil=ystem may have been successfully synchronized at different timw. We can easily refie our specification to handle this case. (Our implementation rdso handes this refinement.) 

Instead of assuming that the repfic~ had some common state O at the end of the previous synchronization, we intro duce into the specification a new flesystem r, which records the contents of each path p at the last time when p was successfully synchronized. 

The specification of the update detector remains the same as before, except that the dirty predicate is dehed with respect to r. That is, ditiy~ @) must be fme whenever p refers in S to something ~erent from what it referred to at the end of the last successful synchronization of p. 

The reconciler is now extended with an additiond output parameter: bwidw calculating the new states C and **_D_** of the two replicas, it returns a new Nwystem r’, which will be used as the r input to the n~~t round of synchronization. For each path p, A@) records the contents of p at the last point where p was successfully synchronized. Formally, we say that the triple (C, **_D, r’)_** is said to be a synchronization of a pair of original filesystems **_(A, B)_** with respect to predicates ditiy~ and ditiyB and original state r if, for each rele~at path p in **_(A, B),_** the following conditions are satisfied: 

## 7dirtyA @) 

+ c@)= **_D@)= B@)=_** r’~) **_=ditiyB ~) a C@)= D@)= A@)= r’@) i$dir~,B @) * iSdirC,D ~)_ A** **_isdirr, ~) ditiy~ @)_ A** ditiyB ~) **A** =isdir~,B@) + C@) = A@) **A D@) = B@) A** if **_A@) = B@)_** then r’b) = **_A@) eke_** r’~) = r~) 

## **7.2** Multiple Repficas 

b general, one may wish to synchronize sever~ rephcas on different hosts, not just two. We can generalize our require ments specification to handle multiple rephcas in a fairly straightforward way. 

Let1d={l,2,..., n} be a set of tags identi~lng the n repficas to be synchronized. Let the set of original repficas to be synchronized be denoted by %S = {Si I i E **_Id}._** For any path **_p,_** let. **_DP,s_** be the set of identifiers of replicas that 

**are** dirty at pie., **_DP,s = {i ] dirt@Si @)}._** A set of new repficas fiR = {~ Ii E Id} is said to be a synchronization of **7S** with respect to dirtiness predicates dirt@Si if, for each relevant path p in 3S, the following conditions are sattied 

**_DP,s = 0_** 

**_-_** Vi~ld. a@) = S~@) DP,s # 0 A Vi,jEDP,s. sib)= **Sjb) ~** **_3jEDP,s._** ViEId. &@) = **Sj@)** isdirS@) ~ isdirR@) Si, **_j~Dp,s. S~@) # Sj@) A =*dirs@) *_** ViG1d. m~) = Si @) 

It is interwting to note that Coda’s reconciliation strategy depends on a similar requirement. Coda h= a certification mechanism which ensures that reconcihation is safe to proceed. Kumar ~um94, pages 58-61] proves that, if certification succeeds at dl servers, then for each data item d, either ~) d is not modified in any partition, (ii) the find due of d in each partition is equal to the pr~paztition due, or (iii) d was modified in exactly one partition. k a mtiti-repfica system, the process of reconcfiation may in general only involve a subset of the replicas at one time. To describe the intended behavior in th~ case, we would need to combine the above specification with the r- finement described in Section 7.1. 

## 7.3 Additiond Filesystem Properties 

A related generalization offers a natural means of extending our simple model of the flesystem to include propertia ~ie read/write/execute permtilons, timwtamps, type information, syrnbofic ~i, etc. For example, a symbolic hnk can be regarded as a special kmd of fle whose contents is the tazget of the fink. Similarly, to hande permission bits for ties, we take the contents of the fle to include both its proper contents and the permission bits. 

Hard finks are somewhat more difficult to hande, espe cidly if it is po~ible to create a hard link from inside a synchronized flesystem to some unsynchronized tie. However, if this case is excluded, it seems reasonable to handle hard finks by annotating ea& flesystem with a relation de scribing which ties are hard-~nked together and taking this additional information into account in the update detector and reconciler. 

## Acknowledgments 

Marat Fairuzov provided a motivating spark for this work by pointing out some of the subtleties of update detection. Luc Maranget and Peter Reiher gave us the benefit of their own deep experience with writing synchronizers. Jay Kistler and Brian Noble helped explore connections with distributed filesystems and gave us many leads and pointers into the literature in that area. Susan Davidson pointed out use ful connections with problems in distributed databases, and Ram Venkatapathy advised us on the mysteriw of Windows. Brian Smith contributed his usual boundess enthusiasm and helped us begin to see what it would mean to really understand synchronization (in the philosophical sense). Conversations with Peter Buneman, Giorgio Ghelfi, Carl Gunter, Bob Harper, Michael Levin, Scott Nettles, and Nlk Swoboda helped us improve our presentation of the material. Haruo Hosoya, Michael Levin, Jonathan Sobel, and the MobiCom 

**107** 

-- —— . . .- 

m 

. .—..., ,... .. .- 

referew gave us useful comments on earlier drafts of this paper. Tkis work was supported by Indiana University and by NSF grant CCR-9701826. 

References 

|**[Bri98]**|**hlicrosoft**<br>**Windows**<br>**95:**<br>**Wsion**<br>**for mobile**<br>**comput-**<br>**ing,**<br>**1998.**<br>**http://www.microso**<br>**ft.com/windows95/**<br>**info/w95mobile**<br>**htm**|
|---|---|
|**[Dav84]**|**.**<br>**.**<br>**S. B. Davidson.**<br>**Optimism**<br>**and consistency**<br>**in parti-**<br>**tioned**<br>**distributed**<br>**dat abmm.**<br>**_ACM_**<br>**_~ansactions_**<br>**_on_**<br>**_Database Systems, 9(3),_ Sep.**<br>**1984.**|
|**[DDD+94]**|**D.**<br>**Daniels,**<br>**L.**<br>**B.**<br>**Doo,**<br>**A.**<br>**Downing,**<br>**C. Elsbernd,**<br>**G. Hallmark,**<br>**S. Jain,**<br>**Bob Jenkins,**<br>**P. Lim, G. Smith,**<br>**B. Souder,**<br>**and J. Stamos.**<br>**Oracle’s**<br>**symmetric**<br>**repli-**<br>**cation**<br>**technology**<br>**and implications**<br>**for application**<br>**d~**<br>**sign.**<br>**In** **_Proceedings of SIGMOD_**<br>**_Conference, 1994._**|
|**[DGhls85]**|**_S._ B.**<br>**Davidson,**<br>**H.**<br>**Garcia-Mofina,**<br>**and**<br>**D.**<br>**Skeen.**<br>**Consistency**<br>**in partitioned**<br>**networks.**<br>**_ACM_**<br>**_Comput-_**<br>**_ing Sumeys,_**<br>**_17(3),_ September**<br>**1985.**|
|**[DPS+94]**|**Alan**<br>**Demers,**<br>**Karin**<br>**Petersen,**<br>**Mike**<br>**Spreitzer,**<br>**Dou-**<br>**glas**<br>**Terry,**<br>**hfarvin**<br>**Theimer,**<br>**and**<br>**Brent**<br>**Welch.**<br>**The**<br>**Bayou**<br>**architecture:**<br>**Support**<br>**for data sharing**<br>**among**<br>**mobile**<br>**users.**<br>**In** **_Proceedings of the Workshop on_**<br>**_Mobile Computing Systems_**<br>**_and Applications,_**<br>**_Santa_**<br>**_Cruz, California,_ December**<br>**1994.**|
|**[FM82]**|**hlichael**<br>**J. Fischer**<br>**and Alan Michael.**<br>**Sacrificing**<br>**seri-**<br>**alizability**<br>**to attain**<br>**high availabihty**<br>**of data in an un-**<br>**reliable**<br>**network.**<br>**In** **_Procetiings_**<br>**_of the ACM Sympo-_**<br>**_sium on Principles of Database Systems,_ March 1982.**|
|**[GPJ93]**|**R. G. Guy, G. J. Popek,**<br>**and T. W. Page**<br>**Jr. Consis-**<br>**tency**<br>**algorithms**<br>**for optimisic**<br>**reification.**<br>**In** **_Prowed-_**<br>**_ings_**<br>**_of the_**<br>**_First_**<br>**_International_**<br>**_Conference_**<br>**_on Net-_**<br>**_work_**<br>**_Protocols,_**<br>**October**<br>**1993.**|
|**[HH95]**|**L. B. Huston**<br>**and P. Honeyman.**<br>**Disconnected**<br>**Oper-**<br>**ation for AFS.**<br>**In** **_Prodings_**<br>**_of the USENIX Sympo-_**<br>**_sium on Mobile and Location Independent_**<br>**_Comput-_**<br>**_ing,_ Spring**<br>**1995.**|
|**[Kis96]**|**James**<br>**Jay Kistler.**<br>**_Dismnnectd_**<br>**Operation**<br>**in a** **_Dis-_**<br>**_tributed_**<br>**_File_**<br>**_System._**<br>**PhD**<br>**thesis,**<br>**Carnegie**<br>**Mellon**<br>**University,**<br>**1996.**|
|**[Kurn94]**|**Puneet**<br>**Kumar.**<br>**_Mitigating_**<br>**_the eflects of Optimistic_**<br>**Replication**<br>**in a** **_Distributti_**<br>**File** **_System._**<br>**PhD thwis,**<br>**Carnegie**<br>**Mellon**<br>**University,**<br>**December**<br>**1994.**|
|**[A4T94]**|**Murray**<br>**S.**<br>**h4azer**<br>**and**<br>**Joseph**<br>**J. Tardo.**<br>**A client-**<br>**sid%only**<br>**approach**<br>**to**<br>**disconnected**<br>**file**<br>**acc~.**<br>**In**<br>**_Workshop_**<br>**_on Mobile_**<br>**_Computing_**<br>**_Systems_**<br>**_and Appli-_**<br>**_mtions_**<br>**December**<br>**1994.**|
|**[PJG+97]**|**_,_**<br><br><br>**T. W. Page,**<br>**Jr., R. G.. Guy, J. S. Heidemann,**<br>**D. H.**<br>**Ratner,**<br>**P. L. Reiher,**<br>**A. Goel,**<br>**G. H. Kuenning,**<br>**and**<br>**G. Popek.**<br>**Perspective=**<br>**on optimistically**<br>**replicated**<br>**peer-t~peer**<br>**filing.**<br>**Software**<br>**-** **_Pmctice_**<br>**_and_**<br>**_Experi-_**<br>**_ence,_**<br>**11(1),**<br>**December**<br>**1997.**|
|**[POW98]**|**Powerh4erge**<br>**software**<br>**(Leader**<br>**Technologies),**<br>**1998.**<br>**ht tp://www.leadertech.com/merge.htm.**|
|**[PST+ 97]**|<br>**Karin**<br>**Petersen,**<br>**h4ike J. Spreitzer,**<br>**Douglas**<br>**B. Terry,**<br>**Nlarvin**<br>**h4. Theimer,**<br>**and Alan**<br>**J. Demers.**<br>**Flexible**<br>**update**<br>**propagation**<br>**for weakly**<br>**consistent**<br>**replication.**<br>**In** **_Proceedings_**<br>**_of the 16th_**<br>**_ACM_**<br>**Symposium**<br>**on** **_Op-_**<br>**_erating_**<br>**_SystemsPrinciples_**<br>**_(SOSP-1_**<br>**_6),_**<br>**Saint**<br>**_Male,_**<br>**_France,_**<br>**October**<br>**1997.**|
||**Designing**<br>**effective**<br>**synchronization**<br>**solutions:**<br>**A**<br>**White**<br>**Paper**<br>**on Synchronization**<br>**from**<br>**Puma**<br>**Tech-**<br>**nology.**<br>**http://www.pumatech.com/sync\W.html.**|



|**[Pumb]**|**A**<br>**white**<br>**paper**<br>**on**<br>**DSXfm**<br>**Technology**<br>**-**<br>**Data**<br>**Synchronization**<br>**Extensions**<br>**from**<br>**Puma**<br>**Technology.**<br>**http://www.pumatech.**<br>**com/dsx~vp.html.**|
|---|---|
|**[Rei97]**|**Peter**<br>**Reiher.**<br>**Rumor**<br>**1.0**<br>**User’s**<br>**Manual.,**<br>**1997.**<br>**http: //fmg-www.~.ucla.edu/rumor.**|
|**[RHR+94]**|**P. Reiher,**<br>**J. S. Heidemann,**<br>**D. Ratner,**<br>**G. Skinner,**<br>**and G. J. Popek.**<br>**Resolving**<br>**file conflicts**<br>**in the Ficus**<br>**file system.**<br>**In** **_USENIX_**<br>**_Conference_**<br>**_Proceedings,_**<br>**June**<br>**1994.**|
|**[RPG+96]**|**P. Reiher,**<br>**J. Popek,**<br>**h4. Gunter,**<br>**J. Sdomone,**<br>**and**<br>**D. htner.**<br>**Peer-t&peer**<br>**reconciliation**<br>**based**<br>**replica-**<br>**tion**<br>**for mobile**<br>**computers.**<br>**In** **_Europmn_**<br>**_Conference_**<br>**_on_**<br>**_Object_**<br>**_On-entsd_**<br>**_Programming_**<br>**_’96 Second_**<br>**_Work-_**<br>**_shop_**<br>**_on Mobility_**<br>**_and Replication,_**<br>**June**<br>**1996.**|
|**[Sch96]**|**Stu Schwartz.**<br>**The**<br>**Briefcas~in**<br>**brief.**<br>**_Windows_**<br>**_95_**<br>**_Professional,_**<br>**May 1996. http: //www.cobb.com/!v9p/**<br>**9605/w9p9651.htm.**|
|**[TTP+95]**|**Douglas**<br>**B.**<br>**Terry,**<br>**Marvin**<br>**M.**<br>**Theimer,**<br>**Karin**<br>**P&**<br>**tersen,**<br>**Alan J. Demers,**<br>**Mike J. Spreitzer,**<br>**and Carl H.**<br>**Hauser.**<br>**Managing**<br>**update**<br>**conflicts**<br>**in**<br>**Bayou,**<br>**a**<br>**weakly**<br>**connected**<br>**replicated**<br>**storage**<br>**system.**<br>**In** **_Pro-_**<br>**ceedings**<br>**_of the_**<br>**_15th_**<br>**_ACM_**<br>**_Symposium_**<br>**_on_**<br>**_Operat-_**<br>**ing** **_Systems_**<br>**_Principles_**<br>**_(SOSP-15),_**<br>**_Copper_**<br>**_Mountain_**<br>**_Resort,_**<br>**_Colorado,_**<br>**December**<br>**1995.**|



**108** 

m - --——–..,. 


