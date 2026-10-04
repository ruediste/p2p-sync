USENIX Association
Proceedings of the
5th Symposium on Operating Systems
Design and Implementation
Boston, Massachusetts, USA
December 9–11, 2002
THE ADVANCED COMPUTING SYSTEMS ASSOCIATION
© 2002 by The USENIX Association All Rights Reserved For more information about the USENIX Association:
Phone: 1 510 528 8649 FAX: 1 510 548 5738 Email: office@usenix.org WWW: http://www.usenix.org
Rights to individual papers remain with the author or the author's employer.
Permission is granted for noncommercial reproduction of the work for educational or research purposes.
This copyright notice must be included in the reproduced paper. USENIX acknowledges all trademarks herein.

Taming aggressive replication in the Pangaea wide-area file system
YasushiSaito,ChristosKaramanolis,MagnusKarlsson,andMallikMahalingam
StorageSystemsDepartment,HPLabs,PaloAlto,CA,USA
{ysaito,christos,karlsson,mmallik}@hpl.hp.com
Abstract
|     |     |     |     |     |     |     |     | Speed: Hidethewide-areanetworkinglatency;fileaccess |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
speedshouldresemblethatofalocalfilesystem.
Pangaeaisawide-areafilesystemthatsupportsdatashar- Availabilityandautonomy: Avoid depending on the
| ing among | a community     |     | of            | widely | distributed    | users. | It   | is           |     |              |       |         |      |       |
| --------- | --------------- | --- | ------------- | ------ | -------------- | ------ | ---- | ------------ | --- | ------------ | ----- | ------- | ---- | ----- |
|           |                 |     |               |        |                |        |      | availability | of  | any specific | node. | Pangaea | must | adapt |
| built on  | a symmetrically |     | decentralized |        | infrastructure |        | that |              |     |              |       |         |      |       |
automaticallytoserveradditions,removals,failuresand
| consists | of commodity |     | computers |     | provided | by  | the end |     |     |     |     |     |     |     |
| -------- | ------------ | --- | --------- | --- | -------- | --- | ------- | --- | --- | --- | --- | --- | --- | --- |
networkpartitioning.
| users. Computers |     | act | autonomously |     | to serve | data | to their |                 |     |          |     |        |           |      |
| ---------------- | --- | --- | ------------ | --- | -------- | ---- | -------- | --------------- | --- | -------- | --- | ------ | --------- | ---- |
|                  |     |     |              |     |          |      |          | Networkeconomy: |     | Minimize | the | use of | wide-area | net- |
localusers.Whenpossible,theyexchangedatawithnearby
works.Nodesarenotdistributeduniformly;somenodes
| peers to | improve | the | system’s | overall | performance, |     | avail- |     |     |     |     |     |     |     |
| -------- | ------- | --- | -------- | ------- | ------------ | --- | ------ | --- | --- | --- | --- | --- | --- | --- |
ability,andnetworkeconomy. Thisapproachisrealizedby areinthesameLAN,whereassomeothersarehalfway
|     |     |     |     |     |     |     |     | acrosstheglobe. |     | Pangaeashouldtransferdatabetween |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --------------- | --- | -------------------------------- | --- | --- | --- | --- |
aggressivelycreatingareplicaofafilewheneverandwher-
|     |     |     |     |     |     |     |     | nodes | in physical | proximity, |     | when possible, | to  | reduce |
| --- | --- | --- | --- | --- | --- | --- | --- | ----- | ----------- | ---------- | --- | -------------- | --- | ------ |
everitisaccessed.
latencyandsavenetworkbandwidth.
| This       | paper | presents    | the | design,      | implementation, |     | and        |     |     |     |     |     |     |     |
| ---------- | ----- | ----------- | --- | ------------ | --------------- | --- | ---------- | --- | --- | --- | --- | --- | --- | --- |
| evaluation | of    | the Pangaea |     | file system. | Pangaea         |     | offers ef- |     |     |     |     |     |     |     |
Wearguethatasystemshouldfollowasymbioticdesign
| ficient, | randomized |     | algorithms | to  | manage | highly | dynamic |     |     |     |     |     |     |     |
| -------- | ---------- | --- | ---------- | --- | ------ | ------ | ------- | --- | --- | --- | --- | --- | --- | --- |
toachievethesegoalsindynamic,wide-areaenvironments.
| and potentially |     | largegroups |     | offile | replicas. | It appliesop- |     |     |     |     |     |     |     |     |
| --------------- | --- | ----------- | --- | ------ | --------- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- |
Insuchasystem,eachserverfunctionsautonomouslyand
| timistic | consistency |     | semantics | to  | replica | contents, | but | it  |     |     |     |     |     |     |
| -------- | ----------- | --- | --------- | --- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- | --- |
allowsreadsandwritestoitsfilesevenwhendisconnected.
alsooffersstrongerguaranteeswhenrequiredbytheusers.
Asmorecomputersbecomeavailable,orasthesystemcon-
TheevaluationdemonstratesthatPangaeaoutperformsex-
|     |     |     |     |     |     |     |     | figuration | changes, | servers | dynamically | adapt | and | collab- |
| --- | --- | --- | --- | --- | --- | --- | --- | ---------- | -------- | ------- | ----------- | ----- | --- | ------- |
istingdistributedfilesystemsinlargeheterogeneousenvi-
|     |     |     |     |     |     |     |     | orate with | each | other, in | a way | that enhances | the | overall |
| --- | --- | --- | --- | --- | --- | --- | --- | ---------- | ---- | --------- | ----- | ------------- | --- | ------- |
ronments,typicaloftheInternetandoflargecorporatein-
performanceandavailabilityofthesystem.
tranets.
|     |     |     |     |     |     |     |     | Pangaea | realizes | symbiosis | by  | pervasive | replication. | It  |
| --- | --- | --- | --- | --- | --- | --- | --- | ------- | -------- | --------- | --- | --------- | ------------ | --- |
aggressivelycreatesareplicaofafileordirectorywhenever
|     |     |     |     |     |     |     |     | and wherever | it  | is accessed. | There | is no | single | “master” |
| --- | --- | --- | --- | --- | --- | --- | --- | ------------ | --- | ------------ | ----- | ----- | ------ | -------- |
1 Introduction
|     |     |     |     |     |     |     |     | replicaofafile. |          | Anyreplicamaybereadorwrittenatany |         |       |            |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --------------- | -------- | --------------------------------- | ------- | ----- | ---------- | --- |
|     |     |     |     |     |     |     |     | time, and       | replicas | exchange                          | updates | among | themselves | in  |
Pangaea is a wide-area file system that supports the daily apeer-to-peerfashion. Pervasivereplicationachieveshigh
storage needs of a distributed community of users. It is a performancebyservingdatafromaserverclosetothepoint
platformforad-hocdatasharing—itenablesmultinational of access, high availability by letting each server contain
corporations,distributedgroupsofcollaboratingusers,and itsworkingset,andnetworkeconomybytransferringdata
content management systems to exchange data efficiently amongclose-byreplicas. Thefollowingsectionsintroduce
usingafilesystem. twokeystrategiesusedtoimplementpervasivereplication.
| Pangaea | builds | a   | unified | file system | across | a   | federation |     |     |     |     |     |     |     |
| ------- | ------ | --- | ------- | ----------- | ------ | --- | ---------- | --- | --- | --- | --- | --- | --- | --- |
of up to thousands of widely distributed computers con- 1.1 Graph-basedreplicamanagement
| nected by | dedicated |     | or virtual | private | networks. |     | We cur- |     |     |     |     |     |     |     |
| --------- | --------- | --- | ---------- | ------- | --------- | --- | ------- | --- | --- | --- | --- | --- | --- | --- |
rentlyassumethatallserversaretrusted;relaxingthetrust Pangaea’s replica management must satisfy three goals.
relationship is future work. The system faces continuous First, it must support a large number of replicas, to max-
reconfiguration, with users moving, companies restructur- imize availability. Second, it needs to manage the repli-
ing,andcomputersbeingaddedorremoved.Thus,Pangaea cas of each file independently, since it is difficult to pre-
mustmeetthreekeygoals: dictfile-accesspatternsaccuratelyinawidearea. Third,it

needstosupportdynamicadditionandremovalofreplicas verylittleconcurrentwritesharing,andthatusersdemand
evenwhensomenodesarenotavailable.Pangaeaaddresses consistencyonlywithinawindowofminutes[31,35].Pan-
thesechallengesbymaintainingasparse,yetstronglycon- gaea’sactualwindowofinconsistencyisaround5seconds
nectedandrandomizedgraphofreplicasforeachfile. The inawidearea,asweshowinSection7.6. Inaddition,Pan-
graph is used both to propagate updates and to discover gaeaprovidesanoptionthatsynchronouslypushesupdates
other replicas during replica addition and removal. This toallreplicasandgivesusersconfirmationoftheirupdate
designoffersthreeimportantbenefits: delivery(Section5.3). WethusbelievethatPangaea’scon-
sistencysemanticsaresufficientforthead-hocdatasharing
| Availableandinexpensivemembershipmanagement: |     |     |     |     |     | thatPangaeatargets. |     |     |     |     |
| -------------------------------------------- | --- | --- | --- | --- | --- | ------------------- | --- | --- | --- | --- |
Areplicacanbeaddedbyconnectingtoafewlive
Pangaeadoesnotsupportapplicationsthatrequirestrong
replicas that it discovers, no matter how many other consistencysuchasopen-closeconsistency,thatuselocks,
| replicas | are unavailable. |     | Since            | the graph | is         | sparse,             |       |           |            |              |
| -------- | ---------------- | --- | ---------------- | --------- | ---------- | ------------------- | ----- | --------- | ---------- | ------------ |
|          |                  |     |                  |           |            | or that synchronize | using | directory | operations | (i.e., “lock |
| adding   | or removing      | a   | replica involves | only      | a constant |                     |       |           |            |              |
files”).
cost,regardlessofthetotalnumberofreplicas.
| Availableupdatedistribution:                     |                                       |     | Pangaea | can | distribute |           |      |     |     |     |
| ------------------------------------------------ | ------------------------------------- | --- | ------- | --- | ---------- | --------- | ---- | --- | --- | --- |
| updatestoalllivereplicasofafileasfarasitsgraphis |                                       |     |         |     |            | 2 Related | work |     |     |     |
| connected.                                       | Theredundantandflexiblenatureofgraphs |     |         |     |            |           |      |     |     |     |
makesthemextremelyunlikelytobedisconnectedeven Traditional local-area distributed file systems do not meet
aftermultiplenodeorlinkfailures.
ourgoalsofspeed,availability,andnetworkeconomy.Sys-
temssuchasxFS[2]andFrangipani[33]relyontightnode
| Networkeconomy: |     | The | random-graph | design | facilitates |     |     |     |     |     |
| --------------- | --- | --- | ------------ | ------ | ----------- | --- | --- | --- | --- | --- |
theefficientuseofwide-areanetworkbandwidth, fora coordinationforreplicamanagementandcannotovercome
system with an aggressive replication policy. Pangaea thenon-uniformnetworkinglatenciesandfrequentnetwork
partitioningthataretypicalinwide-areanetworks.
| achieves | this | by clustering | replicas | in physical | proxim- |     |     |     |     |     |
| -------- | ---- | ------------- | -------- | ----------- | ------- | --- | --- | --- | --- | --- |
itytightlyinthegraph, andbycreatingaspanningtree Pervasive replication resembles the persistent caching
along faster edges dynamically during update propaga- used in client-server file systems such as AFS [13],
| tion. |     |     |     |     |     | Coda[20],andLBFS[21].               |     | Pangaea,however,canharness |                     |                 |
| ----- | --- | --- | --- | --- | --- | ----------------------------------- | --- | -------------------------- | ------------------- | --------------- |
|       |     |     |     |     |     | nodes to improve                    | the | system’s                   | robustness          | and efficiency. |
|       |     |     |     |     |     | First,itprovidesbetteravailability. |     |                            | Whenaservercrashes, |                 |
1.2 Optimisticreplicacoordination
|     |     |     |     |     |     | there are always | other | nodes providing | access | to the files |
| --- | --- | --- | --- | --- | --- | ---------------- | ----- | --------------- | ------ | ------------ |
Adistributedservicefacestwoinherentlyconflictingchal- it hosted. Updates can be propagated to all live replicas
|              |              |     |            |                  |     | even when some | of the | servers | are unavailable. | The de- |
| ------------ | ------------ | --- | ---------- | ---------------- | --- | -------------- | ------ | ------- | ---------------- | ------- |
| lenges: high | availability |     | and strong | data consistency |     | [8,            |        |         |                  |         |
37]. Pangaeaaimsatmaximizingavailability: atanytime, centralized nature of Pangaea also allows any node to be
users must be able to read and write any replica and the removed (even permanently) transparently to users. Sec-
system must be able to create or remove replicas without ond, Pangaea improves efficiency by propagating updates
blocking. between nearby nodes, rather than between a client and a
To address this challenge, Pangaea uses two techniques fixedserverand,creatingnewreplicasfromanearbyexist-
for replica management. First, it pushes updates to repli- ing replica. In this sense, Pangaea generalizes the idea of
casratherthaninvalidatingthem,sincetheformerachieves Fluid replication [16] that utilizes surrogate Coda servers
placedinstrategic(butfixed)locationstoimprovetheper-
higheravailabilityinawideareabykeepingup-to-datedata
in more locations. This approach may result in manag- formanceandavailabilityofthesystem.
ing unnecessary replicas, wasting both storage space and Pangaea’s replication follows an optimistic approach
networking bandwidth. To ameliorate this problem, Pan- similartothatofmobiledata-sharingservices,suchasLo-
gaea lets each node remove inactive replicas, as discussed tus Notes [15], TSAE [10], Bayou [32], and Roam [25].
inSection4.4. These systems lack replica location management and rely
Second, Pangaea manages replica contents optimisti- on polling, usually by humans, to discover and exchange
cally. Itletsanynodeissueupdatesatanytime,propagates updates between replicas. Pangaea keeps track of repli-
themamongreplicasinthebackground,anddetectsandre- cas automatically and distributes updates proactively and
solvesconflictsaftertheyhappen. Thus,Pangaeasupports transparentlytoalltheusers. Mostofthesesystemsrepli-
only “eventual” consistency, guaranteeing that a user sees cateatthegranularityofthewholedatabase(exceptRoam,
achangemadebyanotheruserinsomeunspecifiedfuture whichsupportssubsetreplicas).Incontrast,Pangaea’sfiles
time. Recentstudies,however,revealthatfilesystemsface anddirectoriesarereplicatedindependently,andsomeofits

operations(e.g.,“rename”)affectmultiplefiles,eachrepli- 3 Pangaea: a structural overview
| catedonadifferentsetofnodes. |     |     |     | Suchoperationsdemand |     |     |     |     |     |     |     |     |     |
| ---------------------------- | --- | --- | --- | -------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
a new protocol for ensuring consistent outcome after con- Thissectionoverviewsthestructureofaserverandthema-
|            |            |     |         |              |        |        | jordatastructuresitmaintains. |     |     |     | Pangaea’sdesignfollowsa |     |     |
| ---------- | ---------- | --- | ------- | ------------ | ------ | ------ | ----------------------------- | --- | --- | --- | ----------------------- | --- | --- |
| flicts, as | we discuss | in  | Section | 5.2. Pangaea | offers | a sim- |                               |     |     |     |                         |     |     |
ple conflict resolution policy similar to that of Roam, Lo- symmetricallydistributedapproach.APangaeaserverhan-
cus[36],orCoda[18]. Wechosethisdesignovermoreso- dlesfile-accessrequestsfromusers. Weassumethatauser
|     |     |     |     |     |     |     | uses a single | server | during | a log-in | session |     | (lasting, say, |
| --- | --- | --- | --- | --- | --- | --- | ------------- | ------ | ------ | -------- | ------- | --- | -------------- |
phisticatedapproaches(asinBayou),becausePangaeacan
make no assumptions about the semantics of file-system afewhours), sothaton-demandreplicationimprovesfile-
operations. access latency; the user may move between servers over
|     |     |     |     |     |     |     | time. Eachservermaintainslocalharddisks,usedtostore |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
FARSITE [1] and Pangaea both build a unified file sys- replicasoffilesanddirectories. Serversinteractwitheach
|                                                       |              |         |           |           |                |           | other in          | a peer-to-peer |     | fashion   | to provide       | a   | unified file- |
| ----------------------------------------------------- | ------------ | ------- | --------- | --------- | -------------- | --------- | ----------------- | -------------- | --- | --------- | ---------------- | --- | ------------- |
| tem across                                            | a federation |         | of nodes, | but       | they have      | different |                   |                |     |           |                  |     |               |
| objectives.                                           | FARSITE’s    |         | goal is   | to build  | a reliable     | service   | system.           |                |     |           |                  |     |               |
| on top of                                             | untrusted    | nodes   | using     | Byzantine | consensus      | pro-      |                   |                |     |           |                  |     |               |
| tocols,anditisdesignedprimarilyforlocal-areanetworks. |              |         |           |           |                |           | 3.1 Definitions   |                |     |           |                  |     |               |
| Pangaea                                               | assumes      | trusted | servers,  | but       | it dynamically | repli-    |                   |                |     |           |                  |     |               |
|                                                       |              |         |           |           |                |           | Weusethetermsnode |                |     | andserver | interchangeably. |     | Nodes         |
catesfilesattheedgetominimizetheuseofwide-areanet-
|     |     |     |     |     |     |     | are automatically |     | grouped | into | regions, | such | that nodes |
| --- | --- | --- | --- | --- | --- | --- | ----------------- | --- | ------- | ---- | -------- | ---- | ---------- |
works.
|     |     |     |     |     |     |     | within a                       | region have | low | round-trip | times             | (RTT) | between |
| --- | --- | --- | --- | --- | --- | --- | ------------------------------ | ----------- | --- | ---------- | ----------------- | ----- | ------- |
|     |     |     |     |     |     |     | them(<5msinourimplementation). |             |     |            | Pangaeausesregion |       |         |
Recentpeer-to-peerdatasharingsystems,builtontopof
|                |     |                |             |     |              |       | information | to optimize |     | replica | placement | and | coordina- |
| -------------- | --- | -------------- | ----------- | --- | ------------ | ----- | ----------- | ----------- | --- | ------- | --------- | --- | --------- |
| load-balanced, |     | fault-tolerant | distributed |     | hash tables, | share |             |             |     |         |           |     |           |
tion.
| many properties |     | with Pangaea. |     | Systems | such | as CFS [6] |         |            |      |        |             |     |              |
| --------------- | --- | ------------- | --- | ------- | ---- | ---------- | ------- | ---------- | ---- | ------ | ----------- | --- | ------------ |
|                 |     |               |     |         |      |            | Pangaea | replicates | data | at the | granularity |     | of files and |
andPAST[27]employheuristicstoexploitphysicalprox-
|            |          |         |                   |          |             |           | treats directories                                |     | as files                           | with special | contents. |     | Thus, we    |
| ---------- | -------- | ------- | ----------------- | -------- | ----------- | --------- | ------------------------------------------------- | --- | ---------------------------------- | ------------ | --------- | --- | ----------- |
| imity when | locating | data,   | but               | they do  | not support | con-      |                                                   |     |                                    |              |           |     |             |
|            |          |         |                   |          |             |           | usethetermfile                                    |     | torefertoaregularfileoradirectory. |              |           |     | An          |
| current    | in-place | updates | of hierarchically |          | structured  | data.     |                                                   |     |                                    |              |           |     |             |
|            |          |         |                   |          |             |           | edge representsaknownconnectionbetweentworeplicas |     |                                    |              |           |     |             |
| Pangaea,   | unlike   | these   | systems,          | provides | extra       | machinery |                                                   |     |                                    |              |           |     |             |
|            |          |         |                   |          |             |           | ofafile;updatestothefileflowalongedges.           |     |                                    |              |           |     | Thereplicas |
forconflictdetectionandresolution,aswediscussinSec-
|             |            |         |         |         |             |        | of a file | and the | edges   | between     | them comprise |        | a strongly    |
| ----------- | ---------- | ------- | ------- | ------- | ----------- | ------ | --------- | ------- | ------- | ----------- | ------------- | ------ | ------------- |
| tion 5.2.   | Oceanstore | [17]    | builds  | a file  | system with | strong |           |         |         |             |               |        |               |
|             |            |         |         |         |             |        | connected | graph.  | The set | of replicas | of            | a file | is called the |
| consistency | by         | routing | updates | through | a small     | “core” | of        |         |         |             |               |        |               |
file’sreplicaset.
replicas. Pangaea,instead,allowsin-placeupdatingofany
replicawithoutcentralizedcoordinationtomaximizeavail-
|     |     |     |     |     |     |     | 3.2 Structureofaserver |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------------------- | --- | --- | --- | --- | --- | --- |
ability.Ivy[22]isapeer-to-peerfilesystemthatletsdatabe
updated,anytime,anywhere,byexchangingoperationlogs
|     |     |     |     |     |     |     | The Pangaea | server | is  | currently | implemented |     | as a user- |
| --- | --- | --- | --- | --- | --- | --- | ----------- | ------ | --- | --------- | ----------- | --- | ---------- |
betweenreplicas.Becausethereplicaspollremotelogsfre-
|          |                                           |     |     |     |     |     | space NFSv3 | loopback |     | server (Figure | 1). | The | server con- |
| -------- | ----------------------------------------- | --- | --- | --- | --- | --- | ----------- | -------- | --- | -------------- | --- | --- | ----------- |
| quently, | itsupportsstrongerconsistencythanPangaea. |     |     |     |     |     | Its         |          |     |                |     |     |             |
sistsoffourmainmodules:
| log-based                            | update | propagation |     | also allows | for more | versa-  |                    |     |     |          |          |      |          |
| ------------------------------------ | ------ | ----------- | --- | ----------- | -------- | ------- | ------------------ | --- | --- | -------- | -------- | ---- | -------- |
| tileconflictresolutionthaninPangaea. |        |             |     |             | However, | because |                    |     |     |          |          |      |          |
|                                      |        |             |     |             |          |         | NFSprotocolhandler |     |     | receives | requests | from | applica- |
Ivyforceseachusertoreadthelogsofallotherwriters,it
|     |     |     |     |     |     |     | tions, | updates | local | replicas, | and | generates | requests |
| --- | --- | --- | --- | --- | --- | --- | ------ | ------- | ----- | --------- | --- | --------- | -------- |
canonlysupportasmallfilesystemwithasmallnumberof
|     |     |     |     |     |     |     | for the | replication | engine. |     | It is built | using | the SFS |
| --- | --- | --- | --- | --- | --- | --- | ------- | ----------- | ------- | --- | ----------- | ----- | ------- |
writers.
toolkit[19]thatprovidesabasicinfrastructureforNFS
requestparsingandeventdispatching.
| A number | of  | companies | are | active | in the field | of wide- |                   |     |                                 |     |     |     |     |
| -------- | --- | --------- | --- | ------ | ------------ | -------- | ----------------- | --- | ------------------------------- | --- | --- | --- | --- |
|          |     |           |     |        |              |          | Replicationengine |     | acceptsrequestsfromtheNFSproto- |     |     |     |     |
areacollaborativedatasharing,includingFileFish,Scale8,
WebFS,andXythos. Theyofferauniform,seamlessinter- colhandlerandthereplicationenginerunningonother
|                 |         |           |             |          |             |         | nodes.   | It creates, | modifies, |             | or removes |            | replicas, and |
| --------------- | ------- | --------- | ----------- | -------- | ----------- | ------- | -------- | ----------- | --------- | ----------- | ---------- | ---------- | ------------- |
| face for        | sharing | files in  | a wide-area | network, | independent |         |          |             |           |             |            |            |               |
|                 |         |           |             |          |             |         | forwards | requests    | to        | other nodes | if         | necessary. | It is the     |
| of the physical |         | locations | of users    | and      | data. Some  | of them |          |             |           |             |            |            |               |
provide features such as intelligent location of the cached largestpartofthePangaeaserver.
copyclosesttotheuser. However,theyalluseacentralized Logmodule implementstransaction-likesemanticsforlo-
databasetokeeptrackofthelocationoffilesandreplicas. cal disk updates via redo logging. The server logs all
Thus, theirdesigndoesnotmeetPangaea’sgoalsofavail- the replica-update operations using this service, allow-
| abilityandautonomy. |     |     |     |     |     |     | ingthemtosurvivecrashes. |     |     |     |     |     |     |
| ------------------- | --- | --- | --- | --- | --- | --- | ------------------------ | --- | --- | --- | --- | --- | --- |

(cid:18)MRH
| I/O request |     |     |     |     |     |     |     |     |     |     | SHHU(cid:3)HGJH |     |     |
| ----------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --------------- | --- | --- |
Pangaea
| (application) |     | NFS protocol |     |     |        |     |     |     |     |     |                      |     |     |
| ------------- | --- | ------------ | --- | --- | ------ | --- | --- | --- | --- | --- | -------------------- | --- | --- |
|               |     |              |     |     | server |     |     |     |     |     | EURQ]H(cid:3)UHSOLFD |     |     |
handler
|     |     |     | replication |     | log        |     |     |     |     |     | JROG(cid:3)UHSOLFD |     |     |
| --- | --- | --- | ----------- | --- | ---------- | --- | --- | --- | --- | --- | ------------------ | --- | --- |
|     |     |     | engine      |     | membership |     |     |     |     |     | GRZQOLQNV          |     |     |
User space
EDFNSRLQWHU
Kernel
|     |     | NFS client |     |     | Inter-node |     |     |     |     |     |     |     |     |
| --- | --- | ---------- | --- | --- | ---------- | --- | --- | --- | --- | --- | --- | --- | --- |
communication
(cid:18)MRH(cid:18)IRR
Figure1:
ThestructureofthePangaeaserver.
|                  |     |                                 |     |     |     |     | Figure2: | Anexampleofadirectory/joeandfile/joe/foo. |     |     |     |     |     |
| ---------------- | --- | ------------------------------- | --- | --- | --- | --- | -------- | ----------------------------------------- | --- | --- | --- | --- | --- |
| Membershipmodule |     | maintainsthestatusofothernodes, |     |     |     |     |          |                                           |     |     |     |     |     |
Eachreplicaofjoestoresthreepointerstothegoldreplicasof
| including | their | liveness, | available | disk | space, | the | loca-                                                |     |     |     |     |     |     |
| --------- | ----- | --------- | --------- | ---- | ------ | --- | ---------------------------------------------------- | --- | --- | --- | --- | --- | --- |
|           |       |           |           |      |        |     | foo. Eachreplicaoffookeepsabackpointertotheparentdi- |     |     |     |     |     |     |
tionsofroot-directoryreplicas,thelistofregionsinthe rectory.Bronzereplicasareconnectedrandomlytoformstrongly
system,thesetofnodesineachregion,andaround-trip connectedgraphs. Bronzereplicasalsohaveuni-directionallinks
time(RTT)estimatebetweeneverypairofregions.
tothegoldreplicasofthefile,whicharenotshownhere.
ThismodulerunsanextensionofvanRenesse’sgossip-
| based protocol |           | [34]. Each | node   | periodically |     | sends | its       |               |               |     |              |         |             |
| -------------- | --------- | ---------- | ------ | ------------ | --- | ----- | --------- | ------------- | ------------- | --- | ------------ | ------- | ----------- |
|                |           |            |        |              |     |       | this end, | the directory | entry         | of  | a file lists | the     | file’s gold |
| knowledge      | of nodes’ |            | status | to a random  |     | node  | chosen    |               |               |     |              |         |             |
|                |           |            |        |              |     |       | replicas. | Second,       | gold replicas |     | perform      | several | tasks that  |
fromitslive-nodelist;therecipientmergesthislistwith arehardtodoinacompletelydistributedway. Inparticu-
| its own. | A few | fixed | nodes | are designated |     | as  | “land- |     |     |     |     |     |     |
| -------- | ----- | ----- | ----- | -------------- | --- | --- | ------ | --- | --- | --- | --- | --- | --- |
lar,theyareusedaspivotstokeepthegraphconnectedafter
marks” and they bootstrap newly joining nodes. The apermanentnodefailure,andtomaintainaminimumrepli-
protocolhasbeenshowntodisseminatemembershipin-
cationfactorforafile.Theyformacliqueinthefile’sgraph
| formation | quickly | with | low probability |     | of  | false | failure                                     |     |     |     |     |     |       |
| --------- | ------- | ---- | --------------- | --- | --- | ----- | ------------------------------------------- | --- | --- | --- | --- | --- | ----- |
|           |         |      |                 |     |     |       | sothattheycanmonitoreachotherforthesetasks. |     |     |     |     |     | These |
detection. issuesarediscussedinmoredetailinSection4. Currently,
| The region                | and | RTT information |     | is                  | gossiped | as  | part of |            |          |         |        |         |           |
| ------------------------- | --- | --------------- | --- | ------------------- | -------- | --- | ------- | ---------- | -------- | ------- | ------ | ------- | --------- |
|                           |     |                 |     |                     |          |     | Pangaea | designates | replicas | created | during | initial | file cre- |
| themembershipinformation. |     |                 |     | Anewlybootednodeob- |          |     |         |            |          |         |        |         |           |
ationasgoldandfixestheirlocationsunlesssomeofthem
| tains the | region | information |     | from a | landmark. |     | It then failpermanently. |     |     |     |     |     |     |
| --------- | ------ | ----------- | --- | ------ | --------- | --- | ------------------------ | --- | --- | --- | --- | --- | --- |
pollsanodeineachexistingregiontodeterminewhere
|     |     |     |     |     |     |     | Eachreplicastoresabackpointer |     |     |     | thatindicatesitsloca- |     |     |
| --- | --- | --- | --- | --- | --- | --- | ----------------------------- | --- | --- | --- | --------------------- | --- | --- |
itbelongsortocreateanewsingletonregion.Ineachre-
|     |     |     |     |     |     |     | tioninthefile-systemnamespace. |     |     |     | Abackpointerincludes |     |     |
| --- | --- | --- | --- | --- | --- | --- | ------------------------------ | --- | --- | --- | -------------------- | --- | --- |
gion, thenodewiththesmallestIPaddresselectsitself
theparentdirectory’sIDandthefile’snamewithinthedi-
asaleaderandperiodicallypingsnodesinotherregions
|     |     |     |     |     |     |     | rectory.1 | Itisusedfortwopurposes: |     |     | toresolveconflicting |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------- | ----------------------- | --- | --- | -------------------- | --- | --- |
tomeasuretheRTT. directory operations (Section 5.2), and to keep the direc-
| This membership-tracking |     |     | scheme, |             | especially | the | RTT        |            |      |     |              |     |             |
| ------------------------ | --- | --- | ------- | ----------- | ---------- | --- | ---------- | ---------- | ---- | --- | ------------ | --- | ----------- |
|                          |     |     |         |             |            |     | tory entry | up-to-date | when | the | gold replica | set | of the file |
| management,              | is  | the | key     | scalability | bottleneck |     | in         |            |      |     |              |     |             |
changes(Section6.2).
| our system—its |     | network | bandwidth |     | consumption |     | in  |     |     |     |     |     |     |
| -------------- | --- | ------- | --------- | --- | ----------- | --- | --- | --- | --- | --- | --- | --- | --- |
Figure3showsthekeyattributesofareplica.Thetimes-
| a 10,000-node      |           | configuration |           | is estimated |              | to be | 10K                     |         |              |                           |                 |        |          |
| ------------------ | --------- | ------------- | --------- | ------------ | ------------ | ----- | ----------------------- | ------- | ------------ | ------------------------- | --------------- | ------ | -------- |
|                    |           |               |           |              |              |       | tamp (ts)               | and the | version      | vector                    | (vv) [23]       | record | the last |
| bytes/second/node. |           | We            | plan      | to           | use external |       | RTT-                    |         |              |                           |                 |        |          |
|                    |           |               |           |              |              |       | timethefilewasmodified. |         |              | Theiruseisdescribedinmore |                 |        |          |
| estimation         | services, | such          | as IDMaps |              | [9], once    | they  | be-                     |         |              |                           |                 |        |          |
|                    |           |               |           |              |              |       | detail in               | Section | 5. GoldPeers | are                       | uni-directional |        | links to |
comewidelyavailable.
|     |     |     |     |     |     |     | thegoldreplicasofthefile. |     |     | Peerspointtotheneighboring |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ------------------------- | --- | --- | -------------------------- | --- | --- | --- |
(goldorbronze)replicasinthefile’sgraph.
3.3 Structureofafilesystem
|     |     |     |     |     |     |     | 4 Replica |     | set management |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------- | --- | -------------- | --- | --- | --- | --- |
Pangaeadecentralizesboththereplica-setandconsistency
managementbymaintainingadistributedgraphofreplicas
foreachfile. Figure2showsanexampleofasystemwith In Pangaea, a replica is created when a user first accesses
twofiles. Pangaeadistinguishestwotypesofreplicas: gold the file, and it is removed when a node runs out of disk
andbronze. Theycanbothbereadandwrittenbyusersat spaceorfindsareplicatobeinactive. Becausetheseoper-
anytime,andtheybothrunanidenticalupdate-propagation ationsarefrequent,theymustbecarriedoutefficientlyand
protocol. Goldreplicas,however,playanadditionalrolein
1Areplicastoresmultiplebackpointerswhenthefileishard-linked.
maintainingthehierarchicalnamespace.
|     |     |     |     |     |     |     | A backpointer | need | not remember | the | locations | of the | parent-directory |
| --- | --- | --- | --- | --- | --- | --- | ------------- | ---- | ------------ | --- | --------- | ------ | ---------------- |
First, gold replicas act as starting points from which replicas,sinceaparentdirectoryisalwaysfoundonthesamenodedueto
bronze replicas are found during path-name traversal. To thenamespace-containmentproperty(Section4.3).

|     |     |     |     |     |     | S must | replicate | the file’s | parent | directory. | This | recursive |     |
| --- | --- | --- | --- | --- | --- | ------ | --------- | ---------- | ------ | ---------- | ---- | --------- | --- |
structReplica
| fid:FileID   |     | //96bitgloballyuniquefileID     |     |     |     |                                               |     |     |     |     |     |     |     |
| ------------ | --- | ------------------------------- | --- | --- | --- | --------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
|              |     |                                 |     |     |     | stepmaycontinueallthewayuptotherootdirectory. |     |     |     |     |     |     | The |
| ts:TimeStamp |     | //Pairofhphysicalclock,IPaddri. |     |     |     |                                               |     |     |     |     |     |     |     |
locationsofrootreplicasaremaintainedbythemembership
| vv:VersionVector                        |     | //MapsIPaddr7→TimeStamp |     |     |     |                      |     |     |     |     |     |     |     |
| --------------------------------------- | --- | ----------------------- | --- | --- | --- | -------------------- | --- | --- | --- | --- | --- | --- | --- |
| goldPeers:SethNodeIDi//SetofIPaddresses |     |                         |     |     |     | service(Section3.2). |     |     |     |     |     |     |     |
peers:SethNodeIDi Pangaeaperformsashort-cut replicacreationtotransfer
backptrs:SethFileID,Stringi//PairofhdirID,fnamei
|     |     |     |     |     |     | data from | a nearby | existing | replica. |     | To create | a replica | of  |
| --- | --- | --- | --- | --- | --- | --------- | -------- | -------- | -------- | --- | --------- | --------- | --- |
...
end F, S first discovers the file’s gold replicas in the directory
structDirEntry entryduringthepath-namelookup. Sthenrequeststhefile
fname:String
| fid:FileID |     |     |     |     |     | contentsfromthegoldreplicaclosesttoS(sayP). |     |     |     |     |     |     | Pthen |
| ---------- | --- | --- | --- | --- | --- | ------------------------------------------- | --- | --- | --- | --- | --- | --- | ----- |
downlinks:SethNodeIDi findsareplicaclosesttoSamongitsowngraphneighbors
| ts:Timestamp |     |     |     |     |     | (sayX,whichmaybePitself)andforwardstherequestto |     |     |     |     |     |     |     |
| ------------ | --- | --- | --- | --- | --- | ----------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
end
|     |     |     |     |     |     | X, which | in turn | sends | the contents |     | to S. At | this point, | S   |
| --- | --- | --- | --- | --- | --- | -------- | ------- | ----- | ------------ | --- | -------- | ----------- | --- |
Figure3:
Keyattributesofareplica. replies to the user and lets her start accessing the replica.
Thisrequestforwardingisperformedbecausethedirectory
withoutblocking,evenwhensomenodesthatstorereplicas only knows F’s gold replicas, and there may be a bronze
areunavailable.Thissectiondescribesalgorithmsbasedon replicaclosertoPthanthegoldones.
randomwalksthatachievethesegoals. The new copy must be integrated into the file’s replica
graphtobeabletopropagateupdatestoandreceiveupdates
|     |     |     |     |     |     | fromotherreplicas. |     | Thus,inthebackground,Schoosesm |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | ------------------ | --- | ------------------------------ | --- | --- | --- | --- | --- |
4.1 Filecreation
|     |     |     |     |     |     | existing | replicas | of F, | adds | edges to | them, | and requests |     |
| --- | --- | --- | --- | --- | --- | -------- | -------- | ----- | ---- | -------- | ----- | ------------ | --- |
We describe the interactions between the modules of the themtoaddedgestothenewreplicainS. Theselectionof
mpeersmustsatisfythreegoals:
systemandtheuseofvariousdatastructuresusingasimple
| scenario—auseronserverScreatesfileF |     |     |     | indirectoryD. |     |     |     |     |     |     |     |     |     |
| ----------------------------------- | --- | --- | --- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
For the moment, assume that S already stores a replica • Includegoldreplicassothattheyhavemorechoicesdur-
ofD(ifnot,Screatesone,usingtheprotocoldescribedin ingfutureshort-cutreplicacreation.
| Section  | 4.2.) First, | S determines | the location | of       | g initial |                                                    |     |     |     |     |     |     |     |
| -------- | ------------ | ------------ | ------------ | -------- | --------- | -------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
|          |              |              |              |          |           | • Includenearbyreplicassothatupdatescanflowthrough |     |     |     |     |     |     |     |
| replicas | of F, which  | will become  | the gold     | replicas | of the    |                                                    |     |     |     |     |     |     |     |
fastnetworklinks.
| file(atypicalvalueforgis3). |     |     | OnereplicawillresideonS. |     |     |     |     |     |     |     |     |     |     |
| --------------------------- | --- | --- | ------------------------ | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
Theotherg−1replicasarechosenatrandomfromdifferent • Be sufficiently randomized so that, with high probabil-
regions in the system to improve the expected availability ity,thecrashofnodesdoesnotcatastrophicallydiscon-
of the file. Second, S creates the local replica for F and nectthefile’sgraph.
| addsanentryforF |     | inthelocalreplicaofD. |     | Sthenreplies |     |     |     |     |     |     |     |     |     |
| --------------- | --- | --------------------- | --- | ------------ | --- | --- | --- | --- | --- | --- | --- | --- | --- |
totheclient,andtheclientcanstartaccessingthefile. Pangaea satisfies all these goals simultaneously, as a
Inthebackground,Sdisseminatestwotypesofupdates.
|     |     |     |     |     |     | replica | can have | multiple | edges. | S chooses |     | three types | of  |
| --- | --- | --- | --- | --- | --- | ------- | -------- | -------- | ------ | --------- | --- | ----------- | --- |
It first “floods” the new directory contents to other direc- peersforthenewreplica. First,Saddsanedgetoarandom
tory replicas. It also floods the contents of F (which is goldreplica,preferablyonefromadifferentregionthanS,
| empty, save | for | attributes such | as permissions | and | owner) |     |     |     |     |     |     |     |     |
| ----------- | --- | --------------- | -------------- | --- | ------ | --- | --- | --- | --- | --- | --- | --- | --- |
togivethatgoldreplicamorevarietyofregionsinitsneigh-
toitsgold-replicanodes.Inpractice,aswedescribeinSec- borset.Second,itasksarandomgoldreplica,sayP,topick
tion5,wedeployseveraltechniquestoreducetheoverhead thereplica(amongP’simmediategraphneighbors)closest
of flooding dramatically. As a side effect of the propaga- toS. Third, S asksPtochoosem−2randomreplicasus-
tion, the replicas of D will point to F’s gold replicas so ingrandomwalksthatstartfromPandperformaseriesof
that the latter can be discovered during future path-name RPCcallsalonggraphedges.Thisprotocolensuresthatthe
lookups. resulting graph is m edge- and node- connected, provided
thatitwasm-connectedbefore.
4.2 Replicaaddition Parameter m trades off availability and performance. A
|     |     |     |     |     |     | small value | increases |     | the probability |     | of graph | disconnec- |     |
| --- | --- | --- | --- | --- | --- | ----------- | --------- | --- | --------------- | --- | -------- | ---------- | --- |
Theprotocolforcreatingadditionalreplicasforafileisrun tion(i.e.,theprobabilitythatareplicacannotexchangeup-
when a user tries to access a file not present in her local dateswithotherreplicas)afternodefailures. Alargevalue
node. SaythatauseronnodeSwantstoreadfileF. Aread formincreasestheoverheadofgraphmaintenanceandup-
or write request is always preceded by a directory lookup datepropagationbycausingduplicateupdatedelivery. We
(duringtheopenrequest)onS. Thus, tocreateareplica, foundthatm=4offersagoodbalanceinourprototype.

4.3 Name-spacecontainment To remove a replica, the server sends notices to the
replica’sgraphneighbors.Eachneighbor,inturn,initiatesa
The procedures for file creation and replica addition both randomwalkstartingfromarandomgoldreplica3anduses
require a file’s parent directory to be present on the same
theprotocoldescribedinSection4.2toestablishareplace-
node.Pangaea,infact,demandsthatforeveryfile,allinter-
mentedgewithanotherlivereplica. Startingthewalkfrom
mediatedirectories,uptotheroot,arealwaysreplicatedon
alivegoldreplicaensuresthatthegraphremainsstrongly
thesamenode. Thisname-space-containment requirement
connected.Asimilarprotocolrunswhenanodedetectsan-
yields two benefits. First, it naturally offers the availabil-
othernode’spermanentdeath,aswedescribeinSection6.
ityandautonomybenefitsofisland-basedreplication[14].
Thatis, itenableslookupandaccesstoeveryreplicaeven
4.5 Summary
when the server is disconnected and allows each node to
takeabackupofthefilesystemlocally. Wequantifythese
Thegraph-basedpervasivereplicationalgorithmsdescribed
benefits in Section 7.8. Second, it simplifies the conflict
in this section offer some fundamental benefits over tradi-
resolution of directory operations, as we discuss in Sec-
tional approaches that have a fixed set of servers manage
tion5.2.
replicalocations.
Ontheotherhand,thisrequirementincreasesthesystem-
wide storage overhead by 1.5% to 25%, compared to an
Simpleandefficientrecoveryfromfailures: Graphs
idealized scheme in which directories are stored on only
are, by definition, flexible—spanning edges to any
one node [28].2 We consider the overhead to be reason-
replicamakesthegraphincrementallymorerobustand
able,asusersalreadypaymanytimesmorestoragecostby
efficient. Moreover, using just one type of edges both
replicatingfilesinthefirstplace.
to locate replicas and to propagate updates simplifies
the recovery from permanent failures and avoids any
4.4 Bronzereplicaremoval systemdisruptionduringgraphreconfiguration.
Decouplingofdirectoriesandfiles: Directory entries
This section describes the protocol for removing bronze
pointonlytogoldreplicas,andthesetofgoldreplicasis
replicas. Gold replicas are removed only as a side effect
typicallystable. Thus,afileanditsparentdirectoryact
ofapermanentnodeloss. Wediscussthehandlingofper-
mostlyindependentlyoncethefileiscreated. Addingor
manentfailuresinSection6.
removingabronzereplicaforthefiledoesnotrequirea
A replica is removed for two possible reasons: because
changetothedirectoryreplicas. Addingorremovinga
a node has run out of disk space, or the cost of keeping
goldorbronzereplicaforthedirectorydoesnotrequire
the replica outweighs its benefits. To reclaim disk space,
a change to the file replicas. These are key properties
Pangaea uses a randomized GD-Size algorithm [24]. We
forthesystem’sefficiency.
examine50randomreplicaskeptinthenodeandcalculate
theirmeritvaluesusingtheGD-Sizefunctionthatconsiders
both the replica’s size and the last-access time [5]. The 5 Propagating updates
replicawiththeminimummeritisevicted,andfivereplicas
withthenext-worstmeritvaluesareaddedtothecandidates This section describes Pangaea’s solutions to three chal-
examinedduringthenextround. Thealgorithmisrepeated lenges posed by optimistic replication: efficient and reli-
untilitfreesenoughspaceonthedisk. ableupdatepropagation,handlingconcurrentupdates,and
Optionally, a server can also reclaim replicas not worth thelackofstrongconsistencyguarantees.
keeping. Wecurrentlyuseacompetitiveupdatesalgorithm
for this purpose [12]. Here, the server keeps a per-replica
5.1 Efficientupdateflooding
counter that is incremented every time a replica receives
a remote update and is reset to zero when the replica is The basic method for propagating updates in Pangaea is
read. When the counter’s value exceeds a threshold (4 in flooding alonggraphedges, asshowninFigure4. When-
ourprototype),theserverevictsthereplica. everareplicaismodifiedonaserver,theserverpushesthe
entirefilecontentstoallthegraphneighbors,whichinturn
2Duetothelackofwide-areafilesystemtraces,weanalyzedthestor-
forwardthecontentstotheirneighbors,andsoon,untilall
age overhead using a fresh file system with RedHat 7.3 installed. The
overheadmainlydependsonthespatiallocalityofaccesses,i.e.,thede- thereplicasreceivethenewcontents. Thissimpleflooding
greetowhichfilesinthesamedirectoryareaccessedtogether.Weexpect
theoverheadinpracticetobemuchcloserto1.5%than25%, because 3Thegold-replicasetiskeptasapartofthereplica’sattributes; see
spatiallocalityintypicalfile-systemtracesisusuallyhigh. Figure3.

|     |     |     |     |     |     | step of the | algorithm. |     | Thus, it consumes |     | m times | the op- |
| --- | --- | --- | --- | --- | --- | ----------- | ---------- | --- | ----------------- | --- | ------- | ------- |
r:Replicabeingupdated.
whenUpdateisnewlyissued
timalnetworkbandwidth,wheremisthenumberofedges
Loghr.fid,r.vvi.
perreplica.Harbingerseliminateredundantupdatedeliver-
| Sendhr.fid,r.vv,r.dataitonodesin            |     |     |     | r.peers |     |      |     |     |     |     |     |     |
| ------------------------------------------- | --- | --- | --- | ------- | --- | ---- | --- | --- | --- | --- | --- | --- |
| Unloghr.fid,r.vviafteralltheneighborsreply. |     |     |     |         |     | ies. |     |     |     |     |     |     |
whenUpdatehfid,vv,dataiisreceivedfromnoden.
Pangaeausesatwo-phaseprotocoltopropagateupdates
ifthisupdatehasalreadybeenappliedthenReplyton
elseLogandapplytheupdate. thatexceedacertainsize(1KB).Inphaseone,asmallmes-
|     | Replyton |     |     |     |     | sagethatonlycontainsthetimestampsoftheupdate,called |     |     |     |     |     |     |
| --- | -------- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
Forwardtheupdatetor.peers-{n}. aharbinger,isfloodedalonggraphedges. Theupdatebod-
Unlogafteralltheneighborsreply.
|     |     |     |     |     |     | ies are sent, | in  | phase two, | only | when requested |     | by other |
| --- | --- | --- | --- | --- | --- | ------------- | --- | ---------- | ---- | -------------- | --- | -------- |
Figure 4: A simple flooding algorithm to distribute updates. nodes. When a node receives a new harbinger, it asks the
Thiscodeassumesthatupdatesareissuedoneatatime;thehan- senderoftheharbinger(theimmediateupstreamreplicain
dlingofconcurrentupdatesisdiscussedinSection5.2.
|     |     |     |     |     |     | the flooding | chain)   | to            | push the | update body. | Simultane- |        |
| --- | --- | --- | --- | --- | --- | ------------ | -------- | ------------- | -------- | ------------ | ---------- | ------ |
|     |     |     |     |     |     | ously, it    | forwards | the harbinger |          | to other     | neighbors  | in the |
algorithmguaranteesreliableupdatedeliveryaslongasthe graph. When a node receives a duplicate harbinger with-
replica graph is strongly connected. The following three out having received the update body, it asks its sender to
sections introduce techniques for improving the efficiency retry later. This is required because the sender of the ear-
ofthebasicfloodingalgorithm.
|     |     |     |     |     |     | liest harbinger |     | may crash | before | sending | the update | body. |
| --- | --- | --- | --- | --- | --- | --------------- | --- | --------- | ------ | ------- | ---------- | ----- |
Ifanodereceivesaharbingerafterhavingreceivedtheup-
|                      |     |     |                  |     |     | datebody,ittellsthesendertostopsendingtheupdate. |           |           |     |              |     | We       |
| -------------------- | --- | --- | ---------------- | --- | --- | ------------------------------------------------ | --------- | --------- | --- | ------------ | --- | -------- |
| 5.1.1 Optimization1: |     |     | deltapropagation |     |     |                                                  |           |           |     |              |     |          |
|                      |     |     |                  |     |     | chose the                                        | harbinger | threshold | of  | 1KB, because |     | we found |
Amajordrawbackoffloodingisthatitpropagatestheen- that delta sizes follow a bimodal distribution—one peak
|                    |     |           |          |      |          | around 200 | bytes | representing | directory | operations, |     | and a |
| ------------------ | --- | --------- | -------- | ---- | -------- | ---------- | ----- | ------------ | --------- | ----------- | --- | ----- |
| tire file contents |     | even when | only one | byte | has been | modi-      |       |              |           |             |     |       |
flatterplateauaround20KBrepresentingbulkwrites.4
fied.Deltapropagationimprovesthepropagationefficiency
whilemaintainingthelogicalsimplicityofflooding. Here, Thisharbingeralgorithmnotonlysavesnetworkusage,
wheneveraportionofafileischanged(e.g.,addinganen- but also shrinks the effective window of replica inconsis-
try to a directory), Pangaea propagates only a small, se- tency. When a user tries to read a file for which only a
manticdescriptionofthechange,calledadelta. Deltas,in harbingerhasbeenreceived, shewaitsuntiltheactualup-
general,mustbeappliedinthesameordertoeveryreplica datearrives. Sinceharbinger-propagationdelayisindepen-
toproducethesameresult. Weensurethisbyhavingeach dent of the actual update size, the chance of a user seeing
delta carry two timestamps: the old timestamp that repre- stalefilecontentsisgreatlyreduced.
sentsthestateofthereplicajustbeforethechange,andthe
| newtimestamp                            | thatshowsthestateofthereplicaafterthe   |     |     |     |              |                      |     |     |                            |     |     |     |
| --------------------------------------- | --------------------------------------- | --- | --- | --- | ------------ | -------------------- | --- | --- | -------------------------- | --- | --- | --- |
|                                         |                                         |     |     |     |              | 5.1.3 Optimization3: |     |     | exploitingphysicaltopology |     |     |     |
| change[15].                             | Areplicaappliesadeltaonlywhenitscurrent |     |     |     |              |                      |     |     |                            |     |     |     |
| timestampmatchesthedelta’soldtimestamp. |                                         |     |     |     | Otherwise,it |                      |     |     |                            |     |     |     |
resortstofullcontentstransfer, withpotentialconflictres- Harbingershaveanotherpositivesideeffect.Theyfavorthe
useoffastlinks,becauseanoderequeststhebodyofanup-
| olutionasdescribedinSection5.2. |     |     |     | Inpractice,updatesare |     |                                                 |     |     |     |     |     |      |
| ------------------------------- | --- | --- | --- | --------------------- | --- | ----------------------------------------------- | --- | --- | --- | --- | --- | ---- |
|                                 |     |     |     |                       |     | datefromthesenderofthefirstharbingeritreceives. |     |     |     |     |     | How- |
handledalmostexclusivelybydeltas,andfull-statetransfer
ever,unpredictablenodeorlinkloadmayreducethisbene-
| happensonlywhenthereareconcurrentwrites, |     |     |     |     | orwhena |     |     |     |     |     |     |     |
| ---------------------------------------- | --- | --- | --- | --- | ------- | --- | --- | --- | --- | --- | --- | --- |
fit.Asimpleextensiontotheharbingeralgorithmimproves
noderecoversfromacrash.
Pangaea further reduces the size of updates by delta thedatapropagationefficiency,withoutrequiringanycoor-
|          |         |             |             |     |         | dinationbetweennodes. |     |     | Beforepushing(orforwarding)a |     |     |     |
| -------- | ------- | ----------- | ----------- | --- | ------- | --------------------- | --- | --- | ---------------------------- | --- | --- | --- |
| merging, | akin to | the feature | implemented |     | in Coda | [20].                 |     |     |                              |     |     |     |
harbingeroveragraphedge,aserveraddsadelaypropor-
| For example, | when    | a file | is deleted    | right   | after it is | modi-            |               |      |              |             |         |        |
| ------------ | ------- | ------ | ------------- | ------- | ----------- | ---------------- | ------------- | ---- | ------------ | ----------- | ------- | ------ |
|              |         |        |               |         |             | tional to        | the estimated |      | speed of     | the edge    | (10∗RTT | in our |
| fied (which  | happens | often  | for temporary | files), | the         | server           |               |      |              |             |         |        |
|              |         |        |               |         |             | implementation). |               | This | way, Pangaea | dynamically |         | builds |
quashesthemodificationifithasnotyetbeensenttoother
|           |               |     |                |     |               | a spanning | tree      | whose  | shape closely | matches     | the | physical |
| --------- | ------------- | --- | -------------- | --- | ------------- | ---------- | --------- | ------ | ------------- | ----------- | --- | -------- |
| replicas. | Delta merging |     | is transparent | to  | users because | it         |           |        |               |             |     |          |
|           |               |     |                |     |               | network    | topology. | Figure | 5 shows       | an example. |     | In Sec-  |
addsnodelaytopropagation.
|     |     |     |     |     |     | tion 7.6, | we show | that | this technique | drastically |     | reduces |
| --- | --- | --- | --- | --- | --- | --------- | ------- | ---- | -------------- | ----------- | --- | ------- |
theuseofwide-areanetworkswhenupdatingsharedfiles.
| 5.1.2 Optimization2: |     |     | harbingers |     |     |     |     |     |     |     |     |     |
| -------------------- | --- | --- | ---------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
4PangaeabatchesNFSwriterequestsandflushesdatatodiskandother
Flooding guarantees reliable delivery by propagating up- replicasonlyaftera“commit”request[4].Thus,thesizeofanupdatecan
dates (deltas or full contents) over multiple links at each growlargerthanthetypical“write”requestsizeof8KB.

|     |     |     |     |     |     |     |     | scribedin[28]. |                  | Ourprincipleisalwaystoletthechildfile |        |          |                 |     |
| --- | --- | --- | --- | --- | --- | --- | --- | -------------- | ---------------- | ------------------------------------- | ------ | -------- | --------------- | --- |
|     |     |     |     |     |     |     |     | (“foo”         | in our example), |                                       | rather | than its | parent (“alice” |     |
B
B B B or “bob”), dictate the outcome of the conflict resolution
| A   |     | A   |     | A   |     | A   |     |             |                    |      |                 |     |              |        |
| --- | --- | --- | --- | --- | --- | --- | --- | ----------- | ------------------ | ---- | --------------- | --- | ------------ | ------ |
|     |     |     |     |     |     |     |     | using the   | “last-writer-wins” |      | rule.           | We  | thus let the | file’s |
| C   | F   | C   | F   |     | C F |     | C   | F           |                    |      |                 |     |              |        |
|     |     |     |     |     |     |     |     | backpointer | (Section           | 3.3) | authoritatively |     | define the   | file’s |
D E D E D E D E location in the file-system namespace. We implement di-
rectoryoperations,suchas“mv”and“rm”,asachangeto
| (1) |     | (2) |     |     | (3) |     | (4) |                          |     |     |                             |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | ------------------------ | --- | --- | --------------------------- | --- | --- | --- |
|     |     |     |     |     |     |     |     | thefile’sbackpointer(s). |     |     | Whenareplicareceivesachange |     |     |     |
Figure5: Anexampleofupdatepropagationforafilewithsix to its backpointer, it also reflects the change to its parents
| replicas,AtoF.Thickedgesrepresentfastlinks. |               |         |             |            |         | (1)Anupdate |       |              |           |           |           |          |               |        |
| ------------------------------------------- | ------------- | ------- | ----------- | ---------- | ------- | ----------- | ----- | ------------ | --------- | --------- | --------- | -------- | ------------- | ------ |
|                                             |               |         |             |            |         |             |       | by creating, | deleting, | or        | modifying | the      | corresponding | en-    |
| is issued                                   | at A. (2)     | A sends | a harbinger |            | via the | fat edge    | to C. | C            |           |           |           |          |               |        |
|                                             |               |         |             |            |         |             |       | tries.5 The  | parent    | directory | will,     | in turn, | flood the     | change |
| forwards                                    | the harbinger | to      | D and       | F quickly. | (3)     | D forwards  | the   |              |           |           |           |          |               |        |
toitsreplicas.Inpractice,werandomlydelaythedirectory-
harbingertoE.Aftersometime,AsendstheharbingertoB,anda
spanningtreeisformed.Linksnotinthetreeareusedasbackups entrypatchingandsubsequentflooding,becausethereisa
goodchancethatotherreplicasofthefilewilldothesame.
| whensomeofthetreelinksfail. |     |     |     | (4)Theupdate’sbodyispushed |     |     |     |     |     |     |     |     |     |     |
| --------------------------- | --- | --- | --- | -------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
alongthetreeedges.Inpractice,steps2-4proceedinparallel. Figure 6 illustrates how Pangaea resolves the first conflict
|     |     |     |     |     |     |     |     | scenario. | Thesamepolicyisusedtoresolvethemv-rmdir |         |         |             |        |        |
| --- | --- | --- | --- | --- | --- | --- | --- | --------- | --------------------------------------- | ------- | ------- | ----------- | ------ | ------ |
|     |     |     |     |     |     |     |     | conflict: | when a                                  | replica | detects | the absence | of the | direc- |
5.2 Conflictresolution
toryentrycorrespondingtoitsbackpointer,itre-createsthe
With optimistic replication, concurrent updates are in- entry, which potentially involves re-creating the directory
itselfandtheancestordirectoriesrecursively,allthewayto
| evitable,                                             | although | rare | [35, 31]. | We  | use a | combination |     | of                                              |     |     |     |     |     |     |
| ----------------------------------------------------- | -------- | ---- | --------- | --- | ----- | ----------- | --- | ----------------------------------------------- | --- | --- | --- | --- | --- | --- |
| versionvectorsandthelast-writer-winsruletoresolvecon- |          |      |           |     |       |             |     | theroot.                                        |     |     |     |     |     |     |
| flicts.                                               |          |      |           |     |       |             |     | AdirectoryinPangaeais,ineffect,merelyacopyofthe |     |     |     |     |     |     |
First, recall that when delta timestamps mismatch, backpointersofitschildren.Thus,resolvingconflictsondi-
serversreverttofull-statetransfer.Wethenuseversionvec- rectorycontentsisdonebyapplyingthe“last-writer-wins”
|           |             |      |           |      |       |        |        | ruletoindividualentries. |     |     | Ifafileistoberemovedfroma |     |     |     |
| --------- | ----------- | ---- | --------- | ---- | ----- | ------ | ------ | ------------------------ | --- | --- | ------------------------- | --- | --- | --- |
| tors [23] | to separate | true | conflicts | from | other | causes | (e.g., |                          |     |     |                           |     |     |     |
missing updates) that can be fixed simply by overwriting directory,thedirectorystillkeepstheentrybutmarksitas
thereplica. Thissimplifiesconflictresolution. “dead”(i.e., itactsasa“deathcertificate”[7]), sothatwe
|                             |       |        |            |                            |            |       |            | can detect | when | a stale | change | to the entry | arrives | in the |
| --------------------------- | ----- | ------ | ---------- | -------------------------- | ---------- | ----- | ---------- | ---------- | ---- | ------- | ------ | ------------ | ------- | ------ |
| For conflicts               |       | on the | contents   | of                         | a regular  | file, | we cur-    |            |      |         |        |              |         |        |
| rentlyofferuserstwooptions. |       |        |            | Thefirstisthe“last-writer- |            |       |            | future.    |      |         |        |              |         |        |
| wins” rule                  | using | update | timestamps |                            | (attribute |       | ts in Fig- |            |      |         |        |              |         |        |
ure3). Inthiscase,theclocksofserversshouldbeloosely 5.3 Controllingreplicadivergence
synchronized,e.g.,usingNTP,torespecttheusers’intuitive
|                        |     |     |                             |     |     |     |     | The protocols | described |     | so far | do not provide | hard | guar- |
| ---------------------- | --- | --- | --------------------------- | --- | --- | --- | --- | ------------- | --------- | --- | ------ | -------------- | ---- | ----- |
| senseofupdateordering. |     |     | Thesecondoptionistoconcate- |     |     |     |     |               |           |     |        |                |      |       |
natetwoversionsinthefileandlettheuserfixtheconflict anteesforthedegreeofreplicadivergence—consistencyis
achievedonlyeventually.
| manually. | Other | options, | such | as  | application-specific |     | re- |     |     |     |     |     |     |     |
| --------- | ----- | -------- | ---- | --- | -------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
solvers[36,18,32],arecertainlypossible,butwehavenot Toalleviatethisproblem,Pangaeaintroducesanoption,
implementedthemyet. calledthe“redbutton”,toprovideusersconfirmationofup-
|     |     |     |     |     |     |     |     | datedelivery. | Theredbutton,whenpressedforaparticular |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | ------------- | -------------------------------------- | --- | --- | --- | --- | --- |
Conflictsregardingfileattributesordirectoryentriesare
moredifficulttohandle. Theyfallintotwocategories. The file,sendsharbingersforanypendingupdatestoneighbor-
ingreplicas.Theseharbingers(andcorrespondingupdates)
firstisaconflictbetweentwodirectory-updateoperations;
|              |       |      |     |      |             |     |     | circulate | among | replicas | as described | in  | Section 5.1.2. | A   |
| ------------ | ----- | ---- | --- | ---- | ----------- | --- | --- | --------- | ----- | -------- | ------------ | --- | -------------- | --- |
| for example, | Alice | does | “mv | /foo | /alice/foo” |     | and |           |       |          |              |     |                |     |
Bob does “mv /foo /bob/foo” concurrently. In the replica, however, does not acknowledge a harbinger until
allthegraphneighborstowhichitforwardedtheharbinger
end,wewantoneoftheupdatestotakeeffect,butnotboth.
acknowledgeitortimeout(toavoiddeadlocking,areplica
| The second                  | category   |     | is a conflict |     | between    | “rmdir” | and   |                     |          |      |             |         |                |       |
| --------------------------- | ---------- | --- | ------------- | --- | ---------- | ------- | ----- | ------------------- | -------- | ---- | ----------- | ------- | -------------- | ----- |
|                             |            |     |               |     |            | “mv     | /foo  | replies immediately |          | when | it receives | the     | same harbinger |       |
| any other                   | operation; | for | example,      |     | Alice does |         |       |                     |          |      |             |         |                |       |
|                             |            |     |               |     |            |         |       | twice).             | The user | who  | pressed     | the red | button waits   | until |
| /alice/foo”andBobdoes“rmdir |            |     |               |     | /alice”.   |         | These |                     |          |      |             |         |                |       |
problemsaredifficulttohandle,becausefilesmayberepli- theoperation isfully acknowledgedor some replicastime
out,inwhichcasetheuserispresentedwiththelistofun-
| cated on | different | sets | of nodes, | and | a node | might | receive |     |     |     |     |     |     |     |
| -------- | --------- | ---- | --------- | --- | ------ | ----- | ------- | --- | --- | --- | --- | --- | --- | --- |
availablereplicas.
| only one | of the | conflicting |     | updates | and fail | to  | detect the |     |     |     |     |     |     |     |
| -------- | ------ | ----------- | --- | ------- | -------- | --- | ---------- | --- | --- | --- | --- | --- | --- | --- |
conflictinthefirstplace.
5 Thereplicacanalwaysfindareplicaoftheparentdirectoryinthe
We only outline our solution here, as it is fully de- samenode,becauseofthename-space-containmentproperty.

(1) 50:ts=2, d={[51, foo, 4], [52, alice, 5], [53, bob,  6]} For permanent failures, we try to clean all data structures
51:bp=[50, foo], ts=4
52: bp=[50, alice], ts=5, d={} associatedwiththefailednodesothatthesystemrunsasif
53:bp=[50, bob], ts=6, d={} thenodehadneverexistedinthefirstplace.
% mv foo /bob/foo
% mv foo /alice/foo
50: ts=9, d={[51, *foo, 9],
(2) 50: ts=8, d={[51, *foo, 8], (2’) 6.1 Recoveringfromtemporaryfailures
|     |        [52, alice, 5], |     |     |     |        [52, alice, 5], |     |     |     |     |     |     |     |     |
| --- | ---------------------- | --- | --- | --- | ---------------------- | --- | --- | --- | --- | --- | --- | --- | --- |
       [53, bob, 6]}
       [53, bob, 6]}
51: bp=[52, foo], ts=8 51: bp=[53, foo], ts=9 Temporaryfailuresarehandledbyretrying. Anodepersis-
52: bp=[50,alice],ts=5,d={}
52: bp=[50,alice], ts=8,  tentlylogsanyoutstandingremote-operationrequests,such
|     |   d={[51, foo, 8]}    |     |     | 53: bp=[50,bob], ts=9,  |                     |     |                                             |     |     |     |     |     |       |
| --- | --------------------- | --- | --- | ----------------------- | ------------------- | --- | ------------------------------------------- | --- | --- | --- | --- | --- | ----- |
| 53: | bp=[50,bob],ts=6,d={} |     |     |                         |   d={[51, foo, 9]}  |     |                                             |     |     |     |     |     |       |
|     |                       |     |     |                         |                     |     | ascontentsupdate,randomwalk,oredgeaddition. |     |     |     |     |     | Anode |
Update sent from bob to alice. retriesloggedupdatesuponrebootorafteritdetectsanother
(3) 50: ts=9, d={[51, *foo, 9], [52, alice, 5], [53, bob, 6]} node’s recovery. This recovery logic may sometimes cre-
51: bp=[53, foo], ts=9 ate uni-directional edges or more edges than desired, but
52: bp=[50,alice], ts=10, d={[51,*foo, 8]}
|     |     |     |     |     |     |     | it maintains | the | most important | invariant, |     | that | the graphs |
| --- | --- | --- | --- | --- | --- | --- | ------------ | --- | -------------- | ---------- | --- | ---- | ---------- |
53: bp=[50,bob], ts=9, d=[{51,foo, 9}]
|           |         |             |            |     |           |             | are m-connected |     | and that | all replicas | are | reachable | in the |
| --------- | ------- | ----------- | ---------- | --- | --------- | ----------- | --------------- | --- | -------- | ------------ | --- | --------- | ------ |
| Figure 6: | Example | of conflict | resolution |     | involving | four files, |                 |     |          |              |     |           |        |
hierarchicalnamespace.
| “/” (FileID=50),     |     | “/foo” (FileID=51), |       | “/alice/” | (FileID=52), | and        |         |         |     |                  |     |        |           |
| -------------------- | --- | ------------------- | ----- | --------- | ------------ | ---------- | ------- | ------- | --- | ---------------- | --- | ------ | --------- |
|                      |     |                     |       |           |              |            | Pangaea | reduces | the | logging overhead |     | during | contents- |
| “/bob/” (FileID=53). |     | “ts=2”              | shows | the       | replica’s    | timestamp. |         |         |     |                  |     |        |           |
updateflooding,byloggingonlytheIDofthemodifiedfile
| “bp=[50,foo]” | shows    | that    | the backpointer |        | of the    | replica indi- |                               |     |     |     |                   |     |     |
| ------------- | -------- | ------- | --------------- | ------ | --------- | ------------- | ----------------------------- | --- | --- | --- | ----------------- | --- | --- |
|               |          |         |                 |        |           |               | andkeepingdeltasonlyinmemory. |     |     |     | Toreducethememory |     |     |
| cates that    | the file | has the | name “foo”      | in the | directory | 50 (“/”).     |                               |     |     |     |                   |     |     |
“d={[51,foo,4]}” means that the directory contains one entry, a footprint further, when a node finds out that deltas to an
file“foo”withIDof51andtimestampof4. Boldtextsindicate unresponsive node are piling up, the sender discards the
changesfromthepreviousstep. Entriesmarked“*foo”aredeath deltasandfallsbackonfull-statetransfer.
| certificates.                | (1) Two | sites | initially                  | store the | same | contents. (2) |     |     |     |     |     |     |     |
| ---------------------------- | ------- | ----- | -------------------------- | --------- | ---- | ------------- | --- | --- | --- | --- | --- | --- | --- |
| Alicedoes“mv/foo/alice/foo”. |         |       | (2’)Bobconcurrentlydoes“mv |           |      |               |     |     |     |     |     |     |     |
6.2 Recoveringfrompermanentfailures
/foo/bob/foo”onanothernode.BecauseBob’supdatehasanewer
timestamp(ts=9)thanAlice’s(ts=8),wewantBob’stowinover
|     |     |     |     |     |     |     | Permanent | failures | are | handled | by a garbage |     | collection |
| --- | --- | --- | --- | --- | --- | --- | --------- | -------- | --- | ------- | ------------ | --- | ---------- |
Alice’s. (3)WhenAlice’snodereceivestheupdatefromBob’s,
thereplicaoffile51willnoticethatitsbackpointerhaschanged (GC) module. The GC module periodically scans local
from [52, foo] to [53, foo]. This change triggers the replica to disksanddiscoversreplicasthathaveedgestopermanently
deletetheentryfrom/aliceandaddtheentryto/bob. failednodes.WhentheGCmodulefindsanedgetoafailed
bronzereplica,itreplacestheedgebyperformingarandom
Thisoptiongivestheuserconfirmationthatherupdates
walkstartingfromagoldreplica(Section4.4).
havebeendeliveredtoremotenodesandallowshertotake
actions contingent upon stable delivery, such as emailing Recovering from a permanent loss of a gold replica is
|                |     |           |     |           |     |             | more complex. |         | When    | a gold replica, | say       | P, detects | a per-   |
| -------------- | --- | --------- | --- | --------- | --- | ----------- | ------------- | ------- | ------- | --------------- | --------- | ---------- | -------- |
| her colleagues |     | about the | new | contents. | The | red button, |               |         |         |                 |           |            |          |
|                |     |           |     |           |     |             | manent        | loss of | another | gold replica,   | P creates | a          | new gold |
however,stilldoesnotguaranteeasingle-copyserializabil-
|     |     |     |     |     |     |     | replica | on a live | node | chosen using | the | criteria | described |
| --- | --- | --- | --- | --- | --- | --- | ------- | --------- | ---- | ------------ | --- | -------- | --------- |
ity,asitcannotpreventtwousersfromchangingthesame
|     |     |     |     |     |     |     | in Section | 4.1. | Because | gold replicas | form | a clique | (Sec- |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ---- | ------- | ------------- | ---- | -------- | ----- |
filesimultaneously.
|     |     |     |     |     |     |     | tion 3.3),   | P can                                    | always | detect such | a loss. | This | choice is |
| --- | --- | --- | --- | --- | --- | --- | ------------ | ---------------------------------------- | ------ | ----------- | ------- | ---- | --------- |
|     |     |     |     |     |     |     | floodedtoall | thereplicasofthefile,usingtheprotocolde- |        |             |         |      |           |
6 Failure recovery scribedinSection5,toletthemupdatetheiruni-directional
|     |     |     |     |     |     |     | links to | the gold | replicas. | Simultaneously, |     | P updates | the |
| --- | --- | --- | --- | --- | --- | --- | -------- | -------- | --------- | --------------- | --- | --------- | --- |
FailurerecoveryinPangaeaissimplifiedduetothreeprop- localreplicaoftheparentdirectory(ies),foundinitsback-
erties:1)therandomizednatureofreplicagraphsthattoler- pointer(s),toreflectP’snewgold-replicaset. Thischange
ateoperationdisruptions;2)theidempotencyofupdateop- isfloodedtootherreplicasofthedirectories. Rarely,when
erations;includingNFSrequests;and3)theuseofaunified thesystemisintransientstate, multiplegoldreplicasmay
loggingmodulethatallowsanyoperationtobere-started. initiate this protocol simultaneously. Such a situation is
We distinguish two types of failures: temporary fail- resolved using the last-writer-wins policy, as described in
| ures and | permanent | failures. |     | They are | currently | distin- | Section5.2. |     |     |     |     |     |     |
| -------- | --------- | --------- | --- | -------- | --------- | ------- | ----------- | --- | --- | --- | --- | --- | --- |
guishedsimplybytheirduration—acrashbecomesperma- Recoveringfromapermanentnodelossisaninherently
nentwhenanodeissuspectedtohavefailedcontinuously expensiveprocedure,becausedatastoredonthefailednode
for more than two weeks. Given that the vast majority of must eventually be re-created somewhere else. The prob-
failures are temporary [11, 3], we set two different goals. lem is exacerbated in Pangaea, because it does not have
Fortemporaryfailures,wetrytoreducetherecoverycost. a central authority to manage the locations of replicas—

all surviving nodes must scan their own disks to discover Type # CPU Disk Mem
|     |     |     |     |     |     |     | A   | 2 730MHz |     |     |     | 256MB |
| --- | --- | --- | --- | --- | --- | --- | --- | -------- | --- | --- | --- | ----- |
replicas that require recovery. To lessen the impact, the QuantumAtlas9WLS
GC module tries to discover as many replicas that needs B 3 1.8GHz QuantumAtlasTW367L 512MB
|          |             |      |          |      |          |         | C   | 4 400MHz | SeagateCheetah39236LW |     |     | 256MB |
| -------- | ----------- | ---- | -------- | ---- | -------- | ------- | --- | -------- | --------------------- | --- | --- | ----- |
| recovery | as possible | with | a single | disk | scan. We | set the |     |          |                       |     |     |       |
defaultGCintervaltobeeverythreenights,whichreduces Table1: ThetypeandnumberofPCsusedintheexperiments.
thescanningoverheaddramaticallywhilestillofferingthe AlltheCPUsareversionsofPentiums.
| expected | file availability |     | in the | order of | six-nines, | assum- |     |     |     |     |     |     |
| -------- | ----------------- | --- | ------ | -------- | ---------- | ------ | --- | --- | --- | --- | --- | --- |
ing three gold replicas per file and a mean server lifetime of a file or directory. The node-wide metadata file keeps
of290days[3]. the extended attributes of all replicas stored on the server,
|     |     |     |     |     |     |     | including | graph edges | and version | vectors. | Data | files for |
| --- | --- | --- | --- | --- | --- | --- | --------- | ----------- | ----------- | -------- | ---- | --------- |
directoriesandthemetadatafilearebothimplementedus-
7 System evaluation ingtheBerkeleyDBlibrary[30]thatmaintainsahashtable
|     |     |     |     |     |     |     | in a file. | The intention-log | file | is also | implemented | using |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ----------------- | ---- | ------- | ----------- | ----- |
This section evaluates the design and implementation of theBerkeleyDBtorecordupdateoperationsthatmustsur-
Pangaea.First,weinvestigatethebaselineperformanceand viveanodecrash. AlltheBerkeleyDBfilesaremanaged
| overheads | of Pangaea | and | show | that it | performs | competi- |     |     |     |     |     |     |
| --------- | ---------- | --- | ---- | ------- | -------- | -------- | --- | --- | --- | --- | --- | --- |
usingits“environments”featurethatsupportstransactions
tively with other distributed file systems, even in a LAN. through low-level logging. This architecture allows meta-
| Further, | we measure | the | latency, | network | economy, | and |     |     |     |     |     |     |
| -------- | ---------- | --- | -------- | ------- | -------- | --- | --- | --- | --- | --- | --- | --- |
datachangestomultiplefilestobeflushedwithasequential
availabilityofPangaeainawide-areanetworkingenviron- writetothelow-levellog.
mentinthefollowingways:
• WestudythelatencyofPangaeausingtwoworkloads:a 7.2 Experimentalsettings
personalworkload(Andrewbenchmark)andaBBS-like
|                                        |     |     |     |     |            |     | We compare | Pangaea | to Linux’s | in-kernel | NFS | version 3 |
| -------------------------------------- | --- | --- | --- | --- | ---------- | --- | ---------- | ------- | ---------- | --------- | --- | --------- |
| workloadinvolvingextensivedatasharing. |     |     |     |     | Fortheper- |     |            |         |            |           |     |           |
serverandCoda,allrunningonLinux-2.4.18,withext3as
sonalworkload,weshowthattheuserseesonlylocalac-
thenativefilesystem.
cesslatencyonanodeconnectedtoaslownetworkand
thatroaminguserscanbenefitbyfetchingtheirpersonal WeleteachPangaeaserverserveonlyclientsonthesame
|      |             |          |       |     |                  |     | node. BothPangaeaandNFSflushbufferssynchronously |     |     |                    |     |     |
| ---- | ----------- | -------- | ----- | --- | ---------------- | --- | ------------------------------------------------ | --- | --- | ------------------ | --- | --- |
| data | from nearby | sources. | Using | the | second workload, |     |                                                  |     |     |                    |     |     |
|      |             |          |       |     |                  |     | todiskbeforereplyingtoaclient,                   |     |     | asrequiredbytheNFS |     |     |
weshowthatasafileissharedbymoreusers,Pangaea
progressively lowers the access latency by transferring specifications [4]. Coda supports two main modes of op-
|     |     |     |     |     |     |     | eration: | strongly connected |     | mode (denoted |     | coda-s here- |
| --- | --- | --- | --- | --- | --- | --- | -------- | ------------------ | --- | ------------- | --- | ------------ |
databetweennearbyclients.
after)thatprovidesopen-closesemantics,andweaklycon-
| • We demonstrate |     | network    | economy |        | by studying   | how |                          |     |     |                           |     |     |
| ---------------- | --- | ---------- | ------- | ------ | ------------- | --- | ------------------------ | --- | --- | ------------------------- | --- | --- |
|                  |     |            |         |        |               |     | nectedmode(denotedcoda-w |     |     | hereafter)thatimprovesthe |     |     |
| updates          | are | propagated | for     | widely | shared files. | We  |                          |     |     |                           |     |     |
response-timeofwriteoperationsbyasynchronouslytrick-
show that Pangaea transfers data predominantly over ling updates to the server. We mainly evaluate coda-w,
fastlinks.
sinceitssemanticsareclosertoPangaea’s.
• Todemonstratetheeffectofpervasivereplicationonthe Table1showsthemachinesweusedfortheevaluation.
availabilityofthesystem,weanalyzetracesfromafile All the machines are physically connected by a 100Mb/s
serverandshowthatPangaeadisturbsusersfarlessthan Ethernet. Disks on all the machines are large enough
traditionalreplicationpolicies. that replicas never had to be purged in either Pangaea or
|     |     |     |     |     |     |     | Coda. For   | NFS and  | Coda, we | configured | a        | single server |
| --- | --- | --- | --- | --- | --- | --- | ----------- | -------- | -------- | ---------- | -------- | ------------- |
|     |     |     |     |     |     |     | on a type-A | machine. | Other    | machines   | are used | as clients.   |
7.1 Prototypeimplementation
|     |     |     |     |     |     |     | For Pangaea, | all machines | are | used as | servers | and appli- |
| --- | --- | --- | --- | --- | --- | --- | ------------ | ------------ | --- | ------- | ------- | ---------- |
We have implemented Pangaea as a user-space NFS (ver- cations access files from their local servers. For CPU-
sion3)serverusingtheSFStoolkit[19].Ourprototypeim- intensive workloads (i.e., Andrew), we used a type-A ma-
plementsallthefeaturesdescribedinthepaper,exceptthat chine for all the experiments. The other experiments are
support for recovery from permanent failures (Section 6) completelynetwork-bound,andthustheyareinsensitiveto
| is still fragmentary. |     | Pangaea | currently |     | consists of | 30,000 | CPUspeeds. |     |     |     |     |     |
| --------------------- | --- | ------- | --------- | --- | ----------- | ------ | ---------- | --- | --- | --- | --- | --- |
linesofC++code. For our wide-area experiments, we built a simulated
A Pangaea server maintains three types of files on the WANtoevaluatePangaeareliablyinavarietyofnetwork-
local file system: data files, the metadata file, and the ing conditions. We routed packets to a type-B FreeBSD
intention-log file. A data file is created for each replica node(notincludedinthetable)runningDummynet[26]to

| addartificialdelaysandbandwidthrestrictions.   |     |     |     |     |     | Thisrouter |     |     |     |      |     |          |     |
| ---------------------------------------------- | --- | --- | --- | --- | --- | ---------- | --- | --- | --- | ---- | --- | -------- | --- |
|                                                |     |     |     |     |     |            |     |     |     | %    |     | Overhead |     |
| nodewasfastenoughnevertobecomeabottleneckinany |     |     |     |     |     |            |     |     |     | 7    |     |          |     |
|                                                |     |     |     |     |     |            |     |     |     | 1. 9 |     | Received |     |
| ofourexperiments.                              |     |     |     |     |     |            |     | 100 |     | %    |     |          |     |
|                                                |     |     |     |     |     |            |     |     |     | 2    |     | Sent     |     |
|                                                |     |     |     |     |     |            |     | 75  |     | 1. 1 |     |          |     |
BM
|                               |     |     |     |     |     |     |     | 50  | %   |     |     |     |     |
| ----------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 7.3 BaselineperformanceinaLAN |     |     |     |     |     |     |     |     | 0   |     |     |     |     |
25
| ThissectionevaluatesPangaea’sperformanceinaLANus- |     |     |     |     |     |     |     | 0   |     |     |     |     |     |
| ------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
i n g a se q u e nt i a lw o r k lo a d w i th o u td a ta s h a ri n g . W h il e s u c h pang-1 pang-2 pang-3 pang-4 coda-s coda-w nfs
| a n e nv ir o | n m e n t | is n o t | P a ng a e a’ | s m a in | t a rg e t | , w e co | n d u c t e d |     |     |     |     |     |     |
| ------------- | --------- | -------- | ------------- | -------- | ---------- | -------- | ------------- | --- | --- | --- | --- | --- | --- |
this study to test Pangaea’s ability to serve people’s daily Figure 7: Network bandwidth consumed during the Andrew
storage needs and to understand the system’s behavior in benchmark. The “overhead” bars show bytes consumed by
|     |     |     |     |     |     |     | harbingers | and duplicate |     | updates. The | numbers | above | the bars |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ------------- | --- | ------------ | ------- | ----- | -------- |
anidealizedsituation.
showthepercentageofoverhead.
| We created | a                | variation | of the               | Andrew | benchmark6 |           | that     |          |          |      |           |         |       |
| ---------- | ---------------- | --------- | -------------------- | ------ | ---------- | --------- | -------- | -------- | -------- | ---- | --------- | ------- | ----- |
| simulates  | a single-person, |           | engineering-oriented |        |            | workload. |          |          |          |      |           |         |       |
|            |                  |           |                      |        |            |           | replicas | are gold | and they | form | a clique, | Pangaea | would |
It has the same mix of operations as the original Andrew haveconsumed4to9timesthebandwidthofpang-2were
benchmark [13], but the volume of the data is expanded it not for harbingers. Instead, its network usage is near-
twenty-foldtoallowforaccuratemeasurementsonmodern
optimal,withlessthan2%ofthebandwidthwasted.
hardware. Thisbenchmark,denotedAndrew-Tcl hereafter, Table3showsnetworkbandwidthconsumptionforcom-
consistsoffivestages: (1)mkdir: creating200directories, monfile-systemupdateoperations. Operationssuchascre-
(2)copy: copyingtheTcl-8.4sourcefilesfromonedirec- ating a file or writing one byte show a high percentage of
tory to another, (3) stat: doing “ls -l” on the source files, overhead, since they are sent directly without harbingers,
(4)grep:doing“du”and“grep”onthesourcefiles,and(5)
|     |     |     |     |     |     |     | but | they have only | a minor | impact | on the | overall | wasted |
| --- | --- | --- | --- | --- | --- | --- | --- | -------------- | ------- | ------ | ------ | ------- | ------ |
compile: compiling the source code. We averaged results bandwidthsincetheirsizeissmall. Ontheotherhand,bulk
from four runs per system, with 95% confidence interval writes, which make up the majority of the overall traffic,
below3%forallthenumberspresented.
incuralmostnooverhead.
| Table                               | 2 shows | the | time to | complete | the            | benchmark. |     |     |     |     |     |     |     |
| ----------------------------------- | ------- | --- | ------- | -------- | -------------- | ---------- | --- | --- | --- | --- | --- | --- | --- |
| Throughouttheevaluation,labelpang-N |         |     |         |          | standsforaPan- |            |     |     |     |     |     |     |     |
/ s /s
|     |     |     |     |     |     |     | 480 |     | M b M | b s |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ----- | --- | --- | --- | --- |
gaea system with N (gold) replicas per file. Pangaea’s 1 0 0 0 0 M b / b/s
|     |     |     |     |     |     |     |     | T T | ,   T ,   1 T | ,  5 M |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ------------- | ------ | --- | --- | --- |
performance is comparable to NFS. This is as expected, sdnoceS 360 0ms  R R T R T T ,  1
|     |     |     |     |     |     |     |     | m s |   m s    R | T   |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---------- | --- | --- | --- | --- |
because both systems perform about the same amount of 1 0 1 0 0 m s c o m pile
|     |     |     |     |     |     |     | 240 |     | 3 0 0 |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ----- | --- | --- | --- | --- |
g re p
| bufferflushing,whichisthemainsourceofoverhead. |     |     |     |     |     |     | Pan- |     |     |     |     | stat |     |
| ---------------------------------------------- | --- | --- | --- | --- | --- | --- | ---- | --- | --- | --- | --- | ---- | --- |
120
| gaeaissubstantiallysloweronlyinmkdir.Thisisbecause |     |     |     |     |     |     |     |     |     |     |     | copy |     |
| -------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---- | --- |
mkdir
| PangaeamustcreateaBerkeleyDBfileforeachnewdirec- |      |            |             |     |             |             |     | 0             |        |            |        |     |     |
| ------------------------------------------------ | ---- | ---------- | ----------- | --- | ----------- | ----------- | --- | ------------- | ------ | ---------- | ------ | --- | --- |
|                                                  |      |            |             |     |             |             |     | pang-1 pang-2 | pang-3 | pang-4 nfs | coda-w |     |     |
| tory, which                                      | is a | relatively | expensive   |     | operation.  | Pangaea’s   |     |               |        |            |        |     |     |
| performance                                      | is   | mostly     | independent |     | of a file’s | replication |     |               |        |            |        |     |     |
factor, thanks to optimistic replication, where most of the Figure8: Andrew-Tclbenchmarkresultsonanodewithaslow
|     |     |     |     |     |     |     | networklink. | Thelabelsnexttothebarsindicatethelinkspeeds. |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ------------ | -------------------------------------------- | --- | --- | --- | --- | --- |
replicationprocessinghappensinthebackground.
ForPangaea,thesearethelinksbetweenanytwoservers;forNFS
| Coda’s | weakly | connected |     | mode (coda-w) |     | is very | fast. |     |     |     |     |     |     |
| ------ | ------ | --------- | --- | ------------- | --- | ------- | ----- | --- | --- | --- | --- | --- | --- |
andCoda,theyarethelinksbetweenclientsandserver.NFStook
| This is due | to  | implementation |     | differences: |     | whereas | Pan- |     |     |     |     |     |     |
| ----------- | --- | -------------- | --- | ------------ | --- | ------- | ---- | --- | --- | --- | --- | --- | --- |
1939secondsina5Mb/snetwork,anditdidnotfinishaftertwo
gaeaandNFSflushbufferstodiskaftereveryupdateoper-
hoursina1Mb/snetwork.
ation,Codaavoidsthatbyinterceptinglow-levelfile-access
(VFS)requestsusingasmallin-kernelmodule.
| Figure     | 7 shows    | the | network    | bandwidth | used  | during     | the |             |     |             |     |          |     |
| ---------- | ---------- | --- | ---------- | --------- | ----- | ---------- | --- | ----------- | --- | ----------- | --- | -------- | --- |
|            |            |     |            |           |       |            | 7.4 | Performance |     | of personal |     | workload | in  |
| benchmark. | “Overhead” |     | is defined |           | to be | harbingers | and |             |     |             |     |          |     |
WANs
| update messages |         | that | turn out | to be    | duplicates. |           | Pang-1 |                    |     |           |     |           |         |
| --------------- | ------- | ---- | -------- | -------- | ----------- | --------- | ------ | ------------------ | --- | --------- | --- | --------- | ------- |
| does not        | involve | any  | network  | activity | since       | it stores | files  |                    |     |           |     |           |         |
|                 |         |      |          |          |             |           | We     | ran the Andrew-Tcl |     | benchmark | to  | study the | perfor- |
onlyonthelocalserver. Numbersforpang-3and-4show mance of the systems in WANs for a personal workload.
| the effect        | of Pangaea’s |        | harbinger | algorithm       |     | in conserving |       |               |          |     |               |     |         |
| ----------------- | ------------ | ------ | --------- | --------------- | --- | ------------- | ----- | ------------- | -------- | --- | ------------- | --- | ------- |
|                   |              |        |           |                 |     |               | Since | this workload | involves | no  | data sharing, | the | elapsed |
| network-bandwidth |              | usage. | In        | this benchmark, |     | because       | all   |               |          |     |               |     |         |
timedepends(ifatall)onlyonthelatencyandcapacityof
6This benchmark is available from http://www.hpl.hp.com/ thelinkbetweentheclientandtheserver. Figure8shows
personal/ysaito. the time needed to complete the benchmark. Pangaea and

pang-1 pang-2 pang-3 pang-4 NFS Coda-s Coda-w ext3
mkdir 2.04 2.04 2.18 2.28 0.316 2.25 0.047 0.021
copy 3.40 3.79 3.85 3.90 3.50 201.0 0.85 0.264
stat 0.91 0.90 0.90 0.91 0.87 0.86 0.86 0.162
grep 2.09 2.11 2.13 2.13 2.20 1.22 1.20 0.925
compile 74.4 75.3 75.8 75.9 77.2 90.2 62.1 61.5
Total 82.84 84.14 84.86 85.12 84.08 295.5 65.05 62.87
Table 2: Andrew-TclbenchmarkresultsinaLANenvironment. Numbersareinseconds. Labelpang-NshowsPangaea’sperfor-
mancewhenitcreatesNreplicasforeachnewfile.Ext3isLinux’snative(local)filesystem.
pang-1 pang-2 pang-3 pang-4 NFS coda-w coda-s
Bytes Bytes Overhead Bytes Overhead Bytes Overhead Bytes Bytes Bytes
create 0 248 0% 1.29K 60% 2.61K 68% 503 1.46K 1.96K
write1B 0 323 0% 854 61% 2.01K 68% 667 944 935
write50KB 0 52.04K 0% 104.98K 1.49% 157.44K 1.52% 53.21K 55.56K 82.13K
write25MB 0 26.22M 0% 52.44M 0.01% 78.67M 0.02% 26.76M 1.56M 38.75M
Table3: Networkbandwidthconsumptionforcommonfile-systemoperations.Showsthetotalnumberofbytestransmittedbetween
allthenodesforeachoperation.“Overhead”showsthepercentageofthebandwidthusedbyharbingersandduplicateupdates.
Coda totally hide the network latency, because the bench-
mark is designed so that it reads all the source data from
the local disk, and the two systems can propagate updates 180
to other nodes in the background. On the other hand, the
120
performanceofNFSdegradesseverelyacrossslowlinks.
60
0
7 R . o 5 amin R g o , a i m .e., in a g single user moving between different 1 0 0 + M 1 0 b 0 / s M b/ s 5 M + b 5 / s M b/ s 1 M + b 1 / s M b/ s 1 0 0 M + 5 b M / s b/ s 1 0 0 M + 1 b M / s b/ s
nodes, is an important use of distributed file systems. We
expect Pangaea to perform well in non-uniform networks
in which nodes are connected with networks of different
speeds. Wesimulatedroamingusingthreenodes: S,which
stores the files initially and is the server in the case of
Coda, and two type-A nodes,C andC . We first run the
1 2
Andrew-Tcl benchmark to completion on nodeC , delete
1
the *.o files, and then re-run only the compilation stage
of the benchmark on node C . We vary two parameters:
2
the link speed betweenC andC , and the link speed be-
1 2
tweenthemandS. AsseenfromFigure8,theperformance
depends,ifatall,onlyonthesetwoparameters.
Figure 9 shows the results. It shows that when the net-
work is uniform, i.e., when the nodes are placed either
all close by or all far apart, Pangaea and Coda perform
comparably. However, in non-uniform networks, Pangaea
achievesbetterperformancethanCodabytransferringdata
between nearby nodes. In contrast, Coda clients always
fetch data from the server. (Pangaea actually performs
slightly better in uniformly slow networks. We surmise
that the reason is that Pangaea uses TCP for data transfer,
whereasCodausesitsownUDP-basedprotocol.)
sdnoceS
pang
coda-w
S
(b) (b)
C C
1 (a) 2
Figure 9: The result of recompiling the Tcl source code.
100Mb/s+1Mb/s,forexample,meansthatthelinkbetweenthe
twoclientnodes(link(a)intheright-sidepicture)is100Mb/s,and
thelinkbetweenthebenchmarkclientandtheserver(link(b))is
1Mb/s.Thespeedofotherlinksisirrelevantinthisexperiment.
7.6 Data sharing in non-uniform environ-
ments
The workload characteristics of wide-area collaboration
systems are not well known. We thus created a synthetic
benchmark modeled after a bulletin-board system. In this
benchmark, articles (files) are continuously posted or up-
dated from nodes chosen uniformly at random; other ran-
domlychosennodes(i.e., users)fetchnewarticlesnotyet
read. Afilesystem’sperformanceismeasuredbytwomet-
rics: themeanlatencyofreadingafileneveraccessedbe-
fore by the server, and the wide-area network bandwidth
consumptionforfilesthatareupdated. Thesetwonumbers
depend,ifatall,onlyonthefilesize,thenumberofexist-
ingreplicas(sincePangaeacanperformshort-cutcreation),
and the order in which these replicas are created (since it
affects the shape of the graph). We choose an article size

6
:MAN
6
|     |     |     |     |     | :LAN               |     |             | )s( elif rep ycnetal |     |     |     |        |     |
| --- | --- | --- | --- | --- | ------------------ | --- | ----------- | -------------------- | --- | --- | --- | ------ | --- |
|     | 3   |     |     | 3   |                    |     | ssecca naeM |                      |     |     |     |        |     |
|     |     |     |     |     | :10ms RTT, 100Mb/s |     |             | 0.75                 |     |     |     | Hub    |     |
| 9   |     |     |     |     |                    |     |             |                      |     |     |     | Random |     |
|     |     | 6   |     | 3   | :100ms RTT, 5Mb/s  |     |             | 0.5                  |     |     |     |        |     |
Pangaea
:300ms RTT, 1Mb/s
0.25
| Figure10: | Simulatednetworkconfigurationsmodeledafterour |     |             |            |     |             |     |     |     |     |     |     |     |
| --------- | --------------------------------------------- | --- | ----------- | ---------- | --- | ----------- | --- | --- | --- | --- | --- | --- | --- |
| corporate | network.                                      | The | gray circle | represents | the | SF bay area |     | 0.0 |     |     |     |     |     |
metropolitan-area network (MAN), the upper bubble represents 0 10 20 30
| Bristol | (UK), | and the other | bubbles | represent | India, | Israel, and |     |     |     |     |     |     |     |
| ------- | ----- | ------------- | ------- | --------- | ------ | ----------- | --- | --- | --- | --- | --- | --- | --- |
# of replicas
Japan. Thenumberinacircleshowsthenumberofserversrun-
|     |     |     |     |     |     |     | Figure11: |     | Theaveragetimeneededtoreadanewfileinacol- |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------- | --- | ----------------------------------------- | --- | --- | --- | --- |
ningintheLAN.
laborativeenvironment.TheXaxisshowsthenumberofexisting
of 50KB, a size typical in Usenet [29]. We try to aver- replicasofafile. TheYaxisshowsthemeanlatencytoaccessa
| age out | the final | parameter | by  | creating | and reading | about |     |     |     |     |     |     |     |
| ------- | --------- | --------- | --- | -------- | ----------- | ----- | --- | --- | --- | --- | --- | --- | --- |
fileonanodethatdoesnotyetstoreareplicaofthefile.
1000randomfilesforeachsamplepointandcomputingthe
mean. Werunbotharticlepostersandreadersataconstant
speed(≈5articlespostedorread/second),becauseourper-
| formance | metrics    | are | independent | of       | request | inter-arrival |                |     |     |     |     |     |     |
| -------- | ---------- | --- | ----------- | -------- | ------- | ------------- | -------------- | --- | --- | --- | --- | --- | --- |
| time.    |            |     |             |          |         |               | % refsnart NAW | 100 |     |     |     |     |     |
| In this  | benchmark, |     | we run      | multiple | servers | in a single   |                | 80  |     |     |     |     |     |
60
(physical)nodetobuildaconfigurationwitharealisticsize.
Hub (WAN)
ToavoidoverloadingtheCPUorthedisk,wechoosetorun 40 Random (WAN)
|     |     |     |     |     |     |     |     | 20  |     |     |     | Pang (WAN) |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---------- | --- |
sixvirtualserversonatype-Bmachine(Table1),andthree
| virtual | servers | on each | of other | machines, | with | the total |     | 0   |     |     |     |     |     |
| ------- | ------- | ------- | -------- | --------- | ---- | --------- | --- | --- | --- | --- | --- | --- | --- |
of 36 servers on 9 physical nodes. Figure 10 shows the 4 14 24 34
# of replicas
simulatedgeographicaldistributionofnodes,modeledafter
HP’s corporate network. For the same logistical reasons, Figure 12: Wide-area network bandwidth usage during file
insteadofCoda,wecomparethreeversionsofPangaea: updates. The Y axis shows the percentage of traffic routed
|     |     |     |     |     |     |     | through | the | indicated | networks. | “WAN+MAN” | shows | the traf- |
| --- | --- | --- | --- | --- | --- | --- | ------- | --- | --------- | --------- | --------- | ----- | --------- |
pang: Pangaeawiththreegoldreplicaspernewfile.
|     |     |     |     |     |     |     | ficthatflowedthroughnon-LAN(i.e., |     |     |     | thosewith≥10msRTT), |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------------------------- | --- | --- | --- | ------------------- | --- | --- |
hub: This configuration centralizes replica management whereas “WAN” shows the traffic that flowed through networks
with≥180msRTT(seealsoFigure10).
| by  | creating, | for each | file, | one gold | replica | on a server |     |     |     |     |     |     |     |
| --- | --------- | -------- | ----- | -------- | ------- | ----------- | --- | --- | --- | --- | --- | --- | --- |
chosen from available servers uniformly at random. (#-of-replicas−1)∗filesize), Pangaea uses far less wide-
| Bronze | replicas | connect | only | to the | gold | replica. Up- |      |         |         |       |              |                     |     |
| ------ | -------- | ------- | ---- | ------ | ---- | ------------ | ---- | ------- | ------- | ----- | ------------ | ------------------- | --- |
|        |          |         |      |        |      |              | area | network | traffic | since | it transfers | data preferentially |     |
dates can still be issued at any replica, but they are all along fast links using dynamic spanning-tree construction
routed through the gold replica. This roughly corre- (Section 5.1.3). This trend becomes accentuated as more
| spondstoCoda. |                                           |     |     |     |     |     | replicasarecreated. |     |          |     |               |               |      |
| ------------- | ----------------------------------------- | --- | --- | --- | --- | --- | ------------------- | --- | -------- | --- | ------------- | ------------- | ---- |
| random:       | Thisconfigurationcreatesagraphbyusingsim- |     |     |     |     |     |                     |     |          |     |               |               |      |
|               |                                           |     |     |     |     |     | Figure              |     | 13 shows | the | time the pang | configuration | took |
plerandomwalkswithoutconsideringeithergoldrepli-
casornetworkproximity. Itischosentotesttheeffect topropagateupdatestoreplicasoffilesduringthesameex-
periment.The“max”linesshowlargefluctuations,because
ofPangaea’sgraph-constructionpolicy.
|     |     |     |     |     |     |     | updates | must | travel | over | 300ms RTT links | multiple | times |
| --- | --- | --- | --- | --- | --- | --- | ------- | ---- | ------ | ---- | --------------- | -------- | ----- |
We expect Pangaea’s access latency to be reduced as usingTCP.Bothnumbersareindependentofthenumberof
more replicas are added, since that increases the chance replicas, because (given a specific network configuration)
of file contents being transferred to a new replica from a thepropagationdelaydependsonlyonthegraphdiameter,
nearbyexistingreplica. Figure11confirmsthisprediction. whichisthree,inthisconfiguration. Webelievethat4sec-
In contrast, the hub configuration shows no speedup no onds average/15 seconds maximum delay for propagating
matterhowmanyreplicasofafileexist,becauseitalways 50KB of contents over 300ms, 1Mb/s links is reasonable.
fetchesdatafromthecentralreplica. Infact,mostofthetimeisspentinwaitingwhenconstruct-
Figure 12 shows the network bandwidth consump- ingaspanningtree(Section5.1.3);cuttingthedelayparam-
tion during file updates. Although all the systems con- eter would shrink the propagation latency, but potentially
sume the same total amount of traffic per update (i.e., wouldworsenthenetworkbandwidthusage.

14
10
6
2
4 14 24 34
# replicas
)s(ycnetal
noitagaporp
1.5
delta (max) 1.0 harbinger (max)
delta (mean) 0.5
harbinger (mean)
0.0
5 0 0 0 0 0 0
1 3 0 0 0 0
1 3 0 0
1 3
Figure13: Thetimeneededtopropagateupdatestoallreplicas. # of replicas
Thedashedlinesshowthetimeneededtodistributeharbingersto
replicas. Theyrepresentthewindowofinconsistency; i.e., time
beforewhichusersmayobserveoldcontents.Thesolidlinesrep-
resentthetimeneededtodistributeactualupdates.Theyrepresent
thenumberofsecondsuserswaitbeforeseeingthenewcontents.
The “mean” lines show the mean time needed for an update is-
suedatonereplicatoarriveatallreplicas,forafilewithaspecific
number of replicas. The “max” lines show the maximum time
observedforanupdatetoarriveatallreplicasofthefile.
7.7 Performance and network economy at a
largescale
The previous section demonstrated Pangaea’s ability to
fetch data from a nearby source and distribute updates
through fast links, yet only at a small scale. This sec-
tioninvestigateswhetherthesebenefitsstillholdatatruly
large scale, by using a discrete event simulator that runs
Pangaea’sgraph-maintenanceandupdate-distributionalgo-
rithms. Weextractedperformanceparametersfromthereal
testbedweusedintheprevioussection,andranessentially
the same workload as before. We test two network con-
figurations. Thefirstconfiguration,calledHP,isthesame
as Figure 10, but the number of nodes in each LAN is in-
creased eighty-fold, to a total of 3000 nodes. The second
configuration, calledU,keepsthesizeofeachLANatsix
nodes, but it increases the number of regions to 500 and
connectsregionsusing200msRTT,5Mb/slinks.
Figures 14 and 15 show average file-read latency and
network bandwidth usage in these configurations. These
figures show the same trend as before, but the differences
betweentheconfigurationsaremorepronounced.Inpartic-
ular, intheHPconfiguration, Pangaeapropagatesupdates
almost entirely using local-area network for popular files,
sinceitcrossesoverwide-arealinksonlyafixednumberof
times,regardlessofthenumberofreplicas.IntheUconfig-
uration, Pangaeastillsavesbandwidth, morevisiblywhen
many replicas exist. The systems cannot improve read la-
tencymuchinU,becausemostoftheaccessesareforcedto
go over wide area links, but Pangaea still shows improve-
mentwithmanyreplicas.
ssecca
naeM
)s(
elif
rep
ycnetal
Central U
Random U Pang U Central HP
Random HP
Pang HP
Figure 14: File-readinglatencyinasimulated3000-nodesys-
tem.ThemeaningofthenumbersisthesameasinFigure11.
100
80
60
40
20
0
5 0 0 0 0 0 0
1 3 0 0 0 0
1 3 0 0
1 3
# of replicas
%
refsnart
NAW
Central U
Random U
Pang U
Central HP
Random HP
Pang HP
Figure15: Wide-areanetworkbandwidthusageduringfileup-
datesinsimulated3000-nodesystems. Themeaningofthenum-
bersisthesameasinFigure12.
7.8 Availabilityanalysis
Thissectionstudiestheeffectsofpervasivereplication,es-
pecially name-space containment, on the system’s avail-
ability. A Pangaea server replicates not just replicas ac-
cesseddirectlybytheusers,butalsoalltheintermediatedi-
rectoriesneededtolookupthosereplicas. Thus,weexpect
Pangaea to disrupt users less than traditional approaches
that replicate files (or directories) on a fixed number of
nodes.
We perform trace-based analysis to verify this predic-
tion. Two types of configurations are compared: Pangaea
with one to three gold replicas per file, and a system that
replicates the entire file system contents on one to four
nodes. Our trace was collected on our departmental file
server,anditcontains24usersand116Mtotalaccessesto
566K files [31]. To simulate a wide-area workload from
thissingle-nodetrace,weassumethateachuserisonadif-
ferent node; thus, all the simulated configurations contain
24nodes.
Foreachconfiguration,westartfromanemptyfilesys-
temandfeedthefirsthalfofthetracetowarmthesystem
up. We then artificially introduce remote node crashes or
wide-area link failures. To simulate the former situation,

|       |     |     |     |     |            |     | ingsystemsinthreeaspects: |     |     | accesslatency,efficientusage |     |     |     |
| ----- | --- | --- | --- | --- | ---------- | --- | ------------------------- | --- | --- | ---------------------------- | --- | --- | --- |
| 100.0 |     |     |     |     | pang-1 (1) |     |                           |     |     |                              |     |     |     |
ofWANbandwidth,andfileavailability.
| 10.0 |     |     |     |     | pang-2 (1.65) |     |     |     |     |     |     |     |     |
| ---- | --- | --- | --- | --- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- |
% eruliaF
| 1.0 |     |     |     |     | pang-3 (2.3)   |     |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | -------------- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0.1 |     |     |     |     | fixed-1 (0.66) |     |     |     |     |     |     |     |     |
Acknowledgements
| 0.01 |     |     |     |     | fixed-2 (1.32) |     |     |     |     |     |     |     |     |
| ---- | --- | --- | --- | --- | -------------- | --- | --- | --- | --- | --- | --- | --- | --- |
fixed-3 (1.96)
0.001
fixed-4 (2.62)
| 0.0001 |     |     |     |     |     |     | We thank | our shepherd | Peter | Druschel, |     | the anonymous |     |
| ------ | --- | --- | --- | --- | --- | --- | -------- | ------------ | ----- | --------- | --- | ------------- | --- |
1 2 3 4 5 6 7 20212223 reviewers, and the members of our group in HP Labs—
# of failures
|     |     |     |     |     |     |     | especially, | Eric Anderson, |     | Mahesh | Kallahalla, | Kim | Kee- |
| --- | --- | --- | --- | --- | --- | --- | ----------- | -------------- | --- | ------ | ----------- | --- | ---- |
Figure 16: Availability analysis using a file-system trace; the ton,SusanSpence,RamSwaminathan,andJohnWilkes—
usersofafailednodemovetoafunctioningnode. Thenumbers for offering invaluable feedback that improved the quality
inparenthesesshowtheoverallstorageconsumption,normalized
ofthiswork.
topang-1.
References
wecrash1to7randomnodesandredirectaccessesbythe
| useronafailednodetoanotherrandomnode. |     |       |             |       | Tosimulate   |      |                |         |         |          |             |         |         |
| ------------------------------------- | --- | ----- | ----------- | ----- | ------------ | ---- | -------------- | ------- | ------- | -------- | ----------- | ------- | ------- |
|                                       |     |       |             |       |              |      | [1] Atul Adya, | William | J.      | Bolosky, | Miguel      | Castro, | Ronnie  |
| link failures,                        | in  | which | one to four | nodes | are isolated | from |                |         |         |          |             |         |         |
|                                       |     |       |             |       |              |      | Chaiken,       | Gerald  | Cermak, | John     | R. Douceur, | John    | Howell, |
the rest, we crash 20 to 23 random nodes and throw away Jacob R. Lorch, Marvin Theimer, and Roger Wattenhofer.
futureactivitiesbytheusersonthecrashednodes. Wethen FARSITE:Federated,available,andreliablestorageforan
run the second half of the trace and observe how many of incompletelytrustedenvironment. In5thSymp.onOp.Sys.
|                                                 |     |     |     |     |     |       | Design | and Impl. | (OSDI), | Boston, | MA, | USA, | December |
| ----------------------------------------------- | --- | --- | --- | --- | --- | ----- | ------ | --------- | ------- | ------- | --- | ---- | -------- |
| theusers’sessions7canstillcompletesuccessfully. |     |     |     |     |     | Werun |        |           |         |         |     |      |          |
2002.
simulation2000timesforeachconfigurationwithdifferent
|     |     |     |     |     |     |     | [2] Thomas | Anderson, | Michael | Dahlin, | Jeanna | Neefe, | David |
| --- | --- | --- | --- | --- | --- | --- | ---------- | --------- | ------- | ------- | ------ | ------ | ----- |
randomseedsandaveragetheresults.
|        |          |     |          |             |               |     | Patterson, | Drew | Roselli, | and Randolph | Wang.    |          | Serverless |
| ------ | -------- | --- | -------- | ----------- | ------------- | --- | ---------- | ---- | -------- | ------------ | -------- | -------- | ---------- |
| Figure | 16 shows | the | results. | For network | partitioning, |     |            |      |          |              |          |          |            |
|        |          |     |          |             |               |     | Network    | File | Systems. | In 15th      | Symp. on | Op. Sys. | Princi-    |
Pangaeawinsbyahugemargin;itshowsnear-100%avail-
ples(SOSP),pages109–126,CopperMountain,CO,USA,
| ability thanks       | to  | pervasive | replication,   | whereas | the         | other | December1995.                                       |     |             |                 |     |             |      |
| -------------------- | --- | --------- | -------------- | ------- | ----------- | ----- | --------------------------------------------------- | --- | ----------- | --------------- | --- | ----------- | ---- |
| configurations       |     | must rely | on remote      | servers | for much    | of    |                                                     |     |             |                 |     |             |      |
|                      |     |           |                |         |             |       | [3] WilliamJ.Bolosky,JohnR.Douceur,DavidEly,andMar- |     |             |                 |     |             |      |
| the file operations. |     | For       | node failures, | the     | differences | are   |                                                     |     |             |                 |     |             |      |
|                      |     |           |                |         |             |       | vin Theimer.                                        |     | Feasibility | of a Serverless |     | Distributed | File |
smaller. However, we can still observe that for the same System Deployed on an Existing Set of Desktop PCs. In
storageoverhead,Pangaeaoffersbetteravailability. Conf. on Measurement and Modeling of Comp. Sys. (SIG-
METRICS),pages34–43,SantaClara,CA,USA,June2000.
|     |     |     |     |     |     |     | [4] B. Callaghan, |     | B. Pawlowski, | and | P. Staubach. |     | RFC1813: |
| --- | --- | --- | --- | --- | --- | --- | ----------------- | --- | ------------- | --- | ------------ | --- | -------- |
8 Conclusions NFSversion3protocolspecification. http://www.faqs.org-
/rfcs/rfc1813.html,June1995.
Pangaeaisawide-areafilesystemthattargetstheneedsfor [5] PeiCaoandSandyIrani.Cost-AwareWWWproxycaching
algorithms.In1stUSENIXSymp.onInternetTech.andSys.
dataaccessandsharingofdistributedcommunitiesofusers.
(USITS),Monterey,CA,USA,December1997.
| Itfederatescommoditycomputersprovidedbyusers. |     |     |     |                   |     | Pan- |                                                     |     |     |     |     |     |     |
| --------------------------------------------- | --- | --- | --- | ----------------- | --- | ---- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
|                                               |     |     |     |                   |     |      | [6] FrankDabek,FransKaashoek,DavidKarger,RobertMor- |     |     |     |     |     |     |
| gaeaisbuiltonthreedesignprinciples:           |     |     |     | 1)pervasiverepli- |     |      |                                                     |     |     |     |     |     |     |
ris,andIonStoica.Wide-areacooperativestoragewithCFS.
| cation to | provide | low-access | latency | and | high availability, |     |         |       |             |            |         |       |      |
| --------- | ------- | ---------- | ------- | --- | ------------------ | --- | ------- | ----- | ----------- | ---------- | ------- | ----- | ---- |
|           |         |            |         |     |                    |     | In 18th | Symp. | on Op. Sys. | Principles | (SOSP), | pages | 202– |
2)randomizedgraph-basedreplicamanagementthatadapts
215,LakeLouise,AB,Canada,October2001.
| to changes        | in the | system      | and conserves |             | WAN bandwidth, |        |                      |         |                                 |            |      |         |     |
| ----------------- | ------ | ----------- | ------------- | ----------- | -------------- | ------ | -------------------- | ------- | ------------------------------- | ---------- | ---- | ------- | --- |
|                   |        |             |               |             |                |        | [7] Alan J.          | Demers, | Daniel                          | H. Greene, | Carl | Hauser, | Wes |
| and 3) optimistic |        | consistency |               | that allows | users to       | access |                      |         |                                 |            |      |         |     |
|                   |        |             |               |             |                |        | Irish,andJohnLarson. |         | Epidemicalgorithmsforreplicated |            |      |         |     |
dataatanytime,fromanywhere.
|     |     |     |     |     |     |     | database | maintenance. |     | In 6th | Symp. on | Princ. | of Distr. |
| --- | --- | --- | --- | --- | --- | --- | -------- | ------------ | --- | ------ | -------- | ------ | --------- |
TheevaluationofPangaeashowsthatPangaeaisasfast Comp.(PODC),pages1–12, Vancouver, BC,Canada, Au-
| and as efficient |          | as other  | distributed | file systems, | even             | in    | a gust1987.                    |     |             |              |                        |        |           |
| ---------------- | -------- | --------- | ----------- | ------------- | ---------------- | ----- | ------------------------------ | --- | ----------- | ------------ | ---------------------- | ------ | --------- |
|                  |          |           |             |               |                  |       | [8] ArmandoFoxandEricA.Brewer. |     |             |              | Harvest,yield,andscal- |        |           |
| LAN. The         | benefits | of        | pervasive   | replication   | and the          | adap- |                                |     |             |              |                        |        |           |
|                  |          |           |             |               |                  |       | able tolerant                  |     | systems. In | 6th Workshop |                        | on Hot | Topics in |
| tive graph-based |          | protocols | become      | clear         | in heterogeneous |       |                                |     |             |              |                        |        |           |
OperatingSystems(HOTOS-VI),pages174–178,RioRico,
| environments | that | are | typical of | the Internet | and large | in- |          |       |       |                        |     |     |     |
| ------------ | ---- | --- | ---------- | ------------ | --------- | --- | -------- | ----- | ----- | ---------------------- | --- | --- | --- |
|              |      |     |            |              |           |     | AZ, USA, | March | 1999. | http://www.csd.uch.gr/ |     |     |     |
tranets. Intheseenvironments,Pangaeaoutperformsexist- ˜markatos/papers/hotos.ps.
7Wedefineasessiontobeeitheradirectoryoperation(i.e.,unlink), [9] P.Francis,S.Jamin,C.Jin,Y.Jin,D.Raz,Y.Shavitt,and
|             |           |       |                   |     | open      |     | L. Zhang. | IDMaps: | A   | global Internet | host | distance | esti- |
| ----------- | --------- | ----- | ----------------- | --- | --------- | --- | --------- | ------- | --- | --------------- | ---- | -------- | ----- |
| or a series | of system | calls | to a file between | and | including | and |           |         |     |                 |      |          |       |
close. Ifanyoneofthesystemcallsfails,weconsiderthesessionto mation service. IEEE/ACM Trans. on Networking (TON),
| fail. |     |     |     |     |     |     | 9(5):525–540,October2001. |     |     |     |     |     |     |
| ----- | --- | --- | --- | --- | --- | --- | ------------------------- | --- | --- | --- | --- | --- | --- |

[10] RichardA.Golding,DarrellD.E.Long,andJohnWilkes. [24] KonstantinosPsounisandBalajiPrabhakar. Arandomized
Therefdbmsdistributedbibliographicdatabasesystem. In web-cache replacement scheme. In Infocom, Anchorage,
| USENIXWinterTech.Conf.,SanFrancisco,CA,USA,Jan- |     |     |     |     |     |     | AL,USA,April2001. |     |     |     |     |     |     |     |
| ----------------------------------------------- | --- | --- | --- | --- | --- | --- | ----------------- | --- | --- | --- | --- | --- | --- | --- |
uary1994. [25] DavidH.Ratner. Roam: AScalableReplicationSystemfor
[11] JimGray. AcensusofTandemsystemavailabilitybetween Mobile and Distributed Computing. PhD thesis, UC Los
1985and1990. IEEETrans.onReliability,39(4):409–418, Angeles,1998. Tech.Report.no.UCLA-CSD-970044.
October1990.
|     |     |     |     |     |     |     | [26] LuigiRizzo. |     | Dummynet, | http://info.iet.unipi. |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------------- | --- | --------- | ---------------------- | --- | --- | --- | --- |
[12] Ha˚kanGrahnandPerStenstro¨mandMichelDubois.Imple- it/˜luigi/ip_dummynet/,2001.
| mentation                            | and | evaluation | of update-based |     | cache         | protocols |                                      |     |     |     |     |                        |     |     |
| ------------------------------------ | --- | ---------- | --------------- | --- | ------------- | --------- | ------------------------------------ | --- | --- | --- | --- | ---------------------- | --- | --- |
|                                      |     |            |                 |     |               |           | [27] AntonyRowstronandPeterDruschel. |     |     |     |     | Storagemanagement      |     |     |
| underrelaxedmemoryconsistencymodels. |     |            |                 |     | FutureGenera- |           |                                      |     |     |     |     |                        |     |     |
|                                      |     |            |                 |     |               |           | andcachinginPAST,alarge-scale,       |     |     |     |     | persistentpeer-to-peer |     |     |
tionComputerSystems,11(3),June1995.
storageutility.In18thSymp.onOp.Sys.Principles(SOSP),
| [13] John | Howard, | Michael | Kazar, | Sherri | Menees, | David |     |     |     |     |     |     |     |     |
| --------- | ------- | ------- | ------ | ------ | ------- | ----- | --- | --- | --- | --- | --- | --- | --- | --- |
pages188–201,LakeLouise,AB,Canada,October2001.
Nichols,M.Satyanarayanan,RobertSidebotham,andMica-
helWest.Scaleandperformanceinadistributedfilesystem. [28] Yasushi Saito and Christos Karamanolis. Replica consis-
ACMTrans.onComp.Sys.(TOCS),6(1),1988. tency management in the pangaea wide-area file system.
|         |                |     |           |       |                     |     | Technicalreport,HPLabs,2002. |        |     |         | Tobepublished. |     |     |        |
| ------- | -------------- | --- | --------- | ----- | ------------------- | --- | ---------------------------- | ------ | --- | ------- | -------------- | --- | --- | ------ |
| [14] M. | Ji, E. Felten, | R.  | Wang, and | J. P. | Singh. Archipelago: |     |                              |        |     |         |                |     |     |        |
|         |                |     |           |       |                     |     | [29] Yasushi                 | Saito, |     | Jeffrey | Mogul,         | and | Ben | Vergh- |
anisland-basedfilesystemforhighlyavailableandscalable
|     |     |     |     |     |     |     | ese. | A   | Usenet | performance | study, | September |     | 1998. |
| --- | --- | --- | --- | --- | --- | --- | ---- | --- | ------ | ----------- | ------ | --------- | --- | ----- |
Internetservices.InUSENIXWindowsSystemsSymposium,
http://www.research.digital.com/wrl/
August2000.
projects/newsbench/.
| [15] Leonard | Kawell | Jr., | Steven Beckhart, |     | Timoty | Halvorsen, |     |     |     |     |     |     |     |     |
| ------------ | ------ | ---- | ---------------- | --- | ------ | ---------- | --- | --- | --- | --- | --- | --- | --- | --- |
Raymond Ozzie, and Irene Greif. Replicated document [30] SleepycatSoftware. TheBerkeleydatabase,2002. http:
| management |     | in a group | communication |     | system. | In Conf. | //sleepycat.com. |     |     |     |     |     |     |     |
| ---------- | --- | ---------- | ------------- | --- | ------- | -------- | ---------------- | --- | --- | --- | --- | --- | --- | --- |
onComp.-SupportedCoop.Work(CSCW),ChapelHill,NC,
|     |     |     |     |     |     |     | [31] Susan | Spence, | Erik | Riedel, | and Magnus | Karlsson. |     | Adap- |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ------- | ---- | ------- | ---------- | --------- | --- | ----- |
USA,October1988.
tiveconsistency—patternsofsharinginanetworkedworld.
[16] MinkyongKim,LandonP.Cox,andBrianD.Noble.Safety, Technical Report HPL-SSP-2002-10, HP Labs, February
| visibility, | and | performance | in  | a wide-area | file | system. | In 2002. |     |     |     |     |     |     |     |
| ----------- | --- | ----------- | --- | ----------- | ---- | ------- | -------- | --- | --- | --- | --- | --- | --- | --- |
USENIXConf.onFileandStorageSys.(FAST),Monterey,
|     |     |     |     |     |     |     | [32] Douglas | B.  | Terry, | Marvin | M. Theimer, | Karin | Petersen, |     |
| --- | --- | --- | --- | --- | --- | --- | ------------ | --- | ------ | ------ | ----------- | ----- | --------- | --- |
CA,January2002.Usenix.
AlanJ.Demers,MikeJ.Spreitzer,andCarlH.Hauser.Man-
[17] John Kubiatowicz, David Bindel, Yan Chen, Steven Czer- agingupdateconflictsinBayou,aweaklyconnectedrepli-
winski, Patrick Eaton, Dennis Geels, Ramakrishna Gum- catedstoragesystem. In15thSymp.onOp.Sys.Principles
madi, Sean Rhea, Hakim Weatherspoon, Westley Weimer, (SOSP),pages172–183,CopperMountain,CO,USA,De-
| Chris | Wells, | and Ben | Zhao. | OceanStore: | An  | architecture | cember1995. |     |     |     |     |     |     |     |
| ----- | ------ | ------- | ----- | ----------- | --- | ------------ | ----------- | --- | --- | --- | --- | --- | --- | --- |
forglobal-scalepersistentstorage.In9thInt.Conf.onArch.
|     |     |     |     |     |     |     | [33] ChandramohanThekkath,TimothyMann,andEdwardLee. |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
SupportforProg.Lang.andOp.Sys.(ASPLOS-IX),pages
|     |     |     |     |     |     |     | Frangipani:ascalabledistributedfilesystem. |     |     |     |     |     | In16thSymp. |     |
| --- | --- | --- | --- | --- | --- | --- | ------------------------------------------ | --- | --- | --- | --- | --- | ----------- | --- |
190–201,Cambridge,MA,USA,November2000. on Op. Sys. Principles (SOSP), pages 224–237, St. Malo,
[18] P.KumarandM.Satyanarayanan. Flexibleandsaferesolu- France,October1997.
| tionoffileconflicts. |     |     | InUSENIXWinterTech.Conf.,pages |     |     |     |              |     |          |       |         |     |              |     |
| -------------------- | --- | --- | ------------------------------ | --- | --- | --- | ------------ | --- | -------- | ----- | ------- | --- | ------------ | --- |
|                      |     |     |                                |     |     |     | [34] Robbert | van | Renesse, | Yaron | Minsky, | and | Mark Hayden. |     |
95–106,NewOrleans,LA,USA,January1995.
|     |     |     |     |     |     |     | A gossip-style |     | failure | detection |     | service. | In IFIP | Int. |
| --- | --- | --- | --- | --- | --- | --- | -------------- | --- | ------- | --------- | --- | -------- | ------- | ---- |
[19] David Mazie`res. A toolkit for user-level file systems. In Conf. on Dist. Sys. Platforms and Open Dist. (Middle-
USENIXAnnualTech.Conf.,Boston,MA,USA,June2001. ware),1998.http://www.cs.cornell.edu/Info/
[20] LilyB.Mummert,MariaR.Ebling,andM.Satyanarayanan. People/rvr/papers/pfd/pfd.ps.
| Exploitingweakconnectivityformobilefileaccess. |     |     |     |     |     | In15th |                    |     |                                |     |     |     |     |     |
| ---------------------------------------------- | --- | --- | --- | --- | --- | ------ | ------------------ | --- | ------------------------------ | --- | --- | --- | --- | --- |
|                                                |     |     |     |     |     |        | [35] WernerVogels. |     | FilesystemusageinWindowsNT4.0. |     |     |     |     | In  |
Symp.onOp.Sys.Principles(SOSP),pages143–155,Cop-
|     |     |     |     |     |     |     | 17th | Symp. | on Op. | Sys. Principles |     | (SOSP), | pages 93–109, |     |
| --- | --- | --- | --- | --- | --- | --- | ---- | ----- | ------ | --------------- | --- | ------- | ------------- | --- |
perMountain,CO,USA,December1995.
KiawahIsland,SC,USA,December1999.
[21] AthichaMuthitacharoen,BenjieChen,andDavidMazie`res.
|     |     |     |     |     |     |     | [36] BruceWalker,GeraldPopek,RobertEnglish,CharlesKline, |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | -------------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
Alow-bandwidthnetworkfilesystem.In18thSymp.onOp.
|                                    |     |     |     |     |             |     | and | Greg Thiel. |     | The Locus | distributed | operating |     | system. |
| ---------------------------------- | --- | --- | --- | --- | ----------- | --- | --- | ----------- | --- | --------- | ----------- | --------- | --- | ------- |
| Sys.Principles(SOSP),pages174–187, |     |     |     |     | LakeLouise, | AB, |     |             |     |           |             |           |     |         |
In9thSymp.onOp.Sys.Principles(SOSP),pages49–70,
Canada,October2001.
BrettonWoods,NH,USA,October1983.
| [22] Athicha | Muthitacharoen, |            | Robert       | Morris, | Thomer       | M. Gil,   |                                    |            |          |         |        |                  |            |     |
| ------------ | --------------- | ---------- | ------------ | ------- | ------------ | --------- | ---------------------------------- | ---------- | -------- | ------- | ------ | ---------------- | ---------- | --- |
|              |                 |            |              |         |              |           | [37] Haifeng                       | Yu         | and Amin | Vahdat. | The    | Costs            | and Limits | of  |
| and          | Benjie          | Chen. Ivy: | A read/write |         | peer-to-peer | file sys- |                                    |            |          |         |        |                  |            |     |
|              |                 |            |              |         |              |           | AvailabilityforReplicatedServices. |            |          |         |        | In18thSymp.onOp. |            |     |
| tem.         | In 5th          | Symp. on   | Op. Sys.     | Design  | and Impl.    | (OSDI),   |                                    |            |          |         |        |                  |            |     |
|              |                 |            |              |         |              |           | Sys.                               | Principles | (SOSP),  | pages   | 29–42, | Lake             | Louise,    | AB, |
Boston,MA,USA,December2002.
Canada,October2001.
| [23] D.       | Scott Parker, | Gerald        | Popek, | Gerard           | Rudisin, | Allen  |     |     |     |     |     |     |     |     |
| ------------- | ------------- | ------------- | ------ | ---------------- | -------- | ------ | --- | --- | --- | --- | --- | --- | --- | --- |
| Stoughton,    |               | Bruce Walker, | Evelyn | Walton,          | Johanna  | Chow,  |     |     |     |     |     |     |     |     |
| DavidEdwards, |               | StephenKiser, |        | andCharlesKline. |          | Detec- |     |     |     |     |     |     |     |     |
| tion          | of mutual     | inconsistency | in     | distributed      | systems. | IEEE   |     |     |     |     |     |     |     |     |
Trans.onSoftwareEngineering,SE-9(3):240–247,1983.
