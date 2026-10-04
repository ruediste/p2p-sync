|     |     |     | Ivy: | A Read/Write |     | Peer-to-Peer |     | File | System |     |     |     |     |
| --- | --- | --- | ---- | ------------ | --- | ------------ | --- | ---- | ------ | --- | --- | --- | --- |
AthichaMuthitacharoen,RobertMorris,ThomerM. Gil,andBenjieChen
fathicha,rtm,thomer,benjieg@lcs.mit.edu
|     |     |     |     | MITLaboratoryfor |     |     | ComputerScience |     |     |     |     |     |     |
| --- | --- | --- | --- | ---------------- | --- | --- | --------------- | --- | --- | --- | --- | --- | --- |
200TechnologySquare,Cambridge,MA02139.
Abstract
machineshavenotbeencompromisedbyoutsiders;thus
|          |            |            |     |              |     |              | there should | be  | a way | to ignore | or un-do | some | or all |
| -------- | ---------- | ---------- | --- | ------------ | --- | ------------ | ------------ | --- | ----- | --------- | -------- | ---- | ------ |
| Ivy is a | multi-user | read/write |     | peer-to-peer |     | file system. |              |     |       |           |          |      |        |
modificationsbyaparticipantrevealedtobeuntrustwor-
| Ivy has | no centralized |     | or dedicated |     | components, | and |     |     |     |     |     |     |     |
| ------- | -------------- | --- | ------------ | --- | ----------- | --- | --- | --- | --- | --- | --- | --- | --- |
thy.Finally,distributingfile-systemdataovermanyhosts
| it provides | useful | integrity | properties |     | without | requiring |           |     |        |           |                   |     |     |
| ----------- | ------ | --------- | ---------- | --- | ------- | --------- | --------- | --- | ------ | --------- | ----------------- | --- | --- |
|             |        |           |            |     |         |           | meansthat | the | system | mayhaveto | copewithoperation |     |     |
userstofullytrusteithertheunderlyingpeer-to-peerstor- whilepartitioned,andmayhavetohelpapplicationsre-
agesystemortheotherusersofthefilesystem.
pairconflictingupdatesmadeduringapartition.
AnIvyfilesystemconsistssolelyofasetoflogs,one
|                       |     |     |                 |     |              |     | Ivy uses | logs | to solve | the | problems | described | above. |
| --------------------- | --- | --- | --------------- | --- | ------------ | --- | -------- | ---- | -------- | --- | -------- | --------- | ------ |
| logperparticipant.Ivy |     |     | storesitslogsin |     | theDHashdis- |     |          |      |          |     |          |           |        |
Eachparticipantwithwriteaccesstoafilesystemmain-
| tributed | hash table. | Each | participant |     | finds | data by con- |             |            |     |      |           |        |           |
| -------- | ----------- | ---- | ----------- | --- | ----- | ------------ | ----------- | ---------- | --- | ---- | --------- | ------ | --------- |
|          |             |      |             |     |       |              | tains a log | of changes |     | they | have made | to the | file sys- |
sultingalllogs,butperformsmodificationsbyappending
|     |     |     |     |     |     |     | tem. Participants |     | scan | all the | logs (most | recent | record |
| --- | --- | --- | --- | --- | --- | --- | ----------------- | --- | ---- | ------- | ---------- | ------ | ------ |
onlytoitsownlog.ThisarrangementallowsIvytomain-
first)tolookupfiledataandmeta-data.Eachparticipant
tainmeta-dataconsistencywithoutlocking.Ivyuserscan
maintainsaprivatesnapshottoavoidscanningallbutthe
choosewhichotherlogstotrust,anappropriatearrange-
|     |     |     |     |     |     |     | most recent | log | entries. | The | use of per-participantlogs, |     |     |
| --- | --- | --- | --- | --- | --- | --- | ----------- | --- | -------- | --- | --------------------------- | --- | --- |
mentinasemi-openpeer-to-peersystem.
|     |     |     |     |     |     |     | instead of | shared | mutable | data | structures, | allows | Ivy to |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ------ | ------- | ---- | ----------- | ------ | ------ |
Ivypresentsapplicationswithaconventionalfilesys-
avoidusinglockstoprotectmeta-data.Ivystoresitslogs
| tem interface. |     | When the | underlying |     | network | is fully |     |     |     |     |     |     |     |
| -------------- | --- | -------- | ---------- | --- | ------- | -------- | --- | --- | --- | --- | --- | --- | --- |
inDHash,soaparticipant’slogsareavailableevenwhen
| connected, | Ivy | provides | NFS-like |     | semantics, | such as |     |     |     |     |     |     |     |
| ---------- | --- | -------- | -------- | --- | ---------- | ------- | --- | --- | --- | --- | --- | --- | --- |
theparticipantisnot.
| close-to-openconsistency.Ivy |             |          |                      | detects | conflicting | modi-        |               |              |          |                        |        |      |           |
| ---------------------------- | ----------- | -------- | -------------------- | ------- | ----------- | ------------ | ------------- | ------------ | -------- | ---------------------- | ------ | ---- | --------- |
|                              |             |          |                      |         |             |              | Ivy resists   | attacks      |          | from non-participants, |        |      | and from  |
| fications                    | made        | during a | partition,           | and     | provides    | relevant     |               |              |          |                        |        |      |           |
|                              |             |          |                      |         |             |              | corrupt DHash |              | servers, | by cryptographically   |        |      | verifying |
| version                      | information | to       | application-specific |         |             | conflict re- |               |              |          |                        |        |      |           |
|                              |             |          |                      |         |             |              | the data      | it retrieves | from     | DHash.                 | An Ivy | user | can cope  |
solvers.Performancemeasurementsonawide-areanet-
work show that Ivy is two to three times slower than with attacks from other Ivy users by choosing which
otherlogstoreadwhenlookingfordata,andthuswhich
NFS.
otheruserstotrust.Ignoringalogthatwasoncetrusted
|     |     |     |     |     |     |     | might discard | useful | information |     | or critical |     | meta-data; |
| --- | --- | --- | --- | --- | --- | --- | ------------- | ------ | ----------- | --- | ----------- | --- | ---------- |
1 Introduction
|     |     |     |     |     |     |     | Ivy provides | tools | to  | selectively | ignore | logs | and to fix |
| --- | --- | --- | --- | --- | --- | --- | ------------ | ----- | --- | ----------- | ------ | ---- | ---------- |
brokenmeta-data.
| This paper | describes | Ivy, | a distributed |     | read/write | net- |     |     |     |     |     |     |     |
| ---------- | --------- | ---- | ------------- | --- | ---------- | ---- | --- | --- | --- | --- | --- | --- | --- |
IvyprovidesNFS-likefilesystemsemanticswhenthe
| work file | system. | Ivy presents |     | a single | file | system im- |     |     |     |     |     |     |     |
| --------- | ------- | ------------ | --- | -------- | ---- | ---------- | --- | --- | --- | --- | --- | --- | --- |
age that appears much like an NFS [33] file system. In underlyingnetworkisfullyconnected.Forexample,Ivy
|     |     |     |     |     |     |     | provides | close-to-open |     | consistency. | In  | the case | of net- |
| --- | --- | --- | --- | --- | --- | --- | -------- | ------------- | --- | ------------ | --- | -------- | ------- |
contrasttoNFS,Ivydoesnotrequireadedicatedserver;
workpartition,DHashreplicationmayallowparticipants
instead,itstoresalldataandmeta-dataintheDHash[9]
|     |     |     |     |     |     |     | to modifyfiles |     | in multiple | partitions. | Ivy’slogs |     | contain |
| --- | --- | --- | --- | --- | --- | --- | -------------- | --- | ----------- | ----------- | --------- | --- | ------- |
peer-to-peerblockstoragesystem.DHashcandistribute
versionvectorsthatallowittodetectconflictingupdates
andreplicateblocks,givingIvythepotentialtobehighly
available. One possible application of Ivy is to support after partitions merge, and to provide version informa-
tiontoapplication-specificconflictresolvers.
distributedprojectswithlooselyaffiliatedparticipants.
Building a shared read-write peer-to-peer file system The Ivy implementation uses a local NFS loop-back
|     |     |     |     |     |     |     | server [22] | to providean |     | ordinary | file | system | interface. |
| --- | --- | --- | --- | --- | --- | --- | ----------- | ------------ | --- | -------- | ---- | ------ | ---------- |
posesanumberofchallenges.First,multipledistributed
writersmakemaintenanceofconsistentfilesystemmeta- Performance is within a factor of two to three of NFS.
datadifficult.Second,unreliableparticipantsmakelock- The main performance bottlenecks are network latency
|              |              |          |              |               |         |             | and the        | cost of | generating |     | digital signatures |     | on data |
| ------------ | ------------ | -------- | ------------ | ------------- | ------- | ----------- | -------------- | ------- | ---------- | --- | ------------------ | --- | ------- |
| ing an       | unattractive | approach |              | for achieving |         | meta-data   |                |         |            |     |                    |     |         |
| consistency. | Third,       | the      | participants |               | may not | fully trust | storedinDHash. |         |            |     |                    |     |         |
each other, or may not trust that the other participants’ This paper makes three contributions. It describes a

| read/write      | peer-to-peer | storage   | system;   | previous |         | peer- |     | log-head |     |     |     |     |     |     |
| --------------- | ------------ | --------- | --------- | -------- | ------- | ----- | --- | -------- | --- | --- | --- | --- | --- | --- |
| to-peer systems | have         | supported | read-only |          | data or | data  |     |          |     |     |     |     |     |     |
viewblock
| writeable                   | by a single | publisher. | It describes             |     | how | to de- |     |     |     |     |     |     |     |     |
| --------------------------- | ----------- | ---------- | ------------------------ | --- | --- | ------ | --- | --- | --- | --- | --- | --- | --- | --- |
| signa distributedfilesystem |             |            | withusefulintegrityprop- |     |     |        |     |     |     |     |     |     |     |     |
log-head
| ertiesbasedonacollectionofuntrustedcomponents.Fi- |              |        |             |      |        |      | .   |     |     |     |     |     |     |     |
| ------------------------------------------------- | ------------ | ------ | ----------- | ---- | ------ | ---- | --- | --- | --- | --- | --- | --- | --- | --- |
| nally, it                                         | explores the | use of | distributed | hash | tables | as a | .   |     |     |     |     |     |     |     |
.
building-blockformoresophisticatedsystems.
| Section         | 2 describes | Ivy’s | design.  | Section  | 3 discusses |     |     |     |     |     |     |     |     |     |
| --------------- | ----------- | ----- | -------- | -------- | ----------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| the consistency | semantics   |       | that Ivy | presents | to applica- |     |     |     |     |     |     |     |     |     |
logrecords
tions.Section4presentstoolsfordealingwithmalicious
participants.Sections5and6describeIvy’simplementa- Figure1:ExampleIvyviewandlogs.WhiteboxesareDHash
tionandperformance.Section7discussesrelatedwork, content-hashblocks;grayboxesarepublic-keyblocks.
andSection8concludes.
2 Design
ittodenyingtheexistenceofablockorproducingastale
copyofapublic-keyblock.
An Ivy file system consists of a set of logs, one log Ivy participants communicate only via DHash stor-
| per participant. | A   | log contains | all | of one | participant’s |     |     |     |     |     |     |     |     |     |
| ---------------- | --- | ------------ | --- | ------ | ------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
age;theydon’tcommunicatedirectlywitheachotherex-
| changes | to file system | data | and meta-data. |     | Each partic- |     |     |     |     |     |     |     |     |     |
| ------- | -------------- | ---- | -------------- | --- | ------------ | --- | --- | --- | --- | --- | --- | --- | --- | --- |
ceptwhensettingupanewfilesystem.IvyusesDHash
| ipant appends      | only | to its    | own log, | but reads | from  | all  |              |     |                    |          |      |          |            |        |
| ------------------ | ---- | --------- | -------- | --------- | ----- | ---- | ------------ | --- | ------------------ | -------- | ---- | -------- | ---------- | ------ |
|                    |      |           |          |           |       |      | content-hash |     | blocks             | to store | log  | records. | Ivy stores | the    |
| logs. Participants |      | store log | records  | in the    | DHash | dis- |              |     |                    |          |      |          |            |        |
|                    |      |           |          |           |       |      | DHash        | key | of a participant’s |          | most | recent   | log        | record |
tributed hash system, which provides per-record repli- in a DHash block called the log-head; the log-head is
| cation and | authentication. |     | Each participant |     | maintains | a   |              |     |        |         |     |             |     |        |
| ---------- | --------------- | --- | ---------------- | --- | --------- | --- | ------------ | --- | ------ | ------- | --- | ----------- | --- | ------ |
|            |                 |     |                  |     |           |     | a public-key |     | block, | so that | the | participant | can | update |
mutableDHashrecord(calledalog-head)thatpointsto
|     |     |     |     |     |     |     | its value | without | changing |     | its key. | Each | Ivy participant |     |
| --- | --- | --- | --- | --- | --- | --- | --------- | ------- | -------- | --- | -------- | ---- | --------------- | --- |
theparticipant’smostrecentlogrecord.Ivyusesversion
|     |     |     |     |     |     |     | caches | content-hash |     | blocks | locally | without | fear | of us- |
| --- | --- | --- | --- | --- | --- | --- | ------ | ------------ | --- | ------ | ------- | ------- | ---- | ------ |
vectors[27]toimposeatotalorderonlogrecordswhen
ingstaledata,sincecontent-hashblocksareimmutable.
reading from multiple logs. To avoid the expense of re- AnIvyparticipantdoesnotcacheotherparticipants’log-
| peatedly | reading the | whole | log, each | participant |     | main- |     |     |     |     |     |     |     |     |
| -------- | ----------- | ----- | --------- | ----------- | --- | ----- | --- | --- | --- | --- | --- | --- | --- | --- |
headblocks,sincetheymaychange.
tainsaprivatesnapshotsummarizingthefilesystemstate
|                         |     |     |     |     |     |     | Ivy      | uses | DHash  | through |           | a simple | interface: |         |
| ----------------------- | --- | --- | --- | --- | --- | --- | -------- | ---- | ------ | ------- | --------- | -------- | ---------- | ------- |
| asofarecentpointintime. |     |     |     |     |     |     | put(key, |      | value) |         | get(key). |          |            |         |
|                         |     |     |     |     |     |     |          |      |        | and     |           |          | Ivy        | assumes |
TheIvyimplementationactsasalocalloop-backNFS
that,withinanygivennetworkpartition,DHashprovides
v3[6]server,incooperationwithahost’sin-kernelNFS write-read consistency; that is, if put(k, v) com-
clientsupport.Consequently,Ivypresentsfilesystemse-
|     |     |     |     |     |     |     | pletes, | a subsequent |     | get(k) | will | yield | v. The | current |
| --- | --- | --- | --- | --- | --- | --- | ------- | ------------ | --- | ------ | ---- | ----- | ------ | ------- |
manticsmuchlikethoseofanNFSv3fileserver.
|     |     |     |     |     |     |     | DHash        | implementation |          | does       | not | guarantee | write-read |     |
| --- | --- | --- | --- | --- | --- | --- | ------------ | -------------- | -------- | ---------- | --- | --------- | ---------- | --- |
|     |     |     |     |     |     |     | consistency; |                | however, | techniques |     | are known | which      | can |
2.1 DHash provide such a guarantee with high probability [19].
|     |     |     |     |     |     |     | These | techniques | require |     | that DHash | replicate |     | data and |
| --- | --- | --- | --- | --- | --- | --- | ----- | ---------- | ------- | --- | ---------- | --------- | --- | -------- |
IvystoresallitsdatainDHash[9].DHashisadistributed
|     |     |     |     |     |     |     | update | it carefully, |     | and | might | significantly |     | decrease |
| --- | --- | --- | --- | --- | --- | --- | ------ | ------------- | --- | --- | ----- | ------------- | --- | -------- |
peer-to-peer hash table mapping keys to arbitrary val- performance. Ivy operates best in a fully connected
ues.DHashstoreseachkey/valuepaironasetofInternet network, though it has support for conflict detection
hostsdeterminedbyhashingthekey.Thispaperrefersto
afteroperatinginapartitionednetwork(seeSection3.4).
aDHashkey/valuepairasaDHashblock.DHashrepli- Ivy would in principle work with other distributed
catesblockstoavoidlosingthemifnodescrash.
hashtables,suchasPAST[32],CAN[29],Tapestry[41],
| DHashensurestheintegrityofeachblockwithoneof |     |     |     |     |     |     | orKademlia[21]. |     |     |     |     |     |     |     |
| -------------------------------------------- | --- | --- | --- | --- | --- | --- | --------------- | --- | --- | --- | --- | --- | --- | --- |
twomethods.Acontent-hashblockrequirestheblock’s
key to be the SHA-1 [10] cryptographic hash of the 2.2 LogDataStructure
| block’s value; | this | allows anyone | fetching |     | the block | to  |     |     |     |     |     |     |     |     |
| -------------- | ---- | ------------- | -------- | --- | --------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
verifythevaluebyensuringthatitsSHA-1hashmatches An Ivy log consists of a linked list of immutable log
thekey.Apublic-keyblockrequirestheblock’skeytobe records.EachlogrecordisaDHashcontent-hashblock.
apublickey,andthevaluetobesignedusingthecorre- Table1 describesfieldscommontoalllogrecords.The
spondingprivatekey.DHashrefusestostoreavaluethat prevfieldcontainsthepreviousrecord’sDHashkey.A
doesnotmatchthekey.Ivycheckstheauthenticityofall participant stores the DHash key of its most recent log
dataitretrievesfromDHash.Thesecheckspreventama- record in its log-head block. The log-head is a public-
liciousorbuggyDHashnodefromforgingdata,limiting keyblock witha fixedDHash key,whichmakesiteasy

Field Use typically derived from arguments in the NFS request.
prev DHashkeyofnextoldestlogrecord The new record’s prev field is the DHash key of the
| head |     | DHashkeyoflog-head |     |     |     |     |             |     |               |     |         |         |        |
| ---- | --- | ------------------ | --- | --- | --- | --- | ----------- | --- | ------------- | --- | ------- | ------- | ------ |
|      |     |                    |     |     |     |     | most recent | log | record. Then, | it  | inserts | the new | record |
seq per-logsequencenumber intoDHash,signsanewlog-headthatpointstothenew
| timestamp |     | timeatwhichrecordwascreated |     |     |     |     |     |     |     |     |     |     |     |
| --------- | --- | --------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
logrecord,andupdatesthelog-headinDHash.
| version |     | versionvector |     |     |     |     |               |     |                |     |     |          |        |
| ------- | --- | ------------- | --- | --- | --- | --- | ------------- | --- | -------------- | --- | --- | -------- | ------ |
|         |     |               |     |     |     |     | The following |     | text describes | how | Ivy | uses the | log to |
performselectedoperations.
Table1:FieldspresentinallIvylogrecords. Filesystemcreation.Ivybuildsanewfilesystemby
creatinganewlogwithanEndrecord,anInoderecord
witharandomi-numberfortherootdirectory,andalog-
forotherparticipantstofind. head. The user then mounts the local Ivy server as an
| A log | record | contains | information | about | a single | file |     |     |     |     |     |     |     |
| ----- | ------ | -------- | ----------- | ----- | -------- | ---- | --- | --- | --- | --- | --- | --- | --- |
NFSfilesystem,usingtherooti-numberastheNFSroot
| systemmodification,andcorrespondsroughlytoanNFS |     |     |     |     |     |     | filehandle. |     |     |     |     |     |     |
| ----------------------------------------------- | --- | --- | --- | --- | --- | --- | ----------- | --- | --- | --- | --- | --- | --- |
operation.Table2describesthetypesoflogrecordsand
Filecreation.Whenanapplicationcreatesanewfile,
thetype-specificfieldseachcontains.
thekernelNFSclientcodesendsthelocalIvyserveran
Log records contain the minimum possible informa- NFS CREATE request. The request contains the direc-
| tion to avoid | unnecessary |     | conflicts | from | concurrent | up- |               |     |              |     |         |     |       |
| ------------- | ----------- | --- | --------- | ---- | ---------- | --- | ------------- | --- | ------------ | --- | ------- | --- | ----- |
|               |             |     |           |      |            |     | tory i-number | and | a file name. | Ivy | appends | an  | Inode |
dates by different participants. For example, a Write log record with a new random i-number and a Link
| log record | contains | the | newly | written data, | but | not the |     |     |     |     |     |     |     |
| ---------- | -------- | --- | ----- | ------------- | --- | ------- | --- | --- | --- | --- | --- | --- | --- |
recordthatcontainsthei-number,thefile’sname,andthe
file’s new length or modification time. These attributes directory’si-number.Ivyreturnsthenewfile’si-number
cannot be computed correctly at the time the Write inafilehandletotheNFSclient.Iftheapplicationthen
recordiscreated,sincethetruestateofthefilewillonly
writesthefile,theNFSclientwillsendaWRITErequest
be known after all concurrent updates are known. Ivy containing the file’s i-number, the written data, and the
computes that information incrementally when travers- fileoffset;IvywillappendaWritelogrecordcontain-
ingthelogs,ratherthanstoringitexplicitlyasisdonein
ingthesameinformation.
| UNIXi-nodes[30]. |      |        |     |            |        |     | Filenamelookup.Systemcallssuchasopen()that |     |     |     |     |     |     |
| ---------------- | ---- | ------ | --- | ---------- | ------ | --- | ------------------------------------------ | --- | --- | --- | --- | --- | --- |
| Ivy records      | file | owners | and | permission | modes, | but |                                            |     |     |     |     |     |     |
refertofilenamestypicallygenerateNFSLOOKUPre-
does not use those attributes to enforce permissions. A quests.ALOOKUPrequestcontainsafilenameandadi-
userwhowishestomakeafileunreadableshouldinstead
rectoryi-number.IvyscansthelogtofindaLinkrecord
encryptthefile’scontents.Ausershouldignorethelogs with the desired directory i-number and file name, and
ofpeoplewhoshouldnotbeallowedtowritetheuser’s returns the file i-number. However, if Ivy first encoun-
data.
|     |     |     |     |     |     |     | ters a Unlink |     | record that | mentions | the | same | directory |
| --- | --- | --- | --- | --- | --- | --- | ------------- | --- | ----------- | -------- | --- | ---- | --------- |
Ivy identifies files and directories using 160-bit i- i-number and name, it returns an NFS error indicating
numbers.Logrecordscontainthei-number(s)ofthefiles
thatthefiledoesnotexist.
or directories they affect. Ivy chooses i-numbers ran- Fileread.AnNFSREADrequestcontainsthefile’si-
domly to minimize the probability of multiple partici- number,anoffsetwithinthefile,andthenumberofbytes
pantsallocatingthesamei-numberfordifferentfiles.Ivy
toread.IvyscansthelogaccumulatingdatafromWrite
usesthe160-biti-numberastheNFSfilehandle. recordswhoserangesoverlaptherangeofthedatatobe
| Ivy keeps | log | records | indefinitely, | because | they | may |             |          |      |        |            |     |         |
| --------- | --- | ------- | ------------- | ------- | ---- | --- | ----------- | -------- | ---- | ------ | ---------- | --- | ------- |
|           |     |         |               |         |      |     | read, while | ignoring | data | hidden | by SetAttr |     | records |
be needed to help recover from a malicious participant thatindicatefiletruncation.
orfromanetworkpartition.
|           |        |     |     |     |     |     | File       | attributes. | Some           | NFS       | requests,    |            | including |
| --------- | ------ | --- | --- | --- | --- | --- | ---------- | ----------- | -------------- | --------- | ------------ | ---------- | --------- |
|           |        |     |     |     |     |     | GETATTR,   | require     | Ivy to         | include   | file         | attributes | in the    |
|           |        |     |     |     |     |     | reply. Ivy | only        | fully supports | the       | file length, |            | file mod- |
| 2.3 Using | theLog |     |     |     |     |     |            |             |                |           |              |            |           |
|           |        |     |     |     |     |     | ification  | time        | (“mtime”),     | attribute | modification |            | time      |
For the moment, consider an Ivy file system with only (“ctime”), andlink count attributes. Ivy computesthese
| one log. | Ivy handles | non-updating |     | NFS | requests | with a |     |     |     |     |     |     |     |
| -------- | ----------- | ------------ | --- | --- | -------- | ------ | --- | --- | --- | --- | --- | --- | --- |
attributesincrementallyasitscansthelog.Afile’slength
singlepassthroughthelog.Requeststhatcausemodifi- is determined by either the write to the highest offset
cation use one or more passes, and then append one or sincethelasttruncation,orbythelasttruncation.Mtime
morerecordstothelog.Ivyscansthelogstartingatthe isdeterminedbythetimestampinthemostrecentrel-
most recently appended record, pointed to by the log- evantlogrecord; Ivy mustreturn correct time attributes
head. Ivy stops scanning the log once it has gathered becauseNFSclientcacheconsistencydependsonit.Ivy
enoughdatatohandletherequest. computes the number of links to a file by counting the
Ivyappendsa recordtoalogasfollows.First,itcre- number of relevant Link records not canceled by Un-
atesalogrecordcontainingadescriptionoftheupdate, linkandRenamerecords.

| Type | Fields |     |     |     |     | Meaning |     |
| ---- | ------ | --- | --- | --- | --- | ------- | --- |
Inode type(file,directory,orsymlink),i-number,mode,owner createnewinode
| Write  | i-number,offset,data              |     |     |     |     | writedatatoafile      |     |
| ------ | --------------------------------- | --- | --- | --- | --- | --------------------- | --- |
| Link   | i-number,i-numberofdirectory,name |     |     |     |     | createadirectoryentry |     |
| Unlink | i-numberofdirectory,name          |     |     |     |     | removeafile           |     |
Rename i-numberofdirectory,name,i-numberofnewdirectory,newfilename renameafile
| Prepare  | i-numberofdirectory,filename |     |     |     |     | forexclusiveoperations |     |
| -------- | ---------------------------- | --- | --- | --- | --- | ---------------------- | --- |
| Cancel   | i-numberofdirectory,filename |     |     |     |     | forexclusiveoperations |     |
| SetAttrs | i-number,changedattributes   |     |     |     |     | changefileattributes   |     |
| End      | none                         |     |     |     |     | endoflog               |     |
Table2:SummaryofIvylogrecordtypes.
Directory listings. Ivy handles READDIR requests adoptthenewviewblock(andnewlynamedfilesystem).
by accumulating all file names from relevant Link log Ivy’slackofsupportforautomaticallyaddingnewusers
| records, taking | more recent | Unlink | and Rename log | toaviewisintentional. |     |     |     |
| --------------- | ----------- | ------ | -------------- | --------------------- | --- | --- | --- |
recordsintoaccount.
2.5 CombiningLogs
2.4 UserCooperation:Views
|     |     |     |     | In an Ivy | file system | with multiple logs, | a participant’s |
| --- | --- | --- | --- | --------- | ----------- | ------------------- | --------------- |
When multiple users write to a single Ivy file system, Ivy server consults all the logs to find relevant infor-
eachsourceofpotentiallyconcurrentupdatesmusthave mation. This means that Ivy must decide how to order
its own log; this paper refers to such sources as partici- the records from different logs. The order should obey
pants.AuserwhousesanIvyfilesystemfrommultiple causality,andallparticipantswiththesameviewshould
hostsconcurrentlymusthaveonelogperhost. choosethesameorder.Ivyorderstherecordsusingaver-
TheparticipantsinanIvyfilesystemagreeonaview: sionvector[27]containedineachlogrecord.
the set of logs that comprise the file system. Ivy makes When an Ivy participant generates a new log record,
management of shared views convenient by providing it includes two pieces of information that are later used
a view block, a DHash content-hash block containing to order the record. The seq field contains a numeri-
pointers to all log-headsin the view. A viewblock also cally increasing sequence number; each log separately
containsthei-numberoftherootdirectory.Aviewblock numbers its records from zero. The version vector field
isimmutable;ifasetofuserswantstoformafilesystem containsatupleU:Vforeachlogintheview(including
withadifferentsetoflogs,theycreateanewviewblock. theparticipant’sownlog),summarizingtheparticipant’s
AusernamesanIvyfilesystemwiththecontent-hash most recent knowledgeof that log. U is the DHash key
keyoftheviewblock;thisisessentiallyaself-certifying of the log-head of the log being described,and V is the
pathname [23]. Users creating a new file system must DHashkeyofthatlog’smostrecentrecord.Inthefollow-
exchange public keys in advance by some out-of-band ingdiscussion,anumericVvaluereferstothesequence
numbercontainedintherecordpointedtobyatuple.
means.Oncetheyknoweachother’spublickeys,oneof
them creates a view block and tells the other users the Ivyorderslogrecordsbycomparingtherecords’ver-
viewblock’sDHashkey. sion vectors. For example, Ivy considers a log record
Ivyusestheviewblockkeytoverifytheviewblock’s with version vector (A:5 B:7) to be earlier in time than
contents;thecontentsarethepublickeysthatnameand a record with version vector (A:6 B:7): the latter vec-
verify the participants’ log-heads. A log-head contains torimpliesthatitscreatorhadseentherecordwith(A:5
a content-hash key that names and verifies the most re- B:7).Twoversionvectorsuandvarecomparableifand
|     |     |     |     | only if u | < v or v < | u or u = v. Otherwise, | u and v |
| --- | --- | --- | --- | --------- | ---------- | ---------------------- | ------- |
centlogrecord.ItisthisreasoningthatallowsIvytover-
ifyithasretrievedcorrectlogrecordsfromtheuntrusted areconcurrent.Forexample,(A:5B:7)and(A:6B:6)are
| DHashstoragesystem.Thisapproachrequiresthatusers |     |     |     | concurrent. |     |     |     |
| ------------------------------------------------ | --- | --- | --- | ----------- | --- | --- | --- |
exercise care when initially using a file system name; Simultaneousoperationsbydifferentparticipantswill
thenameshouldcomefromatrustedsource,ortheuser result inequalor concurrentversionvectors.Ivy orders
shouldinspecttheviewblock andverifythatthepublic equal and concurrent vectors by comparing the public
keysarethoseoftrustedusers.Similarly,whenafilesys- keysofthe two logs.Iftheupdatesaffectthe samefile,
tems’usersdecidetoacceptanewparticipant,theymust perhaps due to a partition, the application may need to
allmakeaconsciousdecisiontotrustthenewuserandto take special action to restore consistency;Section 3 ex-

|     |     |     | directoryinode | directoryinodeblock |     |     |     |     |     |     |     |     |
| --- | --- | --- | -------------- | ------------------- | --- | --- | --- | --- | --- | --- | --- | --- |
2.6.2 BuildingSnapshots
|     |     |     |     | D     | E                |             |           |     |        |      |              |     |
| --- | --- | --- | --- | ----- | ---------------- | ----------- | --------- | --- | ------ | ---- | ------------ | --- |
|     |     |     |     | namen | i-number(cid:13) | In ordinary | operation | Ivy | builds | each | new snapshot | in- |
H(E)
sna p sh o t b l ock c re m e nta ll y. I t s ta rt s b y fe t c h ing a ll l o g r e c or d s ( f r o m a l l
|     |     |     |     | .   | . . |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|     |     |     |     | .   | .   |     |     |     |     |     |     |     |
m e ta - da t a . l o g s in th e v i e w ) n e w er t h a n th e p r e vi o u s s n a p s h o t. I t
i-number(cid:11) H(D) traverses these new records in temporal order. For each
|     |     | .   |           |     |     | i-number | that occurs | in  | the new | log records, | Ivy | main- |
| --- | --- | --- | --------- | --- | --- | -------- | ----------- | --- | ------- | ------------ | --- | ----- |
|     |     | .   | fileinode |     |     |          |             |     |         |              |     |       |
.
|         |     |     |     | F         |     | tainsani-nodeandacopyofthefilecontents.Ivyreads |     |     |     |     |     |     |
| ------- | --- | --- | --- | --------- | --- | ----------------------------------------------- | --- | --- | --- | --- | --- | --- |
| filemap |     |     |     | datablock |     |                                                 |     |     |     |     |     |     |
i-number(cid:13) H(F) the initial copy of the i-node and file contents from the
|     |     |     |     | H(B1) | B1  |     |     |     |     |     |     |     |
| --- | --- | --- | --- | ----- | --- | --- | --- | --- | --- | --- | --- | --- |
.
|     |     | .   |     |                 |     | previoussnapshot,andperformstheoperationindicated |     |     |     |     |     |     |
| --- | --- | --- | --- | --------------- | --- | ------------------------------------------------- | --- | --- | --- | --- | --- | --- |
|     |     | .   |     | H(B2) datablock |     |                                                   |     |     |     |     |     |     |
byeachlogrecordonthisdata.
.
|     |     |     |     | .   | B2  | After processing |     | the | new log | records, | Ivy writes | the |
| --- | --- | --- | --- | --- | --- | ---------------- | --- | --- | ------- | -------- | ---------- | --- |
.
accumulatedi-nodesandfilecontentstoDHash.Thenit
|     |     |     |     |     |     | computesa | newfilemapbychanging |     |     |     | theentries | corre- |
| --- | --- | --- | --- | --- | --- | --------- | -------------------- | --- | --- | --- | ---------- | ------ |
Figure2:Snapshotdatastructure.H(A)istheDHashcontent-
spondingtochangedi-nodesandappendingnewentries.
hashofA.
|     |     |     |     |     |     | Ivy creates | a snapshot |     | block that | contains | the file | map |
| --- | --- | --- | --- | --- | --- | ----------- | ---------- | --- | ---------- | -------- | -------- | --- |
andthefollowingmeta-data:apointertotheviewupon
ploresIvy’ssupportforapplication-specificconflictres- which the snapshot is based, a pointer to the previous
|     |     |     |     |     |     | snapshot, | and a | version | vector | referring | to the most | re- |
| --- | --- | --- | --- | --- | --- | --------- | ----- | ------- | ------ | --------- | ----------- | --- |
olution.
centrecordfromeachlogthatthesnapshotincorporates.
Ivycouldhaveusedasimplermethodoforderinglog
IvystoresthesnapshotblockinDHashunderitscontent-
| records, | such | as a Lamport        | clock | [17]. Version | vectors |           |         |                   |     |          |     |          |
| -------- | ---- | ------------------- | ----- | ------------- | ------- | --------- | ------- | ----------------- | --- | -------- | --- | -------- |
|          |      |                     |       |               |         | hash, and | updates | the participant’s |     | log-head | to  | refer to |
| contain  | more | precise information |       | than Lamport  | clocks  |           |         |                   |     |          |     |          |
thenewsnapshot.
aboutcausality;Ivyusesthatinformationtohelpfixcon-
Anewusermusteitherbuildasnapshotfromscratch,
flictingupdatesafterapartition.Versionvectorshelppre-
startingfromtheearliestrecordineachlog,orcopyan-
ventamaliciousparticipantfromretroactivelychanging
other(trusted)user’ssnapshot.
| its log by | pointing | its | log-head | at a newly-constructed |     |     |     |     |     |     |     |     |
| ---------- | -------- | --- | -------- | ---------------------- | --- | --- | --- | --- | --- | --- | --- | --- |
log;otherparticipants’versionvectorswillstillpointto
2.6.3 UsingSnapshots
| the old | log’s records. | Finally, |     | version vectors | from one |     |     |     |     |     |     |     |
| ------- | -------------- | -------- | --- | --------------- | -------- | --- | --- | --- | --- | --- | --- | --- |
logcouldbeusedtohelprepairanotherlogthathasbeen
|     |     |     |     |     |     | When handling |     | an NFS | request, | Ivy first | traverses | log |
| --- | --- | --- | --- | --- | --- | ------------- | --- | ------ | -------- | --------- | --------- | --- |
damaged.
recordsnewerthanthesnapshot;ifitcannotaccumulate
|     |     |     |     |     |     | enough information  |     | to  | fulfill the             | request, | Ivy finds | the |
| --- | --- | --- | --- | --- | --- | ------------------- | --- | --- | ----------------------- | -------- | --------- | --- |
|     |     |     |     |     |     | missing information |     | in  | the participant’slatest |          | snapshot. |     |
2.6 Snapshots
Ivyfindsinformationinasnapshotbasedoni-number.
| Each Ivy | participant | periodically |     | constructs | a private |     |     |     |     |     |     |     |
| -------- | ----------- | ------------ | --- | ---------- | --------- | --- | --- | --- | --- | --- | --- | --- |
3 ApplicationSemantics
| snapshot | of the | file system | in  | order to avoid | traversing |     |     |     |     |     |     |     |
| -------- | ------ | ----------- | --- | -------------- | ---------- | --- | --- | --- | --- | --- | --- | --- |
theentirelog.Asnapshotcontainstheentirestateofthe ThissectiondescribesthefilesystemsemanticsthatIvy
filesystem.ParticipantsstoretheirsnapshotsinDHashto providestoapplications,focusingprimarilyontheways
makethempersistent.Eachparticipanthasitsownlogi-
|     |     |     |     |     |     | in which | Ivy’s semantics |     | differ | from | those of | an ordi- |
| --- | --- | --- | --- | --- | --- | -------- | --------------- | --- | ------ | ---- | -------- | -------- |
callyprivatesnapshot,butthefactthatthedifferentsnap- naryNFSserver.Sections3.1,3.2,and3.3describeIvy’s
| shots have | largely | identical | contents | means | that DHash |           |      |             |          |     |                    |     |
| ---------- | ------- | --------- | -------- | ----- | ---------- | --------- | ---- | ----------- | -------- | --- | ------------------ | --- |
|            |         |           |          |       |            | semantics | when | the network | provides |     | full connectivity. |     |
automaticallysharestheirstorage. Sections3.4and3.5describewhathappenswhenthenet-
workpartitionsandthenmerges.
2.6.1 SnapshotFormat
|     |     |     |     |     |     | 3.1 Cache | Consistency |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --------- | ----------- | --- | --- | --- | --- | --- |
A snapshot consists of a file map, a set of i-nodes, and In general, an update operation that one Ivy participant
somedatablocks.Eachi-nodeisstoredinitsownDHash has completed is immediately visible to operations that
block.Ani-nodecontainsfileattributesaswellasalist otherparticipantssubsequentlystart.Theexceptionsare
of DHash keys of blocks holding the file’s contents; in that Ivy can’t enforce this notion of consistency during
the case of a directory,the content blocks hold a list of network partitions (see Section 3.4), and that Ivy pro-
name/i-number pairs. The file map records the DHash videsclose-to-openconsistencyforfiledata(seebelow).
key of the i-node associated with each i-number. All MostIvyupdatesareimmediatelyvisiblebecause1)an
of the blocks that make up a snapshot are content-hash Ivy server performing an update waits until DHash has
blocks.Figure2illustratesthesnapshotdatastructure.

acknowledgedreceiptofthenewlogrecordsandthenew Ifdifferentparticipantssimultaneouslywritethesame
log-head before replying to an NFS request, and 2) Ivy bytesinthesamefile,thewriteswilllikelyhaveequalor
asks DHash for the latest log-heads at the start of ev- concurrentversionvectors.RecallthatIvyordersincom-
eryNFSoperation.Ivycacheslogrecords,butthiscache parable version vector by comparing the participants’
neverneedstobeinvalidatedbecausetherecordsareim- publickeys.Whentheconcurrentwriteshavecompleted,
| mutable. |     |     |     |     |     | alltheparticipantswillagreeontheirorder;inthiscase |     |     |     |     |     |     |     |
| -------- | --- | --- | --- | --- | --- | -------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
For file reads and writes, Ivy provides a modified Ivyprovidesthesamesemanticsasanordinaryfilesys-
formofclose-to-openconsistency[13]:ifapplicationA
|     |     |     |     |     | 1   | tem. | It may | be  | the case | that | the | applications | did not |
| --- | --- | --- | --- | --- | --- | ---- | ------ | --- | -------- | ---- | --- | ------------ | ------- |
writes data to a file, then closes the file, and after the intend to generate conflicting writes; Ivy provides both
A
close has completed another application 2 opens the tools to help applications avoid conflicts (Section 3.3)
fileandreadsit,A 2 willseethedatawrittenbyA 1.Ivy and tools to help them detect and resolve unavoidable
| mayalsomakewrittendatavisiblebeforetheclose.Most |     |     |     |     |     | conflicts(Section3.4). |     |     |     |     |     |     |     |
| ------------------------------------------------ | --- | --- | --- | --- | --- | ---------------------- | --- | --- | --- | --- | --- | --- | --- |
NFSclientsandserversprovidethisformofconsistency.
Serialsemanticsforoperationsthataffectdirectoryen-
| Close-to-open      |        | consistency | allows      | Ivy to avoid | fetch- |       |            |            |               |          |                  |           |               |
| ------------------ | ------ | ----------- | ----------- | ------------ | ------ | ----- | ---------- | ---------- | ------------- | -------- | ---------------- | --------- | ------------- |
|                    |        |             |             |              |        | tries | are harder |            | to implement. |          | We               | believe   | that applica- |
| ing every log-head |        | for each    | NFS READ    | operation.   | Ivy    |       |            |            |               |          |                  |           |               |
|                    |        |             |             |              |        | tions | rely       | on the     | file system   |          | to provideserial |           | semantics     |
| caches file        | blocks | along with  | the version | vector       | at the |       |            |            |               |          |                  |           |               |
|                    |        |             |             |              |        | on    | directory  | operations |               | in order | to               | implement | locking.      |
timeeachblockwascached.Whentheapplicationopens
|                   |     |             |           |          |     | Ivy | supports | onetype |     | of locking | through |     | the use of ex- |
| ----------------- | --- | ----------- | --------- | -------- | --- | --- | -------- | ------- | --- | ---------- | ------- | --- | -------------- |
| a file and causes |     | NFS to send | an ACCESS | request, | Ivy |     |          |         |     |            |         |     |                |
clusivecreationofdirectoryentrieswiththesamename
| fetches all | the log-heads | from | DHash. | If no | other log- |          |       |              |     |      |     |           |           |
| ----------- | ------------- | ---- | ------ | ----- | ---------- | -------- | ----- | ------------ | --- | ---- | --- | --------- | --------- |
|             |               |      |        |       |            | (Section | 3.3). | Applications |     | that | use | exclusive | directory |
headshavechangedsinceIvycachedblocksforthefile,
creationforlockingwillworkonIvy.
IvywillsatisfysubsequentREADrequestsfromcached
| blocks without | re-fetching |     | log-heads. | While | the NFS |     |     |     |     |     |     |     |     |
| -------------- | ----------- | --- | ---------- | ----- | ------- | --- | --- | --- | --- | --- | --- | --- | --- |
Inthefollowingparagraphs,wediscussspecificcases
client’sfiledatacacheoftensatisfiesREADsbeforeIvy thatIvydiffersfromacentralizedfilesystemduetothe
sees them, Ivy’s cache helps when an application has lackofserializationofdirectoryoperations.
writtenafileandthenre-readsit;theNFSclientcan’tde-
|     |     |     |     |     |     | Ivy | does | not | serialize | combinations |     | of  | creation and |
| --- | --- | --- | --- | --- | --- | --- | ---- | --- | --------- | ------------ | --- | --- | ------------ |
cidewhethertosatisfythereadsfromthecachedwrites
sinceitdoesn’tknowwhethersomeotherclienthascon- deletion of a directory entry. For example, suppose one
|     |     |     |     |     |     | participant |     | calls | unlink("a"), |     | and | a second | partici- |
| --- | --- | --- | --- | --- | --- | ----------- | --- | ----- | ------------ | --- | --- | -------- | -------- |
currentlywrittenthefile,whereasIvycandecideifthat
|     |     |     |     |     |     | pantcallsrename("a", |     |     |     | "b").Onlyoneoftheseop- |     |     |     |
| --- | --- | --- | --- | --- | --- | -------------------- | --- | --- | --- | ---------------------- | --- | --- | --- |
isthecasebycheckingtheotherlog-heads.
erationscansucceed.Ononehand,Ivyprovidestheex-
| Ivy defers | writing | file data | to DHash | until | NFS tells |     |     |     |     |     |     |     |     |
| ---------- | ------- | --------- | -------- | ----- | --------- | --- | --- | --- | --- | --- | --- | --- | --- |
pectedsemanticsinthesensethatparticipantswhosub-
itthattheapplicationisclosingthefile.Beforeallowing
the close()system callto complete,Ivyappends the sequently look at the file system will agree on the or-
deroftheconcurrentlogrecords,andwillthusagreeon
writtendatatothelogandthenupdatesthelog-head.Ivy
|     |     |     |     |     |     | whichoperationsucceeded.Onthe |     |     |     |     |     | other hand,Ivy | will |
| --- | --- | --- | --- | --- | --- | ----------------------------- | --- | --- | --- | --- | --- | -------------- | ---- |
writesthedatalogrecordstoDHashinparalleltoreduce
|     |     |     |     |     |     | return | a success |     | status | to both | of the | two | systems calls, |
| --- | --- | --- | --- | --- | --- | ------ | --------- | --- | ------ | ------- | ------ | --- | -------------- |
latency.ThisarrangementallowsIvytosignandinserta
eventhoughonlyonetakeseffect,whichwouldnothap-
newlog-headonceperfileclose,ratherthanonceperfile
write. We added a new CLOSE RPC to the NFS client peninanordinaryfilesystem.
to make this work. Ivy also flushes cached writes if it There arecasesinwhichan Ivyparticipantmayread
receivesasynchronousWRITEoraCOMMIT.
logsthatareactivelybeingupdatedandinitiallyseeonly
|     |     |     |     |     |     | a subset | of  | a set | of concurrent |     | updates. |     | A short time |
| --- | --- | --- | --- | --- | --- | -------- | --- | ----- | ------------- | --- | -------- | --- | ------------ |
3.2 Concurrent Updates latertheremainingconcurrentupdatesmightappear,but
|     |     |     |     |     |     | be  | ordered | before | the | first subset. |     | If the updates | affect |
| --- | --- | --- | --- | --- | --- | --- | ------- | ------ | --- | ------------- | --- | -------------- | ------ |
Ordinaryfilesystemshavesimplesemanticswithrespect the same meta-data, observerscould see the file system
to concurrent updates: the results are as if the updates in states that could not have occured in a serial exe-
occurred one at a time in some order. These semantics cution. For example, suppose application A executes
1
are natural and relatively easy to implement in a single create("x") link("x","y"),
|     |     |     |     |     |     |     |     |     | and |     |     |     | and applica- |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ------------ |
fileserver,buttheyaremoredifficultforadecentralized tion A 2 on a different Ivy host concurrently executes
file system. As a result, Ivy’s semantics differ slightly remove("x"). A third application A might first see
3
A
fromthoseofanordinaryfileserver. just the log records from 1, and thus see files x and
The simplest case is that of updates that don’t affect y;if Ivyorders theconcurrentremove() betweenthe
the same data or meta-data. For example, two partici- create() and link(), then A might later observe
3
pantsmayhavecreatednewfileswithdifferentnamesin that both x andy haddisappeared. If the three applica-
thesamedirectory,ormighthavewrittendifferentbytes tionscomparenotestheywillrealizethatthesystemdid
inthe samefile.InsuchcasesIvy ensuresthatbothup- notbehavelikeaserialserver.
datestakeeffect.

ExclusiveLink(dir-inum,file,file-inum) system. The particular case of a user intentionally dis-
appendaPrepare(dir-inum,file)logrecord connecting a laptop from the network could be handled
iffileexists
byinstructingthelaptop’sDHashservertokeepreplicas
appendaCancel(dir-inum,file)record
ofallthelog-headsandtheuser’scurrentsnapshot;there
returnEXISTS
isnotcurrentlyawaytoaskDHashtodothis.
ifanotherun-canceledPrepare(dir-inum,file)exists
Afterapartitionheals,thefactthateachlog-headwas
appendaCancel(dir-inum,file)record
backoff() updated from just one host preventsconflicts within in-
returnExclusiveLink(dir-inum,file,file-inum) dividuallogs;itissufficientforthehealedsystemtouse
appendLink(dir-inum,file,file-inum)logrecord thenewestversionofeachlog-head.
returnOK Participants in different partitions may have updated
the file system in ways that conflict; this will result in
concurrentversionvectors.Ivyorderssuchversionvec-
Figure3:Ivy’sexclusivedirectoryentrycreationalgorithm.
tors followingtheschemein Section2.5, so thepartici-
pantswillagreeonthefilesystemcontentsafterthepar-
3.3 ExclusiveCreate titionheals.
The file system’s meta-data will be internally correct
Ordinaryfile system semantics requirethat most opera- afterthepartitionheals.Whatthismeansisthatifapiece
tionsthatcreatedirectoryentriesbeexclusive.Forexam- ofdatawasaccessiblebeforethepartition,andneitherit
ple,tryingtocreateadirectorythatalreadyexistsshould nor anydirectory leading to it was deleted in anyparti-
fail, and creating a file that already exists should return tion,thenthedatawillalsobeaccessibleaftertheparti-
areferencetotheexistingfile.Ivyimplementsexclusive tion.
creation of directory entries because some applications However,ifconcurrentapplicationsrelyonfilesystem
use those semantics to implement locks. However, Ivy techniques suchas atomicdirectorycreation for mutual
only guarantees exclusion when the network provides exclusion,thenapplicationsindifferentpartitionsmight
fullconnectivity. updatefilesinwaysthatcausetheapplicationdatatobe
WheneverIvyisabouttoappendaLinklogrecord,it inconsistent.Forexample,e-mailsmightbeappendedto
firstensuresexclusionwithavariantoftwo-phasecom- the same mailbox file in two partitions; after the parti-
mit shown in Figure 3. Ivy first appends a Prepare tions heal, this will appear as two concurrent writes to
record announcing the intention to create the directory the same offset in the mailbox file. Ivy knows that the
entry. This intention can be canceled by a Cancel writes conflict, and automatically orders the log entries
record,aneventualLinkrecord,oratimeout.Then,Ivy sothatallparticipantsseethesamefilecontentsafterthe
checkstoseewhetheranyotherparticipanthasappended partition heals. However, this masks the fact that some
a Prepare that mentions the same directory i-number fileupdatesarenotvisible,andthattheuserorapplica-
and file name. If not, Ivy appends the Link record. If tion may have to take special steps to restore them. Ivy
Ivy sees a different participant’s Prepare, it appends doesnotcurrentlyhaveanautomaticmechanismforsig-
a Cancel record, waits a random amount of time, and naling such conflicts to the user; instead the user must
retries.IfIvyseesadifferentparticipant’sLinkrecord, runthelctooldescribedinthenextsectiontodiscover
itappendsaCancelrecordandindicatesafailure. conflicts. A better approach might be to borrowCoda’s
technique of making the file inaccessible until the user
fixestheconflict.
3.4 Partitioned Updates
Ivy cannot provide the semantics outlined above if the
3.5 Conflict Resolution
network has partitioned. In the case of partition, Ivy’s
design maximizes availability at the expense of consis- Ivy provides a tool, lc, that detects conflicting appli-
tency, by letting updates proceed in all partitions. This cation updatesto files; these mayarise from concurrent
approachissimilartothatofFicus[26]. writestothesamefilebyapplicationsthatareindifferent
Ivy is not directly aware of partitions, nor does it di- partitions or which do not perform appropriate locking.
rectlyensurethateverypartitionhasacompletecopyof lcscansanIvyfilesystem’slogforrecordswithconcur-
allthelogs.Instead, IvydependsonDHash toreplicate rentversionvectorsthataffectthesamefileordirectory
dataenoughtimes,andinenoughdistinctlocations,that entry. lc determines the point in the logs at which the
each partition is likely to have a complete set of data. partitionmusthaveoccurred,anddetermineswhichpar-
Whether this succeeds in practice depends on the sizes ticipantswereinwhichpartition.lcthenusesIvyviews
of the partitions, the degree of DHash replication, and to construct multiple historic views of the file system:
the total number of DHash blocks involved in the file oneasofthetimeofpartition,andoneforeachpartition

|     |           |     |       |     |     |     | 5 Implementation |     |     |     |     |     |     |
| --- | --------- | --- | ----- | --- | --- | --- | ---------------- | --- | --- | --- | --- | --- | --- |
|     | Ivy agent |     | DHash |     |     |     |                  |     |     |     |     |     |     |
private key Server DHash Server Ivy is written in C++ and runs on FreeBSD. It uses the
SFStool-kit[22]forevent-drivenprogrammingandNFS
| Application |     |     |     |     |     |     | loop-backserversupport. |     |     |     |     |     |     |
| ----------- | --- | --- | --- | --- | --- | --- | ----------------------- | --- | --- | --- | --- | --- | --- |
Ivy Server DHash Server Ivyisimplementedasseveralcooperatingparts,illus-
[System Calls] trated in Figure 4. Each participating host runs an Ivy
serverwhichexposesIvyfilesystemsaslocally-mounted
NFS 3 DHash Server NFS v3 file systems. A file system name encodes the
| Client |     | [NFS 3] |        |     |     |     |                                          |        |          |          |      |        |         |
| ------ | --- | ------- | ------ | --- | --- | --- | ---------------------------------------- | ------ | -------- | -------- | ---- | ------ | ------- |
|        |     |         | Kernel |     |     |     | DHash                                    | key of | the file | system’s | view | block, | for ex- |
|        |     |         |        |     |     |     | ample, /ivy/9RYBbWyeDVEQnxeL95LG5jJjwa4. |        |          |          |      |        |         |
TheIvyserverdoesnotholdprivatekeys;instead,each
Figure4:Ivysoftwarestructure.
|     |     |     |     |     |     |     | participantrunsan                         |      | agenttohold       |     | itsprivatekey,andthe |       |            |
| --- | --- | --- | --- | --- | --- | --- | ----------------------------------------- | ---- | ----------------- | --- | -------------------- | ----- | ---------- |
|     |     |     |     |     |     |     | Ivy server                                | asks | the participant’s |     | local                | agent | program to |
|     |     |     |     |     |     |     | signlogheads.TheIvyserveractsasaclientofa |      |                   |     |                      |       | local      |
justbeforethepartitionhealed.Forexample, DHashserver,whichconsultsotherDHashserversscat-
|     |     |     |     |     |     |     | tered around | the | network. | The | Ivy server | also | keeps a |
| --- | --- | --- | --- | --- | --- | --- | ------------ | --- | -------- | --- | ---------- | ---- | ------- |
% ./lc -v /ivy/BXz4+udjsQm4tX63UR9w71SNP0c LRUcacheofcontent-hashblocks(e.g.logrecordsand
before: +WzW8s7fTEt6pehaB7isSfhkc68
snapshotblocks)andlog-headsthatitrecentlymodified.
| partition1: | l3qLDU5icVMRrbLvhxuJ1WkNvWs |     |     |     |     |     |     |     |     |     |     |     |     |
| ----------- | --------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| partition2: | JyCKgcsAjZ4uttbbtIX9or+qEXE |     |     |     |     |     |     |     |     |     |     |     |     |
% cat /ivy/+WzW8s7fTEt6pehaB7isSfhkc68/file1
|          |         |          |     |     |     |     | 6 Evaluation |     |     |     |     |     |     |
| -------- | ------- | -------- | --- | --- | --- | --- | ------------ | --- | --- | --- | --- | --- | --- |
| original | content | of file1 |     |     |     |     |              |     |     |     |     |     |     |
% cat /ivy/l3qLDU5icVMRrbLvhxuJ1WkNvWs/file1
original content of file1, changed This section evaluates Ivy’s performance 1) in a purely
| append on | first | partition |     |     |     |     |     |     |     |     |     |     |     |
| --------- | ----- | --------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
% cat /ivy/JyCKgcsAjZ4uttbbtIX9or+qEXE/file1 local configuration, 2) over a WAN, 3) as a function of
original content of file1 thenumberofparticipants,4)asafunctionofthenum-
| append on | second | partition |     |     |     |     |              |          |           |               |            |        |           |
| --------- | ------ | --------- | --- | --- | --- | --- | ------------ | -------- | --------- | ------------- | ---------- | ------ | --------- |
|           |        |           |     |     |     |     | ber of DHash |          | nodes, 5) | as a function |            | of the | number of |
|           |        |           |     |     |     |     | concurrent   | writers, | and       | 6) as a       | functionof | the    | snapshot  |
Insimplecases,ausercouldsimplyexaminethever-
interval.Themaingoaloftheevaluationistounderstand
| sions of | the file | and merge | them | by hand | in a text | edi- |     |     |     |     |     |     |     |
| -------- | -------- | --------- | ---- | ------- | --------- | ---- | --- | --- | --- | --- | --- | --- | --- |
thecostsofIvy’sdesignintermsofnetworklatencyand
tor.Application-specificresolverssuchasthoseusedby
cryptographicoperations.
Coda[14,16]couldbeusedformorecomplexcases.
|     |     |     |     |     |     |     | Ivy is | configured | to  | construct | a   | snapshot | every 20 |
| --- | --- | --- | --- | --- | --- | --- | ------ | ---------- | --- | --------- | --- | -------- | -------- |
newlogrecords,orwhen60secondshaveelapsedsince
4 Security andIntegrity the construction of the last snapshot. Unless otherwise
stated,Ivy’sblockcachesizeis512blocks.DHashnodes
Since Ivy is intended to support distributed users with are PlanetLab [1] nodes, running Linux 2.4.18 on 1.2
arms-lengthtrustrelationships,itmustbeabletorecover GHz Pentium III CPUs, and RON [2] nodes, running
frommaliciousparticipants.Thesituationweenvisionis
FreeBSD4.5on733MHzPentiumIIICPUs.DHashwas
that a participant’s bad behavior is discovered after the configuredwithreplicationturnedoff,sincethereplica-
fact.Maliciousbehaviorisassumedtoconsistofthepar-
|     |     |     |     |     |     |     | tion implementation |     | is  | not complete; |     | replication | would |
| --- | --- | --- | --- | --- | --- | --- | ------------------- | --- | --- | ------------- | --- | ----------- | ----- |
ticipantusing ordinaryfile system operationsto modify probablydecreaseperformancesignificantly.Unlessoth-
ordeletedata.Oneformofmalicemightbethatanout- erwise stated, this section reports results averaged over
siderbreaksintoalegitimateuser’scomputerandmodi-
fiveruns.
fiesfilesstoredinIvy. TheworkloadusedtoevaluateIvyistheModifiedAn-
| To cope | with | a good | user turning | bad, | the other | par- |     |     |     |     |     |     |     |
| ------- | ---- | ------ | ------------ | ---- | --------- | ---- | --- | --- | --- | --- | --- | --- | --- |
drewBenchmark(MAB),whichconsistsoffivephases:
ticipants can either form a new view that excludes the (1)createadirectoryhierarchy,(2)copyfilesintothese
bad participant’slog, or form a view that only includes directories, (3) walk thedirectoryhierarchy whileread-
the log records before a certain point in time. In either ingattributesofeachfile,(4)readthefiles,and(5)com-
casethe resultingfilesystem maybe missingimportant pilethefilesintoaprogram.Unlessotherwisestated,the
meta-data.Uponuserrequest,Ivy’sivychecktoolwill MABandtheIvyserverrunona1.2GHzAMDAthlon
detect and fix certain meta-data inconsistencies. ivy- computerrunningFreeBSD4.5atMIT.
| check      | inspects | an existing | file          | system,     | finds          | missing |                   |     |     |     |     |     |     |
| ---------- | -------- | ----------- | ------------- | ----------- | -------------- | ------- | ----------------- | --- | --- | --- | --- | --- | --- |
| Link and   | Inode    | meta-data,  |               | and creates | plausible      | re-     |                   |     |     |     |     |     |     |
|            |          |             |               |             |                |         | 6.1 SingleUserMAB |     |     |     |     |     |     |
| placements | in a     | new fix     | log. ivycheck |             | can optionally |         |                   |     |     |     |     |     |     |
lookintheexcludedloginordertofindhintsaboutwhat Table 3 shows Ivy’s performance on the phases of the
themissingmeta-datashouldlooklike. MAB for a file system with just one log. All the soft-

|     |              |       | Ivy(s) | NFS(s) |     |     |                                             |           |      |        |            |              |     |
| --- | ------------ | ----- | ------ | ------ | --- | --- | ------------------------------------------- | --------- | ---- | ------ | ---------- | ------------ | --- |
|     |              | Phase |        |        |     |     | 6.2 Performanceona                          |           |      |        | WAN        |              |     |
|     |              | Mkdir | 0.6    |        | 0.5 |     |                                             |           |      |        |            |              |     |
|     |              |       |        |        |     |     | Table 4                                     | showsthe  | time | for a  | single MAB | instancewith |     |
|     | Create/Write |       | 6.6    |        | 0.8 |     |                                             |           |      |        |            |              |     |
|     |              |       |        |        |     |     | four DHash                                  | serverson |      | a WAN. | One DHash  | serverruns   |     |
|     |              | Stat  | 0.6    |        | 0.2 |     |                                             |           |      |        |            |              |     |
|     |              | Read  | 1.0    |        | 0.8 |     | onthesamecomputerthatisrunningtheMAB.Theav- |           |      |        |            |              |     |
eragenetworkround-triptimestotheotherthreeDHash
|       |              | Compile                  | 10.0 |     | 5.3 |         |                 |           |           |           |               |           |           |
| ----- | ------------ | ------------------------ | ---- | --- | --- | ------- | --------------- | --------- | --------- | --------- | ------------- | --------- | --------- |
|       |              |                          |      |     |     |         | servers         | are 9,    | 16, and   | 82 ms.    | The file      | system    | contains  |
|       |              | Total                    | 18.8 |     | 7.6 |         |                 |           |           |           |               |           |           |
|       |              |                          |      |     |     |         | four logs.      | The       | benchmark | only      | writes        | one of    | the logs, |
|       |              |                          |      |     |     |         | though          | the other | three     | log-heads | are consulted |           | to make   |
|       |              |                          |      |     |     |         | sure operations |           | see       | the most  | up-to-date    | data. The | four      |
| Table | 3: Real-time | insecondstoruntheMABwith |      |     |     | asingle |                 |           |           |           |               |           |           |
Ivylogandallsoftwarerunningonasinglemachine.TheNFS log-heads are stored on three DHash servers. The log-
columnshowsMABrun-timeforNFSoveraLAN. head that is being written to is stored on the DHash
serverwitharound-triptimeof9msfromthelocalma-
chine.Onelog-headisstoredontheserverwitharound-
|     |     |       | Ivy(s) | NFS(s) |     |     |           |       |         |     |                |     |       |
| --- | --- | ----- | ------ | ------ | --- | --- | --------- | ----- | ------- | --- | -------------- | --- | ----- |
|     |     | Phase |        |        |     |     | trip time | of 82 | ms from | the | local machine. | The | DHash |
|     |     | Mkdir | 11.2   |        | 4.8 |     |           |       |         |     |                |     |       |
servers’nodeIDsarechosensothateachisresponsible
|     | Create/Write |      | 89.2 | 42.0 |     |     | forroughlythesamenumberofblocks. |     |         |          |               |          |           |
| --- | ------------ | ---- | ---- | ---- | --- | --- | -------------------------------- | --- | ------- | -------- | ------------- | -------- | --------- |
|     |              | Stat | 65.6 | 47.8 |     |     |                                  |     |         |          |               |          |           |
|     |              |      |      |      |     |     | A typical                        | NFS | request | requires | Ivy           | to fetch | the three |
|     |              | Read | 65.8 | 55.6 |     |     |                                  |     |         |          |               |          |           |
|     |              |      |      |      |     |     | other log-heads                  |     | from    | DHash;   | this involves |          | just one  |
Compile 144.2 130.2 DHash network RPC per log-head. Ivy issues the three
|     |     | Total | 376.0 | 280.4 |     |     |     |     |     |     |     |     |     |
| --- | --- | ----- | ----- | ----- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
RPCsinparallel,sothetimeforeachlog-headcheckis
|     |     |     |     |     |     |     | governed | by the | largest | round-trip | time | of 82 | ms. The |
| --- | --- | --- | --- | --- | --- | --- | -------- | ------ | ------- | ---------- | ---- | ----- | ------- |
MABcausesIvytoretrievelog-heads3,346times,fora
Table4: MABrun-timewithfourDHashserversonaWAN.
totalof274seconds.ThislatencydominatesIvy’sWAN
Thefilesystemcontainsfourlogs.
performance.
Theremaining102secondsofMABrun-timeareused
infourways.RunningtheMABonaLANtakes22sec-
onds,mostlyintheformofCPUtime.Ivywritesitslog-
| ware (the | MAB, | Ivy, and | a single | DHash | server) | ran |     |     |     |     |     |     |     |
| --------- | ---- | -------- | -------- | ----- | ------- | --- | --- | --- | --- | --- | --- | --- | --- |
on the same computer. To put the Ivy performance in headtoDHash508times;eachwritetakes9msofnet-
|     |     |     |     |     |     |     | work latency,for |     | a total | of  | 5 seconds.Ivy | inserts | 1,003 |
| --- | --- | --- | --- | --- | --- | --- | ---------------- | --- | ------- | --- | ------------- | ------- | ----- |
perspective,Table3alsoshowsMABperformanceover
NFS; the client andNFS server are connectedby a 100 logrecords,someofthemconcurrently.Theaveragein-
Mbit LAN. Note that this comparison is unfair to NFS, sertion takes 54 ms (27 ms for the Chord [37] lookup,
thenanother27msfortheDHashnodetoacknowledge
| since NFS | involved | network | communication |     | while | the |     |     |     |     |     |     |     |
| --------- | -------- | ------- | ------------- | --- | ----- | --- | --- | --- | --- | --- | --- | --- | --- |
Ivybenchmarkdidnot. receipt). This accounts for roughly 54 seconds. Finally,
thelocalcomputersendsandreceives7.0MBytesofdata
ThefollowinganalysisexplainsIvy’s18.8secondsof
duringtheMABrun.Thisaccountsfortheremainingrun
run-time.TheMABproduces386NFSRPCsthatmod-
time.DuringtheexperimentIvyalsoinserts358DHash
ifytheIvylog.118oftheseareeitherMKDIRorCRE- blockswhileupdatingitssnapshot;becauseIvy doesn’t
ATE,whichrequiretwolog-headwritestoachieveatom-
waitfortheseinserts,theycontributelittletothetotalrun
| icity. 119 | of  | the 386 RPCs | are | COMMITs | or CLOSEs |     |     |     |     |     |     |     |     |
| ---------- | --- | ------------ | --- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- | --- |
time.
thatrequireIvytoflushwrittendatatothelog.Another Table4alsoshowsMABperformanceoverwide-area
| 133 RPCs | are | synchronous | WRITEs | generated |     | by the |          |            |     |              |     |            |     |
| -------- | --- | ----------- | ------ | --------- | --- | ------ | -------- | ---------- | --- | ------------ | --- | ---------- | --- |
|          |     |             |        |           |     |        | NFS. The | round-trip |     | time between | the | NFS client | and |
linker. Overall, the 386 RPCs caused Ivy to update the serveris79ms,whichisroughlythetimeittakesIvyto
| log-head | 508 | times. Computing |     | a public-key | signature |     |           |                |     |     |                 |     |         |
| -------- | --- | ---------------- | --- | ------------ | --------- | --- | --------- | -------------- | --- | --- | --------------- | --- | ------- |
|          |     |                  |     |              |           |     | fetch all | the log-heads. |     | We  | use NFS overUDP |     | because |
usesabout14.2milliseconds(ms)ofCPUtime,forato-
itisfasterforthisbenchmarkthanFreeBSD’sNFSover
talof7.2secondsofCPUtime. TCPimplementation.IvyisslowerthanNFSbecauseIvy
|     |     |     |     |     |     |     | operations | often | require | more | network | round-trips; | for |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ----- | ------- | ---- | ------- | ------------ | --- |
TheremainingtimeisspentintheIvyserver(4.9sec-
|        |           |        |                |     |        |          | example, | some | NFS | requests | require | Ivy to both | fetch |
| ------ | --------- | ------ | -------------- | --- | ------ | -------- | -------- | ---- | --- | -------- | ------- | ----------- | ----- |
| onds), | the DHash | server | (2.9 seconds), |     | and in | the pro- |          |      |     |          |         |             |       |
cesses that MAB invokes (2.6 seconds). Profiling indi- andupdatelog-heads,requiringtworound-trips.
| cates that | the | most expensive | operations |     | in the | Ivy and |     |     |     |     |     |     |     |
| ---------- | --- | -------------- | ---------- | --- | ------ | ------- | --- | --- | --- | --- | --- | --- | --- |
DHashserversareSHA-1hashesandmemorycopies. 6.3 ManyLogs,OneWriter
The MAB creates a total of 1.6 MBytes of file data. Figure 5 shows how Ivy’s performance changes as the
Ivy,inresponse,insertsatotalof8.8MBytesoflogand numberoflogsincreases.Otherthanthenumberoflogs,
snapshotdataintoDHash. thisexperimentisidenticaltotheoneintheprevioussec-

800
| 400 |     |     |     | )sdnoces( emit-nur s’BAM enO |     |     |     |     |     |     |
| --- | --- | --- | --- | ---------------------------- | --- | --- | --- | --- | --- | --- |
700
)sdnoces( emit-nur BAM 350
| 300 |     |     |     | 600 |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 250 |     |     |     | 500 |     |     |     |     |     |     |
400
200
300
150
200
100
| 50  |       |          |     | 100 |     |     |     |     |     |     |
| --- | ----- | -------- | --- | --- | --- | --- | --- | --- | --- | --- |
|     | 0     |          |     |     | 0   |     |     |     |     |     |
|     | 4 6 8 | 10 12 14 | 16  |     | 1   |     | 2   | 3   |     | 4   |
Number of logs (with one active log) Number of concurrent MABs
Figure5:MABrun-timeasafunctionofthenumberoflogs. Figure7:Averagerun-timeofMABwhenseveralMABsare
Onlyoneparticipantisactive. runningconcurrentlyondifferenthostsontheInternet.Theer-
rorbarsindicatestandarddeviationoveralltheMABruns.
500
|     |     |     |     | public | keys are | also | used to | ensure | the log-heads | are |
| --- | --- | --- | --- | ------ | -------- | ---- | ------- | ------ | ------------- | --- |
)sdnoces( emit-nur BAM
|     |     |     |     | placed | on random | DHash | servers. | One | DHash | server, |
| --- | --- | --- | --- | ------ | --------- | ----- | -------- | --- | ----- | ------- |
400
|     |     |     |     | the Ivy | server, | and the | MAB always |     | execute | on a host |
| --- | --- | --- | --- | ------- | ------- | ------- | ---------- | --- | ------- | --------- |
atMIT.Theround-triptimesfromthehostatMITtothe
300
PlanetLabhostsaverage32ms,withaminimumof1ms,
| 200 |     |     |     | amaximumof78ms,andastandarddeviationof27ms. |     |     |     |     |     |     |
| --- | --- | --- | --- | ------------------------------------------- | --- | --- | --- | --- | --- | --- |
Therearefourlogsintotal;onlyoneofthemchanges.
100
|     |     |     |     | The      | run-time     | in Figure | 6 growsbecause |          | more   | Chord    |
| --- | --- | --- | --- | -------- | ------------ | --------- | -------------- | -------- | ------ | -------- |
|     |     |     |     | messages | are required |           | to find        | each log | record | block in |
0
|     | 8 16 | 24  | 32  | DHash.Anaverageof2.3,2.9,3.3,and3.8RPCsarere- |     |     |     |     |     |     |
| --- | ---- | --- | --- | --------------------------------------------- | --- | --- | --- | --- | --- | --- |
quiredfor8,16,24,and32DHashservers,respectively.
Number of DHash servers
|           |                      |               |          | These numbersinclude |     |     | the final | DHash | RPC | aswell as |
| --------- | -------------------- | ------------- | -------- | -------------------- | --- | --- | --------- | ----- | --- | --------- |
| Figure 6: | Average MAB run-time | as the number | of DHash | ChordlookupRPCs.     |     |     |           |       |     |           |
servers increases. The error bars indicate standard deviation The high standard deviation in Figure 6 is due to the
| over different | choices of PlanetLab | hosts and | different map- |           |              |     |              |     |        |            |
| -------------- | -------------------- | --------- | -------------- | --------- | ------------ | --- | ------------ | --- | ------ | ---------- |
|                |                      |           |                | fact that | the run-time |     | is dominated |     | by the | round-trip |
pingsofblockstoDHashservers. timesto thefourparticularDHashserversthatstorethe
|     |     |     |     | log-heads. | This | means | that adding | more | DHash | servers |
| --- | --- | --- | --- | ---------- | ---- | ----- | ----------- | ---- | ----- | ------- |
doesn’treducethevariation.
| tion. The | number of logs | ranges from 4 to | 16, but only |     |     |     |     |     |     |     |
| --------- | -------------- | ---------------- | ------------ | --- | --- | --- | --- | --- | --- | --- |
oneparticipantexecutestheMAB—theotherlogsnever
6.5 ManyWriters
change.Figure5reportsresultsaveragedoverthreeruns.
Thenumberoflogshasrelativelylittleimpactonrun- Figure7 showstheeffectofmultipleactivewriters.We
performthreeexperimentsforeachnumberN
| timebecauseIvyfetchesthelog-headsinparallel.There |     |     |     |     |     |     |     |     |     | ofpartic- |
| ------------------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --------- |
is a slight increase caused by the fact that the version ipants;eachexperimentinvolvesoneMABrunningcon-
vectorineachlogrecordhasone44-byteentryperpar- currentlyoneachofNdifferentIvyhostsontheInternet,
ticipant. a file system with four logs, new log-head public keys,
|     |     |     |     | and 32 | DHash | servers. | Each MAB | run | uses | its own di- |
| --- | --- | --- | --- | ------ | ----- | -------- | -------- | --- | ---- | ----------- |
6.4 ManyDHashServers rectoryintheIvyfilesystem.Eachdatapointshowsthe
|     |     |     |     | average | and standard |     | deviation | of MAB | run-time | over |
| --- | --- | --- | --- | ------- | ------------ | --- | --------- | ------ | -------- | ---- |
the3N
Figure 6 shows the averages and standard deviations of MABexecutions.
Ivy’sMABperformanceasthenumberofDHashservers Therun-timeincreaseswiththenumberofactivepar-
increases from 8 to 32. For each number of servers we ticipantsbecauseeachhastofetchtheothers’newlyap-
performtenexperimentalruns.Foreachrun,allbutone pendedlogrecordsfromDHash.Therun-timeincreases
of the DHash servers are placed on randomly chosen relatively slowly because Ivy fetches records from the
PlanetLabhosts(fromapoolof32hosts);newlog-head different logs in parallel. The deviation in run-times is

|     |     |     |     |     |     |     | sure that         | no data | is cached.                     | The | experiment |     | consists of |
| --- | --- | --- | --- | --- | --- | --- | ----------------- | ------- | ------------------------------ | --- | ---------- | --- | ----------- |
|     |     |     |     |     |     |     | twophases.First,X |         | commitschangesto38files,atotal |     |            |     |             |
1000
Y
)sdnoces( emit-nur BAM of 4333 lines. Second, updates its local copy to re-
|     | 800 |     |     |     |     |     | flect X’s   | changes. | Table       | 5 shows | the | run-times | for the     |
| --- | --- | --- | --- | --- | --- | --- | ----------- | -------- | ----------- | ------- | --- | --------- | ----------- |
|     |     |     |     |     |     |     | two phases. | For      | comparison, | Table   | 5   | shows     | the time to |
600
|     |     |     |     |     |     |     | perform | the same | CVS | operations | over | NFS | and ssh; |
| --- | --- | --- | --- | --- | --- | --- | ------- | -------- | --- | ---------- | ---- | --- | -------- |
inbothcasestheclienttoserverround-triplatencyis77
400
ms.
Ivy’sperformancewithCVSisdisappointing.During
200
acommitorupdate,CVSlooksateveryfileintherepos-
|     | 0   |     |     |     |     |     | itory;foreachfileaccess,Ivycheckswhethersomeother |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ------------------------------------------------- | --- | --- | --- | --- | --- | --- |
0 20 40 60 80 100 participanthasrecentlychangedthefile.CVShaslocked
Snapshot interval (number of log records)
|     |     |     |     |     |     |     | the repository,so |     | no such | changes | are | possible; | but Ivy |
| --- | --- | --- | --- | --- | --- | --- | ----------------- | --- | ------- | ------- | --- | --------- | ------- |
doesn’tknowthat.DuringaCVScommit,Ivywaitsfor
Figure 8:
MAB run-time a function of the interval between theDHashinsertofnewlogrecordsandanupdatedlog-
snapshots.Fortheseexperiments,thesizeofIvy’sblockcache
headforeachfilemodified;again,sinceCVShaslocked
is80blocks.
therepository,Ivycouldhavewrittenallthelogrecords
|     |     |     |     |     |     |     | in parallel | and | just a | single | updated | log-head | for the |
| --- | --- | --- | --- | --- | --- | --- | ----------- | --- | ------ | ------ | ------- | -------- | ------- |
Phase Ivy(s) NFS(s) ssh(s) whole CVS commit. A transactional interface between
|     |        |     |       |       |     |     | application    | and | file system | would | help | performance | in  |
| --- | ------ | --- | ----- | ----- | --- | --- | -------------- | --- | ----------- | ----- | ---- | ----------- | --- |
|     | Commit |     | 420.8 | 224.6 | 3.4 |     |                |     |             |       |      |             |     |
|     | Update |     | 284.2 | 135.2 | 2.3 |     | thissituation. |     |             |       |      |             |     |
|     |        |     |       |       |     |     | 7 Related      |     | Work        |       |      |             |     |
Table5:Run-timesfortheCVSexperimentphases.DHashis
runningon32nodesonawide-areanetwork. Ivy was motivatedby recent work on peer-to-peerstor-
|     |         |             |        |           |         |        | age, particularly |                | FreeNet | [8],                | PAST [32], | and      | CFS [9].     |
| --- | ------- | ----------- | ------ | --------- | ------- | ------ | ----------------- | -------------- | ------- | ------------------- | ---------- | -------- | ------------ |
|     |         |             |        |           |         |        | The data          | authentication |         | mechanisms          |            | in these | systems      |
| due | to each | participant | having | different | network | round- |                   |                |         |                     |            |          |              |
|     |         |             |        |           |         |        | limit them        | to read-only   |         | or single-publisher |            |          | data, in the |
triplatenciestotheDHashservers.
|     |     |     |     |     |     |     | sense that | only   | the original | publisher |               | of each | piece of  |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ------ | ------------ | --------- | ------------- | ------- | --------- |
|     |     |     |     |     |     |     | data can   | modify | it. CFS      | builds    | a file-system |         | on top of |
6.6 SnapshotInterval peer-to-peerstorage,usingideasfromSFSRO[11];how-
ever,eachfilesystemisread-only.Ivy’sprimarycontri-
| Figure | 8 shows | the        | effect | on MAB          | run-time | of the in-  |                 |     |          |         |         |         |          |
| ------ | ------- | ---------- | ------ | --------------- | -------- | ----------- | --------------- | --- | -------- | ------- | ------- | ------- | -------- |
|        |         |            |        |                 |          |             | bution relative |     | to these | systems | is that | it uses | peer-to- |
| terval | between | snapshots. |        | The experiments |          | involve one |                 |     |          |         |         |         |          |
peerstoragetobuildaread/writefilesystemthatmultiple
MABinstance,fourlogs,and32DHashservers.Thex-
userscanshare.
| axis                 | represents | the     | number                     | of new      | log records | inserted    |                    |     |     |            |     |     |     |
| -------------------- | ---------- | ------- | -------------------------- | ----------- | ----------- | ----------- | ------------------ | --- | --- | ---------- | --- | --- | --- |
| beforeIvybuildseacha |            |         | newsnapshot.Fortheseexper- |             |             |             |                    |     |     |            |     |     |     |
|                      |            |         |                            |             |             |             | 7.1 Log-structured |     |     | FileSystem |     |     |     |
| iments,              | the        | size of | Ivy’s                      | block cache | is 80       | blocks. The |                    |     |     |            |     |     |     |
reasontherun-timeincreaseswhentheintervalisgreater
|     |     |     |     |     |     |     | Sprite LFS | [31] | represents | a file | system | as  | a log of op- |
| --- | --- | --- | --- | --- | --- | --- | ---------- | ---- | ---------- | ------ | ------ | --- | ------------ |
than 80 is that not all the records needed to build each erations,alongwithasnapshotofi-numbertoi-nodelo-
snapshotcanfitinthecache.
|     |     |     |     |     |     |     | cation mappings. |     | LFS      | uses a     | single     | log managed | by a    |
| --- | --- | --- | --- | --- | --- | --- | ---------------- | --- | -------- | ---------- | ---------- | ----------- | ------- |
|     |     |     |     |     |     |     | single serverin  |     | order to | to speedup | smallwrite |             | perfor- |
mance.Ivyusesmultiplelogstoletmultipleparticipants
| 6.7 | Wide-area |     | CVSonIvy |     |     |     |     |     |     |     |     |     |     |
| --- | --------- | --- | -------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
updatethefilesystemwithoutacentralfileserverorlock
To evaluateIvy’sperformanceas a source-code or doc- server;Ivydoesnotgainanyperformancebyuseoflogs.
| ument | repository, | we  | show | the run-time | of  | some oper- |     |     |     |     |     |     |     |
| ----- | ----------- | --- | ---- | ------------ | --- | ---------- | --- | --- | --- | --- | --- | --- | --- |
ations on a CVS [4] repository stored in Ivy. The Ivy 7.2 DistributedStorage Systems
| filesystem |     | hasfourlogs | storedon32wide-areaDHash |     |     |     |     |     |     |     |     |     |     |
| ---------- | --- | ----------- | ------------------------ | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
servers. The round-trip times from the Ivy host to the Zebra [12] maintains a per-client log of file contents,
DHashserversstoringthe log-headsare17,36,70,and striped across multiple network nodes. Zebra serializes
77 ms. The CVS repository contains 251 files and 3.3 meta-data operations through a single meta-data server.
MBytes. Before the experiment starts, two Ivy partici- Ivyborrowstheideaofper-clientlogs,butextendsthem
|     | X   | Y,  |     |     |     |     |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
pants, and check out a copy of the repository to to meta-data as well as file contents. This allows Ivy to
their localdisks, and both createan Ivy snapshotof the avoid Zebra’s single meta-data server, and thus poten-
filesystem.Eachparticipantthenrebootsitshosttoen- tiallyachievehigheravailability.

xFS [3], the Serverless Network File System, dis- Ficus [26] is a distributed file system in which any
tributes both data and meta-data across participating replicacanbeupdated.Ficusautomaticallymergesnon-
hosts.Foreverypieceofmeta-data(e.g.ani-node)there conflictingupdatesfromdifferentreplicas,andusesver-
is a host that is responsible for serializing updates to sion vectors to detect conflicting updates and to signal
that meta-data to maintain consistency. Ivy avoids any themtotheuser.Ivyalsofacestheproblemofconflicting
meta-data centralization, and is therefore more suitable updates performed in different network partitions, and
forwide-areauseinwhichparticipantscannotbetrusted uses similar techniques to handle them. However, Ivy’s
to run reliable servers. However, Ivy has lower perfor- main focus is connected operation; in this mode it pro-
mancethanxFSandadhereslessstrictlytoserialseman- vides close-to-open consistency, which Ficus does not,
| tics. |     |     |     | and(incooperationwithDHash)doesabetterjobofau- |     |     |     |     |
| ----- | --- | --- | --- | ---------------------------------------------- | --- | --- | --- | --- |
Frangipani [40] is a distributed file system with two tomaticallydistributingstorageoverawide-areasystem.
layers:adistributedstorageservicethatactsasavirtual Bayou [39] represents changes to a database as a log
diskandasetofsymmetricfileservers.Frangipanimain- ofupdates.Eachupdateincludesanapplication-specific
tains fairly conventional on-disk file system structures, merge procedure to resolve conflicts. Each node main-
withsmall,per-servermeta-datalogstoimproveperfor- tains a local log of all the updates it knows about, both
mance and recoverability. Frangipani servers use locks its own and those by other nodes. Nodes operate pri-
toserializeupdatestometa-data.Thisapproachrequires marilyinadisconnectedmode,andmergelogspairwise
reliableandtrustworthyservers. when they talk to each other. The log and the merge
Harp [18] uses a primary copy scheme to maintain procedures allow a Bayou node to re-build its database
identical replicas of the entire file system. Clients send after adding updates made in the past by other nodes.
allNFSrequeststothecurrentprimaryserver,whichse- As updates reach a special primary node, the primary
rializes them. A Harp system consists of a small clus- node decides the final and permanent order of log en-
ter of well managed servers, probably physically co- tries.IvydiffersfromBayouinanumberofways.Ivy’s
located.Ivydoeswithoutanycentralclusterofdedicated per-client logs allow nodes to trust each other less than
theyhavetoinBayou.Ivyusesadistributedalgorithmto
servers—attheexpenseofstrictserialconsistency.
|     |     |     |     | orderthelogs,whichavoids |     | Bayou’spotentiallyunreli- |     |     |
| --- | --- | --- | --- | ------------------------ | --- | ------------------------- | --- | --- |
ableprimarynode.Ivyimplementsasinglecoherentdata
| 7.3 Reclaiming | Storage |     |     |     |     |     |     |     |
| -------------- | ------- | --- | --- | --- | --- | --- | --- | --- |
structure(thefilesystem),ratherthanadatabaseofinde-
The Elephantfile system [34] allows allfile system op- pendent entries; Ivy must ensure that updates leave the
|             |               |          |                      | file system | consistent, while | Bayou | shifts much | of this |
| ----------- | ------------- | -------- | -------------------- | ----------- | ----------------- | ----- | ----------- | ------- |
| erations to | be undone for | a period | defined by the user, |             |                   |       |             |         |
after which the change becomes permanent. While Ivy burden to application-supplied merge procedures. Ivy’s
|                    |         |              |                  | design focuses | on providing | serial | semantics | to con- |
| ------------------ | ------- | ------------ | ---------------- | -------------- | ------------ | ------ | --------- | ------- |
| does not currently | reclaim | log storage, | perhaps it could |                |              |        |           |         |
adoptElephant’sversionretentionpolicies;themainob- nected clients, while Bayou focuses on managing con-
stacleisthatdiscardinglogentrieswouldhurtIvy’sabil- flictscausedbyupdatesfromdisconnectedclients.
| ity to recover | from malicious | participants.  | Experience      |             |                 |     |         |     |
| -------------- | -------------- | -------------- | --------------- | ----------- | --------------- | --- | ------- | --- |
| with Venti     | [28] suggests  | that retaining | old versions of |             |                 |     |         |     |
|                |                |                |                 | 7.5 Storing | DataonUntrusted |     | Servers |     |
filesindefinitelymaynotbetooexpensive.
|     |     |     |     | BFS [7], OceanStore | [15], | and Farsite | [5] all | store data |
| --- | --- | --- | --- | ------------------- | ----- | ----------- | ------- | ---------- |
7.4 Consistencyand ConflictResolution onuntrustedserversusingCastroandLiskov’spractical
|     |     |     |     | Byzantineagreementalgorithm[7]. |     |     | Multipleclients | are |
| --- | --- | --- | --- | ------------------------------- | --- | --- | --------------- | --- |
Coda[14,16]allowsadisconnectedclienttomodifyits allowedtomodifyagivendataitem;theydothisbysend-
own local copy of a file system, which is merged into ingupdateoperationstoasmallgroupofserversholding
the main replica when the client re-connects. A Coda replicasofthedata.Theseserversagreeonwhichopera-
client keeps a replay log that records modifications to tionstoapply,andinwhatorder,usingByzantineagree-
the client’s local copies while the client is in discon- ment.ThereasonByzantineagreementisneededisthat
nectedmode.Whentheclientreconnectswiththeserver, clients cannot directly validate the data they fetch from
Codapropagatesclient’schangestotheserverbyreplay- theservers,sincethedatamaybetheresultofincremen-
ingthelogontheserver.Codadetectschangesthatcon- taloperationsthatnooneclientisawareof.Incontrast,
flictwithchangesmadebyotherusers,andpresentsthe Ivy exposesthe wholeoperationhistory to everyclient.
detailsofthechangestoapplication-specificconflictre- EachIvyclientsignstheheadofaMerklehash-tree[25]
solvers. Ivy’s behavior after a partition heals is similar ofitslog.Thisallowsotherclientstoverifythatthelog
to Coda’s conflict resolution: Ivy automatically merges is correct when they retrieve it from DHash; thus Ivy
non-conflictingupdatesin the logsand letsapplication- clients do not need to trust the DHash servers to main-
specifictoolshandleconflicts. tainthecorrectnessororderofthelogs.Ivyisvulnerable

toDHashreturningstalecopiesofsignedlog-heads;Ivy This research was sponsored by the Defense Advanced
could detect stale data using techniques introduced by Research Projects Agency(DARPA) and the Space and
SUNDR [24]. Ivy’suse of logs makes it slow,although Naval Warfare Systems Center, San Diego, under con-
thisinefficiencyispartiallyoffsetbyitssnapshotmech- tract N66001-00-1-8933,and by grants from NTT Cor-
| anism. |     |     |     |     |     | porationundertheNTT-MITcollaboration. |     |     |     |     |     |     |
| ------ | --- | --- | --- | --- | --- | ------------------------------------- | --- | --- | --- | --- | --- | --- |
TDB[20],S4[38],andPFS[36]useloggingand(for
| TDBandPFS)collision-resistanthashestoallowmodi- |     |     |     |     |     | References |     |     |     |     |     |     |
| ----------------------------------------------- | --- | --- | --- | --- | --- | ---------- | --- | --- | --- | --- | --- | --- |
ficationsbymalicioususersorcorruptedstoragedevices
|                |     |       |     |         |                  | [1] PlanetLab.    | http://www.planet-lab.org/. |                                  |     |     |     |     |
| -------------- | --- | ----- | --- | ------- | ---------------- | ----------------- | --------------------------- | -------------------------------- | --- | --- | --- | --- |
| to be detected | and | (with | S4) | undone; | Ivy uses similar |                   |                             |                                  |     |     |     |     |
|                |     |       |     |         |                  | [2] D.Andersen,H. |                             | Balakrishnan,M.FransKaashoek,and |     |     |     |     |
techniquesinadistributedfilesystemcontext.
RobertMorris.Resilientoverlaynetworks.InProc.ofthe
Spreitzeretal.[35]suggestwaystousecryptograph-
ACMSymposiumonOperatingSystemPrinciples,Octo-
| ically signed | log | entries | to prevent | servers | from tam- |     |     |     |     |     |     |     |
| ------------- | --- | ------- | ---------- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- |
ber2001.
peringwithclientupdatesorproducinginconsistentlog
|            |         |        |         |               |          | [3] T. Anderson, |     | M. Dahlin, | J.         | Neefe,  | D. Patterson, |      |
| ---------- | ------- | ------ | ------- | ------------- | -------- | ---------------- | --- | ---------- | ---------- | ------- | ------------- | ---- |
| orderings; | this is | in the | context | of Bayou-like | systems. |                  |     |            |            |         |               |      |
|            |         |        |         |               |          | D. Roselli,      | and | R. Wang.   | Serverless | network | file          | sys- |
Ivy’slogsaresimplerthanBayou’s,sinceonlyoneclient
|            |       |           |        |        |                 | tems. InProc.oftheACMSymposiumonOperatingSys- |     |     |     |     |     |     |
| ---------- | ----- | --------- | ------ | ------ | --------------- | --------------------------------------------- | --- | --- | --- | --- | --- | --- |
| writes any | given | log. This | allows | Ivy to | protect log in- |                                               |     |     |     |     |     |     |
temPrinciples,pages109–126,December1995.
| tegrity, despite  |     | untrusted | DHash         | servers, | by relatively   |                         |     |                   |                        |          |          |     |
| ----------------- | --- | --------- | ------------- | -------- | --------------- | ----------------------- | --- | ----------------- | ---------------------- | -------- | -------- | --- |
|                   |     |           |               |          |                 | [4] B. Berliner.        | CVS | II: Parallelizing |                        | software | develop- |     |
| simple per-client |     | use of    | cryptographic |          | hashes and pub- |                         |     |                   |                        |          |          |     |
|                   |     |           |               |          |                 | ment. InProc.Winter1990 |     |                   | USENIXTechnicalConfer- |          |          |     |
lickeysignatures.
ence,1990.
|     |     |     |     |     |     | [5] W.Bolosky,J.Douceur,D.Ely,andM.Theimer. |     |     |     |     |     | Feasi- |
| --- | --- | --- | --- | --- | --- | ------------------------------------------- | --- | --- | --- | --- | --- | ------ |
8 Conclusion bilityofaserverlessdistributedfilesystemdeployed on
|     |     |     |     |     |     | an existing | set of | desktop | PCs. | In ACM | SIGMETRICS |     |
| --- | --- | --- | --- | --- | --- | ----------- | ------ | ------- | ---- | ------ | ---------- | --- |
ThispaperpresentsIvy,amulti-userread/writepeer-to- Conference,June2000.
peerfilesystem.Ivyissuitableforsmallgroupsofcoop-
|     |     |     |     |     |     | [6] B.Callaghan,B.Pawlowski,andP.Staubach. |     |     |     |     | NFSver- |     |
| --- | --- | --- | --- | --- | --- | ------------------------------------------ | --- | --- | --- | --- | ------- | --- |
erating participants who do not have (or do not want) a sion3protocolspecification. RFC1813,NetworkWork-
singlecentralserver.Ivycanoperateinarelativelyopen ingGroup,June1995.
peer-to-peerenvironmentbecauseitdoesnotrequirepar- [7] M.CastroandB.Liskov. PracticalByzantinefaulttoler-
ticipantstotrusteachother. ance. InProc.oftheUSENIXSymposiumonOperating
AnIvyfilesystemconsistssolelyofasetoflogs,one SystemsDesignandImplementation,February1999.
log per participant. This arrangement avoids the need [8] I.Clarke,O.Sandberg,B.Wiley,andT.Hong.Freenet:A
for locking to maintain integrity of Ivy meta-data. Par- distributedanonymous informationstorage and retrieval
|           |              |      |           |     |                 | system. | In Proc. | of the | Workshop | on Design | Issues | in  |
| --------- | ------------ | ---- | --------- | --- | --------------- | ------- | -------- | ------ | -------- | --------- | ------ | --- |
| ticipants | periodically | take | snapshots | of  | the file system |         |          |        |          |           |        |     |
to minimize time spent reading the logs. Use of per- AnonymityandUnobservability,pages46–66,July2000.
participant logs allows Ivy users to choose which other [9] F.Dabek,M.FransKaashoek,D.Karger,R.Morris,and
|     |     |     |     |     |     | I. Stoica. | Wide-area | cooperative |     | storage | with CFS. | In  |
| --- | --- | --- | --- | --- | --- | ---------- | --------- | ----------- | --- | ------- | --------- | --- |
participantstotrust.
Due to its decentralized design, Ivy providesslightly Proc.oftheACMSymposiumonOperatingSystemPrin-
ciples,October2001.
non-traditionalfilesystemsemantics;concurrentupdates
|     |     |     |     |     |     | [10] FIPS180-1. | SecureHashStandard. |     |     | U.S.Departmentof |     |     |
| --- | --- | --- | --- | --- | --- | --------------- | ------------------- | --- | --- | ---------------- | --- | --- |
cangenerateconflictinglogrecords.Ivyprovidesseveral
toolstoautomateconflictresolution.Moreworkisunder Commerce/N.I.S.T.,NationalTechnicalInformationSer-
vice,April1995.
waytoimprovethem.
Experimental results show that the Ivy prototype is [11] K. Fu, M. Frans Kaashoek, and D. Mazie`res. Fast and
|     |     |     |     |     |     | securedistributedread-onlyfilesystem. |     |     |     |     | InProc.ofthe |     |
| --- | --- | --- | --- | --- | --- | ------------------------------------- | --- | --- | --- | --- | ------------ | --- |
twotothreetimesslowerthanNFS.Ivyisavailablefrom
|     |     |     |     |     |     | USENIX | Symposium | on  | Operating | Systems | Design | and |
| --- | --- | --- | --- | --- | --- | ------ | --------- | --- | --------- | ------- | ------ | --- |
http://www.pdos.lcs.mit.edu/ivy/.
Implementation,pages181–196,October2000.
|     |     |     |     |     |     | [12] J.HartmanandJ.Ousterhout. |     |     | TheZebrastripednetwork |     |     |     |
| --- | --- | --- | --- | --- | --- | ------------------------------ | --- | --- | ---------------------- | --- | --- | --- |
Acknowledgments file system. ACM Transactions on Computer Systems,
13(3):274–310,1995.
WethankM.SatyanarayananandCarnegie-MellonUni-
[13] J.Howard,M.Kazar,S.Menees,D.Nichols,M.Satya-
| versity for | making | the | Modified | Andrew | Benchmark |                                   |     |     |     |     |              |     |
| ----------- | ------ | --- | -------- | ------ | --------- | --------------------------------- | --- | --- | --- | --- | ------------ | --- |
|             |        |     |          |        |           | narayanan,R.Sidebotham,andM.West. |     |     |     |     | Scaleandper- |     |
available.WearegratefultothePlanetLabandRONtest- formanceinadistributedfilesystem. ACMTransactions
beds for letting us run wide-area experiments. Sameer onComputerSystems,6(1),February1988.
| Ajmani, | Trevor | Blackwell, | Miguel | Castro, | Josh Cates, |                                      |     |     |     |                   |     |     |
| ------- | ------ | ---------- | ------ | ------- | ----------- | ------------------------------------ | --- | --- | --- | ----------------- | --- | --- |
|         |        |            |        |         |             | [14] J.J.KistlerandM.Satyanarayanan. |     |     |     | Disconnectedoper- |     |     |
RussCox,PeterDruschel,FransKaashoek,AlexLewin, ationintheCodafilesystem. InProc.oftheACMSym-
| David Mazie`res, |     | and Rodrigo |     | Rodrigues | gave us help- |     |     |     |     |     |     |     |
| ---------------- | --- | ----------- | --- | --------- | ------------- | --- | --- | --- | --- | --- | --- | --- |
posiumonOperatingSystemPrinciples,pages213–225,
| ful feedback | about | the | design | and description | of Ivy. | 1991. |     |     |     |     |     |     |
| ------------ | ----- | --- | ------ | --------------- | ------- | ----- | --- | --- | --- | --- | --- | --- |

[15] J. Kubiatowicz, D. Bindel, Y. Chen, S. Czerwinski, [29] S. Ratnasamy, P. Francis, M. Handley, R. Karp, and
P. Eaton, D. Geels, R. Gummadi, S. Rhea, H. Weather- S.Shenker. Ascalablecontent-addressablenetwork. In
spoon,W.Weimer,C.Wells,andB.Zhao. OceanStore: Proc.ACMSIGCOMM,pages161–172,August2001.
| An architecture |     | for | global-scale | persistent | storage. | In  |                 |     |              |     |          |              |     |
| --------------- | --- | --- | ------------ | ---------- | -------- | --- | --------------- | --- | ------------ | --- | -------- | ------------ | --- |
|                 |     |     |              |            |          |     | [30] D. Ritchie | and | K. Thompson. |     | The UNIX | time-sharing |     |
Proc.ofACMASPLOS,pages190–201,November2000.
|                                |     |                    |     |                    |     |           | system.                          | Communications |     | of the | ACM,               | 17(7):365–375, |     |
| ------------------------------ | --- | ------------------ | --- | ------------------ | --- | --------- | -------------------------------- | -------------- | --- | ------ | ------------------ | -------------- | --- |
| [16] P. Kumar                  | and | M. Satyanarayanan. |     | Log-based          |     | directory | July1974.                        |                |     |        |                    |                |     |
| resolutionintheCodafilesystem. |     |                    |     | InProc.oftheSecond |     |           |                                  |                |     |        |                    |                |     |
|                                |     |                    |     |                    |     |           | [31] M.RosenblumandJ.Ousterhout. |                |     |        | Thedesignandimple- |                |     |
InternationalConferenceonParallelandDistributedIn-
|     |     |     |     |     |     |     | mentationofalog-structuredfilesystem. |     |     |     |     | ACMTransac- |     |
| --- | --- | --- | --- | --- | --- | --- | ------------------------------------- | --- | --- | --- | --- | ----------- | --- |
formationSystems,pages202–213,January1993.
tionsonComputerSystems,10(1):26–52,1992.
| [17] L. Lamport. |     | Time, | clocks, | and the ordering |     | of events |                               |     |     |     |                      |     |     |
| ---------------- | --- | ----- | ------- | ---------------- | --- | --------- | ----------------------------- | --- | --- | --- | -------------------- | --- | --- |
|                  |     |       |         |                  |     |           | [32] A.RowstronandP.Druschel. |     |     |     | Storagemanagementand |     |     |
in a distributed system. Communications of the ACM, caching in PAST, a large-scale, persistent peer-to-peer
21(7):558–565,July1978.
|     |     |     |     |     |     |     | storageutility. |     | InProc.oftheACMSymposiumonOp- |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------- | --- | ----------------------------- | --- | --- | --- | --- |
[18] B.Liskov,S.Ghemawat,R.Gruber,P.Johnson,L.Shrira, eratingSystemPrinciples,October2001.
| andM.Williams. |     | ReplicationintheHarpfilesystem. |     |     |     | In  |                   |     |           |     |          |           |     |
| -------------- | --- | ------------------------------- | --- | --- | --- | --- | ----------------- | --- | --------- | --- | -------- | --------- | --- |
|                |     |                                 |     |     |     |     | [33] R. Sandberg, | D.  | Goldberg, | D.  | Kleiman, | D. Walsh, | and |
Proc.oftheACMSymposiumonOperatingSystemPrin-
|     |     |     |     |     |     |     | B.Lyon. | DesignandimplementationoftheSunnetwork |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ------- | -------------------------------------- | --- | --- | --- | --- | --- |
ciples,pages226–38,1991.
|     |     |     |     |     |     |     | filesystem. | InProc.UsenixSummerConference, |     |     |     |     | pages |
| --- | --- | --- | --- | --- | --- | --- | ----------- | ------------------------------ | --- | --- | --- | --- | ----- |
[19] N.Lynch,D.Malkhi,andD.Ratajczak. Atomicdataac- 119–130,June1985.
cessincontentaddressablenetworks.InProc.oftheFirst
|     |     |     |     |     |     |     | [34] D.Santry,M.Feeley,N.Hutchinson,A.Veitch,R.Car- |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
InternationalWorkshoponPeer-to-PeerSystems,March
|     |     |     |     |     |     |     | ton,andJ.Ofir. |     | DecidingwhentoforgetintheElephant |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | -------------- | --- | --------------------------------- | --- | --- | --- | --- |
2002.
filesystem.InProc.oftheACMSymposiumonOperating
[20] U. Maheshwari, R. Vingralek, and W. Shapiro. How SystemPrinciples,pages110–123,1999.
| to build | a trusted | database | system    | on untrusted |           | storage. |                    |         |          |              |            |            |        |
| -------- | --------- | -------- | --------- | ------------ | --------- | -------- | ------------------ | ------- | -------- | ------------ | ---------- | ---------- | ------ |
|          |           |          |           |              |           |          | [35] M. Spreitzer, | M.      | Theimer, | K. Petersen, |            | A. Demers, | and    |
| In Proc. | of the    | USENIX   | Symposium | on           | Operating | Sys-     |                    |         |          |              |            |            |        |
|          |           |          |           |              |           |          | D. Terry.          | Dealing | with     | server       | corruption | in         | weakly |
temsDesignandImplementation,pages135–150,Octo-
|     |     |     |     |     |     |     | consistent, | replicated |     | data systems. |     | In Proc. | of the |
| --- | --- | --- | --- | --- | --- | --- | ----------- | ---------- | --- | ------------- | --- | -------- | ------ |
ber2000.
ACM/IEEEMobiComConference,September1997.
| [21] P.MaymounkovandD.Mazie`res. |     |     |     | Kademlia:Apeer-to- |     |     |                                     |     |     |     |                    |     |     |
| -------------------------------- | --- | --- | --- | ------------------ | --- | --- | ----------------------------------- | --- | --- | --- | ------------------ | --- | --- |
|                                  |     |     |     |                    |     |     | [36] C.Stein,J.Howard,andM.Seltzer. |     |     |     | Unifyingfilesystem |     |     |
peerinformationsystembasedonthexormetric.InProc.
protection.InProc.oftheUSENIXTechnicalConference,
oftheFirstInternationalWorkshoponPeer-to-PeerSys-
pages79–90,2001.
tems,March2002.
|     |     |     |     |     |     |     | [37] I.Stoica,R.Morris,D.Karger,M.FransKaashoek,and |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
[22] D.Mazie`res.Atoolkitforuser-levelfilesystems.InProc.
H.Balakrishnan.Chord:AScalablePeer-to-peerLookup
oftheUsenixTechnicalConference,pages261–274,June
|     |     |     |     |     |     |     | Service | for Internet | Applications. |     | In Proc. | ACM | SIG- |
| --- | --- | --- | --- | --- | --- | --- | ------- | ------------ | ------------- | --- | -------- | --- | ---- |
2001.
COMM,August2001.
| [23] D. Mazie`res, |                                       | M. Kaminsky, |     | M. Frans | Kaashoek, | and |                 |             |     |                 |     |        |         |
| ------------------ | ------------------------------------- | ------------ | --- | -------- | --------- | --- | --------------- | ----------- | --- | --------------- | --- | ------ | ------- |
|                    |                                       |              |     |          |           |     | [38] J. Strunk, | G. Goodson, |     | M. Scheinholtz, |     | and C. | Soules. |
| E.Witchel.         | Separatingkeymanagementfromfilesystem |              |     |          |           |     |                 |             |     |                 |     |        |         |
security. InProc.oftheACMSymposiumonOperating Self-securing storage: Protecting data in compromised
|     |     |     |     |     |     |     | systems. | InProc.oftheUSENIXSymposiumonOperat- |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | -------- | ------------------------------------ | --- | --- | --- | --- | --- |
SystemPrinciples,December1999.
ingSystemsDesignandImplementation,pages165–179,
| [24] D.Mazie`resandD.Shasha. |     |     |     | Buildingsecurefilesystems |     |     |     |     |     |     |     |     |     |
| ---------------------------- | --- | --- | --- | ------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
October2000.
| out of | Byzantine | storage. | In  | Proc. of | the Twenty-First |     |                                                    |     |     |     |     |     |     |
| ------ | --------- | -------- | --- | -------- | ---------------- | --- | -------------------------------------------------- | --- | --- | --- | --- | --- | --- |
|        |           |          |     |          |                  |     | [39] D.Terry,M.Theimer,K.Petersen,A.Demers,M.Spre- |     |     |     |     |     |     |
ACMSymposiumonPrinciplesofDistributedComputing
itzer,andC.Hauser.ManagingupdateconflictsinBayou,
(PODC2002),2002.
|     |     |     |     |     |     |     | aweakly | connectedreplicated |     |     | storage system. |     | InProc. |
| --- | --- | --- | --- | --- | --- | --- | ------- | ------------------- | --- | --- | --------------- | --- | ------- |
[25] R.Merkle.Adigitalsignaturebasedonaconventionalen-
oftheACMSymposiumonOperatingSystemPrinciples,
| cryptionfunction. |     | InAdvancesinCryptology—CRYPTO |     |     |     |     |     |     |     |     |     |     |     |
| ----------------- | --- | ----------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
pages172–183,December1995.
’87, volume293ofLectureNotesinComputerScience,
|     |     |     |     |     |     |     | [40] C.Thekkath,T.Mann,andE.Lee.Frangipani:Ascalable |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------------------------------------------------- | --- | --- | --- | --- | --- | --- |
pages369–378.Springer-Verlag,1987.
|                                           |     |     |     |     |     |           | distributedfilesystem. |     |     | InProc.oftheACMSymposium |     |     |     |
| ----------------------------------------- | --- | --- | --- | --- | --- | --------- | ---------------------- | --- | --- | ------------------------ | --- | --- | --- |
| [26] T.Page,R.Guy,G.Popek,andJ.Heidemann. |     |     |     |     |     | Architec- |                        |     |     |                          |     |     |     |
onOperatingSystemPrinciples,pages224–237,1997.
tureoftheFicusscalablereplicatedfilesystem.Technical
|     |     |     |     |     |     |     | [41] B.Zhao,J.Kubiatowicz,andA.Joseph.Tapestry:Anin- |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------------------------------------------------- | --- | --- | --- | --- | --- | --- |
ReportUCLA-CSD910005,1991.
frastructureforfault-tolerantwide-arealocationandrout-
[27] D.Parker,G.Popek,G.Rudisin,A.Stoughton,B.Walker,
|     |     |     |     |     |     |     | ing. | Technical | Report | UCB/CSD-01-1141, |     | Computer |     |
| --- | --- | --- | --- | --- | --- | --- | ---- | --------- | ------ | ---------------- | --- | -------- | --- |
E.Walton,J.Chow,D.Edwards,S.Kiser,andC.Kline.
ScienceDivision,U.C.Berkeley,April2001.
Detectionofmutualinconsistencyindistributedsystems.
| InIEEE | Transactionson |     | Software | Engineering, |     | volume |     |     |     |     |     |     |     |
| ------ | -------------- | --- | -------- | ------------ | --- | ------ | --- | --- | --- | --- | --- | --- | --- |
9(3),pages240–247,1983.
| [28] S. Quinlan  | and | S. Dorward.                     |     | Venti: a | new approach | to  |     |     |     |     |     |     |     |
| ---------------- | --- | ------------------------------- | --- | -------- | ------------ | --- | --- | --- | --- | --- | --- | --- | --- |
| archivalstorage. |     | InProc.oftheConferenceonFileand |     |          |              |     |     |     |     |     |     |     |     |
StorageTechnologies(FAST),January2002.
