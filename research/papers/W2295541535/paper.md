Optimistic Replication
Yasushi Saito, Marc Shapiro
To cite this version:
Yasushi Saito, Marc Shapiro. Optimistic Replication. ACM Computing Surveys, 2005, 37 (1), pp.42–81.
⟨10.1145/1057977.1057980⟩. ⟨hal-01248208⟩
HAL Id: hal-01248208
https://inria.hal.science/hal-01248208v1
Submittedon24Dec2015
HAL is a multi-disciplinary open access archive L’archiveouvertepluridisciplinaireHAL,estdes-
for the deposit and dissemination of scientific re- tinée au dépôt et à la diffusion de documents scien-
searchdocuments,whethertheyarepublishedornot. tifiquesdeniveaurecherche,publiésounon,émanant
Thedocumentsmaycomefromteachingandresearch des établissements d’enseignement et de recherche
institutionsinFranceorabroad,orfrompublicorpri- français ou étrangers, des laboratoires publics ou
vateresearchcenters. privés.
HALAuthorization

Optimistic Replication
YASUSHISAITO
Hewlett-PackardLaboratories,PaloAlto,CA,USA
and
MARCSHAPIRO
MicrosoftResearchLtd.,Cambridge,UK
Data replication is a key technology in distributed systems that enables higher availability and performance.
Thispapersurveysoptimisticreplicationalgorithms. Theyallowreplicacontentstodivergeintheshorttermto
supportconcurrentworkpracticesandtoleratefailuresinlow-qualitycommunicationlinks. Theimportanceof
suchtechniquesisincreasingascollaborationthroughwide-areaandmobilenetworksbecomespopular.
Optimisticreplicationdeploysalgorithmsnotseenintraditional“pessimistic”systems.Insteadofsynchronous
replicacoordination,anoptimisticalgorithmpropagateschangesinthebackground,discoversconflictsafterthey
happenandreachesagreementonthefinalcontentsincrementally.
Weexplorethesolutionspaceforoptimisticreplicationalgorithms. Thispaperidentifieskeychallengesfac-
ingoptimisticreplicationsystems—orderingoperations,detectingandresolvingconflicts,propagatingchanges
efficiently,andboundingreplicadivergence—andprovidesacomprehensivesurveyoftechniquesdevelopedfor
addressingthesechallenges.
CategoriesandSubjectDescriptors:C.2.4[Computer-CommunicationNetworks]:DistributedSystems—Dis-
tributedapplications;H.3.4[InformationStorageandRetrieval]:SystemsandSoftware—Distributedsystems
GeneralTerms:Algorithms,Management,Reliability,Performance
AdditionalKeyWordsandPhrases: Replication,OptimisticTechniques,DistributedSystems,Largescalesys-
tems,DisconnectedOperation
1. INTRODUCTION
Datareplicationconsistsofmaintainingmultiplecopiesofdata,calledreplicas,onseparate
computers. It is an important enabling technology for distributed services. Replication
improves availability by allowing access to the data even when some of the replicas are
unavailable. Italsoimprovesperformancethroughreducedlatency,bylettingusersaccess
nearbyreplicasandavoidingremotenetworkaccess,andthroughincreasedthroughput,by
lettingmultiplecomputersservethedatasimultaneously.
This paper surveys optimistic replication algorithms. Compared to traditional “pes-
simistic”techniques,optimisticreplicationpromiseshigheravailabilityandperformance,
ThisworkissupportedinpartbyDARPAGrantF30602-97-2-0226andNationalScienceFoundationGrant#
EIA-9870740.Authors’addresses:YasushiSaito,Hewlett-PackardLaboratories,1501PageMillRd,MS1134,
PaloAlto,CA,93403,USA. mailto:yasushi@cs.washington.edu,http://www.ysaito.com. MarcShapiro,
MicrosoftResearchLtd.,7JJThomsonAve,CambridgeCB30FB,UnitedKingdom. http://www-sor.inria.fr/
∼shapiro/.
Permissiontomakedigital/hardcopyofallorpartofthismaterialwithoutfeeforpersonalorclassroomuse
providedthatthecopiesarenotmadeordistributedforprofitorcommercialadvantage,theACMcopyright/server
notice,thetitleofthepublication,anditsdateappear,andnoticeisgiventhatcopyingisbypermissionofthe
ACM,Inc. Tocopyotherwise,torepublish,topostonservers,ortoredistributetolistsrequirespriorspecific
permissionand/orafee.
(cid:13)c 2005ACM0360-0300/2005/1200-BOGUSPAGECODE$5.00
ACMComputingSurveys,Vol.V,No.N,32005,Pages1–44.

·
2 Y.SaitoandM.Shapiro
butletsreplicastemporarilydivergeandusersseeinconsistentdata. Theremainderofthis
introductionoverviewstheconceptofoptimisticreplication,definesitsbasicelements,and
comparesittotraditionalreplicationtechniques.
1.1 Traditionalreplicationtechniquesandtheirlimitations
Traditional replication techniques try to maintain single-copy consistency [Herlihy and
Wing1990; BernsteinandGoodman1983; Bernsteinetal.1987]—theygiveusersanil-
lusion of having a single, highly available copy of data. This goal can be achieved in
many ways, but the basic concept remains the same: traditional techniques block access
to a replica unless it is provably up to date. We call these techniques “pessimistic” for
this reason. For example, primary-copy algorithms, used widely in commercial systems,
elect a primary replica that is responsible for handling all accesses to a particular object
[Bernstein et al. 1987; Dietterich 1994; Oracle 1996]. After an update, the primary syn-
chronouslywritesthechangetoother, secondaryreplicas. Iftheprimarycrashes, there-
mainingreplicasconfertoelectanewprimary. Suchpessimistictechniquesperformwell
in local-area networks, in which latencies are small and failures uncommon. Given the
continuingprogressofInternettechnologies,itistemptingtoapplypessimisticalgorithms
towide-areadatareplication. Wecannotexpectgoodperformanceandavailabilityinthis
environment,however,forthreekeyreasons.
First, the Internet remains slow and unreliable; its communication latency and avail-
ability do not seem to be improving [Zhang et al. 2000; Chandra et al. 2001]. In addi-
tion,mobilecomputerswithintermittentconnectivityarebecomingincreasinglypopular.
A pessimistic replication algorithm, attempting to synchronize with an unavailable site,
wouldblockindefinitely. Thereisevenapossibilityofdatacorruption; forinstance,itis
impossible to agree accurately on a single primary after a failure when network delay is
unpredictable[Fischeretal.1985;ChandraandToueg1996].
Second, pessimistic algorithms scale poorly in the wide area. It is difficult to build a
large,pessimisticallyreplicatedsystemwithfrequentupdates,becauseitsthroughputand
availability suffer as the number of sites increases [Yu and Vahdat 2001; Yu and Vahdat
2002]. ThisiswhymanyInternetandmobileservicesareoptimistic,forinstanceUsenet
[SpencerandLawrence1998;Lidletal.1994],DNS[Mockapetris1987;Mockapetrisand
Dunlap 1988; Albitz and Liu 2001], and mobile file and database systems [Walker et al.
1983;KistlerandSatyanarayanan1992;Moore1995;Ratner1998].
Third, somehumanactivitiesrequireoptimisticdatasharing. Cooperativeengineering
orsoftwaredevelopmentoftenrequirespeopletoworkinrelativeisolation. Itisbetterto
allowthemtoupdatedataindependentlyandrepairoccasionalconflictsaftertheyhappen,
thantolockthedataoutwhilesomeoneiseditingit[Kawelletal.1988;Cederqvistetal.
2001;Vesperman2003].
1.2 Whatisoptimisticreplication?
Optimistic replication is a group of techniques for sharing data efficiently in wide-area
or mobile environments. The key feature that separates optimistic replication algorithms
from their pessimistic counterparts is their approach to concurrency control. Pessimistic
algorithmssynchronouslycoordinatereplicasduringaccessesandblockotherusersduring
an update. Optimistic algorithms let data be accessed without a priori synchronization,
basedonthe“optimistic”assumptionthatproblemswilloccuronlyrarely,ifatall.Updates
arepropagatedinthebackground,andoccasionalconflictsarefixedaftertheyhappen.Itis
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 3
notanewidea,1butitsusehasexpandedastheInternetandmobilecomputingtechnologies
havebecomemorewidespread.
Optimisticalgorithmsoffermanyadvantagesovertheirpessimisticcounterparts. First,
they improve availability; applications make progress even when network links and sites
areunreliable.2 Second, theyareflexiblewithrespecttonetworking, becausetechniques
such as epidemic replication propagate operations reliably to all replicas, even when the
communicationgraphisunknownandvariable.Third,optimisticalgorithmswouldscaleto
alargenumberofreplicas,becausetheyrequirelittlesynchronizationamongsites.Fourth,
theyallowsitesanduserstoremainautonomous: forexample, servicessuchasFTPand
Usenet mirroring [Nakagawa 1996; Krasel 2000] let a replica be added with no change
toexistingsites. Optimisticreplicationalsoenablesasynchronouscollaborationbetween
users,asinCVS[Cederqvistetal.2001; Vesperman2003]orLotusNotes[Kawelletal.
1988]. Finally, optimistic algorithms provide quick feedback, as they can apply updates
tentativelyassoonastheyaresubmitted.
These benefits, however, come at a cost. Any distributed system faces a trade-off be-
tween availability and consistency [Fox and Brewer 1999; Yu and Vahdat 2002; Pedone
2001]. Where a pessimistic algorithm waits, an optimistic one speculates. Optimistic
replicationfacesthechallengesofdivergingreplicasandconflictsbetweenconcurrentop-
erations.Itisthusapplicableonlyforapplicationsthatcantolerateoccasionalconflictsand
inconsistent data. Fortunately, in many real-world systems, especially file systems, con-
flictsareknowntoberatherrare,thankstothedatapartitioningandaccessarbitrationthat
naturally happen between users [Ousterhout et al. 1985; Baker et al. 1991; Vogels 1999;
Wangetal.2001].
1.3 Elementsofoptimisticreplication
Thissectionintroducesbasicconceptsofoptimisticreplicationanddefinescommonterms
thatareusedthroughoutthepaper. Wewilldiscusstheminmoredetailinlatersections.
Figure 1 illustrates how these concepts fit together, and Table I provides a reference for
commonterms.
1.3.1 Objects,replicas,andsites. Anyreplicatedsystemhasaconceptoftheminimal
unitofreplication. Wecallsuchunitanobject. Areplicaisacopyofanobjectstoredin
asite,oracomputer. Asitemaystorereplicasofmultipleobjects,butweoftenuseterms
replicaandsiteinterchangeably,sincemostoptimisticreplicationalgorithmsmanageeach
objectindependently. Whendescribingalgorithms,itisusefultodistinguishsitesthatcan
update an object—called master sites—from those that store read-only replicas. We use
thesymbolN todenotethetotalnumberofreplicasandMtodenotethenumberofmaster
replicasforagivenobject.CommonvaluesareM=1(single-mastersystems)andM=N.
1.3.2 Operations. An optimistic replication system must allow accesses to a replica
even while it is disconnected. We call a self-contained update to an object an operation.
Operationsdifferfromtraditionaldatabaseupdates(transactions)becausetheyarepropa-
gatedandappliedinthebackground,oftenlongaftertheyweresubmittedbytheusers.
Conceptually,anoperationcanbeviewedasapreconditionfordetectingconflictscom-
1OurearliestreferenceisfromJohnsonandThomas[1976],buttheideawascertainlydevelopedmuchearlier.
2ToleratingByzantine(malicious)failuresisoutsideourscope;weciteafewrecentpapersinthisarea:Spreitzer
etal.[1997],Minsky[2002]andMazie`resandShasha[2002].
ACMComputingSurveys,Vol.V,No.N,32005.

·
4 Y.SaitoandM.Shapiro
| 2                                | 2                         |     | 1                      |     |
| -------------------------------- | ------------------------- | --- | ---------------------- | --- |
|                                  | 1                         | 1   | 2                      | 1   |
|                                  |                           | 2   |                        | 2   |
| 1                                | 1                         |     | 1                      |     |
|                                  | 2                         |     | 2                      |     |
| (a) Operation submission:        | (b) Propagation: Sites    |     | (c) Scheduling: Sites  |     |
| Users at different sites submit  | communicate and exchange  |     | compute the ordering   |     |
| operations independently.        | operations.               |     | of operations.         |     |
1+2 1+2
1+2
| (d) Conflict resolution: Sites detect       |     | (e) Commitment: Sites agree on the final   |     |     |
| ------------------------------------------- | --- | ------------------------------------------ | --- | --- |
| conflicts and transform offending           |     | ordering and reconciliation result. Their  |     |     |
| operations  to produce results intended by  |     | changes become permanent.                  |     |     |
users.
Fig.1.Elementsofoptimisticreplicationandtheirroles.Disksrepresentreplicas,memosheetsrepresentopera-
tions,andarrowsrepresentcommunicationsbetweenreplicas.
bined with a prescription to update the object. The concrete nature of operations varies
widelyamongsystems. Manysystems,includingPalm[PalmSource2002]andDNS[Al-
bitz and Liu 2001], support only whole-object updates. Such systems are called state-
transfersystems.Othersystems,calledoperation-transfersystems,allowformoresophis-
ticated descriptions of updates. For example, Bayou describes operations in SQL [Terry
etal.1995].
Toupdateanobject,ausersubmitsanoperationatsomesite.Thesitelocallyappliesthe
operation to let the user continue working based on that update. The site also exchanges
andappliesremoteoperationsinthebackground. Suchsystemsaresaidtooffereventual
consistency,becausetheyguaranteethatthestateofreplicaswillconvergeonlyeventually.
Such a weak guarantee is enough for many optimistic replication applications, but some
systemsprovidestrongerguarantees,e.g.,thatareplica’sstateisnevermorethanonehour
old.
1.3.3 Propagation. Anoperationsubmittedbytheuserislogged,i.e.,rememberedin
order to be propagated to other sites later. These systems often deploy epidemic propa-
gation to let all sites receive operations, even when they cannot communicate with each
other directly [Demers et al. 1987]. Epidemic propagation lets any two sites that happen
tocommunicateexchangetheirlocaloperationsaswellasoperationstheyreceivedfroma
thirdsite—anoperationspreadslikeavirusdoesamonghumans.
1.3.4 Tentativeexecutionandscheduling. Becauseofbackgroundpropagation,oper-
ationsarenotalwaysreceivedinthesameorderatallsites. Eachsitemustreconstructan
appropriateorderingthatproducesanequivalentresultacrosssitesandmatchestheusers’
intuitive expectations. Thus, an operation is initially considered tentative. A site might
reorderortransformoperationsrepeatedlyuntilitagreeswithothersonthefinaloperation
ordering. We use the term scheduling to refer to the (often non-deterministic) ordering
policy.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 5
1.3.5 Detecting and resolving conflicts. With no a priori site coordination, multiple
users may update the same object at the same time. One could simply ignore such a
situation—for instance, a room-booking system could handle two concurrent requests to
thesameroombypickingonearbitrarilyanddiscardingtheother. Suchapolicy,however,
causeslostupdates. Lostupdatesareclearlyundesirableinmanyapplications,including
roombooking.
A better way to handle this problem is to detect operations that are in conflict and re-
solvethem,forexample,bylettingthepeoplerenegotiatetheirschedule. Aconflicthap-
pens when the precondition of an operation is violated, if it is to be executed according
tothesystem’sschedulingpolicy. Inmanysystems,preconditionsarebuiltimplicitlyinto
the replication algorithm. The simplest example is when all concurrent operations are
flaggedtobeinconflict, aswiththePalmPilot[PalmSource2002]andtheCodamobile
filesystem[KumarandSatyanarayanan1995].Othersystemsletuserswritepreconditions
explicitly—forexample,inaroombookingsystemwritteninBayou,apreconditionmight
accept two concurrent requests to the same room as far as their durations do not overlap
[Terryetal.1995].
Conflict resolution is usually highly application specific. Most systems simply flag a
conflict and let users fix it manually. Some systems can resolve a conflict automatically.
Forexample,Codaresolvesconcurrentwritestoanobjectfile(compilationoutput)simply
byrecompilingthesourcefile[KumarandSatyanarayanan1995].
1.3.6 Commitment. Scheduling and conflict resolution often make non-deterministic
choices. Moreover, a replica may not have received all the operations that others have.
Commitment referstoanalgorithmtoconvergethestateofreplicasbylettingsitesagree
onthesetofappliedoperations,theirfinalordering,andconflict-resolutionresults.
1.4 Comparisonwithadvancedtransactionmodels
Optimisticreplicationisrelatedtoadvanced(orrelaxed)transactionmodels[Elmagarmid
1992; Ramamritham and Chrysanthis 1996]. Both relax the ACID3 requirements of tra-
ditional database systems to improve performance and availability, but the motives are
different.
Advanced transaction models generally try to increase the system’s throughput by, for
example, letting transactions read values produced by non-committed transactions [Pu
et al. 1995]. Designed for a single-node or well-connected distributed database, they re-
quirefrequentcommunicationduringtransactionexecution.
Optimisticreplicationsystems, incontrast,aredesignedtoworkwithahighdegreeof
asynchronyandautonomy. Sitesexchangeoperationsinthebackgroundandstillagreeon
acommonstate. Theymustlearnaboutrelationshipsbetweenoperations,oftenlongafter
theyweresubmitted, andatsitesdifferentfromwheresubmitted. Theirtechniques, such
as the use of operations, scheduling, and conflict detection, reflect the characteristics of
environmentsforwhichtheyaredesigned. Preconditionsplayarolesimilartotraditional
concurrency control mechanisms, such as two-phase locking or optimistic concurrency
control [Bernstein et al. 1987], but it operates without inter-site coordination. Conflict
resolutioncorrespondstotransactionabortion.
3ACIDdemandsthatagroupofaccesses,calledatransaction,be:Atomic(all-or-nothing),Consistent(safewhen
executedsequentially),Isolated(intermediatestateisnotobservablebyothertransactions)andDurable(thefinal
stateispersistent)[GrayandReuter1993].
ACMComputingSurveys,Vol.V,No.N,32005.

·
6 Y.SaitoandM.Shapiro
|       |                                                             | TableI. Glossaryofrecurringterms. |         |     |          |
| ----- | ----------------------------------------------------------- | --------------------------------- | ------- | --- | -------- |
| Term  |                                                             |                                   | Meaning |     | Sections |
| Abort | Permanentlyrejecttheapplicationofanoperation(e.g.,toresolve |                                   |         |     | 5.1,5.5  |
aconflict).
| Clock | Acounterusedtoorderoperations,possibly(butnotalways)re- |     |     |     | 4.1 |
| ----- | ------------------------------------------------------- | --- | --- | --- | --- |
latedtorealtime.
| Commit              | Irreversiblyapplyanoperation.                           |     |     |     | 5.1,5.5       |
| ------------------- | ------------------------------------------------------- | --- | --- | --- | ------------- |
| Conflict            | Violatingthepreconditionofanoperation.                  |     |     |     | 1.3.5,3.4,5,6 |
| Consistency         | Thepropertythatthestateofreplicasstayclosetogether.     |     |     |     | 5.1,5         |
| Divergencecontrol   | Techniquesforlimitingthedivergenceofthestateofreplicas. |     |     |     | 8             |
| Eventualconsistency | Propertybywhichthestateofreplicasconvergetowardonean-   |     |     |     | 5.1           |
other’s.
| Epidemicpropagation | Propagation | mode that allows | any pair of sites | to exchange any | 3.5 |
| ------------------- | ----------- | ---------------- | ----------------- | --------------- | --- |
operation.
| Log       | Arecordofrecentoperationskeptateachsite. |               |                   |             | 1.3.3        |
| --------- | ---------------------------------------- | ------------- | ----------------- | ----------- | ------------ |
| Master(M) | A site capable                           | of performing | an update locally | (M = number | of 1.3.1,3.1 |
masters).
| Object          | Anypieceofdatabeingshared.                                |                  |                     |                   | 1.3.1   |
| --------------- | --------------------------------------------------------- | ---------------- | ------------------- | ----------------- | ------- |
| Operation(a ,b  |                                                           |                  |                     |                   |         |
| ,...)           | Descriptionofanupdatetoanobject.                          |                  |                     |                   | 1.3.2   |
| Precondition    | Predicatedefiningtheinputdomainofanoperation.             |                  |                     |                   | 1.3.2   |
| Propagate       | Transferanoperationtoallsites.                            |                  |                     |                   | 7       |
| Replica(xi)     | Acopyofanobjectstoredatasite(xi:replicaofobjectxatsitei). |                  |                     |                   | 1.3.1   |
| Resolver        | Anapplication-providedprocedureforresolvingconflicts.     |                  |                     |                   | 5.4     |
| Schedule        | Anorderedsetofoperationstoexecute.                        |                  |                     |                   | 3.3,5.2 |
| Site(i,j,...,N) | A network                                                 | node that stores | replicas of objects | (i,j: site names; | 1.3.1   |
N=numberofsites).
| Statetransfer | Techniquethatpropagatesrecentoperationsbysendingtheobject |     |     |     | 3.2,6 |
| ------------- | --------------------------------------------------------- | --- | --- | --- | ----- |
value.
| Submit | Toenteranoperationintothesystem,subjecttotentativeexecution, |     |     |     | 1.3.2 |
| ------ | ------------------------------------------------------------ | --- | --- | --- | ----- |
roll-back,reordering,commitmentorabort.
| Tentative         | Operationappliedonisolatedreplica;maybereorderedoraborted. |     |     |     | 1.3.3,5.5 |
| ----------------- | ---------------------------------------------------------- | --- | --- | --- | --------- |
| Timestamp         | (SeeClock)                                                 |     |     |     |           |
| Versionvector(VV) | (SeeVectorclock)                                           |     |     |     |           |
| Thomas’swriterule | “Last-writerwins”algorithmforresolvingconcurrentupdates.   |     |     |     | 6.1       |
| Vectorclock(VC)   | Datastructurefortrackingorderofoperationsanddetectingcon-  |     |     |     | 4.3       |
currency.
That said, there are many commonalities between optimistic replication and advanced
transactionmodels. Epsilonserializabilityallowstransactionstoseeinconsistentdataup
to some application-defined degree [Ramamritham and Pu 1995]. This idea has been in-
corporated into optimistic replication systems, including TACT and session guarantees
(Section8). Foranotherexample,Coda’sisolation-onlytransactionsapplyoptimisticcon-
currencycontroltoamobilefilesystem[LuandSatyanarayanan1995]. Ittriestorunaset
ofaccessesatomically,butitmerelyreportsanerrorwhenatomicityisviolated.
1.5 Outline
Section 2 overviews several popular optimistic-replication systems and sketches a vari-
ety of mechanisms they deploy to manage replicas. Section 3 introduces six key design
choicesforoptimisticreplicationsystems,includingthenumberofmasters,state-vs. op-
erationtransfer,scheduling,conflictmanagement,operationpropagation,andconsistency
| guarantees. Thesubsequentsectionsexaminethesechoicesinmoredetail. |     |     |     |     |     |
| ----------------------------------------------------------------- | --- | --- | --- | --- | --- |
Section4reviewstheclassicconceptsofconcurrencyandhappens-beforerelationships,
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 7
whichareusedpervasivelyinoptimisticreplicationforschedulingandconflictdetection.
It also introduces basic techniques used to implement these concepts, including logical
andvectorclocks. Section5introducestechniquesformaintainingreplicaconsistency,in-
cludingscheduling,conflictmanagement,andcommitment. Section6focusesonasimple
subclassofoptimisticreplicationsystems,calledstate-transfersystems,andseveralinter-
estingtechniquesavailabletothem. Section7focusesontechniquesforefficientoperation
propagation. We examine systems that bound replica divergence in Section 8. Finally,
Section 9 concludes by summarizing the systems and algorithms introduced in the paper
anddiscussingtheirtrade-offs.
2. APPLICATIONSOFOPTIMISTICREPLICATION
Optimisticreplicationisusedinseveralapplicationareas, includingwide-areadataman-
agement, mobile information systems, and computer-based collaboration. This section
overviewspopularoptimisticservicestoprovideacontextforthetechnicaldiscussionthat
follows.
2.1 DNS:Internetnameservice
Optimisticreplicationisparticularlyattractiveforwide-areanetworkapplications, which
musttolerateslowandunreliablecommunicationbetweensites. ExamplesincludeWWW
caching[Chankhunthodetal.1996; WesselsandClaffy1997; Fieldingetal.1999], FTP
mirroring[Nakagawa1996]anddirectoryservicessuchasGrapevine[Birrelletal.1982],
Clearinghouse [Demers et al. 1987], DNS [Mockapetris 1987; Mockapetris and Dunlap
1988;AlbitzandLiu2001],andActiveDirectory[Microsoft2000].
DNS(DomainNameSystem)isthestandardhierarchicalnameservicefortheInternet.
Namesforaparticularzone(asub-treeinthenamespace)aremanagedbyasinglemaster
serverthatmaintainstheauthoritativedatabaseforthatzone,andoptionalslaveserversthat
copy the database from the master. The master and slaves can both answer queries from
remote clients and servers. To update the database, the administrator updates the master
andincrementsitstimestamp. Aslaveserverperiodicallypollsthemasteranddownloads
the database when its timestamp changes.4 The contents of a slave may lag behind the
master’sandclientsmayobserveoldvalues.
DNS is a single-master system (all writes for a zone originate at that zone’s master)
withstatetransfer(serversexchangethewholedatabasecontents). Wewilldiscussthese
classificationcriteriafurtherinSection3.
2.2 Usenet: wide-areainformationexchange
Ournextexampletargetsmoreinteractiveinformationexchange. Usenet,awide-areabul-
letinboardsystemdeployedin1979,isoneoftheoldestandstillapopularoptimistically
replicatedservice[KantorandRapsey1986;Lidletal.1994;SpencerandLawrence1998;
Saitoetal.1998]. UsenetoriginallyranoverUUCP,anetworkdesignedforintermittent
connection over dial-up modem lines [Ravin et al. 1996]. A UUCP site could only copy
filestoitsdirectneighbors. Today’sUsenetconsistsofthousandsofsitesformingacon-
nected(butnotcomplete)graphbuiltthroughaseriesofhumannegotiations.
4RecentDNSserversalsosupportproactiveupdatenotificationfromthemasterandincrementalzonetransfer
[AlbitzandLiu2001].
ACMComputingSurveys,Vol.V,No.N,32005.

·
8 Y.SaitoandM.Shapiro
EachUsenetsitereplicatesallnewsarticles,5sothatausercanreadanyarticlefromthe
nearestsite.Usenetletsanyuserpostarticlestoanysite.Fromtimetotime,articlesposted
onasitearepushedtotheneighboringsites. Areceivingsitealsostoresandforwardsthe
articlestoitsownneighbors.Thisway,eacharticle“floods”itswaythroughinter-sitelinks
eventually to all the sites. Infinite propagation loops are avoided by each site accepting
only those articles missing from its disks. An article is deleted from a site by time-out,
orbyanexplicitcancellationrequest,whichpropagatesamongsitesjustlikeanordinary
article. Usenet’sdeliverylatencyishighlyvariable,sometimesaslongasaweek. While
users sometimes find it confusing, it is a reasonable cost to pay for Usenet’s excellent
availability.
Usenet is a multi-master system (an update can originate at any site), that propagates
articlepostingandcancellationoperationsepidemically.
2.3 Personaldigitalassistants
Optimisticreplicationisespeciallysuitedtoenvironmentswherecomputersarefrequently
disconnected. Mobile data systems use optimistic replication, as in Lotus Notes [Kawell
et al. 1988], Palm [Rhodes and McKeehan 1998; PalmSource 2002], Coda [Kistler and
Satyanarayanan1992;Mummertetal.1995],andRoam[Ratner1998].
A personal digital assistant (PDA) is a small hand-held computer that keeps a user’s
schedule, address book, and other personal information. Occasionally, the user synchro-
nizesthePDAwithhisPCandexchangesthedatabi-directionally.Aconflicthappens,say,
when the phone number of a person is changed on both ends. PDAs such as Palm use a
“modifiedbits”scheme[RhodesandMcKeehan1998;PalmSource2002]—eachdatabase
recordinPalmisassociatedwitha“modified”bit,whichissetwhentherecordisupdated
and cleared after synchronization. During synchronization, if only one of the replicas is
foundtobemodified,thenewvalueiscopiedtotheotherside.Ifboththemodifiedbitsare
set, the system detects a conflict. Conflicts are resolved either by an application-specific
resolverormanuallybytheuser.
PDAsrepresentanexampleofmulti-master,state-transfersystems;adatabaserecordis
theunitofreplication,update,andconflictresolution.
2.4 Bayou: amobiledatabasesystem
Bayou is a research mobile database system [Terry et al. 1995; Petersen et al. 1997]. It
lets a user replicate a database on a mobile computer, modify it while disconnected, and
synchronizewithanyotherreplicaofthedatabasethattheuserhappenstofind. Bayouisa
complexsystembecauseofthechallengesofsharingdataflexiblyinamobileenvironment.
A user of Bayou submits update operations as SQL statements, which are propagated
toothersitesepidemically. Asiteappliesoperationstentativelyastheyarereceivedfrom
theuserorfromothersites. Becausesitesmayreceiveoperationsindifferentorders,they
mustundoandredooperationsrepeatedlyastheygraduallylearnthefinalorder. Conflicts
aredetectedbyanapplication-specificpreconditionattachedtoeachoperation. Theyare
resolved by an application-defined merge procedure that is also attached to each opera-
tion. Thefinaldecisionregardingorderingandconflictresolutionismadebyadesignated
5 Inpractice, articlesaregroupedintonewsgroups, andasiteusuallystoresonlyasubsetofnewsgroupsto
conservenetworkbandwidthandstoragespace.Still,articlespostedtoaspecificnewsgrouparereplicatedonall
sitesthatsubscribetothenewsgroup.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 9
“home,”orprimary,site. Thehomesiteordersoperationsandresolvesconflictsintheor-
derofarrivalandsendsthedecisionstoothersitesepidemicallyasasideeffectofordinary
operationpropagation.
Bayouisamulti-master,operation-transfersystemthatusesepidemicpropagationover
arbitrary,changingcommunicationtopologies.
2.5 CVS:softwareversioncontrol
CVS(ConcurrentVersionsSystem)isaversioncontrolsystemthatletsuserseditagroup
offilescollaborativelyandretrieveoldversionsondemand[Cederqvistetal.2001; Ves-
perman 2003]. Communication in CVS is centralized through a single site. The central
site stores the repository that contains the authoritative copies of the files, along with all
changescommittedtotheminthepast. Ausercreatesprivatecopies(replicas)ofthefiles
andeditsthemusingstandardtools. Anynumberofuserscanmodifytheirprivatecopies
concurrently.Aftertheworkisdone,theusercommitstheprivatecopytotherepository.A
commitsucceedsimmediatelyifnootheruserhascommittedachangetothesamefilesin
theinterim. Ifanotheruserhasmodifiedthesamefilebutthechangesdonotoverlap,CVS
mergesthemautomaticallyandcompletesthecommit.6 Otherwise,theuserisinformedof
aconflict,whichheorshemustresolvemanuallyandre-commit.
CVS is a significant departure from the previous generation of version control tools,
suchasRCSandSCCS,whichpessimisticallylocktherepositorywhileausereditsafile
[BolingerandBronson1995]. CVSsupportsamoreflexiblestyleofcollaboration,atthe
costofoccasionalmanualconflictresolutions. Mostusersreadilyacceptthistrade-off.
CVSisamulti-masteroperation-transfersystemthatcentralizescommunicationthrough
asinglerepositoryinastartopology.
2.6 Summary
The following table summarizes the characteristics of the systems just mentioned. The
upcomingsectionswilldetailourclassificationcriteria.
System #Masters Operations Object Conflictresolution
DNS 1 Update Database None
Usenet ≥1 Post,cancel Article None
Palm ≥1 Update Record Manualor
application-specific
Bayou ≥1 SQL App-defined Application-specific
CVS ≥1 Insert, delete, File Manual
modifylines
3. OPTIMISTICREPLICATION:DESIGNCHOICES
The ultimate goal of any optimistic replication system is to maintain consistency; that
is, to keep replicas sufficiently similar to one another despite operations being submitted
independentlyatdifferentsites. Whatexactlyismeantbythisdiffersconsiderablyamong
systems, however. This section overviews how different systems define and implement
consistency. Weclassifyoptimisticreplicationsystemsalongthefollowingaxes:
6Ofcourse,theupdatesmightstillconflictsemantically,e.g.,amergedsourcefilemightnotcompile.
ACMComputingSurveys,Vol.V,No.N,32005.

·
| 10  | Y.SaitoandM.Shapiro |     |     |     |     |
| --- | ------------------- | --- | --- | --- | --- |
Active directory,
Usenet, Coda,
Refdbms, Bayou,
DNS, NIS,
|     |           |     | IceCube,       | Clearinghouse, |     |
| --- | --------- | --- | -------------- | -------------- | --- |
|     | WWW/FTP   |     | Operational    | Roam, Ficus,   |     |
|     | mirroring |     | transformation | Palm           |     |
Multi master
Single master
Fig.2. Singlevs.multi-master
| Choice          |                                | Description |     | Effects |     |
| --------------- | ------------------------------ | ----------- | --- | ------- | --- |
| Numberofwriters | Whichreplicascansubmitupdates? |             |     |         |     |
Definesthesystem’sba-
| Definitionof | Whatkindsofoperationsare |     |     | sic complexity, | avail- |
| ------------ | ------------------------ | --- | --- | --------------- | ------ |
supported,andtowhatdegreeisa
| operations |     |     |     | abilityandefficiency. |     |
| ---------- | --- | --- | --- | --------------------- | --- |
systemawareoftheirsemantics?
Howdoesasystemorder
| Scheduling |     |     |     | Definesthesystem’s |     |
| ---------- | --- | --- | --- | ------------------ | --- |
operations?
abilitytohandle
| Conflict | Howdoesasystemdefineand |     |     | concurrentoperations. |     |
| -------- | ----------------------- | --- | --- | --------------------- | --- |
management
handleconflicts?
| Operation |     |     |     | Definesnetworking |     |
| --------- | --- | --- | --- | ----------------- | --- |
Howareoperationsexchanged
| propagation |     |     |     | efficiencyandthespeedof |     |
| ----------- | --- | --- | --- | ----------------------- | --- |
betweensites?
| strategy |     |     |     | replicaconvergence |     |
| -------- | --- | --- | --- | ------------------ | --- |
Consistency Whatdoesasystemguaranteeabout Definesthetransientquality
| guarantees |     |     |     | ofreplicastate. |     |
| ---------- | --- | --- | --- | --------------- | --- |
thedivergenceofreplicastate?
| 3.1 Numberofwriters: | single-mastervs.multi-master |     |     |     |     |
| -------------------- | ---------------------------- | --- | --- | --- | --- |
Figure2showsthechoiceregardingwhereanupdatecanbesubmittedandhowitispropa-
gated. Single-mastersystemsdesignateonereplicaasthemaster(i.e.,M=1). Allupdates
originateatthemasterandthenarepropagatedtootherreplicas,orslaves. Theymayalso
becalledcachingsystems. Theyaresimplebuthavelimitedavailability,especiallywhen
thesystemexperiencesfrequentupdates.
Multi-master systems let updates be submitted at multiple replicas independently (i.e.,
M≥1)andexchangetheminthebackground. Theyaremoreavailablebutsignificantly
more complex. In particular, operation scheduling and conflict management are issues
unique to these systems. Another potential problem with multi-master systems is their
limitedscalabilityduetoincreasedconflictrate. AccordingtoGrayetal.[1996],ana¨ıve
multi-mastersystemwouldencounterconcurrentupdatesattherateofO(M2),
assuming
that each master submits operations at a constant rate. The system will treat many of
these updates as conflicts and resolve them. On the other hand, pessimistic or single-
mastersystemswiththesameaggregateupdateratewouldexperienceanabortionrateof
only O(M), as most concurrent operations can be serialized using local synchronization
techniques,suchastwo-phaselocking[Bernsteinetal.1987]. Still,thereareremediesto
thisscalingproblem,aswediscussinSection7.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 11
Usenet, DNS, Coda, Refdbms, IceCube,
Clearinghouse, Roam, Bayou, Operational
Palm ESDS transformation
State transfer
Fig.3. Definitionofoperations
3.2 Definitionofoperations: statetransfervs.operationtransfer
Figure3illustratesthemaindesignchoicesregardingthedefinitionsofoperations. State-
transfersystemslimitanoperationeithertoreadortooverwritetheentireobject. Opera-
tiontransfersystemsdescribeoperationsmoresemantically.Astate-transfersystemcanbe
seenasadegenerateformofoperationtransfer,buttherearesomequalitativedifferences
betweenthetwotypesofsystems.
State transfer is simple, because maintaining consistency only involves sending the
newestreplicacontentstootherreplicas. Operation-transfersystemsmustmaintain(orre-
construct)ahistoryofoperationsandhavereplicasagreeonthesetofoperationsandtheir
order. Ontheotherhand,theycanbemoreefficient,especiallywhenobjectsarelargeand
operationsarehighlevel. Forexample,astate-transferfilesystemmighttransfertheentire
file(ordirectory)contentseverytimeabyteismodified[KistlerandSatyanarayanan1992].
Anoperation-transferfilesystem,incontrast,couldtransferanoperationthatproducesthe
desiredeffect,sometimesashigh-levelas“cc foo.c”,resultinginthereductionofnet-
worktrafficbyafactorofafewhundreds[Leeetal.2002]. Operationtransferalsoallow
formoreflexibleconflictresolution. Forexample,inabibliographydatabase,updatesthat
modifytheauthorsoftwodifferentbookscanbothbeaccommodatedinoperation-transfer
systems(semantically,theydonotconflict),butitisdifficulttodothesamewhenasystem
transferstheentiredatabasecontentseverytime[Golding1992;Terryetal.1995].
3.3 Scheduling: syntacticvs.semantic
The goal of scheduling is to order operations in a way expected by users and to produce
equivalent states across replicas. Scheduling policies can be classified into syntactic and
semantic policies (Figure 3). Syntactic scheduling sorts operations based only on infor-
mation about when, where and by whom operations were submitted. Timestamp-based
orderingisthemostpopularexample. Semanticschedulingexploitssemanticproperties,
suchascommutativityoridempotencyofoperations,toreduceconflictsorthefrequency
ofroll-back. Semanticschedulingisusedonlyinoperation-transfersystems, sincestate-
transfersystemsareoblivioustooperationsemanticsbynature.
Syntactic methods are simpler but may cause unnecessary conflicts. Consider, for ex-
ample, a system for reserving some equipment on loan, where the pool initially contains
a single item. Three requests are submitted concurrently: (1) User A requests an item,
(2)UserBrequestsanitem, and(3)UserCaddsanitemtothepool. Ifasiteschedules
the requests syntactically in the order 1, 2, 3, then request 2 will fail (B cannot borrow
fromanemptypool). Usingsemanticscheduling,thesystemcouldorder1,3,then2,thus
satisfyingalltherequests.
Semanticschedulingisalsoseeninreplicatedfilesystems: writingtotwodifferentfiles
commutes, as does creating two different files in the same directory. File systems can
ACMComputingSurveys,Vol.V,No.N,32005.

·
12 Y.SaitoandM.Shapiro
Shrink objects Two App-specific
Single master Thomas Quick propagation timestamps preconditions
write rule App-specific ordering Vector Canonical ordering
Fig.4. Designchoicesregardingconflicthandling.
schedule these operations in any order and still let replicas converge [Balasubramaniam
and Pierce 1998; Ramsey and Csirmaz 2001]. We will discuss techniques for operation
orderinginmoredetailinSections4and5.
3.4 Handlingconflicts
Conflictshappenwhensomeoperationsfailtosatisfytheirpreconditions.Figure4presents
taxonomyofapproachesfordealingwithconflicts.
The best approach is to prevent conflicts from happening altogether. Pessimistic algo-
rithms prevent conflicts by blocking or aborting operations as necessary. Single-master
systems avoid conflicts by accepting updates only at one site (but allow reads to happen
anywhere). Theseapproaches,however,comeatthecostofloweravailabilityasdiscussed
inSection1. Conflictscanalsobereduced,forexample,byquickeningpropagationorby
dividingobjectsintosmallerindependentunits.
Somesystemsignoreconflicts: anypotentiallyconflictingoperationissimplyoverwrit-
tenbyaneweroperation.Suchlostupdatesmaynotbeanissueifthelossrateisnegligible,
or if users can voluntarily avoid lost updates. A distributed name service is an example,
where usually only the owner of a name may modify it [Demers et al. 1987; Microsoft
2000].
The user experience is improved when a system can detect conflicts, as discussed in
Section1.3.5. Conflictdetectionpoliciesarealsodividedintosyntacticandsemanticpoli-
cies. In systems with syntactic policies, preconditions are not explicitly specified by the
userortheapplication. Instead,theyrelyonthetimingofoperationsubmissionandcon-
servativelydeclareaconflictbetweenanytwoconcurrentoperations. Section4introduces
varioustechniquesfordetectingconcurrentoperations. Systemswithsemanticknowledge
of operations can often exploit that to reduce conflicts. For instance, in a room-booking
application,twoconcurrentreservationrequeststothesameroomobjectcouldbegranted,
aslongastheirdurationdoesnotoverlap.
Thetrade-offbetweensyntacticandsemanticconflictdetectionparallelsthatofschedul-
ing: syntacticpoliciesaresimplerandgenericbutcausemoreconflicts,whereassemantic
policiesaremoreflexible,butapplicationspecific. Infact,conflictdetectionandschedul-
ing are closely related issues: syntactic scheduling tries to preserve the order of non-
concurrent operations, whereas syntactic conflict detection flags any operations that are
concurrent. Semanticpoliciesareattemptstobetterhandlesuchconcurrentoperations.
3.5 Propagationstrategiesandtopologies
Local operations must be transmitted and executed at remote sites. Each site will record
(log)itschangeswhiledisconnectedfromothers,decidewhentocommunicatewithothers,
and exchange changes with other sites. Propagation policies can be classified along two
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 13
Pushing Active Directory Usenet
Degree
of Hybrid DNS, Coda Refdbms Clearinghouse
synchrony
Pulling 2-tier replication, Roam Bayou
PDAs
Star Semi-structured Ad-hoc
Topology
Fig.5. Designchoicesregardingoperationpropagation.
Fig.6. Choicesregardingconsistencyguarantees
axes,communicationtopologyandthedegreeofsynchrony,asillustratedinFigure5.
Fixedtopologies,suchasastarorspanningtreecanbeveryefficient,butworkpoorly
indynamic,failure-pronenetworkenvironments. Attheotherendofthespectrum,many
optimistic replication systems rely on epidemic communication that allows operations to
propagate through any connectivity graph even if it changes dynamically [Demers et al.
1987].
The degree of synchrony shows the speed and frequency by which sites communicate
and exchange operations. At one end of the spectrum, pull-based systems demand that
eachsitepollothersiteseithermanually(e.g.,PDAs)orperiodically(e.g.,DNS)fornew
operations. Inpush-basedsystems,asitewithnewupdatesproactivelysendsthemtooth-
ers.Ingeneral,thequickerthepropagation,thelessthedegreeofreplicainconsistencyand
therateofconflict,butmorethecomplexityandoverhead,especiallywhentheapplication
iswriteintensive.
3.6 Consistencyguarantees
Inanoptimisticreplicationsystem,thestatesofreplicasmaydivergesomewhat. Aconsis-
tencyguaranteedefineshowmuchdivergenceaclientapplicationmayobserve. Figure6
showssomecommonchoices.
Single-copyconsistency,orlinearizability,ensuresthatasetofaccessestoanobjecton
multiple sites produces an effect equivalent to some serial execution of them on a single
site,compatiblewiththeirorderofexecutioninthehistoryoftherun[HerlihyandWing
1990].Attheotherendofthespectrum,eventualconsistencyguaranteesonlythatthestate
ofreplicaswilleventuallyconverge. Inthemeantime,applicationsmayobservearbitrarily
stalestate,orevenincorrectstate. WedefineeventualconsistencymorepreciselyinSec-
ACMComputingSurveys,Vol.V,No.N,32005.

·
14 Y.SaitoandM.Shapiro
tion5.1. Eventualconsistencyisafairlyweakconcept, butitistheguaranteeofferedby
mostoptimistic-replicationsystems,forwhichtheavailabilityisofparamountimportance.
As such, most of the techniques we describe in this paper are for maintaining eventual
consistency.
Inbetweensingle-copyandeventualconsistencypolicies, numerousintermediatecon-
sistency types have been proposed, which we call “bounded divergence” [Ramamritham
andChrysanthis1996; YuandVahdat2001]. Boundeddivergenceisusuallyachievedby
blockingaccessestoareplicawhencertainconsistencyconditionsarenotmet.Techniques
forboundingdivergencearecoveredinSection8.
4. DETECTINGCONCURRENCYANDHAPPENS-BEFORERELATIONSHIPS
Anoptimisticreplicationsystemacceptsoperationsthataresubmittedindependently,then
schedulethemand(often)detectsconflicts. Manysystemsuseintuitiveorderingrelations
between operations as the basis for this task. This section reviews these relations and
techniquesforexpressingthem.
4.1 Thehappens-beforeandconcurrencyrelations
Scheduling requires a system to know which events happened in which order. However,
inadistributedenvironmentinwhichcommunicationdelaysareunpredictable,wecannot
defineanaturaltotalorderingbetweenevents. Theconceptofhappens-beforeisanimple-
mentablepartialorderingthatintuitivelycapturestherelationsbetweendistributedevents
[Lamport1978]. Considertwooperationsa andb submittedatsitesiand j,respectively.
Operationa happensbeforeb when:
— i= janda wassubmittedbeforeb ,or
— i6= jandb issubmittedafter jhasreceivedandexecuteda ,or
— Forsomeoperationg ,a happensbeforeg andg happensbeforeb .
Ifneitheroperationa norb happensbeforetheother,theyaresaidtobeconcurrent.
The happens-before and concurrency relations are used in a variety of ways in opti-
mistic replication, e.g., as a hint for operation ordering (Section 5.2), to detect conflicts
(Section 5.3), and to propagate operations (Section 7.1). The following sections review
algorithmsforrepresentingordetectingtheserelations.
4.2 Explicitrepresentation
Somesystemsrepresentthehappens-beforerelationsimplybyattaching,toanoperation,
the names of operations that precede it [Birman and Joseph 1987; Mishra et al. 1989;
Feketeetal.1999;Kermarrecetal.2001;Kangetal.2003]. Operationa happens-before
b if a appears in b ’s predecessors. The size of this set is independent of the number of
replicas,butitgrowswiththenumberofpastoperations.
4.3 Vectorclocks
Avectorclock(VC),alsocalledaversionvector,timestampvector,oramulti-parttimes-
tamp,isacompactdatastructurethataccuratelycapturesthehappens-beforerelationship
[Parker et al. 1983; Fidge 1988; Mattern 1989]. VCs are proved to be the smallest such
datastructurebyCharron-Bost[1991].
AvectorclockVC,keptonSitei,isanM-elementarrayoftimestamps(Misthenumber
i
ofmasterreplicas). Inpractice,vectorclocksareusuallyimplementedasatablethatmaps
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 15
varvc:array[1..M]ofTimestamp
procSubmitOperation(op)//Calledwhenthissitesubmitsanewoperationop
vc[myself]:=vc[myself]+1
op.issuer:=myself
op.vc:=vc
... sendoptoothersites...
procReceiveUpdate(op)//Calledwhenanoperationoparrivesfromaremotesite
//Here,weassumethatoperationsfromasinglesitearrivesinFIFOorder
vc[op.issuer]=op.vc[op.issuer]
... applytheoperation...
Fig.7. Generatingvectorclocks. Everysiteexecutesthesamealgorithm. Variablemyself isthenameofthe
currentsite.
varclock:Timestamp //Logicalclock
procSubmitOperation(op)//Calledwhenthissitesubmitsanewoperationop.
clock:=clock+1
op.clock:=clock
... sendoptoothersites...
procReceiveUpdate(op)//Calledwhenanoperationoparrivesfromaremotesite.
clock:=max(clock,op.clock)+1
... applytheoperation...
Fig.8. Generatinglogicalclocks.Everysiteexecutesthesamealgorithm.
thesite’sname,sayIPaddress,toatimestamp. Atimestampisanynumberthatincreases
foreverydistinctevent—itiscommonlyjustanintegercounter.Tosubmitanewoperation
a , Site i increments VC[i] and attaches the new value of VC, now called a ’s timestamp
i i
VCa ,toa . ThecurrentvalueofVC
i
[i]iscalledi’stimestamp,asitshowsthelasttimean
operationwassubmittedatSitei. IfVC[j]=t,thismeansthatSiteihasreceivedallthe
i
operationsfromSite jwithtimestampsuptot.7 Figure7showshowVCsarecomputed.
VCb dominatesVCa ifVCa 6=VCb and∀k∈{1...M},VCa [k]≤VCb [k]. Operationa
happens before b if and only if VCb dominates VCa . If neither VC dominates the other,
theoperationsareconcurrent.
AgeneralproblemwithVCsissizewhenM islarge,andcomplexitywhensitescome
andgodynamically,althoughsolutionsexist[Ratneretal.1997;Petersenetal.1997;Adya
andLiskov1997].
4.4 Logicalandreal-timeclocks
A single, scalar timestamp can be also used to express happens-before relationships. A
logicalclock,alsocalledaLamportclock,isatimestampmaintainedateachsite[Lamport
1978]. Figure8illustratesitsuse. Whensubmittinganoperationa ,thesiteincrementsthe
clock andattaches thenew value, notedCa , to a . Upon receiving a , thereceiver setsits
logicalclocktobeavaluelargerthaneitheritscurrentvalueorCa . Withthisdefinition,
if operation a happens before b , thenCa <Cb . However, logical clocks (or any scalar
clocks) cannot detect concurrency, because Ca <Cb does not necessarily imply that a
happensbeforeb .
Real-time clocks can also be used to track happens-before relationship. Comparing
suchclocksbetweensites,however,ismeaningfulonlyiftheyareproperlysynchronized.
Consider two operations a and b , submitted at sites i and j, respectively. Even if b is
submittedafter jreceiveda ,b ’stimestampcouldstillbesmallerthana ’sif j’sclocklags
7Forthispropertytohold, operationsfromaparticularsitemustbepropagatedtoanothersiteinsubmission
order.
ACMComputingSurveys,Vol.V,No.N,32005.

·
16 Y.SaitoandM.Shapiro
farbehindi’s. Thissituationcannotultimatelybeavoided,becauseclocksynchronization
isabest-effortserviceinasynchronousenvironments[ChandraandToueg1996]. Modern
algorithms such as NTP, however, can keep clock skew within tens of microseconds in a
LAN, and tens of milliseconds in a wide area with a negligible cost [Mills 1994; Elson
et al. 2002]. They are usually accurate enough to capture most happens-before relations
thathappeninpractice.
Real-timeclocksdohaveanadvantageoverlogicalandvectorclocks: theycancapture
relations that happen via a “hidden channel”, or outside the system’s control. Suppose
thata usersubmits anoperation a oncomputer i, walks overto anothercomputer j, and
submitsanotheroperationb . Fortheuser,a clearlyhappensbeforeb ,andreal-timeclocks
candetectthat. Logicalclocksmaynotdetectsucharelation,becauseiand jmightnever
haveexchangedmessagesbeforeb wassubmitted.
4.5 Plausibleclocks
Plausibleclockscombineideasfromlogicalandvectorclockstobuildclockswithinter-
mediate strength [Valot 1993; de Torres-Rojas and Ahamad 1996]. They have the same
theoreticalstrengthasscalarclocks,butbetterpracticalaccuracy. Thepapersintroducea
variety of plausible clocks, including the use of a vector clock of fixed size K (K ≤M),
with Site i using (imodK)th entry of the vector. This vector clock can often (but not
always)detectconcurrency.
5. CONCURRENCYCONTROLANDEVENTUALCONSISTENCY
A site in an optimistic replication system collects and orders operations submitted inde-
pendentlyatthisandothersites. Thissectionreviewstechniquesforachievinganeventual
consistency of replicas in such environments. We first define eventual consistency using
theconceptsofscheduleanditsequivalence.Wesubsequentlyexaminethenecessarysteps
towardthisgoal: computinganordering,identifyingandresolvingconflicts,andcommit-
tingoperations.
5.1 Eventualconsistency
Informally,eventualconsistencymeansthatreplicaseventuallyreachthesamefinalvalue
ifusersstopsubmittingnewoperations.Thissectiontriestoclarifythisconcept,especially
wheninpracticesitesindependentlysubmitoperationscontinually.
Wedefinetwoschedulestobeequivalentwhen,startingfromthesameinitialstate,they
produce the same final state.8 Schedule equivalence is an application-specific concept;
for instance, if a schedule contains a sequence of commuting operations, swapping their
orderpreservestheequivalence. Forthepurposeofconflictresolution,wealsoallowsome
operationa tobeincludedinaschedule,butnotexecuted. Weusethesymbola todenote
suchanabortedoperation.
Definition: Areplicatedobjectiseventuallyconsistentwhenitmeetsthefollowingcon-
ditions,assumingthatallreplicasstartfromthesameinitialstate.
—At any moment, for each replica, there is a prefix of the schedule that is equivalent to
a prefix of the schedule of every other replica. We call this a committed prefix for the
8 Inanoptimisticsystemusersmayobservedifferenttentativeresults. Therefore,weonlyincludecommitted
results(i.e.,thefinalstate)inourdefinitionofequivalence.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 17
replica.
—Thecommittedprefixofeachreplicagrowsmonotonicallyovertime.
—Allnon-abortedoperationsinthecommittedprefixsatisfytheirpreconditions.
—Foreverysubmittedoperationa ,eithera ora willeventuallybeincludedinthecom-
mittedprefix.
Thisdefinitionleavesplentyofroomfordifferingimplementations. Thebasictrickisto
playwithequivalenceandwithpreconditionstoallowformoreschedulingflexibility. For
instance,inUsenet,thepreconditionisalwaystrue,itneverabortsanoperation,andthusit
postsarticlesinanyorder;eventualconsistencyreducestoeventualdeliveryofoperations.
Bayou,incontrast,allowsexplicitpreconditionstobewrittenbyusersorapplications,and
itrequiresthatcommittedoperationsbeappliedinthesameorderateverysite.
5.2 Scheduling
As introduced in Section 3.3, scheduling policies in optimistic replication systems vary
alongthespectrumbetweensyntacticandsemanticapproaches. Syntacticschedulingde-
fines a total order of operations from the timing and location of operation submission,
whereas semantic approaches provide more scheduling freedom by exploiting operation
semantics.
5.2.1 Syntactic scheduling. A scheduler should at least try to preserve the happens-
before relationships seen by operations. Otherwise, users may observe an object’s state
to “roll back” randomly and permanently, which renders the system practically useless.
Timestampschedulingisastraightforwardattempttowardthisgoal.
A typical timestamp scheduler uses a scalar clock technique to order operations. Ex-
amplesincludeActiveDirectory[Microsoft2000],Usenet[SpencerandLawrence1998],
andTSAE[Golding1992]. Intheabsenceofconcurrentupdates,vectorclocksalsopro-
vide a total ordering, as used in LOCUS [Parker et al. 1983; Walker et al. 1983], and
Coda[KistlerandSatyanarayanan1992;KumarandSatyanarayanan1995]. Systemsthat
maintain an explicit log of operations, such as Bayou, can use an even simpler solution:
exchange the log contents sequentially [Petersen et al. 1997]. Here, a newly submitted
operationisappendedtothesite’slog. Duringpropagation,asitesimplyreceivesmissing
operationsfromanothersiteandappendsthemtotheloginfirst-in-first-outorder. These
systemseffectivelyusethelogpositionofanoperationasalogicalclock.
Syntacticpoliciesorderconcurrentoperationsinsomearbitraryorder. Insomesystems,
e.g., those that use scalar timestamps, sites can order concurrent operations deterministi-
cally. Othersystems,includingBayou,mayproducedifferentorderingsatdifferentsites.
Theymustbecombinedwithanexplicitcommitmentprotocoltoletsiteseventuallyagree
ononeordering. WewilldiscusssuchprotocolsinSection5.5.
5.2.2 Semantic scheduling: Exploiting commutativity. Semantic scheduling tech-
niques take the semantic relations between operations into account, either in addition to
thehappens-beforerelationship,orinsteadofit. Acommonexampleistheuseofcommu-
tativity [Jagadish et al. 1997]. If two consecutive operations a and b commute, they can
runineitherorder,evenifonehappensbeforetheother.Thisenablestoreducethenumber
ofrollbacksandredoswhenatentativescheduleisre-evaluated.
Areplicateddictionary(ortable)isapopularexample,wherealldictionaryoperations
(insertionanddeletion)withdifferentkeyscommutewitheachother[WuuandBernstein
ACMComputingSurveys,Vol.V,No.N,32005.

·
18 Y.SaitoandM.Shapiro
1984;Mishraetal.1989].
5.2.3 Semantic scheduling: Canonical ordering. Ramsey and Csirmaz [2001] for-
mally study optimistic replication in a file system. For every possible pair of concurrent
operations, they define a rule that specifies how they interact and may be ordered (non-
concurrentoperationsareappliedintheirhappens-beforeorder.) Forinstance,theyallow
creating two files /a/b and /a/c in any order, even though they both update the same
directory. Or,ifoneusermodifiesafile,andanotherdeletesitsparentdirectory,itmarks
them as conflicting and asks the users to repair them manually. Ramsey and Csirmaz
[2001]provethatthisalgebrainfactkeepsreplicasofafilesystemconsistent.
This file system supports few operation types, including create, remove, and edit. In
particular, it lacks “move”, which would have increased the complexity significantly, as
movingafileinvolvesthreeobjects: twodirectoriesandafile. Despitethesimplification,
thealgebracontains51differentrules. Itremainstobeseenhowthisapproachappliesto
morecomplexenvironments.
5.2.4 Semantic scheduling: Operational transformation. Operational transformation
(OT)isatechniquedevelopedforcollaborativeeditors[EllisandGibbs1989;SunandEl-
lis1998;Sunetal.1996;Sunetal.1998;Vidotetal.2000].Acommandbyauser,e.g.,text
insertionordeletion, isappliedatthelocalsiteimmediately, andthensenttoothersites.
Sitesapplyremotecommandsinreceptionorder,anddonotreorderalready-executedop-
erations; thustwositesapplythesamesetofoperations, butpossiblyindifferentorders.
Foreverypossiblepairofconcurrentoperations,OTdefinesarewritingrulethatguaran-
tees replica convergence, while preserving the intentions of the operations regardless of
receptionorder.
Consider a text editor that shares a text “abc”. The user at site i executes insert(“X”,
1),yielding“Xabc”,andsendstheupdatetoSite j. Theuseratsite j executesdelete(1)
yielding “bc”, and sends the update to Site i. In a na¨ıve implementation, Site j would
have “Xbc”, whereas Site i would have an unexpected “abc”. Using OT, Site i rewrites
j’soperationtodelete(2). Thus,OTusessemanticstotransformoperationstoruninany
orderevenwhentheydonotnaturallycommute.
The actual set of rewriting rules is complex and non-trivial, because it must provably
convergethestateofreplicasgivenarbitrarypairsofconcurrentoperations[Cormack1995;
Vidotetal.2000]. Theproblembecomesevenmorecomplexwhenonewantstosupport
three or more concurrent users [Sun and Ellis 1998]. Palmer and Cormack [1998] prove
the correctness of transformations for a shared spreadsheet that supports operations such
asupdatingcellvalues,addingordeletingrowsorcolumns,andchangingformulæ. Molli
etal.[2003]extendtheOTapproachtosupportareplicatedfilesystem.
5.2.5 Semanticscheduling: optimizationapproach. IceCubeisatoolkitthatsupports
multipleapplicationsanddatatypesusingaconceptcalledconstraintsbetweenoperations
[Kermarrecetal.2001; Preguic¸aetal.2003]. Aconstraintisanobjectthatreifiesapre-
condition. Constraints can be supplied from several sources: the user, the application, a
datatype,orthesystem.
IceCube supports several kinds of constraints, including dependence (a executes only
after b does), implication (if a executes, so does b ), choice (either a or b may be ap-
plied,butnotboth),andaspecializedconstraintforexpressingresourceallocationtimings
[Matheson2003]. Forinstance,ausermighttrytoreserveRoom1or2(choice);ifRoom
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 19
2 is chosen, rent a projector (implication), which is possible only if sufficient funds are
available(dependence).
IceCube treats scheduling as an optimization problem, where the goal is to find the
“best” schedule of operations compatible with the stated constraints. The goodness of a
schedule is defined by the user or the application—e.g., one may define a schedule with
fewerconflictstobebetter. Furthermore,IceCubesupportsanexplicitcommutativityre-
lationtosubdividethesearchspace. DespitetheNP-hardnatureoftheproblem,IceCube
usesanefficienthill-climbing-basedconstraintsolverthatcanorder10,000operationsin
lessthan3seconds[Preguic¸aetal.2003].
5.3 Detectingconflicts
An operation a is in conflict when its precondition is unsatisfied, given the state of the
replica after tentatively applying operations before a in the current schedule. Conflict
management involves two subtasks: detecting a conflict, the topic of this section, and
resolving it, which we review in Section 5.4. Just like for scheduling, techniques range
overthespectrumbetweensyntacticandsemanticapproaches.
Many systems do nothing about conflict, for instance any system using the Thomas’s
writerule(Section6.1). Thesesystemssimplyapplyoperationsintheorderofschedule,
obliviousofanyconflictsthatmightexistbetweenthem.Detectingandexplicitlyresolving
conflicts,however,alleviatesthelost-updateproblemandhelpsusersbettermanagedata,
asdiscussedinSection1.3.5.
Syntacticconflictdetectionusesthehappens-beforerelationship,orsomeapproximation
ofit,toflagconflicts. Thatis,anoperationisdeemedinconflictwhenitisconcurrentwith
another operation. We describe syntactic approaches in more detail in Section 6 in the
contextofstatetransfersystems,becausethatiswheretheyarethemostoftenused.
Semantic approaches use the knowledge of operation semantics to detect conflicts. In
somesystems,theconflictdetectionprocedureisbuiltin. Forinstance,inareplicatedfile
system,creatingtwodifferentfilesconcurrentlyinthesamedirectoryisnotaconflict,but
updatingthesameregularfileconcurrentlyisaconflict[RamseyandCsirmaz2001;Kumar
andSatyanarayanan1993]. Othersystems,notablyBayouandIceCube,lettheapplication
ortheuserwriteexplicitpreconditions.Thisapproachisolatestheapplication-independent
componentsofoptimisticreplication—e.g.,operationpropagationandcommitment—from
conflictdetectionandresolution. Semanticpoliciesarestrictlymoreexpressivethansyn-
tacticcounterparts,sinceonecaneasilywriteasemanticconflictdetectorthatemulatesa
syntacticalgorithm. Forinstance,Bayou[Terryetal.1995]canbeprogrammedtodetect
conflictusingthetwo-timestampalgorithmpresentedinSection6.2.
Mostoperation-transfersystemsusesemanticconflictdetectors,mainlybecausetheap-
plication already describes operations semantically—adding an application-specific pre-
conditionrequirelittleadditionalengineeringeffort. Ontheotherhand,state-transfersys-
temscouldusebothapproaches.
5.4 Resolvingconflicts
The role of conflict resolution is to rewrite or abort offending operations to remove sus-
pected conflicts. Conflict resolution can be either manual or automatic. Manual conflict
resolution simply excludes the offending operation from the schedule and presents two
versions of the object. It is up to the user to create a new, merged version and re-submit
theoperation. ThisstrategyisusedbysystemssuchasLotus[Kawelletal.1988], Palm
ACMComputingSurveys,Vol.V,No.N,32005.

·
20 Y.SaitoandM.Shapiro
[PalmSource2002],andCVS(Section2.5).
5.4.1 Automatic conflict resolution in file systems. Automatic conflict resolution is
performed by an application-specific procedure that takes two versions of an object and
creates a new one. Such an approach is well studied in replicated file systems, such as
LOCUS [Walker et al. 1983], Ficus, Roam [Reiher et al. 1994; Ratner 1998], and Coda
[KumarandSatyanarayanan1995]. Forinstance,concurrentupdatesonamailfolderfile
canberesolvedbycomputingtheunionofthemessagesfromthetworeplicas.Concurrent
updatestocompiled(*.o)filescanberesolvedbyrecompilingfromtheirsource.
5.4.2 ConflictresolutioninBayou. Bayousupportsmultipleapplicationstypesbyat-
taching an application-specific precondition (called the dependency check) and resolver
(called the merge procedure) to each operation. Every time an operation is added to a
schedule or its schedule ordering changes, Bayou runs the dependency check; if it fails,
Bayou runs the merge procedure, which can perform any fix-up necessary. For instance,
iftheoperationisanappointmentrequest, thedependencycheckmightdiscoverthatthe
requested slot is not free any more; then the merge procedure could try a different time
slot.
Toconvergethestateofreplicas,everymergeproceduremustbecompletelydetermin-
istic, including its failure behavior (e.g., it may not succeed on some site and run out of
memoryonanother). PracticalexperiencewithBayouhasshownthatitisdifficulttowrite
mergeproceduresforallbutthesimplestofcases[Terryetal.2000].
5.5 Commitmentprotocols
Commitmentservesthreepracticalpurposes. First,whensitescanmakenon-deterministic
choicesduringschedulingorconflictresolution,commitmentensuresthatsitesagreeabout
them. Second,itletsusersknowwhichoperationsarestable,i.e.,theireffectwillneverbe
rolledback.Third,commitmentactsasaspace-boundingmechanism,becauseinformation
aboutstableoperationscansafelybedeletedfromthesite.
5.5.1 Implicitcommitmentbycommonknowledge. Manysystemscandowithoutex-
plicit commitment. Examples include systems that use totally deterministic scheduling
andconflict-handlingalgorithms,suchassingle-mastersystems(DNSandNIS)andsys-
temsthatuseThomas’swriterule(Usenet,ActiveDirectory). Thesesystemscanrelyon
timestampstoorderoperationsdeterministicallyandconflictsareeithernonexistentorjust
ignored.
5.5.2 Agreementinthebackground. Themechanismsdiscussedinthissectionallow
sites to agree on the set of operations known to be received at all sites. TSAE (Time-
Stamped Anti Entropy) is an operation-transfer algorithm that uses real-time clocks to
scheduleoperationssyntactically[Golding1992]. TSAEusesackvectorsinconjunction
with vector clocks (Section 7.1) to let each site learn about the progress of other sites.
The ack vector AV on Site i is an N-element array of timestamps. AV [i] is defined to
i i
be min (VC[j]), i.e., Site i has received all operations with timestamps no newer
j∈{1...M} i
thanAV [i],regardlessoftheirorigin. Ackvectorsareexchangedamongsitesandupdated
i
by taking pair-wise maxima, just like VCs. Thus, if AV [k]=t, then i knows that k has
i
received all messages up tot. Figure 9 illustrates the relationship among operations, the
schedule, and ack vectors. With this definition, all operations with timestamps no larger
than min (AV [j]) are guaranteed to have been received by all sites, and they can
j∈{1...N} i
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 21
Schedule of operations
AV i [k] AV k [k]
Site i knows that
Site k has received Site k has received
k has received
these operations. some, but not all of
these operations.
these operations.
Fig.9. Relationshipbetweenoperations,schedule,andackvectors. Thecirclesrepresentoperations,ordered
accordingtoanagreed-uponschedule.AVi[k]showsaconservativeestimateofoperationsreceivedbyk.Itisno
largerthanAVk [k],whichitselfisaconservativerepresentationofthesetofoperationsthatkhasreceived.
safely be executed in the timestamp order and deleted. For liveness and efficiency, this
algorithm must use loosely synchronized real-time clocks (Section 4.4) for timestamps.
Otherwise,asitewithaveryslowtimestampcouldstalltheprogressofackvectorsofall
othersites.Moreover,evenasingleunresponsivesitecouldstalltheprogressofackvectors
onallothersites. Thisproblembecomesmorelikelyasthenumberofsitesincreases.
Timestampmatrices(TMs),ormatrixclocks,achieveasimilareffectusingamatrixof
timestamps[WuuandBernstein1984;Agrawaletal.1997]. Asiteiofanobjectstoresan
N×M matrixoftimestampsTM. TM[i]holdsi’svectorclock,VC. OtherrowsofTM
i i i i
holdSitei’sconservativeestimateofthevectorclocksofothersites.Thus,ifTM[k][j]=t,
i
thenSiteiknowsthatSitekhasreceivedoperationssubmittedatSite jwithtimestampsat
leastuptot.TMsareexchangedamongsitesandupdatedbytakingpair-wisemaxima,just
likeVCs. Withthisdefinition,onanysitei,alloperationssubmittedby jwithtimestamps
nolargerthanmin (TM[k][j])areguaranteedtobereceivedbyallsites. Unlikeack
k∈1...N i
vectors, TMs allow any scalar values to be used as timestamps, but they still suffer from
thelivenessproblem. AswewilldiscussinSection7.4.4, TMscanalsobeusedtopush
operationstoothersitesefficiently.
ESDS is also an operation-transfer system, but it uses non-deterministic syntactic pol-
icy to order concurrent operations. Each operation in ESDS is associated with a set of
operations that should happen before it, using a graph representation (Section 4.2). For
eachoperation,eachsiteindependentlyassignsatimestampthatisgreaterthanthosethat
happenbeforeit. Thefinaltotalorderofcommitmentisdefinedbytheminimaltimestamp
assigned to each operation. Thus, a site can commit an operation a when it receives a ’s
timestampsfromallothersites,andithascommittedalloperationsthathappenbeforea .
NeitherTSAEnorESDSperformsanyconflictdetectionorresolution. Theircommit-
ment protocols are thus simplified—they only need to agree on the set of operations and
theirorder.
5.5.3 Commitmentbyconsensus. Somesystemsuseconsensusprotocolstoagreeon
whichoperationstobecommittedorabortedandinwhichorder[Fischeretal.1985].
Theprimary-basedcommitmentprotocol,usedinBayou,designatesasinglesiteasthe
primarythatmakessuchdecisionsunilaterally[Petersenetal.1997]. Theprimaryorders
operationsastheyarrive(Section5.2.1)andcommitsoperationsbyassigningthemmono-
tonically increasing commit sequence numbers (CSN). The mapping between operations
and their CSNs is transmitted as a side effect of ordinary operation propagation process.
OthersitescommitoperationsintheCSNorderanddeletethemfromthelog. Noticethe
differencebetweenBayouandsingle-mastersystems.Inthelatter,thelonemastersubmits
ACMComputingSurveys,Vol.V,No.N,32005.

·
| 22  | Y.SaitoandM.Shapiro |     |     |
| --- | ------------------- | --- | --- |
updatesandcommitsthemimmediately. Othersitesmustsubmitchangesviathemaster.
Incontrast, Bayouallowsanysitetosubmitoperationsandpropagatethemepidemically
anduserstoseetheeffectsofoperationsquickly.
Denousesaquorum-basedcommitmentprotocol[Keleher1999]. Denoisapessimistic
system that yet exchanges operations epidemically. Deno decides the outcome of each
operation independently. A site that wishes to commit an operation runs a two-phase
weighted voting [Gifford 1979]. Upon receiving a commit request, a site votes in favor
of the update if the operation does not conflict locally with any prior operations. When
asiteobservesthatvotesforanoperationhavereachedamajority,itlocallycommitsthe
operationandsendsacommitnoticetoothersites. Simulationresultssuggestthattheper-
formanceofthisprotocolissimilartoaclassicsingle-masterschemeinthecommoncase
whennositehasfailed.EventhoughDenoisapessimisticsystem,theideaofcommitment
usingweightedvotingshouldapplytooptimisticenvironmentsaswell.
5.6 Summary
Eventualconsistencyinvolvesagreementovertheschedulingofoperations:whiletentative
stateofreplicasmightdiverge,sitesmusteventuallyagreeonthecontentsandorderingof
a committed prefix of their schedules. The following table summarizes the techniques
discussedinthissectionforthistask.
| Problem | Solution            | Advantages     | Disadvantages                     |
| ------- | ------------------- | -------------- | --------------------------------- |
|         | Syntactic           | Simple,generic | Unnecessaryconflicts              |
|         | Commutingoperations | Simple         | App-specific,limitedapplicability |
Ordering Canonicalordering Formal App-specific,limitedapplicability
|     | Operational | Formal | Complexity,limitedapplicability |
| --- | ----------- | ------ | ------------------------------- |
transformation
|     | Semanticoptimization | Expressive, | Complexity |
| --- | -------------------- | ----------- | ---------- |
powerful
|     | Syntactic | Simple,generic | Unnecessaryconflicts |
| --- | --------- | -------------- | -------------------- |
Conflicts
|     | Semantic | Reducesconflicts, | App-specific |
| --- | -------- | ----------------- | ------------ |
expressive
|            | Commonknowledge | Simple | Limitedapplicability |
| ---------- | --------------- | ------ | -------------------- |
| Commitment | Ackvector       | —      | Weakliveness         |
|            | Timestampmatrix | —      | Weakliveness         |
|            | Consensus       | —      | Complex              |
6. STATE-TRANSFERSYSTEMS
State-transfer systems restrict each operations to overwrite the entire object. They can
beconsidereddegenerateinstancesofoperation-transfersystems,buttheyallowforsome
interesting techniques—replicas can converge simply by receiving the newest contents,
skippinganyintermediateoperations.Section6.1discussesasimpleandpopulartechnique
called Thomas’s write rule. Sections 6.2 to 6.4 introduce several algorithms that enable
morerefinedconflictdetectionandresolution.
6.1 Replica-stateconvergenceusingThomas’swriterule
State-transfer systems need to agree only on which replica stores the newest contents.
Thomas’swriteruleisthemostpopularepidemicalgorithmforachievingeventualconsis-
tency[JohnsonandThomas1976; Thomas1979]. Here, eachreplicastoresatimestamp
that represents the “newness” of its contents (Section 4.4). Occasionally, a replica, say
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 23
——Per-object,persistentdatastructuresateachsite ——
varstate:Data
ts:Timestamp
——Calledwhenthesiteupdatestheobject——
procSubmitUpdate(newState)
state:=newState
ts:=CurrentTime()
——Receiverside:calledperiodically——
procReceiveUpdate(src)
srcTs:=Receivesrc’stimestamp.
ifts<srcTsthen
state:=Receivesrc’sstate.
ts:=srcTs
Fig.10.StatepropagationusingThomas’swriterule.Eachobjectkeepstimestamptsthatshowsthelasttimeit
wasupdated,andcontentsdata.AnupdateissubmittedbyasitebySubmitUpdate.EachsitecallsReceiveUpdate
occasionallyanddownloadsapeer’scontentswhenitsowntimestampisolderthanthepeer’s.
i, retrieves another replica j’s timestamp. If j’s timestamp is newer than i’s, i copies the
contents and timestamp from j to itself. Figure 10 shows the pseudocode of Thomas’s
write rule. This algorithm does not detect conflicts—it silently discards contents with
oldertimestamps. Systemsthatneedtodetectconflictswillusealgorithmsdescribedlater
inthissection.
WithThomas’swriterule,deletinganobjectrequiresspecialtreatment.Simplydeleting
a replica and its associated timestamp could cause an update/delete ambiguity. Suppose
thatSiteiupdatestheobjectcontents(timestampT),andSite jdeletestheobject(times-
i
tamp T ) simultaneously. Later, Site k receives the update from j and deletes the replica
j
andtimestampfromdisk. SitekthencontactsSitei. Thecorrectactionforkwouldbeto
createareplicawhenT >T ,andignoretheupdateotherwise;butSitekcannotmakethat
i j
decision,becauseitnolongerstoresthetimestamp.
Two solutions have been proposed to address the update/delete ambiguity. The first
solutionissimplytodemandanoff-line,humaninterventiontodeleteobjects,asinDNS
[Albitz and Liu 2001] and NIS [Sun Microsystems 1998]. The second solution is to use
so-called“deathcertificates”or“tombstones,”whichmaintainthetimestamps(butnotthe
contents) of deleted objects on disk. This idea is used by Fischer and Michael [1982],
Clearinghouse [Demers et al. 1987], Usenet [Spencer and Lawrence 1998], and Active
Directory[Microsoft2000].
6.2 Two-timestampalgorithm
The two-timestamp algorithm is an extension to Thomas’s write rule to enable conflict
detection[Grayetal.1996;BalasubramaniamandPierce1998]. Here,areplicaikeepsa
timestampthatshowsthenewnessofthedata,anda“previous”timestampthatshowsthe
lasttimetheobjectwasupdated.Aconflictisdetectedwhentheprevioustimestampsfrom
twositesdiffer. Figure11showsthepseudocode. Thesamelogicissometimesalsoused
byoperation-transfersystemstodetectconflicts[Terryetal.1995].
Thedownsideofthistechniqueisthatitmaydetectfalseconflictswithmorethantwo
replicas,asshowninFigure12. Thus,itisfeasibleonlyinsystemsthatemployfewsites
andexperienceconflictsinfrequently.
6.3 Modified-bitalgorithm
Themodified-bitalgorithm,usedinthePalmPDA,isasimplificationofthetwo-timestamp
algorithm[PalmSource2002]. Itworksonlywhenthesametwositessynchronizerepeat-
ACMComputingSurveys,Vol.V,No.N,32005.

·
24 Y.SaitoandM.Shapiro
——Per-object,persistentdatastructuresateachsite ——
varstate:Data
ts,prevTs:Timestamp
——Calledwhenthesiteupdatestheobject——
procSubmitUpdate(newState)
state:=newState
ifthisisthefirstupdatesincethelastsynchronizationthen
prevTs:=ts
ts:=CurrentTime()
——Thisprocedurerunsonbothsideswhentwositesexchangetheirstate——
procSynchronize(src)
srcTs,srcPrevTs:=Receivesrc’stsandprevTs.
ifprevTs6=srcPrevTsthen
Aconflictdetected;resolve
elifts<srcTsthen
//Theobjectisupdatedonlyonsrc
state:=Receivesrc’s state.
ts:=srcTs
Fig.11.Operationpropagationandconflictdetectionusingthetwo-timestampalgorithm.Anupdateissubmitted
locallybySubmitUpdate. Twositessynchronizeoccasionally, andtheybothcallSynchronizetoretrievethe
timestampsanddataofthepeer.
(1) Initial (2) Site i (3) Site i (4) Site i tries to
state. updated. synchronizes synchronize with k.
and sends Conflict detected,
i update to j because Pi (cid:3)Pj
Ti=0, Ti=1, Ti=1,
Pi=0 Pi=0 Pi=1
j
Tj=0, Tj=1,
Pj=0 Pj=1
k
Tk=0,
Pk=0
Fig.12.Anexampleoferroneousconflictdetectionusingthetwo-timestampalgorithm.Alighteningboltshows
thesubmissionofanoperation,andanarrowshowsbidirectionaloperationpropagation. Tx showsthecurrent
timestampofreplicax(notedtsinFigure11),andPxshowitsprevioustimestamp(i.e.,prevTs).Initiallyin(1),
thecontentsofthereplicasareidentical,withTx=Px=0forallthereplicas. Instep(4),Replicasiandktry
tosynchronize. Thealgorithmincorrectlydetectsaconflict,becausePi(=2)6=Pk (=0). Inreality,Replicakis
strictlyolderthanReplicai.
edly.
Palm organizes user data as a set of database records. It associates with each record a
setofbitsthattellswhethertherecordismodified,deleted,orarchived(i.e.,tobedeleted
fromthePDAbutkeptseparatelyonthePC).
Palmemploystwomechanisms,calledfastandslowsynchronization,toexchangedata
betweenaPDAandaPC.FastsynchronizationhappensinthecommoncasewhereaPDA
is repeatedly synchronized with a particular PC. Here, each side transfers items with the
“modified” bit set. A site inspects the attribute bits of each record and decides on the
reconciliation outcome—for instance, if it finds the “modified” bit set on both PDA and
PC,itmarksthemasinconflict. Thisuseof“modified”bitcanbeseenasavariationof
two-timestampalgorithm: itreplacesT withabooleanflagwhichissetafterareplicais
i
modifiedandclearedafterthereplicassynchronize.
WhenthePDAisfoundtohavesynchronizedwithadifferentPCbefore,themodified-
bitalgorithmcannotbeused. Twosidesthenreverttotheslowmode,inwhichbothignore
ACMComputingSurveys,Vol.V,No.N,32005.

·
|     |         |          |     |     |           | Optimisticreplication |     | 25  |
| --- | ------- | -------- | --- | --- | --------- | --------------------- | --- | --- |
|     | [{}|{}] | [{}|{1}] |     |     | [{1}|{1}] |                       |     |     |
|     | i I     |          | I   |     | I         |                       |     |     |
|     | 1       |          | 2   |     | 3         |                       |     |     |
fork update
join
|     | j        | J   |             | J    |     | J   | [{1}|{1,01}] |     |
| --- | -------- | --- | ----------- | ---- | --- | --- | ------------ | --- |
|     |          | 1   |             | 2    |     | 3   |              |     |
|     | [{}|{0}] |     | fork [{}|{0 | 1 }] |     |     |              |     |
join
update
|     | k   |           | K   |     | K           |     | K [{1,00}|{1,00,01}] |     |
| --- | --- | --------- | --- | --- | ----------- | --- | -------------------- | --- |
|     |     |           | 1   |     | 2           |     | 3                    |     |
|     |     | [{}|{00}] |     |     | [{00}|{00}] |     | =[{1,00}|{1,0}]      |     |
=[{1,00}|{}]
Fig.13. Exampleoftheuseofversiontimestamps(VTs). AnobjectstartsasasinglereplicaI1withaVTof
[{}|{}]. ItisforkedintotworeplicasI2andJ1. Siteiupdatesthereplica,whichbecomesI3. Mergingreplicas
I3andJ2detectsnoconflict,asI3dominatesJ2,asapparentfromthefactthat{1}⊃{}.Incontrast,concurrent
updatesaredetectedwhenmergingreplicasJ3 andK2,asneitheroftheupd-ids,{00}and{1},subsumesthe
other.
the modified bits and exchange the entire database contents. Any record with different
valuesatthetwositesisflaggedtobeinconflict.
6.4 Vectorclocksandtheirvariations
Vectorclocksaccuratelydetectconcurrentupdatestoanobject(Section4.3).Severalstate-
transfersystemsusevectorclockstodetectconflicts,defininganytwoconcurrentupdates
to the same object to be in conflict. Vector clocks used for this purpose are often called
version vectors (VV). LOCUS introduced VVs and coined the name [Parker et al. 1983;
Walkeretal.1983]. OthersystemsinthiscategoryareCoda[KistlerandSatyanarayanan
1992; Kumar and Satyanarayanan 1995], Ficus [Reiher et al. 1994], and Roam [Ratner
1998].
A replica of an object at Site i carries a vector clock VV . VVs for different objects
i
areindependentfromoneanother. VV i [i]showsthelasttimeanupdatetotheobjectwas
submittedati,andVV [j]indicatesthelastupdatetotheobjectsubmittedatSite jthatSite
i
ihasreceived. TheVVisexchanged,updatedandcomparedaccordingtotheusualvector
clockalgorithm(Section4.3). Conflictsaredetectedbetweentwositesiand jasfollows:
| (1) IfVV | =VV ,thenthereplicashavenotbeenmodified. |     |     |     |     |     |     |     |
| -------- | ---------------------------------------- | --- | --- | --- | --- | --- | --- | --- |
|          | i j                                      |     |     |     |     |     |     |     |
(2) Otherwise, if VV dominates VV , then i is newer than j; that is, Site i has applied
|     |     | i   |     | j   |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
all the updates that Site j has, and more. Site j copies the contents and VV from i.
| Symmetrically,ifVV |     | dominatesVV |     | ,thecontentsandVVarecopiedfrom |     |     |     | jtoi. |
| ------------------ | --- | ----------- | --- | ------------------------------ | --- | --- | --- | ----- |
|                    |     | j           |     | i                              |     |     |     |       |
(3) Otherwise,theoperationsareconcurrent,andthesystemmarksthemtobeinconflict.
Unlikethetwo-timestampalgorithm,VVsareaccurate: aVVprovablydetectsconcur-
rent updates if and only if real concurrency exists [Fidge 1988; Mattern 1989]. The fol-
lowingtwosectionsdescribedatastructureswithsimilarpowertoVVsbutwithdifferent
representations.
6.4.1 Version timestamps. Version timestamps (VTs) are a technique used in the
Panasync file replicator [Almeida et al. 2002; Almeida et al. 2000]. They adapt VVs to
environmentswithfrequentreplicacreationandremoval. VTsupportsonlythreekindsof
operations:forkcreatesanewreplica,updatemodifiesthereplica,andjoin(i,j)mergesthe
contents of replica i into j, destroying i. The idea behind VTs is to create a new replica
ACMComputingSurveys,Vol.V,No.N,32005.

·
26 Y.SaitoandM.Shapiro
|     |     |     | H :0 |     |     |
| --- | --- | --- | ---- | --- | --- |
0,i
| H :0   | H   | :0     | H :0 |     |     |
| ------ | --- | ------ | ---- | --- | --- |
| 0,i    | 0,i | update | 1,i  |     |     |
| i I    |     | I      | I    |     |     |
| 1 fork |     | 2      | 3    |     |     |
H :0
|     |      |        | join | 0,i   |        |
| --- | ---- | ------ | ---- | ----- | ------ |
| j   | J    |        |      |       |        |
|     | 1    |        | J 2  | J H   | :0     |
|     | fork |        |      | 3 1,i |        |
| H   | :0   |        | H :0 |       |        |
|     | 0,i  |        | 0,i  |       |        |
|     |      | update |      | join  |        |
| k   |      | K      | K    |       | K H :0 |
|     |      | 1      | 2    |       | 3 0,i  |
H :0
|     |     | H 0,i :0 | H :0 |     | 1,i H 0,k :0 |
| --- | --- | -------- | ---- | --- | ------------ |
0,i
|     |     |     | H :0 |     | H :0 |
| --- | --- | --- | ---- | --- | ---- |
|     |     |     | 0,k  |     | 1,k  |
Fig.14.Exampleoftheuseofhashhistories(HHes)usingthesamescenarioasFigure13.Theobjectstartsasa
singlereplicaoniwithaHHofH0,whereH0isahashofthecurrentcontentsoftheobject.Afteranupdateati,
theHHbecomesH0-H1byappendingthenewcontentshash.Theresultofmergingandresolvingtwoconflicting
updates(K3)isrepresentedintheHHbycreatinganacyclicgraphasshown.
identifierontheflyatforktime,andtomergeVTsintoacompactformatjointime. Fig-
ure13showsanexampleofVTs.
TheVTofareplicaisapair[upd-id|hist-id]. Hist-id isasetofbitstringsthatuniquely
identifies the history of fork and join operations the replica has seen. An object is first
createdwithahist-id of{}. Afterforking,oneofthereplicasappends0toeachbitstring
in its hist-id, and the other appends 1. Thus, forking a replica with the hist-id of {00,1}
yields {000,10} and {001,11}. After joining, the new hist-id becomes the union of the
originaltwo, exceptthatwhenthesetcontainstwobitstringsoftheformx0andx1, then
they can be merged and contracted to just x. Thus, the result of joining replicas {0} and
{1}is{};theresultofjoining{001,10}and{11}is{001,1}.Ontheotherhand,anupd-id
simplyrecordsthehistory-idofthereplicaatthemomentwhenitwaslastmodified.
VTsofreplicasofanobjectpreciselycapturethehappens-beforeandconcurrencyrela-
tionsbetweenthem:Siteihasseenallupdatesappliedto jifandonlyif,foreachbitstring
xin j’supd-id,abitstringyexistsini’supd-id,suchthatxisaprefixofy(∃z,y=xz).
6.4.2 Hash histories. Hash histories (HHs) [Kang et al. 2003] are a variation of the
graph representation introduced in Section 4.2. The basic ideas behind HHs are to (1)
recordcausaldependenciesdirectlybyhowanobjecthasbranched,updated,andmerged,
and(2)touseahashofthecontents(e.g.,MD5),ratherthantimestamps,torepresentthe
state of a replica. Figure 14 shows an example. While the size of a HH is independent
of the number of master replicas, it grows indefinitely with the number of updates. The
authorsuseasimpleexpiration-basedpurgingtoremoveoldHHentries,similartotheone
describedinSection6.5.
6.5 Cullingtombstones
We mentioned in Section 6.1 a system that retains a tombstone to mark a deleted object.
Thisisinfacttrueforanystate-transfersystem—forinstance,whenusingVVs,theVVis
retainedasatombstone. Unlessmanagedcarefully,thespaceoverheadoftombstoneswill
grow indefinitely. In most systems, tombstones are erased unilaterally at each site after
a fixed period, long enough for most updates to complete propagation, but short enough
to keep the space overhead low; e.g., two weeks [Spencer and Lawrence 1998; Kistler
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 27
and Satyanarayanan 1992; Microsoft 2000]. This technique is clearly unsafe (e.g., a site
rebootingafterbeingdownforthreeweeksmaysendspuriousupdates),butworkswellin
practice.
Clearinghouse[Demersetal.1987]lowersthespaceoverheaddrasticallyusingasimple
technique. InClearinghouse,tombstonesareremovedfrommostsitesaftertheexpiration
period, but are retained on a few designated sites indefinitely. When a stale operation
arrivesaftertheexpirationperiod,somesitesmayincorrectlyapplythatoperation. How-
ever,thedesignatedsiteswilldistributeanoperationthatundoestheupdateandreinstalls
tombstonesonallothersites.
Some systems rely on a form of commitment algorithm to delete tombstones safely.
RoamandFicususeatwo-phaseprotocoltoensurethateverysitehasreceivedanoperation
before purging the corresponding tombstone [Guy et al. 1993; Ratner 1998]. The first
phase informs a site that all sites have received the operation. The second phase ensures
thatallsitesreceivethe“deletethetombstone”request. Asimilarprotocolisalsousedin
Porcupine[SaitoandLevy2000]. Thedownsideofthesetechniquesisliveness: allsites
mustbealiveforthealgorithmtomakeprogress.
6.6 Summary
This section has focused on the specific case of state-transfer optimistic replication sys-
tems. Comparedtooperation-transfersystems,theseareamenabletosimplermanagement
algorithms,assummarizedinthefollowingtable.
| Problem | Solution          | Advantages | Disadvantages |
| ------- | ----------------- | ---------- | ------------- |
|         | Thomas’swriterule | Simple     | Lostupdates   |
Eventual
| consistency, | Twotimestamps | Simple | False-positiveconflicts |
| ------------ | ------------- | ------ | ----------------------- |
conflict man-
|     | Modifiedbits | Simple,spaceefficient | False-positiveconflicts |
| --- | ------------ | --------------------- | ----------------------- |
agement.
|     | Vectorclock | Accurateconflictdetection | Complexity,space |
| --- | ----------- | ------------------------- | ---------------- |
|     | Expire      | Simple                    | Unsafe           |
Tombstone
| management. | Keeponlyat       | Simple | Overheadgrows             |
| ----------- | ---------------- | ------ | ------------------------- |
|             | designatedsites. |        | indefinitelyatthesesites. |
|             | Commit           | Safe   | Complexity,liveness       |
7. PROPAGATINGOPERATIONS
Thissectionexaminestechniquesforpropagatingoperationsamongsites.Ana¨ıvesolution
existsforthisproblem: everysiterecordsoperationsinalog,anditoccasionallysendsits
entire log contents to a random other site. Given enough time, this algorithm eventually
propagates all operations to all sites, even in the presence of incomplete links and tem-
porary failures. Of course, it is expensive and slow to converge. Algorithms described
hereafter improve efficiency by controlling when and which sites communicate, and by
reducing the amount of data sent between the sites. Section 7.1 describes a propagation
technique using vector clocks for operation-transfer systems. Section 7.2 discusses tech-
niques for state-transfer systems to allow for identifying and propagating only parts an
objectthathavebeenactuallymodified. Controllingcommunicationtopologyisdiscussed
| inSection7.3. | Section7.4discussesvarioustechniquesforpush-basedpropagation. |     |     |
| ------------- | ------------------------------------------------------------- | --- | --- |
ACMComputingSurveys,Vol.V,No.N,32005.

·
28 Y.SaitoandM.Shapiro
——Per-sitedatastructures——
typeOperation=record
| issuer:SiteID //Thesitethatsubmittedtheoperation. |     |     |     |
| ------------------------------------------------- | --- | --- | --- |
| ts:Timestamp //Thetimestampatthemomentofissuance. |     |     |     |
op:Operation//Actualoperationcontents
| varvc:array[1..M]ofTimestamp | //Thesite’svectorclock. |     |     |
| ---------------------------- | ----------------------- | --- | --- |
log:setofOperation//Thesetofoperationthesitehasreceived.
| ——Calledwhensubmittinganoperation | ——  |     |     |
| --------------------------------- | --- | --- | --- |
procSubmitOperation(update)
vc[myself]:=vc[myself]+1
log:=log∪{newOperation(issuer=myself,ts=vc[myself],op=update)}
——Senderside:Sendoperationsfromthissitetositedest——
procSend(dest)
destVC:=Receivedest’svectorclock.
| upd:={u∈log | | u.ts>destVC[u.issuer]} |     |     |
| ----------- | ------------------------ | --- | --- |
Sendupdtodest.
——Receiverside:CalledviaSend()——
procReceive(upd)
foru∈upd
Applyu.
vc[u.issuer]:=max(vc[u.issuer],u.ts)
log:=log∪upd
Fig.15. Operationpropagationusingvectorclocks. Thereceivingsitefirstcallsthesender’s“Send”procedure
andpassesitsvectorclock. Thesendingsitesendsupdatestothereceiver,whichprocessesthemin“Receive”
procedure.
i’s vector  i’s log
| clock (2) Site i issues update (cid:4)3. |                              |     |     |
| ---------------------------------------- | ---------------------------- | --- | --- |
| 2 (cid:4)1 (cid:4)2                      | 3 (cid:4)1 (cid:4)2 (cid:4)3 |     |     |
| (cid:5)1 (cid:5)2                        | 2 (cid:5)1 (cid:5)2          |     |     |
2
| i 1 (cid:6)1 | 1 (cid:6)1 |     |     |
| ------------ | ---------- | --- | --- |
(cid:4)1 (cid:4)2
| 2                   | 3 (cid:4)1 (cid:4)2 (cid:4)3 | 3 (cid:4)1 (cid:4)2 (cid:4)3 |     |
| ------------------- | ---------------------------- | ---------------------------- | --- |
| 2 (cid:5)1 (cid:5)2 | 2 (cid:5)1 (cid:5)2          | 3 (cid:5)1 (cid:5)2 (cid:5)3 |     |
| 1 (cid:6)1          | 1 (cid:6)1                   | 1 (cid:6)1                   |     |
j
(3) Site j receives
| (cid:4) (cid:4)     |                   | (4) Site j        | 3 (cid:4) (cid:4) (cid:4)    |
| ------------------- | ----------------- | ----------------- | ---------------------------- |
| 2 1 2               | (cid:4)3. from i. | issues            | 1 2 3                        |
| 2 (cid:5)1 (cid:5)2 |                   |                   | 3 (cid:5)1 (cid:5)2 (cid:5)3 |
| 1 (cid:6)1          |                   | update (cid:5)3.  | 1 (cid:6)1                   |
k
| (1) Initial state |     |     | (5) Site k receives  |
| ----------------- | --- | --- | -------------------- |
(cid:4)3 and (cid:5)3 from j.
Fig.16.Exampleofoperationpropagationusingvectorclocks.Symbolsa ,b andg
showupdatessubmittedati,
j,andk,respectively.Shadedrectanglesshowchangesateachstep.
7.1 Operationpropagationusingvectorclocks
Many operation-transfer systems use vector clocks (Section 4.3) to exchange operations
optimallybetweensites[Golding1992;Ladinetal.1992;Adly1995;Feketeetal.1997;
Petersenetal.1997].Here,aSiteimaintainsvectorclockVC. VC[i]containsthenumber
|     |     | i   | i   |
| --- | --- | --- | --- |
ofoperationssubmittedatSitei,whereasVC[j]showsthetimestampofthelastoperation,
i
9
submittedatSite j,receivedbySitei. ThedifferencebetweentwoVCsshowsprecisely
thesetofoperationsthatneedtobeexchangedtomakethesitesidentical.Figure15shows
thepseudocodeofthealgorithm,andFigure16showsanexample.
TopropagateoperationsfromSiteitoSite j,ifirstreceives j’svectorclock,VC . For
j
9 Alternatively, onecouldstorereal-timeclockvaluesinsteadofcounters, asdoneinTSAE[Golding1992].
VCi[j]wouldshowthetimestampofthelatestoperationreceivedbySiteisubmittedatSite j.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 29
everyksuchthatVC[k]>VC [k],SiteisendstoSite jthoseoperationssubmittedatSite
i j
k that have timestamps larger than VC [k]. This process ensures that Site j receives all
j
operationsstoredonSiteiandthatSite jdoesnotreceivethesameoperationtwice.After
swappingtherolesandlettingSiteireceiveoperationsfromSite j,thetwositeswillhave
receivedthesamesetofoperations.
7.2 Efficientpropagationinstate-transfersystems
Instate-transfersystems,updatepropagationisusuallydonebysendingtheentirereplica
contents to another site, which becomes inefficient as the object size grows. We review
severaltechniquesforalleviatingthisproblemwithoutlosingthesimplicityofstatetrans-
fer.
7.2.1 Hybrid state and operation transfer. Some systems use a hybrid of state and
operation transfer. Here, each site keeps a short history of past updates (“diff”s) to the
object along with past timestamps recording when these updates were applied. When
updatinganotherreplicawhosetimestampisrecordedinthehistory,itsendsonlythesetof
diffsneededtobringituptodate. Otherwise(i.e.,ifthereplicaistoooldorthetimestamp
is not found in the history), it sends the entire object contents. Examples include DNS
incrementalzonetransfer[AlbitzandLiu2001],CVS[Cederqvistetal.2001;Vesperman
2003],andPorcupine[SaitoandLevy2000].
7.2.2 Hierarchical object division and comparison. Some systems divide an object
into smaller sub-objects. One such technique is to structure an object into a tree of sub-
objects (which happens naturally for a replicated file system) and let each intermediate
noderecordthetimestampofthenewestupdatetoitschildren[CoxandNoble2001;Kim
etal.2002]. ItthenappliesThomas’swriteruleonthattimestampandwalksdownthetree
progressivelytonarrowdownchangestothedata. ArchivalIntermemoryusesavariation
of this idea, called range synchronization, to reconcile a key-value database [Chen et al.
1999]. Toreconciletwodatabasereplicas,thereplicasfirstcomparethecollision-resistant
hashvalues(e.g., MD5, SHA1, orRabin’sfingerprints[Rabin1981])ofbothreplicas. If
they do not match, then each replica splits the database into multiple parts using a well-
knowndeterministicfunction,forinstanceintotwosub-databases,onewithkeysstarting
withlettersA-L,andtheotherstartingwithlettersM-Z.Itthenperformshashcomparison
recursivelytonarrowdownthediscrepanciesbetweenthetworeplicas.
Somesystemsexplicitlymaintainthelistofthenamesofmodifiedsub-objectsanduse
a data structure similar to vector clocks to detect the set of sub-objects that are modified
[Microsoft2000; Rabinovichetal.1996]. Theyresembleoperation-transfersystems,but
differ in several essential aspects. First, instead of an unbounded log, they maintain a
(usually small) list of modified objects. Second, they still use Thomas’s write rule to
serializechangestoindividualsub-objects.
7.2.3 Useofcollision-resistanthashfunctions. Thislineoftechniquesalsodivideob-
jectsintosmallerchunks,buttheyaredesignedforobjectsthatlackanaturalstructure,e.g.,
largebinaryfiles.Inthesimplestform,thesendingsidedividestheobjectintochunks,and
sendstheothersideacollision-resistanthashvalueforeachchunk. Thereceiverrequests
thecontentsofeverychunkfoundtobemissingonthereceiverside. Thisscheme,how-
ever,failstoworkefficientlywhenbytesareinsertedordeletedinthemiddleoftheobject.
Toavoidthisproblem,thersyncfilesynchronizationutilitysendshashesintheopposite
ACMComputingSurveys,Vol.V,No.N,32005.

·
30 Y.SaitoandM.Shapiro
direction [Tridgell 2000]. The receiving side first sends the hash of each chunk of its
replicatothesendingside. Thesenderthenexhaustivelycomputesthehashvalueofevery
possiblechunkateverybytepositioninthefile,discoversdatathataremissingontheother
side,andpushesthose.
TheLow-BandwidthFileSystem(LBFS)dividesobjectsatboundariesdefinedbycon-
tentratherthanafixedchunksize[Muthitacharoenetal.2001].Thesendingsidefirstcom-
putesahashofeverypossible48-bytesequenceintheobject(Rabin’sfingerprints[Rabin
1981] can be used efficiently for this purpose). Each 48-byte sequence that hashes to a
particular (well-known but arbitrary) value constitutes a chunk boundary. LBFS sender
thensendsthehashofeachchunktothereceiver. Thereceiverrequestsonlythosechunks
thatitismissing. LBFSreportsupto90%reductioninbandwidthrequirementsintypical
scenarios,overbothUnixandWindowsfilesystems. SpringandWetherall[2000]propose
asimilarapproachforcompressingnetworktrafficoverslowlinks.
7.2.4 Set-reconciliation approach. Minsky et al. [2001] propose a number-theoretic
approach for minimizing the transmission cost for state-transfer systems. This algorithm
isapplicablewhenthestateofareplicacanberepresentedasasetoffixed-sizebitstrings,
e.g.,hashvalues. Totransmitanobject,thesenderappliesspecialpolynomialfunctionsto
itssetofbitstrings,andsendsthevaluestothereceiver. Thereceiversolvestheequation
toderiveattheexactsetofbitstringsitislacking.
This basic algorithm assumes that the size of the difference between the two sets, D,
isknownapriori. IthasnetworkingoverheadofO(D)andcomputationalcomplexityof
O(D3). IfDisnotknownapriori,thesitescanstillstartfromasmallguessofD,sayD0.
The algorithm can bound the probability of giving false answers given D and D0—thus,
onecangraduallyincreasethevalueofD0 untiltheprobabilityofanerrorisaslowasthe
user desires. Minsky [2002] proposes a variation of this algorithm, in which the system
usesafixedD0.Thesystemrecursivelypartitionsthesetsusingawell-knowndeterministic
function until the D0 successfully merges the sub-objects. This algorithm incurs slightly
highernetworkingoverhead,butonlyO(D0)computationaloverhead.
7.3 Controllingcommunicationtopology
Weintroduced,inSection3.1,theargumentbyGrayetal.[1996]thatmulti-mastersystems
do not scale well, because the conflict rate increases at O(M2). To derive this result, the
authors make two key assumptions: that objects are updated equiprobably by all sites,
andthatsitesexchangeupdateswithuniform-randomlychosensites. Theseassumptions,
however, do not necessarily hold in practice. First, simultaneous writes to the same data
item are known to be rare in many applications, in particular file systems [Ousterhout
et al. 1985; Baker et al. 1991; Vogels 1999]. Second, as we discuss next, choosing the
right communication topology and proactively controlling the flow of data will improve
propagationspeedandreduceconflicts.
Theperceivedrateofconflictscanbereducedbyconnectingreplicasinspecificways.
WhereasarandomcommunicationtopologytakesO(logN)timetopropagateaparticular
update to all sites [Hedetniemi et al. 1988; Kempe et al. 2001], specific topologies can
dobetter. AstarshapepropagatesinO(1),forinstance. Anumberofactualsystemsare
indeedorganizedwithacentralhubactingasasortofclearinghouseforupdatessubmitted
byothermasters. CVSisawell-knownexample(Section2.5);seealsoWangetal.[2001]
andRatner[1998].
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 31
Two-tierreplicationisageneralizationofthestartopology[Grayetal.1996;Kumarand
Satyanarayanan 1993]. Here, sites are split into mostly connected “core sites” and more
weaklyconnected“mobilesites”. Thecoresitesoftenuseapessimisticreplicationalgo-
rithmtoremainconsistentwitheachother,butamobilesiteusesoptimisticreplicationand
communicatesonlywiththecore. Notethedifferencebetweensingle-mastersystemsand
two-tiermulti-mastersystems.Thelattertypesofsystemsstillneedtosolvethechallenges
of multi-master optimistic replication systems—e.g., operation scheduling, commitment,
and conflict resolution—but they scale better, at the cost of sacrificing the flexibility of
communication.
Several other topologies are used in real-world systems. Roam connects core replicas
in a ring and hangs other replicas off them [Ratner 1998]. Many choose a tree topology,
whichcombinesthepropertiesofboththestarandrandomtopologies[Chankhunthodetal.
1996;Yinetal.1999;Adly1995;JohnsonandJeong1996]. UsenetandActiveDirectory
often connect sites in a tree or ring structure, supplemented by short-cut paths [Spencer
andLawrence1998;Microsoft2000].
In practice, choosing a topology involves a trade-off between propagation speed, load
balancingandavailability[Wangetal.2001]. Atoneendofthespectrum,thestartopol-
ogy boasts quick propagation, but its hub site could become overloaded, slowing down
propagationinpractice;itisalsoasinglepointoffailure. Arandomtopology,ontheother
hand,isslowerbuthasextremelyhighavailabilityandbalancesloadwellamongsites.
7.4 Push-transfertechniques
Sofar,wehaveassumedthatsitescouldsomehowfigureoutwhentheyshouldstartprop-
agating to one another. This is not too difficult in services that rely on explicit manual
synchronization(e.g.,PDA),oronesthatrelyonoccasionalpollingforasmallnumberof
objects(e.g.,DNS).Inothercasesitisbettertopush,i.e.,tohaveasitewithanewopera-
tionproactivelydeliverittoothers. Thiscanreducethepropagationdelayandeliminates
thepollingoverhead.
7.4.1 Blind flooding. Flooding is the simplest pushing scheme. Here, a site with a
newoperationblindlyforwardsittoitsneighbors. ThereceivingsiteusesThomas’swrite
rule or vector clocks to filter out duplicates. This technique is used in Usenet [Spencer
and Lawrence 1998], Active Directory [Microsoft 2000], and Porcupine [Saito and Levy
2000].
Floodinghasanobviousdrawback: itsendsduplicateswhenasitecommunicateswith
manyothersites[Demersetal.1987]. Thisproblemcanbealleviatedbyguessingwhether
aremotesitehasanoperation. Wereviewsuchtechniquesnext.
7.4.2 Link-state monitoring techniques. Rumor mongering and directional gossiping
aretechniquesforsuppressingduplicateoperations[Demersetal.1987;LinandMarzullo
1999]. Rumor mongering starts like blind flooding, but each site monitors the number
of duplicates it has received for each operation. It stops forwarding an operation when
thenumberofduplicatesexceedsalimit. Indirectionalgossiping,eachsitemonitorsthe
number of distinct “paths” operations have traversed. An inter-site link not shared by
manypathsislikelytobemoreimportant,becauseitmaybethesolelinkconnectingsome
site. Thus, the site sends operations more frequently to such links. For links shared by
manypaths,thesitepusheslessfrequently,withahopethatothersiteswillpushthesame
operationviadifferentpaths.
ACMComputingSurveys,Vol.V,No.N,32005.

·
32 Y.SaitoandM.Shapiro
——Globalpersistentdatastructuresoneachsite——
var log:SethOperationi//Thesetofoperationsthesitehasreceived.
tm:array[1..N][1..M]ofTimestamp//Thesite’stimestampmatrix.
——Senderside:sendoperationstositedest——
procSend(dest)
ops:=f
for1≤i≤M
iftm[dest][i]<tm[myself][i]then
ops:=ops∪{u∈log | u.issuer=i and u.ts>tm[dest][i]}
Sendopsandtmtodest.
tm[dest]:=PairWiseMax(tm[myself],tm[dest])
——Receiverside:calledinresponsetoSend——
procReceive(ops,tmsrc)
foru∈ops
iftm[myself][u.issuer]<u.timestampthen
log:=log∪{u}
Applyutothesite
for1≤i≤N,1≤j≤M
tm[i][j]:=max(t[i][j],tmsrc[i][j])
Fig.17. Sitereconciliationusingtimestampmatrices.
Both techniques are heuristic and may wrongly throttle propagation for a long time.
For reliable propagation, the system occasionally must resort to plain flooding to flush
operations that have been omitted at some sites. Simulation results, however, show that
reasonableparametersettingscannearlyeliminateduplicateoperationswhilekeepingthe
reliabilityofoperationpropagationverycloseto100%.
7.4.3 Multicast-basedtechniques. Multicasttransportprotocolscanbeusedforpush
transfer. These protocols solve the efficiency problem of flooding by building spanning
treesofsites,overwhichdataaredistributed. Theycannotbeapplieddirectlytooptimistic
replication,however,becausetheyare“besteffort”services—theymayfailtodeliveroper-
ationswhensitesandnetworklinksareunreliable.Examplesofmulticastprotocolsinclude
IPmulticast[Deering1991],SRM[Floydetal.1997],XTP[XTP2003]andRMTP[Paul
etal.1997].
MUSEisanearlyattempttodistributeUsenetarticlesoveranIPmulticastchannel[Lidl
etal.1994]. Itsolvesthelackofreliabilityofmulticastbylayingitontopoftraditional
blind-floodingmechanism—i.e., mostofthearticleswillbesentviamulticast, andthose
that dropped through are send slowly but reliably by flooding. Work by Birman et al.
[1999]andSun[2000]alsousemulticastinthecommoncaseandpoint-to-pointepidemic
propagationasafall-backmechanism.
7.4.4 Timestampmatrices. Atimestampmatrix(TM),discussedinSection5.5.2,can
alsobeusedtoestimatetheprogressofothersites,andpushonlythoseoperationsthatare
likelytobemissing[WuuandBernstein1984;Agrawaletal.1997]. Figure17showsthe
pseudocode for propagation using TMs. The operation propagation procedure, shown in
Figure 17, is similar to the one using vector clocks (Section 7.1). The only difference is
thatthesendingSiteiusesTM[j]asaconservativeestimateofSite j’svectorclock,rather
i
thanobtainingthevectorfrom j.
7.5 Summary
This section focused on efficient propagation techniques. After briefly discussing oper-
ation propagation, we mainly described techniques for improving the efficiency of state
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 33
| propagationinthepresenceoflargeobjects. |                   | Ourfindingsaresummarizedhereafter. |                  |
| --------------------------------------- | ----------------- | ---------------------------------- | ---------------- |
| Systemtype                              | Solution          | Advantages                         | Disadvantages    |
| Operation                               | Whole-logexchange | Simple                             | Duplicateupdates |
transfer Vectorclocks Avoidsduplicates O(M)spaceoverhead;complex
whensitescomeandgo.
|          | Hybrid            | –                   | Overheadofmaintainingdiffs |
| -------- | ----------------- | ------------------- | -------------------------- |
| State    | Objectdivision    | –                   | App-specific,limited       |
| transfer |                   |                     | applicability.             |
|          | Hashfunction      | Supportsanydatatype | Computationalcost          |
|          | Setreconciliation | Efficient           | Computationalcost,limited  |
applicability
|     | Blindflooding | –   | Duplicateupdates |
| --- | ------------- | --- | ---------------- |
Push
|     | Link-statemonitoring | –   | Somewhatunreliable |
| --- | -------------------- | --- | ------------------ |
transfer
|     | Timestampmatrix | Efficient | O(M2)spaceoverhead;complex |
| --- | --------------- | --------- | -------------------------- |
whensitescomeandgo.
8. CONTROLLINGREPLICADIVERGENCE
Thealgorithmsdescribedsofararedesignedtoimplementeventualconsistency—i.e.,con-
sistencyuptosomeunknownmomentinthepast. Theyofferlittlecluetousersregarding
thequalityofreplicacontentsatthepresentpointintime. Manyservicesdofinewithsuch
a weak guarantee. For example, replica inconsistency in Usenet is no worse than prob-
lems inherent in Usenet, such as duplicate article submission, misnamed newsgroups, or
out-of-orderarticledelivery[SpencerandLawrence1998].
Manyapplications,however,wouldbenefitiftheservicecanguaranteesomethingabout
the quality of replica contents, e.g., that users will never read data that is more than X
hours old. This section reviews several techniques for making such guarantees. These
techniques work by estimating replica divergence and prohibiting accesses to replicas if
the estimate exceeds a threshold. Thus, they are not a panacea, as they improve data
quality by prohibiting accesses to data and decreasing availability [Yu and Vahdat 2001;
YuandVahdat2002].
8.1 Enforcingread/writeordering
One of the most common complaints with eventual consistency is that a user sometimes
sees the value of an object “move backward” in time. Consider a replicated password
database[Birrelletal.1982; Terryetal.1994]. Ausermaychangethepasswordonone
siteandlaterfailtologinfromanothersiteusingthenewpassword,becausethechange
has not reached the latter site. Such a problem can be solved by restricting when a read
operationcantakeplace.
8.1.1 Explicitdependencies. ThesolutionsuggestedbyLadinetal.[1990]andLadin
et al. [1992] is to let the user define the causal relationship explicitly: a read operation
specifiesthesetofupdateoperationsthatmustbeappliedtothereplicabeforethereadcan
proceed. Thisfeatureiseasilyimplementedusingoneoftherepresentationsofhappens-
beforeintroducedinSection4. Ladinetal.[1990]representbothareplica’sstateandan
operation’s dependency using a vector clock. The system delays the operation until the
operation’sVCdominatesthereplica’sVC.ESDSfollowsthesameidea,butinsteaduses
ACMComputingSurveys,Vol.V,No.N,32005.

·
34 Y.SaitoandM.Shapiro
TableII. Implementationofsessionguarantees. Forexample,toimplementRYW,thesystem
updatesauser’ssessionwhentheusersubmitsawriteoperation. ItensuresRYWbydelaying
a read operation until the user’s write-set is a subset of what has been applied by the replica.
Similarly,MRisensuredbydelayingareadoperationuntiltheuser’sread-setisasubsetofthose
appliedbythereplica.
Property Sessionupdated: Sessionchecked:
RYW onwrite,expandwrite-set onread,ensurewrite-set⊆writesappliedbysite.
MR onread,expandread-set onread,ensureread-set⊆writesappliedbysite.
WFR onread,expandread-set onwrite,ensureread-set⊆writesappliedbysite.
MW onwrite,expandwrite-set onwrite,ensurewrite-set⊆writesappliedbysite.
agraphrepresentation[Feketeetal.1999].
8.1.2 Session guarantees. A problem with the previous approach is that specifying
dependencyforeachreadoperationishardforusers. Sessionguaranteesareamechanism
togeneratedependenciesautomaticallyfromauser-chosencombinationofthefollowing
predefinedpolicies[Terryetal.1994]:
—“Readyourwrites”(RYW)guaranteesthatthecontentsreadfromareplicaincorporate
previouswritesbythesameuser.
—“Monotonic reads” (MR) guarantees that successive reads by the same user return in-
creasinglyup-to-datecontents.
—“Writes follow reads” (WFR) guarantees that a write operation is accepted only after
writesobservedbypreviousreadsbythesameuserareincorporatedinthesamereplica.
—“Monotonic writes” (MW) guarantees that a write operation is accepted only after all
writeoperationsmadebythesameuserareincorporatedinthesamereplica.
These guarantees are sufficient to solve a number of real-world problems. The stale-
password problem can be solved by RYW. MR, for example, allows a replicated email
service to retrieve the mailbox index before the email body. A source code management
systemwouldenforceMWforthecasewhereonesiteupdatesalibrarymoduleandanother
updatesanapplicationprogramthatdependsonthenewlibrarymodule.
Sessionguaranteesareimplementedusingasessionobjectcarriedbyeachuser(e.g.,in
aPDA).Asessionrecordstwopiecesofinformation:thewrite-setofpastwriteoperations
submitted by the user, and the read-set of writes that the user has observed through past
reads. Each of them can be represented in a compact form using vector clocks. Table II
describeshowthesessionguaranteescanbemetusingasessionobject.
8.2 Boundingreplicadivergence
Thissectionoverviewstechniquesthattrytoboundaquantitativemeasureofinconsistency
among replicas. The simplest are real-time guarantees [Alonso et al. 1990], allowing an
object to be cached and remain stale for up to a certain amount of time. This is simple
forsingle-master,pull-basedsystems,whichcanenforcetheguaranteesimplybyperiodic
polling.ExamplesincludeWebservices[Fieldingetal.1999],NFS[Sternetal.2001],and
DNS[AlbitzandLiu2001]. TACToffersareal-timeguaranteeviapushing(Section7.4)
[YuandVahdat2000].
Other systems provide more explicit means of controlling the degree of replica incon-
sistency. One such approach is order bounding, or limiting the number of uncommitted
operationsthatcanbeseenbyareplica. Inthecontextoftraditionaldatabasesystems,this
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 35
canbeachievedbyrelaxingthelockingmechanismtoincreaseconcurrencybetweentrans-
actions. Forexample,boundedignoranceallowsatransactiontoproceed,eventhoughthe
replicahasnotreceivedtheresultsofaboundednumberoftransactionsthatareserialized
before it [Krishnakumar and Bernstein 1994]. See also Kumar and Stonebraker [1988],
Kumar and Stonebraker [1990], O’Neil [1986], Pu and Leff [1991], Carter et al. [1998]
andPuetal.[1995].
TACT applies a similar idea to optimistic replication [Yu and Vahdat 2001]. TACT is
a multi-master operation-transfer system, similar to Bayou, but it adds mechanisms for
controlling replica divergence. TACT implements an order guarantee by having a site
exchangeoperationsandthecommitinformation(Section5.5)withothersites.Asitestops
acceptingnewupdateswhenitsnumberoftentative(uncommitted)operationsexceedsthe
user-specifiedlimit.
TACTalsoprovidesanumericboundingthatboundsthedifferencebetweenthevalues
of replicas. The implementation uses a “quota”, allocated to each master replica, that
bounds the number of operations that the replica can buffer locally before pushing them
toaremotereplica. Considerabankaccount,replicatedattenmasterreplicas,wherethe
balance on any replica is constrained to be within $50 of the actual balance. Then, each
masterreceivesaquotaof$5(=50/10)fortheaccount. AmastersiteinTACTexchanges
operations with other sites. As a side effect, it also estimates the progress of other sites.
TACTusesackvectors(Section5.5.2)forthispurpose,buttimestampmatrices(Sections
5.5.2,7.4.4)couldalsobeused. Thesitethencomputesthedifferencebetweenitscurrent
valueandthevalueofanothersite,estimatedfromitsprogress. Wheneverthedifference
reachesthequotaof$5,thesitestopsacceptingnewoperationsandpushesoperationsto
other replicas. Numeric bounding is stronger and more useful than ordering bounding,
althoughitismorecomplexandexpensive.
8.3 Probabilistictechniques
The techniques discussed in this section rely on the knowledge of the workloads to re-
ducethereplica’sstalenessprobabilisticallywithsmalloverhead. ChoandGarcia-Molina
[2000] study policies based on frequency and order of page re-fetching for web proxy
servers,underthesimplifyingassumptionthattheupdateintervalfollowsaPoissondistri-
bution. They find that to minimize average page staleness, replicas should be re-fetched
in the same deterministic order and at a uniform interval, even when some pages were
updatedmorefrequentlythanothers.
Lawrence et al. [2002] do a similar study using real workloads. They present a
probabilistic-modeling tool that learns patterns from a log of past updates. The tool se-
lectsanappropriateperiod,saydailyorweekday/weekend. Eachperiodissubdividedinto
time-slots, and the tool creates a histogram representing the likelihood of an update per
slot. A mobile news service is chosen as an example. Here, the application running on
themobiledeviceconnectswhenneededtothemaindatabasetodownloadrecentupdates.
Assuming that the user is willing to pay for a fixed number of connections per day, the
application uses the probabilistic models to select the connection times that optimize the
freshnessofthereplica. Comparedtoconnectingatfixedintervals,theiradaptivestrategy
showsanaveragefreshnessimprovementof14%.
ACMComputingSurveys,Vol.V,No.N,32005.

·
| 36 Y.SaitoandM.Shapiro |                                                                          |                   |                       |
| ---------------------- | ------------------------------------------------------------------------ | ----------------- | --------------------- |
| TableIII.              | Summaryofmainalgorithmsusedforclassesofoptimistic-replicationstrategies. |                   |                       |
|                        | SingleMaster,state-or                                                    | Multimaster,state | Multimaster,operation |
|                        | op-transfer                                                              | transfer          | transfer              |
Operation
|     | Thomas’swriterule(6.1) |     | vectorclock(4.3) |
| --- | ---------------------- | --- | ---------------- |
propagation
Syntacticorsemantic
Scheduling
(5.2)
Thomas’swriterule,mod-
Operational
ifiedbits,versionvector(6)
transformation(5.2.4),
| Commitment |     |     | ackvector(5.5.2), |
| ---------- | --- | --- | ----------------- |
Localconcurrencycontrol
primarycommit(5.5.3),
voting(5.5.3)
Twotimestamps,modified
| Conflictdetection |     |     | Syntacticorsemantic |
| ----------------- | --- | --- | ------------------- |
bits,versionvector
| Conflictresolution |     | Ignore,exclude,manual,app.specific(5.4) |                   |
| ------------------ | --- | --------------------------------------- | ----------------- |
| Divergence         |     |                                         | Temporal,session, |
Temporal(8.2),session(8.1.2)
| bounding |     |     | numerical,order |
| -------- | --- | --- | --------------- |
Flooding,rumor
| Pushing    | Flooding(7.4.1),rumormongering,directedgossip- |     | mongering,directed  |
| ---------- | ---------------------------------------------- | --- | ------------------- |
| techniques | ing(7.4.2)                                     |     | gossiping,timestamp |
matrix(5.5.2)
8.4 Summary
Beyondeventualconsistency,thissectionhasfocusedonthecontrolofreplicadivergence
overshorttimeperiods. Thefollowingtablesummarizestheapproachesdiscussedinthis
section.
| Problem             | Solution | Advantages | Disadvantages       |
| ------------------- | -------- | ---------- | ------------------- |
| Enforcingcausalread | Explicit | –          | Cumbersomeforusers. |
&writeordering. Sessionguarantees Intuitive Ausermustcarryasession
object.
| Real-timestaleness | Polling | –   | Pollingoverhead             |
| ------------------ | ------- | --- | --------------------------- |
| guarantee.         | Pushing | –   | Slightlymorecomplex;network |
delaymustbebounded.
| Explicitbounding | Orderbounding     | –    | Notintuitive                 |
| ---------------- | ----------------- | ---- | ---------------------------- |
|                  | Numericalbounding | More | Complex;oftentooconservative |
intuitive.
| Best-effortstaleness | Exploitworkload | –   | App-specific |
| -------------------- | --------------- | --- | ------------ |
| reduction.           | pattern.        |     |              |
9. CONCLUSIONS
This section concludes the paper by summarizing optimistic-replication algorithms and
systemsanddiscussingtheirtrade-offs. TableIIIsummarizesthekeyalgorithmsusedto
solvethechallengesofoptimisticreplicationintroducedinSection3. TableIVcompares
theircommunicationaspects,includingthedefinitionofobjectsandoperations,thenum-
ber of masters, and propagation strategies. Table V summarizes the concurrency control
aspectsofthesesystems: scheduling,conflicthandling,andcommitment. Bibliographical
sourcesandcrossreferenceintothetextareprovidedinTableVI.
TableVIIsummarizeshowdifferentclassesofoptimisticreplicationsystemscomparein
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 37
TableIV. Communicationaspectsofrepresentativeoptimisticreplicationsystems.Opshowswhetherthesys-
tempropagatestheobjectstateorsemanticoperationdescription. Codausesstatetransferforregularfiles,but
operationtransferfordirectoryoperations. M standsforthenumberofmasters; itcanbeanynumberunless
specified. Topologyshowsthecommunicationtopology. Propagationspecifiesthepropagationprotocolused
bythesystem. Spacereclamationtellsthesystem’sapproachtodeleteolddatastructures. “–”meansthat
thisaspecteitherdoesnotapply,orisnotdiscussedintheavailableliterature. (Sessn. Guar.=BayouSession
Guarantees;Op.Transf.=OperationalTransformation).
System
|                 | Object         | Op M  | Topology | Propagation | Spacereclamation |
| --------------- | -------------- | ----- | -------- | ----------- | ---------------- |
| ActiveDirectory | name-valuepair | state | any      | pull        | expiration       |
Bayou
|     | singleDB | op  | any | TV/manual | primarycommit |
| --- | -------- | --- | --- | --------- | ------------- |
Sessn.Guar.
| Clearinghouse | name-valuepair | state   | any       | push/pull | expiration   |
| ------------- | -------------- | ------- | --------- | --------- | ------------ |
| Coda          | file/directory | both    | star      | push      | logrollover  |
| CVS           | File           | op      | star      | manual    | manual       |
| Deno          | record         | op      | any       | –         | quorumcommit |
| DNS           | wholeDB        | state 1 | star      | push/pull | manual       |
| ESDS          | arbitrary      | op      | any       | –         | –            |
| Ficus,Roam    | file/directory | state   | star/ring | pull      | commitment   |
| IceCube       | arbitrary      | op      | any       | TV/manual | –            |
| NIS           | wholeDB        | state 1 | star      | push      | manual       |
| Op.Trans.     | arbitrary      | op      | any       | push      | –            |
| PalmPilot     | DBrecord       | state   | star      | manual    | –            |
Ramsey&
|     | file/directory | op  | –   | –   | –   |
| --- | -------------- | --- | --- | --- | --- |
Csirmaz
| TACT           | singleDB       | op      | any  | TV/push/pull | primarycommit |
| -------------- | -------------- | ------- | ---- | ------------ | ------------- |
| TSAE           | singleDB       | op      | any  | TV/push/pull | ackvector     |
| Unison         | file/directory | op      | any  | –            | –             |
| Usenet         | article        | state   | any  | blindpush    | expiration    |
| Web/filemirror | file           | state 1 | tree | pull         | manual        |
termsofavailability,conflictresolution,algorithmiccomplexity,andspaceandnetworking
overheads. It is clear that there is no single winner; each strategy has advantages and
disadvantages.
Single-mastersystemsareagoodchoiceiftheworkloadisread-dominatedorifthereis
anaturalsinglewriter. Itissimple,conflict-free,andscaleswellinpractice. Multi-master
state transfer works well for many applications. It is reasonably simple and has a low
spaceoverhead—asingletimestamporversionvectorperobject. Itscommunicationcost
isindependentoftherateofupdatesasmultipleupdatestothesameobjectarecoalesced.
The overhead increases with the object size, but it can be reduced substantially, as we
discussed in Section 7.2. These systems have difficulty exploiting operation semantics
duringconflictresolution. Thus,itisagoodchoicewhenobjectsarenaturallysmall,the
conflict rate is low, and conflicts can be resolved by a syntactic rule such as “last writer
wins”.
Multi-master operation transfer overcomes the shortcomings of the state-transfer ap-
proach but pays the cost in terms of algorithmic complexity and the log space overhead.
Thenetworkingcostsofstateandoperationtransferdependonvariousfactors,including
theobjectsize,updatesize,updatefrequency,andsynchronizationfrequency. Whilestate-
transfersystemsareexpensiveforlargeobjects,theycanamortizethecostwhentheobject
isupdatedmultipletimesbetweensynchronization.
Optimistic, asynchronous data replication is an appealing technique; it improves net-
working flexibility and scalability. Many applications would not function without opti-
ACMComputingSurveys,Vol.V,No.N,32005.

·
38 Y.SaitoandM.Shapiro
TableV. Concurrencycontrolaspectsofsomeoptimisticreplicationsystems.Orderingindicatestheorderthe
systemexecutesoperations.Detectingconflictsindicateshowthesystemdetectsconflicts,ifatall,andResolving
conflictshowitresolvesthem.Commitisthesystem’scommitmentprotocol.Consistencyindicatesthesystem’s
consistencyguarantees.“TWR”standsforThomas’swriterule,“1copy”forsingle-copylinearizability.
|                 |              | Detecting | Resolving |        |             |
| --------------- | ------------ | --------- | --------- | ------ | ----------- |
| System          | Ordering     |           |           | Commit | Consistency |
|                 |              | conflicts | conflicts |        |             |
| ActiveDirectory | logicalclock | none      |           | none   | eventual    |
TWR
| Bayou |                |           |             |         | eventual |
| ----- | -------------- | --------- | ----------- | ------- | -------- |
|       | receptionorder | predicate | userdefined | primary |          |
|       | atprimary      |           |             |         | ordering |
Sessn.Guar.
| Clearinghouse | real-timeclock | none |     | none | eventual |
| ------------- | -------------- | ---- | --- | ---- | -------- |
TWR
vectorclock/
| Coda       | receptionorder |               | userdefined | primary  | eventual |
| ---------- | -------------- | ------------- | ----------- | -------- | -------- |
|            | atprimary      | semantic      |             |          |          |
| CVS        | primarycommit  | twotimestamps | exclude     | primary  | eventual |
| Deno       | quorum         | concurrentRW  | exclude     | quorum   | 1copy    |
| DNS        | singlemaster   | –             | –           | -        | temporal |
| ESDS       | scalarclock    | none          | none        | implicit | 1copy    |
| Ficus,Roam | vectorclock    | vectorclock   | userdefined | none     | eventual |
| IceCube    | optimization   | graph         | userdefined | primary  | eventual |
| NIS        | singlemaster   | –             | –           | –        | eventual |
| Op.Transf. | receptionorder | none          | none        | implicit | eventual |
| Palm       | receptionorder | modifiedbits  | resolver    | primary  | eventual |
atprimary
Ramsey&
|     | canonical | semantic | exclude | –   | eventual |
| --- | --------- | -------- | ------- | --- | -------- |
Csirmaz
| TACT | receptionorder | predicate | user-defined | primary | bounded |
| ---- | -------------- | --------- | ------------ | ------- | ------- |
atprimary
| TSAE           | scalarclock    | none     | none    | ackvector | eventual  |
| -------------- | -------------- | -------- | ------- | --------- | --------- |
| Unison         | canonical      | semantic | exclude | primary   | eventual  |
| Usenet         | real-timeclock | none     | TWR     | none      | eventual  |
| Web/filemirror | singlemaster   | –        | –       | –         | eventual/ |
temporal
|     |                 | TableVI. Crossreference      |     |             |     |
| --- | --------------- | ---------------------------- | --- | ----------- | --- |
|     | System          | Mainreference                |     | MainSection |     |
|     | ActiveDirectory | Microsoft2000                |     | –           |     |
|     | Bayou           | Petersenetal.1997            |     | 2.4         |     |
|     | Sessn.Guar.     | Terryetal.1994               |     | 8.1.2       |     |
|     | Clearinghouse   | Demersetal.1987              |     | –           |     |
|     | Coda            | KistlerandSatyanarayanan1992 |     | –           |     |
|     | CVS             | Cederqvistetal.2001          |     | 2.5         |     |
|     | Deno            | Keleher1999                  |     | 5.5.3       |     |
|     | DNS             | AlbitzandLiu2001             |     | 2.1         |     |
|     | ESDS            | Feketeetal.1999              |     | 5.5.2       |     |
|     | Ficus,Roam      | Ratner1998                   |     | –           |     |
|     | IceCube         | Preguic¸aetal.2003           |     | 5.2.5       |     |
|     | NIS             | SunMicrosystems1998          |     | –           |     |
|     | Op.Transf.      | Sunetal.1998                 |     | 5.2.4       |     |
|     | PalmPilot       | PalmSource2002               |     | 2.3         |     |
|     | Ramsey&Csirmaz  | RamseyandCsirmaz2001         |     | 5.2.3       |     |
|     | TACT            | YuandVahdat2001              |     | 8.2         |     |
|     | TSAE            | Golding1992                  |     | 5.5.2       |     |
|     | Unison          | BalasubramaniamandPierce1998 |     | –           |     |
|     | Usenet          | SpencerandLawrence1998       |     | 2.2         |     |
|     | Web/filemirror  | Nakagawa1996                 |     | –           |     |
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 39
TableVII. Comparingthebehaviorsandcostsofoptimisticreplicationstrategies.
Singlemaster, Singlemaster,op Multimaster, Multimaster,op
statetransfer transfer statetransfer transfer
Availability low:mastersinglepointoffailure high
Conflictresolution flexible:semantic
N/A inflexible operation
flexibility scheduling
Algorithmic high:scheduling
verylow low
complexity andcommitment.
Spaceoverhead low:Tombstones high:log low:Tombstones high:log
Networkoverhead O(object-size) O(|#operations|) O(object-size) O(|#operations|)
mistic replication. However, it also comes with a cost. The algorithmic complexity of
ensuring eventual consistency can be high. Conflicts usually require application-specific
resolution, and the lost update problem is ultimately unavoidable. It is important not to
overengineer. Traditional, pessimistic replication, with many off-the-shelf solutions, is
perfectlyadequateinsmall-scale,fullyconnected,reliablenetworkingenvironments. Ad-
vancedtechniquessuchasversionvectorsandoperationtransfershouldbeusedonlywhen
youneedflexibilityandsemanticallyrichconflictresolution.
ACKNOWLEDGMENTS
Wethankouranonymousreviewersfortheirconstructivecomments,aswellasthefollow-
ingpeoplefortheirvaluablefeedbackonearlyversionsofthispaper: MiguelCastro,Yek
Chong, Svend Frølund, Christos Karamanolis, Anne-Marie Kermarrec, Dejan Milojicic,
AntRowstron,SusanSpence,andJohnWilkes.
REFERENCES
ADLY,N.1995. Managementofreplicateddatainlargescalesystems. Ph.D.thesis,CorpusCristiCollege,U.
ofCambridge.
ADYA,A.ANDLISKOV,B.1997.Lazyconsistencyusinglooselysynchronizedclocks.In16thSymp.onPrinc.
ofDistr.Comp.(PODC).SantaBarbara,CA,USA,73–82.
AGRAWAL,D.,ABBADI,A.E.,ANDSTEIKE,R.C.1997.Epidemicalgorithmsinreplicateddatabases.In16th
Symp.onPrinc.ofDatabaseSys.(PODS).Tucson,AZ,USA,161–172.
ALBITZ,P.ANDLIU,C.2001. DNSandBIND,4thed. O’Reilly&Associates,Sebastopol,CA,USA. ISBN
0-596-00158-4.
ALMEIDA,P.S.,BAQUERO,C.,ANDFONTE,V.2000. Panasync:Dependencytrackingamongfilecopies. In
9thACMSIGOPSEuropeanWorkshop,P.Guedes,Ed.Kolding,Denmark,7–12.
ALMEIDA,P.S.,BAQUERO,C.,ANDFONTE,V.2002.Versionstamps—decentralizedversionvectors.In22nd
Int.Conf.onDist.Comp.Sys.(ICDCS).Vienna,Austria,544–551.
ALONSO,R.,BARBARA,D.,ANDGARCIA-MOLINA,H.1990.Datacachingissuesinaninformationretrieval
system.ACMTrans.DatabaseSyst.15,3(Sept.),359–384.
BAKER,M.,HARTMAN,J.H.,KUPFER,M.D.,SHIRRIFF,K.,ANDOUSTERHOUT,J.K.1991.Measurements
ofadistributedfilesystem.In13thSymp.onOp.Sys.Principles(SOSP).PacificGrove,CA,USA,198–212.
BALASUBRAMANIAM,S.ANDPIERCE,B.C.1998. Whatisafilesynchronizer? In4thInt.Conf.onMobile
ComputingandNetworking(MOBICOM).ACM/IEEE,Dallas,TX,USA.
BERNSTEIN,P.A.ANDGOODMAN,N.1983.Thefailureandrecoveryproblemforreplicateddatabases.In2nd
Symp.onPrinc.ofDistr.Comp.(PODC).Montre´al,QC,Canada,114–122.
BERNSTEIN,P.A.,HADZILACOS,V.,ANDGOODMAN,N.1987.Concurrencycontrolandrecoveryindatabase
systems. AddisonWesley,Boston,MA,USA. Availableonlinefromhttp://research.microsoft.com/pubs/
ccontrol/.
BIRMAN,K.P.,HAYDEN,M.,OZKASAP,O.,XIAO,Z.,BUDIU,M.,ANDMINSKY,Y.1999. Bimodalmulti-
cast.ACMTrans.Comput.Syst.17,2,41–88.
ACMComputingSurveys,Vol.V,No.N,32005.

·
40 Y.SaitoandM.Shapiro
BIRMAN, K. P. AND JOSEPH, T. A.1987. Reliablecommunicationinthepresenceoffailures. ACMTrans.
Comput.Syst.5,1(Feb.),272–314.
BIRRELL,A.D.,LEVIN,R.,NEEDHAM,R.M.,ANDSCHROEDER,M.D.1982. Grapevine:Anexercisein
distributedcomputing.Commun.ACM25,4(Feb.),260–274.
BOLINGER,D.ANDBRONSON,T.1995. ApplyingRCSandSCCS. O’Reilly&Associates,Sebastopol,CA,
USA.
CARTER,J.,RANGANATHAN,A.,ANDSUSARLA,S.1998.Khazana:Aninfrastructureforbuildingdistributed
services.In18thInt.Conf.onDist.Comp.Sys.(ICDCS).Amsterdam,TheNetherlands,562–571.
CEDERQVIST, P.,PESCH, R.,ET AL.2001. VersionmanagementwithCVS. http://www.cvshome.org/docs-
/manual.
CHANDRA,B.,DAHLIN,M.,GAO,L.,ANDNAYATE,A.2001. End-to-endWANserviceavailability. In3rd
USENIXSymp.onInternetTech.andSys.(USITS).SanFrancisco,CA,USA.
CHANDRA,T.D.ANDTOUEG,S.1996.Unreliablefailuredetectorsforreliabledistributedsystems.Journalof
theACM(JACM)43,2(Mar.),225–267.
CHANKHUNTHOD,A.,DANZIG,P.B.,NEERDAELS,C.,SCHWARTZ,M.F.,ANDWORRELL,K.J.1996. A
hierarchicalInternetobjectcache.InUSENIXWinterTech.Conf.SanDiego,CA,USA,153–164.
CHARRON-BOST,B.1991.Concerningthesizeoflogicalclocksindistributedsystems.InformationProcessing
Letters39,1(July),11–16.
CHEN,Y.,EDLER,J.,GOLDBERG,A.,GOTTLIEB,A.,SOBTI,S.,ANDYIANILOS,P.N.1999. Aprototype
implementationofArchivalIntermemory.InFourthACMConf.onDigitalLibraries(DL’99).ACM,Berkeley
CA(USA),28–37.
CHO, J. AND GARCIA-MOLINA, H.2000. Synchronizingadatabasetoimprovefreshness. InInt.Conf.on
ManagementofData(SIGMOD).Dallas,TX,USA,117–128.
CORMACK,G.V.1995.Acalculusforconcurrentupdate.Tech.Rep.CS-95-06,UniversityofWaterloo.
COX,L.P.ANDNOBLE,B.D.2001. Fastreconciliationsinfluidreplication. In21stInt.Conf.onDist.Comp.
Sys.(ICDCS).Phoenix,AZ,USA.
DETORRES-ROJAS,F.ANDAHAMAD,M.1996. Plausibleclocks:Constantsizelogicalclocksfordistributed
systems.In10thInt.WorkshoponDist.Algorithms(WDAG).Bologna,Italy.
DEERING,S.E.1991.Multicastroutinginadatagraminternetwork.Ph.D.thesis,StanfordUniversity.
DEMERS,A.J.,GREENE,D.H.,HAUSER,C.,IRISH,W.,ANDLARSON,J.1987. Epidemicalgorithmsfor
replicateddatabasemaintenance. In6thSymp.onPrinc.ofDistr.Comp.(PODC).Vancouver,BC,Canada,
1–12.
DIETTERICH, D. J.1994. DECdatadistributor:Fordatareplicationanddatawarehousing. InInt.Conf.on
ManagementofData(SIGMOD).ACM,Minneapolis,MN,USA,468.
ELLIS,C.A.ANDGIBBS,S.J.1989.Concurrencycontrolingroupwaresystems.InInt.Conf.onManagement
ofData(SIGMOD).Portland,OR,USA.
ELMAGARMID,A.K.,Ed.1992. Databasetransactionmodelsforadvancedapplications. MorganKaufmann,
SanFrancisco,CA,USA.
ELSON, J., GIROD, L., AND ESTRIN, D. 2002. Fine-grainednetworktimesynchronizationusingreference
broadcasts.In5thSymp.onOp.Sys.DesignandImpl.(OSDI).Boston,MA,USA.
FEKETE,A.,GUPTA,D.,LUCHANGCO,V.,LYNCH,N.,ANDSHVARTSMAN,A.1999.Eventuallyserializable
dataservices.TheoreticalComputerScience220,SpecialissueonDistributedAlgorithms,113–156.
FEKETE,A.,LYNCH,N.,ANDSHVARTSMAN,A.1997. Specifyingandusingapartitionablegroupcommuni-
cationservice.In16thSymp.onPrinc.ofDistr.Comp.(PODC).SantaBarbara,CA,USA,53–62.
FIDGE,C.J.1988.Timestampsinmessage-passingsystemsthatpreservethepartialordering.In11thAustralian
ComputerScienceConference.UniversityofQueensland,Australia,55–66.
FIELDING,R.,GETTYS,J.,MOGUL,J.,FRYSTYK,H.,MASINTER,L.,LEACH,P.,ANDBERNERS-LEE,T.
1999.RFC2616:Hypertexttransferprotocol–HTTP/1.1.http://www.faqs.org/rfcs/rfc2616.html.
FISCHER,M.J.,LYNCH,N.A.,ANDPATERSON,M.S.1985. Impossibilityofdistributedconsensuswithone
faultyprocess.JournaloftheACM(JACM)32,2,374–382.
FISCHER,M.J.ANDMICHAEL,A.1982.Sacrificingserializabilitytoattainavailabilityofdatainanunreliable
network.In1stSymp.onPrinc.ofDatabaseSys.(PODS).LosAngeles,CA,USA,70–75.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 41
FLOYD,S.,JACOBSEN,V.,LIU,C.-G.,MCCANNE,S.,ANDZHANG,L.1997.Areliablemulticastframework
forlight-weightsessionsandapplicationlevelframing. IEEE/ACMJournalonNetworking5,6(Dec.),784–
803.
FOX, A. AND BREWER, E. A.1999. Harvest,yield,andscalabletolerantsystems. In6thWorkshoponHot
TopicsinOperatingSystems(HOTOS-VI).RioRico,AZ,USA,174–178.
GIFFORD,D.K.1979.Weightedvotingforreplicateddata.In7thSymp.onOp.Sys.Principles(SOSP).Pacific
Grove,CA,USA,150–162.
GOLDING,R.A.1992. Weak-consistencygroupcommunicationandmembership. Ph.D.thesis,Universityof
CaliforniaSantaCruz.Tech.Reportno.UCSC-CRL-92-52.
GRAY,J.,HELLAND,P.,O’NEIL,P.,ANDSHASHA,D.1996. Dangersofreplicationandasolution. InInt.
Conf.onManagementofData(SIGMOD).Montre´al,Canada,173–182.
GRAY,J.ANDREUTER,A.1993. Transactionprocessing:Conceptsandtechniques. MorganKaufmann,San
Francisco,CA,USA.
GUY,R.G.,POPEK,G.J.,ANDPAGE,JR.,T.W.1993. Consistencyalgorithmsforoptimisticreplication. In
Proc.FirstIEEEInt.Conf.onNetworkProtocols.SanFrancisco,CA,USA.
HEDETNIEMI, S., HEDETNIEMI, S., AND LIESTMAN, O. 1988. Asurveyofgossipingandbroadcastingin
communicationnetworks.Networks18,319–349.
HERLIHY,M.P.ANDWING,J.M.1990.Linearizability:Acorrectnessconditionforconcurrentobjects.ACM
Trans.Program.Lang.Syst.12,3,463–492.
JAGADISH,H.V.,MUMICK,I.S.,ANDRABINOVICH,M.1997. Scalableversioningindistributeddatabases
withcommutingupdates.In13thInt.Conf.onDataEng.(ICDE).Birmingham,U.K.,520–531.
JOHNSON,P.R.ANDTHOMAS,R.H.1976. RFC677:Themaintenanceofduplicatedatabases. http://www-
.faqs.org/rfcs/rfc677.html.
JOHNSON,T.ANDJEONG,K.1996.Hierarchicalmatrixtimestampsforscalableupdatepropagation.Tech.Rep.
TR96-017,UniversityofFlorida.June.
KANG,B.B.,WILENSKY,R.,ANDKUBIATOWICZ,J.2003.Thehashhistoryapproachforreconcilingmutual
inconsistency.In23rdInt.Conf.onDist.Comp.Sys.(ICDCS).Providence,RhodeIsland,USA.
KANTOR,B.ANDRAPSEY,P.1986.RFC977:Networknewstransferprotocol.http://www.faqs.org/rfcs/rfc977-
.html.
KAWELL, JR., L.,BECKHART, S.,HALVORSEN, T.,OZZIE, R.,AND GREIF, I.1988. Replicateddocument
managementinagroupcommunicationsystem. InConf.onComp.-SupportedCoop.Work(CSCW).Chapel
Hill,NC,USA.
KELEHER, P. J. 1999. Decentralized replicated-object protocols. In 18th Symp. on Princ. of Distr. Comp.
(PODC).Atlanta,GA,USA,143–151.
KEMPE,D.,KLEINBERG,J.,ANDDEMERS,A.2001. Spatialgossipandresourcelocationprotocols. In33rd
Symp.onTheoryofComputing(STOC).Crete,Greece.
KERMARREC,A.-M.,ROWSTRON,A.,SHAPIRO,M.,ANDDRUSCHEL,P.2001.TheIceCubeapproachtothe
reconciliationofdivergingreplicas.In20thSymp.onPrinc.ofDistr.Comp.(PODC).Newport,RI,USA.
KIM,M.,COX,L.P.,ANDNOBLE,B.D.2002. Safety,visibility,andperformanceinawide-areafilesystem.
InUSENIXConf.onFileandStorageTechnologies(FAST).Usenix,Monterey,CA.
KISTLER,J.J.ANDSATYANARAYANAN,M.1992.DisconnectedoperationintheCodafilesystem.ACMTrans.
Comput.Syst.10,5(Feb.),3–25.
KRASEL,C.2000.Leafnode:AnNNTPserverforsmallsites.http://www.leafnode.org.
KRISHNAKUMAR,N.ANDBERNSTEIN,A.1994. Boundedignorance:Atechniqueforincreasingconcurrency
inreplicatedsystems.ACMTrans.DatabaseSyst.19,4(Dec.),685–722.
KUMAR,A.ANDSTONEBRAKER,M.1988. Semanticbasedtransactionmanagementtechniquesforreplicated
data.InInt.Conf.onManagementofData(SIGMOD).Chicago,IL,USA,117–125.
KUMAR, A. AND STONEBRAKER, M. 1990. Ananalysisofborrowingpoliciesforescrowtransactionsina
replicatedenvironment.In6thInt.Conf.onDataEng.(ICDE).LosAngeles,CA,USA,446–454.
KUMAR,P.ANDSATYANARAYANAN,M.1993. Log-baseddirectoryresolutionintheCodafilesystem. In2nd
Int.Conf.onParallelandDist.Info.Sys.(PDIS).SanDiego,CA,USA,202–213.
KUMAR,P.ANDSATYANARAYANAN,M.1995.Flexibleandsaferesolutionoffileconflicts.InUSENIXWinter
Tech.Conf.NewOrleans,LA,USA,95–106.
ACMComputingSurveys,Vol.V,No.N,32005.

·
42 Y.SaitoandM.Shapiro
LADIN,R.,LISKOV,B.,SHRIRA,L.,ANDGHEMAWAT,S.1990.Lazyreplication:Exploitingthesemanticsof
distributedservices.Tech.Rep.TR-484,MITLCS.July.
LADIN,R.,LISKOV,B.,SHRIRA,L.,ANDGHEMAWAT,S.1992.Providinghighavailabilityusinglazyreplica-
tion.ACMTrans.Comput.Syst.10,4,360–391.
LAMPORT,L.1978.Time,clocks,andtheorderingofeventsinadistributedsystem.Commun.ACM21,7(July),
558–565.
LAWRENCE,N.D.,ROWSTRON,A.I.T.,BISHOP,C.M.,ANDTAYLOR,M.J.2002. Optimisingsynchro-
nisationtimesformobiledevices. InAdvancesinNeuralInformationProcessingSystems,T.G.Dietterich,
S.Becker,andZ.Ghahramani,Eds.Vol.14.MITPress,Cambridge,MA,USA,1401–1408.
LEE,Y.-W.,LEUNG,K.-S.,ANDSATYANARAYANAN,M.2002. Operationshippingformobilefilesystems.
IEEETrans.Comput.51,12,1410–1422.
LIDL,K.,OSBORNE,J.,ANDMALCOLM,J.1994. Drinkingfromthefirehose:MulticastUSENETnews. In
USENIXWinterTech.Conf.SanFrancisco,CA,USA,33–45.
LIN,M.J.ANDMARZULLO,K.1999. Directionalgossip:Gossipinawide-areanetwork. InThirdEuropean
DependableComputingConference.Prague,Czech,364–379.
LU,Q.ANDSATYANARAYANAN,M.1995. Improvingdataconsistencyinmobilecomputingusingisolation-
onlytransactions. In4thWorkshoponHotTopicsinOperatingSystems(HOTOS-IV).OrcasIsland,WA,
USA.
MATHESON,C.2003.Personalcommunication.
MATTERN, F. 1989. Virtual time and global states of distributed systems. In Int. W. on Parallel and Dist.
Algorithms.ElsevierSciencePublishersB.V.(North-Holland),216–226.
MAZIE`RES,D.ANDSHASHA,D.2002.BuildingsecurefilesystemsoutofByzantinestorage.In21stSymp.on
Princ.ofDistr.Comp.(PODC).Monterey,CA,USA.
MICROSOFT.2000. Windows2000Server:Distributedsystemsguide. MicrosoftPress,Redmond,WA,USA,
Chapter6,299–340.
MILLS, D. L.1994. Improvedalgorithmsforsynchronizingcomputernetworkclocks. InACMSIGCOMM.
London,UnitedKingdom,317–327.
MINSKY,Y.2002.Spreadrumorscheaply,quicklyandreliably.Ph.D.thesis,CornellUniversity.
MINSKY,Y.,TRACHTENBERG,A.,ANDZIPPEL,R.2001. Setreconciliationwithnearlyoptimalcommunica-
tioncomplexity.InInternationalSymposiumonInformationTheory.IEEE,Washington,DC,USA.
MISHRA, S., PETERSON, L.,, AND SCHLICHTING, R. 1989. Implementingfault-tolerantreplicatedobjects
usingPsync.In8thSymp.onReliableDist.Sys.(SRDS).Seattle,WA,USA,42–53.
MOCKAPETRIS, P. V.1987. RFC1035:Domainnames—implementationandspecification. http://www.faqs-
.org/rfcs/rfc1035.html.
MOCKAPETRIS,P.V.ANDDUNLAP,K.1988.DevelopmentoftheDomainNameSystem.InACMSIGCOMM.
Stanford,CA,USA,123–133.
MOLLI,P.,OSTER,G.,SKAF-MOLLI,H.,ANDIMINE,A.2003. Safegenericdatasynchronizer. Rapportde
rechercheA03-R-062,LORIA.May.
MOORE,K.1995.TheLotusNotesstoragesystem.InInt.Conf.onManagementofData(SIGMOD).SanJose,
CA,USA,427.
MUMMERT, L. B., EBLING, M. R., AND SATYANARAYANAN, M. 1995. Exploiting weak connectivity for
mobilefileaccess.In15thSymp.onOp.Sys.Principles(SOSP).CopperMountain,CO,USA,143–155.
MUTHITACHAROEN,A.,CHEN,B.,ANDMAZIE`RES,D.2001. Alow-bandwidthnetworkfilesystem. In18th
Symp.onOp.Sys.Principles(SOSP).LakeLouise,AB,Canada,174–187.
NAKAGAWA, I.1996. FTPmirror–mirroringdirectoryhierarchywithFTP. http://noc.intec.co.jp/ftpmirror.
html.
O’NEIL,P.E.1986.Theescrowtransactionalmethod.ACMTrans.DatabaseSyst.11,4,405–430.
Oracle1996.Oracle7serverdistributedsystemsmanual,vol.2.Oracle.
OUSTERHOUT,J.K.,DACOSTA,H.,HARRISON,D.,KUNZE,J.A.,KUPFER,M.D.,ANDTHOMPSON,J.G.
1985.Atrace-drivenanalysisoftheUnix4.2BSDfilesystem.In10thSymp.onOp.Sys.Principles(SOSP).
OrcasIsland,WA,USA,15–24.
PALMER,C.ANDCORMACK,G.1998. Operationtransformsforadistributedsharedspreadsheet. InConf.on
Comp.-SupportedCoop.Work(CSCW).Seattle,WA,USA,69–78.
ACMComputingSurveys,Vol.V,No.N,32005.

·
Optimisticreplication 43
PALMSOURCE,I.2002.Introductiontoconduitdevelopment.http://www.palmos.com/dev/support/docs/.
PARKER,D.S.,POPEK,G.,RUDISIN,G.,STOUGHTON,A.,WALKER,B.,WALTON,E.,CHOW,J.,EDWARDS,
D.,KISER,S.,ANDKLINE,C.1983.Detectionofmutualinconsistencyindistributedsystems.IEEETrans.
Softw.Eng.SE-9,3,240–247.
PAUL,S.,SABNANI,K.K.,LIN,J.C.,ANDBHATTACHARYYA,S.1997.Reliablemulticasttransportprotocol
(RMTP).IEEEJournalonSelectedAreasinCommunications15,3(Apr.),407–421.
PEDONE,F.2001. Boostingsystemperformancewithoptimisticdistributedprotocols. IEEEComputer34,7
(Dec.),80–86.
PETERSEN, K.,SPREITZER, M. J.,TERRY, D. B.,THEIMER, M. M.,AND DEMERS, A. J.1997. Flexible
updatepropagationforweaklyconsistentreplication.In16thSymp.onOp.Sys.Principles(SOSP).St.Malo,
France,288–301.
PREGUIC¸A,N.,SHAPIRO,M.,ANDMATHESON,C.2003.Semantics-basedreconciliationforcollaborativeand
environments.InProc.TenthInt.Conf.onCooperativeInformationSystems(CoopIS).Catania,Sicily,Italy.
PU,C.,HSEUSH,W.,KAISER,G.E.,WU,K.-L.,,ANDYU,P.S.1995. Divergencecontrolfordistributed
databasesystems.DistributedandParallelDatabases3,1(Jan.),85–109.
PU, C.,HSEUSH, W.,KAISER, G. E.,WU, K.-L.,AND YU, P. S.1995. Divergencecontrolfordistributed
databasesystems.Dist.andParallelDatabases3,1(Jan.),85–109.
PU,C.ANDLEFF,A.1991.Replicacontrolindistributedsystems:Anasynchronousapproach.InInt.Conf.on
ManagementofData(SIGMOD).Denver,CO,USA,377–386.
RABIN,M.O.1981.Fingerprintingbyrandompolynomials.Tech.Rep.TR-15-81,HarvardUniversity.
RABINOVICH,M.,GEHANI,N.H.,ANDKONONOV,A.1996. Efficientupdatepropagationinepidemicrepli-
cateddatabases.InInt.Conf.onExtendingDatabaseTechnology(EDBT).Avignon,France,207–222.
RAMAMRITHAM,K.ANDCHRYSANTHIS,P.K.1996.Executivebriefing:Advancesinconcurrencycontroland
transactionprocessing.IEEEComputerSociety,LosAlamitos,CA,USA.ISBN0818674059.
RAMAMRITHAM, K. AND PU, C.1995. Aformalcharacterizationofepsilonserializability. IEEETrans.on
KnowledgeandDataEng.7,6(Dec.),997–1007.
RAMSEY,N.ANDCSIRMAZ,E.2001. Analgebraicapproachtofilesynchronization. In9thInt.Symp.onthe
FoundationsofSoftw.Eng.(FSE).Austria.
RATNER,D.,REIHER,P.,ANDPOPEK,G.1997.Dynamicversionvectormaintenance.Tech.Rep.CSD-970022,
UCLA.June.
RATNER,D.H.1998. Roam:Ascalablereplicationsystemformobileanddistributedcomputing. Ph.D.thesis,
UCLosAngeles.Tech.Report.no.UCLA-CSD-970044.
RAVIN,E.,O’REILLY,T.,DOUGHERTY,D.,ANDTODINO,G.1996.UsingandmanagingUUCP.O’Reilly&
Associates,Sebastopol,CA,USA.
REIHER,P.,HEIDEMANN,J.S.,RATNER,D.,SKINNER,G.,ANDPOPEK,G.J.1994.Resolvingfileconflicts
intheFicusfilesystem.InUSENIXSummerTech.Conf.Boston,MA,USA,183–195.
RHODES,N.ANDMCKEEHAN,J.1998. Palmprogramming:Thedeveloper’sguide. O’Reilly&Associates,
Sebastopol,CA,USA.
SAITO,Y.ANDLEVY,H.M.2000. OptimisticreplicationforInternetdataservices. In14thInt.Conf.onDist.
Computing(DISC).Toledo,Spain,297–314.
SAITO, Y., MOGUL, J., AND VERGHESE, B. 1998. A Usenet performance study. http://www.hpl.hp.com-
/personal/YasushiSaito/pubs/newsbench.ps.
SPENCER, H. AND LAWRENCE, D.1998. ManagingUsenet. O’Reilly&Associates,Sebastopol,CA,USA.
ISBN1-56592-198-4.
SPREITZER,M.J.,THEIMER,M.M.,PETERSEN,K.,DEMERS,A.J.,ANDTERRY,D.B.1997.Dealingwith
servercorruptioninweaklyconsistent,replicateddatasystems. In3rdInt.Conf.onMobileComputingand
Networking(MOBICOM).Budapest,Hungary.
SPRING,N.T.ANDWETHERALL,D.2000. Aprotocol-independenttechniqueforeliminatingredundantnet-
worktraffic.InACMSIGCOMM.Stockholm,Sweden.
STERN,H.,EISLEY,M.,ANDLABIAGA,R.2001. ManagingNFSandNIS,2nded. O’Reilly&Associates,
Sebastopol,CA,USA.ISBN1-56592-510-6.
SUN,C.ANDELLIS,C.1998. Operationaltransformationinreal-timegroupeditors:Issues,algorithms,and
achievements.InConf.onComp.-SupportedCoop.Work(CSCW).Seattle,WA,USA,59–68.
ACMComputingSurveys,Vol.V,No.N,32005.

·
44 Y.SaitoandM.Shapiro
SUN,C.,JIA,X.,ZHANG,Y.,YANG,Y.,ANDCHEN,D.1998.Achievingconvergence,causality-preservation,
andintention-preservationinreal-timecooperativeeditingsystems,.ACMTransactionsonComputer-Human
Interaction5,1(Mar.),63–108.
SUN,C.,YANG,Y.,ZHANG,Y.,ANDCHEN,D.1996. Aconsistencymodelandsupportingschemesforreal-
timecooperativeeditingsystems. In19thAustralianComputerScienceConference.Melbourne,Australia,
582–591.
SUN,Q.2000.Reliablemulticastforpublish/subscribesystems.M.S.thesis,MIT.
SUNMICROSYSTEMS.1998.Sundirectoryservices3.1administrationguide.
TERRY, D. B.,DEMERS, A. J.,PETERSEN, K.,SPREITZER, M. J.,THEIMER, M. M.,AND WELCH, B. B.
1994. Sessionguaranteesforweaklyconsistentreplicateddata. In3rdInt.Conf.onParallelandDist.Info.
Sys.(PDIS).Austin,TX,USA,140–149.
TERRY, D. B.,THEIMER, M.,PETERSEN, K.,AND SPREITZER, M.2000. Anexaminationofconflictsina
weakly-consistent,replicatedapplication.PersonalCommunication.
TERRY,D.B.,THEIMER,M.M.,PETERSEN,K.,DEMERS,A.J.,SPREITZER,M.J.,ANDHAUSER,C.H.
1995. ManagingupdateconflictsinBayou,aweaklyconnectedreplicatedstoragesystem. In15thSymp.on
Op.Sys.Principles(SOSP).CopperMountain,CO,USA,172–183.
THOMAS,R.H.1979.Amajorityconsensusapproachtoconcurrencycontrolformultiplecopydatabases.ACM
Trans.DatabaseSyst.4,2(June),180–209.
TRIDGELL, A.2000. Efficientalgorithmsforsortingandsynchronization. Ph.D.thesis,AustralianNational
University.
VALOT,C.1993.Characterizingtheaccuracyofdistributedtimestamps.InWorkshoponParallelandDistributed
Debugging.43–52.
VESPERMAN,J.2003.EssentialCVS.O’Reilly&Associates,Sebastopol,CA,USA.
VIDOT,N.,CART,M.,FERRI’E,J.,ANDSULEIMAN,M.2000. Copiesconvergenceinadistributedreal-time
collaborativeenvironment. InConf.onComp.-SupportedCoop.Work(CSCW).Philadelphia,PA,USA,171–
180.
VOGELS,W.1999.FilesystemusageinWindowsNT4.0.In17thSymp.onOp.Sys.Principles(SOSP).Kiawah
Island,SC,USA,93–109.
WALKER,B.,POPEK,G.,ENGLISH,R.,KLINE,C.,ANDTHIEL,G.1983. TheLOCUSdistributedoperating
system.In9thSymp.onOp.Sys.Principles(SOSP).BrettonWoods,NH,USA,49–70.
WANG, A.-I. A.,REIHER, P. L.,AND BAGRODIA, R.2001. Understandingtheconflictratemetricforpeer
optimisticallyreplicatedfilingenvironments.Submittedforpublication.
WESSELS,D.ANDCLAFFY,K.1997. RFC2186:InternetCacheProtocol. http://www.faqs.org/rfcs/rfc2186-
.html.
WUU,G.T.J.ANDBERNSTEIN,A.J.1984. Efficientsolutionstothereplicatedloganddictionaryproblems.
In3rdSymp.onPrinc.ofDistr.Comp.(PODC).Vancouver,BC,Canada,233–242.
XTP2003.Thexpresstransportprotocol.http://www.ca.sandia.gov/xtp/.
YIN,J.,ALVISI,L.,DAHLIN,M.,ANDLIN,C.1999.HierarchicalcacheconsistencyinaWAN.In2ndUSENIX
Symp.onInternetTech.andSys.(USITS).Boulder,CO,USA,13–24.
YU,H.ANDVAHDAT,A.2000.Designandevaluationofacontinuousconsistencymodelforreplicatedservices.
In4thSymp.onOp.Sys.DesignandImpl.(OSDI).SanDiego,CA,USA,305–318.
YU,H.ANDVAHDAT,A.2001.Thecostsandlimitsofavailabilityforreplicatedservices.In18thSymp.onOp.
Sys.Principles(SOSP).LakeLouise,AB,Canada,29–42.
YU,H.ANDVAHDAT,A.2002.Minimalreplicationcostforavailability.In21stSymp.onPrinc.ofDistr.Comp.
(PODC).Monterey,CA,USA,98–107.
ZHANG,Y.,PAXON,V.,ANDSHENKAR,S.2000.ThestationarityofInternetpathproperties:Routing,lossand
throughput.Tech.rep.,ACIRI.May.
ReceivedDecember2001;revisedNovember2004;acceptedFebruary2005
ACMComputingSurveys,Vol.V,No.N,32005.
