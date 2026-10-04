| Merging | Semantics | for Conflict | Updates |     | in Geo-Distributed |
| ------- | --------- | ------------ | ------- | --- | ------------------ |
File Systems
|              | Vinh Tao | Thanh, Marc | Shapiro, | Vianney | Rancurel |
| ------------ | -------- | ----------- | -------- | ------- | -------- |
| To cite this | version: |             |          |         |          |
Vinh Tao Thanh, Marc Shapiro, Vianney Rancurel. Merging Semantics for Conflict Updates in Geo-
Distributed File Systems. ACM Int. Systems and Storage Conf. (Systor), 2015, Haifa, Israel. pp.10.1–10.12,
| ⟨10.1145/2757667.2757683⟩. | ⟨hal-01248190⟩ |         |              |     |     |
| -------------------------- | -------------- | ------- | ------------ | --- | --- |
|                            |                | HAL Id: | hal-01248190 |     |     |
https://inria.hal.science/hal-01248190v1
Submittedon24Dec2015
HAL is a multi-disciplinary open access archive L’archiveouvertepluridisciplinaireHAL,estdes-
for the deposit and dissemination of scientific re- tinée au dépôt et à la diffusion de documents scien-
searchdocuments,whethertheyarepublishedornot. tifiquesdeniveaurecherche,publiésounon,émanant
Thedocumentsmaycomefromteachingandresearch des établissements d’enseignement et de recherche
institutionsinFranceorabroad,orfrompublicorpri- français ou étrangers, des laboratoires publics ou
| vateresearchcenters. |     |     | privés. |     |     |
| -------------------- | --- | --- | ------- | --- | --- |
HALAuthorization

Merging Semantics for Conflict Updates
in Geo-Distributed File Systems
VinhTao MarcShapiro VianneyRancurel
Scality,UPMC-LIP6,andINRIA INRIAandUPMC-LIP6 Scality
vinh.tao@lip6.fr marc.shapiro@acm.org vianney.rancurel@scality.com
Abstract The eventual consistency approach [Vogels 2009, 2008;
Terry et al. 1995] is a usual solution for large scale geo-
We present our model of file systems and our merging se-
distributedfilesystemstosolvetheirproblemsofavailabil-
manticsforresolvingconflictupdatesingeo-distributedfile
ity and scalability. In eventually consistent systems, all up-
systems.Thesystemmodelfullydescribesafilesystemwith
datesarecommittedlocallyoneachsitebeforebeingasyn-
allofitscomponentsincludinghardlinks.Thismodelisable
chronouslypropagatedtotheothersites.Theeventualcon-
toidentifyallconflictcaseswhichareclassifiedintodirect,
sistencyapproachensuresthat,whenallsiteshavereceived
such as concurrent updates to the same file, and indirect,
and applied all updates from each other, all sites will have
suchascyclesinthenamespaceofthefilesystem.Themerg-
thesamestate.Becauseupdatesarecommittedlocallywith-
ingsemanticsresolvealltypesofconflictswhilebeingable
out coordination between sites, writing to these distributed
topreservetheeffectofallconflictupdates.Ourimplemen-
filesystemsisfast,addingmoresitesincreasesthethrough-
tationofthesystemandthemergingsemanticsoutperforms
putlinearly,andasitecouldbedisconnectedwhileuserson
theexistingsystemsintermsoffeaturecompleteness.
asitecanstillmakemodificationstothefilesystems.
CategoriesandSubjectDescriptors D.4.3[OperatingSys- The most long-standing issue with eventually consistent
tems]:FileSystemsManagement—Distributedfilesystems
systems is that they may have conflict updates when syn-
GeneralTerms Algorithm,Design chronizingthesites.Theseupdatesarethosefromdifferent
sitesthatconcurrentlytargetthesamepartofthefilesystem.
Keywords Geo-Distributed File System, Eventual Consis-
Forexample,usersondifferentsitesmayconcurrentlywrite
tency,CRDT,ConflictResolution,State-basedReplication
tothesamefile;itisdifficulttochoosewhichwaytomerge
theupdatessothatallsiteshavethesamestateaftermerging.
1. Introduction
In this work, we focus on the issue of resolving conflict
Geo-distributedfilesystemsarethosespanmultipleseparate updatesineventuallyconsistentgeo-distributedfilesystems.
locations, called sites or data centers, each of which fully
replicates the state of a common file system. Inter-site net-
1.1 ExistingApproaches
works of these file systems usually have limited bandwidth
andhighlatencycomparedtotheintra-sitecounterparts.In Theexistingapproachestotheproblemofconflictresolution
order to be available and scalable to serve and to adapt to forsynchronizationingeo-distributedfilesystemareclassi-
the increasingly high storage demand and large number of fiedintotwogroups:operation-basedandstate-based.
userswiththesenetworks,geo-distributedfilesystemsusu- Theoperation-basedapproacheslogthefilesystemoper-
ally have to make some trade-off between the consistency ations on each site and then propagate the log to the other
andtheavailabilityoftheirservices.Thistrade-offhasbeen sitesonwhichtheseoperationsmaybereplayedtokeepthe
formalized in the CAP theorem [Brewer 2000; Gilbert and replicas consistent. Examples include IceCube [Kermarrec
Lynch2002]. et al. 2001], Bayou [Terry et al. 1995], and Ramsey’s alge-
braicapproach[Ramseyetal.2001].
Permissiontomakedigitalorhardcopiesofallorpartofthisworkforpersonalor The state-based approaches keep track of the state of
classroomuseisgrantedwithoutfeeprovidedthatcopiesarenotmadeordistributed each file and directory, then the final states or deltas of the
forprofitorcommercialadvantageandthatcopiesbearthisnoticeandthefullcitation
onthefirstpage.Copyrightsforcomponentsofthisworkownedbyothersthanthe changed files and directories are propagated to the other
author(s)mustbehonored.Abstractingwithcreditispermitted.Tocopyotherwise,or sitestobemergedthere.ExamplesofthisapproachareFi-
republish,topostonserversortoredistributetolists,requirespriorspecificpermission
and/orafee.RequestpermissionsfromPermissions@acm.org. cus [Reiher et al. 1994], Coda [Kistler and Satyanarayanan
SYSTOR’15, May26–28,2015,Haifa,Israel 1992; Satyanarayanan et al. 1990], Unison [Balasubrama-
Copyrightisheldbytheowner/author(s).PublicationrightslicensedtoACM.
niamandPierce1998],AndrewFileSystem[Howard1988;
Copyright(cid:13)c 2015ACM978-1-4503-3607-9/15/05...$15.00.
http://dx.doi.org/10.1145/2757667.2757683 Kazar1988],andMicrosoft’sDFS-R[Bjørner2007].

Theoperation-basedapproacheshoweverusuallyrequire root tov;thispathisalsoreferredtoastheabsolutepathof
global synchronizations, which are moments when all sites v. Absolute paths are unique, i.e., there are no two vertices
stop receiving more updates and exchange their logs to de- withthesameabsolutepath.
Eachpath2specifiesthestricttotalorderinformationover
finenewsequencesofoperationstobeappliedoneachsite;
this is not practical in real-world geo-distributed file sys- the set of vertices in the path. A path p of a vertex v is
i
| tems.Moreover,anexperiencewithourdeployment[Segura |     |     |     |     |     |     | formalizedby: |     |     |     |     |     |     |
| -------------------------------------------------- | --- | --- | --- | --- | --- | --- | ------------- | --- | --- | --- | --- | --- | --- |
etal.2014]foralargetelecommunicationsserviceprovider
|     |     |     |     |     |     |     |     | p={v | ... | v ,i=0..n} |     |     | (1) |
| --- | --- | --- | --- | --- | --- | --- | --- | ---- | --- | ---------- | --- | --- | --- |
in France has shown that the number of operations in that 0 i
real-world system is three orders of magnitude larger than where v 0 is root and is the hierarchy relation of the
thenumberofchangedfilesanddirectories,whichresultsin vertices,whichisaone-to-manyrelationship.
muchlargerlogsizeascomparedtofinalstatesordeltas. Real-world file systems strictly follow the above speci-
Thestate-basedapproachesusuallydonotmodelfilesys- fication, however, they may use different notations. In Mi-
temscompletely.Forexample,theseapproachesassumethat crosoftNTFS,therootisthedevicename,e.g.,C:,andthe
different names in the namespace are mapped to different hierarchyrelationisdenotedwithabackwardslash\.Inthe
files, thus they do not work with file hard links in file sys- implementations of the POSIX API, the forward slash / is
tems. This incorrect file system model may lead to anoma- usedforboththerootandthehierarchyrelation.Withthese
lousbehavioursasdescribedinSection6ofthispaper. notations, the highlighted path in Figure 1a is denoted as
C:\foo\fileinMicrosoftNTFSwhileitis/foo/filein
1.2 OurContributions
POSIXimplementations.
Inthiswork,wetargettheproblemsofresolvingconflictup- Forsimplicity,wewillusethePOSIX-stylenotation,i.e.,
dateswhensynchronizingdivergedreplicasofafilesystem. to use forward slash / for both the root and the hierarchy
Ourcontributionsare: relation,intheremainingpartsofthepapertopresentpaths.
• Wefullymodelfilesystemswhichinclude:thehierarchi-
Themappingbetweenthenamespaceanddata(Fig.1b),de-
calnamespaceandthemappingbetweenthenamespace notedby→,isaseparatemappingrelationbetweenvertices,
andcontent(Section2).Ourmodelworkswithallcom- whicharerepresentedbytheiruniquepaths{p ,i = 1..n},
i
| ponents, | including | hard | links, | and | with all operations, |     |                                |     |     |     |            |     |     |
| -------- | --------- | ---- | ------ | --- | -------------------- | --- | ------------------------------ | --- | --- | --- | ---------- | --- | --- |
|          |           |      |        |     |                      |     | anddataobjects(namedinodes3){i |     |     |     | ,j =1..m}. |     |     |
j
suchasrename,offilesystems,whicharethelimitations There are two predefined types of inode: directory and
oftheexistingapproaches.
|              |     |        |           |          |      |            | file. The  | mapping of vertices |         | to directory | inodes | is      | always    |
| ------------ | --- | ------ | --------- | -------- | ---- | ---------- | ---------- | ------------------- | ------- | ------------ | ------ | ------- | --------- |
| • We propose | an  | update | detection | strategy | that | helps sys- |            |                     |         |              |        |         |           |
|              |     |        |           |          |      |            | one-to-one | while the           | mapping | of vertices  |        | to file | inodes is |
tematically identifying both direct and indirect conflicts many-to-one. That means, a vertex maps to only one inode
(Section4.1).Thisstrategyimprovestheexistingad-hoc
ofanytype,andmanyverticescanmaptoafileinode.These
approachesindetectingindirectconflictssuchasnames- mappingsaredefinedas:
pacecycles.
(cid:40) 1:1
• Wespecifyourmergingsemanticsforresolvingconflict p −→i type(i )=directory
|     |     |     |     |     |     |     |     | i   | j   | j   |     |     | (2) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
updates(Section4.2).Thesemergingsemantics,fromthe p − n → :1 i type(i )=file
|        |              |          |     |          |            |        |         | i                 | j     | j       |     |          |         |
| ------ | ------------ | -------- | --- | -------- | ---------- | ------ | ------- | ----------------- | ----- | ------- | --- | -------- | ------- |
| user’s | perspective, | preserve |     | both the | namespaces | of the |         |                   |       |         |     |          |         |
|        |              |          |     |          |            |        | whereas | the function type | tells | whether | an  | inode is | of type |
replicasandthedataoftheseconflictupdates.
|     |     |     |     |     |     |     | directory | or file. The | type | of a vertex | is  | identified | as the |
| --- | --- | --- | --- | --- | --- | --- | --------- | ------------ | ---- | ----------- | --- | ---------- | ------ |
2. SystemModel typeoftheinodethatthevertexismappedto.Aleafvertex
|     |     |     |     |     |     |     | is only referred | to as a | directory | or  | a file depending |     | on the |
| --- | --- | --- | --- | --- | --- | --- | ---------------- | ------- | --------- | --- | ---------------- | --- | ------ |
In this section, we describe our model of real-world file typeoftheinodetowhichthepathofthevertexismapped,
systemsandconflictcasesingeo-distributedfilesystems.
|     |     |     |     |     |     |     | while the | type of a non-leaf |     | vertex | (including | the | root) is |
| --- | --- | --- | --- | --- | --- | --- | --------- | ------------------ | --- | ------ | ---------- | --- | -------- |
alwaysadirectory.
2.1 FileSystem
Thefullfilesystemcouldbethendescribedbyacompo-
Afilesystemiscomposedof(1)anamespacethatpresents
|     |     |     |     |     |     |     | sition of | the hierarchy | and mappings |     | relations, | as  | depicted |
| --- | --- | --- | --- | --- | --- | --- | --------- | ------------- | ------------ | --- | ---------- | --- | -------- |
ahierarchicalstructureofthefilesystemtousersand(2)the inFigure1b;thismodelisknownintheliteratureasapar-
mappingbetweenthenamesinthenamespaceanddata.
tiallyorderedset(poset).Becauseoftheone-to-onerelation-
Thenamespaceisadirectedrootedtreewhoseedgespoint shipbetweenverticesanddirectoryinodes,wecanconsider
|           | root1 |       |      |         |            |            | thesemappingsasasinglevertexinthefullmodelofthefile |     |     |     |     |     |     |
| --------- | ----- | ----- | ---- | ------- | ---------- | ---------- | --------------------------------------------------- | --- | --- | --- | --- | --- | --- |
| away from | the   | (Fig. | 1a). | In this | structure, | there is a |                                                     |     |     |     |     |     |     |
vertexrootwhichisthestartingpointfornavigatingthetree. systemwithoutimpactingcorrectness;thesimplifiedmodel
Foranyothervertexv,thereisonlyonedirectedpathpfrom (Fig.1c)isstillaposetofverticesandinodes.
2Unlessstatedotherwise,weusethetermpathtorefertotheabsolutepath
| 1We argue | that the | back and | self pointers | of  | directories | in real-world |     |     |     |     |     |     |     |
| --------- | -------- | -------- | ------------- | --- | ----------- | ------------- | --- | --- | --- | --- | --- | --- | --- |
ofavertex.
| implementations | are | an engineering | optimization |     | to support | the use of |     |     |     |     |     |     |     |
| --------------- | --- | -------------- | ------------ | --- | ---------- | ---------- | --- | --- | --- | --- | --- | --- | --- |
3Weusetheterminodetorefertoaninodeobject,followingPOSIXterms,
relativepaths;notmodellingthesepointersdoesnotimpactthecorrectness
| ofoursystemmodel. |     |     |     |     |     |     | anditsreferringdataasawhole. |     |     |     |     |     |     |
| ----------------- | --- | --- | --- | --- | --- | --- | ---------------------------- | --- | --- | --- | --- | --- | --- |

(a) root (b) root i (c) root Legend
0
foo directory
foo bar foo i bar i foo bar
1 2 file file
i inode
qux file file qux qux file file qux qux file file qux 4
hierarchy relation
i i i i mapping relation
3 4 5 4
Figure 1. System model showing a namespace and its mapping with inodes. With diagrams a shows the namespace as a
rooted-directed tree, b describes the full system model with the namespace and its mapping with inodes, and c depicts the
simplifiedsystemmodelwherepairsofvertexanddirectoryinodearegroupedtogetherintosingleelements.
2.2 DivergenceandReconciliation other user on another site creates a file /bar, merging the
replicas in this case results in {/foo, /bar} which is the
Diverged replicas of a file system may differ in the tree
superset of both replicas and does not violate any speci-
structures of their namespaces, in the mappings of the
fication of the system model. The other conflicts are non-
namespacesandtheinodes,orinthecontentoffileinodes.
commutative. For example, if a directory /foo is concur-
The divergence is a possible outcome of concurrent opera-
rentlyrenamedto/baronasiteandto/quxonanothersite,
tions,whichareusedbyeventualconsistencyinthepresence
thenmergingthereplicascomputestheirsuperset,whichis
ofnetworkpartitionordelay.
{/bar, /qux}, however, the result violates the one-to-one
TheexamplesinFigure2presentsomepossiblecasesof
property of the directory vertex-inode mapping. In this re-
divergence.Inthefirstexample(Fig.2a),usersondifferent
search,weonlyfocusonthenon-commutativeconflictsand
sites A and B see different namespaces of the file system.
use the term conflict to refer to those of this type unless
In the next example (Fig. 2b), users on both sites A and
statedotherwise.
B see the same namespace with the paths /foo and /bar.
Weclassifytheconflictcasesintotwogroups:directand
However, due to different mappings, these paths represent
indirect.
different files on A, while on B, they are hard links to the
samefile.Thesedifferentmappingsleadtothedivergencein
the structures of the replicas. In another example (Fig. 2c), Directconflictsarecausedbyconcurrentupdatesthattarget
replicas on both A and B have the same namespace and the same elements, such as vertices, inodes, or mappings.
mappings, but the contents of the file at /foo are different Thedirectcasesarelistedbellow.
duetodifferentusesonthesesites. Stateconflict(Fig.3a).Anelementisconcurrentlydeleted
andmodifiedbyusersondifferentsites.Forexample,afile
Reconcilingdivergedreplicasistobringthedivergedrepli-
is removed while it is being edited. The different states,
casofafilesystemtoacommonstatewheretheyhavethe
removed and updated, of the same element cannot appear
same namespace, same mappings, and same file contents.
togetherinthemergedresult.
Thecommonstateshouldbe‘meaningful’inthesensethat
Dataconflict(Fig.3b).Usersfromdifferentsitesconcur-
itcanbeexplainedintuitivelytousers.
rentlywritetothesamefile.Thesedifferentcontentsgener-
Reconciliation is a pairwise process in which each pair
ally cannot be merged together in the same inode (we will
ofdivergedreplicasasynchronouslyexchangesandsynchro-
considerthisissueinmoredetailinSection4.3.1).
nizestheirstates.Apairofdivergedreplicasisreconciled(or
Naming conflict (Fig. 3c, 3d, 3e). This type of conflict
merged)bysimplycomputingtheunionsoftheirupdatedel-
happenswhenverticeswiththesamepathexist.Forexam-
ements,suchasverticesandinodes.Mergingelementsthat
ple,usersondifferentsitesmayconcurrentlycreate/rename
are updated on either site updates the other; merging those
filesand/ordirectorieswith/tothesamename.Mergingthese
updated on both sites, a situation which is referred to as a
replicas violates the uniqueness of vertices, or in another
conflict,mayrequireconflictresolution.
way, violates the one-to-one and many-to-one properties of
themappingrelationswhenitcausesapathtobemappedto
2.3 ConflictCases
manyinodes.
Conflicts are either commutative and non-commutative. A Mapping conflict (Fig. 3f). Users on different sites map
commutative conflict is a pair of diverged replicas whose different names to a directory inode. As a result, merging
mergedstateisthesupersetoftheirstatesandwhosemerged thesereplicasviolatestheone-to-onedirectoryvertex-inode
statedoesnotviolateanyspecificationofthesystemmodel. mapping.Forexample,thenameofadirectory/foo,which
For example, if a user on a site creates a file /foo and an- is mapped to inode i, is concurrently changed to /bar and

(a) root A B root (b) root root (c) root root
A B A B
foo bar foo bar qux foo bar foo bar foo foo
qux file file qux bar qux i i i i i
1 2 3
Figure2. ExampleofdivergedreplicasofsitesAandB.Withexamplesa,b,andcdescribedifferentscenariosofdivergenceof
namespaces,namespace-inodemappings,andinodecontents,respectively;squareandtrianglearedifferentupdatedcontents.
/quxonsitesAandB,respectively.Mergingtheseupdates The namespace approach is free from the indirect con-
makesboth/barand/quxtobemappedtoi,whichviolates flicts.Inthecaseofdelete-while-edit,becausetheexistence
thedirectorymappingrule.Concurrentchangestothename of an updated element (file or directory) on a site implies
of a file, however, is not an issue because the mapping of that of its parent directories on the same site, merging by
verticestofileinodesisamany-to-onerelationship. computing the union of the path collections ensures the el-
ementanditsparentdirectoriestoexistinthemergeresult.
Indirect conflicts are caused by updates that target differ-
Inthecaseofcycles-in-the-namespace,becausethenumber
ent elements, suchthat merging the replicas towhich these
of the paths in a collection is finite, merging by computing
updates target would not cause any direct conflict, but the
the union of different collections results in a finite number
merging result would be anomalous. In the followings, we
ofpaths;thereforecyclescannotformwhentheyneedtobe
studysomecommonexamplesusedintheliteratureandfor-
representedbyaninfinitenumberofpaths.
mallydefineindirectconflicts.
The namespace approach however does not fully model
Example 1. Delete-while-edit (Fig. 3g). The example il-
file systems. It assumes the one-to-one mapping between
lustratesthesituationwherethefile/foo/fileiseditedon
paths and inodes, and thus this approach does not take into
site A when the directory /foo is deleted on site B. Merg-
account hard links. This results in the waste of storage and
ingthereplicasonAandB wouldnotresultinanyconflict
bandwidthwhensynchronizingreplicas.Forexample,anew
sincetheupdatestargetdifferentelementsofthesystem,but
name linked to an existing file could be considered to be a
intheresult,thefile/foo/fileisdeleted.
newfile,andthusthecontentofthefilecouldneedtobesyn-
Example 2. Cycles in the namespace (Fig. 3h). In this
chronizedagainbetweenreplicas.Anotherlimitationofnot
example, the directory foo is moved into another directory
modellinghardlinksisindetectingwhetherapathhasbeen
bar on site A, while on site B, bar is moved into foo.
updatedornot.Inthecasethereisanupdatetoafileinode
Merging these replicas produces a directed cycle between
withmultiplenames,thenamespaceapproachonlyidentifies
fooandbar,whichviolatesthepartialorderrelationship.
the names through which the file inode was updated as up-
The common pattern of indirect conflicts from these
dated.Thisapproachthereforecannotdetectthedirectcon-
aboveexamplesisthatupdatesthattargetdifferentelements
flict when users on different sites write to the same file in-
in a file system interfere in the path of each other. This is
odethroughdifferentnames.Moreover,implementationsof
illustrated in Example 1 where the delete update modifies
anincorrectfilesystemmodelmayresultinanomalousout-
the path of the updated file, and in Example 2 where each
comeswhensynchronizingdivergedreplicasasdescribedin
updatemodifiesthepathoftheother.
theexperimentswithDropbox(Section6).
3. ExistingApproaches 3.2 TheInodeApproach
State-basedapproachestotheproblemofconflictresolution The inode approach models a file system as a collection of
for synchronization in geo-distributed file systems can be separateinodes(ordatabaserecordsasinthecaseofDFS-
classifiedasnamespaceapproachesandinodeapproaches. R [Bjørner 2007]). The namespace is stored in the direc-
tory inodes and data is stored in the file inodes. Merging
3.1 TheNamespaceApproach
divergedreplicasinvolvescomputingtheunionofthecorre-
Thenamespaceapproachmodelsafilesystemasacollection spondinginodecollections.Thisisactuallyhowreal-world
of paths, each of which represents a different directory or filesystemsareimplemented.Examplesofthisapproachare
file.Mergingthereplicasofafilesystemcomputestheunion LOCUS [Walker et al. 1983; Parker Jr et al. 1983; Popek
of the path collections. Conflicts happen when the same andWalker1985]anditsdescendants,suchasFicus[Reiher
pathrepresentingdifferentcontentsexists.Examplesofthis etal.1994]Rumor[Guyetal.1999]andRoam[Ratneretal.
approach are Unison [Balasubramaniam and Pierce 1998], 1999,2004].
Dropbox [Dropbox], and version control systems such as Theinodeapproachhoweverispronetotheindirectcon-
Git[Git]andSVN[Apacheb]. flicts,whenupdatestargetelementsthatareonthesamepath

|     |     | Direct Conflicts |      |     |     |      |      |      | Indirect Conflicts |     |      |       |     |     |
| --- | --- | ---------------- | ---- | --- | --- | ---- | ---- | ---- | ------------------ | --- | ---- | ----- | --- | --- |
| foo | foo |                  |      | foo | foo |      |      |      |                    |     | root | (init | )   | (h) |
|     |     | file             | file |     |     | root | root | root | root               |     |      |       |     |     |
2
|       |     |       |       |     |       |         |      |       |        |     | bar  | foo  |        |     |
| ----- | --- | ----- | ----- | --- | ----- | ------- | ---- | ----- | ------ | --- | ---- | ---- | ------ | --- |
| bar   | bar | i     | i     | bar | bar   |         |      |       |        |     |      |      |        |     |
|       |     | 4     | 4     |     |       | foo     | foo  | foo   | foo    |     |      |      | root   |     |
|       |     |       |       |     |       |         |      |       |        |     | A    | B    |        |     |
| A (a) | B   | A     | (b) B | A   | (c) B |         |      |       |        |     |      |      |        |     |
|       |     |       |       |     |       |         |      |       |        |     | root | root |        |     |
| foo   | foo | foo   | foo   | bar | qux   | file    | file | file  | file   |     |      |      | foo    |     |
|       |     |       |       |     |       |         |      |       |        |     | bar  | foo  |        |     |
|       |     |       |       |     |       | i       | i    | i     | i      |     |      |      |        |     |
| bar   | bar | bar   | bar   | i   | i     | 4       | 4    | 4     | 4      |     |      |      | bar    |     |
|       |     |       |       |     |       |         |      |       |        |     | foo  | bar  |        |     |
| A (d) | B   | A (e) | B     | A   | (f) B | (init ) | A    | (g) B | merged |     |      |      | merged |     |
1
Figure3. Conflictcaseswitha,b,c,d,e,andf asexamplesofdirectconflictandg andhasexamplesofindirectconflict.
WithAandBaredifferentsites;dashedelementsarethosedeleted;squaresandtrianglesrepresentdifferentupdatedcontents;
boldshapesareupdatedelements;thediagramsinit andinit aretheinitialcommonstatesofthereplicasintheexamplesg
|     |     |     |     |     |     | 1   | 2   |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
andh,respectively;mergedisthefinalcommonstateofthefilesystemaftermergingthedivergedreplicas.
but are stored in different inodes. Detecting and resolving Thepathofadeletedelement,however,isnotregardedas
indirect conflicts are usually ad-hoc. For example in Ficus, beingupdated.Thisstrategyistopreventthesituationwhere
delete-while-edit
the updated files in indirect conflicts are multipledeletionswouldnotdeleteapath.
| moved into | a special | directory, |     | or in DFS-R, | cycles | in the |     |     |     |     |     |     |     |     |
| ---------- | --------- | ---------- | --- | ------------ | ------ | ------ | --- | --- | --- | --- | --- | --- | --- | --- |
4.2 ConflictResolutionPrinciples
namespacearestoredinsomearbitrarydirectories.
|     |     |     |     |     |     |     | We believe |     | that the common |     | state | of the file | system | after |
| --- | --- | --- | --- | --- | --- | --- | ---------- | --- | --------------- | --- | ----- | ----------- | ------ | ----- |
4. ConflictResolution mergingdivergedreplicasis‘meaningful’touserswhenthe
|                  |     |            |     |                  |     |          | users | can still | see the | effect | of their | own updates | after | the |
| ---------------- | --- | ---------- | --- | ---------------- | --- | -------- | ----- | --------- | ------- | ------ | -------- | ----------- | ----- | --- |
| In this section, |     | we propose | our | update detection |     | strategy |       |           |         |        |          |             |       |     |
merge.Weformalizethis‘meaningfulness’bythefollowing
thatcansystematicallyidentifyallconflictcases,andwede-
principlesforresolvingconflictupdates.
scribeourgeneralprinciplesandthedetailmergingseman-
|     |     |     |     |     |     |     | Principle | 1:  | No lost update. |     | To preserve | all | updates | on all |
| --- | --- | --- | --- | --- | --- | --- | --------- | --- | --------------- | --- | ----------- | --- | ------- | ------ |
ticsforresolvingtheseconflicts.
replicasbecausetheseupdatesareequallyvalid.
4.1 UpdateDetection Principle 2: No side-effect. To not cause anomalous merge
|            |         |     |        |             |        |          | results. | For | example, | moving | /foo | into /bar | on a | site and |
| ---------- | ------- | --- | ------ | ----------- | ------ | -------- | -------- | --- | -------- | ------ | ---- | --------- | ---- | -------- |
| What is an | update? | An  | update | is a change | in the | state of |          |     |          |        |      |           |      |          |
/barinto/fooonanothersitemaycausethemtodisappear
thefilesystemcausedbysomeoperations.Weclassifythese
afterthemergewhenacycleofthesedirectoriesisformed.
operationsintotwomaingroups,deletionandmodification.
Fromtheaboveprinciples,weidentifytheirmainproper-
Thoseofdeletiontypearetheoperationsthatremoveaver-
tex (directory, file, or inode) or a relationship (hierarchy or ties, they are element preservation and relationship preser-
mapping).Thoseofmodificationareoperationsthatmodify vation.Thesepropertiesaredescribedinthefollowings.
| existing elements |     | of the | system, | such as | writing | to an ex- |     |     |     |     |     |     |     |     |
| ----------------- | --- | ------ | ------- | ------- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- | --- |
4.2.1 ElementPreservation
istinginodeorchangingthenameofavertex.Thecreation
|     |     |     |     |     |     |     | The | state of | an updated | replica | of  | an element | is  | always |
| --- | --- | --- | --- | --- | --- | --- | --- | -------- | ---------- | ------- | --- | ---------- | --- | ------ |
ofanewfileordirectoryisregardedasamodificationofthe
|     |     |     |     |     |     |     | preserved | when | its conflict | with | another | updated | replica | of  |
| --- | --- | --- | --- | --- | --- | --- | --------- | ---- | ------------ | ---- | ------- | ------- | ------- | --- |
parentdirectory.Therenameoperationonavertexisconsid-
eredadeletionofthevertex’soldrelationshipandacreation thatelementisresolvedinapairwisemerge.
|                 |     |              |     |           |           |        | We  | define | the act | of resolving | conflicts |     | when merging |     |
| --------------- | --- | ------------ | --- | --------- | --------- | ------ | --- | ------ | ------- | ------------ | --------- | --- | ------------ | --- |
| of the vertex’s | new | relationship |     | which, as | a result, | modify |     |        |         |              |           |     |              |     |
thetargetparentdirectoryandthevertexitself. a pair of diverged replicas of an element as a function,
namedmerge,thattakesthereplicasasinputsandproduces
| What are    | affected       | by  | an update? | The direct  | targets, | i.e., |           |      |           |     |        |          |           |      |
| ----------- | -------------- | --- | ---------- | ----------- | -------- | ----- | --------- | ---- | --------- | --- | ------ | -------- | --------- | ---- |
|             |                |     |            |             |          |       | an output | that | preserves | the | states | of these | replicas. | This |
| vertices or | relationships, |     | are the    | first to be | changed  | by an |           |      |           |     |        |          |           |      |
update;thesearethecausefordirectconflicts.Withourdef- functionwithelementpreservationisformallydefinedas:
initionofindirectconflicts,i.e.,updatesthatdonotdirectly merge(rA,rB)=r(cid:48) :rA ⊆r(cid:48),rB ⊆r(cid:48)
(3)
| conflict but | target | the path | of the | others, | we believe | that if |     |     |     |     |     |     |     |     |
| ------------ | ------ | -------- | ------ | ------- | ---------- | ------- | --- | --- | --- | --- | --- | --- | --- | --- |
whererAandrBaredivergedreplicasofanelementonsites
wewanttodetectindirectconflicts,weneedtoalsoidentify
the elements in the path of an updated element as updated. AandB,respectively.
Indeed, by doing so, we would be able to break an indirect We also require that the outcomes of different mergings
conflict into several direct conflicts when an update targets ofreplicasofdifferentelementsbealsodifferent.Forexam-
anelementinthepathofanother. ple, merging diverged replicas of the file foo and merging

thoseofthefilebardonotresultinthesamefilequx.This changethestructureofthefilesystem,afterthemerge,they
requirementisdefinedas: shouldbeabletoseetheupdatedstructureinthemergedre-
sult. For example, if a directory foo is moved into another
r (cid:54)=r ⇐⇒ merge(r ,r∗)(cid:54)=merge(r ,r∗) (4)
x y x x y y directory bar on a site, the updated hierarchy relationship
foo/bar should be preserved when merging with concur-
where r and r are replicas on the same site of different
x y
elementsxandyofthefilesystem;r∗andr∗arereplicasof rentmodificationstothesedirectoriesonanothersite.
x y
Thispropertyenablesmergetoworkwithfilesystemhard
xandyonanyothersite.
links in the sense that merge can preserve the mapping be-
Theelementpreservationpropertymakesthemergefunc-
tweenafileinodeanditsnameswhenmerging.Forexample,
tion to be able to preserve the consistency of the user’s
ifusersondifferentsitesAandBupdateaninodeithrough
viewoftheirdatabeforeandafterthemerge.Thismeans,if
its hard links foo and bar, respectively, then merge would
the user updates any file or directory, the updated elements
preservebothstructuresof{foo→i,bar→i}oneachsite
shouldalsobeavailableinthemergingoutcome.Someex-
intheoutcome.Thisisanadvantageofoursystemcompared
amplesofusingmergewithelementpreservationinmerging
tothenamespaceapproachwhenthelattercannotdetectthe
conflictupdatesaredescribedinFigure4.Thefirstexample
conflictonfooandbar.
(Fig. 4a) depicts the situation where the content of a file is
The merge function with both element preservation and
changed on different sites A and B; these updates are then
relationship preservation properties is able to resolves all
preserved by merge in different files after the merge. The
conflicts w.r.t to our principles of ‘meaningfulness’. For-
secondexample(Fig.4b)presentsthecasewhenadirectory
mally, merge embeds the structure of each diverged replica
foo is concurrently renamed to bar and qux on A and B,
into the final common state, which means merge preserves
respectively;thenewnamesarepreservedbymappingthem
all updates and presents no side-effect. Indeed, each of the
withdifferentcopiesofthedirectoryinode.
file system replicas could be represented as a poset, and
The merge function with element preservation has the
merging them would (1) preserve the partial order between
sameeffecttotheWrite-Winsstrategy,whichprefersmod-
elements(Equation5)and(2)mapinputandoutputelements
ifications to other concurrent deletions, in merging concur-
inaone-to-onerelationship(Equation3,4);thesespecifica-
rent updates to the same element. It preserves an updated
tions are well-known in the field of order theory with the
replicaintheoutputwhenmergingthisreplicawithanother
names of order-preserving and order-reflecting, which are
deletedreplicaofthesameelement.Theremainingproblem
therequirementstoembedaposetintoanother.
istochooseapresentationforthissolutionthatdoesnotsur-
An example of using merge with relationship preserva-
prise users with strange merging results, such as a locally
tion is shown in (Fig. 4c). In this example, users on A and
deletedfilereappearingafterthemerge.
B concurrentlycreatenewhardlinksbarandqux,respec-
Themergefunctionwithelementpreservationfullypre-
tively,tofilefooofinodei andconcurrentlywrite(cid:3)and(cid:52)
servesallofthestatesofconcurrentlyupdatedreplicasafter 0
toit.Beforethemerge,usersonAwouldsee{foo→i ((cid:3)),
merging them, as opposed to the Last-Writer-Wins (LWW) 0
bar → i ((cid:3))}, while users on B see {foo → i ((cid:52)),
approach when LWW only preserves one of these states 0 0
qux → i ((cid:52))}. Then merge would preserve these struc-
based on their timestamps. As a result, users of systems 0
tures when merging in {foo.A → i ((cid:3)), bar → i ((cid:3))}
using the LWW approach may see their updates to a file, 1 1
and{foo.B→i ((cid:52)),qux→i ((cid:52))},respectively.
whichhavebeenlocallycommitted,becomeunavailableaf- 2 2
termerging.
4.3 MergingSemantics
4.2.2 RelationshipPreservation
The merging semantics implement our conflict resolution
The mapping and the hierarchy relations between updated principlesindetailpolicies.Inthissection,wedescribethese
vertices of a replica of a file system are preserved when policiesforallconflictcases.
merging a replica with another replica. The merge function
withtherelationshippreservationpropertyisformalizedby:
4.3.1 MergingDirectConflicts
(cid:40)
r →r ⇒merge(r ,r∗)→merge(r ,r∗) State conflict A state conflict happens when an element
v i v v i i (5)
r /r ⇒merge(r ,r∗)/merge(r ,r∗) is updated and deleted concurrently on different sites. The
x y x x y y
mergingpolicyforthiskindofconflictdependsonwhether
where r and r are replicas on the same site of a vertex v theelementisadirectoryorafile.
v i
and an inode i, r and r are replicas on the same site of The element is a file: In this case, the updated replica
x y
differentverticesxandyofthefilesystem. is preserved with the name of the original file. This policy
Thisrelationshippreservationpropertyenablesmergeto preserves the view of the users on the updated site where
present a familiar file system structure, which includes the they see the same namespace and content after the merge.
namespace and the vertex-inode mappings, to users on dif- Users on the site where the file is deleted, however, would
ferentsitesbeforeandafterthemerge.Thismeans,ifusers seethedeletedfiletoappearagainwithnewcontent.

(init)
1 (a) (b) (init 2 ) (c) A foo foo.A
root
foo foo foo root root root root root i i
0 1
bar bar
foo
file file file.B file.A bar qux qux bar foo root merged
file
foo foo.B
i i i i i i i’ i’’ i root i i
i 0 0 1 2 0 0 2
0 A B merged A B merged B qux qux
Figure 4. Examples of merging diverged replicas of a file system using the merge function with the element preservation
propertyandmergewiththerelationshippreservationproperty.Thediagramsinit andinit showthecommoninitalstates
1 2
ofthefilesystemsinexamplesaandbandinexamplecrespectively;AandB denotesthenamesofdifferentsites;merged
isthefinalcommonstateofthefilesystemaftermergingthedivergedreplicas;thindashed-linesareforthecorrespondences
betweentheinputandoutputelementsofthemergefunction;boldshapesareupdatedelementswithsquaresandtrianglesas
differentupdatedcontents.
The element is a directory: In this case, the directory is By doing so, multiple users can use multiple virtual ma-
preserved with the same names in the merging result. The chines of the same file without making multiple copies of
ideabehindthisdecisionisthesametothatoftheprevious thefile.
policy.However,thecontentofthemergedelementdepends
onmergingitssub-directoriesandsub-files. Naming conflict This is the situation where different di-
rectoriesand/orfileswiththesamenameexist.
Dataconflict Adataconflicthappenswhenthecontentof
FileswiththesamenameWechangethenamestodistin-
guish different files (Fig. 5a). New names are generated by
afilehasbeenupdatedonmultiplesites.Wehavedifferent
addingsuffixestotheorginalnameasindataconflictpolicy.
policies to resolve this kind of conflict, they are (1) make
File and directory with the same name We only change
newfiles(2)mergethecontentsand(3)branchthestates.
the name of the file (Fig. 5d). This decision is to preserve
The first policy (Fig. 5a) is to make new files to store
the namespaces as much as possible, i.e., the paths of the
the updated contents. The names of these files are chosen
sub-elementsofthedirectorywouldnothavetochange.
so that users on each site would be able to know that there
Directories with the same name They are merged to be-
is a conflict and to identify which file has their updates. A
come a single directory (Fig. 5e); sub-element collections
traditional approach is to append to the original names a
aremergedtogetherinwhichthiscouldberecursivelybro-
specific identifier such as the unique identifier of the site
kendownintoothernamingconflictcases.
of each replica. Industrial cloud storage services choose to
use more information-rich solutions for generating the new
Mappingconflict Thiscasehappenswhenusersondiffer-
file name, for example, to append “conflicted copy” to the
entsitesrenameasamedirectorytodifferentnames,which
originalfilenameaswithDropbox.
violates the one-to-one property of directory vertex-inode
Thenextpolicy(Fig.5b)istomergethecontentstogether
mappingrelationship.Weresolvethisconflictbyrecursively
in the same file. This policy is used by the merge function
makecopiesofthedirectoryanditssub-elementstopreserve
whenthefileisofadata-typethatisknowntobemergeable.
thenamespaces,whichincludedifferentnamesofthedirec-
An example of such data-type could be text-based as some
tory(Fig.5f).
recentstudies[Preguic¸aetal.2009;Weissetal.2009]have
We decide, however, not to make copies of the sub-files
shownthat,mergingconcurrentupdatestothecontentoftext
but to link new names to these files (Fig. 5g). The decision
filesisfeasible.
isnotonlytosavestoragebutalsotodealwiththesituation
In the last policy (Fig. 5c), diverged states are stored in
whereafilehasmultiplenames;copyingthefilewouldcause
different content branches of the file. Users or administra-
thesenamestobereplicatedandmappedtodifferentcopies,
torscanmergethebranchesmanually.Thispatternisexten-
whichmakesmergingmorecomplex.
sively used in version control systems, such as Git [Git] or
SVN [Apache b], to manage collaborative software devel-
4.3.2 MergingIndirectConflicts
opments.Anotherusageofthissolutionisinthecontentof
virtual machine. A virtual machine is represented as a file; Eachoftheindirectconflictsisacombinationofsomedirect
concurrentusesofavirtualmachinewouldleadtodiverged conflicts.Inthissection,wegothroughthepreviouslymen-
replicas of the file, which are then merged and represented tionedexamplesandshowhowtodealwiththeseproblems
by different branches using the Copy-On-Write technique. byusingthepoliciesfordirectcases.

Merging Policies For Direct Conflicts Merging Policies For Indirect Conflicts
| file |      | file |        |        |     | foo | foo (e) | foo     |      |      |     |      |      |      |
| ---- | ---- | ---- | ------ | ------ | --- | --- | ------- | ------- | ---- | ---- | --- | ---- | ---- | ---- |
|      |      |      | file.A | file.B |     |     |         |         | root | root |     | root | root | root |
|      | A    | B    |        | (a)    |     | A   | B       |         |      |      |     |      |      |      |
|      | i    | i    | i ’    | i ”    |     | bar | bar     | bar     |      |      | (h) |      | bar  | foo  |
|      | 4    | 4    | 4      | 4      |     |     |         |         | foo  | foo  |     | foo  |      |      |
|      |      |      |        |        |     |     |         |         |      |      |     |      | foo  | bar  |
|      |      |      |        |        |     | foo | bar (f) | foo bar | file | file |     | file |      |      |
|      | file |      | file   | (c)    |     |     |         |         |      |      |     |      |      |      |
|      |      |      |        |        |     |     |         |         |      |      |     |      | A    | B    |
|      |      |      |        |        |     | A   | B       |         |      |      |     |      |      |      |
| (b)  | i    |      |        | i      |     | i   | i       | i ’ i ” | i    |      | i   | i    | (i)  | root |
|      | 4    |      |        | 4      |     | 4   | 4       | 4 4     | 4    |      | 4   | 4    |      |      |
|      |      |      |        |        |     |     |         |         | A    | B    |     |      |      |      |
| foo  |      | foo  | (d)    | foo    |     |     |         |         |      |      |     |      |      |      |
|      |      |      |        |        |     | foo | bar (g) | foo bar |      |      |     |      | foo  | bar  |
|      | A    | B    |        |        |     |     |         |         |      |      |     |      |      |      |
|      |      |      |        |        |     | A   | B       |         |      |      |     |      |      |      |
| bar  |      | bar  |        | bar    |     | i   | i       | i       |      |      |     |      | bar  | foo  |
bar.A
|     |     |     |     |     |     | 4   | 4   | 4   |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
Figure5. Mergingpoliciesforconflicts.Weonlydisplaythefinaloutcomeoneachsite.
Deleting while editing In this example (Fig. 5h), a file is The merge function computes the Least Upper Bound
(LUB)ofthesereplicasw.r.t≤.TheLUBofapairofstates
| modified | on  | a site | while | a directory |     | on its path | is deleted |     |     |     |     |     |     |     |
| -------- | --- | ------ | ----- | ----------- | --- | ----------- | ---------- | --- | --- | --- | --- | --- | --- | --- |
on another site. Because each of the elements in the path s ands underthepartialorder≤isdefinedas
i j
| of an | updated | element | is  | also | regarded | as being | updated, |     |     |     |     |     |     |     |
| ----- | ------- | ------- | --- | ---- | -------- | -------- | -------- | --- | --- | --- | --- | --- | --- | --- |
(cid:40)
|     |     |     |     |     |     |     |     |     |     | s   | ≤s,s | ≤s  |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---- | --- | --- | --- |
the problem becomes merging pairs of replicas that are in s=LUB(s ,s ): i j . (6)
i j
state conflict of elements in the path. By simply applying (cid:64)s(cid:48) ≤s:s ≤s(cid:48),s ≤s(cid:48)
|     |     |     |     |     |     |     |     |     |     |     |     | i   | j   |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
thepolicyforstateconflictfortheseelements,thepathofthe
Bydefinition,theLUBfunctionisidempotent,commuta-
updatedfileandthefileitselfarepreservedafterthemerge.
tive,andassociative:
| Making | cycles | in  | the namespace |     | This | example | (Fig. 5i) |     |     |     |     |     |     |     |
| ------ | ------ | --- | ------------- | --- | ---- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- |

|     |     |     |     |     |     |     |     | idempotent |     | LUB(s,s)=s |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | ---------- | --- | ---------- | --- | --- | --- | --- |
c a n b e v ie w e d a s a se t o f o t h e r d ir e c t m a p p i n g c o n fl i c t commutative
p ro bl e m s. B e c au s et he d ire c to r y , t o w h i c h an o t h e r di r ec t o r y LUB(s ,s )=LUB(s ,s )
|     |     |     |     |     |     |     |     |     |     |     | i   | j   | j   | i . (7) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ------- |
m o ve d , is c o n si d e re d u p d at e d ,t he d i r e cto r i e s o n it s p a t h a r e  associative LUB (s i , L U B (s j ,s k )) =
als o v ie w e d a s u p d a te d . W h e n m er g i n g w i t h o t he r r e n a m e d L U B ( L UB ( s , s ), s )
|           |       |         |         |         |           |                 |              |                |     |          |         |      | i j     | k        |
| --------- | ----- | ------- | ------- | ------- | --------- | --------------- | ------------ | -------------- | --- | -------- | ------- | ---- | ------- | -------- |
| replicas, | there | would   | be      | mapping | conflicts | on              | these direc- |                |     |          |         |      |         |          |
|           |       |         |         |         |           |                 |              | The idempotent |     | property | implies | that | merging | the same |
| tories    | (foo  | and bar | in this | case).  | We        | can recursively | solve        |                |     |          |         |      |         |          |
these conflicts on foo and bar using the mapping conflict replicamultipletimesdoesnotchangetheresult.Thisprop-
|     |     |     |     |     |     |     |     | erty can | deal with | the unreliable |     | networks | where | updates |
| --- | --- | --- | --- | --- | --- | --- | --- | -------- | --------- | -------------- | --- | -------- | ----- | ------- |
policy.Differentrelationshipswithdifferentparentsofadi-
couldbedeliveredmultipletimes.Thecommutativeproperty
rectoryarepreservedinitscorrespondingcopies,whichare
different,afterthemergesothatcyclesarenotformed. alsoworkswellwiththesenetworks;itenablesthefunction
mergetohandleupdatesinanyorder.Thelastpropertyasso-
ciativeensuresmerginganynumberofreplicasinanyorder
5. ImplementationFramework
wouldstillmakethemconsistent.Thisisanimportantprop-
Inthissection,wedescribethespecificationsofaframework
ertyforCRDTtoworkwithanarbitrarynumberofsites.
forimplementingourmergingsemanticstosupportworking
with an arbitrary number of replicas. This framework is 5.2 ImplementationExample
specified based on CRDT [Shapiro et al. 2011a,b], which The implementation of a system should follow the specifi-
isaspecificationforeventualconsistencydatatypes.
|     |     |     |     |     |     |     |     | cations of | CRDT, | which | means, | to make | the merging | poli- |
| --- | --- | --- | --- | --- | --- | --- | --- | ---------- | ----- | ----- | ------ | ------- | ----------- | ----- |
ciesbecomeidempotent,commutative,andassociativew.r.t
5.1 CRDT
|     |     |     |     |     |     |     |     | the implementation’s |     | definition |     | of ≤ the | partial | order. Dis- |
| --- | --- | --- | --- | --- | --- | --- | --- | -------------------- | --- | ---------- | --- | -------- | ------- | ----------- |
CRDT,whichstandsforConflictFreeReplicatedDataType,
cussingthedetailofourimplementationisoutofthescope
| is a | set of | specifications |     | for data | types | to support | eventual | ofthispaper. |     |     |     |     |     |     |
| ---- | ------ | -------------- | --- | -------- | ----- | ---------- | -------- | ------------ | --- | --- | --- | --- | --- | --- |
consistency.Wefocusonstate-basedCRDTinthisworkand
|     |     |     |     |     |     |     |     | Nevertheless, |     | we describe |     | an example | of  | making the |
| --- | --- | --- | --- | --- | --- | --- | --- | ------------- | --- | ----------- | --- | ---------- | --- | ---------- |
summarizetherequirementsofstate-basedCRDTasfollow.
|     |     |     |     |     |     |     |     | merging | policy for | data | conflict | to be | CRDT | in this sec- |
| --- | --- | --- | --- | --- | --- | --- | --- | ------- | ---------- | ---- | -------- | ----- | ---- | ------------ |
The state of each replica advances upward after modifica- tion. The setup for this example include three sites A, B,
tions w.r.t a partial order ≤. Formally, if s i and s j are the and C with a common file foo of inode i. Users on these
statesofareplicabeforeandafteranupdate,thens ≤s . sitesconcurrentlywritetofoo.
|     |     |     |     |     |     |     | i j |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Wedefinethepartialorder≤basedonthetimestampsof
|     |     |     |     |     |     |     |     | Table 1. | Evaluation | of  | our merging | semantics | with | com- |
| --- | --- | --- | --- | --- | --- | --- | --- | -------- | ---------- | --- | ----------- | --------- | ---- | ---- |
thestates,i.e.,ifstandst+1arethestatesoffoobeforeand mercial systems. Abbreviations: Db for Dropbox, GD for
| afteranupdateoramerge,thenst |     |     |     |     | ≤ st+1.Weuseversion |     |     |     |     |     |     |     |     |     |
| ---------------------------- | --- | --- | --- | --- | ------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
GoogleDrive,andODforMicrosoftOneDrive.
| vector, | which | is a frequently |     | used | technique, | for assigning |     |     |     |     |     |     |     |     |
| ------- | ----- | --------------- | --- | ---- | ---------- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- |
timestampsinoursystem.
|         |     |         |        |     |            |             |     | Feature/Support |     |     | Db GD    | OD  | GeoFS    |          |
| ------- | --- | ------- | ------ | --- | ---------- | ----------- | --- | --------------- | --- | --- | -------- | --- | -------- | -------- |
| We make | our | merging | policy |     | idempotent | by ignoring |     |                 |     |     |          |     |          |          |
|         |     |         |        |     |            |             |     | PreserveUpdates |     |     | (cid:88) | ×   | (cid:88) | (cid:88) |
updates with less recent timestamps. For example, when A PreserveStructure × × × (cid:88)
| merges | the update | with | timestamp |     | v t from | B, it increases |     |          |     |     |     |     |     |          |
| ------ | ---------- | ---- | --------- | --- | -------- | --------------- | --- | -------- | --- | --- | --- | --- | --- | -------- |
|        |            |      |           |     | B        |                 |     | Hardlink |     |     | ×   | ×   | ×   | (cid:88) |
|        |            |      |           | t   | t+1      |                 |     |          |     |     |     |     |     |          |
thetimestampoffoofromv tov afterthemerge,such Samenamedir./files (cid:88) dvg.a (cid:88) (cid:88)
|     |     |     |     | A A |     |     |     |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
thatv t ≤v t+1 andv t ≤v t+1.WhenAreceivesv t again, (cid:88) lwwb (cid:88) (cid:88)
| A                         | A   |     | B A |                  |     | B   |     | Write||Write       |     |     |                |     |          |          |
| ------------------------- | --- | --- | --- | ---------------- | --- | --- | --- | ------------------ | --- | --- | -------------- | --- | -------- | -------- |
| AwouldignoreitbecauseA’sv |     |     |     | t+1ismorerecent. |     |     |     |                    |     |     |                |     |          |          |
|                           |     |     |     | A                |     |     |     | DirectDelete||Edit |     |     | (cid:88) d.w.c |     | (cid:88) | (cid:88) |
We make our merging policy commutative in the sense IndirectDelete||Edit (cid:88) (cid:88) (cid:88)
d.w.
thatapairwisemergeofapairofreplicasoneithersitemust (cid:88) (cid:88)
|         |          |          |     |         |          |         |        | Cycles |     |     | arb.d |     | ×   |     |
| ------- | -------- | -------- | --- | ------- | -------- | ------- | ------ | ------ | --- | --- | ----- | --- | --- | --- |
| produce | the same | outcome. |     | In this | example, | merging | repli- |        |     |     |       |     |     |     |
casof foo from A and B mustproduce thesame newfiles aDiverge:elementsarepreserved,butreplicas’structuresdiverged.
| foo.A of | inode | i and | foo.B | of  | inode i | on both | A and |     |     |     |     |     |     |     |
| -------- | ----- | ----- | ----- | --- | ------- | ------- | ----- | --- | --- | --- | --- | --- | --- | --- |
1 2 bLast-Writer-Wins:thewritewiththelasttimestampwinsovertheothers.
B.Webreakdowntheproblemofcommutativityintomak- cDelete-Wins:theelement,ifdeletedonanysite,isdeletedaftermerging.
ingnewnamesandnewinodenumbersdeterministically.We dArbitrary:thedirectoriesinthecyclesareplacedatrootaftermerge.
makenewnamesbyaddingsuffixesthatarethenamesofthe
| site of each | replica; | this | function | is  | deterministic | since | the |     |     |     |     |     |     |     |
| ------------ | -------- | ---- | -------- | --- | ------------- | ----- | --- | --- | --- | --- | --- | --- | --- | --- |
sitenamesarepredefined.Wemakenewinodenumbersby In the following experiments, we determined how well
|     |     |     |     |     |     |     |     | these systems | could | resolve | the | conflict | cases that | we de- |
| --- | --- | --- | --- | --- | --- | --- | --- | ------------- | ----- | ------- | --- | -------- | ---------- | ------ |
hashingthecombinationsoftheoriginalinodenumberand
thesitenames;withthesamehashfunctionf andthesame scribedinSection2.3.Inallofthecases,ourprototypewas
inputs{i,A,B},theoutcomesarethesameinodenumbers able resolve the conflicts and to produce the desired out-
|               |     |          |     |     |     |     |     | comes of | our merging | semantics. |     | For such | result, | we only |
| ------------- | --- | -------- | --- | --- | --- | --- | --- | -------- | ----------- | ---------- | --- | -------- | ------- | ------- |
| i =f(i,A)andi |     | =f(i,B). |     |     |     |     |     |          |             |            |     |          |         |         |
| 1             |     | 2        |     |     |     |     |     |          |             |            |     |          |         |         |
We make our merging policy associative using the ver- describethebehavioursofthecommercialsystemswiththe
experimentsinthefollowings.
sioningsystem.WhenAreceivesthereplicafromB,itcre-
ates {foo.A → i , foo.B → i } and marks foo → i Experiment 1. Support for hard links We created a file foo
|            |     | 1      |      | 2           |      |          |     |     |     |     |     |     |     |     |
| ---------- | --- | ------ | ---- | ----------- | ---- | -------- | --- | --- | --- | --- | --- | --- | --- | --- |
| as deleted | in  | merge. | When | the replica | from | C comes, | A   |     |     |     |     |     |     |     |
anditshardlinkbaronA.Aftermerging,B,inanyofthe
sees the conflict of the replica with its version of foo and setups,hadthesamenamespaceoffooandbar.However,
| makes {foo.A |     | → i | , foo.C | → i | }. When | all other | sites |     |     |     |     |     |     |     |
| ------------ | --- | --- | ------- | --- | ------- | --------- | ----- | --- | --- | --- | --- | --- | --- | --- |
1 3 these files on B pointed to different inodes in all cases,
havedonethesameprocess,theywillhavethesamestateof which means there is a divergence in the structures of the
| {foo.A→i | ,foo.B→i |     | ,foo.C→i |     | },regardlessofthe |     |     | replicas. |     |     |     |     |     |     |
| -------- | -------- | --- | -------- | --- | ----------------- | --- | --- | --------- | --- | --- | --- | --- | --- | --- |
|          | 1        |     | 2        |     | 3                 |     |     |           |     |     |     |     |     |     |
orderofmergingthesereplicas.
Therewerealsosomeanomalousresultweobservedwith
|     |     |     |     |     |     |     |     | Dropbox | in this | experiment. | We  | updated | bar on | A, after |
| --- | --- | --- | --- | --- | --- | --- | --- | ------- | ------- | ----------- | --- | ------- | ------ | -------- |
6. CompareFeatures merging the update to B, we deleted both foo and bar on
B.However,fooappearedagainonallsitesafterthemerge.
| We compare | the         | features | of            | our approach |          | and the | existing |            |          |        |             |      |      |         |
| ---------- | ----------- | -------- | ------------- | ------------ | -------- | ------- | -------- | ---------- | -------- | ------ | ----------- | ---- | ---- | ------- |
|            |             |          |               |              |          |         |          | Experiment | 2. Files | and/or | directories | with | same | name We |
| approaches | represented |          | by commercial |              | systems, | which   | are      |            |          |        |             |      |      |         |
A B.
Dropbox[Dropbox],GoogleDrive[Google],andMicrosoft concurrently created a file foo on both and On each
site,thesesystemscreateddifferentnamesforthesefilesto
OneDrive[Microsoft](Table1).
|     |     |     |     |     |     |     |     | distinguish | them. | However, | the system | using | Google | Drive |
| --- | --- | --- | --- | --- | --- | --- | --- | ----------- | ----- | -------- | ---------- | ----- | ------ | ----- |
Weimplementedaprototype,namedGeoFS,withNodeJS
andFUSE.Weusedprocesses,communicatingoverHTTP, didnotconvergethereplicatothesamestructure,i.e.,foo’s
contentonAwasnotequaltofoo’sonB.
assites.ThehostmachineranUbuntuDesktop14.04LTS.
Theresultswererepeatedwhenwecreatedafileonasite
ThesetupofDropboxwastwovirtualmachinesrunning
andanotherdirectorywiththesamenameontheothersite.
| Ubuntu | Server | 14.04 | LTS with | Dropbox |     | client for | Linux |         |               |     |              |      |      |         |
| ------ | ------ | ----- | -------- | ------- | --- | ---------- | ----- | ------- | ------------- | --- | ------------ | ---- | ---- | ------- |
|        |        |       |          |         |     |            |       | Systems | using Dropbox |     | and OneDrive | were | able | to make |
v.3.0.3asthereplicasAandB.Theyarehostedinthesame
machinewithNetworkAddressTranslationnetworking. theirreplicasconvergewhileGoogleDrivewasnot.
ThesetupforGoogleDriveandMicrosoftOneDrivewas Experiment 3. Concurrent writes to the same file We wrote
a Mac running Mac OS X v.10.10 as site A and a virtual tothesameexistingfileondifferentsite.Thesystemsusing
machine of Windows 8.1 Enterprise as site B. These sites Dropbox and OneDrive resolved the conflict by creating
were in the same local network. The Google Drive clients different files to distinguish these updated versions of the
for Mac and Windows were the same, v.1.18.7821.2489, file.However,thesystemusingGoogleDriveonlyretained
while the Microsoft OneDrive clients on these sites were theupdatefromonesite.Basedonthisresult,webelievethat
v.17.3.4501andv.6.3.9600.17334,respectively. GoogleDriveusestheLast-Writer-Winsapproachtoresolve

the concurrent writes to the same file which, while simple, conflict,whichisthecasewhenarowisconcurrentlydeleted
doesnotpreservealltheupdates. andupdatedondifferentsites,requiresmanualintervention.
Alloftheseaboveconflictcasesforrelationaldatabases
| Experiment | 4.  | Delete while | editing | We did | different | exper- |     |     |     |     |     |     |     |
| ---------- | --- | ------------ | ------- | ------ | --------- | ------ | --- | --- | --- | --- | --- | --- | --- |
iments with the direct and the indirect cases. In the direct are considered the same conflict in NoSQL databases in
case, we deleted a file foo on A while writing to it on B. whichakeyismappedtodifferentvalues.DynamoandRiak
solvethisconflictbyusingeithertheLWWapproachorby
| Except | the system | using | Google | Drive | which deleted | foo |     |     |     |     |     |     |     |
| ------ | ---------- | ----- | ------ | ----- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- |
on both sites after the merge, the other systems preserved keepingthesevaluesasdifferentversionsofthekey.
theupdatedfile.Intheindirectcase,wedeletedthedirectory Merging framework The problem of merging diverged
bar,whosesub-fileswerequxandquz,onA,whileonB, replicasofageneralmodelhasbeendiscussedelsewhere[Pot-
wewrotetoqux.Aftermerging,thesystemsusingDropbox
|     |     |     |     |     |     |     | tinger and | Bernstein | 2003]. | Pottinger | and | Bernstein | pro- |
| --- | --- | --- | --- | --- | --- | --- | ---------- | --------- | ------ | --------- | --- | --------- | ---- |
and OneDrive preserved qux, bar and deleted quz, while posed in this work some merging semantics including ele-
thesystemusingGoogleDrivedeletedbaranditssub-files. ment preservation and relationship preservation, which in-
WeassumedthatGoogleDriveresolvedthisconflictus- spired the formalization of our model. They also presented
ingtheDelete-Winsapproach,i.e.,anelementthathasbeen someresolutionsforconflictupdatesinmergingmodels.For
deletedonanyreplicawouldbedeletedafterthemerge. example,conlictsinthetype-of
relationship,whichisknown
Experiment 5. Cycles in the namespace Initially, A and B tobeone-to-one,aresolvedinthesamewaytooursystem,
|     |     |     |     |     |     |     | in which | new types | are created. | Tree | cycles | are either | col- |
| --- | --- | --- | --- | --- | --- | --- | -------- | --------- | ------------ | ---- | ------ | ---------- | ---- |
startedwiththesamenamespaceofdirectoriesfooandbar.
OnA,wemovedfoointobar,whileonB,wemovedbar lapsed into a single element or need manual intervention.
intofootomakethecycleofthesedirectories.Weexpected Apartfromthedomain,wearedifferentfromthemintheob-
jectivesofmerging,i.e.,wetrytopreservethestructuresof
| the merged | namespace |     | would be | foo/bar | and | bar/foo, |     |     |     |     |     |     |     |
| ---------- | --------- | --- | -------- | ------- | --- | -------- | --- | --- | --- | --- | --- | --- | --- |
which embeds both that of A and B. The system using thereplicasbyusingorder-preservingandorder-reflecting.
Dropboxwasabletomaketheexpectedoutcome,whilethe Version control systems Git [Git] and SVN [Apache b]
Google Drive system put all the directories in the cycle in are the representative examples of distributed and central-
root andthesystemusingOneDrivestayeddiverged.
|     |     |     |     |     |     |     | ized version | control | systems, | which | could | also be | viewed |
| --- | --- | --- | --- | --- | --- | --- | ------------ | ------- | -------- | ----- | ----- | ------- | ------ |
Asaconclussion,whileDropboxandOneDrivecansupport assimplifiedfilesystems.Themainfocusofthesesystems
|     |     |     |     |     |     |     | is on keeping | replicas | of  | files of different |     | projects | consis- |
| --- | --- | --- | --- | --- | --- | --- | ------------- | -------- | --- | ------------------ | --- | -------- | ------- |
preservingupdatedelements,theydonotpreservethestruc-
tureofthefilesystemwhenmergingdivergedreplicas. tent by keeping the namespaces synchronized. There could
bedifferentversionsofthefilesordifferentversionsofthe
7. RelatedWork
wholeprojectsatthesametimeinthesystemwiththename
|          |         |     |         |              |          |     | ‘branches’. | These | systems | rely totally | on manual |     | interven- |
| -------- | ------- | --- | ------- | ------------ | -------- | --- | ----------- | ----- | ------- | ------------ | --------- | --- | --------- |
| Database | systems | The | problem | of resolving | conflict | up- |             |       |         |              |           |     |           |
tionfromuserstosolvetheconflictupdates.Inversioncon-
| dates has | also    | been studied | in the       | field of | database     | system. |               |            |           |        |             |        |          |
| --------- | ------- | ------------ | ------------ | -------- | ------------ | ------- | ------------- | ---------- | --------- | ------ | ----------- | ------ | -------- |
|           |         |              |              |          |              |         | trol systems, | the        | operation | rename | is regarded | as     | a combi- |
| Databases | usually | model        | their system | as       | a collection | of ta-  |               |            |           |        |             |        |          |
|           |         |              |              |          |              |         | nation of     | delete and | create.   | These  | systems     | do not | have the |
bleswithrowsasintraditionalrelationaldatabasesorsimply
asakey-valuestoreaswithmodernNoSQLdatabases.Op- supportforhardlinks.
erationsonthesesystemsareeitherinsert,update,ordelete
8. ConclusionsandFutureWork
| at the row | or  | key level. | Conflict cases | in  | these systems | are |     |     |     |     |     |     |     |
| ---------- | --- | ---------- | -------------- | --- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- |
therefore mostly situations where a row or a key is concur- In this paper, we presented our theory and implementation
rently created or updated; these cases are very limited in of merging diverged replicas of a geo-distributed file sys-
numberascomparedtothoseinfilesystems.Becauseofthe tem. We proposed our merging semantics which were im-
space constrain, we only present the conflict resolution in plemented in detail by merging policies that work with all
Oracle [Oracle], which is a representative example of rela- components and all operations on the file systems. These
tional databases, and Dynamo [DeCandia et al. 2007] and policiescanhandleboththedirectandtheindirectconflicts,
Riak[Basho],whichareexamplesofNoSQLdatabases. in whichthe latter isknown to bethe limitationof existing
Thereareupdateconflict,uniquenessconflict,anddelete approaches. Finally, we described our specifications for an
conflict in Oracle. An update conflict happens when a row implementationframeworkthatensurestheeventualconsis-
is concurrent updated by users on different sites. Oracle tencyandsomedetailimplementationtechniquestoachieve
resolvesthisconflictbyusingeithertheLWWapproachor the desired merging properties. To the best of our knowl-
some additional mechanisms that produce a deterministic edge,themodelofindirectconflictsandthemergingseman-
outcome for the concurrent updates, such as additive that ticsforthemarenovelinthedistributedfilesystemfield.
aggregates the update values. A uniqueness conflict, which Wealsotargetsomecomplementfeaturesinfuturework.
happenswhendifferentrowswiththesameprimarykeyare They are a session system that reduces the complexity of
concurrentlycreatedondifferentsites,isresolvedbyadding the eventual consistency approach for users and developers
some sequence, such as site identifier or a number, to the andastudyofthefeasibilityonapplyingthesesemanticsin
value of the primary keys to make them unique. A delete moderndistributedfilesystemssuchasHDFS[Apachea].

References
|     |     |     |     |     |     |     | Microsoft. | Microsoft | OneDrive. |     | https://onedrive.live. |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------- | --------- | --------- | --- | ---------------------- | --- | --- |
com/. Accessed:2014-12-31.
| Apache. HDFSUserGuide,v.2.6.0. |     |     | https://hadoop.apache. |     |     |     |     |     |     |     |     |     |     |
| ------------------------------ | --- | --- | ---------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
org/docs/stable/hadoop-project-dist/hadoop-hdfs/ Oracle. Oracle Database Advanced Replication, 12c Release 1
HdfsUserGuide.html,a. Accessed:2015-04-27. (12.1). http://docs.oracle.com/database/121/REPLN/
|                     |     |        |                            |     |     |     | E53117-02.pdf. |     | Accessed:2015-04-27. |     |     |     |     |
| ------------------- | --- | ------ | -------------------------- | --- | --- | --- | -------------- | --- | -------------------- | --- | --- | --- | --- |
| Apache. Subversion, |     | v.1.7. | https://subversion.apache. |     |     |     |                |     |                      |     |     |     |     |
org/,b. Accessed:2014-12-31. Douglas Stott Parker Jr, Gerald J. Popek, Gerard Rudisin, Allen
Stoughton,BruceJ.Walker,EvelynWalton,JohannaM.Chow,
| SundarBalasubramaniamandBenjaminC.Pierce. |                                        |     |     |     | Whatisafile |     |                                            |     |     |     |     |     |             |
| ----------------------------------------- | -------------------------------------- | --- | --- | --- | ----------- | --- | ------------------------------------------ | --- | --- | --- | --- | --- | ----------- |
|                                           |                                        |     |     |     |             |     | DavidEdwards,StephenKiser,andCharlesKline. |     |     |     |     |     | Detectionof |
| synchronizer?                             | InProceedingsofthe4thAnnualACM/IEEEIn- |     |     |     |             |     |                                            |     |     |     |     |     |             |
ternationalConferenceonMobileComputingandNetworking, mutualinconsistencyindistributedsystems. IEEETransactions
onSoftwareEngineering,(3):240–247,1983.
MobiCom’98,pages98–108.ACM,1998.
|                                        |             |     |                             |     |     |           | GeraldPopekandBruceJ.Walker. |                |     | TheLOCUSdistributedsystem |     |     |     |
| -------------------------------------- | ----------- | --- | --------------------------- | --- | --- | --------- | ---------------------------- | -------------- | --- | ------------------------- | --- | --- | --- |
| Basho. Conflict                        | Resolution. |     | http://docs.basho.com/riak/ |     |     |           |                              |                |     |                           |     |     |     |
|                                        |             |     |                             |     |     |           | architecture.                | MITpress,1985. |     |                           |     |     |     |
| latest/dev/using/conflict-resolution/. |             |     |                             |     |     | Accessed: |                              |                |     |                           |     |     |     |
2015-04-27. Rachel A. Pottinger and Philip A. Bernstein. Merging models
|                  |        |     |          |       |          |           | based | on given correspondences. |     |     | In Proceedings |     | of the 29th |
| ---------------- | ------ | --- | -------- | ----- | -------- | --------- | ----- | ------------------------- | --- | --- | -------------- | --- | ----------- |
| Nikolaj Bjørner. | Models | and | software | model | checking | of a dis- |       |                           |     |     |                |     |             |
internationalconferenceonVerylargedatabases,VLDB’03,
| tributed file | replication | system. | Formal | Methods |     | and Hybrid |     |     |     |     |     |     |     |
| ------------- | ----------- | ------- | ------ | ------- | --- | ---------- | --- | --- | --- | --- | --- | --- | --- |
pages862–873.VLDBEndowment,2003.
Real-timeSystems,pages1–23,2007.
|                 |         |        |             |     |         |             | Nuno Preguic¸a, | Joan | Manuel | Marques, | Marc | Shapiro, | and Mihai |
| --------------- | ------- | ------ | ----------- | --- | ------- | ----------- | --------------- | ---- | ------ | -------- | ---- | -------- | --------- |
| Eric A. Brewer. | Towards | robust | distributed |     | systems | (abstract). |                 |      |        |          |      |          |           |
Letia. Acommutativereplicateddatatypeforcooperativeedit-
| In Proceedings | of  | the Nineteenth | Annual | ACM | Symposium | on  |     |     |     |     |     |     |     |
| -------------- | --- | -------------- | ------ | --- | --------- | --- | --- | --- | --- | --- | --- | --- | --- |
ing. InProceedingsofthe200929thIEEEInternationalCon-
PrinciplesofDistributedComputing,PODC’00,pages7–,New
|     |     |     |     |     |     |     | ference | on Distributed | Computing |     | Systems, | ICDCS | ’09, pages |
| --- | --- | --- | --- | --- | --- | --- | ------- | -------------- | --------- | --- | -------- | ----- | ---------- |
York,NY,USA,2000.ACM.
395–403.IEEEComputerSociety,2009.
GiuseppeDeCandia,DenizHastorun,MadanJampani,Gunavard-
|     |     |     |     |     |     |     | NormanRamsey,ElCsirmaz,etal. |     |     | Analgebraicapproachtofile |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | ---------------------------- | --- | --- | ------------------------- | --- | --- | --- |
hanKakulapati,AvinashLakshman,AlexPilchin,Swaminathan
|                                                |     |     |     |     |     |         | synchronization. | ACMSIGSOFTSoftwareEngineeringNotes, |     |     |     |     |     |
| ---------------------------------------------- | --- | --- | --- | --- | --- | ------- | ---------------- | ----------------------------------- | --- | --- | --- | --- | --- |
| Sivasubramanian,PeterVosshall,andWernerVogels. |     |     |     |     |     | Dynamo: |                  |                                     |     |     |     |     |     |
26(5):175–185,2001.
Proceedings
| Amazon’s | Highly Available |     | Key-value | Store. | In  |     |               |       |             |        |          |       |         |
| -------- | ---------------- | --- | --------- | ------ | --- | --- | ------------- | ----- | ----------- | ------ | -------- | ----- | ------- |
|          |                  |     |           |        |     |     | David Ratner, | Peter | Reiher, and | Gerald | J Popek. | Roam: | A scal- |
ofTwenty-firstACMSIGOPSSymposiumonOperatingSystems
Principles, SOSP ’07, pages 205–220, New York, NY, USA, ablereplicationsystemformobilecomputing. InDatabaseand
2007.ACM. ExpertSystemsApplications,1999.Proceedings.TenthInterna-
tionalWorkshopon,pages96–104.IEEE,1999.
| Dropbox. Dropbox. |     | https://www.dropbox.com/. |     |     |     | Accessed: |     |     |     |     |     |     |     |
| ----------------- | --- | ------------------------- | --- | --- | --- | --------- | --- | --- | --- | --- | --- | --- | --- |
2014-12-31. DavidRatner,PeterReiher,andGeraldJPopek. Roam:ascalable
|                           |     |     |                              |     |     |     | replicationsystemformobility. |     |     | MobileNetworksandApplica- |     |     |     |
| ------------------------- | --- | --- | ---------------------------- | --- | --- | --- | ----------------------------- | --- | --- | ------------------------- | --- | --- | --- |
| SethGilbertandNancyLynch. |     |     | Brewer’sconjectureandthefea- |     |     |     |                               |     |     |                           |     |     |     |
tions,9(5):537–544,2004.
sibilityofconsistent,available,partition-tolerantwebservices.
PeterLReiher,JohnSHeidemann,DavidRatner,GregorySkinner,
SIGACTNews,33(2):51–59,June2002.
|                                |     |     |                      |     |     |     | and Gerald | J Popek. | Resolving | file | conflicts | in the | Ficus file |
| ------------------------------ | --- | --- | -------------------- | --- | --- | --- | ---------- | -------- | --------- | ---- | --------- | ------ | ---------- |
| Git. Git. http://git-scm.com/. |     |     | Accessed:2014-12-31. |     |     |     |            |          |           |      |           |        |            |
system. UCLAComputerScienceDepartment,1994.
| Google. Google | Drive. | https://www.google.com/drive/. |     |     |     |     |         |                 |       |     |             |        |        |
| -------------- | ------ | ------------------------------ | --- | --- | --- | --- | ------- | --------------- | ----- | --- | ----------- | ------ | ------ |
|                |        |                                |     |     |     |     | Mahadev | Satyanarayanan, | James |     | J. Kistler, | Puneet | Kumar, |
Accessed:2014-12-31.
|     |     |     |     |     |     |     | MariaE.Okasaki,EllenH.Siegel,andDavidC.Steere. |     |     |     |     |     | Coda: |
| --- | --- | --- | --- | --- | --- | --- | ---------------------------------------------- | --- | --- | --- | --- | --- | ----- |
RichardGuy,PeterReiher,D.Rather,MichialGunter,WilkieMa, Ahighlyavailablefilesystemforadistributedworkstationen-
and Gerald Popek. Rumor: Mobile data access through opti- vironment. IEEE Transactions on Computers, 39(4):447–459,
| misticpeer-to-peerreplication. |     |     | Lecturenotesincomputersci- |     |     |     | 1990. |     |     |     |     |     |     |
| ------------------------------ | --- | --- | -------------------------- | --- | --- | --- | ----- | --- | --- | --- | --- | --- | --- |
ence,pages254–265,1999.
|     |     |     |     |     |     |     | Marc Segura, | Vianney | Rancurel, | Vinh | Tao, | and Marc | Shapiro. |
| --- | --- | --- | --- | --- | --- | --- | ------------ | ------- | --------- | ---- | ---- | -------- | -------- |
John H. Howard. On overview of the andrew file system. In Scality’sexperiencewithageo-distributedfilesystem. InPro-
ProceedingsoftheUSENIXWinterConference.Dallas,Texas,
ceedingsofthePosters&DemosSession,MiddlewarePosters
USA,January1988,pages23–26,1988. andDemos’14,pages31–32,NewYork,NY,USA,2014.ACM.
Michael L. Kazar. Synchronization and caching issues in the Marc Shapiro, Nuno Preguic¸a, Carlos Baquero, and Marek Za-
andrew file system. In Proceedings of the USENIX Winter wirski. Conflict-free replicated data types. In Stabilization,
Conference. Dallas, Texas, USA, January 1988, pages 27–36, Safety, and Security of Distributed Systems, pages 386–400.
| 1988. |     |     |     |     |     |     | Springer,2011a. |     |     |     |     |     |     |
| ----- | --- | --- | --- | --- | --- | --- | --------------- | --- | --- | --- | --- | --- | --- |
Anne-MarieKermarrec,AntonyRowstron,MarcShapiro,andPe- Marc Shapiro, Nuno Preguic¸a, Carlos Baquero, Marek Zawirski,
terDruschel. Theicecubeapproachtothereconciliationofdi- et al. A comprehensive study of convergent and commutative
vergentreplicas. InProceedingsoftheTwentiethAnnualACM replicateddatatypes. 2011b.
SymposiumonPrinciplesofDistributedComputing,PODC’01,
D.B.Terry,M.M.Theimer,KarinPetersen,A.J.Demers,M.J.
pages210–218.ACM,2001.
Spreitzer,andC.H.Hauser.Managingupdateconflictsinbayou,
JamesJ.KistlerandM.Satyanarayanan. Disconnectedoperation aweaklyconnectedreplicatedstoragesystem.InProceedingsof
intheCodafilesystem. ACMTrans.Comput.Syst.,10(1):3–25, theFifteenthACMSymposiumonOperatingSystemsPrinciples,
| February1992. |     |     |     |     |     |     | SOSP’95,pages172–182,NewYork,NY,USA,1995.ACM. |     |     |     |     |     |     |
| ------------- | --- | --- | --- | --- | --- | --- | --------------------------------------------- | --- | --- | --- | --- | --- | --- |

WernerVogels.Eventuallyconsistent.Queue,6(6):14–19,October
2008.
| WernerVogels. | Eventuallyconsistent. | Commun.ACM,52(1):40– |     |     |
| ------------- | --------------------- | -------------------- | --- | --- |
44,January2009.
| Bruce Walker, | Gerald Popek, | Robert English,       | Charles | Kline, and |
| ------------- | ------------- | --------------------- | ------- | ---------- |
| Greg Thiel.   | The locus     | distributed operating | system. | ACM        |
SIGOPSOperatingSystemsReview,17(5):49–70,1983.
Ste´phaneWeiss,PascalUrso,andPascalMolli.Logoot:Ascalable
optimisticreplicationalgorithmforcollaborativeeditingonp2p
| networks. | InDistributedComputingSystems,2009.ICDCS’09. |     |     |     |
| --------- | -------------------------------------------- | --- | --- | --- |
29thIEEEInternationalConferenceon,pages404–412.IEEE,
2009.
