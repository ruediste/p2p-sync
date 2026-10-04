Syncpal: A simple and iterative reconciliation algorithm for file synchronizers 

Von der Fakultät für Mathematik, Informatik und Naturwissenschaften der RWTH Aachen University zur Erlangung des akademischen Grades eines Doktors der Naturwissenschaften genehmigte Dissertation 

vorgelegt von 

## Marius Alwin Shekow (M.Sc.) 

aus Tettnang, Deutschland 

Berichter: Univ.-Prof. Dr. rer. pol. Matthias Jarke Univ.-Prof. Ph. D . Wolfgang Prinz Univ.-Prof. Dr. Tom Gross 

Tag der mündlichen Prüfung: 22. November, 2019 

Diese Dissertation ist auf den Internetseiten der Universitätsbibliothek verfügbar. 

# **Eidesstattliche Erklärung** 

Ich, Marius Alwin Shekow, 

erkläre hiermit, dass diese Dissertation und die darin dargelegten Inhalte die eigenen sind und selbstständig, als Ergebnis der eigenen originären Forschung, generiert wurden. Hiermit erkläre ich an Eides statt 

1. Diese Arbeit wurde vollständig oder größtenteils in der Phase als Doktoranddieser Fakultät und Universitätangefertigt; 

2. Sofern irgendein Bestandteil dieser Dissertation zuvor für einen akademischen Abschluss oder eine andere Qualifikation an dieser oder einer anderen Institution verwendet wurde, wurde diesklar angezeigt; 

3. Wenn immer andere eigene- oder Veröffentlichungen Dritter herangezogen wurden, wurden diese klar benannt; 

4. Wenn aus anderen eigenen- oder Veröffentlichungen Dritter zitiert wurde, wurde stets die Quelle hierfür angegeben. Diese Dissertation ist vollständig meine eigene Arbeit, mit der Ausnahme solcher Zitate; 

5. Alle wesentlichen Quellen von Unterstützung wurden benannt; 

6. Wenn immer ein Teil dieser Dissertation auf der Zusammenarbeit mit anderen basiert, wurde von mir klar gekennzeichnet, was von anderen und was von mir selbst erarbeitet wurde; 

7. Teile dieser Arbeit wurden zuvor veröffentlicht und zwar in: 

   - Marius Shekow. “Syncpal: A simple and iterative reconciliation algorithm for file synchronizers”. In: _Distributed Applications and Interoperable Systems - IFIP International Federation for Information Processing, DAIS 2019, Held as Part of the 14th International Federated Conference on Distributed Computing Techniques, DisCoTec 2019, Lingby, Denmark, June 18-21, 2019, Proceedings_ . Ed. by José Pereira and Laura Ricci. 2019 

   - Marius Shekow and Wolfgang Prinz. “A capability analysis of groupware, cloud and desktop file systems for file synchronization”. In: _Proceedings of 17th European Conference on Computer-Supported Cooperative Work-Exploratory Papers. The International Venue on Practicecentred Computing an the Design of Cooperation Technologies. European Society for Socially Embedded Technologies (EUSSET). 2019_ . DOI: 10.18420/ecscw2019_ep06. URL: `http://dx.doi.org/10.18420/ecscw2019_ep06` . 

Datum 

Unterschrift 

i 

# **Abstract** 

File synchronizers are tools with the goal to facilitate collaboration scenarios and data management across multiple devices. They replicate the file system, e.g. from a cloud storage to a device disk, achieving convergence by only transmitting detected changes. A popular variant available in a plethora of widely adopted products are state-based file synchronizers such as Dropbox. They detect operations by computing the difference between a previously persisted state and the respective current state, performing a bi-directional synchronization of two replicas. While users rely on synchronization to run without errors, bugs in industrial synchronizers make this difficult. Users often have to detect and fix synchronization errors themselves, and some errors remain undetected for a long time. This results in cost-intensive iterations in cooperation processes, which should be avoided in academia and industry. 

This work identifies three core challenges of state-based file synchronization. The first challenge is the heterogeneity of different file systems, which requires the file synchronizer to detect and handle incompatible capabilities. Second, a synchronizer needs to detect and resolve conflicting operations that result from a group of users working on their replica in isolation. Third, non-conflicting operations computed from state differencing are not immediately suitable for propagation. The operation order is not available and operations may be affected by consolidation, such that important intermediate operations are missing. This problem most notably affects file systems that support the move operation which changes an object’s parent folder. The goal of this work is to design and analyze an algorithm and develop an implementation of a file synchronizer that solves these challenges. 

To address heterogeneity we analyze existing real-world implementations (such as the NTFS file system) and their compatibility issues, and also examine related academic works. We identify six file system capabilities relevant to file synchronizers, formally define a file system model _F_ that is in large parts compatible to the various existing definitions and suggest several alternatives for handling incompatible differences. To detect conflicts we perform a precondition analysis of the operations of _F_ . Resolving conflicts is an open problem where the right approach depends on the context. Our related work analysis finds that most academic works present arbitrary resolution methods that lack a rationale for their decisions. To determine a reflected conflict resolution approach we design a four-step framework which starts with an informal definition of consistency properties and iteratively refines it to a set of formal and detailed steps for resolving concrete conflicts. 

Apart from _F_ and a conflict resolution approach the main contribution of this work is Syncpal, an iterative algorithm that reconciles two divergent file systems, solving all of the above challenges. It first handles conflicts, one at a time, such that resolving one conflict does not negatively affect others. Whenever possible, conflicts are avoided. It then finds a valid propagation order for the remaining non-conflicting operations, breaking cyclic dependencies if necessary. The iterative nature of Syncpal reduces the overall complexity and the probability of bugs. The technical evaluation of our implementation of Syncpal includes its complexity analysis, automated testing and a comparison with five industrial-grade file synchronizers. We find that our algorithm improves the handling of file system heterogeneity and synchronizes changes from long offline periods correctly, where other implementations fail and may even cause data loss. Our implementation has been in operation by 30 users over a period of over 18 months, providing valuable insights for further research regarding usage patterns and practical requirements. 

iii 

# **Zusammenfassung** 

File Synchronizer sind Tools, die Kollaborationsszenarien und Daten-Management über mehrere Geräte hinweg vereinfachen sollen. Sie replizieren Dateisysteme, z. B. von Cloud-Speichern auf Festplatten. Dateisystem-Konvergenz wird erreicht, indem nur Änderungen übertragen werden. In der Praxis haben sich zustandsbasierte File Synchronizer wie Dropbox oder ähnliche Produkte durchgesetzt. Sie erkennen Operationen via Differenzbildung aus einem zuvor persistierten und dem aktuellen Zustand zweier Dateisystem-Replikate und erreichen dadurch deren bidirektionale Synchronisation. Benutzer sind darauf angewiesen, dass die Synchronisation fehlerfrei abläuft. Marktübliche File Synchronizer erschweren dies jedoch, sodass Benutzer Synchronisationsfehler oft selbst erkennen und unter großem Zeitaufwand beheben müssen und manche Fehler lange Zeit unerkannt bleiben. Dadurch entstehen im Kooperationsprozess kostenintensive Iterationen, die es in Wissenschaft und Wirtschaft zu vermeiden gilt. 

Diese Arbeit identifiziert drei zentrale Defizite der zustandsbasierten Dateisynchronisation. Erstens bedingt die Heterogenität verschiedener Dateisysteme, dass File Synchronizer inkompatible Eigenschaften oft nicht erkennen und behandeln. Zweitens muss ein Synchronizer Konflikte erkennen und lösen, die durch den isolierten Zugriff mehrerer Benutzer auf Dateisysteme entstehen. Drittens sind konfliktfreie Operationen, die via Differenzbildung der Zustände erkannt wurden, nicht unmittelbar für die Übertragung geeignet. Hier ist die Reihenfolge der Operationen unbekannt und diese wurden evtl. konsolidiert, sodass wichtige Zwischenoperationen fehlen. Dieses Problem betrifft insbesondere Dateisysteme, die Verschiebe-Operationen unterstützen, die den Überordner eines Objekts ändern. Ziel dieser Arbeit ist es, diese Defizite durch einen neuartigen, verbesserten File Synchronizer zu beheben. 

Zum besseren Verständnis der Heterogenität analysiert diese Arbeit bestehende wissenschaftliche Arbeiten und reale Dateisystem-Implementierungen wie NTFS und deren Kompatibilität. Der Autor identifiziert sechs Dateisystem-Fähigkeiten, definiert ein formales Dateisystem-Modell _F_ , das zu bestehenden Definitionen größtenteils kompatibel ist, und schlägt mehrere Alternativen für den Umgang mit inkompatiblen Eigenschaften vor. Zur Konflikt-Identifizierung wird eine Vorbedingungsanalyse der Operationen von _F_ durchgeführt. Das Lösen von Konflikten ist diffizil, da der geeignete Ansatz vom Kontext abhängt. Ein Großteil wissenschaftlicher Arbeiten löst Konflikte willkürlich und ohne Begründung der konkreten Entscheidungen auf. Zur Findung eines reflektierten Konfliktlösungsansatzes hat der Autor ein vierstufiges Framework entwickelt, das eine informelle Definition von Konsistenzeigenschaften iterativ zu einer Menge von formal definierten, detaillierten Konfliktauflösungsschritten verfeinert. 

Neben _F_ und dem obigen Konfliktlösungsansatz ist der Hauptbeitrag dieser Arbeit der iterative Algorithmus _Syncpal_ , der die Abweichungen zweier Dateisysteme synchronisiert und die genannten Defizite löst. Konflikte werden nacheinander behandelt, ohne dass sich die Lösung eines Konflikts negativ auf andere auswirkt. Wenn möglich werden Konflikte vermieden. Anschließend werden eine valide Übertragungsreihenfolge der verbleibenden, konfliktfreien Operationen ermittelt und eventuelle zyklische Abhängigkeiten aufgelöst. Dieser iterative Ansatz reduziert die Gesamtkomplexität und die Wahrscheinlichkeit von Programmierfehlern. Die technische Evaluation der Syncpal-Implementierung umfasst eine Komplexitätsanalyse, automatisierte Tests sowie einen Vergleich mit fünf weiteren, etablierten File Synchronizern. Die Ergebnisse zeigen, dass Syncpal die Dateisystem-Heterogenität besser bewältigt und Änderungen aus langen Offline-Phasen korrekt synchronisiert. Andere Implementierungen scheitern hier häufig, was bis hin zu Datenverlust führen kann. Die Verwendung der Syncpal-Implementierung durch 30 Benutzer über einen Zeitraum von über 18 Monaten liefert für weitere Forschungsfragen wertvolle Einblicke bzgl. Nutzerverhalten und Anforderungen aus der Praxis. 

v 

# **Acknowledgements** 

This work would not have been possible without my employer, Fraunhofer FIT, who provided the freedom to work on the thesis alongside day-to-day project work. I specifically want to thank my department heads Dr. Leif Oppermann and Prof. Wolfgang Prinz for encouraging me to turn the file synchronizer from being just project work into a PhD thesis. They enabled a PhD-friendly environment on the job by giving me the chance to work on projects that contextually overlap with the thesis. Fraunhofer FIT has also been supportive financially by sponsoring technical writing programs and paying for conferences. 

Many thanks go to my thesis advisors Prof. Matthias Jarke and Prof. Wolfgang Prinz who not only helped shape this work by giving valuable input, but also considerably improved several conference papers. I’d also like to express gratitude to my colleague Martin Kretschmer for the extensive discussions, his input and proof reading of many theoretical sections of this work. I would like to thank all colleagues of the CSCW and MARS group at Fraunhofer FIT for the vivid exchange regarding file synchronization. They have been using my file synchronizer implementation for over two years and their input has been invaluable to identify and fix programming errors and to design new software features based on real user requirements. 

To my parents I’m grateful for their kindness and support over the years, not only during the thesis but also any other period of my life. I finally want to thank my partner Julia for being loving, understanding and supportive at all times, reminding me that there more things to life than the thesis. 

Marius Alwin Shekow _July 2019_ 

vi 

# **Contents** 

|**1**<br>**Intr**|**oductio**|**n**||**1**|
|---|---|---|---|---|
|1.1|Motiv|ation . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>1|
|1.2|Goals|and resea|rch questions . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>2|
|1.3|Resear|ch metho|dology . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>3|
|1.4|Contri|butions|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>3|
|1.5|Thesis|structure|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>3|
|**2**<br>**Rela**|**ted Wo**|**rk**||**5**|
|2.1|Distri|buted and|replicated systems . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>5|
||2.1.1|Consiste|ncy . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>6|
||2.1.2|Consiste|ncy-availability trade-off . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>6|
||2.1.3|Optimis|tic and pessimistic replication systems . . . . . . . . . . . .|. . . . . . . . .<br>7|
|2.2|Achiev|ing consi|stency in optimistic replication systems<br>. . . . . . . . . . .|. . . . . . . . .<br>8|
||2.2.1|Charact|eristics of optimistic replication systems . . . . . . . . . . .|. . . . . . . . .<br>8|
|||2.2.1.1|Directionality . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>8|
|||2.2.1.2|Consistency algorithm data . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>9|
|||2.2.1.3|Communication topology . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>10|
|||2.2.1.4|Transferred data<br>. . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>10|
|||2.2.1.5|Coupling to application . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>10|
|||2.2.1.6|Heterogeneity . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>11|
|||2.2.1.7|Invocation . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>11|
|||2.2.1.8|Conflict resolution . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>11|
|||2.2.1.9|File system state and operations model . . . . . . . . . . .|. . . . . . . . .<br>11|
||2.2.2|Bi-direc|tional approaches . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>12|
|||2.2.2.1|State-based . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>12|
|||2.2.2.2|Operation-based . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>13|
||2.2.3|Uni-dire|ctional approaches . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>13|
|||2.2.3.1|Operational Transformation . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>13|
|||2.2.3.2|CRDT . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>14|
|2.3|File sy|stems . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>14|
||2.3.1|File syst i|em specifications . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>15|
||2.3.2|Real-wo i|rld file system operation logs . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>17|
|2.4|File sy|nchroniz|ers . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>17|
||2.4.1|State- vs|. operation-based approaches . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>17|
||2.4.2|State-ba|sed approaches . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>19|
||2.4.3|Operati|on-based approaches . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>22|
||2.4.4|Summa|ry<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>23|
|2.5|Relate i|d fields an|i d technologies . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>23|
||2.5.1|Version|Control Systems . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>23|
||2.5.2|Techniq|ues for detecting causality and concurrency . . . . . . . . .|. . . . . . . . .<br>24|
||2.5.3|Distribu|ted File Systems . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>24|
||2.5.4|Distribu|ted databases . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . .<br>25|



vii 

_CONTENTS_ 

||2.5.5|SyncML|/ OMA DS . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>25|
|---|---|---|---|---|
|2.6|Concl|usion . . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>26|
|**3**<br>**File     i**|**system    i**|**s - analysi  i**|**s and definition**|**29**|
|3.1|Capab|ility analy|sis . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>29|
||3.1.1|Physical|object & namespace mapping . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>30|
|||3.1.1.1<br>|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .<br>|. . . . . . . . . .<br>30<br>|
|||3.1.1.2|Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>30|
|||3.1.1.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>31|
|||3.1.1.4<br>|Advice for handling incompatibilities . . . . . . . . . . .<br>|. . . . . . . . . .<br>31<br>|
||3.1.2|Supporte|d object types . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>32|
|||3.1.2.1|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>32|
|||3.1.2.2|i<br>Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>32|
|||3.1.2.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>33|
|||3.1.2.4|Advice for handling incompatibilities . . . . . . . . . . .|. . . . . . . . . .<br>33|
||3.1.3|Operatio|ns and atomicity . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>33|
|||3.1.3.1|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>33|
|||3.1.3.2|Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>33|
|||3.1.3.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>34|
|||3.1.3.4|i<br>Advice for handling incompatibilities . . . . . . . . . . .|. . . . . . . . . .<br>35|
||3.1.4|Namespa|ce limitations . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>35|
|||3.1.4.1|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>35|
|||3.1.4.2|Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>35|
|||3.1.4.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>37|
|||3.1.4.4|Advice for handling incompatibilities . . . . . . . . . . .|. . . . . . . . . .<br>38|
||3.1.5|Meta-dat|a . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>38|
|||3.1.5.1|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>38|
|||3.1.5.2|i<br>Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>38|
|||3.1.5.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>38|
|||3.1.5.4|Advice for handling incompatibilities . . . . . . . . . . .|. . . . . . . . . .<br>39|
||3.1.6|Locking|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>39|
|||3.1.6.1|Significance . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>39|
|||3.1.6.2|Analysis<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>39|
|||3.1.6.3|Derived unified model . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>40|
|||3.1.6.4|Advice for handling incompatibilities . . . . . . . . . . .|. . . . . . . . . .<br>40|
||3.1.7|Summar|y<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>40|
|3.2|File sy  i|stem mod i|el definition . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>41|
||3.2.1|File syste|m constituents . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>41|
||3.2.2|File syste|m operations . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>42|
|3.3|Concl|usion . . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>43|
|**4**<br>**Upd   i**|**ate det  i**|**ection for i**|**file systems**|**45**|
|4.1|Analys|is and ove|rview . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>45|
||<br>4.1.1|<br>File syste|<br>m API analysis . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>45|
||4.1.2|<br>Taxonom|<br>y . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>46|
|4.2|State-|based upd|ate detection . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>47|
||<br>4.2.1|<br>Snapsho|<br>ts . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>47|
||4.2.2|Database|snapshots . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>47|
||4.2.3|<br>Operatio|<br>n computation . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>48|
||424|Udt t||50|
||..|pae|ees . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>|
|||4.2.4.1|Change-events . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>52|
|||4.2.4.2<br>|Generation<br>. . . . . . . . . . . . . . . . . . . . . . . . . .<br>|. . . . . . . . . .<br>52|
||4.2.5|Operatio|n consolidation<br>. . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>53|
||4.2.6|<br>Side effe|<br>cts of operation consolidation . . . . . . . . . . . . . . . .|. . . . . . . . . .<br>54|



viii 

_CONTENTS_ 

|||4.2.6.1<br>Lack of serialization order . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>54<br><br> <br>|
|---|---|---|
|||4.2.6.2<br>Cycles from missing move operations . . . . . . . . . . . . . . . . . . . . .<br>54|
||4.2.7|<br>Addressing side effects in a synchronizer . . . . . . . . . . . . . . . . . . . . . . . . .<br>54|
|||<br>4.2.7.1<br>Operation sorting<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>56|
|||4.2.7.2<br>Detecting cycles<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>57|
|||4.2.7.3<br>Breaking cycles . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>59|
|4.3|Hybrid|update detection . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>59|
||4.3.1|Advantages<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>59|
||4.3.2|Implementation algorithm . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>60|
||4.3.3|Caveats and workarounds . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>61|
|||4.3.3.1<br>Windows . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>61|
|||4.3.3.2<br>macOS . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>62|
|||4.3.3.3<br>BSCW<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>63|
||4.3.4|Summary<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>63|
|4.4|Concl|usion . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>63|
|**5**<br>**Con  l  i**|**sistency  l  i**|**and conflicts for file synchronizers**<br>**65**|
|5.1|Relate|d work analysis . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>66|
||5.1.1|Conflicts . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>66|
||5.1.2|Consistency philosophies . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>69|
|5.2|Consis|tency philosophy<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>70|
|5.3|Confli|lct resolution policies . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>71|
||5.3.1|Resolution mode: automatic vs. manual . . . . . . . . . . . . . . . . . . . . . . . . .<br>71|
|||5.3.1.1<br>Choosing an approach . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>71|
|||5.3.1.2<br>Challenges of automatic resolution . . . . . . . . . . . . . . . . . . . . . .<br>72|
||5.3.2|Criteria for choosing a winner in automatic resolution . . . . . . . . . . . . . . . . .<br>73|
|5.4|Confli|lcts patterns . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>75<br>|
||5.4.1|Overview . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>75|
||5.4.2|Pseudo conflict . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>76|
||5.4.3|Name clash conflict . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>77|
||5.4.4|Edit conflict . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>78|
||5.4.5|Delete conflict<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>79|
||5.4.6|Move conflict . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>80|
||5.4.7|Indirect conflicts . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>80|
|5.5|Confli|lcts . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>80|
||l<br>5.5.1|l<br>Create-Create . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>80|
||5.5.2|Edit-Edit . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>82|
||5.5.3|Move-Create<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>82|
||5.5.4|Edit-Delete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>83|
||5.5.5|Move-Delete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>84|
||5.5.6|Move-ParentDelete . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>87|
||5.5.7|Create-ParentDelete . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>88|
||5.5.8|Move-Move (Source) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>89|
||5.5.9|Move-Move (Dest) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>89|
||5.5.10|Move-Move (Cycle) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>90|
||5.5.11|<br>Conflict type completeness . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>90|
|5.6|Applic  l|ability of conflicts . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>91|
|5.7|l<br>Iterati l|l<br>ve conflict resolution<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>91|
||l<br>5.7.1|l<br>Introduction<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>91|
||5.7.2|Conflict type sort order . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>94|
|||5.7.2.1<br>Same priority conflict combinations<br>. . . . . . . . . . . . . . . . . . . . .<br>97|
|||5.7.2.2<br>Different priority conflict combinations . . . . . . . . . . . . . . . . . . .<br>97|
|5.8|Proof<br>5.8.1|of termination . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>99<br>Overview . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .<br>99|



ix 

_CONTENTS_ 

||5.8.2|Move-M<br>|ove (Source) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . <br> <br>|. . . . . . . . 101<br>|
|---|---|---|---|---|
|||5.8.2.1|Undo move is possible<br>. . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 101|
|||5.8.2.2|<br>Undo move is impossible . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 102|
||5.8.3|Create-C|reate . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 102|
||5.8.4|Move-C|reate<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 104|
||5.8.5|Move-M|ove (Dest) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 104|
||5.8.6|Move-M|ove (Cycle) . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 105|
|||5.8.6.1|Undo move is possible<br>. . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 105|
|||5.8.6.2|Undo move is impossible . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 105|
||5.8.7|Edit-Del|<br>ete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 105|
||5.8.8|Edit-Edi|t . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 107|
||5.8.9|Move-D|elete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 107|
||5.8.10|Move-P|arentDelete . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 110|
||5.8.11|Create-P|arentDelete . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 110|
|5.9|Concl|usion . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 111|
|**6**<br>**Stat**|**ic synch**|**ronizatio**|**n**|**113**|
|6.1|Algori|thm overv|iew . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 113|
||<br>6.1.1|<br>Update|<br>detection . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 113|
||6.1.2|Reconci|liation<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 115|
||6.1.3|Propaga|tion . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 115|
|6.2|Recon|ciliation|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 115|
|6.3|Propa|gation<br>.|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 116|
||6.3.1|Operati|on details . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 117|
|||6.3.1.1|Create operation . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 117|
|||6.3.1.2|<br>Edit operation . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 117|
|||6.3.1.3|<br>Move operation . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 118|
|||6.3.1.4|Delete operation . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 118|
||6.3.2|Breakin|g cycles in operation sorting . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 119|
|||6.3.2.1|Introduction<br>. . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 119|
|||6.3.2.2|From uni- to bi-directional synchronization . . . . . . . . .|. . . . . . . . 119|
|||6.3.2.3|Resolution rename operation . . . . . . . . . . . . . . . . . .|. . . . . . . . 120|
|||6.3.2.4|Effect and examples . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 120|
|||6.3.2.5|Handling cross-replica operation dependencies<br>. . . . . .|. . . . . . . . 122|
|||6.3.2.6|Proofs of termination<br>. . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 125|
||6.3.3|Operati|on execution challenges . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 125|
|||6.3.3.1|Concurrency handling . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 125|
|||6.3.3.2|File system tricks to emulate atomicity . . . . . . . . . . . .|. . . . . . . . 126|
|||6.3.3.3|Path computation<br>. . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 127|
|6.4|Termi|nation an|d correctness<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 127|
|6.5|Concl|usion . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 128|
|**7**<br>**Imp**|**lement**|**ation**||**129**|
|7.1|Iterati|ons of dev|elopment . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 129|
||7.1.1|Backgro|und . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 129|
||7.1.2|Develop|ment prototypes . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 130|
||7.1.3|<br>Beta ver|<br>sions . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 131|
|7.2|Softwa|re archite|cture . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 131|
||7.2.1|Platform|Inconsistency Checker . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 135|
|7.3|Dyna|mic synch|ronization . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 135|
||7.3.1|Require|ments . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 136|
||7.3.2|Implem|entation<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 136|
|7.4<br>7.5|Softwa<br>Lesson|re deploy<br>s learned|ment . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . <br>  . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . 138<br>. . . . . . . . 138|



_CONTENTS_ 

x 

||7.5.1|Partial s<br>|ynchronization . . . . . . . . . . . . . . . . . . . . . . . . . <br>|. . . . . . . . . . 138<br>|
|---|---|---|---|---|
||7.5.2|Handlin|g of temporary objects . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 139|
||7.5.3|<br>Non-det i|<br>erministic file system behavior . . . . . . . . . . . . . . . .|. . . . . . . . . . 139|
||7.5.4|Bug tria|ge . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 140|
||7.5.5|Platform|incompatibilities<br>. . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 142|
|7.6|Concl|usion . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 142|
|**8**<br>**Eval**<br>|**uation**<br>|||**143**<br>|
|8.1|Autom|ated testi|ng to verify correctness . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 143|
||8.1.1|Hand-cr|afted tests . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 144|
||8.1.2|Generat|ed deterministic tests<br>. . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 145|
||8.1.3|Generat|ed random tests . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 146|
|8.2|File sy|nchronize|r comparative test . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 147|
||<br>8.2.1|<br>Related|<br>work<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 148|
||8.2.2|Synchro|nizers under test . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 148|
||8.2.3|Test cate|gories<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
|||8.2.3.1|Conflict-free operations . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
|||8.2.3.2|Conflict operations . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
|||8.2.3.3|l<br>Cross-platform issues . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
||8.2.4|Test fra|mework and setup . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
|||<br>8.2.4.1|<br>Requirements . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 150|
|||8.2.4.2|Testing method . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 151|
|||8.2.4.3|Architecture and setup<br>. . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 151|
|||8.2.4.4|Test result analysis . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 153|
|||8.2.4.5|Implementation<br>. . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 154|
||8.2.5|Test des|criptions and results . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 154|
|||8.2.5.1|Conflict-free operation tests description . . . . . . . . .|. . . . . . . . . . 155|
|||8.2.5.2|l<br>Conflict-free operation test results<br>. . . . . . . . . . . .|. . . . . . . . . . 156|
|||8.2.5.3|Conflict operation tests description . . . . . . . . . . . .|. . . . . . . . . . 157|
|||8.2.5.4|Conflict operation test results<br>. . . . . . . . . . . . . . .|. . . . . . . . . . 158|
|||8.2.5.5|Cross-platform issue tests description<br>. . . . . . . . . .|. . . . . . . . . . 160|
|||8.2.5.6|Cross-platform issue test results . . . . . . . . . . . . . .|. . . . . . . . . . 163|
||8.2.6|Summar|y and discussion . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 163|
|||8.2.6.1|Synchronizer-specific issues . . . . . . . . . . . . . . . . <br>l|. . . . . . . . . . 163<br>|
|||8.2.6.2|Conflict-free operations . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 165|
|||8.2.6.3|Conflict operations . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 165|
|||8.2.6.4|l<br>Cross-platform issues . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 165|
|||8.2.6.5|Discussion . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 166|
|8.3|Synch  l|ronization  l|and conflict statistics . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 166|
||8.3.1|Related|work analysis . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 166|
||8.3.2|<br>Data col|<br>lection . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 167|
||8.3.3|Results|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 167|
|||8.3.3.1|Operation analysis . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 167|
|||8.3.3.2|Conflict analysis<br>. . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 169|
|8.4|Comp|lexity and|l<br>performance analysis . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 169|
||8.4.1|Comple|xity analysis . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 169|
||8.4.2|<br>Perform|<br>ance analysis . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 172|
|||<br>8.4.2.1|<br>Result analysis<br>. . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 172|
|||8.4.2.2|<br>Future optimizations<br>. . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 173|
|8.5|Concl|usion . .|. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 174|
|**9**<br>**Con**|**clusion**|**and outl**|**ook**|**175**|
|9.1<br>9.2|Discu<br>Limita|ssion of re<br>tions . .|search questions . . . . . . . . . . . . . . . . . . . . . . . . <br> . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . 175<br> . . . . . . . . . . 176|



xi 

|9.3|Future|work . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 177|
|---|---|---|---|
|9.4|Closin|g words<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 178|
|**Bibliog**|**raphy**||**179**|
|**A**<br>**App**|**endix**<br>||**193**<br>|
|A.1|File sy|stem meta-data handling . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 193|
||A.1.1|System-generated meta-data . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 193|
||A.1.2|Writing arbitrary meta-data . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 194|
||A.1.3|<br>Authorization . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 195|
|A.2|Altern i  i|ative file system definitions . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 197|
||A.2.1|File system definition for H-All . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 197|
||A.2.2|i<br>Conflict definitions for H-All . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 198|
||A.2.3|l i<br>File system definition for NED-All . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 200|
||A.2.4|i<br>Conflict definitions for NED-All . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 201|
|A.3|Proof f|or impossible move operation cycles . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 203|
|A.4|Opera|tion reordering methods<br>. . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 205|
|A.5|Findin l|g conflicts in snapshots . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 209|
||A.5.1|Corresponding object id . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 209|
||A.5.2|Corresponding object id (direct) . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 209|
|A.6|Findin l|g conflicts in update trees<br>. . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 211|
||A.6.1|Finding conflicts . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 211|
||A.6.2|Create-Create conflict . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 211|
||A.6.3|Edit-Edit conflict . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 212|
||A.6.4|Move-Create<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 212|
||A.6.5|Edit-Delete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 212|
||A.6.6|Move-Delete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 212|
||A.6.7|Move-ParentDelete . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 213|
||A.6.8|Create-ParentDelete . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 213|
||A.6.9|Move-Move (Source) . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 213|
||A.6.10|Move-Move (Dest) . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 214|
||A.6.11|Move-Move (Cycle) . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 214|
|A.7|Resolv l|ing conflicts in update trees . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 215|
||A.7.1|Undoing a move<br>. . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 215|
||A.7.2|Move-Delete<br>. . . . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 215|
|A.8|File sy|nchronizer comparative test details . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 219|
||A.8.1|Conflict-free operations . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 219|
|||A.8.1.1<br>Result remarks<br>. . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 219|
|||A.8.1.2<br>Complex single-replica operations<br>. . . . . . . . . .|. . . . . . . . . . . . 219|
|||A.8.1.3<br>Multi-level operations . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 225|
||A.8.2|Conflict operations . . . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 229|
|||A.8.2.1<br>Expected result alternatives . . . . . . . . . . . . . . .|. . . . . . . . . . . . 229|
|||A.8.2.2<br>Result remarks<br>. . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 231|
||A.8.3|Cross-platform issues . . . . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 235|
|||<br>A.8.3.1<br>Result remarks<br>. . . . . . . . . . . . . . . . . . . . . .|. . . . . . . . . . . . 235|
|**Publica**|**tions of**|**the author**|**241**|
|**Curricu**|**lum Vit**|**ae**|**243**|



1 

## **Chapter 1** 

# **Introduction** 

Since decades file-based tools, such as word processors, have been a core component in the daily workflow of users of computing devices. Users create large parts of their data in the form of files, which are stored and distributed on multiple devices and storage systems in a hierarchical _file system_ . Tasks such as retrieving a specific file are already challenging when working only on the local disk [RSK04]. Difficulty increases tremendously if a user wants to find the correct version of a file, when it could be located on her mobile phone, personal computer, work laptop, company cloud storage, file server, or group ware system [DP08; JOO15; SW13; Voi+06]. 

To avoid that users need to manually locate and copy files, one approach to ease this data management problem is to consolidate all storage systems to a single, logical namespace. Variant #1 is to _map_ each storage into a local directory on the device. However, the configuration is challenging for novice users, there are security risks and not all storage systems can be assumed to be “online” at every point of time<sup>1</sup> . Variant #2 is to employ _replication_ of the data. Programs called _file synchronizers_ [BP98] keep one or more local directories synchronized with other storage systems. They automatically recognize conflicts and either automatically solve them or allow the user to choose a resolution. 

With the increased availability and affordability of cloud services [Yan+16], cloud storage services like Dropbox, Google Drive and OneDrive have become popular over the last ten years, indicated by the high number of their users [Kol16; Pri17; TBM13]. Once the user installs the corresponding file synchronizer of the cloud storage provider on every computing device, it will maintain the up-to-date versions of all files on all devices, by continuously performing a bi-directional, pair-wise synchronization between each device and the cloud storage system. This also facilitates _collaborative_ scenarios involving _several_ users, because files are no longer located on an isolated system such as the local device storage. Instead files are transparently shared with others, thus supporting a group to develop documents in a cooperative way. 

### **1.1 Motivation** 

We wrote this thesis for two reasons. First, we found that file synchronizer literature is very sparse in academia., s.t. this work can enrich the corpus, in particular in the area of CSCW and cooperative information systems. Second, we observed multiple issues (detailed below) with industrial file synchronizers. By providing up-to-date and thorough research, we hope to inspire file synchronizer developers in academia and industry alike. We envision that future file synchronizer releases make fewer intransparent decisions, cause less frustration for users, and avoid subsequent, manual repairs of directory structures and file contents. Due to the large (and still growing) user-base of these tools, their improvement does have considerable practical impact. 

Some of the issues we observed first hand with existing implementations include: 

> 1For instance, while it would be technically possible to map the work laptop’s storage into a directory on the personal computer, that directory would be inaccessible while the work laptop is turned off. 

_CHAPTER 1. INTRODUCTION_ 

2 

- The synchronization process gets stuck or shows an error in an “offline scenario”, when the synchronizer reconnects to the storage system after the user worked on the local file system without connectivity for an extended time period. 

- Lack of documentation of conflict detection or resolution behavior. When a conflict happens it is automatically resolved, but synchronizers provide little to no visual feedback to the user. As a consequence they make intransparent decisions, because they silently alter or undo operations which the user thought were successful. Conflict resolution behavior also differs between different implementations. Academic works do not provide reflection or recommendations regarding conflict resolution. 

- The synchronized file systems each have a slightly different definition (in other words: they are slightly incompatible with each other). For instance, the BSCW groupware allows directories to be linked into several parent directories, while desktop file systems (Windows, macOS) do not. There are many other issues, such as varying sets of reserved names or characters. For this reason, file synchronizers are _heterogeneous_ data synchronizers [AC08; Fos+07]. Heterogeneity of file synchronizers is not discussed in academia, and industrial synchronizers handle some aspects incorrectly, causing synchronization errors. 

- When a user requires synchronization with _several_ storage systems, she has to install several file synchronizers, because often each synchronizer is proprietary to a specific storage system. This wastes computing resources and shows the need for a synchronizer that can abstract and synchronize different storage systems. Such tools (e.g. Goodsync or Syncovery<sup>2</sup> ) do exist, but come with a complex user interface which is challenging to use for average users. 

### **1.2 Goals and research questions** 

A heterogeneous synchronizer is typically _state-based_ because alternative _operation-based_ approaches require operation logs that are not ubiquitously available. The term _state-based_ means that the synchronizer detects operations from comparing the current file system _state_ with a persisted state of the previous synchronization. This allows the synchronizer to be operational even when shut down while the user changes the file system. However, detected operations lack information about their exact order which makes their synchronization challenging in certain situations. 

The goal of this work is to build a _state-based_ , heterogeneous, near real-time file synchronizer geared towards non-technical end-users. It synchronizes operations detected in two file system replicas as they happen, typically in a client-server setup. It should support several storage system implementations with _different_ file system definitions, without the need to modify the storage systems themselves. Consequently, the synchronization logic is entirely run on the client. This _client-side reconciliation_ concept is also found in previous works [MT94]. 

The research questions are as follows: 

- **RQ1 - File systems:** What different kinds of file system definitions exist in academia and practice? Which criteria are relevant for file synchronizers? How should a file synchronizer’s internal, abstracted file system model look like, which incompatibilities exist and how can they be handled? 

- **RQ2 - Operation order:** As operations detected during state-based update detection lack order, but not all operations are commutative, how can a valid order be detected and propagated by a synchronizer? This question was already answered for file system models which do not support _move_ operations, but remains open for file systems which do support them. 

- **RQ3 - Conflicts:** What sets of operations applied to two disconnected file systems are conflicting? How do conflicts depend on the file system model? Can multiple conflicts be combined? What are possible solutions for resolving individual conflicts and conflict combinations? Which conflicts are relevant in practice? How can conflicts be explained to the user? 

- 2See `https://www.goodsync.com` and `https://www.syncovery.com` , retrieved July 21, 2019. 

_1.3. RESEARCH METHODOLOGY_ 

3 

### **1.3 Research methodology** 

We start with literature review of academic file synchronizer works and works of related systems, as well as manuals of real-world systems (e.g. file system APIs) to gain a deep understanding of file systems, conflicts and related algorithms, and to identify shortcomings in existing works. We use that knowledge to design our own synchronization algorithm. Because we focus on _practical_ use, we implement and distribute the synchronization algorithm as an executable program to real users, immediately after designing it. This allows us to discover unexpected real-world issues and to iteratively improve the algorithm. We verify the correctness of our algorithm via theoretical proofs. We also test our _implementation_ for correct behavior by a large number of hand-written and automatically generated software tests. To compare the behavior of our work with other academical and industrial implementations we perform controlled experiments. 

### **1.4 Contributions** 

The main contribution is the development of a state-based reconciliation algorithm called _Syncpal_ , which synchronizes two disconnected file system replicas. The algorithm was published in [She19]. 

Since real-world file systems are heterogeneous, we present an analysis of the heterogeneous traits of file systems in chapter 3, resulting in a unified model compatible with a large set of real-world models. We refer to [SP19] for the corresponding publication. The individual steps of the Syncpal algorithm terminate provably, and synchronization can be interrupted at any time without causing side-effects. Our approach finds a suitable order of the detected operations of the unified file system model (section 4.2.7), which also supports _move_ operations, which complicates the analysis. Syncpal also features a new conflict resolution approach (chapter 5), which includes graceful handling of conflict _combinations_ and avoids conflicts wherever possible. We discover a lack of guidance in academic works w.r.t. conflict handling and develop a reflected philosophy, which discusses several alternatives for resolving conflicts. 

We implemented the algorithm as an executable application for the operating systems Windows and macOS. It has been used by 30 users over a period of over 18 months, which provides insights into daily life conflicts (section 8.3) and helped discover various other issues in practice (section 7.5). We implement an extensive evaluation framework in section 8.2 that benchmarks our implementation, Dropbox, Google Backup and Sync, Microsoft OneDrive and NextCloud, discovering numerous issues in all other implementations. 

### **1.5 Thesis structure** 

We begin with a survey of related works in chapter 2, which unravels the _distributed systems_ research topic and puts our work into context. We identify several characteristics of file synchronizers which help guide the development of our approach. With our goals from section 1.2 in mind, we then focus on _file systems_ in chapter 3, where we first analyze existing implementations and their compatibility issues, followed by extracting and formally defining the file system model we use for the remainder of this work. This formal definition allows to discuss update detection for state-based synchronizers, which is done in chapter 4. Before we present the overall synchronization algorithm in chapter 6, we first take a deep dive into conflicts in chapter 5, starting from the generic concept (file system model-independent) of what conflicts are and what _achieving consistency_ means, down to the concrete approaches for finding and resolving conflicts in the concrete file system model from chapter 3. In chapter 7 we present the implementation of our algorithm, with its software architecture, project background, and unforeseen issues we encountered in practice. We evaluate our algorithm on a _technical_ level (not with users) in chapter 8, and conclude and present future work in chapter 9. 

5 

## **Chapter 2** 

# **Related Work** 

This chapter discusses related work to identify research gaps, find approaches to base our algorithm on, and to put our work into context. We follow a top-down approach, starting with generic concepts and narrowing them down to our specific field: file synchronizers. 

Because file synchronizers are programs that achieve _consistency_ in replicated, _distributed systems_ , we provide an introduction to these concepts in section 2.1. We identify _optimistic replication systems_ as the suitable sub-field, for which we present important characteristics and generic data synchronization approaches in section 2.2. We then examine file synchronizer works specifically in section 2.4, but also look at intricacies of their underlying data structure, file systems, in section 2.3. Finally, we mention related fields and technologies in section 2.5 and conclude in section 2.6, where we make suitable choices regarding our own work for each characteristic found in section 2.2. The research questions that result from gaps in related work are discussed in sections 2.3 (RQ1) and 2.4 (RQ2+3) respectively. 

### **2.1 Distributed and replicated systems** 

From a high-level perspective, a file synchronizer is an application realizing a _distributed system_ . The main characteristic of a distributed system is that its software (the middleware or applications on top of it) is installed on _networked_ computers which exchange asynchronous messages (see [CKD10], p. 1). The system _appears_ as a _single_ system to the user, even though it is distributed. The main advantage (over a software system that runs on a single, isolated computer) is _sharing_ of resources like data or computing power. On the downside, distributed systems have to deal with many challenges, including heterogeneity, openness, security, scalability, failure handling, concurrency or transparency [CKD10]. To understand how a file synchronizer fits into the research area we provide an ontology of distributed systems and relevant sub-variants shown in figure 2.1. Bold boxes highlight the domain to which the kind of file synchronizer belongs which we develop in this work. 

There are various models that describe and categorize distributed systems. For instance, _architectural_ models like the client-server or peer-to-peer model are _“concerned with the placement of [the system’s] parts and the relationships between them”_ [CKD10, Chap 2]. Different variations of those models exist, see [CKD10, sect. 2.2.3], such as extending a “multiple client, one server” model to “multiple clients, multiple servers”. 

One important variant is the use of _replication_ [CKD10, sect. 15]. Here data is replicated (i.e., copied) fully or in part<sup>1</sup> between computers that serve them. Replication offers several advantages [SS05]: it improves availability, because data can still be accessed even when some replicas are unavailable. It also improves performance in terms of lower latency (users can work on nearby replicas) and increased throughput (better bandwidth to nearby replica, and multiple replicas can serve data simultaneously). We will further discuss replication in section 2.1.3. A very important topic in replicated distributed sys- 

1Partial copies with limited life-span are often referred to as _cache_ . 

_CHAPTER 2. RELATED WORK_ 

6 



Figure 2.1: Ontologoy of distributed and replicated systems 

tems is _consistency_ which is _“the property that the state of replicas stay close together”_ [SS05], which we discuss in the next section. 

#### **2.1.1 Consistency** 

_Ideally_ the whole system’s state should be _consistent_ , i.e., behave as if it consisted of a _single_ data repository that is in a specific state. Since replication systems distribute data over multiple computers this means that the state of _all_ replicas should be equal at any point of time. As we will see in section 2.1.3 replicated systems vary in the degree of how and how fast they achieve consistency. Consistency is in fact a large research field. During the last decades over 50 different notions of consistency have been formulated in the form of _models_ , see [VV16; AT16] for a survey. These models specify rules that make the behavior of the system predictable, without specifying how to implement a consistency mechanism. Two models often found in literature are _strong consistency_ (or: _linearizability_ ) [HW90] and _eventual consistency_ [Ter+94]. The former resembles the ideal conditions described above by simulating _sequential_ access to a single replica. When data is written in one replica, once the write operation has finished, the written data must be available for reading at _all_ replicas immediately. In contrast, _eventual consistency_ provides a much weaker guarantee. Here all replicas will _eventually_ converge to the same state, given that no new write operations are generated at any replica. 

#### **2.1.2 Consistency-availability trade-off** 

The two models presented above are placed on rather opposite ends of the consistency spectrum. From their short description one can already derive the implications on _availability_ in the event of a _partition_ , that is, when one or more replicas have become disconnected. In such an event distributed replicated systems using the _strong consistency_ model do practically _not_ offer availability for the read and write 

_2.1. DISTRIBUTED AND REPLICATED SYSTEMS_ 

7 

operations. Since these systems implement the model using _coordination_ , when a write operation is executed at one replica, _all_ other replicas have to confirm the receipt of that operation before it is actually committed. Consequently, the write operation would lock up the system during the time of the partition, because the confirmation messages of one or more replicas are missing. 

In contrast, _eventually consistent_ replication systems offer high degrees of availability. Read and write operations are executed immediately at the replica from which they originate, and are then propagated to other replicas in the background whenever possible. The downside is that this allows the state of replicas to diverge. _Conflicts_ may arise which the system needs to resolve, with or without the involvement of the users. 

This trade-off between consistency and availability has been formally studied. Fischer’s impossibility result [FLP85] shows that reaching perfect consensus in a distributed, asynchronous system is impossible if just one process can fail. Determining the presence of faulty processes is difficult as well. One cannot reliably distinguish a faulty process from one that is slow or rule out that the network is the culprit. More recently, Brewer has established the CAP theorem [Bre00; Bre12], where _C_ refers to strong consistency, _A_ to availability and _P_ to partition tolerance, that is, the ability to detect start and end of a partition and to keep operating during a partition, possibly with degraded functionality<sup>2</sup> . The CAP theorem states that during a partition, replication system designers have to choose between C and A<sup>3</sup> . However, the decision is not binary for the entire system, but can be made in a fine-grained way, choosing different trade-offs for each operation the system offers. For instance, a calendar management application could choose to favor C over A for _write_ and _update_ operations, while favoring A over C for _read_ operations. Different approaches have been formally studied to achieve such _hybrid_ consistency models, see e.g. [Bal+16; Ter+13]. In summary, designers of replication systems are faced with the challenge of choosing a C-A trade-off for each operation such that the choice minimizes the negative effects of a partition and incorporates the users’ willingness to wait for a response as well as their tolerance of data inconsistencies [Bre12]. 

#### **2.1.3 Optimistic and pessimistic replication systems** 

A common subdivision of replication systems is the one into _optimistic_ and _pessimistic_ replication systems [SS05]. Pessimistic systems bear that name because they are pessimistic about the _lack_ of conflicts or the consequence in case a conflict occurs. They prefer strict concurrency control and strong consistency, avoiding conflicts altogether. Optimistic systems are situated on the opposite end. They assume conflicts happen rarely in practice - and if they do, they can be solved after the fact. Therefore little concurrency control exists, together with rather weak consistency guarantees (such as eventual consistency). Classifying a concrete system as one or the other depends on the chosen C-A trade-off. A system that chooses C over A for all operations is a fully pessimistic system, a system that always chooses A over C is a fully optimistic one. Systems in-between employ a Hybrid approach. 

In the following, the advantages and disadvantages of optimistic and pessimistic systems are discussed. 

The main advantage of _pessimistic_ approaches is strong consistency. The disadvantage is that this consistency model is achieved via coordination between all participating nodes, which makes it hard to deploy such systems in scenarios where nodes may only offer ad-hoc connectivity (e.g. tablet computers only turned on occasionally) or in a _geo-distributed_ setting, where computers are connected via _wide-area networks_ such as the Internet. Such networks have low reliability, increasing the risk that one or more replicas are unreachable, blocking the operation from succeeding. Since communication is unreliable, pessimistic systems also don’t scale well, as adding more replicas further increases the chance of communication failure, degrading availability and throughput. These disadvantage inhibit the use of pessimistic approaches in the area of cooperative work. Pessimistic systems are also unsuitable in scenarios such as software control management (SCM), where individuals need the ability to work on 

> 2In general, forfeiting _P_ is not an option because in practice partitions can and will happen (even in high-availability datacenters). In such an event, a system that has not implemented _P_ loses _both_ C and A. 

> 3While the system is _not_ partitioned, _both_ C and A can be provided. 

_CHAPTER 2. RELATED WORK_ 

8 

files in isolation, not locking them for other users. In this case users prefer A over C, accepting the risk of conflicts due to concurrent changes. 

_Optimistic_ systems solve the disadvantages of pessimistic systems. They are suited for (and scale well in) wide-area networks, by offering high availability. Their disadvantage is the weak consistency model, which increases the chance of conflicts. 

### **2.2 Achieving consistency in optimistic replication systems** 

A system that achieves consistency in optimistic replication systems is often referred to as _data synchronizer_ . In literature the term _synchronization_ appears frequently and is, unfortunately, used with two different meanings, depending on whether it is applied to _processes_ or _data_ . _Process_ synchronization deals with issues that arise when concurrent processes (or threads) need to occasionally join and agree on certain actions, and to avoid race conditions. In this work we refer to _data_ synchronization, which deals with consistency and integrity of disconnected data replicas. We consider the definition of [AC08] suitable who define that _“synchronization is the process of enforcing consistency among a set of [replicated] artifacts and synchronizers are procedures that automate - fully or in part - the synchronization process.“_ 

We start by presenting several _characteristics_ of data synchronizers in section 2.2.1. We consider the _directionality_ characteristic the most distinct one, and thus present corresponding approaches in sections 2.2.2 and 2.2.3. 

#### **2.2.1 Characteristics of optimistic replication systems** 

We have compiled a selection shown in figure 2.2 from related works such as [SS05] and [MT94] and will elaborate on them in the following subsections. It should be noted that various other characteristics exist, such as _syntactic vs. semantic scheduling_ , or _single-master vs. multi-master_ [SS05]. 

##### **2.2.1.1 Directionality** 

_Directionality_ refers to the communication pattern of a synchronizer. 

An _uni_ -directional synchronizer determines the data to be transmitted and sends it to other replicas, without further concern what these replicas will do with it. This data might be the current state of the replica or a list of executed operations since the last synchronization. 

_Bi_ -directional synchronization<sup>4</sup> indicates that information flows in both directions, that is, data is exchanged via a full-duplex channel, making the consistency algorithm aware of the current state (or operations) of both replicas at once. Traditionally such systems have a _synchronization phase_ where, conceptually, all replicas take part in a roundtable meeting with the agenda to achieve consistency. The process works as follows: 

1. First, all available replicas establish a connection with each other and _lock_ access to their data, to avoid that the user can modify data during the synchronization phase. 

2. Replicas exchange data (state or operations) and the merge algorithm (which may be distributed or centralized) determines consistency, given the data from all replicas. It determines what the final, consistent state should be, or which operations need to be propagated to each replica to achieve global consistency. This includes _finding_ conflicts and possibly resolving them, e.g. automatically or by asking the user. 

3. All replicas exchange states or operations data, apply it to their own replica and finally unlock access to their data again. 

> 4Replace _bi-directional_ (which applies to _star_ communication topology) with _N_ -directional in case of a _peer-to-peer_ communication topology. 

_2.2. ACHIEVING CONSISTENCY IN OPTIMISTIC REPLICATION SYSTEMS_ 

9 



Figure 2.2: Characteristics of optimistic replication systems 

In practice such approaches work best when synchronization involves only _two_ replicas at a time, because the likelihood of failure increases for N-directional ( _N >_ 2) synchronization when using unreliable communication channels (such as the Internet). 

A number of issues have been identified for systems that use uni-directional synchronization. Here conflicts are resolved on the receiving replica. The sending replica does not immediately know about detected conflicts or their resolution. In the event of a conflict, the sending replica, which is still oblivious of that conflict, may apply additional operations on objects affected by the conflict. These update operations need careful handling. In [CJ05] the authors approach this problem with vector time pairs, whereas [TRN15] solve it by creating a special conflict resolution function that can redirect the updates happening after a conflict update to the correct targets. 

##### **2.2.1.2 Consistency algorithm data** 

An algorithm whose job is to achieve consistency of two or more replicas needs data. Typically, this data comes in the form of complete _operation logs_ or _state_ . The former requires tight integration of the consistency mechanism with the application that manages the data (see _coupling_ , section 2.2.1.5). The 

_CHAPTER 2. RELATED WORK_ 

10 

logs describe _all_ operations that took place at each replica since the last time consistency was achieved. In the latter case the entire state of the application’s data is used. In some cases the algorithms, given the current and a previous state, first compute operations from state deltas. 

We note that the data _processed_ by the algorithm does not necessarily have to coincide with the data that is being _transferred_ from one replica to another [Csi16], see _transferred data_ characteristic in section 2.2.1.4. 

##### **2.2.1.3 Communication topology** 

Communication topology (or _network topology_ ) describes the way networked computers are connected to each other. Optimistic replication systems often use the _star_ or (arbitrarily connected) _peer-to-peer_ topology, but many others exist, such as _bus_ or _ring_ . The choice of topology has a strong influence on aspects like availability, load balancing and speed. _“At one end of the spectrum, the star topology boasts quick propagation, but its hub site could become overloaded, slowing down propagation in practice; it is also a single point of failure. A random topology, on the other hand, is slower but has extremely high availability and balances load well among sites.”_ [SS05] Systems with a peer-to-peer topology are often _uni-directional_ . 

##### **2.2.1.4 Transferred data** 

The transferred data can, again, be the application’s state, state deltas or operation logs. To illustrate that _transferred_ data and _processed_ data don’t have to coincide, consider the following two examples: 

1. A file synchronizer may achieve consistency between two replicas by sending the entire state from one replica to another. When state data is received, operations are computed from performing a _diff_ (or _delta_ ) of the received state with a persisted state from the last successful synchronization. These operations are then used by the consistency algorithm. 

2. A file synchronizer whose consistency algorithm operates on _state_ data may choose to _transfer_ only the _initial_ state from replica X to Y. X’s state data is cached at Y and subsequently X only transfers _operation logs_ to Y. Y applies the received operations to the cached state, producing an updated version that is supplied to its consistency algorithm. 

##### **2.2.1.5 Coupling to application** 

Coupling describes the degree of integration of the consistency algorithm into the application, from a software development point of view. For example, the application might be a calendar management software. We find that coupling typically ranges from _tight_ to _loose_ . _Tight_ coupling means that achieving consistency amongst multiple replicas is built into the application itself, or added as a plug-in in case the application allows for extension of functionality this way. Typically, consistency algorithms based on _operation logs_ are tightly coupled, because the application needs to provide these logs to the consistency algorithm [Fos+07]. 

On the other end, _loose_ coupling means that the application is not aware of replication or its consistency. Typically, _state-based_ approaches such as [Fos+07] are loosely coupled. They are implemented as a 3rd party program that extracts the entire state from the application on each replica and uses that state to achieve consistency, without the application’s awareness. 

We note that when we classify a system as _loose_ later in this chapter, we refer to the coupling between the synchronizer application and the replica it directly works with. There may still be _intermediate_ systems with _tight_ coupling. For instance, consider a calendar application where a loosely coupled synchronizer achieves consistency between _N_ client replicas. The synchronizer is loosely coupled to the calendar application, but may be tightly coupled to additional servers (and their software) used to exchange data or meta-data. 

_2.2. ACHIEVING CONSISTENCY IN OPTIMISTIC REPLICATION SYSTEMS_ 

11 

##### **2.2.1.6 Heterogeneity** 

A _heterogeneous_ synchronizer [AC08; Fos+07; PSG04; Her+12] allows data on different replicas to be stored using varying schemas or languages. For instance, an address book application may store contacts as a comma-separated value spreadsheet on one replica, but may use a proprietary database engine on another replica. Another example from software engineering, is the model of a program which may exist as source code but also as UML model. Algorithms that achieve consistency need to convert the representations of the data accordingly and handle incompatibilities. In contrast, _homogeneous_ synchronizers are built on systems that store data in the same format on all replicas. _Uni-directional_ replication systems, such as Operational Transformation-based systems, are typically homogeneous. 

##### **2.2.1.7 Invocation** 

Invocation determines the points of time when synchronization is triggered. Synchronizers may follow a _continuous_ approach that attempts to automatically synchronize whenever users update their replica, given that other replicas are available for synchronization. Another solution is _discrete check-pointing_ [MT94] where the user needs to explicitly trigger the synchronization process. 

Each approach has advantages and disadvantages. While continuous synchronizers do their work as often as possible at the expense of high bandwidth usage and lack of control for the user, it lowers the risk for conflicts and avoids divergences of replicas that happen simply because the user may have forgotten to trigger the synchronization. Discrete check-pointing suffers from increased risk for conflicts but saves bandwidth and allows for workflows where users may not actually want automatic synchronization, e.g. because they want to be in control over when updates of their replica are propagated (say, to avoid synchronization of incorrect, intermediate file states, or to avoid high charges for bandwidth) [How93]. Most _uni-directional_ systems use continuous invocation. 

##### **2.2.1.8 Conflict resolution** 

When users make concurrent updates in different replicas in isolation these may be incompatible with each other and cause a conflict. The list of incompatible updates depends on the replica’s data structure. For example, when considering a file system, a conflict occurs if a specific file is deleted in one replica while its content is updated in another replica. Related work distinguishes two general approaches for resolving conflicts [RC01]: 

1. _Manual_ conflict resolution: the conflict is visually presented to the user who is then given multiple resolution options to choose from. Concrete synchronizers differ in whether they can propagate all non-conflicting updates first and present the remaining conflicting updates to the user at the end of the process, or whether conflict resolution is a necessary first step. 

2. _Automatic_ conflict resolution: the synchronizer chooses a resolution for the user and applies it automatically. If users need specific conflicts to be resolved differently, they need to apply the fix manually afterwards. This approach is typically used in synchronizers that run as a transparent background service. 

Which approach is superior to the other one is still an open question in the domain of file systems [RC01]. We elaborate on conflict-related issues later in this work in chapter 5 on page 65. 

##### **2.2.1.9 File system state and operations model** 

While the general idea is that a file synchronizer is a synchronizer that works on file systems, each work defines the state and operations of a file system differently. The most notable difference is whether a file system is modeled as a set of paths without identity, or whether identities are also part of the model. There are several other differences further examined in section 2.3. 

_CHAPTER 2. RELATED WORK_ 

12 

#### **2.2.2 Bi-directional approaches** 

As described in section 2.2.1.1 the algorithm of a bi-directional approach is given data from _all_ replicas and decides how to achieve convergence. We sub-divide them further by the type of _data_ used by the consistency algorithm, which is either _state_ or _operations_ . The following two subsections further elaborate on the respective approaches. 

##### **2.2.2.1 State-based** 

Approaches used in state-based synchronization vary strongly, depending on the supported data structures (and its operations) and other characteristics such as listed in section 2.2.1. We now describe a few exemplary systems. 

**Non-incremental systems** are one of the most simple approaches. They do not store any historic information but only know about the _current_ state of a replica. These systems resort to comparing the respective current states, typically between _two_ replicas at a time. RSync [TM+96] follows this approach. Such approaches are also used in _heterogeneous_ synchronizers (see section 2.2.1.6) where replicas whose state is encoded in different formats are synchronized by _converting_ the _current_ state of one replica to the format of the other one, followed by equalizing the two states by computing the delta/diff between them [AC08]. 

**Three-way merging** (3wm) is a technique often used in _incremental_ systems [AC08] that store historic information. It refers to a family of algorithms that are given a base data set _db_ (e.g. the state at the last synchronization) and two independently modified data sets _d_ 1, _d_ 2 (which are both based on _db_ ). These algorithms then find (or are already given) the differences between _d_ 1 and _db_ (and _d_ 2 and _db_ , respectively) and produce a merged data set _dm_ that contains a merge of both differences and _db_ . 3wm is more powerful than two-way merging because the historic state solves the _create/delete ambiguity_ [GPP93]. Thus, 3wm can reliably conclude whether the absence of an object is due to deletion, or the existence of an object is due to creation. When conflicts are detected, they may be resolved automatically or with user involvement. 

The algorithms strongly depend on the data structure and the operations they provide, s.t. merging two text documents is very different from merging sets of tuples (e.g. databases), trees, or other applicationspecific structures. Solving 3wm for _text_ data is feasible with limited complexity. In [Mye86] the authors describe how to detect the shortest sequence of edit operations ( _edit script_ ) that transforms an input text file to an output file, considering only line insertions and deletions as operations. After computing the edit scripts _e_ 1 _= d_ 1 _− db_ , _e_ 2 _= d_ 2 _− db_ , applications such as _diff3_ , perform the 3wm of _db_ , _e_ 1, _e_ 2 [KKP07]. 3wm has been prominently used in practice for merging software source code, in spite of the risk for syntactic or semantic merge conflicts [Ape+11]. 

For _tree_ -based data structures _differencing_ (which includes _matching_ of nodes) and _merging_ is considerably more complex, as surveys such as [Lin01, section 4.4] and [Bil05] demonstrate. Research is frequently driven by concrete domains, such as _merge conflict resolution_ [Ape+11; Men02] of source code or merging UML models [PB03]. Generic synchronizers also exist, such as [Lin04], a framework that applies 3wm to XML documents. 3wm has also been applied to _heterogeneous_ systems, see [Fos+07]. Here the authors built a universal, heterogeneous synchronization framework called "Harmony" for _tree_ - based data. Developers who want to use Harmony synchronize an application’s state need to implement two _transformation functions_ (called “lenses”) that transform data structure to and from Harmony’s expected, internal tree format, to which 3wm is applied. Developers also need to specify a synchronization _schema_ , which defines invariants of the data<sup>5</sup> . 

We note that conventional 3wm requires the state of both replicas to remain _static_ during the synchronization. In systems where updates occur frequently, such as collaborative text editors where multiple key strokes per second are common, locking the data during synchronization would severely impact the 

5For instance, a phone book application would have the invariant that states that a person can have at most one Work number. 

_2.2. ACHIEVING CONSISTENCY IN OPTIMISTIC REPLICATION SYSTEMS_ 

13 

usability of the application. [Fra09] proposes an extension to 3wm that improves handling of concurrency, allowing the user to manipulate data even while the system is performing a synchronization. 

For our work, 3wm is relevant, but not for merging divergent _file content_ , because earlier (base) versions of files are typically not available. Instead we consider the file system’s _structure_ as a document. 3wm can be used to detect and merge changes on the structural level, which is also done in [LKT05], see section 2.4.2. 

##### **2.2.2.2 Operation-based** 

Operation-based approaches which use a complete log (sometimes also referred to as “trace” or “journal”), such as [SRK00; Ker+01; Ter+95; CH06], follow a common pattern. At each replica the application collects a complete log of all operations performed by the user. When synchronization is triggered, replicas connect to each other in a synchronization phase. The application locks access to the user, logs are transmitted and all replicas _roll back_ to a consistent state, i.e., a point in the log where all replicas’s logs coincide. Then the reconciliation algorithm produces a _schedule_ , that is, a total order of operations that interleaves the operations of all replicas, including conflict detection and resolution - which may involve the user. Once a valid schedule is found, all replicas apply it and finally unlock access to the user. [Qia04; SRK00] Approaches to find a schedule, such as [SRK00; Ker+01] model operations in a way s.t. dependencies between operations are made explicit using _assertions_ . Assertions consist of preand post-conditions often formulated as first-order logic (FOL). These assertions represent _constraints_ . Finding a valid schedule therefore becomes a constraint satisfaction problem, where the goal is to maximize (rather than minimize, which would be the goal for solving _planning_ problems) the number of operations in the schedule - a NP-hard problem. There are also distributed variants of such algorithms, see [CH06]. 

These log-based approaches suffer from multiple issues. In case operations occur frequently, the log grows quickly and may become very large, especially if a replica is disconnected for longer periods. This causes increased storage requirements, transfer time and time required for finding a schedule, which in turn causes the replica to be locked for extended time periods. [Qia04] In addition, the log collection must be 100% reliable. If there is a chance that executed operations are missing in the log, the replica’s state will begin to diverge over time [Fra09]. 

#### **2.2.3 Uni-directional approaches** 

The merge algorithms of uni-directional approaches are able to achieve consistency over time, without the requirement to know about the data of all replicas at the point of the merge. Conflicts are detected and resolved without the need to coordinate with other replicas. To achieve this, conflict resolution is typically fully automatic and deterministic, without user involvement<sup>6</sup> . In the following sections we introduce two popular approaches, Operational Transformation and Commutative and Convergent Replicated Data Types. 

##### **2.2.3.1 Operational Transformation** 

Operational Transformation (OT), presented in the seminal work [EG89] and follow-up paper [SE98], is an approach with a large amount of academic works and industrial use-cases<sup>7</sup> . OT has been used in different types of collaborative applications, such as groupware [EG89; SE98], document editing [Sun+04], CAD design [Agu+08], XML [DSL02; Ost+06a] file systems ([NS16; Mol+03], see section 2.4.3) and many others<sup>8</sup> . 

> 6There are exceptions, such as [CJ05], where users can resolve conflicts non-deterministically. 

> 7OT was popularized by Google Wave, which became Apache Wave `http://incubator.apache.org/projects/wave. html` . It lives on in web-frameworks such as SwellRT `http://swellrt.org/` or ShareDB `https://github.com/share/ sharedb` , and is used in industry products such as Google Docs `https://www.google.com/docs/about/` or Codox Wave, `https://www.codox.io/` . URLs retrieved July 21, 2019. 

> 8See sections 1.2+1.3 of `http://www3.ntu.edu.sg/home/czsun/projects/otfaq/` for further applications, retrieved July 21, 2019. 

_CHAPTER 2. RELATED WORK_ 

14 

The general idea of OT is the following: every operation performed by the user causes an OT operation to be generated, executed and sent to all other sites. The operation is given the context in which it was generated (e.g. operation _Insert(“e”, 1)_ and context being a document’s state, _“tst”_ , such that the _Insert_ operation transforms the document to _“test”_ ). Whenever a site receives an OT operation, it transforms it against already-executed operations to account for their effect on the state. OT systems are generally divided into two parts, the generic _control algorithm_ (sometimes referred to as _integration algorithm_ ) and a set of application-specific _transformation functions_ . The control algorithm is independent of the application and is in charge of processing incoming OT operations and maintaining a buffer of executed (applied) operations, calling the transformation functions against the correct, buffered operations, in the correct order. Many different variants were developed over time, such as dOpt [EG89], GOT [Sun+98], SOCT2 [SCF97], TIBOT [LSL04], or COT [SS09]. Some rely on a central server to determine a global operation order, some allow for arbitrary peer-to-peer arrangements. The application developer then needs to develop one transformation function for each possible operation pair and ordering<sup>9</sup> . 

##### **2.2.3.2 CRDT** 

A CRDT<sup>10</sup> [Sha+11b; Sha+11a] is a mutable object, replicated to each site, which offers an interface to the application that uses it to store data. CRDTs can be divided into _state-based_ CRDTs (Convergent Replicated Data Types, CvRDT), and _operation-based_ CRDTs (Commutative Replicated Data Types, CmRDT). CvRDTs can be emulated by CmRDTs and vice-versa. Modifying an object immediately affects the object’s local state. CvRDTs then asynchronously distribute the whole data type’s state to other replicas (or just state- _deltas_ [ASB15; vLP16; Ene+18] to reduce network usage), whereas CmRDTs log and send only the operation itself, including its parameters. A core concept of CRDTs is _commutativity_ , which applies to the merge procedure of both operations and states. Whenever a site of a state-based CRDT receives another replica’s state, it _merges_ that received state with its local one. CvRDTs build on the concept of a join-semilattice, which is a partial order with a defined least upper bound (LUB) [DP02]. By its definition, the LUB has commutative, idemptotent and associative properties, and the merge function computes the LUB of the local and received state, which ensures eventual convergence of all replicas. In operation-based CRDTs operations are enriched with additional meta-data to make them commutative. 

A considerable number of basic CRDTs have already been specified, such as counters, sets, registers, maps, graphs [Sha+11a], lists [Roh+11] and JSON [KB17] which are used as building blocks in more complex applications, such as collaborative rich text editing [Pre+09; Nic+16], 3d modeling [TIH19], CAD [Lv+17; Lv+18] and file synchronization [TSR15; AMU12]. CRDTs have also been increasingly used in industry applications, such as Amazon DynamoDB [DeC+07], Riak [Bas18] and many others<sup>11</sup> . 

OT and CRDT research is still actively pursuing the holy grail of building _truly_ provably correct replicated systems with optimal space and time complexities (i.e., less than quadratic). While there is some effort to unify these two theories [Meh+14] we also observed heated separative discussions where each “camp” defames solutions of the other, in the style of “your proof is not a real proof” or “our implementation is n times faster than yours”, see [Sun+18] and the corresponding online discussions<sup>12</sup> . It remains open which theory turns out to be superior. 

### **2.3 File systems** 

The core of any data synchronizer is the data model the synchronizer works on. For file synchronizers, the data model is the file system. We reviewed related _academic_ file synchronizer works to understand how they model the file system (see section 2.3.1) and their general approach (see section 2.4). As our results from section 2.3.1 show, file system definitions vary strongly between each work, different models are not compared against each other, and some works don’t even provide any formal model. We would 

> 9For instance, for a text editing system with insert operation _i_ and delete operation _d_ , the transformation functions _Tii_ , _Tdd_ , _Tid_ , _Tdi_ need to be developed. 

> 10See [Pre18; Rij18] for an overview. The originating work is WOOT [Ost+06b]. 

> 11See `https://github.com/ipfs/research-CRDT/issues/40` , retrieved July 21, 2019. 

> 12See `https://news.ycombinator.com/item?id=18191867` , retrieved July 21, 2019. 

_2.3. FILE SYSTEMS_ 

15 

have expected that the file system model is formally defined, including its operations and invariants. Since the analyzed works don’t consider _several_ models, their differences or incompatibilities are not discussed either. While there is some literature regarding the heterogeneity of data models for _generic_ data synchronizers (as presented in section 2.2.1.6), there is a lack of discussion for _file systems_ . This caused us to ask research question 1: 

**RQ1 - File systems:** What different kinds of file system definitions exist in academia and practice? Which criteria are relevant for file synchronizers? How should a file synchronizer’s internal, abstracted file system model look like, which incompatibilities exist and how can they be handled? 

The answers are found in sections 2.3.1 (for _academic_ file system definitions) and 3.1 for practical models. Section 3.1 also discusses how to handle incompatibilities, providing guidance to researchers and developers of file synchronizers. 

The following sections discuss short-comings in related file synchronizer works regarding the file system and operations model. 

#### **2.3.1 File system specifications** 

When reviewing related works we found that the degree of _formalism_ varies strongly. Some works don’t provide any file system specification [CJ05; EYL13], some [Bao+11; Li+12a; LKT05; Mol+03; TSR15; UFB10; Bjø07] only provide a partial description, e.g. of their internal state database records or operations, and some synchronizers [BP98; NS16; RC01; Csi16] formally specify _a_ file system (includings its operations and invariants). There are considerable differences in the specifications<sup>13</sup> , which affects how the synchronizers work internally. By comparing the file system models used in related works, we found the following characteristics: 

- _Identity- vs. path-based model_ : as discussed in [TSR15, section 3] the file system and its operations can be modeled using the _identity_ -based approach where each object is identified by a unique ID, or by a _path_ -based approach (“namespace-based” in [TSR15]) where objects are only identified by their path. ID-based approaches include [TSR15; Bao+11; Li+12a; Li+12b; LKT05; Mol+03; Bjø07], for path-based approaches, see [BP98; NS16; Csi16; TH10]. 

- _Hardlink_<sup>14</sup> support for files: a file system may support that a specific _file_ is linked into multiple directories (the name of each link may vary), or is linked multiple times under different name into a specific directory. This typically affects only files, not directories, because that would allow to form cyclic namespaces. File systems without hardlink support only allow each file and dir to be linked once, which allows a diff algorithm to detect _unambiguous_ move operations. For file systems with hardlink support _move_ operations can still be detected, but only for _directories_ . For _files_ we instead have the _link_ and _unlink_ operation, and move operations become ambiguous once the _before state_ or _after state_ link count for a file is different than 1. 

- _Directory_ support: while all file systems we encountered support directories (as otherwise they would not be hierarchical), some file synchronizers limit the support for directories. In [Qia04, Definition 2.3.1 + section 2.4.4] the authors model their file system as a set consisting only of _file_ paths and their identities. The Git VCS [TH10] does include directories as _tree_ objects in its internal commit files, but does not support _empty_ directories. Although dropping support for directories reduces the complexity of the synchronization algorithm, it also reduces the efficiency and intention preservation<sup>15</sup> . We assume that for this reason the majority of synchronizers do support directories. 

> 13We note that modeling file systems is far from trivial once all its features (such as permissions and concurrency) are considered [Ntz16]. Many real-world specifications such as POSIX have only informal definitions and works exist [Rid+15] that iteratively extract a formal model from existing file system implementations. 

> 14We do not cover _soft_ or _symbolic_ links because they do not influence the file system model. They are separate files (with their own identity) which the file system APIs can treat in a specific way (following them). 

> 15If the user renames or moves a directory that contains sub-files, this causes the synchronizer to instead apply move operations to the other replica for each sub-file separately. This is neither efficient nor do the operation-intentions of the two replicas match. 

_CHAPTER 2. RELATED WORK_ 

16 

|Category|Short-<br>hand|Description|Related works|
|---|---|---|---|
|Directory<br>support, but|NH-MD|Full support of operations: create,<br>edit,**m**ove,**d**elete.|[LKT05], [Bao+11],<br>[Bjø07],**our work**|
|**n**o**h**ardlink<br>support|NH-M|Supports create, edit,**m**ove. The<br>delete operation becomes a move<br>to agarbage directory.|[Mol+03]|
||NH-D|Supports create, edit,**d**elete.<br>Move becomes a sequence of<br>delete + create.|[BP98], [Li+12a; Li+12b],<br>[RC01; Csi16]|
||NH-RD|Supports create, edit,**r**ename,<br>**d**elete. Rename operations do not<br>change the object’s parent<br>directory. Move operations that<br>do change the object’s parent<br>directory become a sequence of<br>delete + create.|[NS16]|
|Directory<br>support,<br>with<br>**h**ardlink<br>support|H-All|Supports edit, link, unlink, move.<br>Move exists only for directories.<br>Unlink of the last instance of an<br>object represents the_delete_<br>operation. Link represents_create_.|[TSR15]|
|**N**o (**e**mpty)<br>**d**irectory<br>support|NED-All|Supports createfile, edit,<br>deletefile, movefile.|Git [TH10], [Qia04,<br>Definition 2.3.1 + section<br>2.4.4]|



Table 2.1: Operation support of file synchronizers 

Overview of hardlinks, directory and operations supported by related works. Note that additional file system interpretations are possible (e.g. NED-D, where _move_ is modeled as _delete_ + _create_ ), but we found no corresponding works using those interpretations. 

- _Operation_ support: while all file synchronizers we found support create and edit operations, support for other operations varies. 

Table 2.1 presents an overview of the above characteristics. If an implementation wants to detect _move_ or _rename_ operations, approaches that compute operations from state should use an identity-based model, because path-based models do not allow to reliably detect moved objects. Operation-based approaches such as [Mol+03; NS16] can be based on either identity-based or path-based models, because the implementation is sure to know the exact order of operations. 

Aside from the large diversity of definitions we found that most works fail to mention on which file system their _implementation_ is actually executed. It is often unclear whether the authors implemented their own file system from scratch, use an abstraction layer on top of an existing file system, or whether the implementation operates on an existing real-world file system directly (e.g. ext3, NTFS, etc.). 

Finally, we note that the choice of a file system model influences the set of _conflicts_ that exist for the chosen model. We examine this effect more closely in section 5.6 on page 91, after having discussed the concept of conflicts. 

_2.4. FILE SYNCHRONIZERS_ 

17 

#### **2.3.2 Real-world file system operation logs** 

We found that approaches based on _operation logs_ (such as [NS16; Mol+03]) do not discuss how this log is obtained. Our conclusion is that a complete log of file system operations can only be obtained when the information is either provided by the user, or when the synchronizer is tightly integrated into the file system implementation, e.g. by hooking into the file system implementation, or writing the file system from scratch. We back this observation in chapter 4 where we investigate the capabilities of obtaining a complete log from the most wide-spread end-user file systems on Windows and macOS computers. We find that without extending the file system implementation in some way, any solution is best-efforts, i.e., the complete log _cannot_ be obtained in all scenarios, and the synchronizer needs to fall back to computing operations from state. 

### **2.4 File synchronizers** 

This section presents selected file synchronizer works. Our overview excludes the following two related areas: 

- Systems that distribute files on _multiple_ cloud-storage backends, see e.g. [Han+16; Tan+15; Cel+16]. The core contribution of these works is the distribution aspect. 

- Papers related to _(remote) file synchronization_ , see [SM02]. This research topic discusses how a specific file which exists on two remote sites can be efficiently transferred, s.t. after the transfer both sites have the same file content, without transmitting the whole file. 

An overview of the related works is presented in table 2.2 . After explaining the basic differences of stateand operation-based approaches in section 2.4.1 we briefly describe each work, presenting _state-based_ approaches in section 2.4.2 and _operation-based_ ones in section 2.4.3. Details regarding conflict detection and resolution are deferred to chapter 5, where we analyze the approaches of related work in section 5.1 and propose solutions. In summary the analysis exhibits several shortcomings in related works, such as inadequate formalism, lack of rationale regarding conflict resolution approaches, or missing discussions how to resolve situations where _several_ conflicts apply to one specific file at once. For this reason the third research question considers different conflict-related aspects: 

**RQ3 - Conflicts:** What sets of operations applied to two disconnected file systems are conflicting? How do conflicts depend on the file system model? Can multiple conflicts be combined? What are possible solutions for resolving individual conflicts and conflict combinations? Which conflicts are relevant in practice? How can conflicts be explained to the user? 

Answers are provided in chapter 5. 

#### **2.4.1 State- vs. operation-based approaches** 

This section presents the basic differences between state- and operation-based approaches for file synchronizers. Figure 2.3 provides an overview for a two-replica scenario. 

For _operation-based_ approaches the concept we discussed in section 2.2.2.2 for _generic data_ synchronizers also applies to _file_ synchronizers. The file synchronizer’s merge algorithm is given a list of operations and uses _only_ this operation list for the reconciliation of updates and equalizing the replica’s states. The operation lists may have been retrieved from the file system directly, or may have been deduced by a preprocessing step that compares the current file system _state_ to a previous state. 

The merge algorithm of _state-based_ approaches uses structures derived from the state of the synchronized replicas, although some works also describe the _additional_ use of computed operations from state deltas. As presented in the seminal work [BP98], most state-based file synchronizers use a three-stage process that consists of _update detection_ , _reconciliation_ and _propagation_ stage. During _update detection_ , which is performed for each replica separately, the differences between the state of the previous 

_CHAPTER 2. RELATED WORK_ 

18 

||[BP98]|[LKT05]|[Bjø07]|[UFB10]|
|---|---|---|---|---|
|Directionality|Bi-directional|Bi-directional|Uni-directional|N-directional|
|Coupling|Loose|Loose|Loose|Loose|
|Merge approach|Analysis of<br>dirtyness<br>predicate|Variant of 3wm|Unclear|Unclear|
|Communication<br>architecture|Client/Server|Client/Server|Peer-to-peer|Peer-to-peer via<br>dynamically elected<br>master|
|Consistency<br>algorithm data|State|State|State|Operations computed<br>from state|
|Conflict<br>resolution|Manual|Semi-Automatic<br>(depends on<br>operation)|Automatic|Manual, Automatic|
|Transferred data|State|State|Unclear (State?)|State|
|ID/Path-based|Path-based|ID-based|ID-based|Path-based|
|file system model|||||
||[Bao+11]|[CJ05]|[Li+12a; Li+12b]|[RC01; Csi16]|
|Directionality|Uni-directional|Uni-directional|Uni-directional|N-directional|
|Coupling|Loose|Loose|Loose|Loose|
|Merge approach|Unclear|Vector timepairs|Version vectors|Command algebra|
|Communication<br>architecture|Client/Server|Peer-to-peer|Client/Server|Unclear|
|Consistency<br>algorithm data|Operations<br>computed from<br>state / logged on<br>server)|State|Operations<br>computed from<br>state|Operations|
|Conflict<br>resolution|Automatic|Manual|Automatic|Manual|
|Transferred data|Operations|Operations|Operations|Operations|
|ID/Path-based|ID-based|Unclear|ID-based|Path-based|
|file system model|||||
||[Mol+03]|[NS16]|[TSR15]|[Naj16]|
|Directionality|Uni-directional|Uni-directional|Uni-directional|Unclear|
|Coupling|Loose|Unclear|Unclear (Loose?)|Unclear|
|Merge approach|Operational<br>Transformation|Operational<br>Transformation|State-based CRDTs|Synchronization<br>phase for move<br>operations, CRDTs for<br>all other operations|
|Communication<br>architecture|Client/Server|Client/Server|Peer-to-peer|Unclear|
|Consistency|Operations|Operations|State|Unclear (State?)|
|algorithm data|||||
|Conflict<br>resolution|Automatic|Automatic|Automatic|Automatic|
|Transferred data|Operations|Operations|State|Unclear|
|ID/Path-based|ID-based|Path-based|ID-based|ID-based|
|file system model|||||



Table 2.2: Overview of file synchronizers 

_2.4. FILE SYNCHRONIZERS_ 

19 



<!-- Start of picture text -->
2./Transmit<br>Replica/X Replica/Y<br>create('f',/2content2)<br>move('f','test/g')<br>deletedir('test')<br>1./Get/operation/log ... 3./Merge<br>Operation/log<br>File/system File/system<br>(a) Operation-based<br>2.3Transmit<br>Replica3X id333name333lastmod Replica3Y 3.3Merge<br>...333...3333333333...<br>1.3Get3current3state ...333...3333333333...State DetectionUpdate Reconciliation Propagation<br>id333name333lastmod<br>...333...3333333333...<br>...333...3333333333...<br>File3system<br>Previous3state File3system<br>(b) State-based<br><!-- End of picture text -->

Figure 2.3: Overview of state- vs. operation-based file synchronizers (2-replica scenario) 

synchronization and the replica’s current state are determined. The _reconciliation_ stage uses the available states and the detected updates to schedule a list of operations that eliminate the divergences of all replicas, including conflict detection. Finally, the _propagation_ stage executes these operations. 

Some state-based approaches, such as _three-way merging_ discussed in section 2.2.2.1, allow the synchronizer to build a merged state (during reconciation) and then _atomically_ replace each replica’s diverged state with the merged state (during propagation). However, this is typically not possible for file systems. Modifying file systems is expensive. Atomically replacing a replica with 30’000 files with another state that consists of, say, 32’000 files is typically not supported. Consequently a state-based synchronizer needs to manipulate each diverged replica in _incremental_ steps, using the available file system operations. This is challenging because states (and operations computed from state deltas) do _not_ indicate the order of operations. An example is shown in figure 2.4. Most works we present in section 2.4.2 ignore this detail. Only the authors of [RC01; Csi16] discuss it, but their work is limited to a file system that does not support _move_ operations, whose support is, however, very desirable. For this reason our second research question is: 

**RQ2 - Operation order:** As operations detected during state-based update detection lack order, but not all operations are commutative, how can a valid order be detected and propagated by a synchronizer? This question was already answered for file system models which do not support _move_ operations, but remains open for file systems which do support them. 

We answer this question in section 4.2 on page 47. 

#### **2.4.2 State-based approaches** 

[BP98] is among the most-cited works for file synchronizers. Additional details are available in a tech report, see [PV04] and an unfinished manuscript [JPV02]. Its algorithm is implemented in _Unison_ , a 

_CHAPTER 2. RELATED WORK_ 

20 

user-level program available to end users<sup>16</sup> . Unison synchronizes two replicas upon the user’s request. The authors formally specify their file system model, but they don’t mention operations explicitly. Their model is path-based and excludes multiple hard-links, or symbolic links. Their algorithm consists of the three stages _update detection_ , _reconciliation_ and _propagation_ , which is a typical approach for many bi-directional file synchronizers. In the _update detection_ stage, both update detectors (one for the local, one for the remote replica) collect the current state of the file system, consisting of a set of paths with associated meta-data, such as the inode and the last-modified timestamp. The _current_ state is compared against an _archive_ version taken from the most recently completed synchronization, to compute a dirtyness-predicate, an upward-closed boolean function that returns _true_ for a path that has changed<sup>17</sup> between the archived and current state, _false_ otherwise. The _reconciliation_ stage is given the both replica’s dirtyness-predicates, computes a list of all non-conflicting changes and finds conflicting ones, which are presented to the user for resolution. Finally, the _propagation_ stage executes the changes determined during reconciliation, to achieve convergence among both replicas. Unison is known to work on end-user file systems on Windows, macOS and Linux. Its main limitations are (1) the assumption that the file system is not changed by the user during any of the three stages, and (2) file system objects have no identity, s.t. a move operation is not detected but instead synchronized via a delete + create operation. 

[LKT05] also performs a pair-wise synchronization of two replicas. The authors approach the task by representing the scanned file system hierarchy as an XML document. The XML elements contain a globally unique ID (GUID) for each object<sup>18</sup> , but the authors don’t provide any further formalization of the file system or its operations. Using a persisted base document _T_ 0 (from the last successful synchronization) and the current documents _T_ 1, _T_ 2 from each replica they apply their own tree-based three-way merge algorithm _3dm_ [Lin04] to compute the merged tree _Tm_ that represents the final outcome, including the resolution of conflicts. To make both replicas consistent their algorithm finally iterates over _Tm_ ’s nodes and compares them to the ones of _T_ 1 and _T_ 2 respectively to determine the necessary _create_ , _move_ and _delete_ operations required to achieve consistency<sup>19</sup> . Their work additionally contributes a method to speed up the repeated scanning of the replica’s file system hierarchy. Their approach is called _bubbling modification timestamps_ (BMT). They hook into the Linux file system API functionality, such that whenever a file is modified, their hook updates the _last-modified_ timestamp of not only the immediate parent directory, but _all_ ancestor directories. When subsequently scanning a directory (to obtain _T_ 1 or 

> 16Available at `http://www.cis.upenn.edu/~bcpierce/unison/` , retrieved July 21, 2019. 

17The specific change could be the creation of a new object, a change in an existing object, or the deletion of an existing object. In case an object is moved, both its source and destination paths are marked dirty. 

18The paper mentions the usage of UNIX _inodes_ to uniquely identify file system objects, which does not make sense because inodes are system-dependent, i.e., _not_ globally unique. We can only assume that the implementation maintains a 1:1 mapping for each object’s inode to the corresponding GUID. 

19Missing from this list of operations is the _update_ operation (i.e., updated files) which their algorithm deals with in a final step after all other operations are executed. 



<!-- Start of picture text -->
Previous state Current state Operations:<br>2. deletedir('A')<br>1. move('A/c', 'B/c')<br>A B B<br>c deleted c<br>moved<br><!-- End of picture text -->

Figure 2.4: Example for non-commutative operation order 

Applying the two operations _move(’A/c’, ’B/c’)_ , _deletedir(’A’)_ detected in one replica to the other replica is not trivial. Choosing the wrong order, i.e., first deleting “A”, would also delete “A/c” on the other replica, causing replica states to diverge. 

_2.4. FILE SYNCHRONIZERS_ 

21 

_T_ 2) their algorithm can then check whether the last-modified timestamp from _T_ 0 matches the one from the scan, in which case it can skip recursively scanning that directory. 

In [Bjø07] Microsoft’s distributed NTFS file system, _DFS-R_ for Windows Server, is presented. Their approach synchronizes _N_ replicas, arranged in an arbitrary (peer-to-peer) topology. Their state-based approach is divided into _two_ synchronization phases, a _local_ and a _global_ one, which take turns. The _local_ synchronization synchronizes a directory on a NTFS disk with a machine’s local database. Details of local synchronization are not discussed by the authors. They also do not describe the (NTFS) file system or its operations formally, but mention that they support _create_ , _delete_ , _rename_ (which includes _move_ ) and _file-update_ as operations, and the file system to resemble an arborescence where each node is uniquely identified by a local ID (c.f. _inode_ ) and appears exactly once. The database, however, is formally described. It contains _version vectors_ and _globally_ unique IDs (as well as the local IDs) for each object. Deleted objects remain in the database, with a tombstone-flag. A garbage-collection mechanism that reclaims tombstones is also briefly discussed. The _global_ synchronization, which is briefly discussed, then synchronizes the databases of two machines at a time, using version vector information to properly detect concurrent (conflict) changes. 

In [UFB10] the authors also present an approach suitable for an environment where the goal is to achieve efficient synchronization of _N_ replicas, where the devices on which the replicas reside only provide ad-hoc connectivity via a (wireless) local area network. A common approach is _cloud-based file synchronization_ [Zha+14] such as Dropbox, which is a pair-wise synchronization between a replica and a server, over the Internet. The authors want to avoid downsides of cloud-based file synchronization, such as poor performance due to slow upload bandwidths, costs, security and privacy concerns, by keeping the content of files only on the devices, transmitting the content only within the local area network. Whenever multiple devices which are concurrently connected to the local network and the Internet want to synchronize, they connect to a _cloud service_ . This service stores only the meta-data of the synchronized objects and is in charge of coordinating the synchronization process. It chooses one device to act as master, which then performs two rounds of pair-wise synchronizations with all other local devices which are currently online. After the first round, the master is sure to have collected the changes of all replicas, which it then distributes to all other replicas during the second round. This work does not provide sufficient detail to fully understand the pair-wise synchronization protocol. Neither the file system model nor the operations are modeled formally. The update detection stage seems to compute the set of operations from the archived and the current state of a replica. It is unclear whether the states (and computed operations) use unique IDs or paths. It is also unclear how the reconciliation stage determines the list of operations and how they are propagated. 

[Bao+11] present a cloud-based file synchronization approach with an architecture similar to Dropbox. Consistency is achieved by clients performing a pair-wise synchronization of their file system with a central server. While clients communicate with a single end-point using a HTTP-REST interface, in the cloud the load is distributed among a _control_ server (providing the REST interface), _meta-data_ server and _storage_ server. The authors specify their ID-based internal database record structure of the file system state, but they don’t formalize the file system or its operations. Their synchronization protocol achieves bi-directional synchronization by two consecutive uni-directional synchronizations. In the upstream synchronization the client regularly scans its file system and detects operations. Since the authors don’t explicitly explain the process, we assume that the client stores a persistent snapshot of the file system’s state locally and computes a difference of the persistent snapshot and the current scan to derive the list of operations. The client sends the list of operations to the control server (including payload in case of created or modified files), which performs the reconciliation, updating both the storage- and metadata server. The latter keeps a historic _log_ of operations of all users. The downstream synchronization is achieved by clients regularly querying the control server, requesting the list of operations that happened since the previous query. The client provides a timestamp in this query and the server serves the log of operations (again, including file payloads) that the client then merges. 

In [CJ05] the authors introduce synchronization using _vector time pairs_ . Their algorithm internally uses two (instead of one) version vectors, which fixes several of short-comings of version vectors, like the inability to record conflict resolutions or the necessity for _global_ consensus to garbage-collect deletion 

_CHAPTER 2. RELATED WORK_ 

22 

notices. The work presents how the algorithm works for synchronizing the _modifications_ of _individual files_ . Whenever a file’s content has changed<sup>20</sup> the corresponding logical clock entry is increased by one in the respective vectors. The authors don’t formally specify their file system model or its operations. Although the work presents pseudo-code for the synchronization of _directories_ , it is unclear how files and directories of two replicas are associated with each other. We assume that their approach is path-based where files and directories have no identity, because _move_ or _rename_ operations are not mentioned at any point. The authors make a Linux implementation available<sup>21</sup> which appears to work on existing, real-world file systems. 

Another cloud-based file synchronization approach (similar to [Bao+11]) is presented in [Li+12a; Li+12b]. The authors specify their ID-based internal database record structure of the file system state, but they don’t formalize the file system or its operations. Even though files and directories are modeled on IDs, they propagate _move_ operations as _delete_ + _create_ operations. The basic synchronization is done in two phases, where each phase reflects a uni-directional synchronization: In phase 1 (client-to-server) the client sends locally detected operations to server. The server checks for conflicts, if any exist, none of the operations are executed but the conflicts are returned to the client. The client then converts the conflicting operations to resolve the conflicts and then retransmits the operations to the server, until no more conflicts are reported. Then the server applies the client’s operations. In phase 2 the client receives the list of updates from server. If there are local conflicts, the client first transforms the operations and sends them to the server (the cycle from phase 1 is applied again). When there are no conflicts anymore, all server updates are applied on the client. 

In [TSR15] the authors use state-based CRDTs for file system synchronization. They formally specify their file system model, but don’t mention operations explicitly. Their model is ID-based and supports multiple hard-links for files. Their work describes conflicts and their automatic resolution in detail on a conceptual level, but omits the intricate details of their implementation as state-based CRDT due to space constraints. 

In the thesis [Naj16] (and the later paper [NSE18]) a lot of _theoretical_ work was done using CRDTs on various applications, including file systems. The authors formally specify a file system including its operations and their pre- and post-conditions in first order logic. They use CRDT _sets_ to store the file system state and design the merge function to solve different conflicts that can occur. Using CISE [Got+16], an SMT-based solver, they find that it is not possible to use CRDTs to build a _fully_ asynchronous file system (where _all_ file system operations can be executed asynchronously). The reason is that _move_ operations (of _directories_ , for which only one path or link may exist at a time) executed concurrently in different replicas can break the invariant of a file system. The invariant dictates that file systems always form an arborescence from graph theory. When directory X is moved to Y in one replica but X is moved to Z in the other (or when X is moved into Y in one replica and Y into X in the other) then the invariant is violated<sup>22</sup> . To solve this problem, the authors then specify a _mostly_ asynchronous file system, which is a hybrid approach where _move_ operations affecting directories have to be _synchronized_ , while all other operations can be dealt with by CRDTs. This model was verified to be provably correct using CISE. As the approach was not actually implemented, various characteristics such as coupling, topology, directionality and others are unknown. 

#### **2.4.3 Operation-based approaches** 

In [RC01] and the follow-up work [Csi16] the authors formally model their path-based file system and present a formal _algebra_ of commands for the different file system operations. To reduce the complexity of the problem, the _move_ operation is replaced by _delete_ + _create_ . The authors provide a table of algebraic laws which explore various properties of the commands, such as _commutativity_ . Their synchronization algorithm follows the general paradigm of [BP98]. First, all replicas determine their list of commands 

> 20Deletions also count as modification. 

> 21See `https://swtch.com/tra/` , retrieved July 21, 2019. 

> 22In [TSR15] the authors work around the issue by translating two conflicting move operations into creating two _copies_ of the directory, including all its children. 

_2.5. RELATED FIELDS AND TECHNOLOGIES_ 

23 

(operations) by computing the difference of their previous and current file system state. The updates are sent to one chosen replica which performs reconciliation, including conflict detection. The notable difference to [BP98] is that the number of participating replicas is _n ≥_ 2 (not forced to _n =_ 2). Nonconflicting updates are propagated, while conflicting ones need to be solved by the user. The work does not discuss implementation details, such as propagation. 

The following two works apply Operational Transformation to file systems. [Mol+03] is the first work to our knowledge. They use SOCT4 [Vid+00] as control algorithm. The authors don’t specify their file system model but provide a semi-formal definition of the operations ( _create_ files or directories, _move_ objects, deletion is modeled as a move operation of the object into a garbage directory), which are based on IDs instead of paths. When files are text or XML files the OT algorithm can be applied to propagate changes to other replicas or merge conflicting updates. The synchronizer was developed as part of the _LibreSource_ collaboration platform<sup>23</sup> . The implementation is a command-line tool with commands<sup>24</sup> similar to SCM tools such as SVN or Git. In [Mol+03] the authors don’t discuss update detection, but examining the workflow in LibreSource’s manual reveals that operations are computed by diffing states. One issue in this work is that the transformation functions are provably wrong [NS16] because the author’s definition of the _move_ operation misses the pre-condition that states that the path of the parent directory of the destination may not begin with the source path<sup>25</sup> . This allows for concurrent changes to become unsynchronizable. 

[NS16] is a more recent OT-based work using the COT [SS09] control algorithm. The authors provide a formal specification of their file system as a graph. They also specify the operations that create or delete files or directories, update file contents or _rename_ objects. _Move_ operations that move an object to another parent directory are _not_ supported and are replaced by _create_ + _delete_ operations. Their file system model is path-based. The provided OT transformation functions, apart from dealing with conflicting and non-conflicting operations, also handle issues such as the _adaptation_ of _paths_ in case directories are renamed on a specific replica<sup>26</sup> . Although the work mentions a prototype implementation, it is unclear how operations (updates) are detected and how their implementation is coupled to existing file system implementations. 

#### **2.4.4 Summary** 

Of the 12 surveyed systems, three are operation-based and nine are state-based, indicating that statebased systems are more common. While the merge approach of operation-based works is either OTor CRDT-based, the merge algorithm of each state-based work differs, and is sometimes not explained at all, see [Bjø07; UFB10; Bao+11]. The merge algorithms vary because the data foundation, the file system model, is defined differently, and because some synchronizers desire certain characteristics such as peer-to-peer synchronization. Our survey provides us with an understanding of the challenges and trade-offs made in the presented works. With these findings we selected a suitable merge algorithm for our implementation. Details of our decisions are found in section 2.6. 

### **2.5 Related fields and technologies** 

#### **2.5.1 Version Control Systems** 

Version control systems (VCS) are software systems that manage and document changes to artifacts, such as source code or other kinds of files. Different variants exist, e.g. _centralized_ client/server systems like Concurrent Versions System (CVS) or Subversion (SVN), and _decentralized_ systems such as Git. 

> 23 `http://dev.libresource.org` , retrieved July 21, 2019. 

> 24See `http://dev.libresource.org/home/doc/so6-user-manual/manuals/commandline.html` and `http://dev. libresource.org/home/doc/so6-user-manual/manuals/reference/commands.html` , retrieved July 21, 2019. 

> 25For example, _Move(“A”, “A/x”)_ must fail. 

> 26For instance, if an already synchronized directory “x” is renamed to “y” by one device, while on another device the user creates a new object at “x/z”, the create-path is adapted to “y/z”. 

_CHAPTER 2. RELATED WORK_ 

24 

Each user has a local replica of the entire data set. It is divided into a _workspace_ directory (which resides on the normal file system, representing a specific revision or commit) and a proprietary database that stores a complete history of all files and their versions, tracing their creation, deletion and content modification over time. Thus, VCSs are optimistically replicated systems, because users can work freely on their workspace copy in isolation. The synchronization process is triggered manually by the user, by pulling new changes from the server, merging those with the user’s own changes. Most systems support automatic, syntactic merging of text files using _three-way merging_ (see section 2.2.2.1). They are often deployed in software development and other text-data-focused environments. While file systems (and file synchronizers working on those) only store the latest version of each object (such that after deleting a file it is typically lost), the monotonically growing database of a VCS, which may consume an excessive amount of disk space over time, is considered a feature. 

#### **2.5.2 Techniques for detecting causality and concurrency** 

When optimistically replicated systems exchange updates, various techniques exist to detect conflicting vs. non-conflicting updates and how to merge them. When a system allows replicas to be arranged in an _arbitrary_ topology (e.g. peer-to-peer), the update detection (and merging) mechanisms need to be able to detect whether any two updates are _truly_ conflicting, i.e., it has to reject _false conflicts_ that arise because updates were received out-of-order. This requires knowledge about _causality_ , i.e., which updates precede others. Attaching a single time stamp to updates _ui_ , such as a real-time clock value or even Lamport’s logical clock [Lam78] is not sufficient, because _timestamp_ ( _u_ 1) _> timestamp_ ( _u_ 2) does not imply that _u_ 2 causally precedes _u_ 1 [SM94]. Numerous approaches exist to determine causality from attaching meta-data to transmitted operations or state, each with different advantages and disadvantages. Examples include Version Vectors [Par+83] (and derivative works such as Concise version vectors [MT07]), Vector clocks [Mat+89], Interval Tree Clocks [ABF08], Vector time pairs [CJ05], Hash histories [KWK03] and many others. 

We note that the file synchronizer we are building in this work does not require above mechanisms, for there are only _two_ replicas being synchronized. We use a star topology with one server and many clients. The merge procedure run in the client is unaware of the identity of other clients. Instead, the updates detected in the server replica are a serialization of the updates of _all_ other clients. Because we use statebased differencing, each artifact can only be affected by a specific type of update at most once (e.g. a file can be detected as _moved_ only 0 or 1 times). Thus, causality is implied and does not need to be explicitly recorded. 

#### **2.5.3 Distributed File Systems** 

A distributed file system (DFS) is defined as a system that allows _different_ (several) machines to share a _common_ filesystem [LS90]. DFSs such as AFS [Mor+86] and its descendant Coda [Sat+90], Locus [Wal+83] and descendants such as Rumor [Guy+99] and Ficus [Rei+94] store large amounts of data, structured in a hierarchical name space, distributed among multiple servers. To users who are connected to one or more servers accessing the namespace is done transparently, i.e., clients (and users) are unaware of the distribution and actual server topology. Typically support for this kind of transparency is integrated into the client’s operating system kernel. The goal is not that client machines like desktop workstations have a complete replica of the data, since they aren’t considered to be fault tolerant nor are their disks large enough [Sat+90]. Some works offer caching functionality to clients on which they can operate, but DFSs typically consider such modes of disconnected operations to be undesired. Therefore, synchronization plays a role not so much between client and server, but between servers. Servers run special, homogeneous DFS-software with _tight_ coupling between the physical file system on the server and the DFS, making it possible to lock the DFS during synchronization. In conclusion, DFSs differ from typical file synchronizers because: (a) they don’t necessarily fully replicate the data on the clients, (b) they are not fully optimistic in disconnected mode (between client and server), and (c) the client OS is extended on kernel or driver level. 

_2.5. RELATED FIELDS AND TECHNOLOGIES_ 

25 

#### **2.5.4 Distributed databases** 

Database systems, such as relational databases or NoSQL key-value stores, also benefit from optimistic replication and thus need to handle concurrent updates and resulting conflicts. Because the data model and set of operations is typically limited to rows and columns (relational) or keys (NoSQL) with _insert_ , _delete_ and _update_ operations, the set of conflicts is smaller than for file systems. However, in practice complexity is large, because databases have many additional operations, such as index-operations or transactions, which complicate concurrency issues. 

Industrial and academic systems are divided into two camps, where one focuses on applications that require _strong consistency_ (here database engines attempt to maximize performance/scalability), while the other foregoes strong consistency for availability (CP vs. AP, see _CAP theorem_ , section 2.1.2). Database systems favoring consistency provide replication mechanisms that either use a _multi_ -master<sup>27</sup> approach with _pessimistic_ concurrency control (e.g. Two-Phase Commit), or use a _single_ master that optimistically (i.e., asynchronously) replicates data to multiple slaves. To improve scalability techniques such as _sharding_<sup>28</sup> are applied. Examples for these kinds of systems are MySQL<sup>29</sup> , MongoDB<sup>30</sup> and Postgres-R [KA10]. In the field of traditional relation databases there are also exceptions, such as Oracle<sup>31</sup> which offers _optimistic_ , _multi_ -master replication. However, Oracle’s conflict resolution is best efforts, without guarantee for convergence. If conflicts occur that cannot be automatically resolved, it is the job of the database administrators to resolve them (man-made consistency). Aside from relational systems, several NoSQL key-value stores have emerged which are specifically designed for optimistic replication, such as Riak [Bas18] (based on CRDTs), CouchDB<sup>32</sup> , Amazon Dynamo [DeC+07] and Cassandra [LM10]. These systems use variants of causal histories or version vectors (see section 2.5.2) to detect concurrent, conflicting updates. Conflict resolution either happens within the database engine, or is delegated to the application. In the former case, only simplistic resolution options are available, such as Last-writer wins (LWW), where each update is assigned a timestamp and the update with larger timestamp wins [JT75]. In the latter case the database stores multiple conflicting versions and defers conflict resolution to the application. For instance, the read operation may indicate that there are multiple conflicting versions to the first client that reads data. The client can then solve the conflict, with or without involving the user. 

#### **2.5.5 SyncML / OMA DS** 

SyncML (Sync Markup Language, known as OMA DS<sup>33</sup> since 2002, Open Mobile Alliance Data Synchronization) is an open standard for data synchronization. It specifies both the syntax and protocol for a pair-wise synchronization of several types of data-collections between a client and a server. While originally designed to synchronize a _mobile_ device with a server, OMA DS is nowadays also used for other kinds of devices, such as desktop PCs. Different synchronization types are supported, e.g. two-way or one-way synchronization, as well as various data types such as email, contacts, calendars, etc. The OMA DS protocol specifies how to uniquely identify data objects and how to detect which objects need to be transferred (via synchronization anchors). The synchronization is triggered by a client, sending its changes to the server and requesting changes that happened on the server. Conflict detection and resolution is done on the server, but OMA DS does not specify any particular approach. 

While synchronization of files and folders is possible since version OMA DS 1.2<sup>34</sup> the support is optional 

> 27Master nodes are allowed to perform write operations, slaves only read operations. 

> 28In simplified terms, sharding in a relational database refers to storing some rows (data objects) in one master node, and some other rows in another master node. Thus, there are _multiple_ master using asynchronous replication, but they are in charge of different data ranges, which avoids conflicts. 

> 29See `https://dev.mysql.com/doc/refman/8.0/en/replication.html` , retrieved July 21, 2019. 

> 30See `https://docs.mongodb.com/manual/replication/` , retrieved July 21, 2019. 

> 31See “Oracle Advanced Replication” `https://docs.oracle.com/cd/E18283_01/server.112/e10706/toc.htm` , retrieved July 21, 2019. 

> 32See `http://docs.couchdb.org/en/stable/replication/index.html` , retrieved July 21, 2019. 

> 33See `http://www.openmobilealliance.org/wp/` for further details, retrieved July 21, 2019. 

> 34See sections 6.10.3 and 6.11 of `http://www.openmobilealliance.org/release/DS/V1_2_2-20090319-A/` 

_CHAPTER 2. RELATED WORK_ 

26 



Figure 2.5: Characteristics chosen for our work 

and only offered by few client and server implementations<sup>35</sup> . Just like with any other data type, _file_ data is embedded into the XML-encoded messages, using approaches such as _base64_ , which adds _∼_<sup><u>1</u></sup> 3<sup>of the</sup> file size. The size of each SyncML message is also limited (to avoid that parsing of a, say, 100 MB XML file becomes problematic due to memory limitations), typically to 10s or 100s of kilobytes. Consequently, larger files are split into a potentially large number of messages, which leads to a slower, inefficient synchronization. 

### **2.6 Conclusion** 

The goal of this thesis is to build a file synchronizer for end-users that can synchronize a variety of storage systems without the need to modify their code-base. From our related work analysis we now con- 

> `OMA-TS-DS_Protocol-V1_2_2-20090319-A.pdf` , as well as `http://www.openmobilealliance.org/release/DS/V1_ 2_2-20090319-A/OMA-TS-DS_DataObjFile-V1_2_2-20090319-A.pdf` and `http://www.openmobilealliance.org/ release/DS/V1_2_2-20090319-A/OMA-TS-DS_DataObjFolder-V1_2_2-20090319-A.pdf` , retrieved July 21, 2019. 

> 35See “Files” column of implementation tables on `https://en.wikipedia.org/wiki/SyncML` , retrieved July 21, 2019. 

_2.6. CONCLUSION_ 

27 

clude which trait our synchronizer should have for each of the characteristics we built in section 2.2.1. An overview is shown in figure 2.5. 

- **Directionality:** we choose a _bi-directional_ approach, because it avoids issues of uni-directional approaches discussed in subsection 2.2.1.1, and because it fits well to using the _star_ communication topology (see below). 

- **Heterogeneity:** as per our requirement, the synchronizer we build is a _heterogeneous_ synchronizer that transforms implementation-specific file system models into a slightly different, internal model that is compatible with several specific models. 

- **Consistency algorithm data:** We adopt the approach of related work such as [UFB10; Bao+11; Li+12a; Li+12b]. They use _state_ together with a computed list of operations determined from comparing a cached state with the currently determined state of the file system. As chapter 4 will show, the _state_ -based approach is more feasible in practice, because operation logs are not available on many real-world storage systems, and because well-understood mechanisms such as three-waymerge can be used. From the seminal work of [BP98] we adopt the generic three-stage processing pipeline: update-detection, reconciliation and propagation. 

- **Communication topology:** the goal is to support synchronization for a large number (that is, two or more) of replicas/users. We choose the _star_ topology (over peer-to-peer) with a central replica for multiple reasons: 

   - Pair-wise synchronization between a client and a central replica is typically easier to implement than peer-to-peer based approaches. For instance, the synchronizer does not need to implement membership management, as this is already offered by the storage system APIs if necessary<sup>36</sup> . Also, peer-to-peer approaches have to establish causality between updates, e.g. using _version vectors_ (or similar mechanisms as discussed in section 2.5.2), dealing with modifying these vectors in case new members are added. 

   - Peer-to-peer systems require maintenance of additional meta-data about files and directories (e.g. version vectors), which consume storage space. 

   - With the increased popularity and availability of cloud storage, average users are now well familiar with setting up a pair-wise synchronization between their local disk and a cloud storage. Setting up pair-wise synchronization requires less expertise than setting up peer-topeer systems. 

   - The central replica resides on a highly reliable server that is always online (assuming network connectivity). This allows two devices _A_ and _B_ to synchronize their changes even when they are not online at the same time. In contrast, even peer-to-peer systems that support _epidemic propagation_ where device _A_ can get its changes to device _B_ via device _C_ , require that such a device ( _C_ ) must have been online and connected to A and B _coincidentally_ during the synchronization of A <-> C and B <-> C. 

- **Coupling:** our synchronizer has a _loose_ coupling to the file system, because it is a _heterogeneous_ synchronizer. The file system is not aware of its synchronization. 

- **Transferred data:** we use a _hybrid_ approach. As will be shown in section 4.2.1, all storage systems provide transferring state information. However, always requesting and transferring state is expensive. Therefore we use online or offline change detection for those storage systems that support it. 

- **Invocation:** we prefer the _continuous_ mode over discrete checkpointing because it resembles a “set it and forget it” approach suitable for end-users. This is also the prevalent mode in today’s industrial file synchronizers. 

> 36For example, a WebDAV-based file system offered by ownCloud would require the user to authenticate over HTTP’s authentication mechanism. The backend providing the WebDAV API, here: ownCloud, already provides membership management functionality, such as registration. 

_CHAPTER 2. RELATED WORK_ 

28 

- **Conflict resolution:** we choose _automatic_ over manual resolution because it is in line with the automatic _invocation_ , and because automatic resolution saves time and effort in case the resolution was appropriate. We refer to chapter 5 on page 65 for more details. 

- **Model of file system state and operations:** as shown in section 2.3 on page 14 there are a number of choices to make for a file system model used internally by a file synchronizer. As chapter 3 will show, we will choose a model where objects are unique identified by an ID, are linked into exactly _one_ parent directory with one name, supporting the operations _create_ , _delete_ , _update_ and _move_ . 

The next chapter will further clarify why our choice for the file system state and operations model is suitable. 

29 

## **Chapter 3** 

# **File systems - analysis and definition** 

In section 2.2.1.6 we provided a _general_ introduction to heterogeneity. In this chapter we take a closer look at heterogeneity in the context of _file systems_ . Just like many other industrial file synchronizers, we aim to build a synchronizer that supports the file system APIs of popular end-user operating systems it runs on, as well as the APIs of popular remote file systems, maximizing practical use. This enables users to continue using existing file systems, without the (expensive) migration to a homogeneous system. The disadvantage of such a file system agnostic approach is that we need to build an _internal_ file system model used in the synchronizer that is as compatible as possible with every file system the synchronizer aims to support. This is challenging because no two file systems are exactly equal. 

We start in section 3.1 where we analyze file system capabilities relevant to file synchronizers. We use the term _capability_ for a specific characteristic of a file system, such as namespace limitations or the way object relationships are modeled. Their traits may be different (heterogeneous) between any two file systems. If the synchronizer developer ignores or overlooks a capability, this impairs the usability of the system because of bad side effects that occur during synchronization. For example, if a developer overlooks that a file may not be named “aux” on Windows, the Windows implementation will run into unexpected loops or errors while trying to synchronize such a file, which was synchronized successfully by the macOS implementation. We have observed several instances of such side effects in practice in leading industrial synchronizers. The result is either just a divergence of the file systems, or worse, data loss. From our analysis we formalize the internal file system _F_ using First Order Logic in section 3.2, which we use in the remainder of this work. 

### **3.1 Capability analysis** 

This section corresponds to our published work [SP19]. It provides an in-depth analysis of six file system capabilities relevant to file synchronizers, which we discovered while implementing and technically evaluating our own file synchronizer implementation. We discuss one capability per subsection. We first state its significance for the user, followed by an analysis, then extract similarities that manifest in the file synchronizer’s internal model and finally give advice how file synchronizers can handle incompatibilities, if applicable, with the goal to avoid data loss whenever possible. 

To find capabilities we sample different _types_ of file systems, selecting representative implementations often used in today’s computing landscape. We examine **Windows** version 7-10 (NTFS) and **macOS** version 10.11-10.13 (HFS+ and APFS) APIs because these are the most widespread end-user operating systems at the time of writing. Our findings also transfer to UNIX and therefore to both file servers (e.g. network-attached storage) and mobile devices such as smartphones. We consider **WebDAV** [Dus07] which is widely available as interface for proprietary as well as open-source Internet (cloud) storages. **Dropbox** (HTTP API v2 [Dro17]) is chosen as a representative for widespread cloud storages. **BSCW Social** [Orb18] is a representative for groupware systems commonly found in academia, a system that originates from the CSCW community [BHT97; JP14]. We note that many more file systems exist in each category, such as ownCloud [own19] and OneDrive [Mic19] for cloud storages or CDMI [Sto15] for 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

30 



Figure 3.1: Analysis of object identification and namespace to object mappings 

generic interfaces. However, they are either less used than the systems we analyzed, or their degree of use is not documented. Our decision to reduce the number of examined systems to a representative set improves the clarity of the results. 

We note that the comparison done in this section is _informal_ , as this allows the provision of immediate results for a large selection of file systems. Some online resources such as [Cra08; Wik17] also provide informal comparisons. Apart from [JPV02], an unfinished manuscript by the authors of [BP98], there is no related scientific literature to the best of our knowledge that provides an in-depth discussion of the capabilities of file systems. Providing a _formal_ comparison, while interesting, is hindered by the fact that real-world file system specifications, such as POSIX, are only formulated informally. While there are a few academic works such as [Rid+15] which extract exhaustive formal specifications for real-world implementations, most main stream file systems are not covered yet. 

#### **3.1.1 Physical object & namespace mapping** 

The _namespace_ is the user-facing side of a file system. It consists of a hierarchical set of _paths_ , where a path is a notation for addressing a specific _object_ . A path is a sequence of _names_ , where names are simple strings. Hierarchy levels of a path are separated by a _separation character_ , such as ’/’ or ’\’. File system implementations differ in their approach how objects are identified, physically stored and how the mapping between namespace and objects works. 

##### **3.1.1.1 Significance** 

From the user’s perspective the synchronizer translates a prefix of the synchronized namespace between the local and the remote replica, e.g. _’C:\SyncFolder’_ to _’https://server.com/synced’_ . Users expect that the local disk’s and the server’s namespace match exactly. However, due to technical limitations (analyzed below) this is not always possible. A synchronizer that is aware of incompatibilities should find a suitable way to inform the user about namespace mismatches [Dou96]. 

##### **3.1.1.2 Analysis** 

An overview of the analysis is shown in figure 3.1. 

We first classify whether file system objects (files, directories, etc.) can be identified uniquely (e.g. after moving them) by a persistent identity, or whether only the path is available [TSR15]. Windows provides the _file index_ , and macOS or UNIX systems provide _inode_ numbers. The WebDAV protocol specification leaves it up to the implementation how to identify objects beyond their path. Some implementations 

_3.1. CAPABILITY ANALYSIS_ 

31 

may internally provide an ID for each resource, but a HTTP client cannot rely on its exposure<sup>1</sup> . BSCW and Dropbox identify each object by a unique object ID, assigned upon creation by the server. 

For identity-based systems, two further classifications are appropriate, because an object with a specific ID may be accessible from one or more paths. In practice the cardinality varies per object type, s.t. Windows or macOS forbid more than one link to a _directory_ to prevent cycles to occur in the tree<sup>2</sup> . Some systems model the parent child relationship s.t. each directory has a list of ( _name_ , _id_ ) tuples of its immediate children (name of the objects is part of the _link_ ), whereas others store the name as part of the object and each directory maintains a simple list of immediate child IDs. 

Two more aspects not covered in figure 3.1 are that the invariants of each file system need further examination. A file system may or may not allow two sibling objects to have the same name, and it may use a case-sensitive or case-insensitive comparison while enforcing this invariant (case-sensitivity is further discussed in section 7.2.1 on page 135). 

##### **3.1.1.3 Derived unified model** 

To derive the internal file system model we suggest the following approach: 

- If one or more file systems are _path-based_ , either let the internal model be path-based too, or emulate IDs by generating them on the client, setting IDs as custom meta-data, if the file system API supports it (e.g. WebDAV PROPPATCH, see section 9.2 of [Dus07]). 

- When the parent child mapping varies, let the name be part of the object. 

- If link cardinality varies, use the smaller (1) cardinality. 

- When invariants vary, enforce the one that is most strict. 

Applying these guidelines to the set of examined file systems yields an _ID_ -based file system where each object is linked exactly once, the name is part of the object and sibling nodes may not have the same name, being case-insensitive. 

##### **3.1.1.4 Advice for handling incompatibilities** 

When a file synchronizer encounters an incompatible mapping at run-time, e.g. if a specific file exists at multiple paths but the internal model limits file cardinality to 1, we suggest the synchronizer either stops synchronizing, asking the user to fix the situation, or to automatically add the affected paths or IDs to an _ignore list_ . Numerous industrial synchronizers provide such an ignore list that users can fill with paths to files or directories they want to exclude from synchronization. We suggest that this list can also be manipulated by the reconciliation algorithm automatically to handle compatibility issues, notifying the user in such an event. For certain traits, workarounds may be possible. For example, junctions (Windows) and symbolic links (macOS) may be used to allow a _N_ -cardinality for directories. The synchronizer needs to choose one link as primary one and use junctions or symbolic links for all other paths, updating them in case the primary link (and its path) changes. 

1As an example, our analysis has shown that _ownCloud’s_ WebDAV implementation provides the unique ID of an object, while Apache’s _mod_dav_ does not. 

2Cycles cause problems for programs that iterate over the file system namespace, such as backup or synchronizer tools. If multiple links for directories were allowed, these tools wouldn’t be able to easily detect them. To detect them anyway, they would have to keep a _visited_ list, containing IDs and corresponding paths, which would be memory-intensive for large namespaces. In case of a backup tool, the inability to detect directory links would artificially inflate the number of files and dirs copied to the backup medium. In the worst case, this number becomes _infinitely_ large, if links formed a _cycle_ in the namespace. To enable users to conveniently access a directory from multiple other locations, they can instead use symbolic links or junctions, which are discussed in subsection 3.1.2. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

32 

||Files|Directories|Device<br>files|Symbolic<br>links|Others|
|---|---|---|---|---|---|
|Windows|✓|✓|✓<sup>3</sup>|✓|Link files,junctions|
|macOS|✓|✓|✓|✓|–|
|WebDAV<br>[Dus07]|✓|✓|X|X|–|
|BSCW<br>Social<br>[Orb18]|✓|✓|X|X|Plethora of object types other<br>than regular files and dirs, such<br>as: contact list, forum, calendar,<br>URL, poll, voting, appointment<br>scheduling,project, ....|
|Dropbox<br>[Dro17]|✓|✓|X|X|–|



Table 3.1: Supported object types per file system 

#### **3.1.2 Supported object types** 

Files and directories are the two object types offered by all examined file systems. [JLP13] show that even in groupware systems such as BSCW which offer many additional object types, the majority (90%) of user interaction takes place with these two object types. A file system may also support other object types that are incompatible with other systems. 

##### **3.1.2.1 Significance** 

When an object available on one file system is unavailable on the other one, its omission in the namespace, which is a loss of information, will confuse the user. 

##### **3.1.2.2 Analysis** 

While all examined file systems offer files and directories, there are several other types supported by just a subset of file systems, e.g. device files or symbolic links on macOS and Windows, or special types like contact lists, calendars or URLs on BSCW. Table 3.1 provides an overview. Some elements in table 3.1 are elaborated below: 

- **Device files** : files mapped into the namespace that allow programs to communicate with devices. Examples are block or character devices (terminals, printers or other physically installed devices) or communication channels such as named pipes or sockets. Since UNIX systems have the mantra that “everything is a file”<sup>4</sup> , the file system’s namespace is used to list and work with these devices. 

- **Symbolic links** : special files that link to a target path in the file system. The target is typically a (real) file or directory. The file system APIs (and file managers) automatically interpret these files and follow the redirection to the target path. Symbolic links differ from hard links in multiple points: 

   - They are distinct files with their own ID. Programs working with the file system can identify when a path is a symbolic link. Consequently, tools such as a backup programs can avoid backing up the same physical files and dirs multiple times, by detecting symbolic links and refusing to follow them. 

> 3Unlike for macOS, devices aren’t mapped in ordinary file system namespace, but are accessible from a dedicated namespace, such as \\.\DEVICENAME 

> 4See `https://web.archive.org/web/20120320050159/http://ph7spot.com/musings/in-unix-everything-is-a-file` , retrieved July 21, 2019. 

_3.1. CAPABILITY ANALYSIS_ 

33 

   - They can link to any target on any volume (whereas hard links pointing to a specific file always exist on the volume of that file). 

   - When the target moves or gets deleted, the symbolic link breaks. 

- **Link files** : similar to symbolic links: binary files with the .lnk extension that point to a user-defined target path. File managers automatically follow the redirection, while file system APIs typically don’t! 

- **Junctions** : a special type of symbolic link that causes an existing, empty directory to redirect to another target directory. They are implemented via _reparse points_<sup>5</sup> , a mechanism that attaches meta-data to the source directory, causing file system APIs and file managers to automatically follow the redirection to the target. 

##### **3.1.2.3 Derived unified model** 

By taking the intersection set of the available object types of each file system, the internal model should consist only of files and directories. We suggest to ignore other object types because they are specific to that file system and cannot be meaningfully viewed or manipulated on other systems that do not support them. 

##### **3.1.2.4 Advice for handling incompatibilities** 

We propose a similar handling as for mapping issues (section 3.1.1) where the synchronizer either stops or adds affected objects to the ignore list automatically, notifying the user about this action. A workaround is to create proxy objects, such as ’.url’ files, that allow the user to see the existence of the corresponding objects, redirecting the user to the respective location on the other file system in case she opens the proxy object. 

#### **3.1.3 Operations and atomicity** 

File system APIs offer many operations to both _query_ the current state of the file system (e.g. listing a directory’s content) or to _manipulate_ it. In the update detection stage a state-based file synchronizer relies on the _query_ operations to extract the current state. At the final _propagation_ stage, the synchronizer needs to transform the scheduled abstract operations (which equalize both file systems) to concrete _manipulation_ operations of each file system. This is challenging because the exact operations, their preconditions and their degree of atomicity<sup>6</sup> vary. 

##### **3.1.3.1 Significance** 

A user expects that operations she applied to her local file system are consistently applied to other file system by the synchronizer. Users also expect the synchronizer to avoid inconsistent states while synchronization is active or was interrupted. Not handling related issues causes confusion (e.g. attempting to open a partially transferred file) or additional work (such as manually cleaning up inconsistent files and directory structures) for the user. 

##### **3.1.3.2 Analysis** 

Each examined file system offers operations to _query_ the current state. This allows to list the names of immediate children of a directory and to retrieve both system-generated and arbitrary meta-data information about objects, such as their ID or the timestamp of last modification. There are slight variations in the query operation signatures between each file system, but these are merely an implementation detail. When considering _manipulation_ operations, all file systems offer operations to create or delete empty directories, or to move an object. However, there is significant variation in the availability and 

> 5See `https://docs.microsoft.com/en-us/windows/win32/fileio/reparse-points` , retrieved July 21, 2019. 

> 6We refer to _atomicity_ as known from database systems, see also section 1.3.4 of [EN15]. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

34 

||Windows|macOS|WebDAV|BSCW|Dropbox|
|---|---|---|---|---|---|
|Create empty<br>dir|✓|✓|✓|✓|✓|
|Create file|Initially<br>empty|Initially<br>empty|With content|With content|With content|
|Move file or<br>dir|✓|✓|✓|✓|✓|
|Copyfile|✓|✓(UNIX:X)<sup>7</sup>|✓|✓|✓|
|Copy dir<br>(with<br>sub-objects)|X|✓(UNIX:X)|✓|✓|✓|
|Delete file|✓|✓|✓|✓|✓|
|Delete empty<br>dir|✓|✓|✓|✓|✓|
|Delete<br>non-empty<br>dir|X|✓(UNIX:X)|✓|✓|✓|
|Create file<br>hard-link|✓|✓|X|✓|✓|
|Create dir<br>link|X|X|X|✓|✓|
|Create other<br>file types|Symlinks,<br>Junctions|Symlinks|X|Calendar,<br>contact list,<br>URL, ...|X|
|Mount<br>volume|✓|✓|X|X|X|



Table 3.2: Namespace manipulation operations per file system 

atomicity of operations used to create or update files, or to delete non-empty directories. For instance, BSCW allows to atomically create non-empty files or delete non-empty directories, while Windows does not. Another observation is that desktop file systems like Windows and macOS offer _mount_ operations which create a mount point that establishes a transition between volumes. An overview is shown in table 3.2. 

##### **3.1.3.3 Derived unified model** 

A user would expect a file synchronizer to be capable of a set of operations the user also knows from using the file manager. An exemplary list could be as follows: 

- _createdir(path)_ creates an empty directory at _path_ 

- _deletefile(path)_ deletes the file at _path_ 

- _deletedir(path)_ deletes the directory and all its children at _path_ 

- _move(source, dest)_ moves an existing object from _source_ to _dest_ 

- _transfer(source, dest)_ transmits a _file_ located at _source_ on the source file system to _dest_ on the destination file system, to create a new file or update an existing one 

7On a UNIX storage, files are copied by manually copying data-chunks by reading from the source file and writing to the destination file. 

_3.1. CAPABILITY ANALYSIS_ 

35 

To not leave either file system in an inconsistent state, every operation is expected to succeed or fail atomically. Optionally, a _copy file_ operation can be used to copy a file on the destination file system in case it is feasible to detect exact copies of files on the source file system, e.g. by using checksums. 

##### **3.1.3.4 Advice for handling incompatibilities** 

All discrepancies we found between concrete file system operations and the ones presented above result from varying degrees of atomicity, which can be solved in the following ways: 

- _deletedir(path)_ : if a file system does not offer an atomic, recursive implementation, we suggest to first call _move(path, temp)_ where _temp_ is a path outside of the synchronized namespace, but on the same volume. This move operation succeeds (or fails) atomically and _appears_ as an atomic delete operation to the synchronizer. Next, perform a _post-order_ traversal of _temp_ ’s subnamespace, deleting first files then directories. 

- _transfer(source, dest)_ : if the destination file system’s operation is not atomic, we propose to execute _transfer(source, temp)_ , i.e., write transferred data to a temporary location _temp_ that is outside the synchronized namespace but also on the same volume. Once finished, perform _move(temp, dest)_ on the destination file system. 

Finally, file synchronizers which detect move operations via the object’s ID should be aware of _mount points_ within the synchronized namespace. IDs are only unique within a volume. However, a mount point establishes a transition between volumes. When the user performs a conceptual _move(source, dest)_ operation where _source_ is on volume _A_ and _dest_ on volume _B_ , the synchronizer will incorrectly detect a _delete_ operation for _source_ and a _create_ operation for _dest_ . We therefore suggest that synchronizers detect mount points<sup>8</sup> and either reject them (by stopping synchronization) or automatically adding them to the _ignore list_ . 

#### **3.1.4 Namespace limitations** 

Although the general namespace allows each name to consist of an arbitrary sequence of _Unicode_ characters, a file system may pose limitations on the namespace, affecting paths or the names of a path, usually for technical or historical reasons. 

##### **3.1.4.1 Significance** 

When a user attempts to create an object with a name that violates a namespace limitation, the file manager (or web interface) prevents the creation and provides _immediate_ feedback how to fix the name. When using file synchronization, the chosen name may be accepted by the source file system API, but may violate a limitation of the destination API. The file synchronizer discovers this issue after a (possibly large) delay which surprises the user, because to her the creation of the object initially appeared to be successful. Furthermore, users will be confused if objects exist on one system but not the other one due to a limitation that affects only the latter system. 

##### **3.1.4.2 Analysis** 

Table 3.3 illustrates which limitations each file system’s API is affected by. The following list provides a 

> 8This can be achieved by querying the _device ID_ of an object on UNIX or macOS, or retrieve the _volume serial number_ of Windows files. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

36 

||Windows|macOS|WebDAV|BSCW|Dropbox|
|---|---|---|---|---|---|
|Case-<br>sensitive<sup>9</sup>|X|X(default) /<br>✓|Depends on<br>impl.<sup>10</sup>|✓|X|
|Unicode nor-<br>malization<br>sensitive|✓|X|Depends on<br>impl.|✓|Requires<br>NFC input|
|Reserved<br>characters|**/**<sup>11</sup>, <, >, :, “,<br>/, \, | ?, *|**/**, :<sup>12</sup>|Only**/**(when<br>other<br>characters<br>are<br>encoded*)|See WebDAV,<br>Windows’<br>reserved<br>characters|See WebDAV,<br>Windows’<br>reserved<br>characters|
|Reserved<br>names|**.**,**..**, ’ ’, CON,<br>PRN, AUX,<br>NUL, COM1-<br>COM9,<br>LPT1-LPT9<sup>13</sup>|**.**,**..**|–|Windows’<br>reserved<br>names|**.**,**..**|
|Max. path<br>length|32767|1016<sup>14</sup>|–|–|–|
|Max. name<br>length|255|255|–|–|255|
|Other<br>limitations|Names<br>ending with<br>’.’ or space<sup>15</sup>,<br>Short file<br>names|Violates<br>Unicode nor-<br>malization<br>preservation|–|–|–|



Table 3.3: Namespace limitations per file system 

*WebDAV character encoding: since all objects are addressed by URLs, subsection 2.2 of the URL specification (RFC 1738) addresses that in URLs there is a set of characters that are always _safe_ to use, a set of characters that are always _unsafe_ , and that each _scheme_ may have a set of _reserved_ characters (in designated positions of the URL) with special meaning. _Safe_ characters can be used in a name as is. It is necessary to _encode_ all _unsafe_ characters as well as those _reserved_ characters used outside of their normal context of the used scheme. In our case, the HTTP(S) scheme is used, therefore the “?” character (whose typical meaning is to start a _query string_ ) needs to be encoded when it should be used without that meaning, but, say, as part of a dir that should be created. The encoding process converts ASCII characters into a %<ascii hex representation> (e.g. _#_ is encoded as _%23_ ), non-ASCII characters (such as arbitrary Unicode characters) are converted to a UTF-8 byte sequence, of which each byte is encoded as %<byte value> (e.g. the character “ö” with UTF-8 code position U+00F6 has a hexadecimal byte representation of _c3b6_ and is thus encoded as _%C3%B6_ ). 

_3.1. CAPABILITY ANALYSIS_ 

37 

summary of our findings. We refer to the respective file system documentation for further details<sup>16</sup> . 

- A file system may reserve a set of _characters_ from being used in object names, either at any position, or only in specific positions. _Forward slashes_ are forbidden in all examined systems, as they separate names in a path. Windows reserves the most characters, and other systems such as BSCW or Dropbox have adopted Windows’ set of reserved characters and names for compatibility reasons. 

- Similarly, some systems reserve a set of _names_ , such as “.” or “..”. Windows reserves a large set of names such as “CON” or “PRN” for historical reasons and also reserves _short file names_ [Mic18b] in case a longer file name already exists (for example, given a directory named “project report”, creating an object at “projec~1” is forbidden on volumes with short file name creation enabled). 

- Many systems impose a maximum length of names and paths. Often names are limited to a length of 255 characters. Shorter path lengths (such as macOS with 1016 characters) also cause issues, e.g. deep directory hierarchies being inaccessible. 

- While all examined systems use the Unicode alphabet with some form of encoding (e.g. UTF8), not all systems _preserve_ the _normalization form_ (such as NFC or NFD<sup>17</sup> ) of characters. For instance, the HFS+ file system on macOS does not preserve a large set of input characters but converts them to an NFD-like form. 

- _Case-sensitivity_ may vary between two file systems. By default, the _examined_ systems are all caseinsensitive. However, others such as the UNIX file system, are case-sensitive! We found all systems to be case- _preserving_ . 

- In rare instances the file system APIs behave deceptively. They accept a name, seemingly execute successfully, but actually change the name internally. This is problematic for file synchronizers, as the next update detection phase will find an unexpected name and assume that the object was moved by the user. One example is the Unicode normalization conversion of HFS+ volumes mentioned above, another is Windows which silently strips trailing spaces/dots from a name during execution. 

##### **3.1.4.3 Derived unified model** 

For each limitation of file systems _A_ and _B_ we propose to take the one that is stricter (i.e., provides a more _narrow_ set of characters and names) and let the file synchronizer apply it to the file system with the weaker limitation. For _reserved characters_ or _names_ this means to apply the _union_ of the character/name sets to both _A_ and _B_ . For _length_ limitations, the shorter length is stricter. Regarding case-sensitivity, caseinsensitivity is stricter than case-sensitivity. 

> 9Storage systems with X are case-insensitive. However, _all_ of them are case- _preserving_ , i.e., when creating a file “/aX”, it is also stored with that upper/lower-casing. 

> 10According to subsections 5.1 and 5.2 of [Dus07] , the namespace can be case-sensitive or case-insensitive, as chosen by the implementation. 

> 11Forward slashes are generally not allowed in file or dir names, because they separate the names within a path. 

> 12The : character in a file/dir’s name is shown as forward slash in Finder, but the UNIX layer stores it as :, which can be verified using the _ls_ command. 

> 13Extensions thereof are also reserved, like “CON.” or “CON.ext” 

> 14This value was determined experimentally. The official documentation and other sources incorrectly state that there is no limit. 

> 15Windows APIs do accept paths end with dots or spaces, but they are stripped automatically. For instance, creating a new dir “test..” will create a dir named “test”. 

> 16See e.g. [BMM94], [App04], [App17] or [Mic18b]. 

> 17See `http://unicode.org/reports/tr15/` , retrieved July 21, 2019. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

38 

##### **3.1.4.4 Advice for handling incompatibilities** 

We suggest a file synchronizer takes one of the following approaches when encountering paths that are incompatible w.r.t. the unified limitations: 

1. Stop synchronization, ask the user to manually rename objects 

2. Automatically rename objects to establish compatibility 

3. Automatically add incompatible objects to the ignore list 

While approach (1) is easy to implement, it is labor-intensive for the user. In case the stopped synchronization goes unnoticed, and if it remains in that state for extended periods of time, this increases the chance for conflicts. Approach (2) mitigates this problem, but automatic renaming can cause issues when the affected objects belong to a naming scheme of a third-party application. Such applications may stop working once these files and directories no longer correspond to the expected naming scheme. The last approach fixes the issues of the two ones but requires the implementation of the aforementioned ignore list. 

#### **3.1.5 Meta-data** 

Meta-data provides further information about objects. It is not stored as part of the object, but at a separate location. 

##### **3.1.5.1 Significance** 

When meta-data stored on one file system is incompatible with the other file system, a synchronizer must skip their synchronization or perform a conversion. This type of data loss negatively affects the user, because she cannot access meta-data available only on the remote file system during an offline period. 

##### **3.1.5.2 Analysis** 

Each file system provides a diverse set of meta-data. Some meta-data are _attributes_ managed by the file system, others can be changed by a client application, such as a file synchronizer. Some systems offer one or more APIs to write _custom_ meta-data, e.g. _Extended Attributes_ and _Alternate Data Streams_ on Windows, or _xattr_ and _Resource forks_ on macOS. Appendix A.1 provides further details. The following meta-data is available on _all_ file systems: 

- Object type (file, directory, ...) 

- File size (for files) 

- Timestamp of creation and last modification 

##### **3.1.5.3 Derived unified model** 

All file systems support the retrieval of meta-data that is necessary to extract their state, such as the object’s type or the last-modified timestamp. In case a file synchronizer models the file system using IDs, all file systems except for WebDAV automatically generate and provide unique IDs. For WebDAV we propose that the _file synchronizer_ generates globally unique IDs (GUIDs) when creating objects on a WebDAV file system, assigning the GUID via the _PROPPATCH_ command. 

_3.1. CAPABILITY ANALYSIS_ 

39 

##### **3.1.5.4 Advice for handling incompatibilities** 

Some meta-data, such as _attributes_ , are system-specific and often lose meaning when copied to another file system, especially when it is of different type or located on a different operating system or machine. For instance, synchronizing the _compressed_ attribute of a Windows file to the corresponding file on a macOS file system defies any purpose. We find that bypassing meta-data synchronization largely facilitates a file synchronizer’s implementation. This also applies to _authorization_ mechanisms, such as UNIX _permissions_ or the more powerful _Access Control List_ entries, which can also be considered to be meta-data, with varying availability and heterogeneity.<sup>18</sup> 

The _last-modified_ timestamp is an exception. We suggest to synchronize it because it is typically available on each file system, has the same meaning everywhere and users are aware of it when using the file manager. Windows, macOS and Dropbox support overwriting this timestamp. WebDAV-based implementations (including BSCW) protect the timestamp from being modified. Here we propose to set the timestamp as a custom meta-datum instead. A caveat developers need to consider is the variety of resolutions and formats of timestamps. 

#### **3.1.6 Locking** 

_Locking_ allows one user to _exclusively_ modify an object on a file system, while all other users are prevented from modifying their own replica of that object. 

##### **3.1.6.1 Significance** 

Locking is an important mechanism that introduces _pessimistic_ concurrency control in situations where users expect that conflicts are likely to happen. It avoids conflicts or lost updates. In an example scenario, a user locks a document she exclusively wants to work on for an hour. During this time, other users should be unable to concurrently modify this file, and should be _aware_ of this lock while it is set. The information about the lock’s existence can be propagated by the synchronizer to other users while they are online. In practice we have not observed locking to play a role for files stored on _local_ disks. However, this feature is frequently used in groupware systems such as BSCW, and the transparent handling and awareness of locking behavior is an early requirement for CSCW systems as described in [BR94]. 

##### **3.1.6.2 Analysis** 

We analyzed the file systems’ locking capabilities to determine whether a file synchronizer can safely protect an object from modification by the local user, because a different user locked the object. We found that some systems such as Dropbox do not offer any locking mechanism. WebDAV and BSCW provide an elaborate locking model, including lock meta-data such as the owner and expiration time. 

The locking mechanisms of Windows ( _read-only_ attribute, _file handle_ locking) and macOS ( _immutable_ attribute, advisory locks via _fcntl_<sup>19</sup> API) are less elaborate. They each work differently and protect other aspects of modification. For example, the read-only attribute on Windows does not protect objects from being moved or renamed, while the immutable attribute on macOS does. 

We think that this diversity stems from the fact that each mechanism has a different purpose. On Windows and macOS the read-only/immutable file attribute or handle-based locks were not designed for a multi-user locking scenario. It is our understanding that they exist to allow users (and programs) to protect objects from modification _on the same device_ , not across multiple devices. Handle-based locking suffers from _volatile_ characteristics<sup>20</sup> . On macOS, handle-based locking is designed for a set of _cooperative_ programs and not intended to prevent third-party programs from modifying files. On Windows, 

> 18As an example for heterogeneity, macOS and Windows both support _Access Control Lists_ , but their implementations vary considerably. Additionally, synchronization of authorization data would require to also synchronize _authentication_ data, i.e., user accounts, which introduces additional challenges. 

> 19See `http://man7.org/linux/man-pages/man2/fcntl.2.html` , retrieved July 21, 2019. 

> 20When the program that owns the handle to an object terminates, the lock is automatically cleared. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

40 

handle-based locking has more wide-spread effect than just locking the object itself. It works on a “first come, first served” basis. Even just opening a file for reading already locks it, protecting it against move or delete operations. A file synchronizer may fail to obtain a lock, or inadvertently lock the path of any parent object, which is not desired. In addition, reliable recursive locking of a _directory_ is not possible with the mechanisms offered by Windows and macOS. 

##### **3.1.6.3 Derived unified model** 

In case a pair-wise synchronization targets two file systems of _equal_ type, such as two WebDAV systems, lock synchronization is feasible. In any other scenario we advise to ignore lock synchronization due to the strong differences in their implementation, making it impossible to meaningfully map one lock type onto another one. 

##### **3.1.6.4 Advice for handling incompatibilities** 

Not synchronizing locks does not necessarily mean that the synchronizer completely ignores locks. Some systems like WebDAV allow the _discovery_ of locks ( _before_ the attempt of modifying a locked resource). Assume a scenario where a synchronizer detects that user 1 updated file _f_ locally, while _f_ is locked on the remote replica by another user 2. The synchronizer may then skip synchronizing _f_ and notify user 1 about the lock’s existence. With additional implementation effort, a synchronizer may also monitor the local user’s opened files and warn her in case she opens a file that is locked by other users on the remote replica. We also suggest to convey the existence of locks by the use of _overlay icons_ in the file manager. 

If lock discovery is unavailable we propose to treat failed operations like any other permission-related failures, such as failures resulting from prohibitive ACL entries or UNIX permissions. The synchronization may be stopped or the affected object could be skipped. The user should be notified about the problem in either case and be provided with as much available information as possible to fix the problem. 

#### **3.1.7 Summary** 

Object & namespace mapping 



<!-- Start of picture text -->
Locking Supported object types<br>Windows<br>macOS<br>WebDAV<br>BSCW<br>Dropbox<br>Meta-data Operation degree of atomicity<br>Namespace limitations<br><!-- End of picture text -->

Figure 3.2: File system capabilities overview 

A summary of the capabilities of each file system is shown in figure 3.2. This radar chart depicts a rough estimate of the degree of power for each capability from 0% (center) to 100%, based on a technical evaluation that includes information from above sections and appendix A.1. Smaller values indicate less powerful namespace mappings, fewer supported object types, stronger namespace limitations, smaller level of locking, etc. We chose 20% as minimum value only to improve readability. By intersecting the 

_3.2. FILE SYSTEM MODEL DEFINITION_ 

41 

areas of the file systems a synchronizer supports we can derive the degree of limitations of the synchronizer’s internal model<sup>21</sup> . 

### **3.2 File system model definition** 

In this section we formally define the file system model _F_ using First Order Logic (FOL). We built _F_ based on the _NH-MD_ file system from section 2.3.1, while also considering the analysis from section 3.1. We provide exemplary alternative specifications for the _H-All_ and _NED-All_ file system models in appendix A.2. The rationale of each decision is summarized below: 

- Identity-based (rather than _path_ -based) because IDs allow to reliably discern _create_ , _delete_ and _move_ operations when using a _state-based_ update detection approach. Also, the majority of examined file systems are identity-based. 

- Operation support: we detect _delete_ , _move_ , _create_ and file _edit_ operations, and we expect their execution to be _atomic_ . We consider it an important aspect to preserve and synchronize _move_ operations instead of replacing them with _delete_ and _create_ , for three reasons [RC01]: 

   - Improved performance: a _move_ operation completes quickly and atomically, while deletion and creation may incur significant overhead, such as payload data. 

   - Retention of meta-data, such as access permissions, previous versions or an event history. 

   - Improved usability: a user would be confused if she checked the log of the other replica and found that her _move_ operations are not shown, but are replaced by _delete_ and _create_ operations. 

- Support for directories (even empty ones). We think that users expect directory support, because directories are also provided by the file managers they regularly use. 

- No hardlink support: examining the physical object and namespace mapping from section 3.1.1, we see that to be most compatible with all of the examined real-world file systems, _F_ must limit each object to exist exactly once in the tree. We choose to model the name as part of the object, rather than of parent-child relation (this choice is arbitrary). 

- The supported, synchronized object types are limited to files and directories, because they are common to all examined file systems. 

- We do _not_ enforce any _namespace limitations_ in _F_ ’s invariants or operations directly. This allows to build _internal_ file system states during _update detection_ stage which accurately reflect the underlying, real file system’s state (which may have no namespace limitations). As explained later in section 7.2.1 on page 135, our implementation still enforces a set of namespace limitations _L_ , but defers the evaluation and correction of object names until the _reconciliation_ stage. _L_ dictates that name-comparisons are case- _in_ sensitive and Unicode normalization- _in_ sensitive, name lengths are limited to 255 characters and various limitations from Microsoft Windows are applied to _all_ file systems, such as reserved characters and names. 

#### **3.2.1 File system constituents** 

We define _F_ as a set of tuples where each tuple represents an object with a unique ID _i ∈ I_ , parent directory ID _p ∈ I_ (where _I_ is the set of unique IDs), type _t ∈ T_ (with _T =_ � _f ile_ , _dir_ �), name _n ∈_ Σ<sup>_+_</sup> (with Σ<sup>_+_</sup> _=_ Σ<sup>_∗_</sup> \{ _ϵ_ }), _lastmodified_ meta-datum _l ∈ L_ (where _L_ is the set of all valid meta-datum values, e.g. N) and content _b ∈ B_ (where _B_ is the set of arbitrary byte sequences, including _ϵ_ ). That is, _F ⊂ I × I × T ×_ Σ<sup>_+_</sup> _× L × B_ , with tuples ( _ik_ , _pk_ , _tk_ , _nk_ , _lk_ , _bk_ ) where the following invariants hold: 

> 21The only exception is _atomicity_ where, instead of accepting the union of all limitations, we suggested to _emulate_ a higher level of atomicity. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

42 

|Function|Description|
|---|---|
|_name_(_i_)|Returns the name_nk_ of the tuple where_ik = i_, or_error_ if no such tuple<br>exists.|
|_type_(_i_)_→_<br>�<br>_dir_, _f ile_<br>�|Returns type_tk_ of the tuple where_ik = i_, or_error_ if no such tuple exists.|
|_list_(_i_)|Returns the set of IDs of the_immediate_child nodes for the node with<br>ID_i_, i.e., the set of IDs of all tuples for which _pk = i_ holds.|
|_content_(_i_)|Returns the binary data_bk_ of the tuple where_ik = i ∧t ype_(_i_)_= f ile_, or<br>_error_if no such tuple exists.|
|_lastmodif ied_(_i_)|Returns the_lastmodified_meta-datum for the node with ID_i_.|
|_id_(_i_,_n_)|Returns the ID of the tuple where_pk = i_ and name_nk = n_, or_error_ if no<br>such object exists.|



Table 3.4: Functions for working with file system IDs 













where helper functions are defined in table 3.4 and _e_ is a shorthand for _error_ in equation 3.6. We additionally define the predicate 



to express whether the object with ID _i_ is an ancestor of the object with ID _j_ . _F_ is an arborescence rooted in the well-known object _iroot ∈ I_ with _t ype_ ( _iroot_ ) _= dir_ , where each object exists exactly once. Equation 3.6 expresses that each object must be reachable from _iroot_ , while equation 3.7 states that no two siblings may have the same name. 

#### **3.2.2 File system operations** 

The file system operations and their pre- and postconditions are specified in table 3.6. As it is often more intuitive to address objects using _paths_ rather then IDs, we also indicate the relationship between IDs and paths. A _path_ is a notation for addressing a specific file or dir node with ID _i_ in the file system tree. It is built by concatenating the _names_ of all traversed nodes, starting from _iroot_ until reaching node _i_ , separating the names with / (forward slash). Path-related helper functions are defined in table 3.5. Our preconditions and invariants are equivalent to those defined by [NSE18], which the authors proved to be correct using the CISE SMT solver [Got+16]. 

_3.3. CONCLUSION_ 

43 

|Function / Predicate|Description|
|---|---|
|_split_(_path_)|Returns a list of the individual name segments, by splitting_path_at each<br>_/_character.|
|_parent_(_path_)|Returns_/_for all paths that contain_/_once, otherwise removes from path<br>all characters from the right-hand side up to (including) the first<br>encountered_/_character.|
|_basename_(_path_)|Returns the last list element of_split(path)_, e.g.<br>_basename(’/home/user/foo’) = ’foo’_. Formally, if we can split_path = π_/_ω_<br>s.t._π = parent_(_path_) and_ω = basename_(_path_) then the following<br>holds:<br>_p = π_/_ω ⇐⇒∃i_, _j ∈I_ :_i ∈list_(_j_)_∧path_(_j_)_= π∧name_(_i_)_= ω_|
|_id_(_path_)|Returns the id of_path_in case traversal was successful, or_error_<br>otherwise. The traversal algorithm starts with_i = iroot_, and iteratively<br>updates_i = id_(_i_,_n_) for each_n_in_split_(_path_).|
|_path_(_i_)|Performs a_tree search_starting at_iroot_, iterating over_all_nodes until the<br>node_n_with ID_i_ was found. If no node is found,_error_is returned.<br>Otherwise returns the path from the root node to_n_, concatenating the<br>respective names of the nodes alongthe traversal-path.|



Table 3.5: Functions for working with paths 

### **3.3 Conclusion** 

This chapter addresses all aspects of RQ1. We completed the analysis of file system definitions (which we started in the previous chapter for _academic_ models) by examining _industrial_ file system models for heterogeneity, sampling a set of representative systems. The discovered _capabilities_ reflect the _“criteria [...] relevant for file synchronizers”_ of RQ1. Most of the characteristics we found for academic file systems, such as _identity- vs. path-based_ , different _hardlink support_ and varying _operation support_ are also present in industrial systems. However, by experimentation and studying manuals we discovered several _additional_ heterogeneous capabilities not discussed in academic works which file synchronizers need to address, including namespace limitations, meta-data or locking. 

Our general suggestion for building a model with maximum compatibility is to limit its features to the lowest common denominator. As doing so for _all_ discovered capabilities would result in a weak model, _F_ follows this advice only for some capabilities (meta-data, locking, supported object types). For other capabilities, like _mappings_ or _operations_ , _F_ demands stronger features to improve performance and usability. 

_CHAPTER 3. FILE SYSTEMS - ANALYSIS AND DEFINITION_ 

44 

|Operation|Description,pre- andpostconditions|
|---|---|
|_createdir_(_path_),<br>_createdir_(_i_,_p_,_n_)|Creates a new, empty directory with ID_i_ and name_n = basename_(_path_)<br>in parent directory with ID_p = id_(_parent_(_path_))_._<br>• Precondition:_¬ancestor_(_iroot_,_i_)_∧ancestor_(_iroot_,_p_)_∧t ype_(_p_)_=_<br>_dir ∧id_(_p_,_n_)_= error_<br>• Postcondition:<br>_i ∈list_(_p_)_∧t ype_(_i_)_= dir ∧lastmodi f ied_(_i_)_̸ = error_|
|_create f ile_(_path_),<br>_create f ile_(_i_,_p_,_n_)|Creates an empty file with ID_i_ and name_n = basename_(_path_) in parent<br>directory with ID_p = id_(_parent_(_path_)).<br>• Precondition: see_createdir_(_path_)<br>• Postcondition:<br>_i ∈list_(_p_)_∧t ype_(_i_)_= f ile ∧lastmodi f ied_(_i_)_̸ = error_|
|_move_(_source_,_dest_),<br>_move_(_i_,_u_,_v_,_n_)|Moves a file or directory with ID_i = id_(_source_) from parent directory with<br>ID_u = id_(_parent_(_source_)) to_v = id_(_parent_(_dest_)), and/or change the<br>object’s name to_n_.<br>• Precondition: _t ype_(_u_)_= dir ∧i ∈list_(_u_)_∧t ype_(_v_)_= dir_<br>_∧id_(_v_,_n_)_= error ∧¬ancestor_(_i_,_v_)<br>• Postcondition:_i ∈list_(_v_)_∧i ∉list_(_u_)<br>Note: precondition_¬ancestor_(_i_,_v_) ensures that the user cannot move a<br>directory to a destination dir below it, e.g._source =_’/A’ cannot be moved to<br>_dest = ’/A/x’._|
|_delete f ile_(_path_),<br>_delete f ile_(_i_,_p_)|Removes the file with ID_i = id_(_path_) from parent directory with ID<br>_p = id_(_parent_(_path_)).<br>• Precondition: _ancestor_(_iroot_,_i_)_∧i ∈list_(_p_)_∧t ype_(_i_)_= f ile_<br>• Postcondition:<br>_i ∉list_(_p_)_∧¬ancestor_(_iroot_,_i_)_∧lastmodi f ied_(_i_)_= error_|
|_deletedir_(_path_),<br>_deletedir_(_i_,_p_)|Removes the empty directory with ID_i = id_(_path_) from parent directory<br>with ID_p = id_(_parent_(_path_)).<br>• Precondition: _ancestor_(_iroot_,_i_)_∧t ype_(_i_)_= dir ∧list_(_i_)_=_{}<br>• Postcondition: see_delete f ile_(_path_)|
|_edit_(_path_,_op_),<br>_edit_(_i_,_op_)|Changes the byte content of file with ID_i = id_(_path_) by performing the<br>operation_op_ (e.g. adding, removing or changing bytes at specific positions<br>within the file).<br>• Precondition: _ancestor_(_iroot_,_i_)_∧t ype_(_i_)_= f ile_<br>Let_lpre = lastmodi f ied_(_i_)<br>• Postcondition: _ancestor_(_iroot_,_i_)_∧lastmodi f ied_(_i_)_̸ = lpre_|



Table 3.6: File system operations 

45 

## **Chapter 4** 

# **Update detection for file systems** 

A central component of every file synchronizer is _update detection_ , which is concerned with observing the file system of a replica for changes. It delivers the changes to the reconciliation algorithm. The reconciliation algorithm then decides which operations need to be executed on each replica to equalize their state. 

In our work we build a _state-based_ file synchronizer that runs in the _background_ and continuously synchronizes two replicas, which cannot be protected (locked) against concurrent user activity. Its update detector should ideally have the following characteristics: 

1. Deliver _operations_ and the _current state_ in near _real-time_ , to allow the synchronizer to start working in a timely manner. A state-based reconciliation requires the current _state_ as foundation for finding updates. Real-time _operations_ are helpful to allow the synchronizer to discern concurrently executed operations by the _user_ from those executed by the synchronizer itself. This allows the synchronizer to _abort_ an on-going synchronization in case the user’s concurrent operations are conflicting its own scheduled ones (e.g. if a user locally deleted a file _after_ the reconciliator scheduled its upload operation). 

2. Put as _little load_ as possible on the replica, to allow a large number of concurrent users, e.g. on a server. 

In this chapter we look at update detection mechanisms offered by file systems. We identify issues that violate the above characteristics and propose solutions. We start with analyzing available mechanisms in real-world file systems to extract an overview of approaches in section 4.1. In the remaining sections we take a closer look at the specific approaches. 

### **4.1 Analysis and overview** 

Many data synchronization solutions, such as near real-time collaborative text editors, have a _built-in_ replication mechanism, which is thus tightly coupled to the application itself. Unfortunately, heterogeneous file synchronizers are typically loosely coupled. The application, the file system, is not aware of its synchronization. A file synchronizer needs to extract its current state and operations, using APIs offered by the file system. We start with an API analysis in section 4.1.1 and extract a taxonomy from it, presented in section 4.1.2. 

#### **4.1.1 File system API analysis** 

A basic requirement for every file system is the ability to sample the current state, by listing all objects in a directory and extracting their attributes (ID, parent, name, type, lastmodified - see section 3.2.1). Without going into details, our analysis shows that all file systems examined in section 3.1 (Windows, 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

46 

||Online|Offline|
|---|---|---|
|Windows|✓<br>_ReadDirectoryChangesW_ <sup>1</sup>|✓<br>_Change journal_<sup>2 </sup>keeps track of<br>file operations. It is limited in<br>length, onlyfor NTFS|
|macOS|✓<br>_FSEvents_<sup>3</sup>|X|
|WebDAV|(X)<br>See below for more details|(X)<br>See below for more details|
|BSCW,|✓|✓|
|Dropbox|ViaproprietaryREST interface|ViaproprietaryREST interface|



Table 4.1: File system APIs for update detection 

macOS, BSCW, WebDAV, Dropbox) offer APIs for this purpose. Because _regular sampling_ to detect activity is expensive, we further analyze APIs that detect operations in near real-time in table 4.1. We subdivide them into _online_ and _offline_ , where _online_ APIs only provide information while a process (such as the file synchronizer) subscribes to receiving changes, whereas _offline_ APIs have an internal persistence mechanism, offering a process to retrieve information at any (later) point of time. 

Regarding WebDAV, its RFC [Dus07] does not provide any means for monitoring changes. There is RFC 6578<sup>4</sup> , a WebDAV extension that offers a _sync-collection report_ that contains changes that took place since retrieving the previous report, with the help of a sync token. As is common with HTTP, the API needs to be called in regular intervals (polling), but it allows to track changes even while the client is not online. To the best of our knowledge, at the time of writing there is no implementation of RFC 6578 for tracking changes of files and directories. There are some implementations which support it for synchronizing changes of calendars and contact information<sup>5</sup> . 

#### **4.1.2 Taxonomy** 

From above analysis we build a taxonomy of update detection approaches: 

1. **Log-based update detection:** the state-based file synchronizer uses an existing file system API that provides a _complete_ log of all operations as a side effect of executing them. There are two sub-categories: 

   - a) **Online update detection:** a callback mechanism transmits operations to the file synchronizer (while it is active) in the order they happen. Operations taking place while the file synchronizer is stopped will be lost. Examples are the callback-based APIs provided by macOS or Windows for local disks. Section 4.3.3 discusses technical challenges that occur when using such APIs, because the provided operation events often lack important information or are incorrect to begin with. 

   - b) **Offline update detection:** similar to the online-mechanism, but the log of operations is _persistently_ stored by the file system, irrespective of whether the file synchronizer is active. The file synchronizer can retrieve all operations even after a period of inactivity. Only some storage systems such as Dropbox or BSCW offer such a service. The API is typically token-based. 

> 1 `https://docs.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-readdirectorychangesw` , retrieved July 21, 2019. 

> 2 `https://docs.microsoft.com/en-us/windows/win32/fileio/change-journals` , retrieved July 21, 2019. 

> 3 `https://developer.apple.com/library/content/documentation/Darwin/Conceptual/FSEvents_ProgGuide/ Introduction/Introduction.html` , retrieved July 21, 2019. 

> 4 `https://www.greenbytes.de/tech/webdav/rfc6578.html` , retrieved July 21, 2019. 

> 5 `http://sabre.io/dav/sync/` , retrieved July 21, 2019. 

_4.2. STATE-BASED UPDATE DETECTION_ 

47 

The file synchronizer calls the API (with a token received from a previous call) and receives a new token together with a list of all operations that took place since the last API call. 

2. **State-based update detection:** using the (always available) file system APIs to build a file system _snapshot_ , the file synchronizer computes operations from those. By taking snapshots at times _t_ 1 and _t_ 2 and comparing them with each other, it deduces the list of file system operations. Section 4.2 provides further details. 

3. **Hybrid update detection:** uses _state-based_ update detection as foundation, but ensures that the characteristics from the introduction are maintained, by additionally using _log-based_ update detection where available. Section 4.3 provides more details. 

As stated in section 4.1.1, approach (2) is available for every file system API. Several academic works explicitly state the lack of availability of log-based update detection mechanisms as rationale for using a similar approach [LKT05; RC01]. In our implementation we use a _hybrid_ approach presented in section 4.3. 

### **4.2 State-based update detection** 

In this section we elaborate on details of state-based update detection. We start with _snapshots_ which we describe in section 4.2.1, which are a data structure that stores state information. While snapshots are typically kept in memory and are specific to one replica, _database snapshots_ are a persistently stored variant which include two replicas, see section 4.2.2. We describe how operations are computed from two snapshots in section 4.2.3. In section 4.2.4 we describe update trees, which are memory structures used in our reconciliation algorithm with better efficiency than snapshots. Finally, we address the problem of operation consolidation in section 4.2.5, which is specific to state-based update detection, and discuss its side effects and a solution in subsequent sections. 

#### **4.2.1 Snapshots** 

In section 4.1.1 we discussed that obtaining the current state of a replica is feasible in all examined file system APIs. File system states change over time and query operations are expensive, thus we now define the _snapshot_ data structure which is kept in memory and represents a replica’s _abstracted_ state at a specific point of time. It stores the list of objects with their attributes, such as ID, name, parent ID, etc. and can be built using the functions _list_ ( _i_ ), _t ype_ ( _i_ ), _name_ ( _i_ ), _lastmodi f ied_ ( _i_ ) from section 3.2.1. Its functions are shown in table 4.2, which are similar to those defined in section 3.2. These snapshot functions also return _error_ in case looking up the requested data is not successful. Note that snapshots do not store any file contents, but only checksums of files. 

#### **4.2.2 Database snapshots** 

To compute operations it is necessary to take two snapshots at times _t_ 1 and _t_ 2 and compare them with each other. We present the corresponding algorithm in the next section. In a file synchronizer the computation is always done between two snapshots of the _same_ replica, where the first snapshot is the persisted<sup>6</sup> _database snapshot_ that expresses the replica’s state after the last synchronization, while the second snapshot reflects the _current_ replica’s state. Because the state of the two replicas and the database is exactly equal once their synchronization has successfully finished, it is sufficient to have _one_ database snapshot that stores information of _both_ replicas. Only the replica-specific IDs mismatch, thus the database snapshot needs to establish a relationship between them. 

A database snapshot _dbsnapshot_ internally stores each object as a tuple 

_〈idb_ , _pdb_ , _name_ , _i X_ , _iY_ , _lastmodi f iedX_ , _lastmodi f iedY_ , _t ype_ , _checksum〉_ 

> 6For instance, a database snapshot might be built from a table stored in a relational database. 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

48 

|Function|Description|
|---|---|
|_id_(_snapshot_,_path_)|Returns the id of the path in case traversal was successful, or_error_<br>otherwise.|
|_type_(_snapshot_,_i_)|Returns the type (file, dir) of the object with ID_i_.|
|_lastmodif ied_(_snapshot_,_i_)|Returns the_lastmodified_meta-datum for_i_.|
|_parent_(_snapshot_,_i_)|Returns theparent directoryID of the object with ID_i_.|
|_path_(_snapshot_,_i_)|See _path_(_i_) in table 3.5.|
|_name_(_snapshot_,_i_)|Returns_basename_(_path_(_snapshot_,_i_)).|
|_checksum_(_snapshot_,_i_)|Returns the checksum of the content of the file with ID_i_, or_error_if_i_<br>is not known or_type_(_snapshot_,_i_)_= dir_.|
|_paths_(_snapshot_)|Returns the list ofpaths contained in_snapshot_.|
|_ids_(_snapshot_)|Returns the list of IDs contained in_snapshot_.|
|_ancestor_(_snapshot_,_i_,_j_)|Returns whether node with ID_i_ is an ancestor of the node with ID _j_<br>in_snapshot_.|



Table 4.2: Functions for working with snapshots 

|Function|Description|
|---|---|
|_dbid_(_dbsnapshot_,_i_,_r_)|Returns_idb_ for the provided ID_i_ of replica_r_ if_i_ is known,_error_<br>otherwise.|
|_id_(_dbsnapshot_,_idb_,_r_)|Returns the replica-specific ID of replica_r_ for the database ID_idb_,<br>_error_otherwise.|



Table 4.3: Functions for working with database snapshots 

where _iddb_ is an unique (primary key) ID generated by the database engine. It establishes the 1:1 mapping between the IDs _i X_ , _iY_ of the replicas, where replica identifiers are denoted _X_ , _Y_ henceforth. The same principle applies to _lastmodi f ied_ . _t ype_ details whether the path points to a file or directory and _pdb_ expresses the database ID of the parent object. This compact representation allows to generate both _dbsnapshotX_ and _dbsnapshotY_ , where e.g. _dbsnapshotX_ is the database snapshot for replica _X_ . To generate _dbsnapshotX_ we only need _iddb_ , _pdb_ , _name_ , _i X_ , _lastmodi f iedX_ , _t ype_ from each tuple. All snapshot functions defined in table 4.2 also apply to _dbsnapshotX_ and _dbsnapshotY_ . Additionally, functions _dbid_ ( _dbsnapshot_ , _i_ ) and _id_ ( _dbsnapshot_ , _idb_ , _r_ ) defined in table 4.3 also apply. 

#### **4.2.3 Operation computation** 

The function _compute_ops(_ _`db`_ , _`snapshot` )_ shown in algorithm 1 detects an unordered set of operations _O =_ { _o_ 1,..., _on_ } that explain the differences between the snapshots `db` and `snapshot` . For each _oi_ we define the following functions: 

- _opt ype_ ( _oi_ ) returns the operation’s type: _create_ , _delete_ , _move_ , or _edit_ 

- _id_ ( _oi_ ) retrieves the object’s ID 

- _t ype_ ( _oi_ ) returns the object’s type (file, directory) 

- _lastmodi f ied_ ( _oi_ ) returns the object’s _lastmodified_ meta-datum (the updated one for edited files) 

- _path_ ( _oi_ ) returns the path of the affected object, or path-tuple _(source,dest)_ if _opt ype_ ( _oi_ ) _= move_ . If _opt ype_ ( _oi_ ) _∈_ { _edit_ , _delete_ } then _path_ ( _oi_ ) returns the path from `db` rather than the one from `snapshot` . 

We can also express _compute_ops(db, snapshot)_ using FOL to more clearly demonstrate the relationship between the post-conditions of operations introduced in table 3.6 and the detection of these operations: 

_4.2. STATE-BASED UPDATE DETECTION_ 

49 

- 1 Input : db, snapshot : snapshot structures taken at two different points of time , of the same replica 

- 2 Output : **set** of detected operations 3 

- 4 O = Set () 

- 5 **for** i **in** ids (db) : 

- 6 current_path = path ( snapshot , i ) 

- 7 **i f** current_path == error : 8 o = Operation ( delete , i , **type** (db, i ) , lastmodified (db, i ) , path (db, i ) ) 

- 9 O. put (o) 

- 10 **continue** 

- 11 

- 12 has_moved = name(db, i ) != name( snapshot , i ) **or** parent (db, i ) != parent ( snapshot , i ) 13 **i f** lastmodified (db, i ) != lastmodified ( snapshot , i ) **and type** (db, i ) == **f i l e** : 14 o = Operation ( edit , i , **file** , lastmodified ( snapshot , i ) , path (db, i ) ) 

- 15 O. put (o) 

- 16 **i f not** has_moved : 17 **continue** 

- 18 

- 19 **i f** has_moved : 

- 20 path_tuple = ( path (db, i ) , path ( snapshot , i ) ) 21 o = Operation (move, i , **type** (db, i ) , lastmodified ( snapshot , i ) , path_tuple ) 

- 22 O. put (o) 

- 23 **continue** 

- 24 

- 25 **for** i **in** ( ids ( snapshot ) _−_ ids (db) ) : _# consider only IDs in ’ snapshot ’ that are not in ’ db ’_ 

- 26 o = Operation ( create , i , **type** ( snapshot , i ) , lastmodified ( snapshot , i ) , path ( snapshot , i ) ) 

- 27 O. put (o) 

**Algorithmus 1 :** Pseudo-code for _compute_ops(_ _`db` ,_ _`snapshot` )_ 

- Creation of a file or directory, then a new ID must exist in _snapshot_ that did not exist in _db_ yet: _∃o ∈ O_ : _opt ype_ ( _o_ ) _= create ⇐⇒ id_ ( _o_ ) _∈ ids_ ( _snapshot_ ) _∧ id_ ( _o_ ) _∉ ids_ ( _db_ ) 

- Deletion of a file or directory, then the corresponding ID that still exists in _db_ is no longer available in _snapshot_ : _∃o ∈ O_ : _opt ype_ ( _o_ ) _= delete ⇐⇒ id_ ( _o_ ) _∈ ids_ ( _db_ ) _∧ id_ ( _o_ ) _∉ ids_ ( _snapshot_ ) 

- Moving a file or directory, then the ID exists in both snapshots, but with different parent ID or name: _∃o ∈ O_ : _opt ype_ ( _o_ ) _= move ∧ path_ ( _o_ ) _=_ ( _source_ , _dest_ ) _⇐⇒ id_ ( _o_ ) _∈ ids_ ( _snapshot_ ) _∧ id_ ( _o_ ) _∈ ids_ ( _db_ ) _∧_ [ _name_ ( _db_ , _id_ ( _o_ )) _̸ = name_ ( _snapshot_ , _id_ ( _o_ )) _∨parent_ ( _db_ , _id_ ( _o_ )) _̸ = parent_ ( _snapshot_ , _id_ ( _o_ ))] 

- Editing a file, then the ID exists in both snapshots, but with different _lastmodified_ meta-datum: _∃o ∈ O_ : _opt ype_ ( _o_ ) _= edit ∧ t ype_ ( _o_ ) _= f ile ⇐⇒ id_ ( _o_ ) _∈ ids_ ( _snapshot_ ) _∧ id_ ( _o_ ) _∈ ids_ ( _db_ ) _∧ lastmodi f ied_ ( _db_ , _id_ ( _o_ )) _̸ = lastmodi f ied_ ( _snapshot_ , _id_ ( _o_ )) 

It is easy to see that the number of detected operations is finite, as the following axiom and lemma show. 

**Axiom 1.** The number of objects (files, dirs) _n = |R|_ in any replica _R_ is _finite_ , because each _R_ requires each object to be explicitly created by the user, creation speed is limited to _y_ objects/second, _R_ comes into existence with 0 objects at time _t_ (in the past) with _now − t < ∞_ . 

**Lemma 1.** _The number of operations detected by_ compute_ops( `db` , `snapshot` ) _is finite._ 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

50 

_Proof._ by contradiction: Let _O=compute_ops(_ _`db` ,_ _`snapshot` )_ . Let _k = |O|_ be the number of detected operations. For _k_ to be infinite, and because _|db|_ and �� _snapshot_ �� are finite (as demonstrated in axiom 1), the _compute_ops_ algorithm must find an infinite number of operations for some object pair ( _x_ , _y_ ), _x ∈ db ∨ x = ϵ_ , _y ∈ snapshot ∨ y = ϵ_ . However, it is trivial to see (and is further demonstrated in section 4.2.5) that _operation consolidation_ does not allow for this: for any ( _x_ , _y_ ) we can only detect one of the following operations: none, create, delete, move, edit, move+edit. I.e., for any ( _x_ , _y_ ) the number of operations that can be detected ranges from 0 _< ∞_ to 2 _< ∞_ . 

#### **4.2.4 Update trees** 

To find and resolve conflicts, the _snapshot_ structure and operation set _O_ is insufficient. Instead our algorithm converts a _database snapshot_ and _O_ of a specific replica into an _update tree_ . The update tree is an in-memory tree structure where each node represents a specific file or directory. Once built, it represents the file system in the _current_ state of the respective replica, but also contains deleted nodes. This tree improves run-time performance by providing efficient means to _search_ for nodes, e.g. using _idb_ or _i X_ , _iY_ and offers efficient traversal both up and down the hierarchy. In contrast to (immutable) snapshots and computed operation sets, the structure of update trees can be efficiently manipulated and easily updated with meta-information by different components of the reconciliation and propagation phase. More specifically, the conflict finder and resolver components update the tree by marking which conflicts were already considered for each node, to avoid searching for conflicts more than once. The propagation component keeps the update tree structure in sync with the changes it applies to the physical replica and to the database. This allows the update trees to serve as shadow copy of the file system’s current state and allows to efficiently look up the correct paths during propagation, which is necessary because file system APIs are typically not ID- but _path-based_ . 

Each node has the following attributes: 

- _idb_ : provided by the database snapshot, is undefined (-1) for _created_ files or directories, which are not part of the database yet 

- _side_ : the identifier of the replica ( _X_ , _Y_ ) 

- _name_ : the name of the object 

- _type_ : file or dir 

- _change_events_ : list of change-events (more details below) 

- _ID_ and _lastmodified_ meta-data, where _ID_ is the replica-specific ID, _i X_ or _iY_ 

- _processed_ : indicates whether the node was already considered during reconciliation, i.e., while generating operations 

- _children_ : list of child nodes 

- _parent_ : pointer to the parent node 

Objects that were moved by the user have two additional attributes: 

- _move_origin_ : the path of the object before it was moved 

- _move_origin_parent_id_ : _pdb_ , that is, the ID of the object’s parent directory _before_ it was moved 

We use _n_ for nodes. Attribute access is denoted with a dot, e.g. _i = n_ . _ID_ . The path _p = path_ ( _n_ ) of a node _n_ is computed by concatenating the nodes’ names from the root node to _n_ , with a forward slash separation character. 

Figure 4.1 shows two visualizations of an update tree representing a file system before and after the user applied some operations. Efficient search for nodes is implemented by adding look-up tables at each tree’s _root_ node, mapping from ID to the node object. 

_4.2. STATE-BASED UPDATE DETECTION_ 

51 



<!-- Start of picture text -->
S<br>1 2<br>            some dir: Dir                                 some file.html: File<br>3 4<br>            another dir: Dir                                 another file.txt: File<br>(a) Start situation<br>L<br>4 2<br>1<br> another file.txt: File             some file.html: File<br>some dir: Dir<br>Move ’some dir/another file.txt’ (P-ID: 1) Edit<br>3<br>another dir: Dir<br>Delete<br>(b) Changed situation<br><!-- End of picture text -->

Figure 4.1: Update tree visualization 

Subfigure (a) shows a visualization of an update tree of a file system as a graph. At the top, the label _S_ indicates _Synchronized_ state (i.e. the local and remote replica have the same structure) - alternatively _L_ or _R_ indicate that the update tree is of the _Local_ or _Remote_ replica respectively (see subfigure (b)). _L_ and _R_ are used synonymously to _X_ and _Y_ for identifying replicas. In the tree each file or dir is represented by a node. The top left corner indicates the node’s _idb_ . The first line of the node indicates its _name_ and _type_ (file, dir). The line(s) below the name indicate _changeevents_ , if present. Because subfigure (a) depicts a synchronized file system, there are no changeevents. In contrast, subfigure (b) shows a situation in which the user changed the file system from subfigure (a) via multiple operations. “/some dir/another dir” was deleted, “/some file.html” was edited, and “/some dir/another file.txt” was moved to the root level. The line in the node (here: _idb =_ 4) that starts with “Move” has the following form: Move <source path> (P-ID: _n_ . _move_origin_parent_id_ ) 

P-ID is _−_ 1 in case the source parent dir is the _root_ directory, since the root directory itself is not part of the database and thus has no _idb_ value. 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

52 

##### **4.2.4.1 Change-events** 

To efficiently find conflicts or to determine non-conflicting operations, all those nodes affected by the user since the last synchronization are marked dirty during the creation of the tree, using the information from the set of computed operations _O_ . Instead of using a single dirty bit, each node is assigned 0-2 change-events (like a bit _mask_ ). We define the following events: 

- _Create_ : indicates that a new file or dir was created, its _ID_ is unknown to the database 

- _Edit_ : for files: indicates that the file’s content was changed (or it was deleted and replaced by another created file, see next section for more details) 

- _Delete_ : indicates that a file or dir was deleted (either directly, or indirectly because a parent node was deleted) 

- _Move_ : indicates that a file or dir was moved, by the user (changing the name, parent node, or both) 

##### **4.2.4.2 Generation** 

We perform the update tree generation as follows: 

1. Create dir nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= move ∧ t ype_ ( _oi_ ) _= dir_ , using _path_ ( _oi_ ). Assign each node with the _Move_ change-event. This step (as well as others) creates missing intermediate nodes if necessary (e.g. if _path_ ( _oi_ ) _=_<sup>_′_</sup> _some_ / _dir_<sup>_′_</sup> then create an intermediate node for ’some’ if it doesn’t exist yet). 

2. Create file nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= move∧t ype_ ( _oi_ ) _= f ile_ , see above for details. 

3. Create dir nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= delete ∧ t ype_ ( _oi_ ) _= dir_ . Each node is assigned the _Delete_ change-event. An important detail is that for any such _oi_ , _path_ ( _oi_ ) points to the path of the `db` snapshot. In case the user applied operations _o_ 1 _=move(’/dir’, ’/dirMoved’)_ + _o_ 2 _=deletedir(’/dirMoved/somedir’)_ to a replica, then _path(o_ 2 _)=’/dir/somedir’_ ! When inserting the node for _o_ 2 into the tree, the insertion algorithm must first attempt to find the possibly existing new path ( _’/dirMoved/somedir’_ in this case). 

4. Create file nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= delete ∧ t ype_ ( _oi_ ) _= f ile_ . Like above, each node is assigned the _Delete_ change-event. In addition we also implement the special case where we replace the _Delete_ with an _Edit_ change-event in case the user deleted the file and created another file with the same name under the same parent. This approach has also been applied in other works, such as [Li+12a; Li+12b; RC01]. In case the replacement is done, we remove the corresponding create-operation _o j_ with _opt ype_ ( _o j_ ) _= create_ from _O_ . The reason for this decision is that many end-user applications, such as Word processors, apply a “safe” file replacement strategy when updating a file’s content. The application _doesn’t_ change the file by opening it, writing bytes and closing it again (this approach would have preserved the file’s ID). Instead, it saves the new version of the file to a temporary location, followed by deleting the original file, followed by moving the temporary file to the original location. _Semantically_ , this operation reflects an _Edit_ operation, therefore we decide to replace _Delete_ and _Create_ of a file with _Edit_ in the update tree. We think that it is reasonable to assume that, statistically, users edit files more often than deliberately deleting a file and creating a new one with the exact same name, expecting that the synchronizer honors the latter intention verbatim. The incorrect replacement would also only happen during long periods in which no synchronization takes place. 

5. Create dir nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= create ∧ t ype_ ( _oi_ ) _= dir_ with the _Create_ change-event. 

6. Create file nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= create ∧ t ype_ ( _oi_ ) _= f ile_ with the _Create_ change-event. 

_4.2. STATE-BASED UPDATE DETECTION_ 

53 

7. Create file nodes for each _oi ∈ O_ where _opt ype_ ( _oi_ ) _= edit ∧t ype_ ( _oi_ ) _= f ile_ with the _Edit_ changeevent. 

8. Create missing and update existing intermediate nodes for all _unchanged_ files and dirs, by iterating over all objects in the database snapshot. This step serves several purposes: 

   - a) It makes sure to update all those _intermediate_ nodes created during the previous steps, which lack important information, such as _idb_ , the replica-specific _ID_ or the _lastmodi f ied_ metadata, because this data wasn’t available for them in the corresponding operation used during their creation. 

   - b) It creates missing nodes without any change-event. 

The update tree generation procedure generates a _updatetree X_ and a _updatetreeY_ object. 

**Axiom 2.** The number of change-events in each _updatetree_ structure is finite. The change-events are created based on _O_ , where _k = |O|_ is finite, see lemma 1 on page 49. As described above, some _delete_ and _create_ operations in _O_ may be consolidated to an _Edit_ change-event. Whenever such a replacement happens, the number of operations is _decreased_ by 1, making it impossible that the number of changeevents reach infinity. 

#### **4.2.5 Operation consolidation** 

Operations computed from state are affected by _consolidation_ , which refers to the elimination of operations that take place inbetween taking snapshots. Consider taking a snapshot at time _t_ 1. Then a user creates a new file, then moves it to a different path and then deletes it. If we then take another snapshot at time _t_ 2, the computed set _O_ between the two snapshots will not contain any of these operations, because the file’s ID is not known in either snapshot. This observation was also made by [RC01] as _simplifying laws_ and their follow-up work [Csi16] which refers to this as _minimal sequence or set of commands_ . The authors considered file systems without _move_ operation support _._ For _F_ , which does support _move_ operations, we define the concept _operation consolidation_ where two file system operations (as listed in table 3.6) are consolidated to zero or one operation. We use<sup>_∼_</sup> _=_ as notation for a consolidation, such that the left side of<sup>_∼_</sup> _=_ indicates the operations done by the user and the right side indicates how such two operations would appear in _O_ . The following list shows all consolidation rules, which we built by examining every possible combination of two operations: 

1. _move_ ( _i_ , _u_ , _v_ 1, _n_ 1) _+ move_ ( _i_ , _v_ 1, _v_ 2, _n_ 2)<sup>_∼_</sup> _= move_ ( _i_ , _u_ , _v_ 2, _n_ 2) 

2. _createfile_ ( _i_ , _p_ , _n_ ) _+edit_ ( _i_ , _op_ )<sup>_∼_</sup> _= createfile_<sup>_′_</sup> ( _i_ , _p_ , _n_ , _c_ ) (with _lastmodi f ied_ ( _snapshot_ , _i_ ) corresponding to the timestamp _after_ the _edit_ ( _i_ , _op_ ) operation took place) 

3. _create_ ( _i_ , _p_ , _n_ 1) _+ move_ ( _i_ , _p_ , _v_ , _n_ 2)<sup>_∼_</sup> _= create_ ( _i_ , _v_ , _n_ 2) 

4. _edit_ ( _i_ , _op_ 1) _+edit_ ( _i_ , _op_ 2)<sup>_∼_</sup> _= edit_<sup>_′_</sup> ( _i_ ) (with _lastmodi f ied_ ( _snapshot_ , _i_ ) corresponding to the timestamp _after_ the second _edit_ ( _i_ , _op_ ) operation took place) 

5. _create_ ( _i_ , _p_ , _n_ ) _+ delete_ ( _i_ , _p_ )<sup>_∼_</sup> _=_ [] 

6. _edit_ ( _i_ , _op_ ) _+ deletefile_ ( _i_ , _p_ )<sup>_∼_</sup> _= deletefile_ ( _i_ , _p_ ) 

7. _move_ ( _i_ , _u_ , _v_ , _n_ ) _+ delete_ ( _i_ , _v_ )<sup>_∼_</sup> _= delete_ ( _i_ , _u_ ) 

with _create = createfile ∨ createdir_ , _delete = deletefile ∨ deletedir_ . _create f ile_<sup>_′_</sup> is a slightly modified variant of _create f ile_ which also indicates the file’s content, while _edit_<sup>_′_</sup> is a variant of _edit_ that does not indicate the exact operation, as it is not necessary to know the exact content during update detection. 

The consolidation rules can be applied iteratively to an operation input sequence, by first examining all operation pairs in _O_ for matching rules 1-4 (until no more of these rules match), followed applying rules 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

54 

5-7. For example, the sequence _create f ile_ (1,0,<sup>_′_</sup> _test_<sup>_′_</sup> ) _+edit_ (1) _+move_ (1,0,0,<sup>_′_</sup> _test_ 2<sup>_′_</sup> ) _+delete f ile_ (1,0) can be consolidated to 

_∼=create f ile_<sup>_′_</sup> (1,0,<sup>_′_</sup> _test_<sup>_′_</sup> , _c_ ) _+ move_ (1,0,0,<sup>_′_</sup> _test_ 2<sup>_′_</sup> ) _+ delete f ile_ (1,0) _∼=create f ile_<sup>_′_</sup> (1,0,<sup>_′_</sup> _test_ 2<sup>_′_</sup> , _c_ ) _+ delete f ile_ (1,0) _∼=_ [] 

using consolidation rules 2, 3 and 5. 

#### **4.2.6 Side effects of operation consolidation** 

The advantage of operation consolidation is that the operation count is reduced, which improves processing and transmission time and simplifies conflict resolution. However, there are two side effects which we introduce informally in the subsequent subsections. Some of the related works, such as [LKT05; Li+12a], fail to mention these side effects, or handle them incorrectly. For simplicity we restrict the scenario to a _uni_ -directional synchronization, where two replicas _X_ , _Y_ are initially equal, the user modifies only replica _X_ and the computed operations _O_ are then applied to _Y_ , see figure 4.2. 

##### **4.2.6.1 Lack of serialization order** 

It is easy to see that _compute_ops()_ does not provide the _serialization order_ of the computed operations. It is not straightforward to determine the serialization of the _create_ , _delete_ , _move_ and _edit_ operations when the goal is to apply them to the other replica, because the synchronization algorithm may not violate the operation’s invariants. Consider the following examples: 

1. User deletes file “x“ and moves “a” to “x”: _deletefile_ operation has to be applied before the _move_ operation 

2. User moves file “a” to “b” and creates another file at “a”: _move_ has to be applied before _createfile_ 

There are several other examples which are discussed in subsection 4.2.7.1, which also explains how to formally determine these reordering dependencies. 

##### **4.2.6.2 Cycles from missing move operations** 

Consolidation rule (1) can lead to _cycles_ when considering the serialization order. Consider the example of two files named “a” and “b” whose names are _swapped_ by the user. This can only be done by performing _three_ move operations, e.g. _move(’a’, ’temp’)_ , _move(’b’, ’a’)_ , _move(’temp’, ’b’)_ . _compute_ops()_ will detect only _two_ move operations, _move(’a’, ’b’)_ and _move(’b’, ’a’)_ . Synchronizing these operations is not straightforward, because the first one has to precede the second one, while the second one also has to precede the first one. 

#### **4.2.7 Addressing side effects in a synchronizer** 

The following subsections address the issue of finding a valid operation sort order. Parts of this section are also found in [She19]. We explain how to find order dependency rules, detect _cycles_ and how to break them. 

As noted above and explained in figure 4.2 we assume _uni_ -directional synchronization. In chapter 6 (section 6.3.2) we will address the specifics of _bi_ -directional synchronization for which there is a chance for _conflicts_ between concurrent operations. We will show that our approach to address the side effects of operation consolidation still works for bi-directional synchronization, as long as there are no such conflicts (but, at most, _pseudo_ -conflicts). 

_4.2. STATE-BASED UPDATE DETECTION_ 

55 



Figure 4.2: State-based synchronization using computed operations 

Illustration of a _uni_ -directional synchronization from replica _X_ to _Y_ . Both replicas are initially equal (e.g. empty). The file synchronizer maintains a `db` snapshot of _X_ and _Y_ . Whenever replica _X_ was modified by the user, e.g. by _create_ or _move_ operations, these are detected by taking a current `snapshot` and calling _compute_ops(_ _`db` ,_ _`snapshot` )_ . The detected operations are then applied to replica _Y_ , after which both replicas are equal again. Afterwards the file synchronizer updates `db` to be able to detect only modifications made by the user to _X_ from this point forward. 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

56 



<!-- Start of picture text -->
Create 6, 8 Move Delete<br>7 4 2 3 5 1<br>Create Move Create Delete Create Move<br>(a) Create (b) Move (c) Delete<br><!-- End of picture text -->

Figure 4.3: Operation order dependencies 

##### **4.2.7.1 Operation sorting** 

We build an algorithm _O_<sup>¯</sup> _= sor t_  operations_ ( _O_ ) which turns an unordered set _O_ to an ordered list _O_<sup>¯</sup> . This requires an analysis of the operation preconditions because not all operations are commutative. Let _OT_ be the list of considered operation types. For any two types _t A_ , _tB ∈ OT_ we instantiate the respective operations _o A_ , _oB_ , detected on replica _X_ . We choose the parameters ( _i_ , _p_ , _u_ , _v_ , _n_ for _F_ ’s operations) such that applying the sequence ( _o A_ , _oB_ ) to replica _Y_ is feasible, but applying ( _oB_ , _o A_ ) would fail, because a precondition of one of the two operations is violated<sup>7</sup> . We end up with a list of _order dependencies_ , where each order dependency contains _t A_ , _tB_ (in a specific order) and the violated operation precondition(s). Finally, we examine whether cycles can be built from the _order dependencies_ . 

For _F_ we choose _OT =_ � _createfile_<sup>_′_</sup> , _createdir_ , _move_ , _edit_<sup>_′_</sup> , _delete_<sup>_′_�</sup> . The operation _delete_<sup>_′_</sup> summarizes _deletefile_ and _deletedir_ and _recursively_ deletes a directory’s children. Figure 4.3 shows an overview of the eight order dependencies we found for the operation types in _OT_ . The arrows are denoted with a dependency number explained below: 

1. _delete_ before _move_ , e.g. user deletes an object at path “x” and moves another object “a” to “x” 

2. _move_ before _create_ , e.g. user moves an object “a“ to “b“ and creates another object at “a” 

3. _move_ before _delete_ , e.g. user moves object “X/y“ outside of directory “X“ (e.g. to “z“) and then deletes “X“ 

4. _create_ before _move_ , e.g. user creates directory “X“ and moves object “y“ into “X“ 

5. _delete_ before _create_ , e.g. user deletes object “x“ and then creates a new object at “x“ 

6. _move_ before _move_ (occupation), e.g. user moves file “a“ to “temp“ and then moves file “b“ to “a“ 

7. _create_ before _create_ , e.g. user creates directory “X“ and then creates an object inside it 

8. _move_ before _move_ (parent-child flip), e.g. user moves directory “A/B“ to “C“, then moves directory “A“ to “C/A“ (parent-child relationships are now flipped) 

We found these ordering rules by analyzing the pre-conditions of the file system operation from table 3.6 on page 44: 

- _createdir(i, p, n)_ , _createfile(i, p, n)_ : 

   - _ancestor_ ( _iroot_ , _p_ ) _∧ t ype_ ( _p_ ) _= dir_ , in other words, the parent directory has to exist: rule _create before create_ (7) 

   - _id_ ( _p_ , _name_ ) _= error_ , that is, _name_ in _p_ has to be free: rules _move before create_ (2) and _delete before create_ (5) 

- _move(i, u, v, n)_ : 

   - _i ∈ list_ ( _u_ ), i.e., object has to exist: rule _move before delete of parent dir_ (3) 

> 7For instance, when creating a directory and a file inside it, the order cannot be flipped. That is, ( _o A_ , _oB_ ) is feasible with _o A = createdir_ (1, _iroot_ ,<sup>_′_</sup> _dir_<sup>_′_</sup> ) and _oB = create f ile_ (2,1,<sup>_′_</sup> _f ile_<sup>_′_</sup> ), but ( _oB_ , _o A_ ) would fail, because precondition _t ype_ (1) _= dir_ of _oB_ would be violated. 

_4.2. STATE-BASED UPDATE DETECTION_ 

57 



<!-- Start of picture text -->
6 2 3<br>Move Move Move Create Move Delete<br>6, 8 4 1<br>(a) Pure move cycle (b) Move create cycle (c) Move delete cycle<br>Move Move<br>4 3 1 8<br>Create Delete Delete Move<br>5 3<br>(d) Move delete create cycle (e) Move move delete cycle<br><!-- End of picture text -->

Figure 4.4: Operation cycles 

Graphical illustration of different types of operation cycles that result from chaining order dependency rules. Subfigures a-c show _minimal_ cycles that involve as few rules as possible. For subfigure a we refer to appendix section A.3 that proves that cycles that exclusively consist of move operations connected only by rule (8) are impossible. Subfigures d-e show examples for more elaborate cycles. 

- _t ype_ ( _v_ ) _= dir_ , i.e., destination parent directory must exist: rule _create before move_ (4) 

- _id_ ( _v_ , _n_ ) _= error_ , i.e., destination name has to be free: rules _delete before move_ (1), _move before move occupation_ (6) 

- _¬ancestor_ ( _i_ , _v_ ), i.e., destination parent directory may not be below the destination itself, rule _move before move parent-child flip_ (8) 

The analysis of _deletefile_ and _deletedir_ do not yield any pre-conditions that lead to additional ordering rules. We note that the pre-condition _list_ ( _i_ ) _=_ {} of _deletedir_ is not an issue in practice, because the implementation, when given the task to delete a directory, will automatically traverse over the sub-tree of all child objects and delete all objects in a depth-first _postorder_ order. In general, two _delete_ operations cannot be dependent on each other, because either the two paths of the delete operations are _independent_ of one another (i.e., not in a parent-child relationship, in which case the delete order is also independent), or they are hierarchically dependent on each other, in which case our reconciliation algorithm will only create _one_ delete operation for the highest node. See section 6.3.1 for more details. We note that due to operation consolidation, _edit’_ can only co-exist with a _move_ operation affecting the same object. Because these two operations are commutative, _edit’_ is not part of any order dependencies. 

We implemented `fix_op_before_op()` functions to detect and reorder incorrectly ordered operations, see algorithms in appendix A.4 on page 205. Each operation is given the unsorted set _O_ ( `ops` ) and the (partially) sorted list _O_<sup>¯</sup> ( `sorted_ops` ), as well as the `db` and `snapshot` snapshots. The functions then perform an in-place reordering of _O_<sup>¯</sup> . 

##### **4.2.7.2 Detecting cycles** 

To detect cycles we connect the order dependencies found above, see figure 4.4. As is easy see it’s impossible to build cycles using only _delete_ and _create_ operations. Cycles _always_ include at least one _move_ operation. It is possible to find _multiple_ cycles. The length of each cycle (as well as the total number of cycles) is limited by _k = |O|_ , see lemma 1 on page 49. 

Algorithm 2 shows how a _total_ order of operations is computed using the eight `fix_xyz()` functions presented in appendix A.4 on page 205, where each function only establishes a _partial_ order. 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

58 

- 1 Input : ops ( unsorted **set** of operations , O) , db, snapshot 

- 2 Output : sorted_ops , **l i s t** of **sorted** operations 

- 3 Global variables : has_order_changed , reorderings 

- 4 

- 5 sorted_ops = **l i s t** (ops) 

- 6 complete_cycles = [ ] 

- 7 reorderings = [ ] _# type : List [ Tuple [ Operation , Operation ] ]_ 

- 8 **while** True : 

- 9 has_order_changed = False 

- 10 _# All functions perform in−place reordering . I f the order was changed , they set variable has_order_changed to True and add the partial order to ’ reorderings ’ ( unless ’ reorderings ’ already contains i t )_ 

- 11 _# Note that the call order of the fix methods does NOT matter_ 12 fix_delete_before_move (ops , sorted_ops , db, snapshot ) 

- 13 fix_move_before_create (ops , sorted_ops , db, snapshot ) 

- 14 fix_move_before_delete (ops , sorted_ops , db, snapshot ) 

- 15 fix_create_before_move (ops , sorted_ops , db, snapshot ) 

- 16 fix_delete_before_create (ops , sorted_ops , db, snapshot ) 

- 17 fix_move_before_move_occupied (ops , sorted_ops , db, snapshot ) 

- 18 fix_create_before_create (ops , sorted_ops , db, snapshot ) 

- 19 fix_move_before_move_hierarchy_flip (ops , sorted_ops , db, snapshot ) 

- 20 **i f not** has_order_changed : 

- 21 **break** 

- 22 complete_cycles = find_complete_cycles ( reorderings ) 

- 23 **i f len** ( complete_cycles ) > 0: 

- 24 **break** 

- 25 

- 26 **i f len** ( complete_cycles ) > 0: 

- 27 resolution_operation = break_cycle ( complete_cycles [ 0 ] ) 

- 28 **return** [ resolution_operation ] 

- 29 **else** : 

- 30 **return** sorted_ops 

**Algorithmus 2 :** Pseudo-code for `sort_operations()` 

The function `find_complete_cycles()` used in algorithm 2 analyzes the `reorderings` collected so far and attempts to find complete (closed) cycles by chaining the partial reorderings. The mundane details of the implementation of this function are omitted. A developer needs to ensure that cycles are found, even if...: 

- ... the order in which reorderings are found do not follow the order of the operations in the cycle. For instance, if a cycle consists of the operations (A, B, C, D), that cycle still has to be found even if the partial reorderings were found in the order (B,C), (D,A), (A,B), (C,D). 

- ... the cycle is _hidden within a chain_ , in other words, a cycle can be formed not by just testing whether the _end_ of an just-extended chain coincides with the _beginning_ of that chain, but it might also coincide with any of the inner nodes of the chain. As an example, the reorderings might be found and processed in the following order: (B,A), (A,C), (C,A). Simply connecting these orderings produces a chain, but also an inner cycle (A,C). 

The termination property of algorithm 2 is proven in the following theorem. 

**Theorem 1.** _The_ _`sort_operations()` algorithm terminates._ 

_4.3. HYBRID UPDATE DETECTION_ 

59 

_Proof._ by contradiction: To not terminate, the algorithm would have to be caught in the `while` loop indefinitely. However, this cannot happen, because after a finite number of iterations, the loop is always left (line 21, 24). 

At line 20 there are two cases that can hold: (1) one or more cycles exists (irrespective of whether our algorithm already discovered them), or (2) no cycles exist at all (thus our algorithm cannot possibly detect one). 

We start with (1). If there is a cycle, then at line 20 `order_changed` must be _True_ in _every_ loop iteration, because `sorted_operations` is a _list_ with start and end, thus, an operation located at the beginning of the list will always be moved to the end of the list by one of the `fix` functions. In this case, a cycle is either detected in line 22 (because our algorithm discovered all its `reorderings` ), in which case we exit the while loop (line 24), or the cycle has not been discovered yet and a new loop iteration is started. Because each cycle has a _finite_ length of at most _k_ operations, the cycle must also be discovered within _k_ loop iterations, after which the while loop is exited (line 24). 

When considering situation (2), then there must be a total order of operations that is executable. If there are _k_ non-cyclic operations, then after at most _k_ iterations, none of the `fix_op_before_op()` functions will rearrange any operations any more (because they are already in a satisfactory order). Then `order_changed` will be _False_ and the loop will be exited (line 21). 

##### **4.2.7.3 Breaking cycles** 

To break a cycle _C_ we need to find a suitable operation _O_ in _C_ , s.t. _renaming_ the object targeted by _O_ to a _temporary_ name will break _C_ and convert it into a chain. By closely analyzing the different types of cycles from figure 4.4, we find that for any cycle found in replica _X_ there must always be at least one operation _oX_ (with _id_ ( _oX_ ) _= i_ ) which frees a location (i.e., a name in a specific directory) that is used by a follow-up operation _o_<sup>_′_</sup> _X_<sup>.</sup><sup>_oX_must either be a</sup><sup>_move_(dependency rules 6+2) or a delete (dependency</sup> rules 1+5) operation. Instead of executing _oX_ , `break_cycle()` generates a different _move_ operation _rY_ that breaks the cycle. _rY_ renames _i_ by appending a unique suffix to its name. We execute _rY_ on _Y_ and the database snapshot and then restart the synchronization. This way, _rY_ ’s effect is not detected after the restart, but _oX_ and _o_<sup>_′_</sup> _X_<sup>are still detected because we did not modify</sup><sup>_X_8.However, the cycle is now</sup> broken, because the order dependency (6, 2, 1, or 5) no longer applies. 

An example can be found in figure 4.5. We refer to section 6.3.2 for additional examples and further implementation details. 

### **4.3 Hybrid update detection** 

A background synchronizer needs to _continuously_ synchronize two replicas. Using only the state-based approach would require to _regularly_ sample each replica’s current state. On large file systems (with a high number of files and directories), computing and transferring the state between replicas is expensive. To avoid high system load and large delays, we propose a _hybrid_ approach for those file systems that offer log-based update detection. 

The synchronizer determines the replica’s state information _once_ , when creating its snapshot for the first time. Subsequently, operations are retrieved from the log (via the log-based update detection) which are applied to the cached state, producing the current state. The reconciliation then uses operations computed from the state difference between the _database_ snapshot (that contains the state of the file system at the last successful synchronization) and the _cached_ , current snapshot. A similar approach was proposed by [Li+12a]. 

#### **4.3.1 Advantages** 

This hybrid approach has several advantages: 

> 8If _oX_ is a _move_ operation, changing the name of _i_ in the database snapshot to a _unique_ name will still find _i_ as moved in _X_ . If _oX_ is a _delete_ operation then it will still be deleted in _X_ 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

60 



<!-- Start of picture text -->
Y X Y X<br>a d a d a-temp d a d<br>b a b a<br>1<br>deletedir('a') move('d', 'a') deletedir('a-temp') move('d', 'a')<br>3 2 3 2<br>move('a/b', 'd/a') createdir('d') move('a-temp/b', 'd/a') createdir('d')<br>4 4<br>(a) Cycle (b) Broken cycle<br><!-- End of picture text -->

Figure 4.5: Example for breaking operation cycle 

Subfigure _a_ shows the directory structure on the unchanged replica _Y_ , the changed state on replica _X_ and the four operations computed by _compute_ops()_ on replica _X_ . The _actual_ operation sequence on _X_ might have been something like _move(’a’, ’a-temp’)_ , _move(’d’, ’a’)_ , _createdir(’d’)_ , _move(’a-temp/b’, ’d/a’)_ , _deletedir(’a-temp’)_ . The computed operations cannot be applied to replica _Y_ because they form a cycle. Let _oX = deletedir_ (<sup>_′_</sup> _a_<sup>_′_</sup> ) then we can instead apply _rY = move_ (<sup>_′_</sup> _a_<sup>_′_</sup> ,<sup>_′_</sup> _a − temp_<sup>_′_</sup> ) on _Y_ and update the `db` snapshot. The cycle is converted to a chain as shown in subfigure _b_ . 

- In case a file system supports any online or offline log-based update detection APIs, these can be leveraged to reduce its load. Since the operation log is applied to a cached snapshot to produce the current snapshot, this effectively converts operation logs to state. In case the log-based update detection becomes (temporarily) unavailable, our synchronizer can always fall back to sampling the file system to build the current state. 

- Only a state-based variant of the reconciliation algorithm needs to be implemented, because the update detector can always provide the current state. It is not necessary to implement another variant of the reconciliation algorithm that processes operation logs. Implementing the latter would require significant efforts, using a different algorithm altogether. 

#### **4.3.2 Implementation algorithm** 

The simplified algorithm that detects updates for a file system offering operation logs is shown in algorithm 3. Function `set_up_subscription()` uses the log-based, proprietary update detection API of the file system to subscribe to operation logs. From now on, updates are collected in the background and can be retrieved by calling `get_new_operations()` , which blocks the caller until new operations are available. `apply(ops, snapshot)` takes the operation log and applies it to the provided, cached snapshot, updating `snapshot` internally. The details of this function are omitted, because they depend on the exact log data structure. Applying the log may fail if the log is incomplete or faulty. If applying the logs was successful, the listener (e.g. the reconciliation component) is given the operation log ( `ops` ) as well as the current `snapshot` . Otherwise, operations are computed from state, using `compute_ops()` from section 4.2.3. 

_4.3. HYBRID UPDATE DETECTION_ 

61 

Input : root_path to observe , listener whose on_new_operations () method **is** called when updates are detected 

set_up_subscription ( root_path ) 

snapshot = create_state_snapshot ( root_path ) **while** True : 

ops = get_new_operations () 

success = **apply** (ops , snapshot ) 

**i f** success : 

listener . on_new_operations (ops , snapshot ) 

**else** : 

_# f a l l back to computing operations from state ( slow )_ current_snapshot = create_state_snapshot ( root_path ) ops = compute_ops( snapshot , current_snapshot ) snapshot = current_snapshot 

listener . on_new_operations (ops , snapshot ) 

**Algorithmus 3 :** Pseudo-code for hybrid update detection 

#### **4.3.3 Caveats and workarounds** 

While implementing the hybrid update detector component we encountered numerous issues. One general issue is that snapshot creation is affected by race conditions. The function `create_state_snapshot()` may take several seconds up to minutes to complete. During this time period the user may execute concurrent operations. The effect of each of these operations may or may not be contained in the resulting snapshot. We implemented logic in `create_state_snapshot()` to internally apply concurrently detected operations to the snapshot after it was taken. If applying the operations should fail, we restart the snapshot generation. 

There are also numerous file system specific issues discussed in the following subsections. 

##### **4.3.3.1 Windows** 

On Windows the `ReadDirectoryChangesW` API fills a size-limited _event buffer_ with file system events. The information of each event is limited to a bitmask that indicates the operation type (create, delete, ...) and the affected object path. Most notably, information such as the object’s ID, type and lastmodified meta-datum are _not_ provided. This results in various caveats: 

1. Missing events: when many events occur in a short time period and the event buffer limit is exceeded, events are missed. Because the buffer size cannot be increased indefinitely, the workaround is to fall back to the state-based approach in case a buffer overflow is detected. 

2. Bloated rename/move events: when an object is renamed, this causes `ReadDirectoryChangesW` to produce _two_ file system events, one that indicates the old, one that indicates the new path. When an object is moved (i.e., its parent directory changes) this causes a _delete_ followed by a _create_ event. Without further knowledge about object IDs it is impossible to discern actual _move_ operations from _delete_ + _create_ operations. 

3. Race conditions when retrieving IDs and other meta-data: because events lack IDs and other meta-data our implementation needs to use the file system’s query APIs<sup>9</sup> to retrieve this data after the fact. This may fail because of the time that passes between the operation’s execution and 

> 9We first open the object’s handle using `CreateFileW(path)` , followed by retrieving information via `GetFileInformationByHandle()` , see `https://docs.microsoft.com/en-us/windows/desktop/api/fileapi/ nf-fileapi-getfileinformationbyhandle` , retrieved July 21, 2019. 

_CHAPTER 4. UPDATE DETECTION FOR FILE SYSTEMS_ 

62 

our implementation calling the query API (even though this happens merely a few milliseconds later). For instance, if the first event indicates the creation of an object at path “x” and the second event indicates that “x” was renamed to “y”, querying the information for “x” (when processing the first event) will fail, because “x” no longer exists. Even worse, if the real operation sequence was _createfile(“x”)_ + _move(“x”, “y”)_ + _createfile(“x”)_ , then querying “x” will succeed, but provide wrong values. We handle such cases by performing extensive plausibility checks that make sure that IDs returned from the query operation, in conjunction with the reported operation type and path, logically match with IDs from the cached snapshot. If the checks fail, we fall back to computing operations from state. To avoid that the event buffer overflows due to spending too much time with applying events, we use a multi-threaded approach. Thread #1 retrieves data from the event buffer and uses the query APIs to complete the information as soon as possible. Thread #1 then hands the events (enriched with query API information) over to thread #2 which applies them to the cached snapshot. 

4. Missing directory structure information: when the user moves an existing directory with subobjects from outside the synchronized directory _d_ into _d_ , then only a _create_ event of the new path is reported. Even after we retrieve the meta-data for that path itself, and learn that a new _directory_ was created, we still needed to implement extra logic to distinguish whether the user truly created an (empty) directory (and possibly also created other objects inside it soon after), or whether the user moved an existing, non-empty directory into _d_ . 

##### **4.3.3.2 macOS** 

On macOS the _FSEvents_ API allows to specify a callback function which FSEvents calls with a list of paths and corresponding event type bitmask that indicates which change(s) occurred. The bitmask indicates whether the object is a file or directory, and the type of operation (create, move/rename, modify, delete). Just as for Windows’ `ReadDirectoryChangesW` API, the object’s ID, type and lastmodified meta-datum are _not_ provided. While FSEvents does not drop events (because its buffers are not limited), we still need to handle several issues: 

1. Bloated rename/move events: see Windows. Note: when an object’s parent directory changes, the bitmask of each event indicates _move_ correctly (not _delete_ + _create_ as done by Windows) 

2. Race conditions when retrieving IDs and other meta-data: see Windows 

3. Missing directory structure information: see Windows 

4. Event aliasing: FSEvents may _coalesce_ multiple operations to a single event in case they all affect the same path within a short time period. There is no documentation how exactly this coalescence is done. For instance, the callback might report two events where the first event is for path “x” and has the _create_ , _modify_ , _delete_ and _rename_ bits set, and the second event is for file “y”, with only the _rename_ bit set. An event may even indicate that it affects both a file and a directory at the same time. Due to this aliasing it is impossible to deduce the exact operations or their order, as there are several valid explanations for these two events. As an example, the user could have performed the following operation sequences: 

   - a) _delete(“x”)_ + _create(“x”)_ + _edit(“x”, <new content>)_ + _move(“x”, “y”)_ 

   - b) _move(“x”, “y”)_ + _create(“x”)_ + _edit(“x”, <new content>)_ + _delete(“x”)_ 

Our solution for issue (4) is to no longer rely on the event type bitmask and instead execute the query API on every reported path, deducing operations from the resulting data. For the above example, we would query paths “x” and “y” and look up corresponding objects in the cached snapshot, using the IDs delivered by the query API calls. query(“x”) would result in an error, and query(“y”) would either yield the ID known in the snapshot at path “x”, indicating explanation (b), or would yield an ID unknown in the snapshot, which would indicate explanation (a). 

_4.4. CONCLUSION_ 

63 

##### **4.3.3.3 BSCW** 

BSCW 7 offers a proprietary REST API _sync_events_ for offline update detection. It delivers operations, including their type (link, unlink, delete, undelete, create, rename, move), affected object and all its meta-data, including its ID. There are still several caveats to consider: 

1. Missing events: to save space the server admin may purge the event history. In this case a special purge-event is inserted, which indicates this fact and allows our implementation to fall back to computing operations from state. 

2. Irrelevant events: because BSCW allows an object to be linked into _multiple_ parent directories, _sync_events_ reports events that are irrelevant for our synchronized directory. For instance, if object _o_ is both linked to the root _ds_ of the synchronized directory, but also to a directory _do_ outside of _ds_ , then _sync_events_ will report irrelevant events (which our implementation needs to discard), such as an _unlink_ operation of _o_ from _do_ , or a _move_ operation that moves _o_ to a different parent directory _do_<sup>_′_under</sup><sup>_do_, or a</sup><sup>_link_operation of</sup><sup>_o_into yet another directory</sup><sup>_d_</sup> _o_<sup>_′′_outside of</sup><sup>_ds_.</sup> 

3. Name aliasing: BSCW does not store historic information of an object’s _name_ , but only the most recent name. If, inbetween two _sync_event_ calls, a user performs the operations _create(“x”)_ + _rename(“x”, “y”)_ , _sync_events_ would deliver a _create_ event for “y” and a _rename_ event that indicates that “y” was renamed to “y”, which is superfluous and needs to be discarded. 

4. Missing directory structure information: see Windows 

#### **4.3.4 Summary** 

We found our hybrid update detection approach to be feasible and efficient, but challenging to implement. Operation log data provided by file systems needs to be treated carefully, as it may lack important information (such as object IDs) or be affected by aliasing. We can generalize that an update detector of _any_ ID-based file synchronizer that supports our examined file systems and relies _only on operation logs_ would need to implement _hybrid_ approach anyway, using a cached snapshot, because the operation log APIs do not provide sufficient detail on their own. 

### **4.4 Conclusion** 

The goal of this chapter was to find an update detection approach that is both efficient and provides not only the current state, but also a log of operations. The hybrid update detector we presented solves this problem, by combining state-based with log-based methods. We examined the foundation, statebased update detection, in detail for our file system model _F_ , which supports the _move_ operation. We identified the concept of _operation consolidation_ , which complicates detecting a valid operation order. Our analysis of the side effects of this consolidation yields not only issues with operation _commutativity_ addressed by RQ2, but also the possibility of _cycles_ . We presented a sorting algorithm that is based on operation ordering rules which we found by analyzing the pre- and postconditions of the operations of our model _F_ . It solves RQ2 and the problem of finding cycles by iteratively applying the ordering rules to a partially sorted list, until no more rules are apply, or the applied rules start to repeat, in which case a cycle was identified. 

65 

## **Chapter 5** 

# **Consistency and conflicts for file synchronizers** 

The goal of a bi-directional file synchronizer is to achieve eventual consistency between two replicas. We first mentioned the concept of consistency on a _technical_ level in section 2.1.1. Achieving consistency between replicas means that _divergences_ of each replica, which exist due to operations that were applied to replicas in isolation, are eliminated by applying them from one replica to the other one. Once the consistency algorithm has finished, the state of both replicas should converge _and_ incorporate the previously detected divergences<sup>1</sup> . To achieve this, the synchronizer has to propagate non-conflicting changes, and detect and resolve conflicting ones. The most challenging aspect of consistency is conflict handling. Conflicts are combinations of operations that are discovered to be incompatible with each other by the synchronizer during synchronization, even though they were _successfully_ executed on each replica in isolation. On a technical level an operation _oX_ detected in replica _X_ is conflicting with operation _oY_ detected in replica _Y_ (and thus cannot be applied to _Y_ by the synchronizer) if the preconditions of _oX_ no longer hold for _Y_ ’s new state that resulted from applying _oY_ to _Y_ . A typical example is when the content of a file is updated in replica _X_ while that file is deleted in replica _Y_ . 

This chapter elaborates on consistency for file synchronizers. We start with an analysis of conflict resolution approaches of related work in section 5.1. With a few exceptions we find the presented conflict resolution methods to be _arbitrary_ . They are typically not backed up by a _rationale_ , nor do they consider prior work or discuss side effects. Only two works, [TSR15; NS16], present a general set of consistency _properties_ which are reflected in the concrete conflict resolution approaches (see section 5.1.2). To not make the same mistake of presenting arbitrary conflict resolution approaches, we design a four-step framework shown in figure 5.1. This top-down approach starts with an informal definition of consistency properties and iteratively refines it to a set of formal and detailed steps for detecting and resolving concrete conflicts. The first step is the definition of a high-level _consistency philosophy_ in section 5.2, which is a set of consistency properties that define what it means to achieve consistency, based on the consistency properties of [TSR15; NS16] and our own suggestions. Step 2 defines _conflict resolution policies_ presented in section 5.3, which are policies that generally apply for resolving conflicting operations, but are still independent of any concrete conflict definitions. Because many concrete conflicts have common traits, they should also be resolved using a similar approach. We group such similar conflicts to _conflict patterns_ and present them in section 5.4. In section 5.5 we present individual conflicts in detail, focusing on the _NH-MD_ file system. As discussed in section 2.3.1, there are several other file system definitions. We discuss the relationship between the complexity of each file system and the number of conflicts that need to be detected in section 5.6. Since a file system can be affected by more than one conflict at once, which has not been discussed in related academic works, we present how we _iteratively_ resolve conflicts in section 5.7. Finally, we proof the termination properties of our conflict resolution approach in section 5.8. 

1Consequently, a trivial bi-directional synchronizer that always deletes all files and directories in both physical replicas and the database snapshot would only achieve _convergence_ but not consistency. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

66 



Figure 5.1: Conflict handling framework 

### **5.1 Related work analysis** 

The goal of the analysis is to determine how academic file synchronizers or Distributed File Systems (DFS) _resolve_ file system related conflicts (i.e., _detection_ is not our concern, because the mechanisms are very diverse and depend on the file system definition). We look both for concrete approaches for specific conflict types and generic guidelines or policies the concrete approaches are based on. Additional points we examine are the following: 

- Is conflict resolution always automatic, always manual, or a mixture of both? 

- How clear are conflict resolution approaches described? 

- Given a clear description, is there a _rationale_ provided for the specific approach? 

- Do authors compare their approach with other approaches, to defend their rationale? 

- Are any conflict _awareness_ mechanisms being discussed? 

We present the findings of our analysis in section 5.1.1. We keep the analysis on a high level. The description of the resolution of specific conflicts is deferred to subsections of section 5.5, after we described the conflicts themselves in detail. In a few works we found generic consistency properties, which we refer to as “consistency philosophies” which we describe in section 5.1.2 on page 69. 

#### **5.1.1 Conflicts** 

In this analysis we examined the works from section 2.4 as well as the DFSs _Ficus_ and _Coda_ . An overview of the results is shown in table 5.1. 

##### **Resolution mode** 

A first glance at the _Resolution mode_ column reveals a large degree of diversity. While some systems are fully automatic or fully manual, others are hybrid. _Hybrid_ systems get their name either because they don’t mention the mode at all [CJ05], allow users to configure which mode they prefer [UFB10], or 

_5.1. RELATED WORK ANALYSIS_ 

67 

||Type|Resolution mode|Clarity of conflict<br>_resolution_description|Remarks (e.g. introduced<br>winner-criteria orphilosophy)|
|---|---|---|---|---|
|[Guy91;<br>Rei+94] (Ficus)|DFS|Some automatic (call<br>user-made resolver),<br>some manual|Clear for automatic,<br>unclear for manual|1)_No lost update_criterion,<br>applies to concurrent file<br>update + delete. Resolve by<br>move to orphan directory<br>2) Email report provides<br>conflict awareness|
|[Sat+90] [KS91]<br>(Coda)|DFS|Some automatic (call<br>user-made resolver),<br>some manual|Clear for automatic,<br>unclear for manual|1) Repair tool user invokes to<br>manually resolve conflicts|
|[BP98; PV04]|Sync.|Manual|None|Unison implementation offers<br>several options to resolve<br>conflict|
|[Bao+11]|Sync.|Automatic|Only for file content<br>update conflict.<br>Mentions but ignores<br>other conflicts|–|
|[Bjø07]|Sync.|Automatic|None|–|
|[CJ05]|Sync.|Automatic or manual|None|–|
|[Mol+03]|Sync.|Automatic|Clear (formal, via OT<br>transformation<br>functions)|Object unique ID comparison<br>used as criterion to choose a<br>winner|
|[Naj16]|Sync.|Automatic|Clear|–|
|[RC01; Csi16]|Sync.|Manual|None|Discusses conflict encoding<br>into file system vs. special GUI|
|[TSR15]|Sync.|Automatic|Clear|Presents philosophy that<br>conflict resolution<br>approaches (mostly) follow|
|[UFB10]|Sync.|Automatic or manual|Only for file content<br>update conflict.|–|
|[Li+12a;<br>Li+12b]|Sync.|Automatic|Clear|–|
|[LKT05]|Sync.|Some automatic<br>(speculative merging),<br>some manual|None|–|
|[NS16]|Sync.|Automatic|Clear|Presents philosophy that<br>conflict resolution<br>approaches follow|



Table 5.1: Analysis of conflict resolution approaches 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

68 

because the mode depends on the conflict. That is, some conflicts can be resolved automatically and some require manual user intervention. 

##### **Clarity of conflict resolution** 

The _clarity_ column demonstrates that out of the 14 analyzed works five provide a fully clear description, five do not describe conflict resolution at all, and the remaining four only describe it partially. We would have expected works which use _manual_ resolution to describe which resolution _alternatives_ are presented to users for them to choose from. However, the majority of works does not provide any alternatives. Coda mentions a conflict “repair tool”, but it is never illustrated or explained. 

##### **Resolution rationale** 

We found that the majority of works which describe their resolution do _not_ present any _rationale_ to explain their concrete conflict resolution approaches, nor do they compare their approach with related work. There are, however, a few notable exceptions: 

- Ficus mentions the “no lost update rule” which states that if a file was deleted in one replica but modified in the other, the file should be restored to avoid losing the update. However, the resolution of other conflict types is not following any identifyable rules. 

- In [NS16] and [TSR15] the authors define a _consistency philosophy_ , which they follow in their concrete resolution implementation. We present further details in section 5.1.2 on the facing page. The authors make several specific choices, however, not covered by the philosophy, which seem to be _arbitrary_ . For instance, if a specific name is concurrently occupied (via _create_ or _move_ operations) on both replicas, [NS16] decide to resolve the conflict by renaming _both_ objects, while [TSR15] use a different approach for each object type combination (file-file, file-directory, directory-directory) and provide a rationale for only one of these combinations. 

- In [NS16] the authors evaluate the convergence behavior of their own work with those of industrial synchronizers Dropbox, Google Drive and Microsoft OneDrive. In this evaluation the authors briefly discuss advantages of their own conflict resolution approaches, _comparing_ it to the ones applied by industrial tools. For example, the authors note that if an industrial synchronizer recursively merges the sub-objects of two independently created directories (where the directories are located in the same parent directory and using the same name), unmerging is a painful process for the user in case the automatic merge was inappropriate for that specific directory. 

- Sometimes the rationale is dictated by the specifics of the underlying _algorithm_ . In [Naj16] the authors use _CRDT sets_ and _maps_ to model the file system. For those CRDT data types the conflict resolution mechanism for concurrent _add_ or _remove_ operations is limited to “add wins” or “remove wins”. Consequently, update/delete conflicts (e.g. where one user updates a file that the other user deletes) are resolved using one of these choices. 

##### **Winner-criterion** 

While most works define no consistency philosophy or general guidelines, we identified several _winnercriteria_ that can be used to identify the winner of a conflict. These include: 

- Object type, e.g. _directory_ wins over _file_ , see [TSR15] 

- Replica, e.g. let a _specific_ replica’s operation take precedence, or choose a _random_ replica, see [Li+12a; Li+12b] 

- Object ID, e.g. the object with higher ID wins, see [Mol+03] 

- Timestamp, e.g. last writer wins (LWW), see [UFB10] 

- No lost [file content] update, i.e. let file modifications win over deletions, see [Rei+94] 

_5.1. RELATED WORK ANALYSIS_ 

69 

##### **Conflict awareness** 

Most works do not discuss how to make the user aware of conflicts or their resolution. Some DFS such as Ficus or Locus [Wal+83] mention _email reports_ , but provide no specifics of the email’s content, nor whether reports are sent for automatically resolved conflicts. 

##### **Final remarks** 

In [RC01; Csi16] the authors note that when a synchronizer detects a conflict, it can either: 

1. Keep knowledge about the conflict at the site of its discovery, making it accessible via a special user interface of the synchronizer. Only one user is aware of the conflict. Files affected by the conflict remain in their old state (diverging from other replicas) until the user chooses a specific resolution. 

2. Encode the conflict’s _detection_ (and optionally a preliminary _resolution_ ) into the file system, which allows replicas to immediately converge, making _all_ users aware of the conflict. 

The second alternative demonstrates that if a synchronizer claims to _resolve_ a conflict automatically, e.g. by appending a unique suffix to an object’s name in case of a name clash, it has in fact only encoded the conflict’s _detection_ into the file system, using a preliminary resolution. The conflict is only truly resolved once a user performs further actions on the automatically renamed object. 

#### **5.1.2 Consistency philosophies** 

The two works [TSR15] and [NS16] present high-level consistency philosophies which we now review. 

[TSR15] define that the common state of a file system is ”meaningful” to the users _“when the users can still see the effect of their own updates”_ after the synchronization finished. They define the two properties _element preservation_ and _relationship preservation_ . Element preservation means that (1) _“if the user updates any file or directory, the updated elements should also be available in the merging outcome_ ”, and (2) that separate objects are not merged into a single object. Relationship preservation requires that path-related changes made by the user on a replica should be visible after synchronization. The underlying principles of the properties are that not a single update should ever be lost (even in the face of conflicts), and that merging the changes of two replicas should have no _side-effects_ , such as anomalous results where objects unexpectedly disappear. 

While most of these statements intuitively make sense, it is easy to see that the first underlying principle, “no lost updates”, cannot possibly hold for every conflict<sup>2</sup> . The authors even provide a counter example in their work. They define the “state conflict”, which is concerned with deleting an object in one replica and updating it (e.g. the file’s content) in the other one. The authors chose the _update_ operation to take precedence over the _delete_ operation in the merged file system. The _delete_ operation is effectively lost, which contradicts their _relationship preservation_ property and the underlying “no lost updates” principle. 

A slightly different philosophy is defined as a set of _consistency requirements_ in [NS16], where the consistency reflects the limitation that not _all_ updates can be preserved: 

- Causality preservation: causally related operations must be executed in their causal orders, 

- Convergence: after all operations were sent to all replicas, the state of all replicas must be equal, 

- Intention-confined effect: operations applied to replicas by the synchronizer must be based on operations generated by the user, 

> 2We note that in [TSR15] the definition of “update” goes beyond the operation that changes the content of files (which was the narrow definition used in [Rei+94]), but also includes other operation types. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

70 

- Aggressive effect preservation: _“effects of compatible operations should be preserved fully; effects of conflicting operations should be preserved_ _<u>as much as possible”</u>_ . 

These four properties are a variation of the consistency model from Operational Transformation (OT), see _convergence_ , _causality preservation_ , and _intention preservation_ in [SE98]. These OT-based papers are founded on work in the area of distributed systems with seminal works like [Lam78]. We note that _Causality preservation_ applies to _operation_ -based systems, but not _state_ -based systems such as the synchronizer we build in this work, where the exact operation sequence is not available. 

Finally, we note that both [TSR15] and [NS16] defined their consistency philosophies with fully automatic conflict detection and resolution in mind. 

### **5.2 Consistency philosophy** 

In this section we define the consistency philosophy we use in our synchronizer. We cherry-pick properties from the two philosophies presented above, refine them and add additional properties. 

Like for [TSR15] and [NS16] our philosophy is designed for _automatic_ conflict resolution mode. We prefer _automatic_ resolution for two reasons. First, user involvement to keep replicas synchronized becomes _optional_ . Users only need to act and repair the file system if the automatic resolution was inappropriate in that specific case (in the eyes of the user), which allows them to concentrate on their actual work. Second, our algorithm is built such that it requires to first resolve conflicts before non-conflicting operations can be applied (see section 6.1). By automatically resolving conflicts we can immediately propagate the non-conflicting operations, too, which reduces the risk that new conflicts occur for those non-conflicting files and directories. Section 5.3.1 provides further details regarding the automatic vs. manual resolution mode. 

Our consistency philosophy has the following properties: 

- _Convergence_ : after a completed synchronization, the state of all replicas must be equal, 

- _Impact_ : operations applied to replicas by the synchronizer must be based on operations generated by the user, 

- _Intention preservation_ : the synchronizer applies non-conflicting operations from one replica to the other one without modification. For conflicting operations, the synchronizer preserves the intention of both operations as much as possible, by modifying _one_ of the operations. If preservation is not possible the synchronizer discards the operation that required less effort to generate (for the user). At the same time the synchronizer minimizes undesirable, manual clean-up work for the user in case the automatic resolution was inappropriate. The synchronizer should store sufficient information to allow the user to _retroactively_ choose a different resolution option for a conflict, in case she considers the default approach to be inappropriate. 

- _Awareness_ : if the synchronizer applies a non-conflicting operation from replica _X_ to _Y_ , the user of _Y_ must be able to inspect the operation history _on demand_ , to understand which file system changes originate from replica _X_ . If the synchronizer modifies or discards conflicting operations generated by the user, the user must be actively notified (e.g. via a push notification). This improves transparency of the actions done by the synchronizer. 

In comparison to previously presented philosophies such as [NS16] we added two major contributions. 

First, we refined the _intention preservation_ property by (1) specifying that each conflict has a winner and a loser, (2) the synchronizer should prefer to _modify_ the losing operation - and only if that fails, _discard_ the losing operation, (3) the modified/discarded operation is chosen according to the amount of effort put into it by the user (if possible to estimate), and (4) the resolution approach considers the ease of undoing the resolution if the user found that specific resolution to be inappropriate for the specific conflict. 

_5.3. CONFLICT RESOLUTION POLICIES_ 

71 



Figure 5.2: Overview of conflict resolution policies 

Second, we added the _awareness_ property. Awareness, which [DB92] define as _“an understanding of the activities of others, which provides a context for your own activities”_ , is a well-researched topic in CSCW literature. Various classifications (e.g. _workspace_ awareness [GG02]), awareness types (e.g. activity or process awareness [CSS09]), models (e.g. event-based [Pri93] or spatial models [BF93]) have been identified. According to [FPP95] our awareness property asks for _asynchronous_ awareness, which is either tightly coupled (for conflicting operations) or loosely coupled (for non-conflicting operations affecting objects the user of replica _X_ did not work on). 

### **5.3 Conflict resolution policies** 

Conflict resolution policies are a set of rules that refine the conflict-related parts of the above consistency philosophy, specifically the _intention preservation_ property. An overview is shown in figure 5.2. 

#### **5.3.1 Resolution mode: automatic vs. manual** 

##### **5.3.1.1 Choosing an approach** 

The first, most important decision to make is whether to resolve conflicts automatically or manually<sup>3</sup> . As stated in [RC01], choosing between _automatic_ and _manual_ conflict resolution is still an open problem. The advantage of _manual_ resolution is that the user has full control, which may increase her trust in the synchronizer, because resolution actions have to be confirmed. Users also avoid subsequent, unnecessary clean-up work that would have been required if the synchronizer had automatically resolved a conflict inappropriately. The downside is that users may feel annoyed by being requested to act. This particularly affects users who often work offline for extended periods of time, which increases 

> 3We note that a synchronizer can make _different_ choices (automatic vs. manual) regarding the (1) general synchronization, and (2) conflict resolution. For instance, a synchronizer may be configured to synchronize automatically whenever changes are detected, but still enforce manual conflict resolution. This section focuses solely on aspect (2). 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

72 

the chance for conflicts. Additionally, this would increase the risk that new conflicts occur for _nonconflicting_ files in case a synchronizer’s algorithm requires that all conflicts have to be resolved first before non-conflicting operations can be applied. 

The advantages and disadvantages of _automatic_ resolution can be derived from inverting those of _manual_ resolution. Users benefit because they save time and effort in case the resolution was appropriate. However, in cases where the user considers a specific resolution to be inappropriate, the costs incurred for subsequent, manual repair must be manageable. We refer to section 5.4.3 for a concrete example regarding two conflicting _createdir_ operations. 

##### **5.3.1.2 Challenges of automatic resolution** 

The challenge for automatic resolution is to find default settings that are appropriate for the variety of workflows and scenarios in which the synchronizer is used. The following points outline different choices a synchronizer developer must make. 

First, the synchronizer needs to _arbitrate_ , i.e., choose winners and losers. When two operations conflict, there are two basic ways of arbitration: 

1. The synchronizer picks a _winner_ and a _loser_ operation, based on some criterion (see next section). The winner operation is applied as is to the other replica, the loser operation is first modified in some way, depending on the conflict. In the most extreme case, the loser operation is discarded completely. The advantage is that only the loser has to be notified about the conflict, because only her operation was not applied as originally intended. 

2. The synchronizer considers _both_ operations as _losers_ . Both operations require modification. This approach avoids having to choose a winner-criterion, at the expense of preserving neither user’s original intention of the operation. 

For our own implementation we decided to choose a winner and loser operation to maximize intention preservation. 

The second choice is the _configuration_ of automatic resolution, which can be split into _who_ decides how to resolve conflicts, and _how_ is each conflict resolved. 

**_Who_ decides?** In the majority of file synchronizers we found the _developer_ of the synchronizer makes this choice and hard-codes _one_ specific resolution mechanism into the algorithm. We find this approach problematic due to its lack of flexibility and instead suggest that the synchronizer offers _several_ resolution approaches. However, who should be allowed to pick from those options? 

1. Give _administrators_ this choice. Compared to end-users they have a higher technical expertise required to choose resolution approaches on an abstract level without concrete examples. However, they may not be familiar with exact workflows of the users. There might also be _several_ groups of users with different workflows, which further complicates the selection for administrators. 

2. Let _users_ decide. This improves control over the program, but if implemented incorrectly (e.g. showing an overloaded technical dialog such as shown in figure 7.1 on page 130) the choice may overburden non-technical users and allow them to pick options that are often inappropriate. 

We suggest to let _users_ decide. To avoid overburdening them, users should be able to tweak the conflict resolution on a case-by-case basis. Whenever a conflict occurs, the synchronizer resolves it automatically using some pre-configured option and notifies the user. This opens a graphical dialog with several functionalities: 

_5.3. CONFLICT RESOLUTION POLICIES_ 

73 

- It explains the conflict to the user. This includes providing context the user needs to understand and resolve the conflict. For instance, if a file was updated by two users, the synchronizer can open both conflicting files for the user with a single click (or even open a comparison view if the corresponding application offers one). 

- If the user considers the applied resolution as appropriate, she can just confirm that she has seen the conflict. 

- Otherwise she can ask the synchronizer to retroactively apply a different approach, from a set of alternative resolution options. This way the user is not burdened with configuring resolution options for _other_ conflict types she has never seen (but just the one at hand), and is given a concrete example she can understand. The synchronizer can learn from the user’s choices over time, e.g. applying the user’s previous choice the next time a similar conflict occurs. A challenge left as future work is how exactly the learning algorithm works, including how the user can influence the learning process. 

**_How_ are conflicts resolved?** Any resolution approach requires two decision steps. First, the choice of a _winner-criterion_ (see next section) that defines the winner operation (in the presence of a multitude of criteria, which may also be ranked). Second, the specifics of how the synchronizer manipulates/discards the loser operation and executes the winner operation. Because the concrete choices depend on the conflict type we discuss our suggestions in the corresponding conflict sections. 

#### **5.3.2 Criteria for choosing a winner in automatic resolution** 

We collected numerous criteria from our related work analysis and user suggestions, and added our own ideas. The following list explains and criticizes each criterion. 

- _Timestamps_ : the “last writer wins” (LWW, [JT75]) approach is well known in research [SS05] and practice and has also been adopted by [UFB10]. The general idea is that _newer is better_ . We frequently observed that this is not necessarily true in practice and heavily depends on the concrete scenario. Consider an example where a user revises a document on a laptop computer for 3 hours on a train ride, returns home, turns on the desktop PC and fixes a typo in the same document which has not been synchronized yet and thus contains the outdated version. It would be very inappropriate if the synchronizer overwrote the document on all machines with the typo-fixed version, only because it is newer. We advise against using this criterion, because a single timestamp bears no information w.r.t. the work put into the affected operation. There are also many other issues with timestamps. Some file systems may not provide them for all operations<sup>4</sup> . Several types of timestamps may exist (timestamp of when a change was registered on a _client_ vs. _server_ ). Also, wall-clock-based timestamps can be tampered with or may accidentally have lost synchronicity, making it difficult to even decide who is the last writer. 

- _Operation type_ : suppose two conflicting operations have a _different_ type (e.g. _move_ vs. _createfile_ , or _edit_ vs. _delete_ ) then we can choose one to take precedence. The “no lost update” rule of [Rei+94] is an example for this criterion. It is not conclusive if both operation types are equal. As a general framework we propose that operations are ranked by the _amount of work_ that presumably went into it. We propose the following simple order: 

   - _Delete_ operations are ranked lowest - the user only needs to choose a target object and press a button to delete it. 

   - _Move_ operations require selection of source and target object, which may involve typing characters if the object is renamed. Similarly, _createdir_ operations require the selection of the parent directory and choosing the directory’s name. 

- 4For instance, the time when a file was renamed or moved is not available on many file system implementations. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

74 

   - _Edit_ and _createfile_ operations come last (and are ranked equally) because they involve changing content, which is presumably a lot more work. 

- _Replica/user_ : the synchronizer selects the operation of a specific replica or user as winner. This is a suitable fallback criterion if other criteria are inconclusive or make no sense. We found that a good choice is to prefer the operation performed on the _server_ replica for synchronizers deployed in a client-server star-topology setup. The server’s replica reflects the current state affected by (potentially) _many_ users, whereas the client’s local replica is typically only affected by a _single_ user. We think that a conflict caused by one user should not negatively affect the work done by other users, especially since these users already have agreed on the common server replica state. 

- _Object ID_ : this approach is presented in [Mol+03]. The (unique) object IDs of the two objects affected by the conflicting operations are compared to determine the winner operation. We do not recommend this approach because users are typically not aware of object IDs and thus the resolution result will appear _arbitrary_ to the user. 

- _Object type_ : in [TSR15] the authors use this criterion for conflicts related to name clashes. If one operation affects a directory and the other one affects a file, then one type can be chosen to take precedence over the other. This criterion is inconclusive in case both objects have the same type. 

We note that a user-centered design approach makes sense where users are involved in adding new domain-specific winner-criteria. For instance, the following criteria were proposed by members of the author’s organization: 

- Estimate the _amount of work_ for two conflicting edited files by counting the number of modification file system events of each replica since the last synchronization. The user who updated a file more often is assumed to have done more work and thus her file should win the conflict. More elaborate versions of this criterion can also be built, e.g. including the delta of file’s size after each modification. However, this criterion may only make sense for specific file types (where the amount of work is reflected in the byte size delta) and user behaviors (where users or the application actually updates a file regularly). 

- _Time of day_ : for example, in certain workflows the changes that occur at late hours may be more important than changes registered in the afternoon. However, the same disadvantages as mentioned for _Timestamps_ above do apply. 

- _File type_ (extension): is not actually a criterion, because by itself it is always inconclusive. However, it can be used as a precursor in combination with another criterion. For instance, the configuration could define that for concurrent file edit conflicts, the _replica_ criterion is fixed to “server file wins” for _document_ files, whereas it is fixed to “client file wins” for _spreadsheet_ files. 

- _Namespace location_ : similar to the _file type_ presented above the location in the namespace may be a precursor for another criterion, s.t. conflict that affect objects in one part of the hierarchy are resolved differently than those situated in another part of the hierarchy. 

- _User_ : operations made by specific user accounts (or groups) may take precedence over the operation made by another user/group. 

The set of criteria may also be combined to a _cascaded_ set of criteria with different priorities. For instance, the concurrent file edit conflict might be configured as “Server Replica wins” (lowest priority used as fall back) + “Change made by Prof. Pepper wins” (higher priority) + “Client changes on _document_ files win” (highest priority). Due to the complexity we left implementing user-proposed winner-criteria and cascaded winner-criteria as future work. 

_5.4. CONFLICTS PATTERNS_ 

75 

|Pattern|Related concrete conflicts|Affects name|Affects<br>content|Intentions<br>preservable|
|---|---|---|---|---|
|Pseudo conflict|Create-Create, Link-Link,<br>Move-Move (Source),<br>Move-Move (Dest),<br>Edit-Edit|✓|✓|✓|
|Name clash<br>conflict|Create-Create, Link-Link,<br>Create-Link,<br>Move-Create, Move-Link,<br>Move-Move (Dest)|✓|X<br>(✓for<br>Create-Create)|✓|
|Edit conflict|Edit-Edit|X|✓|✓|
|Delete conflict|Edit-Delete,<br>Move-Delete,<br>Link-Unlink|✓|✓|X|
|Move conflict|Move-Move (Source)|✓|X|X|
||Move-ParentDelete|✓|X|X|
|Idi fli|Create-ParentDelete|✓|✓|✓|
|nrect conlct|Move-Move (Cycle)|✓|X|X|
||Node-typing|✓|X|✓|



Table 5.2: Overview of conflict patterns 

### **5.4 Conflicts patterns** 

Our consistency philosophy and conflict resolution policies are high-level concepts which are consistently applied to the concrete conflicts presented in section 5.5. Because many conflicts share some characteristics, they should also be resolved in similar approach. The _conflict patterns_ presented in this section group these conflicts to a set of conflict patterns. 

Conflict patterns borrow ideas from Alexander’s _Pattern Language_ [Ale+13]. At its core are _design patterns_ , which refer to each other, forming a pattern language. Design patterns describe problems and possible solutions in a structured way. They are written from a high-level perspective such that their solutions typically require adaptation to solve the concrete problem at hand. Patterns aren’t considered perfect, but rather being hypotheses of what is most likely an appropriate solution for a given problem. To quote Alexander, 

- [...] each pattern represents our current best guess as to what arrangement of the physical environment will work to solve the problem presented. 

Although the origin of the pattern language is in architecture, it has been applied to many other domains, including software engineering [Gam+77]. We apply the pattern language to file system conflicts. We note that even though this domain is very specific, it is straightforward to abstract our patterns and their foundation (the file system) s.t. they apply to generic graph structures whose operations follow similar rules. 

We start with an overview of patterns in section 5.4.1 and then present each pattern in sections 5.4.25.4.7. From these patterns we derive the list of concrete conflicts presented in section 5.5. 

#### **5.4.1 Overview** 

An overview of conflict patterns is shown in table 5.2. The _related concrete conflicts_ column shows the relationship between _conflict patterns_ and _conflicts_ . The _Intentions preservable_ column addresses whether the intention of _both_ conflicting operations can be preserved - either completely or at least to a large extent. Where this is not the case, one of the operations typically has to be discarded by the synchronizer. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

76 

The general naming scheme for a conflict is _<operation on replica X>_ **_-_** _<operation on replica Y>_ . 

For instance, _Move-Create_ means that a _move_ operation of one replica conflicts with a _create_ operation on the other replica. Some conflicts such as “Move-Move (Dest)” deviate from this scheme slightly. The corresponding conflict sections explain the deviation in more detail. 

#### **5.4.2 Pseudo conflict** 

**Description:** At each replica an operation with the _exact same outcome_ was applied. A pseudo conflict may also be referred to as _false conflict_ in other works. 

**Solution:** Such kinds of conflicts can be solved without data loss and don’t require user notification. The synchronizer only needs to avoid that the same operations are detected again in the future, by updating its internal database to reflect the change. The replicas do not have to be manipulated, because they both already have the same operation applied to them. A synchronizer that correctly differentiates real vs. pseudo conflicts enable maximum conflict avoidance. 

##### **Conflicts:** 

- Create-Create: creating a _file_ with the same name under the same parent where the content is the same on both replicas 

   - Note: the concurrent creation of a _directory_ is not listed here. We argue that if it were a pseudo conflict, the effect would be that the synchronizer recursively merges sub-objects, which may not be the intention by the user. 

- Link-Link: creating a link to the same file in the same parent and name 

- Move-Move (Source+Dest): moving the same object to the same parent and name 

- Edit-Edit: editing the content of a file such that the content is equal on both replicas 

To verify that two files have the same content (Create-Create, Edit-Edit), _checksums_ are used. Various algorithms exist, such as MD5 or SHA-1. These algorithms take binary data of _any_ length (the file’s content in our case) and produce a short, _fixed_ -length output, the checksum, which can be compared with negligible costs regarding network bandwidth and CPU time. There are two disadvantages. First, computing them takes at least as much time as reading the entire data. Second, two files with _different_ contents can theoretically produce the _same_ checksum, however, with a very low probability. We use checksums because of the following advantages: 

- Transmitting large files, which is extremely costly over slow networks, can be omitted in case of an equal checksum. 

- Checksums avoid that the synchronizer causes Create-Create or Edit-Edit conflicts when the synchronization process was interrupted by the user while the synchronizer transmits a file to the remote server. This is an implementation-specific detail due to the lack of transactions in some protocols such as HTTP, and is further explained in figure 5.3. 

- Support for _migrating_ all objects of a replica from one location to another one without the need of retransmitting sub-files: The user may desire to change the physical location of either replica (i.e., on the local disk or on the remote server), e.g. because of quota restrictions or because she wants to reorganize the file system namespace for any reason, then the use of checksums enables the following workflow: 

   1. User deletes the configured pairing of the two root directories in the synchronizer user interface. This deletes all synchronization-related meta-data, including the local database. 

_5.4. CONFLICTS PATTERNS_ 

77 



<!-- Start of picture text -->
CreateOperation Database FileSystemOperator HttpHandler<br>file_id=Uupload_file(path)<br>headers=UPUT(path,Udata)<br>file_id=Uextract_file_id_from_headers(headers)<br>id_db=Uinsert_new_file(path,Ufile_id)<br><!-- End of picture text -->

Figure 5.3: Uploading a file to a server 

This figure is an UML sequence diagram, illustrating the upload process of a file. Time flows from top to bottom. The synchronizer executes a _CreateOperation_ , whose first task is to upload the file, expecting the file’s server-replica-specific ID, followed by inserting the name, file ID (and other meta-data such as lastmodified meta-data) into the database. Assume that at the first red dashed line the file has been fully received by the server and was stored in its persistent storage. The _CreateOperation_ can be aborted at any time, either by the user, or, say, by a power failure or program crash. Should this happen anywhere in-between the two red dashed lines, then the upload is complete, but the change is not reflected in the synchronizer’s database yet. When the synchronization is restarted, the file would be detected as a Create-Create conflict when not using checksums. 

2. User physically moves the root directories to a new location on either replica, using the tools they are used to (such as a file manager). 

3. User sets up a new pairing in the synchronizer user interface. The initial synchronization will detect Create-Create pseudo conflicts for all files and directories<sup>5</sup> . Effectively, no files are transmitted, only the local database is repopulated. 

#### **5.4.3 Name clash conflict** 

**Description:** The _create_ , _link_ or _move_ operation _oX_ detected in replica _X_ and _create_ , _link_ or _move_ operation _oY_ detected in replica _Y_ both target the same name within a specific parent dir. However, a name may only be used once within a parent dir. 

**Exception:** See _Pseudo conflict_ pattern. 

**Solution:** In general there are two approaches which preserve both user’s intentions: 

> 5In this case, concurrent creation of directories would need to be considered as pseudo-conflicting. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

78 

1. _Merge_ the content of the two objects affected by _oX_ and _oY_ , 

2. _Rename_ one or both objects. In the latter case finding a more suitable name for the objects is left to the user after being notified about the conflict. 

We note that there are other options for resolving name clash conflicts, e.g. discarding one operation by deleting the affected object. However, such options would completely discard the intention of one of the users and thus we do not follow up on them any further. 

Which solution is better depends on the operation and the type of the object. We generally recommend the _rename_ approach, because when the users generated the operations, their intention was for _oX_ and _oY_ to modify _different_ objects. We agree with [TSR15] in their assumption that the user wants the synchronizer to keep both objects separate, instead of merging them. Merging has several caveats (see section 5.4.4 for issues when merging _files_ ), most notably that unmerging, in case of a bad merge, is more tedious than keeping objects separate in the first place, deferring the merge to the user. The authors of [Naj16] disagree and propose to always merge, but do not explain their decision. 

One exception where _renaming_ may not be appropriate is the case where _oX = oY = createdir_ . All tested industrial synchronizers (see section 8.2.5.4) and [TSR15; Naj16] prefer to _merge_ the contents of the directories recursively. While [TSR15; Naj16] don’t provide a rationale for this decision, we understand that merging directories avoids conflicts. This is important when establishing a new synchronization between two replicas (for which no synchronization was set up before), but whose file system are already equal (as was explained above in section 5.4.2). Other works [Mol+03; NS16] prefer _renaming_ over merging for two _createdir_ operations. In [NS16] the authors argue that _“the merging is not the intention of any operation involved”_ , merging directories recursively may involve finding and solving conflicts for sub-objects which is _“difficult for users to understand and complex to implement by the underlying system”_ and merging _“may create a nontrivial burden to users who need to unmerge the merged subtrees”_ . We consider the best solution to be a mixture of both approaches: generally prefer _renaming_ , but initially switch to _merge_ mode temporarily until the very first synchronization has completed. 

Whenever name clash conflicts are solved by _renaming_ , the author of the synchronizer needs to choose whether to rename _one_ or _both_ objects. In either case we recommend that objects are renamed by appending a globally unique suffix to their name. Other helpful information to include for the new name are the date and time of conflict discovery, or the name of the replica or user. When renaming _both_ objects, the synchronizer implicitly considers both replicas to be negatively affected and should consequently notify _all_ affected users. When renaming just _one_ object, one replica is implicitly chosen as the winner and only the loser needs to be notified. 

As for the winner-criterion (section 5.3.2) different criteria can be used for chosing the winner, such as _object type_ , _operation type_ or _replica/user_ . In our implementation we use only a _single_ criterion, _replica/user_ , to make conflict resolution predictable and comprehensible for the user. The first two criteria ( _object type_ , _operation type_ ) are inconclusive in case _oX_ and _oY_ use the same object or operation type. As discussed in section 5.3.2, we suggest to prefer the operation performed on the _server_ replica for synchronizers in a client-server star-topology setup. 

#### **5.4.4 Edit conflict** 

**Description:** The content of a file was changed on both replicas. 

**Exception:** See _Pseudo conflict_ pattern. 

**Solution:** The goal is to preserve both replica’s changes. There are several approaches: 

1. _Merging_ : the synchronizer merges the content of both files. It needs to support specific file formats (in [Mol+03] the authors support text and XML files). If the synchronizer is not integrated into the application used to manipulate the file, this is done by a state-based 3-way-merge approach. Merging has several disadvantages, which is why we do not recommend this approach: 

_5.4. CONFLICTS PATTERNS_ 

79 

   - The 3-way-merge procedure needs to be implemented for each file format separately. This is infeasible in practice, due to the large number of proprietary file formats. 

   - Either the complete history (for log-based approaches such as OT) or a base version (for 3- way-merge) is required to merge both replica’s changes. The latter may not be available. In particular, basic file system implementations such as those of macOS, UNIX and Windows do not store older versions of a file by default. 

   - The merge itself may fail due to conflicts within the data, requiring user intervention. 

   - If the merge is successful, the result is a _syntactic_ merge [SS05] which may be _semantically_ incorrect.<sup>6</sup> 

2. _Renaming both files:_ like for _name clash_ conflicts, each file is renamed to a unique name. This approach is applied in [TSR15]. All affected users should be notified. 

3. _Renaming one file:_ similar to _name clash_ conflicts this requires to choose a winner and rename the file of the loser (notifying the corresponding user). Renaming the loser can be implemented by a simple rename operation, or moving the file to a different location, possibly outside of the synchronized namespace, to preserve the loser’s changes. To select the winner there are several criteria available, such as: 

   - _Last-modified timestamps:_ the file with the higher timestamp is assumed to be winning copy [UFB10] 

   - _Replica or user:_ (see _name clash_ conflicts) [Bao+11; Li+12a; Li+12b] 

For the typical setup where synchronization happens in a client-server star-topology, we recommend approach 3, with the _server_ replica’s file taking precedence. We advice to _not_ synchronize the loser’s renamed file to the server (but keep it only on the local user’s replica). This avoids unnecessary bandwidth use and makes sure that other users not involved in the conflict are oblivious of it. 

#### **5.4.5 Delete conflict** 

**Description:** On one replica an object is moved or edited, on the other replica the corresponding object is deleted (either directly or as a consequence of deleting a parent directory). 

**Solution:** Whether deletion or the edit/move operation should take precedence depends on the environment in which the synchronizer is used, hence we don’t give a recommendation for the preference itself. While using a criterion such as _replica/user_ would be possible, the majority of works from research and industry use the _operation type_ criterion, with the configuration to prefer the move/edit operation [NS16; Li+12a; Li+12b; TSR15; Rei+94; Naj16]. This is in line with our philosophy to discard the operation that was less work for the user. Given a selected preference, there are different ways to implement the manipulation of the loser operation: 

- If deletion is preferred: 

   - If the object is a file: 

      - <sup>If it was only moved, delete the moved file</sup> 

      - <sup>If it was (also) edited, the edited copy should be backed up prior to its deletion to pre-</sup> serve the intention by the user who edited the file 

   - If the object is a moved directory, it should be deleted, but the implementation first needs to check for sub-node conflicts and resolve these. This can be achieved by undoing the move operation on the corresponding replica. 

> 6For instance, if the base version is a text file that contains “The dogs jumps”, which is modified in replica _X_ to “The dog jumps” and in replica _Y_ to “The dogs jump”, the merged result will be “The dog jump” which is semantically incorrect. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

80 

- If the edit or move operation is preferred, this should lead to a reconstruction of the object on the replica that deleted it (in case the object was a dir: including the reconstruction of its sub-objects), at the new location (the move operation’s destination). 

#### **5.4.6 Move conflict** 

**Description:** On both replicas the _same_ file or directory was moved to a different location. That is, on each replica either the name or parent directory (or both) differs. Note: if the operation affects a _file_ on a file system with _hardlink_ support, this is _not_ necessarily a conflict because the synchronizer can create _two_ links for the file in the merged result. 

##### **Exception:** See _Pseudo conflict_ pattern. 

**Solution:** For file systems where a file or dir may only exist once, it is difficult to preserve the moveintention of both replicas. One possibility is to create a _copy_ of the object, as done in [TSR15] for directories. However, this approach no longer resembles the original intention of the user, because a _second_ , different object has come into existence, cluttering the file system and confusing the user. We suggest that the synchronizer discards the move operation of one replica and prefers the one of the other replica, which was also implemented in [NS16; Mol+03]. We use the _replica/user_ criterion in our implementation, configured to let the server replica’s move operation take precedence. 

#### **5.4.7 Indirect conflicts** 

Two operations indirectly conflict with each other if they don’t target the same object or name, but different objects. There is always a hierarchical parent-child relationship between the objects affected by the operations. Refer to sections 5.5.6+5.5.7+5.5.10 for the description and solutions of the conflicts _Move-ParentDelete_ , _Create-ParentDelete_ and _Move-Move (Cycle)_ . The _Node-typing_ conflict is described in appendix A.2.4 on page 201. 

### **5.5 Conflicts** 

This section presents each individual conflict and its resolution in full detail. Each subsection is dedicated to a specific conflict, providing a description, a formal definition using the operations from table 3.6 on page 44 and the resolution approach. Our implementation actually finds conflicts using the update trees introduced in section 4.2.4 on page 50. Detailed pseudo code for finding conflicts in these trees is presented in appendix A.6 on page 211. To limit the scope of this work the following subsections only present details of the conflicts and their resolution for the _NH-MD_ file system introduced in section 2.3.1 on page 15. For the file systems _H-All_ and _NED-All_ we provide conflict descriptions and definitions in appendix A.2 on page 197 for the interested reader, but omit details for their _resolution_ because our implementation focuses on the NH-MD file system. 

Because it is possible that _several_ conflicts are found, we find that a special property that must hold for resolving any conflict is that the resolution must work independently of the existence of other conflicts, and have no negative effect on other conflicts. We will provide further details in section 5.7, but already mention here that our high-level conflict resolution approach is _iterative_ . We find all conflicts, sort them, resolve the conflict with highest priority and the restart the synchronization. 

#### **5.5.1 Create-Create** 

##### **Description** 

On both replicas a new file or directory is created with the same name under the same parent dir. Is a pseudo conflict if both operations are _createfile_ operations where the content/checksum of the files is equal, or if both operations are _createdir_ operations and this synchronization iteration is the very first one. 

_5.5. CONFLICTS_ 

81 

The associated pattern is the _name clash_ conflict. 

##### **Definition** 

Let _f ir stsync_ be a property that holds if the following two conditions hold: (1) synchronization has not yet fully completed since the user configured the synchronization, (2) all nodes in the update trees either have no change-event or only the _create_ change-event. 

Let 





with _create_ : _= createdir ∨ create f ile_ . 

Then 

_pseudo_ ( _oX_ , _oY_ ) _=_ [ _f ir stsync ∧ t ype_ ( _oX_ ) _= createdir ∧ t ype_ ( _oY_ ) _= createdir_ 

_∧ corresponding_  ob ject_  id_ ( _i X_ , _X_ ) _= iY_ ] _∨_ [ _t ype_ ( _oX_ ) _= create f ile ∧ t ype_ ( _oY_ ) _= create f ile ∧ corresponding_  ob ject_  id_ ( _i X_ , _X_ ) _= iY_ 

_∧ checksum_ ( _snapshotX_ , _i X_ ) 

_= checksum_ ( _snapshotY_ , _corresponding_  ob ject_  id_ ( _i X_ , _X_ )] 

is a function that indicates whether two _createfile_ operations are pseudo-conflicting. Function _corresponding_object_id()_ is defined in appendix A.5.1. 

We can now define the Create-Create conflict, using the _⊗_ symbol to indicate that the two operations are conflicting: 

_create X_ ( _i X_ , _p X_ , _nX_ ) _⊗ createY_ ( _iY_ , _pY_ , _nY_ ) _=¬pseudo_ ( _oX_ , _oY_ ) 

_∧ corresponding_  ob ject_  id_ ( _i X_ , _X_ ) _= iY_ 



where 

_isedit_ ( _oX_ ) _= t ype_ ( _oX_ ) _= create f ile ∧∃delete f ile X_ ( _j_ , _v_ ) : _v = p X ∧ name_ ( _dbsnapshotX_ , _j_ ) _= nX_ 

ensures that the _createfile_ operation _oX_ is not replaced by an _edit_ operation, as explained in section 4.2.4.2. 

The violated precondition is _id_ ( _p_ , _n_ ) _= error_ of the _createfile_ or _createdir_ operation. A file or dir cannot be created at a location that is already occupied. 

##### **Resolution** 

Following the recommendations from section 5.4.3, we rename the object of the loser operation using the following form, which is also used for other conflict types: 



The random string forces the new name to be unique, making sure that the rename operation will not fail in practice (e.g. because the new name might be occupied by another object). After the rename 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

82 

operation finished and the synchronization is restarted, the two affected objects no longer have the same name and the conflict is resolved. 

We note that our implementation offers flexibility by providing two options for the _replica_ criterion, usually chosen at compile-time of the program. We offer options for Create-Create as well as most other conflict types. We implemented the following options for Create-Create conflicts: 

- **Option 1** : Local replica always wins. 

- **Option 2** : Remote replica always wins. 

#### **5.5.2 Edit-Edit** 

##### **Description** 

The content of an already synchronized file was changed on both replicas. Is a pseudo conflict if the new content of both files is equal. 

The associated pattern is the _Edit_ conflict. 

##### **Definition** 

_editX_ ( _i X_ , _op X_ ) _⊗ editY_ ( _iY_ , _opY_ ) _=_ [ _i X = cid_ ( _iY_ , _Y_ )] 

_∧_ [ _checksum_ ( _snapshotX_ , _i X_ ) _̸ = checksum_ ( _snapshotY_ , _iY_ )] 

Function _cid()_ is defined in appendix A.5.2. 

Note that a _delete f ile_ and _create f ile_ can count as _edit_ due to the consolidation of these two operations described in section 4.2.4.2. 

Syntactically, no precondition is violated, but overwriting the file content on replica _X_ with the one from replica _Y_ would cause _X_ ’s changes to be lost. This _semantic_ violation instead takes place on the _update tree_ levels, which are aware of the before-state (particularly its _lastmodified_ meta-datum) of the file and therefore know that both replicas independently made incompatible modifications. 

##### **Resolution** 

We resolve this conflict as described in the Edit conflict pattern, see section 5.4.4. We first back up the file on the loser replica and then _replace_ it with the content of the winner replica, updating the database snapshot with updated replica-specific IDs, lastmodified meta-datum and checksums. Some replicas may store older file versions automatically, in this case creating a backup is not necessary. 

Like for Create-Create conflicts, we implemented the following options for the _replica_ criterion: 

- **Option 1** : Local replica always wins. 

- **Option 2** : Remote replica always wins. 

#### **5.5.3 Move-Create** 

##### **Description** 

On one replica the user moved an object into a specific parent dir _v_ , giving it the name _n_ , on the other replica the user created a new object named _n_ in the parent directory corresponding to _v_ . 

The associated pattern is the _name clash_ conflict. 

_5.5. CONFLICTS_ 

83 

##### **Definition** 

_create X_ ( _i X_ , _p X_ , _nX_ ) _⊗ moveY_ ( _iY_ , _uY_ , _vY_ , _nY_ ) _=_ [ _p X = cid_ ( _vY_ , _Y_ )] _∧_ [ _nX = nY_ ] 

_∧¬isedit_ ( _create X_ ) 

with _create_ : _= createdir ∨ create f ile_ 

Violated preconditions are _id_ ( _p_ . _n_ ) _= error_ of the _createfile_ / _createdir_ operation, and _id_ ( _v_ , _n_ ) _= error_ of the _move_ operation. A file or dir cannot be created at (or moved to) a location that is already occupied. 

##### **Resolution** 

Conflict resolution happens the same way as for Create-Create conflicts. Our implementation offers four options, two for the _replica_ criterion, two for the _operation type_ criterion, using option 2 by default: 

- **Option 1** : Local replica always wins. 

- **Option 2** : Remote replica always wins. 

- **Option 3** : Move operation always wins. 

- **Option 4** : Create operation always wins. 

#### **5.5.4 Edit-Delete** 

##### **Description** 

On one replica the content of an already synchronized file was changed, on the other replica that file was deleted. 

The associated pattern is the _delete_ conflict. 

##### **Definition** 

_editX_ ( _i X_ , _op X_ ) _⊗ delete f ileY_ ( _iY_ , _pY_ ) _=_ ( _i X = cid_ ( _iY_ , _Y_ )) 

Note that a _delete f ile_ and _create f ile_ can count as _edit_ due to the consolidation of these two operations described in section 4.2.4.2. 

On replica _Y_ , _ancestor_ ( _iroot_ , _iY_ ) is violated when trying to apply the _edit_ operation. On replica _X_ there is no violation on a syntactic level, but on the semantic level: the changes of the _edit_ operation would be lost. The user who deleted the file would have done so without knowing that it was recently edited by another user on the other replica. 

##### **Resolution** 

Our implementation offers four options, using the _replica_ and _operation type_ criterion, to determine whether the _edit_ or _deletefile_ operation takes precedence, choosing option 1 as default: 

- **Option 1** : _Edit_ operation always wins. 

- **Option 2** : _deletefile_ operation always wins. 

- **Option 3** : Operation of the local replica always wins. 

- **Option 4** : Operation of the remote replica always wins. 

If the configured option favors the _edit_ operation, we perform the following checks: 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

84 

- If the delete node’s parent has no _Delete_ change-event, a delete operation is performed for the edit node which only deletes the corresponding local and remote replica’s entries from the _database_ (not the file system). This will cause the file to be detected as new in the next sync iteration, thus it will be restored. 

- If the delete node’s parent has a _Delete_ change-event, the delete operation mentioned above would only turn the Edit-Delete conflict into a Create-ParentDelete conflict in the next sync iteration. To avoid that the synchronizer presents the user two conflicts, we first execute a move operation that moves the file to the root physically, with a conflict-suffix, followed by executing the delete operation that removes the file’s entry from the database. 

If instead the _delete_ operation takes precedence, we first perform a backup operation of that file. Next, we perform the delete operation that removes the file physically from the losing replica and from the database. 

#### **5.5.5 Move-Delete** 

##### **Description** 

On one replica an object was moved, on the other replica the same object was deleted. 

The associated pattern is the _delete_ conflict. 

##### **Definition** 

_move X_ ( _i X_ , _uX_ , _v X_ , _nX_ ) _⊗ deleteY_ ( _iY_ , _pY_ ) _=_ ( _i X = cid_ ( _iY_ , _Y_ )) _∧¬isedit_ ( _deleteY_ ) 

with _delete_ : _= delete f ile ∨ deletedir_ 

and 

_isedit_ ( _deleteY_ ) _=_ [ _t ype_ ( _deleteY_ ) _= delete f ile_ ] 

_∧∃create f ileY_ ( _jY_ , _vY_ , _nY_ ) : _vY = pY ∧ nY = name_ ( _dbsnapshotY_ , _iY_ ) 

On replica _Y_ , _i ∈ list_ ( _u_ ) is violated when trying to apply the _move_ operation _._ You cannot move a file or dir that is already deleted. On replica _X_ there is no violation on the syntactic level, but on the semantic level: the structural change of the _move_ operation would be lost. The user who deleted the object would have done so without knowing that it was recently moved by another user on the other replica. 

##### **Resolution** 

Like for Edit-Delete conflicts, the conflict resolution first needs to determine whether the _move_ or _delete_ operation takes precedence. Because both _Edit-Delete_ and _Move-Delete_ conflicts belong to the _Delete_ conflict pattern, it makes sense that these two conflict types are resolved similarly. Thus, the options described for Edit-Delete also apply here for Move-Delete. 

When the move or delete operation applies to _directories_ , conflict resolution becomes more involved, because dirs can have children on which the user could have performed additional operations, on either (or both) replicas. Specifically, new files or dirs could have been created, existing ones could have been deleted or edited, and different kinds of move operations could have happened. Consider dir node _A_ , then these three types of moves on its child nodes are possible: 

- Moves within the hierarchy of _A_ (in other words: _A_ ’s path is a prefix of both the _source_ and _dest_ path of such moves) - can only happen on the Move-replica of a Move-Delete conflict. 

- Some node _N_ outside of _A_ is moved so that _N_ is now a child of _A_ (referred to as _Move-ParentDelete_ conflict, see next section) - can only happen on the Move-replica of a Move-Delete conflict. 

_5.5. CONFLICTS_ 

85 

- Some child node of _A_ is moved to a destination that is outside of _A_ - can happen on both the Moveor Delete-replica of a Move-Delete conflict (on the Delete-replica, the user must have moved the child node out of _A_ prior to deleting _A_ ). 

For each of these cases we have to make sure that the synchronizer’s behavior is consistent w.r.t. the selected resolution option and that it doesn’t produce incorrect file system or database operations. 

The outline of the behavior related to child nodes is shown in the following table. It shows desired outcomes for a Move-Delete situation. All situations have in common that on the left replica directory “/A” was moved to “/B”, while “/A” was deleted on the right replica. However, in each situation (table row) one or both replicas performed additional operations. The _Delete wins_ and _Move wins_ columns illustrate the desired outcome for the corresponding chosen Move-Delete resolution option. The color legend is as follows: 

- Green: creation of a new object 

- Yellow: a file’s content was edited 

- Blue: node was moved 

|Situation|Delete wins|Move wins|
|---|---|---|
|L<br>R|S|S|
|A → B<br>R<br>S<br>R<br>A<br>S|All files are deleted (deletions<br>on move-replica have no<br>effect)|B<br>R|
|Q<br>Q<br>Move-replica also deleted<br>some of the child nodes||Q<br>Child nodes also deleted by<br>the move-replica were deleted<br>from both replicas and thus<br>cannot be restored|
|L<br>R|S|S|
|A → B<br>A|X|B|
|R<br>Q'<br>S<br>R<br>Q<br>S<br>X<br>Move-replica creates new<br>child nodes or edits existing<br>ones, inside the moved<br>directory|After undoing the move locally<br>(“/B” to “/A”), an Edit-Delete<br>conflict for “’/A/R/Q’” and a<br>Create-ParentDelete for<br>“/A/S/X” is detected (see<br>subsection 5.5.7) and resolved.|R<br>Q'<br>S<br>X<br>“/B” (and_all_of its<br>sub-elements) are detected as<br>new and are synchronized to<br>the delete-replica|



_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

86 

|Situation|Delete wins|Move wins|
|---|---|---|
|L<br>R<br>A → B<br>R<br>S'<br>R<br>Q<br>A<br>S<br>Q|S<br>Both “/B” and “/S’“ are deleted<br>on the move-replica, because<br>a Move-Delete conflict is<br>detected for both of them and<br>the moves are undone|B<br>R<br>Q<br>S<br>S'|
|Move-replica moves the child<br>node “/B/S” out of “/B”, to<br>“/S’”||Both “/B” and “/S’“ are<br>detected as_new_and are<br>synchronized to the<br>delete-replica, at the new<br>respective locations|
|L<br>R|S|S|
|A → B<br>A<br>S'|S'|B<br>S'|
|R<br>Q<br>S<br>R<br>Q|“/B” is deleted on the<br>move-replica, but “/B/S” is<br>first moved to “/S’” on the<br>move-replica|R<br>Q|
|The delete-replica moves the<br>child node “/A/S” out of “/A”<br>to “/S’” prior to deleting “/A”||The move “/A/S” to “/S’”<br>should be executed on the<br>local replica. “/B” (with all its<br>children, except “/B/S”) is<br>detected as new on the<br>move-replica and therefore<br>synchronized to the<br>delete-replica|



Another specialty in our desired Move-Delete resolution is explained in figure 5.4. 

The general behavior of our Move-Delete _resolution_ (which realizes the desired behavior described above) is similar to the one defined for Edit-Delete conflicts: 

- When the _move_ operation takes precedence, that node and all its child nodes are deleted from the database, so that the files are detected as _new_ on the subsequent sync iteration and are therefore synchronized to the replica that deleted the node, so that they restored at the _new_ location (i.e. the one corresponding to the Move-replica). However, for directories, the user who deleted the dir could have moved one or more child objects outside of the deleted directory. Such objects potentially still exist on _both_ replicas and should therefore not be deleted from the database. More details are provided in appendix A.7.2. 

- When the _delete_ operation takes precedence, we should _not_ delete the other replica’s object in the file system or database (as done in the Edit-Delete resolution), because this would have undesired 

_5.5. CONFLICTS_ 

87 



<!-- Start of picture text -->
L R L R<br>A A A A<br>R → X S R S X S S<br>(a) Initial situation (b) Introduced Create-ParentDelete<br>conflict<br><!-- End of picture text -->

Figure 5.4: Avoiding Move-Delete detection for within-dir moves 

Subfigure (a) shows the conflict situation. While a dir is deleted on the remote replica, the user of the local replica moves/renames child nodes _within_ that dir. According to our conflict definitions, this causes the detection of both a _Move-ParentDelete_ conflict (explained in the next section) and a _Move-Delete_ conflict for that node. There are two options to resolve this. First, we could treat the situation as _Move-Delete_ conflict. When that conflict’s resolution option is set to “move wins”, this would cause the “/A/R” entry to be removed from the database, causing a Create-ParentDelete conflict as shown in subfigure (b), which is resolved by moving the object to the root (see section 5.5.7 for more details). In other words, treating the situation as _Move-Delete_ conflict would save the user’s within-dir move operation. The second option is to treat the situation as _Move-ParentDelete_ conflict, which is resolved by undoing the move. Effectively this means that within-dir move operations are lost. We prefer and implemented the latter approach, for two reasons: (1) Semantically we see no advantage in saving namespace reorganizations _within_ a dir that is deleted by another replica. Moving such objects to the root (as done by the _Create-ParentDelete_ resolution) only fragments the synchronized namespace. (2) When interpreting the situation as _Move-Delete_ conflict, the user would be confused by _two_ conflict messages shown consecutively, one for the _Move-Delete_ , one for the _Create-ParentDelete_ conflict. 

effects on its child nodes in case the conflict affects a _directory_ . Instead, we _undo_ the move, so that a subsequent sync iteration no longer recognizes the move, but just the delete operation, as a conflict-free situation. The delete operation will eventually be executed, but special situations (such as other conflicts caused by child nodes) can be detected and handled first. Note that undoing the move is an involved operation and is further elaborated on in appendix A.7.1. 

#### **5.5.6 Move-ParentDelete** 

##### **Description** 

On one replica a directory node _A_ was deleted, on the other replica another object was moved such that it is now an immediate child of _A_ . 

The associated pattern is the _indirect_ conflict. 

##### **Definition** 

_move X_ ( _i X_ , _uX_ , _v X_ , _nX_ ) _⊗deletedirY_ ( _iY_ , _pY_ ) _= name_ ( _snapshotY_ , _cid_ ( _i X_ , _X_ )) _̸ = error ∧_ ( _cid_ ( _v X_ , _X_ ) _= iY_ ) 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

88 

The precondition _t ype_ ( _v_ ) _= dir_ of the _move_ operation is violated. You cannot move a file or directory into a directory that is already deleted. 

##### **Resolution** 

Unlike the conflicts presented so far, we decided _not_ to offer any configurable options for resolving this conflict. Instead we always resolve it by undoing the move operation, as described in appendix A.7.1. In the subsequent sync iteration, only the delete operation remains, which is then executed. This approach favors the user’s intention to delete the directory over the move operation. Other alternatives have considerable disadvantages: 

- Preferring the _deletion_ , and _not_ undoing the move operation, would destroy the object that was moved inside it, which was neither intended by the user who moved the object nor the one who deleted the directory. 

- Preferring the _move_ operation would require the synchronizer to recreate the directory (and all ancestor directories leading to it), producing an _inconsistent_ preservation of the delete-intention, fragmenting the namespace. To the deleting user the directory would inexplicably re-appear. This merged result is undesirable, also for the user who moved the object. She would not have moved an object into the directory, had she known before that the other user already deleted it. Cleaning up the workspace may cause a considerable amount of time. 

Our suggestion to favor the deletion but to first undo the move causes only little loss of data. Once the user is notified about the undone move operation, she can choose to restore the deleted directory, e.g. from a server backup, and repeat the move operation. 

#### **5.5.7 Create-ParentDelete** 

##### **Description** 

On one replica a directory node _A_ was deleted, on the other replica a new object was created as an immediate child of _A_ . 

The associated pattern is the _indirect_ conflict. 

##### **Definition** 

_create X_ ( _i X_ , _p X_ , _nX_ ) _⊗ deletedirY_ ( _iY_ , _pY_ ) _=_ ( _p X = cid_ ( _iY_ , _Y_ )) _∧¬isedit_ ( _create X_ ) 

with _create_ : _= createdir ∨ create f ile_ 

The violated precondition is _t ype_ ( _p_ ) _= dir_ of the _createfile_ or _createdir_ operation. You cannot create a file in a directory that is already deleted. 

##### **Resolution** 

Like _Move-ParentDelete_ , we offer no configurable options for resolving _Create-ParentDelete_ conflicts. We resolve it by physically moving created objects to the root, appending the conflict suffix introduced for Create-Create conflicts. On the next sync iteration, the conflict is no longer detected and the directory is deleted. This approach favors the user’s intention to delete the directory and we favor this solution over recreating the deleted parent directory(ies), for the same reasons as given for Move-ParentDelete conflicts. 

_5.5. CONFLICTS_ 

89 

#### **5.5.8 Move-Move (Source)** 

##### **Description** 

On both replicas the _same_ file or directory was moved to a different location. We denote this Move-Move conflict “Move-Move ( _Source_ )” because it affects the _same source_ object. Is a pseudo conflict if both the destination parent dir and new name of the file or dir are equal. 

The associated pattern is the _move_ conflict. 

##### **Definition** 

_move X_ ( _i X_ , _uX_ , _v X_ , _nX_ ) _⊗ moveY_ ( _iY_ , _uY_ , _vY_ , _nY_ ) _=_ ( _i X = cid_ ( _iY_ , _Y_ )) _∧_ [( _v X̸ = cid_ ( _vY_ , _Y_ )) _∨_ ( _nX̸ = nY_ )] The violated preconditions is _i ∈ list_ ( _u_ ). The object is no longer in the expected source location of the _move_ operation. 

##### **Resolution** 

Following the recommendation of the Move conflict pattern (section 5.4.6) we resolve this conflict by undoing the move of the loser replica. Like for Create-Create conflicts, we implemented the following options: 

- **Option 1** : Local replica always wins. 

- **Option 2** : Remote replica always wins. 

We decided to offer only a single resolution option that is equal for Move-Move (Source), Move-Move (Dest) and Move-Move (Cycle) conflicts, because the _move_ operation is the central operation in all of these conflicts. Hence, resolving these conflicts the same way makes conflict resolution more comprehensible to the user. 

As discussed in subsection A.7.2, the _Move-Delete_ conflict resolution code may manipulate the locations of _orphaned_ nodes in the database. Because this causes _Move-Move (Source)_ conflicts, the resolution code adds the _idb_ of the orphaned nodes together with the `winner_replica` to an in-memory registry. Consequently, to determine the winning replica, the Move-Move (Source) resolution code first checks whether the provided node’s _idb_ is known in the registry: 

- If not, `loser_node` is chosen according to the preconfigured resolution option for Move-Move (Source) conflicts. 

- If yes, `loser_node = conflict.local_node if winner_replica == Remote else conflict.remote_node` 

Then, `undo_move(loser_node)` is called to resolve the conflict. 

#### **5.5.9 Move-Move (Dest)** 

##### **Description** 

The users of both replicas each move a _different_ object into the same parent directory with the same name. The name of this conflict is Move-Move ( _Dest_ ) because both move operations affect the _same destination_ . 

The associated pattern is the _name clash_ conflict. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

90 

##### **Definition** 

_move X_ ( _i X_ , _uX_ , _v X_ , _nX_ ) _⊗ moveY_ ( _iY_ , _uY_ , _vY_ , _nY_ ) _=_ [ _i X̸ = cid_ ( _iY_ , _Y_ )] _∧_ [ _v X = cid_ ( _vY_ , _Y_ )] _∧_ ( _nX = nY_ ) The violated precondition is _id_ ( _v_ , _n_ ) _= error_ . A file or dir cannot be moved to a location that is already occupied. 

##### **Resolution** 

Following the recommendations of the _name clash_ conflict pattern (section 5.4.3), we _rename_ the file on the losing replica by appending the _conflict suffix_ . There are two options for the _replica_ criterion: 

- **Option 1** : Local replica always wins. 

- **Option 2** : Remote replica always wins. 

#### **5.5.10 Move-Move (Cycle)** 

##### **Description** 

Given two distinct directories _A_ and _B_ , the user moves _A_ into _B_ ’s namespace on one replica while _B_ is moved into _A_ ’s namespace on the other replica. This would create a cyclic parent-child relationship in the merged result, which is not allowed by _F_ ’s invariants. 

##### **Definition** 

_move X_ ( _i X_ , _uX_ , _v X_ , _nX_ ) _⊗ moveY_ ( _iY_ , _uY_ , _vY_ , _nY_ ) _=_ ( _t ype_ ( _snapshotX_ ,, _i X_ ) _= t ype_ ( _snapshotY_ , _iY_ ) _= dir_ ) 

- _∧ i X̸ = cid_ ( _iY_ , _Y_ ) 

- _∧ ancestor_ ( _snapshotX_ , _cid_ ( _iY_ , _Y_ ), _i X_ ) 

_∧ ancestor_ ( _snapshotY_ , _cid_ ( _i X_ , _X_ ), _iY_ ) 

The violated precondition is _¬ancestor_ ( _i_ , _v_ ). A file or dir cannot be moved to a location that is below itself. 

##### **Resolution** 

Since the attempt of applying the move operation of one replica to the other one will fail, the synchronizer needs to undo one of the move operations. 

#### **5.5.11 Conflict type completeness** 

The ten conflicts presented above cover _all_ possible conflict types. To show this, consider _OT={createfile, createdir, move, edit, deletefile, deletedir}_ which is the list of all operation types of file system _F_ , see table 3.6 on page 44. We start from an initially equal state for replicas _X_ and _Y_ . For any two types _t A_ , _tB_ from _OT_ we instantiate operations _oX_ (of type _t A_ ) and _oY_ (of type _tB_ ), apply _oX_ to _X_ (which yields _X_<sup>_′_</sup> ) and _oY_ to _Y_ (yields _Y_<sup>_′_</sup> ). We choose the operation parameters ( _i_ , _p_ , _u_ , _v_ , _n_ for _F_ ) such that either applying _oY_ to _X_<sup>_′_</sup> , or _oX_ to _Y_<sup>_′_</sup> fails, due to violated preconditions. 

Finding conflicts can be done manually or in an automated approach. We applied the manual, pragmatic approach, examining each individual precondition of each operation type _t A_ and finding a _tB_ , _oY_ and _oX_ that produces a conflict. The following list illustrates the relationships between the conflicts defined above and the preconditions: 

• createdir, createfile: 

_5.6. APPLICABILITY OF CONFLICTS_ 

91 

   - _ancestor_ ( _iroot_ , _p_ ) _∧ t ype_ ( _p_ ) _= dir_ : see Create-ParentDelete conflict 

   - _id_ ( _p_ , _n_ ) _= error_ : see Create-Create and Move-Create conflict 

   - Note: precondition _¬ancestor_ ( _iroot_ , _i_ ) is never violated, because the file system implementation makes sure to choose a unique _i_ 

- move: 

   - _t ype_ ( _u_ ) _= dir ∧ i ∈ list_ ( _u_ ): see Move-Delete and Move-Move (Source) conflict 

   - _t ype_ ( _v_ ) _= dir_ : see Move-ParentDelete conflict 

   - _id_ ( _v_ , _n_ ) _= error_ : see Move-Create, Move-Move (Dest) conflict 

   - _¬ancestor_ ( _i_ , _v_ ): see Move-Move (Cycle) conflict 

- deletefile, deletedir: 

   - _ancestor_ ( _iroot_ , _i_ ) _∧ i ∈ list_ ( _p_ ): see Move-Delete, or pseudo Delete-Delete conflict 

- edit: 

   - _ancestor_ ( _iroot_ , _i_ ): see Edit-Delete conflict. 

The above list covers all conflict types except for Edit-Edit. However, this conflict type is not a _syntactic_ but a _semantic_ conflict, thus no formally defined precondition of the _edit_ operation is violated. 

### **5.6 Applicability of conflicts** 

Table 5.4 illustrates which conflicts apply for each file system model from table 2.1 on page 16. The last row makes apparent that the number of conflicts increases with the complexity of the underlying file system definition. The more conflict types, the more elaborate the implementation of the file synchronizer needs to be, because it requires detection and resolution logic for each conflict. However, this insight should _not_ tempt a synchronizer developer to deliberately choose a simplified, internal file system model (such as _NH-D [BP98]_ or _NH-M_ [Mol+03]), when the underlying file system’s definitions is actually more complex<sup>7</sup> . Doing so degrades performance and usability, as explained in section 3.2 on page 41. While a heterogeneous synchronizer, such as the one we develop in this work, also chooses a simpler model ( _NH-MD_ vs. _H-All_ ), it does so for compatibility reasons rather than ease of implementation. 

### **5.7 Iterative conflict resolution** 

#### **5.7.1 Introduction** 

When resolving conflicts there are two challenges: 

1. It is possible that a set of specific files is affected by _multiple_ conflicts at the same time. Figure 5.5 illustrates a few examples. However, implementing resolution methods that consider multiple conflicts is futile, as the number of conflict combinations is (countably) infinite. 

2. Resolving a conflict may have bad side effects on other (still unresolved) conflicts or may require special logic in case executing the winner operation on the loser replica first requires propagation of other operations. 

All related academic works we presented in chapter 2 ignore these challenges. We approach them as follows: 

> 7The available implementations of the two cited works operate on top of file systems such as NTFS (Windows) or APFS (macOS), which are similar to the more powerful _H-All_ definition. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

92 

||NH-MD|NH-M|NH-D|NH-RD|H-All|NED-All|
|---|---|---|---|---|---|---|
|Create-Create|✓|✓|✓|✓|✓|✓|
|Link-Link|X|X|X|X|✓|X|
|Link-Unlink|X|X|X|X|✓|X|
|Create-Link|X|X|X|X|✓|X|
|Move-Link|X|X|X|X|✓|X|
|Edit-Edit|✓|✓|✓|✓|✓|✓|
|Move-Create|✓|✓|X<sup>A)</sup>|✓<sup>B)</sup>|✓<sup>C)</sup>|✓|
|Edit-Delete|✓|✓|✓|✓|✓|✓|
|Move-Delete|✓|X<sup>D)</sup>|X|✓<sup>B)</sup>|✓<sup>C)</sup>|✓|
|Move-Move (Source)|✓|✓|X|✓<sup>B)</sup>|✓<sup>C)</sup>|✓|
|Move-Move (Dest)|✓|✓|X|✓<sup>B)</sup>|✓<sup>E)</sup>|✓|
|Move-ParentDelete|✓|X|X<sup>F)</sup>|X|✓|X|
|Content-ParentDelete|✓|X|✓|✓|✓|X|
|Move-Move (Cycle)|✓|✓|X|X|✓|X|
|Node-typing|X|X|X|X|X|✓|
|No. of conflicts|10|7|4|8|14|8|



Table 5.4: Conflict applicability per file system 

Footnotes: 

- A) Becomes _Create-Create_ conflict. 

- B) ✓ if detected _move_ is actually a _rename_ operation. X otherwise. 

- C) ✓ only for directories. X otherwise. 

- D) Becomes _Move-Move (Source)_ conflict. 

- E) Applies to directories _and_ files. 

- F) Becomes _Create-ParentDelete_ conflict. 

File system model acronyms, as introduced in table 2.1 on page 16: 

- NH-MD: **N** o **h** ardlink support, support for **m** ove and **d** elete operations 

- NH-M: **N** o **h** ardlink support, support for **m** ove but not the delete operation 

- NH-D: **N** o **h** ardlink support, support for **d** elete but not move operation 

- NH-RD: **N** o **h** ardlink support, support for **r** ename and **d** elete operations 

- H-All: **H** ardlink support, support for _all_ operations 

- NED-All: **N** o ( **e** mpty) **d** irectory support, support for _all_ operations 

_5.7. ITERATIVE CONFLICT RESOLUTION_ 

93 



<!-- Start of picture text -->
S<br>1<br>            a: File<br>(a) Start situation for (b) and (c)<br>L R<br>1<br>1 -1<br>            b: File<br>            a: File                         b: File<br>Move ’a’ (P-ID: -1)<br>Edit         Create<br>Edit<br>Edit-Edit Move-Create<br>(b) Edit-Edit + Move-Create<br>L R<br>1 -1 -1 1<br>            b: File                         c: File                         b: File                         c: File<br>Move ’a’ (P-ID: -1)         Create         Create         Move ’a’ (P-ID: -1)<br>Move-Create Move-Move-Source Move-Create<br>(c) Move-Move (Source) + Move-Create + Move-Create<br><!-- End of picture text -->

Figure 5.5: Conflict combination examples 

Given the start situation shown in subfigure (a), (b) illustrates how a specific file can be affected by both an Edit-Edit and a Move-Create conflict, because on replica L the file was moved from ’a’ to ’b’, on R another ’b’ was created, and the content of the file was edited on both replicas. In (c) we see how a Move-Move (Source) conflict can be combined with two Move-Create conflicts, by creating a new file on each replica, at the same location that is occupied by the respective other replica’s move operation. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

94 



<!-- Start of picture text -->
L R<br>1 -1<br>            B’: Dir                         X: Dir<br>Move ’A’ (P-ID: -1)         Create<br>S 1<br>            A’: Dir<br>Move ’A’ (P-ID: -1)<br>1<br>            A: Dir         Move-Move-Source<br>(a) Initial situation (b) Conflict situation<br><!-- End of picture text -->

Figure 5.6: Move-Move (Source) conflict resolution with new directory 

1. We find all conflicts, sort them in a specific order (explained in section 5.7.2) and only solve _one_ conflict at a time, the one with the highest priority, followed by restarting the symchronization. 

2. When resolving a conflict of type _A_ - _B_ , where operation _A_ should win, rather than executing operations that merge the effects of _A_ and _B_ to yield a file system that looks as if operation _A_ had won, we instead only _undo_ (or modify) operation _B_ and then restart the synchronization. The consecutive synchronization iteration recognizes operation _A_ again, which no longer conflicts with _B_ , and eventually executes _A_ once all conflicts have been resolved. The concrete resolution approaches presented in section 5.5 use this approach. 

This iterative approach keeps our implementation logic simple and thus less prone to errors. The following examples illustrate the advantages of our approach: 

- For Move-Delete conflicts affecting a directory where the option is set to “delete wins”, undoing the move is better than immediately executing the delete operation, because the latter would ignore possibly existing Move-ParentDelete and Create-ParentDelete conflicts on child nodes of the affected dir. See subfigures (a)+(b) of figure 5.11 on page 100. 

- For Move-ParentDelete conflicts, just undoing the moves (rather than executing the Delete operation immediately) allows to correctly handle potential Create-ParentDelete conflicts for other child nodes of the deleted directory. 

- For Move-Move (Source) or Move-Move (Cycle) conflicts, undoing the move of the losing replica is better than trying to immediately execute the winning replica’s move operation, because the winning replica may have moved the object to a new directory which doesn’t exist on the losing replica yet. Extra logic would be required to catch such situations and compute the synchronization of such directories. An example can be seen in figure 5.6. 

#### **5.7.2 Conflict type sort order** 

Our implementation sorts all found conflicts in the following order: 

1. Move-ParentDelete 

_5.7. ITERATIVE CONFLICT RESOLUTION_ 

95 

2. Move-Delete 

3. Create-ParentDelete 

4. Move-Move (Source) 

5. Move-Move (Dest) 

6. Move-Create 

7. Edit-Delete 

8. Create-Create 

9. Edit-Edit 

10. Move-Move (Cycle) 

If, for any particular conflict type, there are more than one conflicts, these are solved by path as follows: 

- Move-ParentDelete, Create-ParentDelete: path of deleted directory node 

- Move-Delete, Edit-Delete: path of deleted node 

- Move-Move (Source): move origin path of the local node 

- Move-Move (Dest), Move-Move (Cycle): path of local move node 

- Move-Create: path of the create node 

- Create-Create, Edit-Edit: path of the local create/edit node 

Path sorting first compares the tree _depth level_ (e.g. ’x/y’ < ’a/b/c’ because the former is on level 2, the latter on level 3) . If two paths have equal depth, we use lexicographical order. 

The following analysis explains the rationale for using this particular sort order. While the presented conflict resolution approaches would work in _any_ order, a closer look reveals that resolving one conflict can have a large effect on other existing conflicts, such as eliminating them or turning one conflict type into another one. We examined a large set of conflict combination _pairs_ and tested different orders (including all variations for the resolution options, where available). The conflict type sort order introduced above is the result of building a _totally_ ordered list from the _partial_ priorities we determined between two conflict types. We write: 

- _type1 > type2_ if conflicts of type1 have a higher priority and should be resolved _before_ type2, for reasons as detailed below, 

- _type1 | type2_ if the order is irrelevant (as in: “it doesn’t matter which conflict type is solved first”), usually due to an independence between the affected nodes, parent nodes or names. 

The general metric for deciding whether _type1 > type2_ holds is that the _intention_ of both replica’s operations and the respective conflict resolution option should be _maximized_ . Figure 5.7 illustrates the maximization. The following table provides an overview. Each conflict combination is briefly explained in subsections 5.7.2.1 and 5.7.2.2. From the list of partial priorities it is trivial to construct the totally ordered priority list presented above. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

96 



<!-- Start of picture text -->
L R<br>3 2<br>1 2 1<br>S A: Dir B: Dir c: File             B: Dir             A: Dir<br>Move ’A/c’ (P-ID: 1) Delete<br>1 2 3<br>A: Dir B: Dir<br>c: File<br>Move ’A/c’ (P-ID: 1)<br>3<br>c: File Move-Move-Source Move-ParentDelete<br>(a) Start situation (b) Conflict situation<br>L R<br>2 3<br>2 1 1<br>B: Dir             c: File<br>B: Dir A: Dir A: Dir<br>Delete Move ’A/c’ (P-ID: 1)<br>3<br>c: File<br>(c) Solving Move-ParentDelete first<br>L R<br>2<br>1 2 1<br>B: Dir<br>A: Dir B: Dir A: Dir<br>Delete<br>3<br>3<br>c: File<br>c: File<br>Move ’A/c’ (P-ID: 1)<br>Move-ParentDelete<br>(d) Solving Move-Move (Source) first<br><!-- End of picture text -->

Figure 5.7: Move-ParentDelete > Move-Move (Source) 

The start situation shown in (a) is changed into a Move-Move (Source) + Move-ParentDelete situation in (b). (c) illustrates that when first solving the Move-ParentDelete conflict, the Move-Move (Source) conflict disappears. The intention of the operation of the local replica, moving ’A/c’ to ’B/c’, was denied because of the Move-ParentDelete resolution, but the intention of the remote replica, moving ’A/c’ to ’c’ prevails. In (d), we show what happens when instead solving the Move-Move (Source) conflict first, with the configuration option that the _local_ replica wins such types of conflicts. In this case, the Move-ParentDelete conflict remains, and once it is solved, _all_ moves shown in (b) will have been undone. We end up in a situation like the start situation, with the only difference that ’B’ was deleted. None of the move operations prevail. Consequently, we favor the approach of (c) over (d). 

97 

##### _5.7. ITERATIVE CONFLICT RESOLUTION_ 

|Type1|Type2|Type1 > Type2|
|---|---|
|Move-Move (Source) | Move-Move (Dest)<br>Move-ParentDelete | Edit-Edit<br>Move-Move (Source) | Edit-Edit<br>Move-Create | Edit-Edit|Move-Delete > Edit-Delete<br>Move-Delete > Move-Create<br>Move-ParentDelete > Move-Delete<br>Move-Delete > Move-Move (Dest)|
|Move-Move (Cycle) | Move-Move (Dest)|Move-Move (Source) > Move-Create|
|Move-ParentDelete | Create-ParentDelete<br>Move-Move (Cycle) | Move-Create|Move-Move (Source) > Create-Create<br>Move-ParentDelete > Move-Move (Source)<br>Move-Delete > Create-ParentDelete<br>Move-Delete > Create-Create|



##### **5.7.2.1 Same priority conflict combinations** 

- Move-Move (Source) | Move-Move (Dest): solving the conflicts in either order results in no difference for the final outcome. 

- Move-ParentDelete | Edit-Edit, Move-Move (Source) | Edit-Edit, Move-Create | Edit-Edit: for the correctness of synchronization, first moving a file and then replacing it is equivalent to first replacing and then moving it. 

- Move-Move (Cycle) | Move-Move (Dest): solving the conflicts in either order doesn’t result in any difference. Even though undoing the move as part of solving a Move-Move (Cycle) conflict dissolves the Move-Move (Dest) conflict, the end result is equal, because of the same resolution option configured for all Move-Move (Source/Dest/Cycle) conflicts. 

- Move-ParentDelete | Create-ParentDelete: solving the conflicts in either order results in no difference for the final outcome. 

- Move-Move (Cycle) | Move-Create: solving the conflicts in either order result in negligible difference. 

##### **5.7.2.2 Different priority conflict combinations** 

- Move-Delete > Edit-Delete: while the resolution order doesn’t matter in case both conflicts affect the same _file_ , an example scenario exists where the Move-Delete conflict affects a parent directory, and the Edit-Delete conflict affects a file inside that parent directory (i.e. the user who edited the file also moved a parent dir). In this case resolving the Move-Delete conflict first allows the file to stay in the directory. The opposite order would first move it to the root, which is unnecessary. 

- Move-Delete > Move-Create: An example is shown in figure 5.8. In case _delete_ is preferred over _move_ for Move-Delete conflicts, undoing the move also solves the Move-Create conflict. This produces fewer files with conflict-suffix. When choosing other configuration options, the conflict resolution order is irrelevant and produces the same outcome. 

- Move-ParentDelete > Move-Delete: as discussed in figure 5.4 this choice was made such that _move_ operations that take place within a directory that was deleted on the other replica will be discarded. 

- Move-Delete > Move-Move (Dest): An example is shown in figure 5.9. When resolving Move-Move (Dest) first, the Move-Delete conflict always remains, irrespective of the configured option. The end result always includes one file with a conflict suffix. If resolving the Move-Delete conflict first and the configured option prefers _delete_ to win, the _move_ operation is undone, the Move-Move (Dest) conflict disappears, an no file has the conflict suffix. If _move_ wins, the entry for one object is removed from the database and the Move-Move (Dest) becomes a Move-Create conflict., where one file with conflict suffix remains. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

98 



<!-- Start of picture text -->
L R<br>1 -1 1<br>            c: File                         c: File                         a: File<br>Move ’a’ (P-ID: -1)         Create         Delete<br>Move-Create Move-Delete<br><!-- End of picture text -->

Figure 5.8: Move-Delete > Move-Create 



<!-- Start of picture text -->
S<br>2 1<br>            b: File                                 a: File<br>(a) Start situation<br>L R<br>2 2 1<br>1<br>            x: File                         b: File                         x: File<br>            a: File<br>Move ’b’ (P-ID: -1)         Delete         Move ’a’ (P-ID: -1)<br>Move-Delete Move-Move-Dest<br>(b) Conflict situation<br><!-- End of picture text -->

Figure 5.9: Move-Delete > Move-Move (Dest) 

_5.8. PROOF OF TERMINATION_ 

99 



<!-- Start of picture text -->
L R<br>2 1<br>1<br>            x: File                         A: Dir<br>L             A: Dir                     Move ’A/x’ (P-ID: 1)         Delete<br>1 -1 2<br>            A: Dir                                 x: File                         x: File<br>Create         ParentDelete<br>2<br>            x: File                     Create-ParentDelete Move-Delete<br>(a) Start situation (b) Conflict situation<br><!-- End of picture text -->

Figure 5.10: Move-Delete | Create-ParentDelete 

- Move-Move (Source) > Create-Create and Move-Move (Source) > Move-Create: Undoing the move (as part of the Move-Move (Source) resolution) solves the respective other conflict, while the opposite order, solving the other conflict first, doesn’t. After the synchronization finished, this produces fewer files with a conflict suffix in their name, compared to solving the conflicts in the opposite order. 

- Move-ParentDelete > Move-Move (Source): When solving Move-ParentDelete first, the other conflict disappears, since the resolution undoes the move operation of one replica. See figure 5.7. 

- Move-Delete > Create-ParentDelete: These two conflict types can be intertangled in different ways. Figure 5.10 demonstrates an example where different resolution orders yield no difference. However, when the shared node of both conflicts is the deleted parent dir node, as shown in figure 5.11, resolving the Create-ParentDelete conflict first might produce a suboptimal result, because the created node would definitely be moved to the root directory, even if the configuration for the Move-Delete is “move wins”, which would have produced a result where the created node stays in the user-specified location. 

- Move-Delete > Create-Create: Regardless which resolution option is used for resolving the MoveDelete conflict, the final outcome will always contain just one file with a conflict-suffix. When resolving the Create-Create conflict first, two files may have the suffix. 

### **5.8 Proof of termination** 

#### **5.8.1 Overview** 

As explained in section 5.7 our conflict resolution algorithm iteratively solves one conflict at a time, until all conflicts are resolved. Here we demonstrate that our algorithm cannot get caught in an infinite loop, by closely examining the effects of resolving conflicts. We find that sometimes resolving a conflict introduces another conflict, or changes the conflict type of another existing one. Figure 5.12 provides an overview of these kinds of dependencies. An arrow that connects conflict _c_ with another conflict _c_<sup>_′_</sup> indicates that resolving _c_ introduces _c_<sup>_′_</sup> . If it were possible to find cycles<sup>8</sup> , our algorithm would never terminate. However, below we show that each cyclic dependency (see red arrows in figure 5.12) does _not_ 

> 8For instance, a cycle would exist if resolving conflict _c_ 1 introduces _c_ 2, whose resolution introduces _c_ 3, whose resolution introduces _c_ 4, etc. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

100 



<!-- Start of picture text -->
L R<br>1 2<br>1<br>S             A: Dir            Delete                     A: Dir                                 B_moved: Dir            Move ’A/B’ (P-ID: 1)<br>1 2 -1<br>            A: Dir                                 B: Dir                         new.txt: File<br>Delete         Create<br>2<br>            B: Dir                     Create-ParentDeleted Move-Delete<br>(a) Start situation (b) Conflict situation<br>S<br>1<br>S             B_moved: Dir<br>1 2 2<br>B_moved: Dir new-conflict-2018-10-24 08_44_24-dte4z.txt: File             new.txt: File<br>(c) Result (resolved Create-ParentDelete first) (d) Result (resolved Move-Delete first)<br><!-- End of picture text -->

Figure 5.11: Move-Delete > Create-ParentDelete 

cause a cycle. That is, the resolution of a conflict _c_ of type _t_ may cause another conflict _c_<sup>_′_</sup> of type _t_ , but resolving _c_<sup>_′_</sup> will not cause another _c_<sup>_′′_</sup> of type _t_ . 

We now present arguments for each conflict type in a separate section, where the following common terminologies hold: 

- The conflict indicated by the section title is denoted _c_ . 

- For conflicts Move-Move (Source), Edit-Edit, Move-Delete and Edit-Delete _c_ affects nodes _nX_ and _nY_ . 

- For all other conflicts _c_ affects nodes _nX_ and _mY_ . 

- By default, _nX_ is the loser node, unless stated otherwise. 

- _si t_ is the synchronization iteration where _c_ is detected. _si t +_ 1 is the consecutive sync iteration where _c_ is resolved. 

For each conflict we explain whether the resolution of _c_ itself introduces a new conflict _c_<sup>_′_</sup> in _si t +_ 1. This is typically not the case, but if it is, we clarify that it’s impossible that the resolution of _c_<sup>_′_</sup> would cause a conflict _c_<sup>_′′_</sup> in _si t +_ 2, etc., leading to a never-terminating loop. For conciseness of our proofs we define that the second and third parameter of _ancestor_ ( _snapshot_ , _n_ , _m_ ) may also be update tree _nodes_ rather than IDs. In other words _ancestor_ ( _dbsnapshot_ , _n_ , _m_ ) : _= ancestor_ ( _dbsnapshot_ , _n_ . _idb_ , _m_ . _idb_ ) and _ancestor_ ( _snapshotX_ , _n_ , _m_ ) : _= ancestor_ ( _snapshotX_ , _nX_ . _ID_ , _mX_ . _ID_ ). 

_5.8. PROOF OF TERMINATION_ 

101 



Figure 5.12: Conflict dependency graph 

#### **5.8.2 Move-Move (Source)** 

We apply _undo_move()_ to the loser node. Physically undoing the move of _nX_ may be possible or impossible, as explained in appendix A.7.1. 

##### **5.8.2.1 Undo move is possible** 

The object is moved only physically, the database is untouched. In _si t +_ 1 the _Move_ change-event for _nX_ is no longer detected. This solves _c_ . A close examination of every other conflict type shows that _undo_move()_ can only cause a Move-Move (Cycle) conflict _c_<sup>_′_</sup> to be detected in _si t +_ 1 in certain situations: 

1. Create-Create, Edit-Edit, Edit-Delete, Create-ParentDelete, Move-ParentDelete, Move-Move (Source): resolving _c_ does not cause new _Move_ , _Create_ , _Edit_ or _Delete_ change-events in _si t +_ 1. Thus, _c_<sup>_′_</sup> must have existed in _si t_ already. 

2. Move-Create, Move-Move (Dest): see point 1 - if another Move-Create / Move-Move (Dest) conflict _c_<sup>_′′_</sup> that already existed in _si t_ affects _nX_ with a _Move_ change-event, then resolving _c_ also resolves _c_<sup>_′′_</sup> . 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

102 

3. Move-Delete: see point 1 - also note that _nY_ cannot have the _Move_ and _Delete_ change-event at the same time. 

4. Move-Move (Cycle): it’s possible that the existence of _c_ for node _n_ introduces a Move-Move (Cycle) conflict _c_<sup>_′_</sup> in _si t +_ 1. For instance, _c_<sup>_′_</sup> might exist for two other distinct nodes _q_ and _r_ , as illustrated in figure 5.13. 

##### **5.8.2.2 Undo move is impossible** 

The object is moved to the root (with a unique conflict suffix) physically, and the DB entry is updated. In _si t +_ 1 the _Move_ change-event of _nX_ is no longer detected, which solves _c_ . The _Move_ change-event node for _nY_ is _still_ detected in _si t +_ 1. As a side effect this solves other conflicts such as Move-Create, Move-Move (Dest) in some situations. 

It is not possible that _undo_  move_ ( _nX_ ) applied in _si t_ introduces a Move-Move (Cycle) conflict _c_<sup>_′_</sup> in _si t +_ 1, given that undoing the move is impossible. Theorem 2 shows that no _c_<sup>_′_</sup> can exist for node _n_ and some other node _q_ . Theorem 3 shows that no _c_<sup>_′_</sup> can exist for two other distinct nodes _q_ and _r_ . 

**Lemma 2.** _Consider nodes n and q situated in an arbitrary relationship in si t , see figure 5.14 for possible configurations. After moving n to the root level, there cannot be a parent-child relationship between n and q in si t +_ 1 _, unless there already was one in si t . The proof is a trivial proof by exhaustion, by examining the effect of moving n to the root level for each configuration shown in figure 5.14._ 

**Theorem 2.** _When undo_move_ ( _nX_ ) _is used to resolve a conflict c in si t (conflict type of c can be MoveMove (Cycle), Move-Move (Source), Move-Delete, Move-ParentDelete) and undoing the move operation of nX is impossible, undo_move_ ( _nX_ ) _cannot introduce new a Move-Move (Cycle) conflict c_<sup>_′_</sup> _between n and some other node q in si t +_ 1 _._ 

_Proof._ by contradiction. For _c_<sup>_′_</sup> to be found only in _si t +_ 1 (see section 5.5.10 for details) _undo_  move_ ( _nX_ ) would need to cause oppositional parent-child relationships between _q_ and _n_ in the replicas in _si t +_ 1 (where these relationships did not exist in _si t_ yet, because if they had already existed in _si t_ , _c_<sup>_′_</sup> would have been detected in _si t_ already). However, lemma 2 demonstrates that moving _nX_ to the root cannot _introduce_ a new parent-child relationships between _nX_ and _qX_ in _si t +_ 1. 

**Lemma 3.** _Consider nodes n, q and r situated in an arbitrary relationship in si t , see figure 5.15 for possible configurations. After moving n to the root level, there cannot be a parent-child relationship between q and r in si t +_ 1 _, unless there already was one in si t . The proof is a trivial proof by exhaustion, by examining the effect of moving n to the root level for each configuration shown in figure 5.15._ 

**Theorem 3.** _When undo_move_ ( _nX_ ) _is used to resolve a conflict c in si t (conflict type of c can be MoveMove (Cycle), Move-Move (Source), Move-Delete, Move-ParentDelete) and undoing the move operation of nX is impossible, undo_move_ ( _nX_ ) _cannot introduce new a Move-Move (Cycle) conflict c_<sup>_′_</sup> _between two distinct nodes q and node r in si t +_ 1 _._ 

_Proof._ by contradiction: see proof for theorem 2. Now lemma 3 demonstrates that moving _nX_ to the root cannot _introduce_ a new parent-child relationships between _qX_ and _r X_ in _si t +_ 1. 

#### **5.8.3 Create-Create** 

The resolution physically renames _nX_ by appending a random conflict suffix. In _si t +_ 1 conflict _c_ is solved because _nX_ . _name̸ = mY_ . _name_ . It is not possible that the resolution introduces another conflict _c_<sup>_′_</sup> of type Create-Create, Move-Create or Create-ParentDelete, because: 

_5.8. PROOF OF TERMINATION_ 

103 



<!-- Start of picture text -->
S<br>1 2<br>            q: Dir                                 r: Dir<br>3<br>            n: Dir<br>(a) Initial situation<br>L R<br>1 3 3 2<br>            q: Dir                                 n_moved: Dir                         n_moved2: Dir                         r: Dir<br>Move ’q/n’ (P-ID: 1)         Move ’q/n’ (P-ID: 1)<br>2 1<br>            r: Dir             Move-Move-Source             q: Dir<br>Move ’r’ (P-ID: -1)         Move ’q’ (P-ID: -1)<br>(b) Detection of Move-Move (Source) conflict in  sit<br>L R<br>3<br>1 2<br>            q: Dir                                 r: Dir                                 n_moved2: Dir<br>Move ’q/n’ (P-ID: 1)<br>1<br>3<br>            n: Dir                                 q: Dir<br>Move ’q’ (P-ID: -1)<br>2<br>            r: Dir<br>Move ’r’ (P-ID: -1)<br>Move-Move-Cycle<br>(c) Detection of Move-Move (Cycle) conflict in  sit + 1<br><!-- End of picture text -->

Figure 5.13: Move-Move (Source) turns to Move-Move (Cycle) (undo possible) 

Subfigure (a) shows the database state. We construct a situation where we first build a Move-Move (Cycle) conflict _c_<sup>_′_</sup> between nodes _q_ and _r_ . Because _undo_  move_ ( _nX_ ) only modifies replica _X_ , _q_ and _r_ must have been in a parent-child relationship in replica _Y_ . We locally move _r_ into _n_ (which also means that _r_ is below _q_ , since _n_ is a child of _q_ ) and remotely move _q_ into _r_ . To disguise _c_<sup>_′_</sup> we move the intermediate node, _n_ , to the root on both replicas, but with slightly different names. This causes the Move-Move (Source) conflict _c_ for _n_ , hiding the parent-child relationship between _q_ and _r_ . When synchronizing the current situation, we detect only _c_ as shown in subfigure (b). After resolving _c_ in favor of replica _R_ we detect _c_<sup>_′_</sup> in _si t +_ 1 as shown in subfigure (c). 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

104 



Figure 5.14: Configurations of two nodes _q_ and _n_ 

We note that _dashed_ lines indicate that other nodes (not shown) may exist between the beginning and end of a dashed line. 



Figure 5.15: Configurations of nodes _q_ , _r_ and _n_ 

We note that _q_ and _r_ are interchangable. 

- Create-Create, Move-Create: _nX_ . _name_ was chosen at random. A conflict _c_<sup>_′_</sup> would require that another node _mY_<sup>_′_exists (with</sup><sup>_Create_or</sup><sup>_Move_change-event), where</sup><sup>_m_</sup> _Y_<sup>_′_.</sup><sup>_parent= mY_.</sup><sup>_parent ∧_</sup> _mY_<sup>_′_.</sup><sup>_name = nX_.</sup><sup>_name_, which is extremely unlikely due to the</sup><sup>_randomized_name suffix.</sup> 

- Create-ParentDelete: The existence of _mY_ with a _Create_ change-event implies that _mY_ . _parent_ must not have a _Delete_ change-event (a created object cannot exist in a deleted directory). Because our conflict resolution did not delete the physical path of _my_ . _parent_ or _nX_ . _parent_ , their changeevents must have stayed the same between _si t_ and _si t +_ 1 which means that it is impossible that a _c_<sup>_′_</sup> Create-ParentDelete conflict was introduced by our resolution algorithm. 

#### **5.8.4 Move-Create** 

Appending the conflict suffix to _nX_ ’s name has exactly the same effect as described in section 5.8.3, irrespective of whether _nX_ ’s change-event is _Move_ or _Create_ . 

#### **5.8.5 Move-Move (Dest)** 

Appending the conflict suffix to _nX_ ’s name has exactly the same effect as described in section 5.8.3. 

_5.8. PROOF OF TERMINATION_ 

105 

#### **5.8.6 Move-Move (Cycle)** 

Uses undo_move() of the loser node. 

##### **5.8.6.1 Undo move is possible** 

Similar to the argumentation of section 5.8.2.1, resolving a conflict _c_ via _undo_  move_ ( _nX_ ) may introduce another Move-Move (Cycle) conflict _c_<sup>_′_</sup> in _si t +_ 1. An example is shown in figure 5.16. 

##### **5.8.6.2 Undo move is impossible** 

The file is moved to the root (with conflict suffix) physically, and the DB entry is updated. In _si t +_ 1 this introduces a _Move_ change-event for node _nY_ . This can cause Move-Create, or Move-Move (Dest) follow-up conflicts, but no Move-Delete, Move-ParentDelete, Move-Move (Source) or Move-Move (Cycle) followup conflicts: 

- Move-Delete, Move-ParentDelete: the resolution of _c_ just performed a physical _move_ operation (and changed the parent and name of the object in the database). Nothing was deleted, thus no _Delete_ change-event could have been introduced in _si t +_ 1. 

- Move-Move (Source): because the resolution of _c_ moved _nX_ to the root on replica _X_ , no _Move_ change-event is detected in _si t +_ 1 for _nX_ , thus the introduction a Move-Move (Source) conflict for _n_ is impossible. Since the resolution does not affect _mX_ at all, the introduction of a Move-Move (Source) conflict for _mY_ is also impossible. 

- Move-Move (Cycle): see theorems 2+3. 

Note that it is generally impossible that our resolution causes an infinite loop, see theorem 4. 

**Theorem 4.** _Resolving Move-Move (Cycle) conflicts cannot cause an infinite loop due to the introduction of new Move-Move (Cycle) conflicts._ 

_Proof._ by contradiction: A necessary condition for finding a Move-Move (Cycle) conflict _c_ is that at least one directory node with a _Move_ change-event exists on _each_ replica. To cause an infinite loop, resolving _c_ would have to keep the number of _Move_ change-events for directories stable (or even increase it) on _both_ replicas. This is impossible, for two reasons: (1) the configured option of which replica should win conflict _c_ is always the same for each resolution process (e.g. “remote replica wins”); (2) the only effect of _undo_move()_ on the loser replica is the reduction of _Move_ change-events by 1. Consequently, once only Move-Move (Cycle) conflicts remain, the number of directory nodes on the losing replica with a _Move_ change-event involved in a Move-Move (Cycle) conflict must reach 0 eventually. 

#### **5.8.7 Edit-Delete** 

If the configured option is _delete wins_ : because our resolution of _c_ removes the affected file both physically and from the database, _n_ will no longer exist in _si t +_ 1. Because file nodes cannot have children, there cannot be any other side-effects on other nodes. 

If _edit wins_ : resolving _c_ removes the database entry of _n_ . Thus, the only consequence is that the physical file is detected with a _Create_ change-event in _si t +_ 1. This can cause a Create-Create or Move-Create conflict _c_<sup>_′_</sup> in _si t +_ 1, when a corresponding child node of _nY_ . _parent_ exists, with a _Create_ or _Move_ changeevent. It cannot cause a Create-ParentDelete conflict, because when we resolve _c_ we explicitly check for a Delete change-event in _nY_ . _parent_ and then move _nX_ physically to the root. For that move operation of _nX_ to cause a Create-ParentDelete conflict _c_<sup>_′_</sup> in _si t +_ 1 this would require that replica _Y_ ’s root was deleted, which is an exceptional situation in which no synchronization is executed anyway. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

106 



<!-- Start of picture text -->
S<br>1 2<br>            q: Dir                                 r: Dir<br>3 4<br>            m: Dir                                 n: Dir<br>(a) Initial situation<br>L R<br>3 4<br>2 1<br>            m: Dir                         n: Dir<br>            r: Dir                                 q: Dir<br>Move ’q/m’ (P-ID: 1)         Move ’r/n’ (P-ID: 2)<br>4 2 3<br>            n: Dir                         r: Dir                         m: Dir<br>Move ’r/n’ (P-ID: 2)         Move ’r’ (P-ID: -1)         Move ’q/m’ (P-ID: 1)<br>1<br>            q: Dir             Move-Move-Cycle Move-Move-Source Move-Move-Source<br>Move ’q’ (P-ID: -1)<br>(b) Detection of Move-Move (Cycle) conflict  c in  sit<br>L R<br>2 3 1 4<br>            r: Dir                                 m: Dir            Move ’q/m’ (P-ID: 1)                     q: Dir                                 n: Dir            Move ’r/n’ (P-ID: 2)<br>4 2 3<br>            n: Dir                                 r: Dir                         m: Dir<br>Move ’r’ (P-ID: -1)         Move ’q/m’ (P-ID: 1)<br>1<br>            q: Dir<br>Move ’q’ (P-ID: -1)<br>Move-Move-Cycle Move-Move-Source<br>(c) Detection of Move-Move (Cycle) conflict  c ′ in  sit + 1<br><!-- End of picture text -->

Figure 5.16: Move-Move (Cycle) introduces new Move-Move (Cycle) (undo possible) 

Subfigure (a) shows the database state. We construct a situation where we first build a MoveMove (Cycle) conflict _c_<sup>_′_</sup> between nodes _q_ and _r_ which is then disguised by moving _nX_ into _mX_ , which causes a Move-Move (Cycle) conflict _c_ between _m_ and _n_ . Subfigure (b) illustrates _c_ . After _undo_  move_ ( _nX_ ) was applied, conflict _c_<sup>_′_</sup> is discovered in _si t +_ 1, as illustrated in subfigure (c). 

_5.8. PROOF OF TERMINATION_ 

107 

#### **5.8.8 Edit-Edit** 

The only effect of resolving _c_ is that neither _nX_ nor _nY_ will have a _Edit_ change-event in iteration _si t +_ 1. All other nodes and their change-events are exactly as they were in _si t_ . Consequently, resolving _c_ cannot cause follow-up conflicts _c_<sup>_′_</sup> . 

#### **5.8.9 Move-Delete** 

If the configured option is _delete wins_ : 

- If node is a _file_ : See section 5.8.7 “delete wins”. 

- If node is a _dir_ : 

   - If undo_move() is possible: as described in section 5.8.2.1 a Move-Move (Cycle) follow-up conflict _c_<sup>_′_</sup> is possible. 

   - Otherwise, no other conflicts are caused: 

      - <sup>Move-Move (Cycle): see theorems 2+3.</sup> 

      - <sup>Move-Move (Source) does not make sense, because</sup><sup>_nY_is still deleted in</sup><sup>_sit+_1.</sup> 

      - <sup>Move-Move (Dest) and Move-Create are impossible due to the randomness of the con-</sup> flict suffix. 

      - <sup>Move-Delete and Move-ParentDelete are impossible, the resolution of</sup><sup>_c_just performed</sup> a physical _move_ operation (and changed the parent and name of the object in the database). Nothing was deleted, thus no _Delete_ change-event could have been introduced in _si t +_ 1. 

If the configured option is _move wins_ : the resolution described in appendix A.7.2 may cause the following follow-up conflicts _c_<sup>_′_</sup> : 

- Move-Move (Source): if orphans are found their paths are bent to the root in the database. This causes Move-Move (Source) conflicts which are automatically resolved in favor of the deletereplica. 

- Move-Move (Cycle): Let _q_ and _r_ both be orphan nodes below _n_ for which _ancestor_ ( _dbsnapshot_ , _q_ , _r_ ) _∧ ancestor_ ( _snapshotX_ , _q_ , _r_ ) _∧ ancestor_ ( _snapshotY_ , _r_ , _q_ ) holds in _si t_ . Resolving _c_<sup>_′_</sup> makes _q_ and _r_ independent in the database in _si t +_ 1, while not affecting the physical oppositional parent-child relationships. This causes a Move-Move (Cycle) conflict _c_<sup>_′_</sup> in _si t +_ 1. An example is shown in figure 5.17. 

- Create-Create: given that a Move-Create conflict _c_<sup>_′′_</sup> existed in _si t_ between _n_ and some other node _q_ (s.t. we detect _c_ and _c_<sup>_′′_</sup> in _si t_ ), then resolving _c_ transforms _c_<sup>_′′_</sup> to a Create-Create conflict _c_<sup>_′_</sup> in _si t +_ 1. 

- Move-Create: given that a Move-Move (Dest) conflict _c_<sup>_′′_</sup> existed in _si t_ between _n_ and some other node _q_ (s.t. we detect _c_ and _c_<sup>_′′_</sup> in _si t_ ), then resolving _c_ transforms _c_<sup>_′′_</sup> to a Move-Create conflict _c_<sup>_′_</sup> in _si t +_ 1. 

- Move-Delete: let _q_ be an orphan node of _n_ . Let node _r_ with _ancestor_ ( _dbsnapshot_ , _q_ , _r_ ) be deleted on replica _X_ - but _r_ is not modified, moved or deleted on replica _Y_ . Then we detect no Move-Delete conflict _c_<sup>_′_</sup> for _r_ in _si t_ . However, resolving _c_ will cause _r_ to be detected with a Move change-event in replica _Y_ in _si t +_ 1, which introduces _c_<sup>_′_</sup> . An example is shown in figure 5.18. However, as theorem 5 shows, this cannot cause an infinite cycle. 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

108 



<!-- Start of picture text -->
S<br>1<br>            n: Dir<br>2<br>            q: Dir<br>3<br>            r: Dir<br>(a) Initial situation<br>L R<br>3 1 1<br>            r: Dir                         n: Dir                         n_renamed: Dir<br>Move ’n/q/r’ (P-ID: 2)         Delete         Move ’n’ (P-ID: -1)<br>2 2<br>            q: Dir             Move-Delete             q: Dir<br>Move ’n/q’ (P-ID: 1)         ParentMoved<br>3<br>            r: Dir<br>ParentMoved<br><!-- End of picture text -->



<!-- Start of picture text -->
(b) Detection of Move-Delete conflict  c in  sit<br>L R<br>3 -1<br>            r: Dir                         n_renamed: Dir<br>Move ’r-suffix’ (P-ID: -1)         Create<br>2 2<br>            q: Dir                         q: Dir<br>Move ’q-suffix’ (P-ID: -1)         Move ’q-suffix’ (P-ID: -1)<br>3<br>            r: Dir<br>Move ’r-suffix’ (P-ID: -1)<br>Move-Move-Cycle Move-Move-Source Move-Move-Source<br>(c) Detection of Move-Move (Cycle) conflict  c ′ in  sit + 1<br><!-- End of picture text -->

Figure 5.17: Move-Delete introduces new Move-Move (Cycle) 

_5.8. PROOF OF TERMINATION_ 

109 



<!-- Start of picture text -->
L R<br>S<br>2 1 1<br>            q: Dir                         n: Dir                         n_moved: Dir<br>Move ’n/q’ (P-ID: 1)         Delete         Move ’n’ (P-ID: -1)<br>1<br>            n: Dir<br>3 2<br>            r: Dir             Move-Delete             q: Dir<br>2 Delete         ParentMoved<br>            q: Dir<br>3<br>3             r: Dir<br>            r: Dir                     ParentMoved<br>(a) Initial situation (b) Detection of Move-Delete conflict in  sit<br>L R<br>3 2 -1<br>            r-conflict: Dir                         q: Dir                         n_moved: Dir<br>Delete         Move ’q-conflict’ (P-ID: -1)         Create<br>2<br>            q: Dir<br>Move ’q-conflict’ (P-ID: -1)<br>S 3<br>            r: Dir<br>Move ’r-conflict’ (P-ID: -1)<br>2 3<br>q-conflict: Dir r-conflict: Dir Move-Delete Move-Move-Source<br>(c) Database state after resolving  c (d) Detection of new Move-Delete conflict in  sit + 1<br><!-- End of picture text -->

Figure 5.18: Move-Delete resolution yields a new Move-Delete conflict 

- Move-ParentDelete: let _q_ be an orphan node. Let nodes _r_ and _s_ with _ancestor_ ( _dbsnapshot_ , _q_ , _r_ ) _∧ ancestor_ ( _dbsnapshot_ , _r_ , _s_ ) both be deleted on replica _X_ but _r_ and _s_ are not modified, moved or deleted on replica _Y_ . Then we detect no Move-Delete or Move-ParentDelete conflicts _c_<sup>_′_</sup> for _r_ or _s_ in _si t_ . However, resolving _c_ will cause _r_ and _s_ to be detected with a Move change-event in replica _Y_ in _si t +_ 1, which introduces a Move-ParentDelete conflict _c_<sup>_′_</sup> for _r_ and _s_ and one Move-Delete conflict for _r_ and _s_ each. 

- Create-ParentDelete: as explained in figure 5.4 on page 87, if _nX_ is affected by a Move-Delete _c_ and a Move-ParentDelete conflict _c_<sup>_′_</sup> in _si t_ , resolving _c_ will transform the Move-ParentDelete conflict to a Create-ParentDelete conflict. 

- All other conflict types (Move-Move (Dest), Edit-Edit, Edit-Delete) are impossible, because the resolving _c_ does not introduce the corresponding events in _si t +_ 1. 

**Theorem 5.** _Resolving Move-Delete conflicts cannot cause an infinite loop due to the introduction of new Move-Delete conflicts._ 

_Proof._ by contradiction: As figure 5.18 demonstrates, the reason why resolving _c_ causes _c_<sup>_′_</sup> is because _ancestor_ ( _dbsnapshot_ , _n_ , _q_ ) _∧ ancestor_ ( _snapshot_ , _q_ , _r_ ) holds, _q_ and _r_ are orphan nodes, and _r X_ is deleted. _c_<sup>_′_</sup> then applies to _r_ . For _c_<sup>_′_</sup> to cause a new Move-Delete conflict _c_<sup>_′′_</sup> , such relationships would still need to exist, i.e., there would have to be child nodes of _r_ that can become orphaned. However, the 

_CHAPTER 5. CONSISTENCY AND CONFLICTS FOR FILE SYNCHRONIZERS_ 

110 

resolution of _c_ changes the database in such a way that all sub-nodes of _r_ are moved to the root level, i.e., they are in a _flat_ hierarchy, where no node below _r_ exists. For any _c_<sup>_′_</sup> caused by resolving _c_ , resolving _c_<sup>_′_</sup> is trivial. The configuration must still be “move wins”, thus, the entry for _r_ will be deleted from the database (to reconstruct _r_ in _si t +_ 2), without finding any orphan nodes below _r_ . 

#### **5.8.10 Move-ParentDelete** 

Conflict _c_ affects nodes _n_ and _m_ where _nX_ is moved into _mX_ and _mY_ is deleted. 

- If undo_move() is possible: Similar to the argumentation of section 5.8.2.1, resolving a conflict _c_ via _undo_  move_ ( _nX_ ) may introduce another Move-Move (Cycle) conflict _c_<sup>_′_</sup> in _si t +_ 1. 

- If undo_move() is impossible: The object is moved to the root (with conflict suffix) physically, and the DB entry is updated. In _si t +_ 1 this introduces a _Move_ change-event for node _nY_ . This can cause Move-Create, Move-Move (Dest), or Move-ParentDelete follow-up conflicts, but no Move-Delete, Move-Move (Source) or Move-Move (Cycle) follow-up conflicts: 

   - Move-Delete: the resolution of _c_ just performed a physical _move_ operation (and changed the parent and name of the object in the database). If _nY_ did not have a Move change-event in _si t_ this introduces one in _si t +_ 1. However, _nX_ was not deleted when resolving _c_ , thus no Move-Delete conflict _c_<sup>_′_</sup> can be introduced for _n_ . Because resolving _c_ did not delete anything, no _c_<sup>_′_</sup> for another node could have been introduced in _si t +_ 1. 

   - Move-Move (Source): because the resolution of _c_ moved _nX_ to the root on replica _X_ , no _Move_ change-event is detected in _si t +_ 1 for _nX_ , thus the introduction a Move-Move (Source) conflict for _n_ is impossible. Since the resolution does not affect _mX_ at all, the introduction of a Move-Move (Source) conflict for _mY_ is also impossible. 

   - Move-ParentDelete: resolving a conflict _c_ via _undo_  move_ ( _nX_ ) may introduce another Move-ParentDelete conflict _c_<sup>_′_</sup> in _si t +_ 1, where _c_<sup>_′_</sup> affects _nY_ and _qX_ , given that _ancestor_ ( _dbsnapshot_ , _q_ , _n_ ) _∧ ancestor_ ( _snapshotY_ , _q_ , _n_ ) holds. An example is shown in figure 5.19. However, as theorem 6 shows, this cannot cause an infinite cycle. 

   - Move-Move (Cycle): is impossible, see theorems 2+3. 

**Theorem 6.** _Resolving Move-ParentDelete conflicts cannot cause an infinite loop due to the introduction of new Move-ParentDelete conflicts._ 

_Proof._ by contradiction: As figure 5.19 demonstrates, resolving _c_ may introduce _c_<sup>_′_</sup> in case the following two conditions hold: (1) _undo_  move_ ( _nX_ ) is impossible, which causes a new _Move_ change-event in _nY_ in _si t +_ 1, and (2) the corresponding node of _nY_ . _parent_ already has a _Delete_ change-event in _si t_ on replica _X_ . For the resolution of Move-ParentDelete conflicts to cause an infinite loop, it would be necessary that resolving _c_<sup>_′_</sup> (which exists due to the new _Move_ change-event in _nY_ ) would be able to cause another Move-ParentDelete conflict _c_<sup>_′′_</sup> in _si t +_ 2. However, this is impossible, because when resolving _c_<sup>_′_</sup> _undo_  move_ ( _nY_ ) is always possible: _undo_  move_ ( _nY_ ) means to move _nY_ to the root level, with the unique conflict suffix (unique part of suffix not shown in figure 5.19 for brevity), which cannot fail, as the path must still be free in replica _Y_ . Consequently, none of the reasons for _undo_move()_ to be impossible do apply (see appendix A.7.1). 

#### **5.8.11 Create-ParentDelete** 

A Create-ParentDelete conflict _c_ affects nodes _nX_ and _m_ where _nX_ is a created object under _mX_ , while _mY_ is deleted. Because resolving _c_ simply moves created nodes to the root physically, with a random conflict-suffix (and without modifying the database), it is not possible to cause any follow-up conflict _c_<sup>_′_</sup> . The resolution of _c_ does not introduce any new change-events. The node _nX_ is detected with a _Create_ change-event in _si t +_ 1, just like in _si t_ . 

_5.9. CONCLUSION_ 

111 



<!-- Start of picture text -->
S L R<br>1 2 1 2<br>1 2             p: Dir            Delete                     q: Dir                                 p: Dir                                 q: Dir            Delete<br>p: Dir q: Dir<br>3 3<br>            n: Dir                         n: Dir<br>Move ’p/n’ (P-ID: 1)<br>3<br>n: Dir<br>Move-ParentDeleted<br>(a) Initial situation (b) Detection of Move-ParentDelete conflict in  sit<br>L R<br>3 2 1 2 1<br>            n-conflict: Dir                                 q: Dir                                 p: Dir            Delete                     q: Dir            Delete                     p: Dir<br>3<br>            n: Dir<br>Move ’n-conflict’ (P-ID: -1)<br>Move-ParentDeleted<br>(c) Detection of new Move-ParentDelete conflict in  sit + 1<br><!-- End of picture text -->

Figure 5.19: Move-ParentDelete resolution yields a new Move-ParentDelete conflict 

### **5.9 Conclusion** 

This chapter answers RQ3 which focuses on conflicts. We solve the problem of _identifying_ all conflicts by analyzing the preconditions of operation pairs, looking for cases where the effect of one operation violates the precondition of the other one. For our model _F_ we found a total of ten conflicts. By repeating the analysis for other file system models we found a dependency between conflicts and model, i.e., the number of conflicts increases with the number and complexity of file system operations. By combining all possible conflict pairs we found that _several_ conflicts may affect a specific object. To address this we contributed an _iterative_ conflict resolution approach that solves one conflict at a time, automatically without involving the user. It is configurable, as it offers several criteria to decide which operation wins or loses. It also avoids negative side effects for other files or directories that are not part of the conflict, by modifying the loser operation instead of the winner operation. To decide how conflicts are resolved, each part of our four-step framework improves contributions made by related academic works. For instance, we make conflict _awareness_ an integral part of our consistency philosophy, and our resolution is not arbitrary but based on a discussions and comparisons of different options. The resulting algorithm is simple to implement and terminates provably. 

113 

## **Chapter 6** 

# **Static synchronization** 

This chapter presents our synchronization algorithm called _Syncpal_ , which combines conflict handling (presented in chapter 5) and operation sorting (see section 4.2.7) into a high-level algorithm that solves state-based synchronization of two file system replicas. Parts of this chapter (as well as conflict related aspects from chapter 5) are also found in [She19]. To facilitate the explanation, we discuss the simplified case of _static_ synchronization where we assume that the file system is not changed by the user during synchronization. This assumption is reasonable for file synchronizers explicitly invoked by the user, because she can refrain from changing the file system during synchronization (and close all other applications that might cause changes). In practice, however, many file systems cannot be locked by a synchronizer. Thus, concurrent changes may invalidate an ongoing synchronization process. We refer to section 7.3 which introduces the _dynamic_ synchronization architecture of our implementation which can handle such concurrent changes. We start with an overview of the algorithm in section 6.1. The remaining sections explain the individual components introduced in section 6.1 and provide a conclusion. 

### **6.1 Algorithm overview** 

On a high level our algorithm is similar to the one presented in [BP98] where file system synchronization is broken down into a three-stage process - _update detection_ , _reconciliation_ and _propagation_ , which we briefly introduce in the following subsections. In figure 6.1, a flow chart illustrates how our static synchronization algorithm works in detail. The process starts at the top and follows the solid, bold arrows. Computations happen in blue, hexagonal shapes. Outputs of computations are colored green. Bold, dashed arrows associate computations with their output. Inputs are associated to computations using thin, dashed arrows. Decisions are yellow. To reduce clutter, computations that require the local/remote tree as input do not use incoming arrows, but instead have the tree icon as part of their shape. 

#### **6.1.1 Update detection** 

In the _static_ synchronization approach, update detection is triggered in regular intervals (whereas the _dynamic_ version triggers update detection based on file system activity). This involves generating _four_ different snapshots. File system (FS) snapshots are generated using APIs provided by the file system, see section 4.2.1, yielding _snapshotlocal_ and _snapshotremote_ . They represent the current state of the local and the remote replica respectively. To find changes since the last synchronization, _compute_  ops_ () (see section 4.2.3) does not compare those two snapshots, but compares the file system snapshot of each replica with the corresponding _database_ (DB) snapshots _dbsnapshotlocal_ and _dbsnapshotremote_ introduced in section 4.2.2, which contain the historic state of each replica at the point of the last synchronization. After computing 

_Olocal = compute_  ops_ ( _dbsnapshotlocal_ , _snapshotlocal_ ) 

and 

_Oremote = compute_  ops_ ( _dbsnapshotremote_ , _snapshotremote_ ) 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

114 



Figure 6.1: Static synchronization algorithm 

_6.2. RECONCILIATION_ 

115 

the operation sets are transformed into the corresponding in-memory _update trees_ for further processing, as described in section 4.2.4. These trees are given to the reconciliation phase. 

#### **6.1.2 Reconciliation** 

The goal of reconciliation, which is detailed further in section 6.2, is to compute a list of operations from the local and remote tree that will equalize both replicas once all operations have been executed. In some cases achieving this goal in just _one_ iteration (top to bottom in figure 6.1) is impossible. If conflicts are detected our algorithm instead first sorts the conflicts and solves the first one by generating a new list of one or more _already sorted_ resolution operations which it sends to the propagation phase. It then restarts automatically, as shown by the dark-red arrow in figure 6.1. 

#### **6.1.3 Propagation** 

In the propagation phase, see section 6.3 for more details, _non-conflicting_ operations are first sorted to avoid that operations scheduled earlier violate preconditions of later operations. If the sorting algorithm finds that operations depend on each other in a _cyclic_ relationship, the operation list is replaced by a single operation that breaks the cycle. The operation(s) are then executed, updating the physical replicas, the corresponding in-memory trees and the local state database. 

### **6.2 Reconciliation** 

The reconciliation phase computes the list of _Syncpal operations_ that need to be executed. It requires _updatetreelocal_ and _updatetreeremote_ to build them. Reconciliation takes place as follows: 

1. Iterate over all nodes in both trees and determine `conflicts` , which is a _sorted_ list of conflicts found in the tree (see section 5.5 and appendix A.6.1 for more information). 

2. If `conflicts` is non-empty, generate a list of operations that solve the _first_ conflict in `conflicts` . See section 5.7 for more information. Provide the list to the propagation phase. 

3. Otherwise, if no conflicts are found, iterate over all _unprocessed_ nodes<sup>1</sup> in a breadth-first approach and generate _Syncpal operations_ according to each node’s _change-events_ as follows: 

   - _Create_ : generate a create-operation. As with all other operations presented next, the _operation_ data structure contains the corresponding node from the respective update tree, allowing the propagation phase to query the node for the operation-specific information. In case of _Create_ , this information includes whether a file up-/download or a remote/local directory creation is requested, as well as the corresponding paths at the time of execution. If the node is affected by a _pseudo_ Create-Create conflict, the operation is provided with the _omit_ flag (see next section for clarification). Mark the node processed. If the node is affected by a pseudo Create-Create conflict, also mark the corresponding create node processed. 

   - _Move_ : generate a move-operation. If the node is affected by a _pseudo_ Move-Move (Source) conflict, the operation is provided with the _omit_ flag and the corresponding node is marked processed. Mark the node processed, unless the node also has an _Edit_ change-event, in which case mark the node as partially processed. 

   - _Edit_ : generate an edit-operation. If the node is affected by a _pseudo_ Edit-Edit conflict, the operation is provided with the _omit_ flag (and the corresponding node on the other replica is also marked processed). Mark node processed if the node has no move change-event (or has one but is already marked partially processed), otherwise mark it partially processed. 

> 1Before starting the iteration in step 3, we mark all nodes _unprocessed_ . The iteration algorithm returns the next unprocessed node using a breadth-first approach, considering the union of _updatetreelocal_ and _updatetreeremote_ . 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

116 

- _Delete_ : generate a delete-operation, which is given the update tree node and all its childnodes in case of a directory node (if any), because the propagation component needs to delete the paths of the dir and all its children from the database and file system. If the corresponding node of the other replica also has a delete flag, the operation is provided with the _omit_ flag. Mark the node and all its child-nodes processed. Also find the corresponding node in the update tree of the other side and mark it (and all its child-nodes) processed. 

- No change-events: don’t generate an operation. Mark the node processed. 

Step 2 illustrates our _iterative_ approach. This keeps the implementation complexity simple, at the expense of execution time. In a scenario where there are 2 files affected by different conflicts, and 10 other files not affected by any conflicts, the first iteration would generate (and execute) operations that solve the first conflict. Then the synchronization would restart and would detect the one remaining conflict and, say, 11 files not affected by any conflict<sup>2</sup> . The second iteration therefore generates and executes operations to mitigate the second conflict. Then the synchronization would restart again, no conflicts would be found and operations are generated for, say, 12 conflict-free files or dirs. 

### **6.3 Propagation** 

The goal of the propagation phase is to sort and execute the Syncpal operations provided by the reconciliation phase. The _Syncpal operation_ data structure contains all necessary information required to do so. Depending on the specific type of the operation, it will modify the relational database, the file system of the replicas, and the _updatetreelocal_ or _updatetreeremote_ objects. A _Syncpal operation_ structure is given the following information: 

- Both _updatetreelocal_ and _updatetreeremote_ . 

- Affected node (of the replica where the operation was _detected_ ). 

- Omit flag: boolean flag, if True, the operation only needs to change the database and omits changing the physical replicas. This is the case when the _same_ change was physically detected in both replicas (pseudo conflict) and only the database needs to be updated to avoid detecting this change again. 

- Corresponding node (optional, only if _omit_ flag is True) on the other replica. 

- Conflict information (optional), such as name of the conflict or affected paths, to be displayed to the user at the time of execution. 

As illustrated in figure 6.1, the operations are sorted in some cases, or remain unsorted in other cases. If the operations are _conflict resolution_ operations, they don’t need to be sorted, because the resolution algorithm already makes sure the operations are provided in an order which doesn’t violate the file system limitations. Otherwise, if no conflicts were detected, the provided list of operations needs to be sorted before execution, because operations are scheduled by the reconciliation phase in an order determined by the _breadth-first_ iteration of the trees. The propagation would otherwise fail to execute these operations due to the limitations of the file system APIs, see section 4.2.7.1 for more details. In case a _cycle_ is found while sorting, our algorithm generates a single operation that breaks this cycle and executes it. Otherwise the sorted list of operations must be in a valid, non-cyclic order, ready for propagation. 

For the remainder of this section, we first discuss the postconditions of each operation in more detail in section 6.3.1. In section 6.3.2 we describe how operations are sorted and cycles are resolved. In section 6.3.3 we describe and solve several challenges that arise during propagation, which exist mainly due to the lack of transactions on desktop file systems. 

> 2Assume that solving the first conflict was done in a way that it produces a single new file/dir that is no longer in conflict with the other replica 

_6.3. PROPAGATION_ 

117 

#### **6.3.1 Operation details** 

The following subsections detail how each respective Syncpal operation changes the file system, database snapshot and update trees. The first subsection is more verbose than the others, which omit some details already explained in the first one. 

##### **6.3.1.1 Create operation** 

A _create_ operation is based on a file system operation _create X_ ( _i X_ , _p X_ , _name X_ ) on replica _X_ (where _X_ is a placeholder for _local_ or _remote_ ) with _create_ : _= createdir ∨ create f ile_ , which was detected by _compute_ops()_ and caused the corresponding node _nX_ to be created in _updatetree X_ , with a _create_ change-event. If the _omit_ flag is set for the _create_ operation, it is also given the corresponding create node _nY_ in _updatetreeY_ . 

The execution of the create operation consists of three steps: 

1. If omit-flag is False, propagate the file or directory to replica _Y_ , because the object is missing there. 

2. Insert a new entry into the database (and database snapshot), to avoid that the object is detected again by _compute_ops()_ on the next sync iteration. 

3. Update the _update tree_ structures to ensure that follow-up operations can execute correctly, as they are based on the information in these structures. 

The implementation of each step requires the explanation of a few details: 

1. The path on replica _Y_ is computed as _pathY = path_ ( _npY_ )/ _nX_ . _name_ . This requires finding the corresponding parent node _npY_ in _updatetreeY_ via the _iddb_ of _nX_ . _parent_ , s.t. _nX_ . _parent_ . _iddb = npY_ . _iddb_ . The sorting algorithm described in section 4.2.7.1 guarantees that the _create_ operation of a parent directory is executed before the _create_ operation of any of its children. Therefore, _npY_ must exist in _updatetreeY_ . Once _pathY_ is computed, the corresponding _create_ operation can be executed. During execution, _iY_ and _lastmodi f iedY_ are returned by the file system APIs of replica _Y_ . Should the _omit_ flag be set, _iY_ and _lastmodi f iedY_ are already known from the corresponding node, i.e., _iY = nY_ . _ID_ and _lastmodi f iedY = nY_ . _lastmodi f ied_ . 

2. A new tuple _〈iddb_ , _name_ , _idlocal_ , _idremote_ , _lastmodi f iedlocal_ , _lastmodi f iedremote_ , _t ype〉_ is inserted into the database, where _X_ and _Y_ take the _local_ or _remote_ value respectively, with _name = nX_ . _name_ . The database component generates and returns _iddb_ . 

3. Set _nX_ . _iddb = iddb_ (with _iddb_ from step 2). If _nY_ was given (omit-Flag = True), also set _nY_ . _iddb = iddb_ . Otherwise create and insert a new node _nY_ below _npY_ with the corresponding values. 

##### **6.3.1.2 Edit operation** 

An _Edit_ operation is either based on a file system operation _editX_ ( _i X_ , _op_ ), or by operations 

##### _delete f ile X_ ( _i X_ , _p X_ ) _+ create f ile X_ ( _j X_ , _p X_ , _nX_ )[ _+editX_ ( _j X_ , _op_ )] 

where the last operation fills the new file with some content, and _nX = name_ ( _dbsnapshotX_ , _i X_ ). Either case causes the generation of a corresponding node _nX_ in _updatetree X_ with an _edit_ change-event. If the _omit_ flag is set to True for the _edit_ operation, it is also given the corresponding edit node _nY_ in _updatetreeY_ . 

The execution of the edit operation consists of three steps: 

1. If omit-flag is False, propagate the file to replica _Y_ , replacing the existing one. 

2. Update the database entry (and database snapshot), to avoid detecting the edit operation again. 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

118 

3. If the omit flag is False, update the _updatetreeY_ structure to ensure that follow-up operations can execute correctly, as they are based on the information in this structure. 

##### Further details: 

1. _pathY_ is computed as _path_ ( _nY_ ) where _nY_ is found in _updatetreeY_ using _nX_ . _iddb_ . 

2. The values _lastmodi f iedX_ and _lastmodi f iedY_ are always updated. If _idlocal_ or _idremote_ no longer match _nX_ . _ID_ or _nY_ . _ID_ because the file was actually deleted+created, _idlocal_ and _idremote_ are updated, too. 

3. _nY_ . _ID_ and _nY_ . _lastmodi f ied_ are updated with the values determined during propagation. 

##### **6.3.1.3 Move operation** 

A _move_ operation is based on a file system operation _move X_ ( _i X_ , _uX_ , _v X_ , _name X_ ) which was detected by _compute_ops()_ and caused the corresponding node _nX_ to be created in _updatetree X_ , with a _move_ change-event. The omit flag is set for the _move_ operation in case the same move was also detected in _updatetreeY_ (pseudo Move-Move (Source) conflict). 

The three execution steps are as follows: 

1. If omit-flag is False, move the object on replica _Y_ (where it still needs to be moved) from _uY_ to _vY_ , changing the name to _name X_ . 

2. Update the database entry (and database snapshot), to avoid detecting the move operation again. 

3. If the omit flag is False, update the _updatetreeY_ structure to ensure that follow-up operations can execute correctly, as they are based on the information in this structure. 

##### Further details: 

1. A _move_ ( _sourceY_ , _destY_ ) operation is executed with _sourceY = path_ ( _nY_ ), where _nY_ is found in _updatetreeY_ using _nX_ . _iddb_ , and _destY = path_ ( _nvY_ )/ _nX_ . _name_ , where _nvY_ is the node corresponding to _nX_ . _parent_ . 

2. The path _psource = path_ ( _dbsnapshotX_ , _i X_ ) and the parts of all sub-paths _q_ which start with _psource_ /... are replaced with path _pdest = path_ ( _dbsnapshotX_ , _nX_ . _parent_ . _dbid_ )/ _nX_ . _name_ . All other values in the database remain equal. 

3. If the omit flag is false, _nY_ is moved to be an immediate child of _nvY_ in _updatetreeY_ and is renamed to _nX_ . _name_ . 

##### **6.3.1.4 Delete operation** 

A _delete_ operation _o_ is based on one or more file system operations _delete_ ( _i X_ , _p X_ ) with _delete_ : _= deletedir ∨ deletefile_ , which were detected by _compute_ops()_ and caused the corresponding node _nX_ (and child-nodes) to be created in _updatetree X_ , with a _delete_ change-event. If the omit flag is set to True, _o_ is also given the corresponding delete node _nY_ in _updatetreeY_ . As discussed in section 6.2 delete operations are only generated for the _highest-level_ node with a _delete_ change-event, not for those who have parent nodes with a _delete_ change-event. If _nX_ is a _directory_ node, _o_ is also given the list _S_ of sub-nodes which need to be deleted from the database. 

The three execution steps are as follows: 

1. If omit-flag is False, delete the file or directory on replica _Y_ , because the object still exists there 

_6.3. PROPAGATION_ 

119 

2. Remove the entry from the database (and snapshot). If _nX_ is a directory node, also remove all entries for each node _n ∈ S_ . This avoids that the object(s) are detected again by _compute_ops()_ on the next sync iteration 

3. Update the _update tree_ structures to ensure that follow-up operations can execute correctly, as they are based on the information in these structures 

##### Further details: 

1. The delete operation uses _pathY = path_ ( _nY_ ) where _nY_ is found in _updatetreeY_ using _nX_ . _iddb_ . If _nX_ is a directory, the implementation needs to recursively delete all sub-objects. 

2. Let path _psource = path_ ( _dbsnapshotX_ , _i X_ ). Then all entries with path _p_ are removed from the database, where _p = psource ∨∃r ∈_ Σ<sup>_+_</sup> : _p = psource_ / _r_ , where Σ<sup>_+_</sup> _=_ Σ<sup>_∗_</sup> \{ _ϵ_ }. 

3. Remove _nX_ and _nY_ from the _update tree_ structures. 

#### **6.3.2 Breaking cycles in operation sorting** 

##### **6.3.2.1 Introduction** 

To reiterate the topic of operation sorting, we extracted order dependency rules between all file system operations in section 4.2.7.1 on page 56. By analyzing the operation preconditions we found a total of _eight_ rules. We tested all possible ways how two non-conflicting operations (detected on replica _X_ ) can be arranged in an order such that trying to apply them on replica _Y_ fails, because executing the first operation invalidates the precondition of the second one. In section 4.2.7.2 we elaborated which kinds of _cycles_ can be built from the ordering rules and presented the `sort_operations()` algorithm in algorithm 2 on page 58. In section 4.2.7.3 we discussed the concept of how to break cycles. 

So far, the presented algorithms and discussions focused on the scenario where changes occur only in one replica, being applied to the other, _unchanged_ replica. In this section we generalize the scenario to bi-directional synchronization, where operations are detected on both replicas, working with _Syncpal operations_ explained in section 6.3.1 instead of computed file system operations. Here we also discuss implementation details of `break_cycle()` . 

##### **6.3.2.2 From uni- to bi-directional synchronization** 

Section 4.2.7.3 on page 59 explains how `break_cycle()` works, in a setting where a set of file system operations detected in one replica are applied to another replica (where no operations are detected). It is straightforward to adapt the `fix_op_before_op()` functions introduced in section 4.2.7.1 (details in appendix A.4 on page 205), the `sort_operations()` algorithm (algorithm 2) and `break_cycle()` to our bi-directional synchronization algorithm introduced in this section, where non-conflicting changes are detected on _both_ replicas and operations are not pure file system operations but Syncpal operations. The reconciliation phase provides an unsorted set of operations _O = Olocal ∪Oremote ∪Op_ , which `sort_operations()` turns into a sorted list _O_<sup>¯</sup> . _Olocal_ and _Oremote_ contain operations affecting _only_ the respective replica, while _Op_ contains _pseudo_ -conflicting operations (Create-Create, Delete-Delete, Move-Move (Source), Edit-Edit) which affect _both_ replicas. We assume that there is no negative effect of some operation _oXi ∈ O X_ on any _oY j ∈ OY_ (and vice versa), i.e., the order of any _oXi_ relative to _oY j_ in _O_<sup>¯</sup> is irrelevant. We therefore adapt the comparison logic in the `fix_op_before_op()` functions to compare _all_ operation tuples _except_ for � _oXi_ , _oY j_ � and � _oYi_ , _oX j_ � pairs. Comparing such pairs would be computationally expensive. In section 6.3.2.5 we explain that this assumption is not true in all scenarios and elaborates on how we handle such cases. 

As discussed in section 4.2.7.3, every cycle must either contain a _delete_ operation _o_ (dependency rules 1 or 5) or a _move_ operation _o_ (dependency rules 6 or 2). Our implementation of `break_cycle()` takes 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

120 

the first cycle of Syncpal operations it finds and looks for a matching _delete_ operation _o_ in it. If no such _o_ can be found, a matching _move_ operation _o_ is found instead. 

We then generate a _resolution rename_ operation _or_ (see next section) and only execute _or_ , followed by restarting the synchronization. Let _nX_ be the node affected by _o_ , then _or_ is given _nX_ ’s corresponding node _nY_ which we find in _updatetreeY_ . _or_ is provided with flag _t = db_ if the _omit_ flag is set for _o_ (when _o ∈ Op_ ), or _t = both_ otherwise. Flag _t_ is explained below. 

##### **6.3.2.3 Resolution rename operation** 

A _resolution rename_ operation is a new type of Syncpal operation which changes only the _name_ of an object by appending a _random_ suffix to it. It is provided with a flag _t_ (target) where _t = db_ indicates to rename the object only in the database, whereas _t = both_ means to rename it both physically and in the database. Similar to other operations described in section 6.3.1, the paths on the physical file system are computed using the current state, i.e., a _move_ ( _source_ , _dest_ ) operation computes _source = path_ ( _nY_ ) and _dest = source+suffix_ . 

##### **6.3.2.4 Effect and examples** 

The key to our approach is that the information of the move/delete operation _o_ is _not_ lost when executing _or_ , because _or_ affects replica _Y_ , while _o_ affects replica _X_ . Sorting (and thus, resolving a cycle) is only done when _no_ conflicts are found. After restarting the synchronization, both _nX_ and _nY_ will still have the same change-events they had before executing _or_<sup>3</sup> . Operation _o_ will be detected again, but the sorting algorithm will no longer find the dependency rule 1, 5, 6 or 2 for _o_ that was found before executing _or_ . Due to the randomness of the suffix, executing _or_ cannot cause _new_ order dependencies either, because it’s extremely unlikely that the new object name (containing the suffix) would coincide with another existing object. Consequently, the cycle is broken and becomes a chain. Figures 6.2+6.3 provide further examples. 

3If _o_ is a delete operation, renaming the database entry (and the physical object on replica _Y_ if it still exists, i.e., if the omit flag is False) cannot possibly change the _Delete_ change-event in _nX_ , and won’t change the change-events in _nY_ either. The same holds if _o_ is a move operation. 

_6.3. PROPAGATION_ 

121 



<!-- Start of picture text -->
L R<br>-1<br>1<br>            A: Dir<br>            A: File<br>S Create<br>1<br>1<br>            subpath: File<br>            A: File                     Move ’A’ (P-ID: -1)<br>(a) Start situation (b) Cyclic situation<br>L R<br>-1<br>1<br>            A: Dir<br>            A-random-suffix: File<br>Create<br>move('A', 'A/subpath')<br>1<br>4 2             subpath: File<br>Move ’A-random-suffix’ (P-ID: -1)<br>createdir('A')<br>(c) Cyclic order dependen- (d) Broken cycle situation<br>cies<br><!-- End of picture text -->

Figure 6.2: Breaking a cycle, example 1 

Subfigure (a) shows the start situation. On the local replica, the operations _move(’A’,’ temp’)_ , _createdir(’A’)_ , _move(’temp’, ’A/subpath’)_ were performed, which are detected as _createdir(’A’)_ and _move(’A’, ’A/subpath’)_ , see subfigure (b). Subfigure (c) shows the cyclic order dependencies. Our algorithm breaks the cycle by appending a random suffix to A’s name, on the remote replica and in the database, which results in a cycle-free situation shown in subfigure (d), where only order rule 4 holds. 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

122 



<!-- Start of picture text -->
S<br>L R<br>1<br>1 2<br>            A: Dir                                 A: Dir                         A: Dir             1<br>            A: Dir<br>Delete         Move ’A/B’ (P-ID: 1)<br>2<br>2<br>            B: Dir                                 B: Dir<br>(a) Start situation (b) Cyclic situation<br>L R<br>1 2<br>1<br>            A-rand-suffix: Dir                         A: Dir<br>            A-rand-suffix: Dir<br>Delete         Move ’A-rand-suffix/B’ (P-ID: 1)<br>move('A/B', 'A')<br>2<br>1 3<br>            B: Dir<br>deletedir('A')<br>(c) Cyclic order de- (d) Broken cycle situation<br>pendencies<br><!-- End of picture text -->

Figure 6.3: Breaking a cycle, example 2 

Subfigure (a) shows the start situation. On the local replica, the operations _move(’A’,’ temp’)_ , _move(’temp/B’, ’A’)_ , _deletedir(’temp’)_ were performed, which are detected as _deletedir(’A’)_ and _move(’A/B’, ’A)_ , see subfigure (b). Subfigure (c) shows the cyclic order dependencies. Our algorithm breaks the cycle by appending a random suffix to A’s name, on the remote replica and in the database, which results in a cycle-free situation shown in subfigure (d), where only order rule 3 holds. 

##### **6.3.2.5 Handling cross-replica operation dependencies** 

In section 6.3.2.2 we stated the assumption that the order of an operation _oXi ∈ O X_ relative to some operation _oY j ∈ OY_ is irrelevant. Dependencies between operations detected in different replicas are typically detected as real _conflicts_ , but our algorithm already resolved these conflicts once function `sort_operations()` is called. However, our randomized testing procedure presented in section 8.1.3 on page 146 found several counter examples where the precondition _¬ancestor_ ( _i_ , _v_ ) of operation _move X_ ( _i X_ , _uX_ , _v X_ , _name X_ ) (that is, an object cannot be moved to a target below itself) is violated by another _moveY_ ( _iY_ , _uY_ , _vY_ , _nameY_ ) operation. A requirement is that each move operation targets a different object, and both objects are _directories_ . An example is shown in figure 6.4, where subfigures (a+b) demonstrate the scenario and subfigure (c) illustrates the detected order dependencies, found for each replica. The final result of `sort_operations()` is 



and the propagation of these operations to the other replica will fail, because the precondition of the first move operation is violated. The remote replica does not allow to move the node with _idb =_ 1 into 

_6.3. PROPAGATION_ 

123 



<!-- Start of picture text -->
L R<br>4<br>2 1<br>            n: Dir<br>            t: Dir                                 n: Dir<br>Move ’t/q/e’ (P-ID: 3)<br>S<br>1 2<br>3<br>1 2             g: Dir                         w: Dir<br>            q: Dir<br>n: Dir t: Dir Move ’n’ (P-ID: -1)         Move ’t’ (P-ID: -1)<br>3<br>3<br>            q: Dir<br>q: Dir ParentMoved<br>4<br>4<br>            e: Dir<br>e: Dir ParentMoved<br>(a) Start situation (b) Changes by local and remote replica<br>Local replica: Remote replica: Local replica:<br>move(4, 3, -1, 'n') move(4, 3, -1, 'n')<br>move(2, -1, 1, 'w')<br>6 6 8<br>move(1, -1, 4, 'g') move(1, -1, 4, 'g')<br>(c) Incomplete order dependencies (d) Cyclic order dependencies<br><!-- End of picture text -->

Figure 6.4: Example for cross-replica operation dependencies 

the node with _idb =_ 4. This situation is _not_ a _Move-Move (Cycle)_ conflict. It can be solved by first applying the move operation detected in the remote replica to the local one (i.e. executing _moveR_ (2, _−_ 1,1,<sup>_′_</sup> _w_<sup>_′_</sup> ) locally) followed by restarting the synchronization. In the next iteration `sort_operations()` will find a _cycle_ for the the operations with _idb =_ 1,4, as illustrated in subfigure (d). After breaking the cycle, the remaining move operations can be successfully propagated. 

The underlying reason for the incorrect behavior of `sort_operations()` is that some functions `fix_op_before_op()` called for some operation pair involving operation _oXi_ require information from the _database_ snapshot, which changes after operations from _OY_ are executed. In the example shown in figure 6.4 the function `fix_move_before_move_parent_child_flip()` (for order dependency rule 8) presented in appendix A.4 on page 205 fails to detect the necessary database condition<sup>4</sup> , because it can only be detected once the move operation for node with _idb =_ 2 has executed. We analyzed similar dependencies in other `fix_op_before_op()` functions by hand but found no related issues. 

We now present our approach for solving this issue, referred to as _Operation reshuffling_ . 

This approach assumes that the only side effect that exists between two operations _oXi_ and _oY j_ is the one we found in figure 6.4, affecting `fix_move_before_move_parent_child_flip()` . It assumes that 

> 4Variable `is_y_below_x_in_db` would have to be true, but is detected as false. 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

124 

our analysis of all other `fix_op_before_op()` functions was flawless and that there are not any other side effects we overlooked. 

The _Operation reshuffling_ approach gets its name because it reshuffles the order of the scheduled operations, lazily. It defers the execution of those impossible move-directory operations to a later synchronization iteration, executing those first which _are_ possible. It performs a simple analysis of `sorted_ops` once it is available. Consider the pseudo-code of `sort_operations()` shown in algorithm 2 on page 58. We replace the last line, `return sorted_ops` , with the following code: 

##### reshuffled_ops = fix_impossible_first_move_op ( sorted_ops ) **i f** reshuffled_ops : 

**return** reshuffled_ops 

**return** sorted_ops 

Function `fix_impossible_first_move_op(sorted_ops)` analyzes whether the first operation in `sorted_ops` is an impossible move-directory operation. If yes, it returns a list of operations to execute instead of `sorted_ops` (more details below). If not, it returns nothing. Of course there might be an operation _oXi_ in `sorted_ops` which is an impossible move-directory operation, but _oXi_ is not the _first_ operation. Determining whether _oXi_ is (im)possible is not easily possible, unless all operations in `sorted_ops` were simulated first, which is computationally expensive. Instead our approach deliberately ignores this issue. Instead, it restarts synchronization whenever it detects that a move-directory operation _oXi_ would fail (at the point when it is executed). After restarting the synchronization _oXi_ will come _first_ in the next synchronization iteration, and `fix_impossible_first_move_op()` will handle it. We now explain the details of `fix_impossible_first_move_op()` and relate its steps to the example from figure 6.4: 

1. Check whether the first operation _o_ 1 in `sorted_ops` is a move-directory operation. If not, return. 

2. Compute the paths `source` and `dest` necessary to execute _o_ 1 on the other replica, say, _Y_ , and check whether _o_ 1 is impossible, which is the case if `dest` starts with `source + ’/’` . If _o_ 1 is possible, return. 

   - In our example _o_ 1 _= moveL_ (<sup>_′_</sup> _n_<sup>_′_</sup> ,<sup>_′_</sup> _n_ / _w_ / _q_ / _e_<sup>_′_</sup> ) is found to be impossible. On replica _R_ the node with _idb =_ 1 cannot be moved into the one with _idb =_ 4, because 4 is below 1. 

3. From our above analysis we know that _o_ 1 _= move_ ( _i_ 1, _u_ 1, _v_ 1, _n_ 1) is impossible because there must be at least one other move-directory operation _oY j_ that affects a node situated _between_ the source node _i_ 1 and target node _v_ 1. Traverse _updatetreeY_ , starting from the corresponding destination parent directory node _v_ 1, up to the corresponding source node _i_ 1. Build a list _l_ of all movedirectory nodes _oY j_ found along the way. 

   - In our example, we traverse the _remote_ update tree, from destination parent directory node _v_ 1 _=_ 4, up to the source node _i_ 1 _=_ 1. We find _l =_ [ _moveR_ (2, _−_ 1,1,<sup>_′_</sup> _w_<sup>_′_</sup> )]. 

4. Iterate over all operations in _l_ and determine _o f ir st_ which is the operation in _l_ which comes earliest in `sorted_ops` . 

5. Return a list `reshuffled_ops` that is a filtered version of `sorted_ops` . It contains only those operations in `sorted_ops` that affect replica _Y_ (or _both_ replicas for operations whose omit-flag is true), up to (including) _o f ir st_ . 

   - In our example, `reshuffled_operations` = _l_ . The first two operations from `sorted_ops` , _moveL_ (1, _−_ 1,4,<sup>_′_</sup> _g_<sup>_′_</sup> ), _moveL_ (4,3, _−_ 1,<sup>_′_</sup> _n_<sup>_′_</sup> ), are excluded. 

_6.3. PROPAGATION_ 

125 

Syncpal then executes only the operations in `reshuffled_ops` and restarts the synchronization. In our example, _moveR_ (2, _−_ 1,1,<sup>_′_</sup> _w_<sup>_′_</sup> ) is executed, updating the local replica and the database. The next iteration will find and break the cycle as depicted in subfigure 6.4d. The consecutive iteration will achieve convergence. 

Our approach is easy to implement and has minimal impact on run-time performance. Exhaustive testing using _randomly_ generated operation sequences (see section 8.1.3 on page 146), which also produced the example in figure 6.4, found no scenarios that exhibit any side effects other than the one we just discussed. This strengthens our degree of certainty that the assumption we made at the beginning of this section is correct. 

##### **6.3.2.6 Proofs of termination** 

**Theorem 7.** _The_ _`break_cycle()` operation does not cause new cycles._ 

_Proof._ by contradiction: To cause new cycles, executing the resolution rename operation _or_ in sync iteration _si t_ , which breaks the cycle (see section 6.3.2.3), would need to cause at least one new cycle in _si t +_ 1. In general, each cycle must contain one of the reordering situations 6, 2, 1 or 5. Operation _or_ dissolves one of these situations, by renaming some node _nY_ to a _unique_ , un-used name _ω_ , turning the cycle into a chain. _ω_ is impossible to cause a new situation 6, 2, 1, 5 in _si t +_ 1, because that would require that a _move_ or _create_ operation exists in _si t +_ 1 where the affected node’s name matches _ω_ , which is impossible. 

**Theorem 8.** _The execution of the operations in_ _`sorted_ops` returned by_ _`sort_operations()` cannot fail._ 

_Proof._ by contradiction: For the execution to fail, it would require that `sorted_ops` _=_ �..., _oi_ ,..., _o j_ ,...� where executing _oi_ breaks the execution of _o j_ . Because the `fix_op_before_op()` methods ensure that order dependencies of operations on the same replica (or pseudo-conflicting operations) are dealt with, _oi_ must have been detected only in replica _X_ and _o j_ only in replica _Y_ . For example, if some _oYi_ is placed before some _oXi_ in `sorted_ops` , such that the execution of _oYi_ invalidates _oXi_ , then there would need to be some kind of a dependency between _oYi_ and _oXi_ . However, this is not possible, because to have a dependency between _oYi_ and _oXi_ would require a (real or pseudo) _conflict_ that involves the corresponding two nodes, or involve the cross-replica move operation depedency discussed in section 6.3.2.5 on page 122. However, real conflicts are already solved at the point where `sort_operations()` is called, `sort_operations()` does consider pseudo conflicts, and the cross-replica move operation depedency has also been handled as discussed above. 

#### **6.3.3 Operation execution challenges** 

During the execution of Syncpal operations, our implementation needs to take care of several details to allow for a successful execution of all operations. These are explained in the following subsections. 

##### **6.3.3.1 Concurrency handling** 

As outlined at the beginning of this chapter, our algorithm treats file systems as _static_ , assuming that the user does not perform any operations during the phases _update detection_ , _reconciliation_ and _propagation_ . In practice, file systems are _dynamic_ , and we cannot ask the user to stop all activities during these phases, especially since the synchronizer is active in the _background_ . As a result, the generated operations may become outdated, and attempting to execute them could have disastrous effects. 

Our solution to minimize the chance that such problems occur is to query the file system for meta-data right before performing an operation, to ensure that the operation is still valid. The returned metadata is compared to the data available from the snapshots and update trees that were generated during update detection. The specific queries depend on the operation: 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

126 

- Create file, Move: 

   - _ID_ and _lastmodified_ meta-datum of the source object 

   - _ID_ of the destination’s parent directory 

   - Verify that the name is not occupied in the destination parent directory 

- Edit (file): 

   - _ID_ and _lastmodified_ meta-datum of the source object, on _both_ replicas 

- Create directory 

   - _ID_ of the destination’s parent directory 

   - Verify that the name is not occupied in the destination parent directory 

- Delete file: _ID_ and _lastmodified_ meta-datum of the target object 

- Delete dir: _ID_ of the target object 

If any of the checks fail, the operation as well as all other operations are aborted and a new synchronization iteration is triggered. Note that even though our _dynamic_ synchronizer implementation (see section 7.3) automatically detects user-made changes to the file system in real time, we still perform most of these checks. The reason is that, in practice, several hundred milliseconds may pass before a user-made change is registered by our dynamic implementation, which may already be a too long time period. 

##### **6.3.3.2 File system tricks to emulate atomicity** 

There are two types of operations that may take long and whose effect may be atomic<sup>5</sup> in some file systems but not in others: 

1. File transmissions (upload or download to/from the server), due to limited transmission speed, 

2. Deleting non-empty directories, because some file system APIs only offers methods for the deletion of files and _empty_ dirs, thus all dirs have to be traversed and all their children have to be deleted first (recursively). In some cases it’s even impossible to delete some sub-files or dirs due to permission problems, e.g. caused by UNIX permission bits or prohibitive ACLs (Access Control Lists). On Windows systems, a file that is opened by another program (even just for reading) cannot be deleted either. 

To avoid that the file system, in particular its namespace, is left in an inconsistent state during or after synchronization, or after interrupting an operation, we apply a few tricks to make the effect of these operations to appear to be atomic to the user and the update-detection components. 

1. To avoid that the user sees _partially_ downloaded files in the synchronized namespace on a local disk, Syncpal downloads files into an invisible, temporary directory (which is on the same disk volume as the file’s final destination). Only once the download is complete it is moved to its final destination. Such move operations, as long as they are on the same disk volume, are atomic and take few milliseconds to complete. 

2. To avoid that a recursive delete operation of a directory only succeeds partially, we instead _move_ the targeted directory to the aforementioned invisible, temporary directory. This move operation succeeds (or fails) atomically and thus it atomically alters the synchronized file system namespace. After the move has completed, the directory and its content can be deleted in parallel to the execution of other operations. 

5We refer to _atomicity_ as known from database systems, see also section 1.3.4 of [EN15]. 

_6.4. TERMINATION AND CORRECTNESS_ 

127 

##### **6.3.3.3 Path computation** 

Operations need to use the correct, up-to-date paths on the replica to which the operation should be applied. An example is shown in figure 6.5. The _operation_ data structure the propagator works with is given the _node_ of the respective _update tree_ where the operation was detected. We need to compute the path on the _update tree_ of the other replica using the `cid()` function from appendix A.5.2. 



<!-- Start of picture text -->
S<br>1<br>            D: Dir<br>2<br>            F: File<br>(a) Start situation<br>L R<br>2 1<br>1<br>            A: File                         E: Dir<br>            D: Dir<br>Move ’D/F’ (P-ID: 1)         Move ’D’ (P-ID: -1)<br>2<br>            F: File<br>ParentMoved<br>(b) Changes from local and remote replica<br><!-- End of picture text -->

Figure 6.5: Path computation during propagation 

Given the initial situation shown in subfigure (a), the local replica performed _move(’D/F’, ’A’)_ while the remote replica performed _move(’D’, ’E’)_ , as shown in subfigure (b). Due to the lexicographical sorting and breadth-first iteration of _update tree_ nodes, the operation scheduled first is the move of the _local_ replica. To successfully execute it, the corresponding remote source path needs to be changed from _’D/F’_ to _’E/F’_ (because _’D/F’_ no longer exists due to the _move(’D’, ’E’)_ operation _already_ applied to the remote replica). 

### **6.4 Termination and correctness** 

It is an important property for our algorithm to be provably correct and to terminate in finite time. In our case, _correctness_ is not easily formulated, because of the large amount of possible conflicts ( _ten_ different types) and the different resolution options we provide for each conflict to make the implementation usable in practice. The combination of different resolution options leads to an exponential explosion of formal notions of correct behavior. 

_CHAPTER 6. STATIC SYNCHRONIZATION_ 

128 

We instead show correctness _indirectly_ . We demonstrate that each operation our algorithm generates and executes is both meaningful (because it keeps the _intention_ of the user as much as possible) and brings the algorithm one step closer to _termination_ . Our algorithm _terminates_ if both replicas have _converged_ . This is the case once the _local_ replica matches the _remote_ one. By the law of transitivity this is the case if the _local_ and _remote_ replica’s snapshots each match their corresponding _database_ snapshot. We define termination as follows: 

**Definition 1.** Let _X_ , _Y_ be identifiers for two distinct replicas where _snapshotX_ and _snapshotY_ reflect the corresponding current file system state, and _dbsnapshotX_ and _dbsnapshotY_ reflect the file system state known to the synchronizer after the last synchronization (the _database_ snapshot). The _compute_ops()_ algorithm presented in section 4.2.3 computes _divergences_ between these replicas. Our synchronization algorithm _sync_ ( _X_ , _Y_ ) terminates iff _compute_  ops_ ( _dbsnapshotX_ , _snapshotX_ ) _= compute_  ops_ ( _dbsnapshotY_ , _snapshotY_ ) _=_ {}. 

When this situation occurs during update detection, no operations will be generated, and thus there won’t be any conflicts, cycles or any non-conflicting operations to propagate. 

We provided proofs of termination in different sections of this work. To reiterate, we showed that: 

- As long as there are _conflicting_ operations, the operations we generate to resolve a conflict cannot cause an infinite loop (i.e., a resolution operation won’t generate infinitely many follow-up conflicts). Thus the conflict-detection and resolution phase will terminate, because the number of conflicts is limited by the number of detected operations, which depends on the number of files and directories, which is finite. More details are found in section 5.8 on page 99. 

- The part of our algorithm that breaks cycles cannot create infinite loops because the generated breaking operations won’t create new cycles. Similarly, the number of cycles is limited by the number of detected operations, which depends on the number files and directories, which is finite. See theorem 7 in section 6.3.2.6. 

- An iteration of our algorithm that finds neither conflicts nor cycles and has thus sorted the operations must terminate, because the execution of each operation _o_ cannot fail. See theorem 8 in section 6.3.2.6. 

By showing that each step of our algorithm does the “correct step” w.r.t. our consistency philosophy, the consequence is that our overall algorithm is _correct_ . 

### **6.5 Conclusion** 

Our Syncpal algorithm integrates the iterative nature of conflict resolution and operation sorting (presented earlier) into an overall algorithm that builds on state-based update detection, efficient reconciliation using update trees and propagation using Syncpal operations. Our operation sorting approach designed for uni-directional synchronization required some adaptations to work in a bi-directional setup. Each of Syncpal’s steps are designed to be minimal (i.e., each step executes as few operations as possible to solve a specific part of the problem) and atomic (by avoiding long-lasting transactions). Therefore the user can interrupt the synchronization at any time without causing side effects. While the theoretical drawback of our iterative algorithm is the increased run-time in those scenarios where _many_ iterations are required, we found that this is not problematic in practice. Either the synchronization is triggered often, either manually or because the implementation uses hybrid update-detection that triggers on file system changes. In this case one iteration typically suffices, with only 1-2 operations being propagated. Or the synchronization was triggered rarely, in which case the user expects synchronization to take a considerable amount of time anyway. 

While our previous discussions are tailored to the file system model _F_ , our approach is generic. It can be adapted to other models comprised of different operations, preconditions and invariants. While a file synchronizer developer needs to repeat finding all conflicting operations and order dependency rules, she can reuse all other parts of Syncpal without further modifications. 

129 

## **Chapter 7** 

# **Implementation** 

A primary goal of this thesis is not only the design but also the implementation of our Syncpal algorithm. In this chapter we present _BSync_<sup>1</sup> , a tool geared towards end-users of Windows and macOS, with _production_ level quality. It uses a _dynamic_ version of Syncpal presented in section 7.3. BSync runs in the background and detects and propagates file system operations in near real-time. It allows users to configure several different folder pairs, residing on heterogeneous storage systems, and have them synchronized in parallel. BSync provides a graphical user interface (GUI) that offers conflict awareness and inspection. To better understand how (and how often) conflicts affect users, BSync collects statistics about synchronization, which is presented in section 8.3 in detail. 

We start by presenting the different iterations of development in section 7.1. Section 7.2 then provides an overview of the software architecture and describes its components and their interaction. The core novelty of BSync is _dynamic_ synchronization presented in section 7.3, which automatically triggers synchronization based on user activity and handles concurrent user activity during synchronization. In section 7.4 we describe our userbase as well as how BSync is packaged and deployed to users. We conclude with lessons learned in section 7.5, which describes requirements and problems that surfaced only with the extensive help of BSync’s users. 

### **7.1 Iterations of development** 

#### **7.1.1 Background** 

The development of BSync and Syncpal did _not_ start as an academic PhD thesis. In late 2014 a new work package was created at Fraunhofer FIT for the _EnArgus_ project. EnArgus<sup>2</sup> is a platform built on top of BSCW [BHT97] and we were in charge of the work package to create a file synchronizer compatible with BSCW. To avoid building another proprietary solution (tailored to BSCW) we modified the requirements and decided to build a solution with support for heterogeneous file systems. From 2015 to 2016 BSync’s development was funded and approached with a software development mindset. In 2017 work on the accompanying PhD thesis began. Since then the design and implementation of Syncpal radically improved, because the academic research mindset and thorough analysis of related work identified and corrected many shortcomings, such as an incomplete analysis of the operation order dependencies. 

> 1Abbreviation for Better Sync, as a homage to the application GoodSync, `https://www.goodsync.com/` , retrieved July 21, 2019. 

> 2See `https://www.enargus.de/` , retrieved July 21, 2019. 

_CHAPTER 7. IMPLEMENTATION_ 

130 



Figure 7.1: Local sync development prototype 

#### **7.1.2 Development prototypes** 

In 2015 and 2016 several internal development prototypes were built which were only distributed among a few close test users. We picked Python 3<sup>3</sup> and Qt5<sup>4</sup> with PyQt5<sup>5</sup> bindings as technology stack, which has not changed since then. Python is a versatile, yet powerful language that allows for rapid prototyping, and Qt5 is used for the GUI and other platform-dependent tasks. 

The following prototypes were built: 

- v0.1 local synchronization: this very first prototype focused on the synchronization logic itself and only supported the file system APIs of Windows and macOS (which are abstracted by Python’s core libraries). It allowed the user to synchronize two local directories, with configurable conflict resolution options. Figure 7.1 shows a screen shot of the application, which consisted of a single window. 

- v0.2 WebDAV synchronization: based on version 0.1 we added support for WebDAV-based storage systems, including BSCW. Additionally the synchronization was done in the background, by repeatedly triggering the static Syncpal algorithm every 15 seconds. Figure 7.2 shows the GUI, which is very similar to version 0.1. 

- v0.3 Background sync: several alpha versions featuring a GUI comparable to those of industrial synchronizers such as Dropbox, stream-lined for end-users. The user can configure several synchronized folder pairs (referred to as _“shares”_ in BSync) which are synchronized in parallel. By default BSync is hidden, and the user accesses it via a tray icon. Figure 7.3 on page 132 has some illustrations. Background synchronization works as in version 0.2. 

We also continuously developed automated software tests further presented in section 8.1 along with all versions. 

> 3 `https://www.python.org/` , retrieved July 21, 2019. 

> 4 `https://www.qt.io/qt-for-application-development/` , retrieved July 21, 2019. 

> 5 `https://pypi.org/project/PyQt5/` , retrieved July 21, 2019. 

_7.2. SOFTWARE ARCHITECTURE_ 

131 



Figure 7.2: WebDAV development prototype 

#### **7.1.3 Beta versions** 

In 2017 and 2018 a total of eight beta versions of BSync were released, distributed to a wider target audience of close to 30 users. The most important changes were the handling of platform inconsistencies, improving the core algorithm in accordance to new findings made in writing this thesis, and the introduction of a truly _dynamic_ synchronization approach that is no longer dependent on regularly executing the static Syncpal algorithm. Instead we implemented hybrid, filesystem event-based update detectors for macOS, Windows, BSCW and other file systems, see sections 4.3+7.3. We also added the automatic collection of statistics and further stream-lined the user experience. This included improving the visual appeal of the GUI (see figure 7.4 ), the option to automatically start BSync after logon, automatic updating (deployment) and automatic retrial of operations that failed temporarily. These beta versions were used exclusively to synchronize with BSCW. Other file system implementations (such as generic WebDAV or ownCloud/Nextcloud) were supported, but were not developed to production-level quality and thus the functionality to select these file system types was not shown in the GUI. 

### **7.2 Software architecture** 

The simplified software architecture is shown in figure 7.5 . The following list explains the purpose of the individual modules. The red numbered arrows denote the activity flow which is further explained in section 7.3. We use a layered approach where the UI implementation (which could be graphical or text-based) is interchangeable. 

- `GUI` : the user interface implemented using the Qt5 framework. It communicates with the application layer via method calls (GUI to application) and Qt signal messages (application to GUI). 

- `BSyncCore` : the facade of the application layer. All interaction between UI and application layer take place via this module. It is in charge of managing one or more `Syncpal` instances (one 

_CHAPTER 7. IMPLEMENTATION_ 

132 







<!-- Start of picture text -->
(a) Quick view window (single click on tray (b) Context menu (right click on tray icon)<br>icon)<br>(c) Conflict inspection window<br>(d) Synchronization progress inspection<br><!-- End of picture text -->

Figure 7.3: Background sync development prototype 

_7.2. SOFTWARE ARCHITECTURE_ 

133 



Figure 7.4: BSync beta version 

instance per share, i.e., synchronized folder pair). Combines states from different `Syncpal` instances to one common state. Forwards messages from `Syncpal` instances to the GUI. 

- `Syncpal` : Is in charge of orchestrating the synchronization process, split into the three phases _update detection_ , _reconciliation_ and _propagation_ . Each `Syncpal` performs its work asynchronously using its own thread. 

- `Update Detector` : its task is to discern _expected_ from _unexpected_ file system events delivered by the `File System Observer` sub-module, to deliver _unexpected_ events to the `Syncpal` module, and to generate an _update tree_ from the most recent file system state upon request by `Syncpal` . There are _two_ `Update Detector` instances per `Syncpal` , one for the remote, one for the local replica. 

- `File System Observer` : regularly delivers up-to-date snapshots and file system events of the designated replica to the `Update Detector` . The implementation subscribes to the proprietary event stream and performs _hybrid update detection_ where possible, as described in section 4.3. If an unsuitable mapping between namespace and objects is found (see section 3.1.1), that is, if a specific object appears more than once, the `File System Observer` raises an error, causing the synchronization to stop. The user then has to remove all but one link to the object. 

- `Reconciliator` : Given two update trees, the `Reconciliator` examines them for cross-platform incompatibility issues and conflicts and returns the list of operations to propagate. The work is divided into several sub-modules described below: 

   - `Platform Inconsistency Checker` : examines the names of the objects of each tree for issues they would cause when attempting to propagate these objects to the respective other replica. See section 7.2.1 for a detailed module description. 

   - `Conflict Finder` , `Conflict Resolver` : as described in section 5.5 these sub-modules find and sort conflicts and generate resolution operations that resolve the first conflict (if any). 

   - `Operation Generator` : Generates the unsorted list of operations for non-conflicting and pseudo-conflicting operations. 

- `Propagator` : Given the generated list of operations from the `Reconciliator` , the `Propagator` sorts the operation (if necessary) and the propagates one operation at a time, using the `Executor` . 

_CHAPTER 7. IMPLEMENTATION_ 

134 



Figure 7.5: Implementation architecture 

_7.3. DYNAMIC SYNCHRONIZATION_ 

135 

- `Sorter` : sorts the list of operations as described in section 6.3.2. 

- `Executor` : executes each operation and tells the `Update Detector` about the file system event it should expect. 

- `Database` : contains the persisted file system state. We implemented the database using the SQLite library. 

#### **7.2.1 Platform Inconsistency Checker** 

In chapter 3 we elaborated on different platform incompatibilities. The `Platform Inconsistency Checker` handles all issues related to the _names_ of objects. Our approach is to build a set of rules from the _union_ of all limitations of the supported file systems and apply these rules to all replicas. This way, the names of objects can be equal on both replicas and in the database, which simplifies synchronization logic. The rules enforce case- and Unicode normalization _in_ sensitivity on sensitive replicas, and ensure that the union of all reserved characters, names and maximum name lengths apply to the names of all objects on all replicas (even on those replicas where no such reservations exist). 

Our implementation iterates over the objects of the _update tree_ of each replica separately. It examines the names of all objects as well as all sibling nodes. Whenever it finds a name (or sibling names) that violates a rule, the `Platform Inconsistency Checker` generates one or more resolution _rename_ operations that change the object’s names on the replica. Users are notified about such rename operations, similarly as they are notified about conflicts. The synchronization process is then restarted, as it would be for conflicts. We implemented a two stage process shown in algorithm 4. The split into two stages reduces the complexity of the implementation of each individual stage. The first stage, `normalize_unicode_chars()` , enforces Unicode normalization insensitivity by applying the NFC normalization to object names. It detects clashes and renames one of the objects to enforce insensitivity. There are many intricate details our code deals with which are beyond the scope of this work, such as the HFS+ file system on macOS which is neither Unicode normalization preserving nor correctly implementing Unicode normalization insensitivity. 

- **def** handle_cross_platform_inconsistencies ( update_tree_root_node ) : resolution_operation_list = normalize_unicode_chars ( update_tree_root_node ) **i f len** ( resolution_operation_list ) > 0: **return** resolution_operation_list 

   - resolution_operation_list = handle_reserved_chars_and_case_sensitivity ( update_tree_root_node ) 

**return** resolution_operation_list 

**Algorithmus 4 :** Platform Inconsistency Checker pseudo code 

Stage two is reached only once Unicode normalization insensitivity has been established. `handle_reserved_chars_and_case_sensitivity()` then checks each object’s name for reserved characters or names and generates rename operations that replace, strip or prefix these characters/names to resolve the issue. It also limits the names of objects to 255 characters and detects clashes. 

### **7.3 Dynamic synchronization** 

Instead of asking the user to explicitly invoke the synchronization, BSync runs transparently in the background. The development prototypes described in section 7.1.2 simply applied the _static_ Syncpal algorithm in regular intervals. This is very inefficient, because sampling a file system’s state is computationally expensive. Doing so takes a long time and puts strong load on the system (i.e., does not scale). In general, file system load caused by a user comes in bursts, thus most state samplings are superfluous. If the user did perform changes, they are not picked up until the next sampling, causing a considerable delay until BSync processes those changes. 

_CHAPTER 7. IMPLEMENTATION_ 

136 

#### **7.3.1 Requirements** 

In BSync we require a new, efficient approach that performs synchronization shortly after the detection of user activity, without putting much load on the system. Since most file systems cannot be locked for exclusive access by BSync (and doing so would not be a good idea), our new dynamic algorithm needs to invalidate an ongoing synchronization against concurrent user activity. For instance, let _o_ be a _createfile_ operation that uploads file _f_ to the remote replica. If _o_ is scheduled as 5th operation in the queue, by the time _o_ is executed, _f_ might already have been deleted, edited, or moved to a different location by the user. The hybrid update detection approach used by BSync’s new approach (see section 4.3) does _not_ indicate the origin of the operation (i.e. which program caused it). Thus, some module of BSync must disambiguate those detected file system operations caused by BSync’s propagation stage from those caused by the user. That is, the stream of detected operations must be separated into _expected_ and _unexpected_ ones. BSync then needs an approach to use _unexpected_ operations to invalidate the ongoing synchronization. 

#### **7.3.2 Implementation** 

The modules of our implementation are shown in figure 7.5 on page 134. The dynamic version of Syncpal is explained via the activity flow, depicted by the red arrows. We now elaborate on the meaning of each arrow: 

- **1.** The `File System Observer` modules report detected operations together with the most recent snapshot to the `Update Detector` , whenever file system activity is detected. 

- **2.** The `Update Detector` compares the detected operations against a list of _expected_ operations, which it received from the `Propagator` (see step 6.3). Those operations that do not match are, consequently, _unexpected_ events. If all received events are _expected_ , the activity ends here. Otherwise the _unexpected_ events are reported to `Syncpal` which decides how to handle them depending on which stage it is in: 

   - If no stage is active (i.e. BSync is _idle_ ), a new synchronization iteration is started. `Syncpal` switches into _update detection_ stage and proceeds with step 3. 

   - If some stage is active, `Syncpal` keeps it running and does _not_ abort it. `Syncpal` sets a _restart_ flag to True, which remembers to start a new iteration once the ongoing iteration has concluded. 

- **3.** `Syncpal` requests the two `Update Detector` instances to build _update trees_ . During the generation the `Update Detector` implementation is able to handle concurrently detected operations reported by the `File System Observer` , by internally restarting the tree generation. 

- **4.** The generated _update trees_ are returned to `Syncpal` . Since BSync is implemented in Python whose threads do not use multiple CPU cores due to the Global Interpreter Lock<sup>6</sup> , in step (3) `Syncpal` requests the generation of the _local_ and _remote_ update tree, one at a time. Once both trees are built, `Syncpal` asks both `Update Detector` instances whether any of the trees are out of date again (e.g. the _local_ update tree might have become out of date by the time the _remote_ update tree was generated). If so, `Syncpal` goes back to step (3), otherwise it proceeds to the next step. 

- **5.** `Syncpal` switches to the _reconciliation_ stage and requests that the `Reconciliator` builds a list of operations from the two _update trees_ . The reconciliation process is divided into several steps and submodules, each with a specific task: 

   - **5.1** The `Platform Inconsistency Checker` is invoked first. It checks each _update tree_ for problems with object names. If any are found, _reconciliation_ ends and the _rename_ operations that resolve these problems are provided to `Syncpal` . Otherwise we continue with step 5.2. 

- 6 `https://wiki.python.org/moin/GlobalInterpreterLock` , retrieved July 21, 2019. 

_7.3. DYNAMIC SYNCHRONIZATION_ 

137 

   - **5.2** The `Conflict Finder` examines both trees for real and pseudo conflicts. If no real conflicts are found, continue with step 5.3. Otherwise the found conflicts are given to the `Conflict Resolver` , see step 5.4. 

   - **5.3** The `Operation Generator` builds the list of non-conflicting operations and returns them to `Syncpal` . 

   - **5.4** The `Conflict Resolver` sorts the conflicts found in step 5.2, and returns a list of resolution operations that resolve the _first_ conflict to `Syncpal` . 

- **6.** `Syncpal` switches to _propagation_ stage and provides the operations to the `Propagator` . If the operations consist only of non-conflicting operations, the `Propagator` gives the operations to the operation `Sorter` (step 6.1). Otherwise the `Propagator` proceeds with executing them, by handing them to the `Executor` (step 6.2). 

   - **6.1** The `Sorter` sorts operations and finds cycles, as described in section 6.3.2. It either provides the sorted list of operations (if no cycle was found), or the resolution rename operation that breaks a cycle, to the `Executor` . 

   - **6.2** The `Executor` executes one operation _o_ at a time. It provides each _o_ with the current list of _unexpected_ detected operations it retrieves from the `Update Detector` modules. Each operation has a tailored implementation that compares every unexpected event against the update tree node(s) it is supposed to propagate. If at least one unexpected event is found to _disturb o_ , the `Executor` aborts propagation prematurely and continues at step 7. 

      - <sup>Forinstance,let</sup><sup>_o_bea</sup><sup>_Create_operationforadirectorynode</sup><sup>_nX_suchthatexecuting</sup> _o_ will create the directory on replica _Y_ (see section 6.3.1 on page 117). The following _unexpected_ events would be disturbing: 

         - _move(P, any)_ where _P_ is the path of some parent directory of _nX_ (check _move_ operations on both replicas) 

         - _move X (path(nX ), any)_ , as the object is no longer at its original location 

         - _moveY (any, pathY )_ to detect a Move-Create conflict 

         - _delete(P)_ where _P_ is the path of some parent directory of _nX_ (check on both replicas), which also detects Create-ParentDelete conflicts 

         - _delete X (path(nX ))_ , as the object no longer exists 

         - _createY_ ( _pathY_ ) to detect Create-Create conflicts 

         - _editX_ ( _path_ ( _nX_ )), as the object’s meta-data stored in _nX_ has become out of date 

         - _editY_ ( _pathY_ ) - only for for pseudo Create-Create conflicts of a file 

- **7.** The `Executor` reports to `Syncpal` that it finished. It provides a boolean flag _b_ that indicates whether a new synchronization iteration should be triggered, either because a cycle was broken, or because a disturbing unexpected event was detected. 

   - If _b_ or the _restart_ flag from step 2 is _True_ , `Syncpal` starts a new synchronization iteration (go to step 3). 

   - Otherwise the synchronization has concluded and `Syncpal` is _idle_ again. 

As these detailed steps illustrate, our approach simply lets the `Update Detector` modules detect and collect _unexpected_ (user-made) operations. Each executing operation needs to request ( _pull_ ) those unexpected events and compare them against its own data. 

We built this approach after several other, unsuccessful designs. For instance, we found a push-based approach to be problematic, where each core module ( `File System Observer` , `Update Detector` , `Reconciliator` , `Propagator` ) is implemented as asynchronous module with its own thread. Information was _pushed_ from module to module, i.e., an `Update Detector` would asynchronously generate an _update tree_ and then push it to the `Reconciliator` , the `Reconciliator` would asynchronously build a list of operations and push them to the `Propagator` , etc. However, such a design suffers from many problems, such as high code complexity, difficult-to-catch race conditions or dead-locks, and large delays between detecting unexpected events and aborting an ongoing synchronization. 

_CHAPTER 7. IMPLEMENTATION_ 

138 



Figure 7.6: BSync’s monthly unique users over time 

### **7.4 Software deployment** 

Between July 2017 and December 2018 close to 30 unique users transmitted usage statistics, see figure 7.6. The majority of users were from our organization, Fraunhofer FIT, as well as associated partners like RWTH Aachen. The author of this thesis has also used BSync throughout this time period to synchronize thesis documents between personal and work computers. 

To allow for an easy installation of BSync we developed a one-click build tool that produces an installation program for Windows and macOS. The build tool performs the following fully automated steps: 

1. _Freezing_ of the Python code, using pyqtdeploy<sup>7</sup> , which compiles Python source code into byte code and bundles them in an executable binary (.exe on Windows) with a C++ compiler. This binary also bundles a Python interpreter, such that users are not required to install Python itself, 

2. Collection of dependencies, such as dynamically linked libraries like Qt, SQLite or (py)curl, which need to be shipped with the binary for it to work, 

3. Building of an installation program, using the WiX toolset<sup>8</sup> on Windows, and DropDMG<sup>9</sup> on macOS. See figure 7.7 for an illustration of the installation program. 

The resulting installation programs allow end-users to install or update an existing BSync installation with ease. 

### **7.5 Lessons learned** 

Over the years of developing BSync we acquired numerous requirements and insights from our users, presented below. 

#### **7.5.1 Partial synchronization** 

The core idea of BSync has been to synchronize _all_ divergences of two file system replicas, in both directions. However, users presented two use-cases where only a subset of objects should be synchronized: 

> 7 `https://pypi.org/project/pyqtdeploy/` , retrieved July 21, 2019. 

> 8 `http://wixtoolset.org/` , retrieved July 21, 2019. 

> 9 `https://c-command.com/dropdmg/` , retrieved July 21, 2019. 

_7.5. LESSONS LEARNED_ 

139 







<!-- Start of picture text -->
(a) Windows (b) macOS<br><!-- End of picture text -->

Figure 7.7: BSync installation programs 

1. Exclusion of specific files or directories: a user may want to avoid that a specific _local_ sub-directory is uploaded to the server (and thus shared with other users). Conversely, the user may want to exclude a specific _remote_ sub-directory from being downloaded, e.g. because she doesn’t need it, or because it’s child objects would consume too much local disk space. 

2. _Uni_ -directional synchronization: file systems like BSCW may limit the permissions a user has for specific directories. These permissions themselves are not present on the corresponding other replica, such as the local disk. When the user modifies objects locally, BSync needs to detect that these operations cannot be propagated to the other replica (due to a permanent lack of permission) and should not even try to do so. 

Addressing these use-cases is left as future work, see section 9.3. 

#### **7.5.2 Handling of temporary objects** 

The majority of files BSync propagates are managed by other third-party applications which store their state in a set of files. While these applications are in use, they create temporary objects, such as temporary files containing intermediate results, automatic backups or _lock_ files. For instance, Microsoft Word creates a lock file named “~$filename.docx” when the user opens “filename.docx”, which is automatically deleted once the user closes the document again. Because synchronizing these files is not helpful and consumes unnecessary bandwidth, earlier beta versions of BSync used a _hard-coded_ filter that discards such objects on the lowest level, during update detection. Newer BSync versions allow expert users to add their own filter patterns, to accommodate other applications. 

#### **7.5.3 Non-deterministic file system behavior** 

We have observed different kinds of erratic behavior in the implementations of file systems where a file system operation (that should execute successfully) was unexpectedly refused, with some kind of permission error. These kinds of errors were hard or impossible to synthesize in automated tests. The issue is sometimes _temporary_ , where failures only occur due to high concurrent load. For example, Windows might refuse to delete a directory because it was supposedly not empty, even though BSync previously deleted all its children and verified that the directory was empty. The issue may also be _permanently_ , e.g. if a Antivirus program, which interferes with file system operations on a kernel level, blocks access to a (supposedly) infected file, which inhibits BSync from reading or uploading it. 

_CHAPTER 7. IMPLEMENTATION_ 

140 



Figure 7.8: BSync user feedback form 

#### **7.5.4 Bug triage** 

When deploying software to users it is important to detect and resolve bugs that do not occur during development. Such defects occur e.g. due to a different usage pattern of the software or due to a different environment, such as system configuration. In BSync we included a feedback form shown in figure 7.8 that allows users to report errors (or suggest new features). The form’s user interface is similar to authoring an email. Users can provide a summary, a longer description, and BSync automatically attaches the most recent replica states, the database state and a log file to the report. The log file contains extensive traces of BSync’s modules. 

Even with this feedback mechanism in place, we still found bug triage to be very challenging. Data synchronization is a state-based process that involves the state of several systems (file system replicas in this case). Bugs can occur at different places (file system and its observation, update detection, reconciliation, propagation, etc.) and in some instances BSync’s implementation is not even to blame. For instance, if the server’s file system lists files that are supposedly in a specific directory, but returns “file not found” errors when BSync attempts to download them, the user blames BSync first. The main challenge for a synchronizer developer is to answer the following questions: 

1. What did truly happen? E.g. what operations did the user perform, or what was the remote replica’s state? 

2. What did the synchronizer think was happening? 

3. What did the user expect to happen? What should the synchronizer have done? 

Logs and state files (attached to the feedback form) only cover question 2, as well as the synchronizer’s behavior (including error messages). However, logs do not cover errors in the user interface, which require screenshots to understand the issue. Questions 1 and 3 need to be answered by the user. An analysis of the submitted reports has provided the following two key insights: 

_7.5. LESSONS LEARNED_ 

141 



Figure 7.9: BSync user feedback form, revisited 

- Without guidance users have a hard time formulating informative reports. Many reports contained barely any information beyond statements such as “The sync doesn’t work” or “The application is just hanging...”. 

- Timing is important: the larger the time period between error and extraction of information from the user, the less likely users still remember their last steps (to answer question 1). 

Even when interviewing users following a bug report _a few minutes_ after it was submitted, users were already fuzzy about the file system operations they performed. Answering question 1 is further complicated when _several_ BSync users are involved, whose operations are distributed via the server. 

To improve BSync’s quality we used two approaches. First, we provided guidance and improved the feedback form, see figure 7.9. Users are now presented with a number of questions (with sample answers) as guidelines for what information to provide in a report. Users can also create screenshots (for UI problems) and attach arbitrary files. However, this approach is no silver bullet. It is unrealistic to expect that users remember their actions. Our second approach addresses this via in-house testing. Before releasing a new version of BSync with major changes we perform a one-hour test session with multiple users and developers, all simultaneously located in the same room. Users were instructed to work with BSync and their third-party applications as usual, but keep their latest actions in mind, or even note them down. Sometimes users were also asked to design and execute collaborative scenarios with a high degree of concurrency and system load. Whenever problems occur, users report them immediately with the feedback form and the developer additionally examines the problem personally, or assists with filling the report. 

_CHAPTER 7. IMPLEMENTATION_ 

142 

#### **7.5.5 Platform incompatibilities** 

We discovered the majority of platform incompatibility issues early on in section 3.1, via manual testing and examination of the official documentation of file system APIs. Here we present how these issues manifest in practice on the Fraunhofer FIT BSCW server which covers over 20 years of user activity. While recent BSCW versions perform similar reserved object name checks as Windows does, for compatibility reasons, earlier BSCW versions did not. Consequently, a number of work spaces contained legacy objects with names that contain such reserved patterns, e.g.: 

- A directory named “Interview, etc.” would require to strip the trailing dot. 

- When BSCW auto-generates objects, such as archived emails, the names contain quotation marks which BSync needs to remove. 

- Objects with names like “A/B Test 1.8.2001 14:30 Uhr” contain multiple reserved characters, such as forward slash or colons often used in time stamps. 

- Some file systems APIs such as the one of macOS allow object names to contain control chars (ASCII range 1-31) not supported by other file systems. 

We also investigated whether the varying support of _namespace to object mapping_ causes issues in practice. In the largest work spaces we examined we found that only a handful of BSCW objects were linked into more than one parent directory, which is rejected by Syncpal. Manual removal of all but one link could be achieved within a short amount of time to resolve the issue in practice. 

### **7.6 Conclusion** 

Building a near real-time synchronizer is difficult. In contrast to the _static_ variant of Syncpal, we need to detect and handle concurrent operations. This requires an appropriate software architecture with an efficient, yet simple data flow between components. Over time, we tested and implemented various approaches. We found that purely push-based ones do not perform well, which push data from _update detection_ to _reconciliation_ to _propagation_ . It has proven difficult to manage the high, multi-threaded code complexity, race conditions and latency. Our final approach is much simpler. As figure 7.5 shows, the three stages are no longer arranged as a strict chain. We only push file system operations into the _update detection_ component, which identifies and caches unexpected user-made operations. These are then _pulled_ just in time, by the propagation component. The dynamic version of Syncpal is a singlethreaded component that coordinates the overall synchronization process. While this limited use of multi-threading slows down some aspects of synchronization, it reduces the overall CPU usage to a level that users accept and expect of a _background_ process. Another side effect of near real-time synchronization is the necessity to translate conflict detection logic. As conflicts could arise during an ongoing propagation, it would be inefficient to run the reconciliation process (including conflict detection) whenever a new, unexpected operation is detected. We instead built simple, purely operation-based heuristics that evaluate whether the history of all detected, unexpected operations disturbs the currently scheduled operation. While this approach requires additional implementation effort, it improves the synchronization efficiency considerably. 

The longitudinal evaluation of the implementation by the BSync user group yielded numerous user requirements and a deep understanding of the day-to-day use of our file synchronizer. Many of these learnings have been adopted but also provide insights for future work, most notably the ability to synchronize only _parts_ of a replica. 

143 

## **Chapter 8** 

# **Evaluation** 

In this chapter we perform a technical evaluation of Syncpal and its implementation. We first focus on _correctness_ of _our_ implementation by building hand-crafted as well as automatically generated tests in section 8.1. We then examine the _correctness_ of _other_ synchronizers in section 8.2, where we develop and apply a test framework to four industrial-grade file synchronizers. Our hypothesis is that their convergence behavior varies strongly, because we found strong divergences in academics works as well, as demonstrated in section 5.1. To understand how our implementation is used in practice we collected statistics which we analyze in section 8.3. We examine the complexity in terms of computation time and memory use in section 8.4 and conclude in section 8.5. 

### **8.1 Automated testing to verify correctness** 

The theoretical proofs presented in previous chapters are a necessary, but not sufficient, condition for the correctness of the _implementation_ of Syncpal. To complement the proofs we created three types of automated tests in the form of black-box tests [Nid12] where implementation details of our algorithm are considered to be unknown. We apply these tests to our implementation of the Syncpal algorithm, BSync, which we presented in chapter 7. They cover aspects not covered by the theoretical proofs, e.g. platform-specifics, and verify that the translation of our algorithm (from English language or pseudocode to Python code) is free of errors. 

An inherent issue in testing is that the problem’s state space (file system operation sequences) is infinite, but test execution is required to be a finite process<sup>1</sup> . Consequently, tests cannot verify correctness of an algorithm the same way proofs by contradiction or induction can, which are not example-based. To reduce the chance for errors to a minimum we apply the combination of both approaches, formal proofs and explorative black-box testing. During our development process those tests also turned out to be very valuable because they discovered regression errors introduced when changing parts of the code. 

We apply the concept of _falsifiability_ [Pop02; HO48] to the statement “the synchronization result of our BSync implementation is correct for all existing file system operation sequences executed concurrently on two replicas”, by building tests that demonstrate _counter examples_ of the following forms: 

- Synchronization does not finish in a certain time period (caught in an infinite loop), or crashes with an error. 

- Synchronization finishes without error, but the two replica states are not equal. 

- If a test _oracle_ (=expected result) is available, the two equal replica states (determined after synchronization finished without errors) do not match the oracle. Such an oracle can either be crafted by hand or can be inferred if an arbitrary list of operations is only applied to replica _X_ by the test (i.e., the test does not manipulate replica _Y_ ). In that case, we can automatically build a test oracle which expects that the synchronizer applies an equivalent set of operations to replica _Y_ . 

- 1In the release process of a software the test execution is typically expected to finish within a few hours. 

_CHAPTER 8. EVALUATION_ 

144 

The steps performed by each test are as follows (where steps 2, 4 and 8 only apply to tests for which a test oracle is available): 

1. Establish a _start scenario_ : create a set of files (with random content) and directories on one replica and synchronize them the other (empty) one, s.t. both replicas and the database state match. 

2. Create two expected states _elocal_ , _eremote_ , by taking a snapshot of each replica, which still resemble the start scenario. 

3. Execute a list of file system operations _O_<sup>¯</sup> _local_ , _O_<sup>¯</sup> _remote_ on the physical local and remote replica. 

4. Execute a list of simulated file system operations by manipulating _elocal_ , _eremote_ to reflect the expected final replica states after synchronization has completed. 

5. Start the BSync implementation, wait for it to complete within a limited time. 

6. Take final state snapshots _flocal_ , _fremote_ of the local and remote replica. 

7. Verify that _flocal = fremote_ . 

8. Verify that _elocal = flocal_ and _eremote = fremote_ . 

When a test fails, either the algorithm or its implementation is the cause. To investigate, our test implementation ensures that sufficient information is kept to repeat the same scenario. 

We now explain the three types of test we built, each one presented in a separate subsection. The first type is a set of _hand-crafted_ test cases presented in section 8.1.1, which focus on implementation- and platform-specific aspects as well as conflicts and their correct resolution. Sections 8.1.2+8.1.3 explain _automatically generated_ tests, where the first type generates all possible scenarios, each with a limited number of operations, and the second type _randomly_ generates a long list of operations. 

#### **8.1.1 Hand-crafted tests** 

We built over 350 tests (with test and tooling code exceeding 13’000 lines of code). Around 150 tests are _white-box_ tests, with the following goals: 

- Test individual components, such as the database or file system observers, 

- Regression tests of libraries used by BSync (including Python’s file system abstraction), to verify that the behavior of these libraries is stable when updating the library or using different operating system versions, 

- Synchronization logic tests, where the test needs state introspection to detect when a new synchronization is started. These tests generate non-conflicting, concurrent operations, which modify a replica while the synchronization is in progress. They verify that the affected operations are skipped (if necessary), and that BSync starts a consecutive iteration which synchronizes the remaining operations. 

The remaining tests are black-box tests where each test manually defines a list of operations to execute on each replica, and a test oracle. The tests are executed on _static_ file systems and cover the following goals: 

- Verify that BSync applies non-conflicting operations to the other replica without modification. The tests include both simple sanity checks and more involved scenarios with complex operation interleavings. The tests also consider case-insensitivity, operation _sorting_ (as well as breaking cycles) and _pseudo_ conflicts. 

_8.1. AUTOMATED TESTING TO VERIFY CORRECTNESS_ 

145 

- Verify that BSync handles namespace limitations correctly, such as reserved names or characters. See section 7.2.1 on page 135 for further details. 

- Verify the correct detection and resolution of conflicts. Our tests cover both simple and complex scenarios, where a set of files is affected by _multiple_ conflicts. We provide test oracles for all conflict resolution options. 

When implementing tests we applied Equivalence Class Partitioning (ECP) and Boundary Value Analysis [SLS14; Nid12] to limit the number of tests. 

#### **8.1.2 Generated deterministic tests** 

Building hand-crafted tests does not scale and misses a lot of incorrect behavior of the system under test [Amj04]. We applied a variant of Model checking [Cla08], which was also done by the file synchronizer work [Bjø07]. Model checking is _“an automated technique that, given a finite-state model of a system and a formal property, systematically checks whether this property holds for (a given state in) that model”_ [BK08]. A common property to check is the _safety_ property. In our case, the synchronizer should not do anything undesirable, such as crash because of a bug or because a precondition of a file system operation was violated, or terminate without crashing, producing two divergent replicas. Another common property is the _liveliness_ property, that states the synchronizer performs those actions it is supposed to do, such as propagating all non-conflicting operations to the other replica, producing two convergent replicas. 

Applying model checking to our file synchronizer requires reducing the problem space, as the space is otherwise infinitely large when tree depth and/or the set of object names is unlimited<sup>2</sup> . We implemented the following reductions, some of which were also used in [Bjø07]: 

- We only apply operations to _one_ replica. This eliminates the need to check the correct resolution of _conflicts_ , which would require another, independent implementation that finds and resolves conflicts. This also allows us to compute the test oracle, as discussed in section 8.1. The synchronization problem space is now defined as follows: for any synchronized _start state_ , with replica states _sX = sY_ , executing any list of file system operations _l_ that transforms _sX_ to _s_<sup>_′_</sup> _X_<sup>(</sup><sup>_sX̸= s′_</sup> _X_<sup>), run</sup> the synchronization and verify that the synchronizer changed _sY_ to _sY_<sup>_′_such that</sup><sup>_s_</sup> _Y_<sup>_′= s′_</sup> _X_<sup>.</sup> 

- We limit the _start state_ space to consist of _b f_ files and _bd_ directories. We find the distinct set of start states by using a recursive solution to the well-known _integer partition_ problem [And03]. 

- We limit the names of objects to single-character strings, drawn from the lower-case ASCII alphabet, a-z. 

- We limit the number of operations to up to _cc f_ create file operations, _ccd_ create directory operations, _cm_ move operations, _cd_ delete operations and _ce_ edit operations. 

The generation of test scenarios is a recursive process: 

- Build a set _OT L_ of operation type lists by computing all permutations of the operation types. For instance, if _ccd =_ 1, _cm =_ 2 and all other _c_ variables are 0, then _OT L = per mutate_ ([ _createdir_ , _move_ , _move_ ]) 

- _=_ {[ _createdir_ , _move_ , _move_ ],[ _move_ , _createdir_ , _move_ ],[ _move_ , _move_ , _createdir_ ]}. 

> 2In practice there are limitations, such as maximum path length and the limited alphabet of names, e.g. based on the Unicode alphabet. However, the space of all file system states is still extremely large, and the problem space of every possible transition between any two states is exponentially larger. 

_CHAPTER 8. EVALUATION_ 

146 

- For every start state _s_ , iterate over every operation type list _otl ∈ OT L_ , and iterate over every operation type _ot ∈ otl_ . Generate all possible operations _o_ for the given operation type _ot_ . For instance, if _ot = delete_ , iterate over every possible object in the file system (except for the root directory) and instantiate a _delete_ operation _o_ for that object. This requires keeping a shadow copy of the file system state in memory for every possible operation sequence. Add _o_ to operation list _l_ . 

- Whenever an operation _o_ was applied, persist a new test scenario to disk. 

Because this simple approach produced many similar test scenarios, further reductions were necessary: 

- For any two _equal_ states _s_<sup>_′_</sup> _X_ 1<sup>and</sup><sup>_s′_</sup> _X_ 2<sup>thatresultfromapplyingtwo</sup><sup>_different_operationlist</sup><sup>_l_1,</sup><sup>_l_2</sup> to _sX_ , discard one of the lists. For instance, if _sX_ consists of a single file named _’a’_ and _l_ 1 _=_ [ _move_ (<sup>_′_</sup> _a_<sup>_′_</sup> ,<sup>_′_</sup> _b_<sup>_′_</sup> )], _l_ 2 _=_ [ _move_ (<sup>_′_</sup> _a_<sup>_′_</sup> ,<sup>_′_</sup> _c_<sup>_′_</sup> ), _move_ (<sup>_′_</sup> _c_<sup>_′_</sup> ,<sup>_′_</sup> _b_<sup>_′_</sup> )], then we can discard either _l_ 1 or _l_ 2. 

- For any two _distinct_ states _s_<sup>_′_</sup> _X_ 1<sup>,</sup><sup>_s′_</sup> _X_ 2<sup>whosesetsofnamesaredifferentbutwouldbecoincidental</sup> if every new, unused name (used by any of the operations) were replaced by a universal variable name, discard one of the states (and the corresponding list). For instance, if _sX_ consists of a single file named _’a’_ and _s_<sup>_′_</sup> _X_ 1<sup>is based on</sup><sup>_l_1</sup><sup>_=_[</sup><sup>_move_(</sup><sup>_′a′_,</sup><sup>_′ b′_] and</sup><sup>_s′_</sup> _X_ 2<sup>is based on</sup><sup>_l_2</sup><sup>_=_[</sup><sup>_move_(</sup><sup>_′a′_,</sup><sup>_′ c′_)], then</sup> both lists have in common that object _’a’_ was renamed to a previously unused name and we can discard either _l_ 1 or _l_ 2. 

For the implementation of our model checker we applied additional implementation-specific techniques to speed up test generation and execution: 

- We used parallelization for generating test scenarios, distributing the computation using a divide and conquer approach. Generation was run on over 120 CPU cores (distributed over dozens of machines) for almost one month, followed by merging the results on a single machine (filtering duplicated results) over a period of 5 days. 

- We analyzed slow code paths and optimized them by a factor of 4-16 using Cython, which converts Python to C code. 

- For generating and executing test scenarios we implemented a simple in-memory file system in C (using Cython) used instead of the real device file system, because real file systems (even RAM disks) turned out to be a performance bottleneck. 

Despite these optimizations we had to choose rather small values for _b_ and _c_ defined above, to limit generation and execution time. We chose _b f =_ 1, _bd =_ 3, focusing on a larger number of _directories_ (rather than files) because move operations on directories produce the most challenging scenarios for the _operation sorting_ routines. We chose _cc f =_ 1, _ccd =_ 2, _cm =_ 3, _cd =_ 3, _ce =_ 0, ignoring _edit_ operations because they have no impact on operation ordering. The resulting _5.5 million_ test cases (computed in almost one month as described above) helped solving various issues in the Syncpal algorithm and its implementation. 

#### **8.1.3 Generated random tests** 

To overcome the limits resulting from state explosion in the model checking approach described above, we also implemented the generation of _random_ test scenarios. As noted in [GHJ07], who also applied randomized testing, the _“difficulty of model checking (and theorem proving) makes randomized testing attractive, when software actually has to be delivered”_ . 

The randomized testing approach generates a much larger number of operations (e.g. 30 and more) before starting the synchronizer. Each operation type and its parameters is chosen at random, using a _uniform_ distribution. Thus, every operation type is equally likely. We implemented two test modes. The first mode applies the randomly generated operations only to replica _X_ . The test oracle expects Syncpal 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

147 

to apply an equivalent set of operations to replica _Y_ . As such, this mode is similar to the generated deterministic tests presented in the previous section. The second test mode applies operations to replicas _X_ and _Y_ separately, deliberately causing conflicts. Here the test oracle can only verify that the state of both replicas has converged, but does not assume that the final states coincide with _s_<sup>_′_</sup> _X_<sup>or</sup><sup>_s_</sup> _Y_<sup>_′_,dueto</sup> possible conflicts. 

When generating a large number _g_ of operations, an initially open question is how large _g_ should be, and the effect of _g_ on the tree size. We identified two conditions that must be avoided when running the tests: 

1. When _g_ is large, the file system tree size (i.e., the number of objects) might grow beyond a threshold, causing performance bottlenecks. For instance, if the tree grew to, say, 100’000 nodes, performance would degrade, because every aspect of running the test becomes more complex (e.g. executing Syncpal, or generating suitable operations). A work around is to enforce pruning of the tree once its size exceeds a threshold. 

2. Trivial situations where _compute_  ops_ ( _sX_ , _s_<sup>_′_</sup> _X_<sup>) and</sup><sup>_compute_ops_(</sup><sup>_sY_,</sup><sup>_s_</sup> _Y_<sup>_′_) both yield a set of</sup><sup>_delete_</sup> and _create_ operations, without _move_ or _edit_ operations. For instance, it is intuitively obvious that applying 1’000 randomly generated operations to a tree of 20 objects is very likely to delete all those 20 objects that originally existed in _sX_ , and create, move, delete and edit an arbitrary number of objects which then all appear as _new_ ( _create_ operation) in _s_<sup>_′_</sup> _X_<sup>, due to operation consolida-</sup> tion. However, such scenarios are simple to synchronize and we strive for generating operation sets _O X_ , _OY_ which include _all_ types of operations equally, to increase the chance that _all_ types of conflicts and operation order dependencies are triggered. 

We experimentally collected several statistics, by running tests for uni-directional synchronization (affecting only replica _X_ ) as well as bi-directional synchronization, repeating tests thousands of times for each _g ∈_ {3,10,20,30,50,100,200,1000}. The following statistics were collected: 

- Tree size _st_ after the synchronization has completed. 

- Percentage _sp =_<sup>#</sup><sup>_of createo_</sup> _st_<sup>_<u>perations</u>_</sup> , determined at the stage where all conflicts have been resolved. Condition (2) above states that _sp_ should be as _small_ as possible. 

- Number of conflicts _sc_ found in the first reconciliation iteration (only for bi-directional tests), to determine how many conflicts the tests cause, in relation to _st_ . 

By analyzing histograms we found that _st_ does not grow beyond 40 objects for uni-directional or beyond 250 objects for bi-directional synchronization, for any _g_ . Thus, explicitly pruning the tree is not necessary. However, for _g >_ 30 the majority of test iterations yield _sp =_ 100%. The statistic _sc_ roughly follows a normal distribution with _µ ≈_<sup>_<u>g</u>_</sup> 3<sup>, as long as</sup><sup>_g≤_100.For</sup><sup>_g≥_200 the value of</sup><sup>_sc_is typically below 4 again,</sup> because the distribution of _sp_ is very strongly skewed towards 100%. 

As a consequence we decided to also randomize _g_ . We chose the intervals [3,100] for bi-directional tests, and [3,30] for uni-directional tests, using a uniform distribution. This makes it possible to test all kinds of scenarios. We ran millions of test cases which uncovered many smaller implementation issues. We stopped testing once no anomalies were discovered for several weeks. 

### **8.2 File synchronizer comparative test** 

The goal of this section is to explore the convergence behavior of industrial-grade file synchronizers. We start with discussing related work in section 8.2.1 and present the selected synchronizers in section 8.2.2. We briefly introduce the test categories we designed in section 8.2.3. The generic test framework and setup is presented in section 8.2.4. Section 8.2.5 then describes the concrete tests and the detailed results. We conclude in section 8.2.6 with a result summary and a discussion. 

_CHAPTER 8. EVALUATION_ 

148 

#### **8.2.1 Related work** 

When considering papers that compare file synchronizers, the majority of related work is primarily concerned with _performance_ benchmarks, not with convergence behavior. In [BDM15] the authors benchmark 11 cloud file synchronizers w.r.t. how client capabilities such as _chunking_ , _streaming_ , _deduplication_ and others affect metrics such as consumed traffic and synchronization completion time. The authors in [CLD16] perform similar benchmarks, but for synchronization apps running on mobile devices with limited computing power and unreliable networks. Further benchmarking works exist, see e.g. [KK17; LZD13; Wan+16]. Although these papers do not deal with convergence, they provide valuable clues for how to build a test framework for synchronizers based on virtual machines. 

A few works exist which focus on _convergence_ . [Hug+16] apply _property-based_ testing using Quviq QuickCheck [Art+06] which _"tests properties—universally quantified boolean formulæ—by generating random values for the quantified variables and checking that the formula evaluates to true"_ [Hug+16]. They test the file synchronizers of Dropbox, Google Drive and ownCloud, building a formal specification of the behavior of these synchronizers in the process. To reduce the number of model states the authors limit the operations they randomly generate to read, write and delete operations on a _single_ file. Contrary to our work, they omit testing the _move_ operation or working on multiple files or directories. 

Two academic works presenting novel file synchronization algorithms also compare their convergence behavior with those of industrial synchronizers. The first work, [NS16], formally defines a _path-based_ file system with its operations (create, update, delete, rename, but without the _move_ operation). The authors formally define their merge algorithm for concurrent operations (conflicting and non-conflicting) as "combined effects", and generate test cases by building all possible _pair-wise_ operations (one operation on the local, one on the remote replica). For each pair they iterate over the operation parameters using similar strategies as the _parameters_ we introduce in section 8.2.5, e.g. operation types, number of targets and path relationships, which yields a total of 61 distinct tests which they execute in a similar VM-based setup. The second work, [TSR15] also formally defines the file system and merge behavior, but limits the test to five hand-selected scenarios that mostly deal with conflicting operations. Both works test Google Drive, OneDrive and Dropbox and discover some of the same issues as we do in this section. However, the degree of detail of testing the propagation of _conflict-free_ operations is limited in both works. This, and the fact that the results are over two years old, justify a repetition. 

There are also works that perform black-box testing of other distributed systems with eventual consistency, similar to file synchronizers. For instance, [BMP17] test the convergence behavior of near realtime _text editors_ like Google Docs. The authors develop an approach to automatically build test cases, while eliminating redundant ones, which speeds up test execution. However, their approach does not test an accumulation of _multiple_ operations, as it always executes a _single_ concurrent operation per replica. 

#### **8.2.2 Synchronizers under test** 

There is a tremendous number of industrial-grade synchronizers available on the market<sup>3</sup> . In our selection we focused on implementations that work on macOS and Windows, have a provably wide-spread user base and popularity and offer an English or German user interface and documentation. Testing took place from January to April 2018. We selected the following implementations, with version numbers and remarks shown in table 8.1: 

- Dropbox [Dro18], Google _Backup and Sync_<sup>4</sup> [Goo18] and Microsoft OneDrive [Mic19], because they are synchronization clients for commercial cloud services with the three largest documented 

> 3Examples include Dropbox, Google Backup and Sync, Microsoft OneDrive, Box, Amazon Drive, OwnCloud/NextCloud, Goodsync, cloudStore, Resilio, Seafile, SugarSync, MagentaCloud, SpiderOakOne, Leitz Cloud, Tonido, TeamDrive, MyDrive, Strato HiDrive, CloudMe, hubiC, pCloud, sync.com, tresorit, iDrive and many more. 

> 4The Backup and Sync client synchronizes data with the Google Drive service, see `https://drive.google.com` . The client was formerly known as “Google Drive” [Hac17]. 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

149 

|Name|Windows version|macOS version|Remarks|
|---|---|---|---|
|Dropbox|42.4.114|42.4.114|LAN sync was deactivated.|
|Google Backup<br>and Sync|3.38.7642.3857|3.39.8297.0200|Only the_sync_functionality was used<br>(all_Backup_functions were<br>deactivated).|
|Microsoft<br>OneDrive|Version 2018,<br>17.3.7294.0108|Version 2018,<br>17.3.7131|“On-demand” option was<br>deactivated to ensure that files are<br>fullydownloaded.|
|NextCloud|2.3.3.1|2.3.3 (84)|Synchronization via NextCloud 13.0.1<br>server.|
|Unison|2.48.4|2.48.15|Synchronization via Linux server<br>instance also runningversion 2.48.4.|



Table 8.1: Tested synchronizer implementations 

number of users<sup>5</sup> we could find. 

- ownCloud/NextCloud [Nex18]: an open source industrial-grade client-server software suite used by organizations such as the author’s employer (Fraunhofer FIT), to set up an in-house private enterprise cloud. We tested the _NextCloud_ client which, at the time of testing, was technically equal to the _ownCloud_ client<sup>6</sup> . 

- Unison [Pie18]: an open-source file synchronizer client for Windows, macOS and Linux from research by the authors of [BP98], which is still under active development! 

Apart from Unison we also found the research synchronizers Tra<sup>7</sup> by the authors of [CJ05], and So6<sup>8</sup> from [Mol+03]. We discarded testing the implementations for the following reasons: 

- Tra: the implementation was only available for Linux as source package, which has been unmaintained since 2005. Compilation with modern compilers on modern Linux systems was not successful despite considerable efforts. Support from the original author was not available. Crosscompilation for Windows was not possible. 

- So6: the available client implementation does not work as described in the paper. The core concept of the command-line based So6 synchronizer is to synchronize multiple _workspace_ directories with a central _queue_ directory. Similar to Version Control System tools such as Subversion, users modify their own workspace and then issue a _commit_ to the queue via So6, or they pull changes from the queue by issuing an _update_ command. Another similarity to Subversion is that a _commit_ only succeeds if the workplace is already up to date. We identified an issue that So6 does _not_ allow concurrent, isolated _move_ operations in two different workspaces. When applying move operations directly to the file system via the file manager, So6 detects them as delete + create operations, which is not what we intended. However, So6 provides a _”rename”_ subcommand that moves (or renames) a file or directory, making So6 aware of that operation. The flaw of the So6 implementation is that this _rename_ subcommand immediately triggers a _commit_ command. Since a commit command will fail whenever the workspace is not up to date w.r.t. the queue, concurrent move or rename operations are impossible. For clarification we had a discussion with the authors but were unable to find a solution for this issue. 

> 5Dropbox: 500 million (03/2016) [Dro16], Google Backup and Sync: 800 million (01/2016) [Pri17], OneDrive: 115 million _daily users_ (08/2017) [Sur17] or 250 million (11/2014) [Gri14]. 

> 6See `https://github.com/owncloud/client` , retrieved July 21, 2019. 

> 7See `https://swtch.com/tra/` , retrieved July 21, 2019. 

> 8See `http://dev.libresource.org/home/doc/so6-user-manual.html` , retrieved July 21, 2019. 

_CHAPTER 8. EVALUATION_ 

150 

#### **8.2.3 Test categories** 

We divided our tests into three categories that target different aspects of synchronization. They were chosen because we put considerable effort into solving the related issues in BSync. The tests determine the amount of effort developers of other synchronizers put into their implementation. We now briefly describe each category. Detailed test descriptions are presented later, in section 8.2.5. 

##### **8.2.3.1 Conflict-free operations** 

These tests aim at exploring how a synchronizer handles both easy and challenging concurrent file system operations which do _not_ conflict. The goal is to determine how closely operations performed at one replica are applied to the other replica. In an “easy” test we examine whether a _move_ operation applied to a file or directory at one replica is also applied as _move_ operation to the other replica. More “challenging” tests perform a series of _move_ , _create_ and _delete_ operations that are challenging to reproduce on the other replica due to operation consolidation. By shutting down the synchronizer we enforce state-based update detection. Some tests only operate on one replica, leaving the other replica untouched. Others modify both replicas concurrently, but avoid conflicts by always targeting _different_ objects. 

##### **8.2.3.2 Conflict operations** 

Tests of this category apply operations on both replicas in order to produce conflicts. We investigate how synchronizers resolve conflicts and how they visually present them to the user. As explained in section 5.1 there is a high degree of variance in conflict detection and resolution in academics works, and we assume that industrial synchronizers lack standardization as well. 

A core assumption we make in all our comparative tests is that we presume that the tested file synchronizers use a similar file system model _F_ , as defined in section 3.2. Thus, the set of conflicts and propagated operations should match the ones of Syncpal. The tests result presented in section 8.2.5 will show if this assumption does not hold. For instance, a synchronizer that internally models the file system as a set of _paths_ without IDs is unable to reliably detect _move_ operations, which will be reflected in the test results. 

##### **8.2.3.3 Cross-platform issues** 

As discussed in section 3.1 filesystems on Windows and macOS have many subtle yet important differences. Synchronizers that support both operating systems need to handle undesired side effects and should provide awareness to the user in case files or directories with incompatible characteristics (e.g. _names_ ) cannot be synchronized. Our tests focus on aspects such as case-insensitivity and reserved names. 

#### **8.2.4 Test framework and setup** 

To run tests introduced in section 8.2.3 we designed a framework and test setup that enables complete reproducibility of the results by eliminating errors resulting from manual test execution. We built a set of fully automated test suites where tests are executed in an environment that is tightly controlled. We note that while test _execution_ is fully _automated_ , the _analysis_ of the results is _manual_ . We initially implemented automated result analysis, however, we found that client behaviors varied strongly, making the implementation of test oracles cumbersome and error-prone. By repeating each test three times we minimize the chance of result misinterpretation due to human error. 

##### **8.2.4.1 Requirements** 

Before designing and implementing the framework, we collected two general requirements: 

1. Testing new synchronizers must be effortless. These synchronizers typically come with a proprietary _server_ file system. Our framework should avoid additional implementation efforts to support 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

151 



Figure 8.1: Synchronizer evaluation system setup 

those server file system APIs. Only some _configuration_ effort should be required when adding a new synchronizer, e.g. the specification of the local data directory root path, and the path to the synchronizer client binary file. 

2. Generate _reproducible_ results not affected by race conditions, by strictly controlling concurrency. Other works such as [BMP17] are affected by race conditions, which complicates the result analysis. 

##### **8.2.4.2 Testing method** 

Because the tested synchronizers provide no introspection into their state, we are limited to _black-box_ testing. We only manipulate and monitor file systems and network activity to detect quiescent states. We control concurrency by explicitly starting and stopping synchronizers, solving requirement 2, and collects results for analysis. 

##### **8.2.4.3 Architecture and setup** 

The synchronizers under test typically synchronize a specific local disk directory with a _server_ file system. They don’t allow to synchronize two local directories with each other. For this reason, and because we want to test the synchronizers on both Windows and macOS, a _multi_ -machine setup is required. As noted in [BDM15], virtual machines (VM) are most suitable, because they allow to establish a controlled environment, with fixed hardware configurations, eliminating uncontrolled variables in the experiment. 

Our system setup is shown in figure 8.1. It consists of two physical machines. The test suite runs on a _test machine_ such as the author’s regular work station. On the _virtualization machine_ we run two VMs of 

_CHAPTER 8. EVALUATION_ 

152 

each operating system (OS). This allows to test for different behavior of the synchronization clients w.r.t. platform combinations (e.g. Windows-to-Windows, Windows-to-macOS, etc.). In each VM we install the synchronizer clients under test and configure them with equal settings, such as account credentials and data directory path on the local disk. To test cross-platform issues such as case-sensitivity, we set up a _case-sensitive_ disk volume on macOS instead of using the default volume which is _case-insensitive_ . 

We built a test framework split into three components: _agents_ , _TestCommander_ and _test suites_ : 

- _Agents_ are components we developed, installing one in each VM. Their job is to execute commands received by the _TestCommander_ , such as _start Dropbox_ , _create directory ’test’ in the Dropbox data directory_ , _await file system activity in the Dropbox data directory to cease_ or _await network activity to cease_ . To improve configuration flexibility, the commands are not sent from the test machine to an agent directly but are relayed via a message queue server. 

- The _TestCommander_ offers the same functionality as the _agents_ , but augments the agent’s API by a _host_ parameter that specifies on which VM host(s) the command should be executed. It also implements two helper methods. The _reset synchronization_ method provides a clean slate for a specific synchronizer by clearing its data directories on all hosts, making sure that all clients are in an empty, quiescent state. The method _establishBase_ establishes a base set of files and directories. It first _resets synchronization_ , followed by creating the files (with random content) and directories at one host, waiting that they are synchronized to all other hosts. 

- The _test suites_ are a set of tests that use the _TestCommander_ to establish the base scenario consisting of a predefined set of files and directories, as well as applying operations to the local and server replica. 

The multi-machine setup makes it easily possible to account for requirement 1. Instead of directly manipulating the _local_ and _server_ replica, we use _two_ hosts, the _remote_ and the _local_ host, and install the synchronizer and agent in each of them. The _remote host_ runs the _remote client_<sup>9</sup> , the _local host_ runs the _local client_ . The remote client is used to _indirectly_ manipulate the _server_ replica as follows: 

1. Shut down the _local_ client, to avoid concurrency effects, 

2. Start the _remote_ client, 

3. Manipulate the file system on the _remote_ host, 

4. Wait for the network activity to cease on the _remote_ host. 

The replicas and agents are shown in figure 8.2. 

In summary, the execution of a test involves the following steps: 

1. Establish a _base scenario_ , using the _TestCommander_ , 

2. Shut down _local_ client, 

3. (optional) manipulate server replica via remote replica, wait for remote client to synchronize changes (see steps 2-4 described above), 

4. (optional) manipulate _local_ replica, 

5. Start _local_ client, wait for synchronization to finish, 

6. Collect results from the local and remote agent. 

- 9“Remote client” = file synchronizer client application executed on the remote host. 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

153 



Figure 8.2: Indirect manipulation of the server replica 

##### **8.2.4.4 Test result analysis** 

The analysis of a finished tests either yields that it passed or failed. A passed test should indicate that the synchronization was successful. Informally a user will come to this conclusion if the state of the local and remote replica matches (considering directory structure and file contents, as presented by the file manager) and if the effect of the operations applied by the user to both replicas separately is observable in the final state. We use a similar assessment to decide whether a test has passed or failed. Formally, we consider the distributed system of the local, remote and server replica to be a labeled transition system ( _S_ , _O_ , _→_ ). _S_ is a set of states (each _σ ∈ S =_ ( _σl_ , _σr_ , _σs_ ) is a triplet of the local, remote and server replica state). _O_ is the finite set of operations that are part of the test. _→_ is a set of labeled transition relations ( _S× O ×S_ ) that transform one state into another. Let _σb_ be the state that represents the _base scenario_ , i.e. the set of initial files and directories established at the beginning of each test, before applying operations. 

**Definition 2.** Two states _σ_ 1, _σ_ 2 _∈ S_ are _equivalent_ , or _σ_ 1 _≡ σ_ 2, iff the directory structures (set of paths) of both states match, the type of every path (file, directory) matches and checksums (e.g. SHA-1) of _files_ match. 

We do not require the ID or _lastmodified_ meta-datum to match, because each replica uses its own values, and because we found that some synchronizers _copy_ objects they are supposed to move, which creates new IDs. The average user is typically not aware of IDs on the local disk, nor are IDs presented by the file manager. 

**Definition 3.** A necessary condition for a test to _pass_ is that _σl ≡ σr_ , where _σl_ , _σr_ are the quiescent local/remote states obtained after all test operations were applied (and the synchronizer clients have presumably propagated them). 

_CHAPTER 8. EVALUATION_ 

154 

**Definition 4.** A test that consists exclusively of _conflict-free_ operations has _passed_ iff def. 3 and _σl ≡ σo ∧σr ≡ σo_ holds, where _σo_ is the _test oracle_ state obtained by applying all test operations (for the local and remote replica) to _σb_ in a separate simulation. 

For _conflict operations_ and _cross-platform issues_ tests we do _not_ build oracles because we expect each file synchronizer to resolve the situation differently anyway, and we do not claim authority on what the most correct resolution is for each case. Here only definition 3 applies. 

##### **8.2.4.5 Implementation** 

**VM provisioning** To run all VMs in parallel a powerful host was chosen as virtualization machine. We used a Mac Pro with macOS 10.11.6 (El Capitan), 2x 2,26 GHz Quad-Core Intel Xeon, 24 GB RAM, and a SSD drive. The VMs were created using VirtualBox<sup>10</sup> v5.2.6 and Vagrant<sup>11</sup> v2.0.1, using Boxcutter<sup>12</sup> templates to generate fresh Windows guest operating systems. Each VM was configured with 2 virtual CPUs and 2 GB of memory. We installed Windows 10 Enterprise Fall Creator’s update (v1709) and macOS 10.12.6 (Sierra) as guest operating systems. We used Ubuntu server (Xenial) for the message queue server VM, running RabbitMQ<sup>13</sup> v3.5.7 as message queue server implementation. 

Machine provisioning was done using Vagrant which allows to execute provisioning commands on each boot, such as copying the current version of the _agent_ code into VM’s file system and setting up an additional _private network_ used for communication between VMs. The agents use this private network to communicate with the message queue server. 

**Network monitoring and firewall** On operating systems such as Windows we found no ready-to-use method to monitor network traffic of a specific process. We instead chose a solution that allows to query the traffic of a network interface card (NIC). To ensure that we only count traffic of synchronizer clients, we executed only one client at a time and used the Binisoft Windows Firewall Control<sup>14</sup> on Windows and the Radiosilence<sup>15</sup> firewall on macOS to limit Internet access to synchronizer clients only. Using a Firewall has additional advantages. For one, synchronizer client versions remain fixed over time, because auto-updaters shipped with the clients cannot detect or download newer versions. Another benefit is that system CPU and network load is kept constantly low, because no other auto-update mechanisms (e.g. Windows Update or macOS App Store updates) can start consuming CPU or network resources. Since we configured an additional private networking interface for each VM for the agent-to-messagequeue-server communication, we could easily exclude agent traffic in our network monitoring, by only querying the NIC traffic of the NAT interface used by the synchronizers to communicate with the server replica. 

**Agent implementation** We implemented the _agent_ program in Python, to allow re-use of components such as the file system observer used in BSync. The communication between _TestCommander_ and _agent_ was done using the celery<sup>16</sup> framework, using RabbitMQ as messaging broker. Each agent was assigned to a dedicated message queue so that the _TestCommander_ could address them separately. 

#### **8.2.5 Test descriptions and results** 

In this section we present the description and results for each test category. To improve the reading experience, some details are moved to appendix A.8 on page 219. We refer to the specific appendix sections where appropriate. 

> 10See `https://www.virtualbox.org/` , retrieved July 21, 2019. 

> 11See `https://www.vagrantup.com/` , retrieved July 21, 2019. 

> 12See `https://github.com/boxcutter/` , retrieved July 21, 2019. 

> 13See `https://www.rabbitmq.com/` , retrieved July 21, 2019. 

> 14See `https://www.binisoft.org/wfc` , retrieved July 21, 2019. 

> 15See `https://radiosilenceapp.com/` , retrieved July 21, 2019. 

> 16See `http://www.celeryproject.org/` , retrieved July 21, 2019. 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

155 

An unforeseen but very important consideration in test design was to limit the number of tests. While implementing and executing tests we discovered that some file synchronizers are very slow. It may take them up to a minute to transfer even simple changes from one replica to the other. Consequently most of the test execution time is spent waiting for quiescent states. Long waiting periods to detect these states are necessary to avoid invalid test results, causing test execution times of 3 minutes or more, per test. This means that automatic test generation as done in sections 8.1.2+8.1.3 is inappropriate. To have tests complete within finite time and to make the result analysis manageable, we instead carefully selected a small _sample_ of tests (a few _hundred_ , not thousands or more), using equivalence class partitions (ECP) [SLS14] to reduce the test count. 

##### **8.2.5.1 Conflict-free operation tests description** 

Tests of this category consist of one or more operations that do not conflict with each other. We designed four test _groups_ presented in table 8.2. Its “Param.” column contains the short-hand of _parameters_ , whose values represent different equivalence classes. The tests are run multiple times, s.t. each iteration uses a different parameter value. Table 8.3 introduces the parameters for _conflict-free operations_ tests. 

|Name|Description|Param.|Expected result|
|---|---|---|---|
|Individual<br>single-replica<br>operation|Each test executes a_single_operation on<br>the_remote_replica:_create_,_delete_,_edit_and<br>_move_. The operation affects either a_file_or<br>a_directory_(_edit_operation only affects<br>files). These tests are sanitychecks.|H, O|The_local client_applies the exact<br>same operation on the_local replica_.|
|Complex<br>single-replica<br>operations|Multiple_create_,_move_and_delete_<br>operations with complex order<br>dependencies from section 4.2.7 are<br>executed on the_remote replica_. E.g.<br>swapping the name of two files or<br>directories. See appendix section A.8.1.2<br>for more details.|H, O|The_remote_replica remains<br>unchanged. The_local_client applies<br>an_equivalent_set of operations s.t.<br>the final_local_replica state matches<br>the_remote_replica state.|
|Multi-level<br>operations|Two sets of_rename_,_move_,_create_and_edit_<br>operations are applied to a set of<br>hierarchically dependent objects. See<br>appendix section A.8.1.3 for more details.|H, O, D|The operations of a set applied to<br>one replica by our test code should<br>be applied to the corresponding<br>other replica in exactly the same<br>waybythe synchronizer.|
|Distributed<br>move and<br>edit of file|One specific synchronized_file_is only<br>moved on the_remote replica_and only<br>edited on the_local replica_.|H, O|On both replicas the file should be<br>located at the destination path of<br>the_move_operation, with the<br>updated content.|



Table 8.2: Description of conflict-free operations tests 

_CHAPTER 8. EVALUATION_ 

156 

|Parameter name,<br>values|Short-<br>hand|Description|
|---|---|---|
|_Host pair_:<br>Windows-Windows,<br>macOS-macOS,<br>Windows-macOS,<br>macOS-Windows|H|The operation systems of the local and remote host. For instance,<br>“macOS-Windows” means that the_remote_client runs on a macOS<br>VM, the_local_client on a Windows VM. Tests of the categories<br>_conflict-free operations_and_conflicts_are repeated with all four host<br>pair values. Our hypothesis is that the choice of the host pair may<br>influence the test result, because the Windows and macOS code of<br>the synchronizer may differ, or because subtle differences<br>between macOS and Windows file system APIs affect the data<br>collected during update detection and therefore affect the<br>synchronization result.|
|_Operation detection mode_:<br>online, offline|O|When “offline”, the_remote_client is first shut down, then our test<br>agent applies operations to the remote replica and finally starts<br>the remote client again. Therefore the remote client is forced to<br>detect operations from state. When “online”, the remote client is<br>kept running during the execution of operations on the remote<br>replica. It can use the log of file system events for synchronization,<br>which may yield different results.|
|_Operation distribution_:<br>same replica, distributed|D|Given two sets of non-conflicting operations, when “distributed”,<br>one set is executed on the local, the other on the remote replica.<br>Otherwise (“same replica”) both sets are executed on the_remote_<br>replica.|



Table 8.3: Test parameters for conflict-free operations tests 

##### **8.2.5.2 Conflict-free operation test results** 

The test results are shown in table 8.4. A ✓ symbol in a cell indicates that definition 4 on page 154 holds and that the final file system structure corresponds to the expected result from the test description table. The X indicates that the final file system structure is either not equal or does not match the expected result. In that case see the corresponding appendix sections for further details. Please refer to section A.8.1.1 for the remarks. 

|Test|BSync|Dropbox|Backup<br>and Sync|One-<br>Drive|Next-<br>Cloud|Unison|
|---|---|---|---|---|---|---|
|Individual<br>single-replica<br>operation (create,<br>delete, edit)|✓|✓|✓|✓<sup>A)</sup>|✓|✓|
|Individual<br>single-replica<br>operation (move)|✓|✓<sup>B)</sup>|✓<sup>C)</sup>|✓|✓|✓<sup>D)</sup>|
|Complex<br>single-replica<br>operations|✓|X|X <sup>E)</sup>|✓|X|✓<sup>D)</sup>|
|Multi-level<br>operations|✓|X<sup>F)</sup>|X|✓|X|X|
|Distributed move<br>and edit of file|✓|X<sup>G)</sup>|✓|X<sup>G)</sup>|X<sup>G)</sup>|X<sup>H)</sup>|



Table 8.4: Results of conflict-free operations tests 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

157 

##### **8.2.5.3 Conflict operation tests description** 

Each of our tests enforce a situation we classify as conflict, equal to those described in section 5.5. To limit the amount of tests, we did not build tests where files are affected by _multiple_ conflicts. 

During the execution we instruct the _agent_ to automatically create screen shots in regular intervals shortly after starting the _local_ or _remote_ client. This allows us to manually inspect whether the user is visually notified about synchronization issues or automatic decisions made by the client. We created one test per conflict, as illustrated in table 8.5. The parameters are explained in table 8.6. 

|Conflict name|Description|Param.|
|---|---|---|
|Create-Create|A file or directorynamed “test” is concurrentlycreated in each replica.|H, F, R|
|Edit-Edit|The content of synchronized file “test” is concurrently changed in each<br>replica.|H, R|
|Name clash|Forces a_Move-Create_or a_Move-Move (Dest)_conflict. For the former, the<br>_move_operation is always done on the_remote_replica, while the_create_<br>operation is done on the_local_replica. We combine these two different<br>conflict types because all tested synchronizers produce the same result for<br>both types.|H, F|
|Edit-Delete of file|Concurrently changes the content of a file in one replica and deletes it in the<br>other one.|H, C|
|Move-Delete|Concurrently moves a file or directory in one replica and deletes it in the<br>other one.|H, F, C|
|Move-<br>ParentDeleted|Moves a synchronized file “file” into a synchronized directory “test” on one<br>replica. Deletes “test” in the other replica.|H, C|
|Create-|Creates a new file “test/new” or edits an existing one at “test/file” in one|H, C|
|ParentDeleted|replica, and deletes directory“test” in the other replica.||
|Move-Move<br>(Source)|Moves a synchronized file or directory “test” to “testMoved” in one replica<br>and to “testOtherMoved” in the other replica.|H, F|
|Move-Move|Given the synchronized directories “A” and “B”, we move “A” to “B/A_moved”|H|
|(Cycle)|on the_remote_replica and move “B” to “A/B_moved” on the_local_replica.||



Table 8.5: Description of conflict operations tests 

_CHAPTER 8. EVALUATION_ 

158 

|Parameter name,<br>values|Short-<br>hand|Description|
|---|---|---|
|_Object type mix:_<br>file-file, file-directory,<br>directory-directory,<br>file, dir|F|When testing conflicts,_different_objects are concurrently<br>manipulated at the local and remote replica. For instance,<br>“file-directory” indicates that the operation applied to one<br>replica affects a file, while the operation applied to the other<br>replica affects a directory. For tests that target a_specific_, already<br>synchronized object,_file_or_dir_means that operations are<br>applied to a file or directoryrespectively.|
|_File content randomization_:<br>random, deterministic|R|When testing_create-create_or_edit-edit_conflicts, “deterministic”<br>means both files are created (or updated) with the same file<br>content in both replicas (which Syncpal counts as_pseudo_<br>_conflict_), whereas “random” generates different, randomized<br>contents.|
|_Delete on replica choice_:<br>remote, local|C|When testing conflicts that involve a_delete_operation (e.g.<br>Move-Delete or Edit-Delete), this parameter decides whether the<br>_delete_operation is applied to the_local_or_remote replica_.|



Table 8.6: Test parameters for conflict operations tests 

The table in appendix section A.8.2.1 provides a list of alternative expected outcomes. 

##### **8.2.5.4 Conflict operation test results** 

Before discussing test results, table 8.7 presents how each synchronizer client notifies the user about conflicts as they happen and whether users can inspect conflicts after the fact (after the notification has disappeared). We note that the table does not include _Unison_ because here the user manually triggers the synchronization process and both conflicting and non-conflicting operations are graphically presented in a table. The “Not synced” tab of NextCloud’s client GUI is used to show both _unsynchronizable_ files due to _namespace limitations_ (such as files using Windows reserved names or characters) and _conflict_ files. Figure 8.3 illustrates the GUI users can use to inspect conflicts for our BSync implementation, NextCloud and Unison. 

||BSync|Dropbox|Backup<br>and Sync|One-Drive|Next-<br>Cloud|
|---|---|---|---|---|---|
|Notification about conflict|✓|X|X|✓|✓|
||Windows,|||Only|Windows,|
||macOS|||Windows|macOS|
|GUI to inspect conflicts|✓|X|X|X|(✓)|



Table 8.7: Conflict notifications 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

159 





<!-- Start of picture text -->
(a) Unison<br>(b) BSync (our implementation)<br>(c) NextCloud<br><!-- End of picture text -->

Figure 8.3: User interfaces used to visualize conflicts 

Graphical user interfaces provided by Unison, NextCloud and BSync to inspect conflict detections or resolutions (after the fact). Subfigure a (Unison) shows two non-conflicting _create_ operations (of a file and directory) as well as the detection of an _Edit-Edit_ conflict. The user needs to choose from a set of options for resolving the conflict (left to right, right to left, skip, merge). 

_CHAPTER 8. EVALUATION_ 

160 

Table 8.8 shows the detailed test results. For detailed explanations of the cell labels (Merge, Duplicate, ...) and further remarks see appendix sections A.8.2.1 and A.8.2.2. Unexpected or wrong out-of-sync results are marked in **bold** . 

|Conflict name|Param.|Our impl.|Dropbox|Backup<br>and Sync|OneDrive|Next-<br>Cloud|Unison|
|---|---|---|---|---|---|---|---|
|Create-Create|F=file-file,<br>R=random|Rename<br>A)|Rename<br>B)|Rename<br>C)|Rename<br>D)|Rename<br>E)<br>|Detected<br>F)|
||F=file-file,<br>R=determ.|Merge|Merge|Merge|Merge|Merge<sup>G)</sup>|Merge|
||F=dir-dir|Merge|Merge|Merge|Merge|Merge|Merge|
||F=file-dir|Rename<br>A)|Rename<br>B)|Rename<br>C)|Rename<br>D)|**Out of**<br>**sync** <sup>H)</sup>|Detected<br>F)|
|Edit-Edit|R=random|Overwrite|Duplicate-<br>Sync|Duplicate-<br>Sync|Duplicate-<br>Sync|Duplicate-<br>HostOnly<br>|Detected<br>F)|
||R=determ.|Merge|Merge|**Duplicate-**<br>**Sync**|Merge|Merge<sup>G)</sup><br>|Merge|
|Name clash|F=dir-dir|Rename<br>A)|Merge|Rename,<br>**out of**<br>**sync** <sup>I)</sup>|Rename<br>D)|Merge<sup>G)</sup>|Merge|
||F=file-file,<br>file-dir|Rename<br>A)|Rename<br>B)|Rename,<br>**out of**<br>**sync** <sup>I)</sup>|Rename<br>D)|Rename,<br>**out of**<br>**sync** <sup>J)</sup>|Detected<br>K)|
|Edit-Delete of<br>file|C=any|Restore|Restore|Delete<br>|Restore|Restore|Detected<br>F)|
|Move-Delete|F=file,<br>C=any|Restore|Restore|Local<sup>N)</sup><br>|Restore|Restore|Restore|
||F=dir,<br>C=any|Restore|Restore|Local<sup>N)</sup><br>|Local|Restore|Restore|
|Move-<br>ParentDeleted,|C=any|Delete|Restore|Local<sup>L)</sup>|Restore|Restore|Detected<br>M)|
|Create-<br>ParentDeleted||||||||
|Move-Move<br>(Source)|F=file|Remote|**Duplicate**|Remote|Remote /<br>**Duplicate**<br>O)|**Duplicate**|**Duplicate**|
||F=dir|Remote|**Duplicate**|Remote|Remote|**Duplicate**|**Duplicate**|
|Move-Move<br>(Cycle)|–|Remote|**Out of**<br>**sync** <sup>P)</sup>|**Out of**<br>**sync** <sup>P)</sup>|Remote|**Out of**<br>**sync** <sup>P)</sup>|Detected<br>Q)|



Table 8.8: Results for conflict operations tests 

##### **8.2.5.5 Cross-platform issue tests description** 

We implemented one or more tests for each of the following five cross-platform issues: 

- Case-sensitivity: Windows is always case-insensitive. macOS disk volumes, by default, are caseinsensitive as well. However, macOS allows to create case-sensitive disk volumes. We create one and configure the synchronizer to use it, if possible. Our tests explore how synchronizers deal with upper/lower-case file name clashes and whether conflicts are correctly detected if names only differ regarding their upper/lower-case between replicas. 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

161 

- Unicode normalization: Windows disk volumes are both Unicode normalization preserving and sensitive. On macOS disk volumes formatted with HFS+ are neither sensitive nor preserving as macOS always normalizes Unicode special characters to a NFD-like version. Our tests investigate whether Unicode special characters are automatically normalized to the form that is natively used on the corresponding platform and how synchronizers handle duplicated file names that only differ in their normalization form. 

- Reserved characters and names: Windows provides a large set of reserved characters and names that can be created without problems on macOS, including issues such as short (8.3) filenames. 

- Maximum name length limit: on both Windows and macOS a file name’s maximum length is 255 characters. We test whether synchronizers take this limit into account in situations where they proactively extend a file’s name, e.g. when creating conflict copies of files. 

- Windows 8.3 file names: 8.3 file names (or _short filenames_ , SFN) are a relict from the DOS (Disk Operating System) era. Given an existing file “my longer text.myext”, Windows forbids creating (or moving other objects to) “MY_LON~1.MYE”. This is counter-intuitive, because “MY_LON~1.MYE” is not part of the parent directory’s listing returned by the file system APIs. On macOS, no such limitations exist. Our tests explore how synchronizers deal with situations where corresponding files are created or moved on macOS and then synchronized to Windows. 

We refer to section 3.1 on page 29 where we present multiple expected results a client might implement to handle each issue. Like for _conflict operation_ tests (see section 8.2.5.3) _cross-platform_ tests also instruct the agent to automatically create screen shots. 

- 1) Case-sensitivity: 

|Name|Description|Parameters|
|---|---|---|
|Case-sensitivity<br>clash|On macOS (_remote_replica) we create two files or directories<br>with equal name but varying upper/lower case (one<br>upper-case, one lower-case object). If F=directory, we create a<br>sub-file named “lower” or “upper” in the corresponding<br>directory.|F,<br>H=macOS-macOS,<br>macOS-Windows|
|Case-sensitivity<br>conflict|On one replica we apply CreateFile(f), on the other we apply<br>CreateFile(F). This needs to cause a conflict on case-insensitive<br>volumes (solved by automatically renaming one file), but not<br>necessarilyon case-sensitive ones.|H=macOS-macOS,<br>macOS-Windows|



2) Unicode normalization: 

_CHAPTER 8. EVALUATION_ 

162 

|Name|Description|Parameters|
|---|---|---|
|Unicode<br>normalization -<br>clash|On Windows (_remote_replica) we create two files named “ä” and<br>“ä” (one normalized using the NFC, one using the NFD<br>normalization). This can be synchronized to another Windows<br>host, but needs to be handled on macOS where both<br>normalizations would clash.|H=Windows-<br>macOS,<br>Windows-Windows|
|Unicode<br>normalization -<br>Windows<br>conversion|On Windows (_remote_replica) we create file “ä” with the<br>NFD-normalization (untypical for Windows) to observe<br>whether the client performs an automatic conversion. We<br>expect the client to either convert the file name to the typical,<br>platform-native NFC normalization on_both_the remote and<br>local Windows host, or to skip conversion and use the<br>NFD-form on_both_Windows hosts.|H=Windows-<br>Windows|
|Unicode<br>normalization -<br>macOS conversion|On macOS (_remote_replica) we create file “ä” (which macOS<br>always normalizes as NFD) to observe whether the local<br>replica’s Windows client converts the normalization form to the<br>Windows-native NFC form.|H=macOS-Windows|



##### 3) Reserved characters and names: 

|Name|Description|Parameters|
|---|---|---|
|Reserved characters<br>and names|On macOS (_remote_replica) we create a file that contains “?”, as<br>a representative for_reserved characters_, assuming that a<br>synchronization client will behave equally for any reserved<br>character. We also create the files "LPT1" and "LPT1.foo.bar" to<br>test_reserved names_. Finally we create files "a ." and "b " to test<br>handling of names that end with “.” or space. We expect a<br>synchronizer to either automatically rename those files, or to<br>skip their synchronization. In any case, they should notify the<br>user about their decision.|H=macOS-macOS,<br>macOS-Windows|



##### 4) Name length limit: 

|Name|Description|Parameters|
|---|---|---|
|Name length limit -<br>conflict names|We produce a_Create-Create_conflict for two random-content<br>files whose name length is 252 characters, being very close to<br>the 255 limit. We test whether the_local_client (who discovers<br>the conflict) truncates the original file’s name before<br>appending the “conflict” suffix that is typically used when<br>resolvingsuch conflicts, see section 8.2.5.4.|H|
|Name length limit -<br>temporary names|This test only applies to BSync and OneDrive. Their local client<br>can correctly apply_complex single-replica operations_(see<br>section 8.2.5.1) which involve having to temporarily rename<br>objects to a randomly generated name to make other necessary<br>_move_operations possible. This test runs the_“Move Occupied 1”_<br>test described in appendix section A.8.1.2. For instance,<br>OneDrive creates a directory “1180107144710-A” which it<br>finally renames to “A” after applying other_move_operations. We<br>test whether the synchronization is still successful if directory<br>“A”’s name is instead 253 characters long.|H=Windows-macOS|



_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

163 

##### 5) Windows 8.3 file names: 

|Name|Description|Parameters|
|---|---|---|
|Windows 8.3 file<br>names - distributed<br>creation|On the_remote_replica we create a file “aaaaaa~1” which uses a<br>short file name pattern. The_remote_client transmits the file to<br>the_server_replica. On the_local_replica we create a long file<br>“aaaaaaaaaa” and start the_local_client, to examine whether it<br>detects (and how it handles) the issue that “aaaaaa~1” cannot<br>be created on the_local_replica.|H=Windows-<br>Windows|
|Windows 8.3 file<br>names - macOS<br>clash|The initial file system consists of two synchronized files named<br>“aaaaaaaaaa” and “b”. On macOS (_remote replica_) we apply the<br>operation_Move(b, aaaaaa~1)_and observe the result on the<br>_local replica_, where this_move_operation is forbidden by<br>Windows.|H=macOS-Windows|



##### **8.2.5.6 Cross-platform issue test results** 

The detailed test result are shown in table 8.9, with shorthands: NN = No Notification, IN = Inconsistent Names. 

Unexpected or wrong results are marked in **bold** . For remarks and referenced figures, see appendix section A.8.3.1. 

#### **8.2.6 Summary and discussion** 

Using our test framework we compared the convergence behavior of _BSync_ , _Dropbox_ , _NextCloud_ , Google _Backup and Sync_ , Microsoft _OneDrive_ and _Unison_ . We designed three test categories (conflict-free operations, conflict operations and cross-platform issues), each made of several tests. We built a setup that consists of four virtual machines (2x Windows, 2x macOS) where the corresponding synchronizer clients are installed. A separate test computer runs the automated tests, sending commands to test agents we developed that are installed in the VMs as well. These agents execute these commands, such as operations that modify the file system, or start or stop a specific synchronizer client. We limited the number of tests using Equivalence Class Partitioning. The test execution time was still considerably long. Conflict and cross-platform tests took about 12 hours each, conflict-free tests took about 28 hours, for a _single_ run. Because we repeated all tests three times, added run-time amounts to one week. Additional time was required for manual result analysis. In the remainder of this section we summarize our findings. 

##### **8.2.6.1 Synchronizer-specific issues** 

We found that some synchronizers show behavior that affects the result of several (if not all) test categories. 

The _Backup and Sync_ client appends a numeric suffix such as “ (1)” to the name of objects whenever there is a problem with using the originally intended name. This suffix is only used on _one_ , not all, replicas. This causes names to be inconsistent on both replicas. The _Dropbox_ client does not seem to use an internal _ID-based_ file system model (such as _F_ ) but a _path-based_ one, which we discovered in our _multi-level_ conflict-free operation tests. To avoid performance bottlenecks, Dropbox seems to use heuristics to identify moved _files_ , such as comparing checksums, which allows it to send _move_ operations to the server, rather than deleting and re-uploading such files. Once the Dropbox client _receives_ a _move_ operation from the server, it never physically moves the affected object on the local replica, but first _copies_ the local file(s) to the destination, followed by either deleting the local source or moving it to an internal cache where it is kept for a varying amount of time before it is finally deleted. This behavior degrades the performance of _move_ operations that affect _large_ files or directories and consumes additional disk space. The Unison client works similarly in this regard, and it is known that its internal file system model is path-based [BP98]. 

_CHAPTER 8. EVALUATION_ 

164 

|Test name|Param.|Our impl.|Dropbox|Backup<br>and Sync|One-Drive|Next-<br>Cloud|Unison|
|---|---|---|---|---|---|---|---|
|Case-<br>sensitivity<br>clash|F=any,<br>H=macOS-<br>macOS|Remote<br>rename|Remote<br>rename,<br>NN|Un-<br>changed<br>|–<sup>A)</sup>|Un-<br>changed<br>|Skipped<sup>B)</sup><br>|
||F=any,<br>H=macOS-<br>Windows|Remote<br>rename|Remote<br>rename,<br>NN|**IN** <sup>C)</sup>||Skipped<sup>D)</sup>|Skipped<sup>B)</sup>|
|Case-<br>sensitivity|H=macOS-<br>macOS|Autom.<br>rename|Autom.<br>rename|No conflict|Autom.<br>rename|No conflict|Detected<br>G)|
|conflict|H=macOS-<br>Windows|Autom.<br>rename|Autom.<br>rename|**IN** <sup>E)</sup>|Autom.<br>rename|**Out of**<br>**sync** <sup>F)</sup>|Detected<br>G)|
|Unicode nor-<br>malization -<br>clash|H=Windows-<br>Windows|Remote<br>rename|Remote<br>rename,<br>NN|Unchanged<br>|**Out of**<br>**sync** <sup>H)</sup>|**Out of**<br>**sync** <sup>H)</sup>|**Out of**<br>**sync** <sup>I)</sup>|
||H=Windows-<br>macOS|Remote<br>rename|Remote<br>rename,<br>NN|**IN** <sup>J)</sup>|**Out of**<br>**sync** <sup>H)</sup>|**Out of**<br>**sync** <sup>H)</sup>|**Out of**<br>**sync** <sup>I)</sup>|
|Unicode nor-<br>malization -<br>Windows<br>conversion|H=Windows-<br>Windows|Autom.<br>con-<br>version<br>K)|Autom.<br>con-<br>version<sup>K)</sup>,<br>NN|No con-<br>version|No con-<br>version|**Normali-**<br>**zation**<br>**mismatch**<br>L)|**Normali-**<br>**zation**<br>**mismatch**<br>L)|
|Unicode nor-<br>malization -<br>macOS<br>conversion|H=macOS-<br>Windows|Autom.<br>con-<br>version|Autom.<br>con-<br>version|**No con-**<br>**version**<br>M)|Autom.<br>con-<br>version|Autom.<br>con-<br>version|Autom.<br>con-<br>version|
|Reserved<br>characters<br>and names|H=macOS-<br>macOS|Remote<br>rename|Trims<br>white-<br>space of<br>file “b “<sup>N)</sup>|Unchanged|Skips chars<br>(syncs<br>names)<sup>O)</sup>|Unchanged|Unchanged|
||H=macOS-<br>Windows|Remote<br>rename|Trims<br>white-<br>space<sup>N)</sup>,<br>NN<sup>P)</sup>|**IN, creates**<br>**un-**<br>**deletable**<br>**files** <sup>Q)</sup>|Skips chars<br>and names<br>O)|Skips chars<br>and names<br>on<br>Windows<br>R), NN|Skips chars<br>and names<br>on<br>Windows<br>R)|
|Name length<br>limit -<br>conflict<br>names|H=any|Truncates<br>file name|**Out of**<br>**sync** <sup>S)</sup>|**IN** <sup>T)</sup>|Truncates<br>file name|**Out of**<br>**sync** <sup>S)</sup>|–|
|Name length<br>limit -<br>temporary<br>names|H=Windows-<br>macOS|Truncates<br>file name|–|–|**Hangs**<br>indefini-<br>tely<sup>U)</sup>|–|–|
|Win 8.3 file<br>names - distr.<br>creation, see<br>figure A.14|H=Windows-<br>Windows|Remote<br>rename<sup>V)</sup>|Skips SFN<br>(local), NN|**IN**|Autom.<br>Rename|Skips SFN<br>(local)|Skips SFN<br>(local)<sup>W)</sup>|
|Win 8.3 file<br>names -<br>macOS clash,<br>see fig. A.15|H=macOS-<br>Windows|Remote<br>rename<sup>V)</sup>|Removes<br>file “b”<br>(local)|**IN**|**SFN**<br>**replaces**<br>**long file**<br>**name**|**Out of**<br>**sync**|**Out of**<br>**sync**|



Table 8.9: Results for cross-platform issues tests 

_8.2. FILE SYNCHRONIZER COMPARATIVE TEST_ 

165 

##### **8.2.6.2 Conflict-free operations** 

We found that all synchronizers were able to synchronize _individual_ operations correctly. However, only our implementation and OneDrive managed to correctly apply _complex operation sequences_ or _multilevel operations_ to the other replica. Other implementations showed various flaws, such as becoming out of sync temporarily or permanently, or even crashing completely. The distributed _edit_ and _move_ operation applied to a file was only merged by BSync and Google’s implementation - all others produced two files: the old file at the new location and the new file at the old location. 

##### **8.2.6.3 Conflict operations** 

We observed several cases of unexpected behavior when clients detect or resolve conflicts. 

Our tests show that _NextCloud_ could not handle _Create-Create_ conflicts in cases where a file and a directory was created on the first and second replica respectively. Some _name clash_ conflicts also caused issues with NextCloud, s.t. different replicas became out of sync. 

The _OneDrive_ client showed inconsistent behavior for _Move-Delete_ conflicts. The client always _restored_ the object in case it is a _file_ , but always favored the locally applied operation in case it is a _directory_ . All other synchronizers applied the _same_ resolution strategy, irrespective of whether the affected object is a file or directory. 

Apart from BSync and OneDrive, all other synchronizers had issues dealing with _Move-Move (Source)_ and _Move-Move (Cycle)_ conflicts appropriately. They either duplicated directory structures, which clutters the file system, or became out of sync. For _OneDrive_ ’s client we discovered that the resolution of _MoveMove (Source)_ conflicts is done differently on _macOS_ vs. _Windows_ . 

Considering conflict awareness we observed that most synchronizers display little (graphical) information about conflicts and their resolution to the user. Apart from BSync only Unison and NextCloud provided a GUI to inspect conflicts. Unison’s GUI showed _detected_ conflicts, providing the user with multiple choices for _resolving_ them. NextCloud’s GUI offered a window that shows entries for _resolved_ conflicts, providing the affected file name and an explanation, but only for those types of conflicts the client solved by appending a _conflict-suffix_ to the file name. Most notably, conflicts such as _Move-Delete_ or _Edit-Delete_ (whose resolution does not involve appending this suffix) are _not_ found in this GUI. In contrast BSync offers a GUI with detailed conflict information for _all_ conflict types. We also found that the only synchronizers that showed real-time _notifications_ while conflicts happen are BSync, NextCloud and OneDrive’s _Windows_ client. 

As expected, we found a high degree of variance in the automatic conflict resolution applied by the industrial synchronizers. While all implementations agreed on how to deal with _Create-Create_ conflicts, there was no consensus on how other conflict types should be resolved. 

##### **8.2.6.4 Cross-platform issues** 

When one replica is case-sensitive and one is case-insensitive, we found that _NextCloud_ and _Backup and Sync_ had issues with detecting and resolving case-sensitivity clashes. 

Except for BSync, all other synchronizers had issues dealing with _Unicode normalization_ , or handling clashes of duplicate names that only differ by their normalization. The clients either went into an unexpected error state, or they did not inform the user about automatically applied rename operations or skipped synchronizations of affected files. 

There is no consensus regarding the handling of Windows’s reserved characters and names. Some synchronizers transferred _all_ those objects to the _server_ replica but then omitted their synchronization on the Windows replica, while others (such as OneDrive) skipped sending reserved _characters_ to the server replica, but did send reserved _names_ . Some implementations applied automatic renaming (that turns reserved characters into allowed ones) already on the macOS replica when encountering those reserved names or characters. We observed that some synchronizers applied _consistent_ rename-schemes, while others only renamed some reserved characters but not others. 

_CHAPTER 8. EVALUATION_ 

166 

Apart from our implementation, all synchronizers had issues with some of our tests regarding Windows 8.3 short file names or using names with a length that is close to the limit of 255 characters. 

##### **8.2.6.5 Discussion** 

As our results have shown, other synchronizers often did not correctly synchronize concurrent operations accumulated during longer offline periods. This is particularly relevant in practice for users who frequently work offline. Such bugs cause irritation for the user and lower their trust in the synchronizer<sup>17</sup> . If any error messages were shown at all (for instance, _Dropbox_ never did and failed silently throughout all tests), they were as helpful as “Unknown error” or “Upload error” (Backup and Sync, NextCloud) which is not indicative of the cause. These errors and messages indicate that synchronizer developers did not thoroughly analyze the file system model. Our own thorough analysis of file system model _F_ made it not only possible to avoid these issues, but also helped to design the tests. 

We think that further insights may be gained from a dialog between researchers and industry, in particular for conflict handling. It would be interesting to discuss why products such as Dropbox and Google’s Backup and Sync hide conflicts from the user, given that these companies have UX specialists who know how to design usable software. The dialog may also reveal insights for the specific conflict resolution approaches used in each synchronizer. 

### **8.3 Synchronization and conflict statistics** 

As discussed in section 7.1 on page 129, our implementation, BSync, has been in productive use since July 2017 by close to 30 users. We performed an empirical evaluation by collecting various statistics to answer the following questions: 

- Can we confirm the findings of other works [SS05; WRB01; KS92; Rei+94] which state that file system conflicts are rare in practice? 

- Which of the ten conflicts discussed in chapter 5 are most common? We assume that Edit-Edit conflicts occur most often, from personal observation and asking other users. 

- Considering conflict- _free_ operations, how is BSync used by its users? What is the distribution of operation types? Is the number of _create_ operations outweighed by the sum of _edit_ , _move_ and _delete_ operations? 

- How often do _pseudo_ conflicts occur in practice? Was the effort justified to implement pseudo conflict detection? 

In the following subsections we discuss related work, how we collected statistics data and present our results. 

#### **8.3.1 Related work analysis** 

There are numerous works [SS05; WRB01; KS92; Rei+94] which re-affirm the use of optimistic replication for file systems. They found that its main disadvantage, _conflicts_ , are not actually a problem in practice, because they occur rarely. The cited works can be divided into theoretical analyses and empirical evaluations. The former is done in [WRB01] where the authors built a mathematical model used in a simulation where synchronization and file updates occur regularly. The simulation then manipulates the synchronization interval _µ_ , while files are regularly updated (with a different rate) in each replica. # _of conflicts_ They found that _s = synchronization_<sup>increases with</sup><sup>_µ_.The relationship is not linear, but</sup><sup>_s_asymptotically</sup> approaches a limit. Consequently, when _µ_<sup><u>1</u>is the number of reconciliations per time unit, the conflict</sup> 

> 17We are aware of multiple anecdotes (and have first hand experience) where users stop relying on file synchronizers doing their job correctly after working offline, because of mishaps. Users then copy files they work on out of the synchronized directory and finally copy it back in once they are online again. 

_8.3. SYNCHRONIZATION AND CONFLICT STATISTICS_ 

167 

rate _r = s ∗ µ_<sup><u>1</u>is small not only for small values of</sup><sup>_µ_(which is intuitive:synchronizing often will cause a</sup> very small _s_ , and thus _r_ is also small), but also for large values of _µ_ (synchronizing very rarely also causes _r_ to be small). There is a worst-case for some _µ_ where _r_ is largest. 

Other works provide empirical evidence. In [KS92] write update conflicts were only found in less than 0.5% of all write operations. In [Rei+94] the relative conflict frequency was even smaller, stated as _∼_ 0.0035%. However, this figure only covers _non-directory_ updates and ignores name-collision conflicts which were automatically resolved at an earlier stage. The authors also found that _“an environment in which some machines are often disconnected will generate more conflicts”_ , which confirms the propositions of [WRB01]. Another discovery in [Rei+94] is that such averaged numbers are misleading. _“Conflicts tended to happen more often to users who worked with the disconnected machines. A few users thus experienced much higher conflict rates, while many users encountered considerably fewer conflicts than the average.”_ [Rei+94]. 

#### **8.3.2 Data collection** 

In BSync we implemented a log that contains all successfully executed operations and resolved conflicts. The log is regularly transmitted to a central server for data analysis. For non-conflicting operations, the log entries include the following information: 

- Operation name (“create”, “edit”, “move”, “delete”) and affected replica (local, remote), 

- Omit flag: if True, then the operation is a _pseudo conflict_ operation, False otherwise, 

- File extension, e.g. “.pdf”, or “dir” in case the affected object is a directory. 

For conflicts the log entries include these items: 

- Conflict type (“Create-Create”, ...), 

- Local and remote file extension, 

- Loser replica identifier (local, remote). 

For both conflicts and non-conflicting operations we always transmitted an anonymized user ID. Any information that allows to identify individual users was omitted, such as file or account names. Unfortunately this made it difficult to answer questions like “what user activity causes conflicts?”, as this would require user interviews, but we did not know the user’s identity. We note that users are able to opt out of the statistics collection in the settings dialog of BSync. 

#### **8.3.3 Results** 

We now present the analysis of the statistics data we collected in the time period of July 2017 to December 2018. We note that the results of every analysis strongly depends on the environment, i.e., the users and the way they work. The discussed user base with close to 30 users consisted predominantly of property administration staff, who work with tools like Autodesk Revit or WSCAD. The files produced by these tools are then synchronized by BSync. The second user group (about one third of the users) were mostly software engineers who use BSync for any kind of files _other than_ source code, e.g. documents and images. Source code management is delegated to expert tools, such as Git. 

##### **8.3.3.1 Operation analysis** 

Figure 8.4 breaks down the 793<sup>_′_</sup> 973 conflict-free operations logged by BSync. The innermost ring shows the operation type distribution. The next ring breaks each operation type down into real vs. pseudoconflicting operations. The outermost ring breaks the real operations down into local vs. remote operations. _Local operations_ refer to operations that were detected on the server and thus propagated to the user’s device. The enclosed table provides values rounded to 0.5%. 

_CHAPTER 8. EVALUATION_ 

168 



|Name|Operations|Relative|Pseudo conflicting,<br>relative|Local operations<br>(relative to non<br>pseudo conflicting)|
|---|---|---|---|---|
|Create|666’735|84%|25%|72%|
|Edit|74’645|9.5%|17%|37.5%|
|Move|35’276|4.5%|0.5%|83.5%|
|Delete|17’317|2%|3.5%|54%|



Figure 8.4: Break down of operations 

Contrary to our assumptions the _create_ operation makes up the largest portion of all operations (84%). There are several explanations for this. First, 25% of the _create_ operations are _pseudo_ conflicting operations, which were created when the user configured a new folder pair, but already possessed the data on the local disk. These operations simply reflect the rebuilding of the persisted state, see section 5.4.2 on page 76 for more details. Second, some users treated BSync as _backup_ software for a large number of (smaller) files, such as images, which were never modified again. Third, applications such as Autodesk Revit or WSCAD create a large number of temporary files and folders which they seem to never delete. Because the log entries do not contain directory names we do not know the extent of create operations that can be explained by the last two arguments. 

Our mechanism to detect and resolve _pseudo_ conflicts has proven beneficial to avoid real conflicts. As the table in figure 8.4 shows, _create_ and _edit_ operations have a considerable amount of such pseudo conflicts. We note that it is unlikely that two users independently _edited_ a specific file resulting in the exact same content. A deeper analysis of these _edit_ operations revealed that 84% of them affect files which change very frequently on the local disk, because they are continuously being written to, e.g. lock files or other temporary files. When they change _during synchronization_ while BSync is online, the problem 

_8.4. COMPLEXITY AND PERFORMANCE ANALYSIS_ 

169 

discussed in section 5.4.2 on page 76 applies again. The synchronizer detects that the file changed while it was uploaded, aborts the transfer and restarts the synchronization. The next synchronization iteration detects that the file has been edited on both the server and the local disk, because the previous transfer did finish (was not truly aborted). 

##### **8.3.3.2 Conflict analysis** 

An overview of the 951 conflicts is shown in figure 8.5, together with the most common file extensions for Edit-Edit conflicts. The inner ring of figure 8.5a shows the conflict type distribution. The outer ring uses a different color for each unique user experiencing the conflict. 

The resulting chance for conflicts per operation is 793951<sup>_<u>′</u>_</sup> 973<sup>_=_0.12%,whichisinthesamerangeasin</sup> [Rei+94; KS92]. We can also confirm the statement of [Rei+94] that conflicts are concentrated on a few users. 79.3% of all conflicts were experienced by _two_ users. The chance for conflicts is 1.88% for the user who experienced most of the conflicts (shown in pure green in figure 8.5a), which is 15 times larger than the average. Interestingly, most Edit-Edit conflicts occurred for users who predominantly worked _online_ , which is contradictory to previous statements that assumed that conflicts occur the longer the _offline_ period is. The three most affected file extensions, shown in figure 8.5b, are extensions used by the WSCAD application. When two distinct users work on a file in parallel in WSCAD, the synchronizer is only notified of the _edit_ operation (and can detect conflicts for it) once the file is _saved_ , which happens either due to the user’s request, or when some condition triggers WSCAD to automatically save it. Since it is possible that a user may work on such a file without saving it for, say, an hour, the chance that some other user also works on that file (and saves it) within that time period is not negligible, causing such conflicts. 

### **8.4 Complexity and performance analysis** 

To determine the practical viability we evaluate both the complexity and performance of Syncpal. We start with a complexity analysis using the Big-O notation in section 8.4.1, followed by performance measurements of our BSync implementation in section 8.4.2. 

#### **8.4.1 Complexity analysis** 

Let _n = count_ ( _ids_ ( _db_ )) _+count_ ( _ids_ ( _snapshot_ )) be the number of objects. _h_ is the height of the update trees, and _k_ is the number of operations detected by _compute_  ops_ (). We start with _memory_ complexity analysis, which is straightforward. Syncpal generates _snapshots_ and _update tree_ structures, each with _n_ objects, as well as _k_ operation objects. Thus, memory complexity is _O_ ( _n + k_ ). For the _computational_ complexity we break down the analysis for each stage separately, followed by analyzing the overall complexity. 

We start with _update detection_ whose complexities are shown in table 8.10. Building snapshots is _O_ ( _n_ ) 

|Step|Average case|Worst case|
|---|---|---|
|Build snapshot|_O_(_n_)|_O_(_n_)|
|_compute_ops(db, snapshot)_|_O_(_n_)|_O_(_n_)|
|Generate update trees (step1-7, step8)|_O_(_k·h_),_O_(_n·h_)|_O_(_n_<sup>2</sup>)|
|Generate update trees (total)|_O_(_n·h_)|_O_(_n_<sup>2</sup>)|
|Total|_O_(_n·h_)|_O_(_n_<sup>2</sup>)|



Table 8.10: Complexities of update detection stage 

because we have to iterate over all objects in the file system (or database), which is _O_ ( _n_ ), and add them to the snapshot structure, which is _O_ (1). The _compute_ops()_ function, see algorithm 1 on page 49, iterates over all objects in the snapshots twice and creates _k_ operations. We can approximate _k = n_ , even though in practice this only holds in the _first_ synchronization iteration. Typically, _k ≪ n_ in consecutive 

_CHAPTER 8. EVALUATION_ 

170 





<!-- Start of picture text -->
(a) Break down of conflict types<br>(b) Most common file extensions for Edit-Edit conflicts<br><!-- End of picture text -->

Figure 8.5: Conflict analysis 

_8.4. COMPLEXITY AND PERFORMANCE ANALYSIS_ 

171 

iterations. When generating update trees, creating and inserting a new node into a tree is _O_ ( _h_ ). Steps 1-7 depend on _k_ and step 8 depends on _n_ , so in an average case, complexity is _O_ (( _k +n_ ) _· h_ )). In the worst case, where the file system resembles a _degenerate_ tree, i.e., _h = n_ , the complexity is _O_ ( _n_<sup>2</sup> ). 

Table 8.11 shows the complexities of _finding_ conflicts during reconciliation, see algorithms presented in appendix A.6 on page 211. We distinguish between the very first synchronization iteration ( `first_sync` 

|Step|Average case|Worst case|
|---|---|---|
|_corresponding_node_direct_(),_node_._get_child_()|_O_(1)|_O_(1)|
|_corresponding_object_id()_|_O_(_h_)|_O_(_n_)|
|Find Move-Move (Cycle) conflict|_O_(_k_<sup>2</sup>)|_O_(_n_<sup>2</sup>)|
|Find all other conflicts (`first_sync`is True)|_O_(_n·h_)|_O_(_n_<sup>2</sup>)|
|Find all other conflicts (`first_sync`is False)|_O_(_n_)|_O_(_n_<sup>2</sup>)|
|Total (`first_sync`is True)|_O_(_n·h_)|_O_(_n_<sup>2</sup>)|
|Total (`first_sync`is False)|_O_(_k_<sup>2</sup> _+n_)|_O_(_n_<sup>2</sup>)|



Table 8.11: Complexities of _finding_ conflicts 

is True) and consecutive iterations. In the first iteration only Create-Create conflicts can exist, and _corresponding_object_id()_ is used to find them. The average complexity in this case is _O_ ( _n· h_ ) rather than _O_ ( _k· h_ ), because all _n_ objects are detected as created in the first iteration, thus _k = n_ . In consecutive iterations, finding conflicts is _O_ ( _n_ ) for all conflicts except _Move-Move (Cycle)_ , because we iterate over all _n_ objects, but finding a conflict for a specific object is _O_ (1). Create-Create conflicts may still exist, but now finding them involves calling _corresponding_node_direct()_ rather than _corresponding_object_id()_ . Complexities of _resolving_ conflicts depend on the conflict, as shown in table 8.12. 

|Conflict|Average case|Worst case|
|---|---|---|
|Create-Create, Move-Create, Edit-Edit, Edit-Delete,<br>Move-Move (Dest),|_O_(1)|_O_(1)|
|Undo move: Move-Move (Source/Cycle), Move-Delete<br>(when_delete_operation wins)|_O_(_h_)|_O_(_n_)|
|Move-Delete (when_move_operation wins)|_O_(_n_)|_O_(_n_)|
|Create-ParentDelete|_O_(_k_)|_O_(_n_)|
|Move-ParentDelete|_O_(_k·h_)|_O_(_n_<sup>2</sup>)|
|Total (take worst of all above)|_O_(_n_)|_O_(_n_<sup>2</sup>)|



Table 8.12: Complexities of _resolving_ conflicts 

For the _propagation_ stage, if _sorting_ is not required, complexity is _O_ ( _k_ ). However, if sorting is required, we need to analyze _sort_operations()_ , whose complexities are shown in table 8.13. Considering algo- 

|Step|Average case|Worst case|
|---|---|---|
|Sortingiterations (outer loopin algorithm 2)|_O_(1)|_O_(_k_)|
|_fix_xyz()_(inner loop)|_O_(_k_<sup>2</sup>)|_O_(_k_<sup>2</sup>)|
|_fix_impossible_first_move_op()_, section 6.3.2.5|_O_(1)|_O_(_n_)|
|_find_complete_cycles()_,_break_cycle()_(inner loop)|_O_(_k_)|_O_(_k_)|
|Total|_O_(_k_<sup>2</sup>)|_O_(_n +k_<sup>3</sup>)|



Table 8.13: Complexities of _sort_operations()_ in propagation stage 

rithm 2 on page 58 and theorem 1 on page 58, we see that the outer loop is executed at most _k_ times. The _fix_xyz()_ functions in the inner loop are _O_ ( _k_<sup>2</sup> ) each, _find_complete_cycles()_ and _break_cycle()_ are 

_CHAPTER 8. EVALUATION_ 

172 

_O_ ( _k_ ), which overall yields _O_ ( _k_<sup>3</sup> ) for _sort_operations()_ . In practice, _cycles_ are rare and sorting completes in a single iteration, thus _sort_operations()_ is typically _O_ ( _k_<sup>2</sup> ). 

Table 8.14 shows the overall complexity of Syncpal, assuming that there are _c_ synchronization iterations. The complexity of the very first synchronization is lower because no sorting is needed. As long as there 

|Case|Average case|Worst case|
|---|---|---|
|First synchronization (only_create_operations)|_O_(_c·n·h_)|_O_(_c·n_<sup>2</sup>)|
|Normal synchronization|_O_(_c·_(_n·h +k_<sup>2</sup>))|_O_(_c·_(_n_<sup>2</sup> _+k_<sup>3</sup>))|



Table 8.14: Complexities of Syncpal algorithm 

are only create operations (non-conflicting or pseudo-conflicting) only order dependency rule 7 applies. However, the generation of operations described in section 6.2 is done by breadth first iteration, which implicitly satisfies this rule. In practice, when only a small set of changes needs to be synchronized, _c =_ 1 and _k_ is so small that even _k_<sup>2</sup> is irrelevant. Thus, the average case complexity is _O_ ( _n· h_ ). 

#### **8.4.2 Performance analysis** 

We built an automated test that determines execution times and memory usage while it runs. The goal is to determine the maximum feasible number of objects and operations, and to identify potential optimizations. Our test first creates _n =_ [100,1<sup>_′_</sup> 000,10<sup>_′_</sup> 000,100<sup>_′_</sup> 000] directories on the root level on one replica, followed by synchronizing them to the other one. Then _k =_ [100,1<sup>_′_</sup> 000,10<sup>_′_</sup> 000,100<sup>_′_</sup> 000] operations are executed on one replica and then synchronized to the other one. We evenly divide _k_ into _createdir_ , _delete_ and _move_ operations. We are interested in execution times and memory usage incurred when building snapshots, detecting operations ( _compute_ops()_ ), building update trees, finding conflicts (note that our test scenario does not have any), generating Syncpal operation and sorting them. From the user’s perspective, synchronization consists of only two stages, the _planning_ stage that builds the list of operations (which entails all steps we just mentioned) and the _execution_ stage, which propagates all planned operations. We are only interested in performance of the _planning_ stage, because the execution stage’s duration depends entirely on the operations themselves<sup>18</sup> and we can provide visual progress feedback to the user. Such feedback is _not_ available for the _planning_ stage, consequently this stage should be as short as possible. A user would stop using a synchronizer if planning took several minutes for every change, or consumed large amounts of memory. 

##### **8.4.2.1 Result analysis** 

Results for the memory use are shown in table 8.15, execution times are given in table 8.16. 

The first bottleneck regarding execution time is _snapshot creation_ . While filling a snapshot is efficient ( _O_ (1)), obtaining meta-data for each object is expensive. On a local disk this requires opening and closing a file handle on Windows, or performing a _stat()_ call on macOS, which becomes noticeable ( _t >_ 0.27 s) for _n >_ 1<sup>_′_</sup> 000. On a remote file system addressed using WebDAV obtaining meta-data is even more 

18For instance, transferring large files takes much longer than moving objects or creating directories. 

|Artifact|Memory<br>use (MB)|Growth|Notes|
|---|---|---|---|
|Snapshot|70|Linear with_n_|Use _per replica_.|
|Operation list from<br>_compute_ops()_|1|Linear with_k_|–|
|Update tree|50|Linear with_n_|Use _per replica_.|



Table 8.15: Measured memory use for _n =_ 100<sup>_′_</sup> 000 

_8.4. COMPLEXITY AND PERFORMANCE ANALYSIS_ 

173 

|Step|Execution<br>time<br>(seconds)|Growth|Notes|
|---|---|---|---|
|Snapshot creation|_∼_27|Linear with_n_|Time_per replica_. Measured on a Solid<br>State Disk.|
|_compute_ops()_|_∼_1|Linear with_n_|–|
|Update treegeneration|_∼_3|Linear with_n·h_|Time _per replica_.|
|Reconciliation (find<br>platform<br>inconsistencies, find<br>conflicts, build Syncpal<br>operations)|_∼_10|Linear with_n_,<br>quadratic with_k_|The 10 seconds evenly distribute<br>among the three sub steps. Measured<br>for_k ≤_10<sup>_′_</sup>000.|
|Operation sorting|_∼_160|Quadratic with_k_|Measured_after_applying_k =_10<sup>_′_</sup>000<br>operations (also measured<br>_k =_100,1<sup>_′_</sup>000 but aborted with<br>_k =_100<sup>_′_</sup>000).|



Table 8.16: Measured execution times for _n =_ 100<sup>_′_</sup> 000 

expensive, as it requires network calls for _each directory_ . We note that our _hybrid update detection_ approach presented in section 4.3 on page 59 mitigates this issue in practice, because the expensive snapshot creation is done only during the very first synchronization iteration. 

The second execution time bottleneck is _operation sorting_ . Sorting 10<sup>_′_</sup> 000 operations or more is infeasible, as the user would assume that the synchronizer has crashed. However, in practice we observed that reconciliation typically either produces a large number of _create_ operations exclusively (which don’t require sorting), or a small number (usually below 100) of operations of mixed type, where sorting completes in a fraction of a second. 

Considering memory use, the bottlenecks clearly are _snapshots_ and _update trees_ . For instance, synchronizing a directory with _n =_ 100<sup>_′_</sup> 000 objects would incur 4 _∗_ 70 _+_ 1 _+_ 2 _∗_ 50 _=_ 381 MB, because 4 snapshots and 2 update trees are required. 

##### **8.4.2.2 Future optimizations** 

Our first suggestion is to reduce memory use. By moving snapshots from memory to a relational database and implementing the _compute_ops()_ algorithm using SQL queries, the memory use can be reduced considerably. However, execution time is expected to increase by one order of magnitude. To reduce the memory footprint of update trees we can omit step 8 of the update tree generation procedure and instead only update missing meta-data of _intermediate_ nodes from _dbsnapshot_ . However, additional work is required to verify that finding conflicts and sorting still works on such _incomplete_ trees, and the _Platform Inconsistency Checker_ module needs to be updated to efficiently find inconsistencies using incomplete update trees. 

The latter suggestion also has a beneficial impact on _execution time_ . As the update tree generation time mostly depends on the number of nodes, omitting step 8 will reduce the complexity from _O_ ( _n· h_ ) to _O_ ( _k· h_ ). Omitting step 8 will also speed up reconciliation time, as the majority of time is spent iterating over all _n_ nodes. When instead iterating over the _incomplete_ tree, i.e., over _k_ nodes, this reduces the average case complexity from _O_ ( _k_<sup>2</sup> _+ n_ ) to _O_ ( _k_<sup>2</sup> _+ k_ ) _= O_ ( _k_<sup>2</sup> ). 

Optimizing sorting is left as future work. 

_CHAPTER 8. EVALUATION_ 

174 

### **8.5 Conclusion** 

This chapter provided a technical evaluation of BSync, our implementation of Syncpal, as well as other industrial-grade synchronizers. Except for section 8.4.1 we employed automated testing or data collection combined with a manual result analysis. 

We found that the tests from section 8.1, which focus on our own implementation, are a valuable complement to the theoretical proofs. They uncovered several kinds of coding errors. Some affected parts of Syncpal which were formally proven to be correct but were incorrectly translated to code. Others affected modules that were not part of the proofs (e.g. the update tree generation routines). The tests also helped to confirm or refute assumptions, such as the one discussed in section 6.3.2.5. They also uncovered an incorrect definition of the _Move-Move (Cycle)_ conflict (see section 5.5.10), which we originally defined with an additional precondition that requires that directories _A_ and _B_ are _hierarchically independent_ in _dbsnapshot_ . However, our random tests in section 8.1.3 found counter examples where the conflict holds, even when _A_ and _B_ are hierarchically dependent. 

Looking outwards, testing other synchronizers revealed a plethora of issues when faced with extended offline periods or platform inconsistencies, which negatively affect their usability. The results thus validate our motivation and confirm all those issues discussed in section 1.1. Our implementation significantly improves usability for the discussed problems. 

Finally, our complexity and performance analysis done in section 8.4 revealed numerous future optimizations. These were not implemented yet, as it is good engineering practice to focus on features and correctness first and optimize the code later. Some memory and performance bottlenecks, such as linear growth of complexity with _n_ , affect _state-based_ synchronizers in general, not just BSync. Even leading industrial products with large software engineering teams have not been able to overcome them. For instance, the help documents of Dropbox and OneDrive warn users that the synchronizer’s performance degrades when synchronizing more than 300’000 or 100’000 objects respectively<sup>19</sup> . In practice BSync did not yet encounter performance bottlenecks, as even large synchronized BSCW workspace directories contained less than 50<sup>_′_</sup> 000 objects. Future optimizations will still be necessary when rolling out to a wider audience. 

> 19See `https://support.microsoft.com/en-us/help/3125202` for OneDrive and `https://help.dropbox.com/space/ file-storage-limit` for Dropbox, retrieved July 21, 2019. 

175 

## **Chapter 9** 

# **Conclusion and outlook** 

This work was driven by the motivation to build a file synchronizer for end-users. Compared to stateof-the-art academic works and industrial products it should provide a better handling of heterogeneous file systems and synchronize long offline periods correctly. To show that we have achieved this goal with our Syncpal algorithm we revisit the research questions and discuss the effect of our findings on research and industry in section 9.1. There are still some limitations illustrated in section 9.2. In section 9.3 we present future work, which is derived from these limitations as well as suggestions made by our users who used BSync in practice for several years. We finally conclude this work in section 9.4. 

### **9.1 Discussion of research questions** 

**RQ1 - File systems:** What different kinds of file system definitions exist in academia and practice? Which criteria are relevant for file synchronizers? How should a file synchronizer’s internal, abstracted file system model look like, which incompatibilities exist and how can they be handled? 

The file system models used internally in _academic_ file synchronizers have shown to be dissimilar. Some works model files and directories as uniquely identifiable objects, others use anonymous paths. The mechanisms used to link objects in the file system tree may differ. The existence of _directories_ as a distinct entity varies between works, and there is a disagreement regarding the set of available operations, including their pre- and postconditions. We extended our analysis to implementations found in _practice_ , such as NTFS or WebDAV, which have shown further traits of heterogeneity. These include varying namespace limitations, as well as different mechanisms to manipulate meta-data or lock objects. We proposed a method to build an internal model with maximum compatibility to existing real-world file systems, by integrating compatible traits and suggesting methods to handle incompatible ones, e.g. by adding objects to an ignore list. Our comprehensive heterogeneity analysis and methods for handling incompatibilities improve the _usability_ of file synchronizers by providing _awareness_ to the user. In contrast, several existing industrial systems ignore some of these heterogeneous traits, causing permanent replica discrepancies which degrades usability. To the best of our knowledge we are the first to discuss heterogeneity for _file systems_ and file synchronizers in academia, thus providing a foundation for prospective works. 

**RQ2 - Operation order:** As operations detected during state-based update detection lack order, but not all operations are commutative, how can a valid order be detected and propagated by a synchronizer? This question was already answered for file system models which do not support _move_ operations, but remains open for file systems which do support them. 

We first solved the problem of finding a valid operation order for the simplified case of _uni_ -directional synchronization, where only one replica is manipulated. Using the formally defined pre- and postcon- 

_CHAPTER 9. CONCLUSION AND OUTLOOK_ 

176 

ditions of the operations of our file system model we found a number of operation order dependency rules<sup>1</sup> , by analyzing all possible operation combinations. As our file system model includes the _move_ operation, these rules deviate from those of related works that omit this operation. We found that our rules can form _cycles_ and proposed a mechanism that breaks them by injecting a temporary rename operation. Our approach also works for _bi_ -directional synchronization, but requires minor adaptations to compensate for cross-replica side effects of the _move_ operation. Our evaluation of leading industrial synchronizers confirmed that our approach substantially improves the efficiency and correctness of synchronization in practice, when several operations accumulate due to long offline periods. Detected _move_ operations are retained, and their synchronization is more efficient than re-transmitting files. Syncpal does not exhibit anomalous behavior found in other academic and industrial solutions, such as temporary or permanent synchronization loops which cause divergent file system replicas. 

**RQ3 - Conflicts:** What sets of operations applied to two disconnected file systems are conflicting? How do conflicts depend on the file system model? Can multiple conflicts be combined? What are possible solutions for resolving individual conflicts and conflict combinations? Which conflicts are relevant in practice? How can conflicts be explained to the user? 

Conflicts between operations depend on the specific operations offered by the file system model. Using operation precondition analysis we found ten different conflicts types for our internal file system model. We discovered that the lower a file system model’s complexity, the fewer conflict types exist. We designed and applied a four-step framework that derives how to _resolve_ conflicts. It allows to build a _coherent_ and _reflected_ set of conflict resolution steps, superior to the _arbitrary_ resolution steps we found in related academic works. We designed an _iterative_ conflict resolution approach which solves one conflict at a time, manipulating the operation on the losing (rather than the winning) replica. This avoids negative side effects of the resolution on other conflicts which we observed in industrial synchronizers and keeps the implementation of resolving each individual conflict simple. Instead of limiting Syncpal to one specific resolution approach we designed and implemented several criteria for selecting the winner or loser replica. We are also the first in academia to discuss conflict _combinations_ where multiple conflicts affect a specific object. To deal with such situations we extended our iterative resolution approach to first sort conflicts according to their type. We built an optimal sort order that maximizes the preservation of the users’ intentions by analyzing all possible resolution orders for different conflict type combinations. 

We found that Edit-Edit and Create-Create conflicts were most relevant in practice for our test-users. These conflicts were predominantly produced a single application, WSCAD, but not due to _offline_ work, but by WSCAD which delays committing the user’s work to files in parallel _online_ work. We designed a tabular log that explains the conflicts and their resolution to the user in a graphical user interface. While leading industrial synchronizers provide little or no conflict-related information our conflict handling and their visualization improves the conflict recovery process for end-users. 

### **9.2 Limitations** 

We start with presenting two major limitation that apply to _state-based_ file synchronizers in general, followed by providing limitations specific to Syncpal. 

The first limitation is the performance hit already encountered during the _update detection_ stage. Memory and execution times depend on the number of objects _n_ . Regardless how well an implementation is optimized, a user might want to synchronize a directory that has too many objects for the implementation to handle. To overcome this limitation, users either have to cooperate and synchronize only a sub-tree of the file system, or concepts such as _bubbling modification timestamps_ [LKT05] can be used, which allow to identify the specific sub-trees that changed since the last synchronization, so that update detection can be limited to those sub-trees. The second inherent limitation is that files are _black-boxes_ 

1For instance, the _createdir_ operation of a parent directory needs to precede the creation of its children. 

_9.3. FUTURE WORK_ 

177 

that make it impossible for the synchronizer to merge conflicting _edit_ operations. However, even if the synchronizer understood the data model of a file, automatic merging would still be undesired because it is limited to syntactic merging. Ultimately, merging is an AI-complete problem. 

The following is a list of Syncpal- or BSync-specific limitations: 

- When incompatibilities between two different file systems are found, BSync does not always implement the most user-friendly alternative to resolve them. For instance, when BSync detects objects with more than one link, it stops and asks the user to delete all but one, instead of adding links an _ignore list_ . 

- Replicas synchronized by Syncpal may not be arranged in _cyclic_ configurations. For instance, given replicas _X_ , _Y_ , _Z_ we may synchronize _X ↔ Y_ and _Y ↔ Z_ . Thus _X ↔ Z_ implicitly holds, by transitivity. However, we may not synchronize _X ↔ Z_ explicitly. This may cause non-conflicting situations to be detected as real conflicts, because operations detected in _X_ may reach _Z_ several times, out of order. To overcome this limitation, Syncpal would need to keep a _history_ of operations, to be able to identify out-of-order operations. We ignore this limitation, because not only would a solution incur additional time and space complexity, but most synchronization configurations (in practice) are set up using a client-server _star_ topology, where this problem does not occur. 

- Because Syncpal cannot lock file systems for exclusive access, there is a chance that concurrent file system modifications made by the user invalidate operations executed by Syncpal. We implemented some checks to minimize this chance, but they cannot entirely eliminate it. 

- When a user edits _and_ moves/renames a file while being offline, and the _edit_ operation changes the file’s ID (e.g. to safely replace it), Syncpal’s state-based update detection incorrectly detects the two operations as _delete_ + _create_ operation. 

### **9.3 Future work** 

Discussions with our users revealed several use-cases where synchronization needs to be _limited_ to a sub-set of objects. For instance, when a user has only _read-only_ permissions to one of the replicas, the synchronization _direction_ should be uni-directional. This requires major adaptations to Syncpal, which is inherently built with bi-directional synchronization in mind. Users also requested the ability to save space, by limiting the synchronization to smaller files or directories. This could be realized by implementing an _ignore list_ feature. An even better approach is the use of _placeholder files_ , where all files and directories are visible on the user’s local disk, but files are empty placeholders that are downloaded (and kept in sync) only on-demand. This approach has also been implemented by various industrial file synchronizers<sup>2</sup> . The _ignore list_ is still needed for limitations that cannot be handled by placeholder files, such as ignoring extra links on file systems that support only one link per file, or ignoring files whose names are reserved. 

Another requested feature was to improve _control_ over the synchronization process such that it is no longer fully automatic. A user may not trust a fully automatic synchronizer [How93], or the connection to the server might be metered. This can be addressed with a user-controlled synchronization mode that is semi-automatic. It holds off from automatically synchronizing until some condition is met. For instance, the user might set a pause interval during which synchronization is completely stopped, or define that update detection stays active but synchronization only starts after the file system’s state was quiescent for at least five minutes. Syncpal might also ask the user for confirmation based on a set of rules, e.g. to confirm the synchronization of _delete_ operations. 

A lot of future work is left to improve _conflict handling_ , such as implementing additional user-proposed or cascaded criteria to choose the conflict winner, or retroactively applying different conflict resolutions 

2See e.g. Microsoft OneDrive’s _Files On-Demand_ feature, Google’s _Drive File Stream_ application, or Dropbox’s _Smart Sync_ feature. 

_CHAPTER 9. CONCLUSION AND OUTLOOK_ 

178 

using learning techniques. We also plan to investigate approaches that _avoid_ conflicts to begin with. This can be achieved by providing better _awareness_ about concurrent user activity, e.g. a warning if another user opened the same file(s) as we did. Pessimistic concurrency control, such as locks, is another alternative. Integrating BSync into the operating system’s file manager would allow to use overlay icons to convey status information and a file’s lock state and offer custom actions (made available in the context menu), such as the ability to lock a file. Any changes affecting the user interface should be _evaluated with users_ , to complement the rather _technical_ evaluations we performed in this work. 

Finally, some users were dissatisfied with BSync’s performance during _propagation_ . Propagation was particularly slow when transferring many small files, or transferring a large file where only small portions changed. The performance can be improved significantly by implementing _client capabilities_ [BDM15] such as _chunking_ , _delta encoding_ and _bundling_ . We note that this requires changes to both the client and server replicas, where changing the latter might not be possible. As work-around users suggested the ability to prioritize specific files or directories such that they are transferred first. 

### **9.4 Closing words** 

Optimistic replication has many advantages, most notably improved availability, which enables users to work offline. State-based file synchronizers such as Syncpal are universally applicable, because they allow to replicate the state of _any_ third-party application. However, the synchronization granularity stops at the _file_ level, ignoring the _data model_ of the third-party application that creates those files. There are a few tailored applications whose synchronization is aware of the data model, but they require the application’s author to integrate the synchronization process natively into the software. To simplify this process several frameworks have emerged that make integration of data synchronization techniques such as Operational Transformation and CRDTs easier. They range from replicated databases such as Couchbase Lite or CouchDB<sup>3</sup> , to web frameworks like Y.js<sup>4</sup> that run in the browser, to SDKs for mobile apps<sup>5</sup> . Mobile apps are an excellent use-case for replication to mitigate negative effects of intermittently losing connectivity due to unreliable cellular networks. However, integrating optimistic replication is inherently difficult. The application’s data model and its operations may be _very_ elaborate, which in turn requires complicated conflict handling. Many of these applications that integrated data synchronization, such as Google Docs, are known to misbehave under certain conditions and result in a divergent state [BMP17]. Having to transform and store the data model in a replicated database requires additional effort. Due to the increased complexity developers often shy away from using replication and synchronization. Consequently, file-based applications, which ignore these concepts, are here to stay, and file synchronizers will continue to play an important role in users’ lives. As we have shown, the synchronizer’s lacking ability to deal with the application’s data model can be mitigated with the help of usability tweaks, like providing helpful awareness and intelligent conflict handling. Our suggestions are an important first step towards a better user experience for file synchronizers. By sparing users the work and frustration to find and fix synchronization errors, our work avoids cost-intensive iterations in cooperation processes for both academia and industry. 

> 3See `https://www.couchbase.com/products/lite` and `http://couchdb.apache.org/` , retrieved July 21, 2019. 

> 4See `http://y-js.org/` , retrieved July 21, 2019. 

> 5See e.g. `https://aerogear.org/services/data-sync/` , `http://www.ensembles.io/` , `https://firebase.google. com/` , or `https://realm.io/` . Retrieved July 21, 2019. 

179 

# **Bibliography** 

- [ABF08] Paulo Sérgio Almeida, Carlos Baquero, and Victor Fonte. “Interval Tree Clocks”. In: _Principles of Distributed Systems_ . Ed. by Theodore P. Baker, Alain Bui, and Sébastien Tixeuil. Berlin, Heidelberg: Springer Berlin Heidelberg, 2008, pp. 259–274. ISBN: 978-3-540-92221-6. 

- [AC08] Michał Antkiewicz and Krzysztof Czarnecki. “Design Space of Heterogeneous Synchronization”. In: _Generative and Transformational Techniques in Software Engineering II: International Summer School, GTTSE 2007, Braga, Portugal, July 2-7, 2007. Revised Papers_ . Ed. by Ralf Lämmel, Joost Visser, and João Saraiva. Berlin, Heidelberg: Springer Berlin Heidelberg, 2008, pp. 3–46. ISBN: 978-3-540-88643-3. DOI: `10.1007/978-3-540-88643-3_1` . URL: `https://doi.org/10.1007/978-3-540-88643-3_1` . 

- [Agu+08] Ng Agustina, Fei Liu, Steven Xia, Haifeng Shen, and Chengzheng Sun. “CoMaya. Incorporating Advanced Collaboration Capabilities into 3D Digital Media Design Tools”. In: _Proceedings of the 2008 ACM Conference on Computer Supported Cooperative Work_ . CSCW ’08. New York, NY, USA: ACM, 2008, pp. 5–8. ISBN: 978-1-60558-007-4. DOI: `10.1145/1460563. 1460566` . URL: `http://doi.acm.org/10.1145/1460563.1460566` . 

- [Ale+13] Christopher Alexander, Sara Ishikawa, Murray Silverstein, and Max Jacobson. _A pattern language. Towns, buildings, construction_ . 36. print. Vol. 2. Center for Environmental Structure series. Ishikawa, Sara, (Author.) Silverstein, Murray, (Author.) New York, NY: Oxford Univ. Press, ca. 2013. XLIV, 1171 S. ISBN: 978-0195019193. 

- [Amj04] Hasan Amjad. “Combining model checking and theorem proving”. University of Cambridge, Computer Laboratory, 2004. URL: `https://www.cl.cam.ac.uk/techreports/ UCAM-CL-TR-601.pdf` . 

- [AMU12] Mehdi Ahmed-Nacer, Stéphane Martin, and Pascal Urso. “File system on CRDT”. In: _CoRR_ abs/1207.5990 (2012). 

- [And03] George E. Andrews. _The theory of partitions_ . Digital printing. Cambridge Mathematical Library. Cambridge: Cambridge university press, 2003. XVI, 255 s. ISBN: 0-521-63766-X. 

- [Ape+11] Sven Apel, Jörg Liebig, Benjamin Brandl, Christian Lengauer, and Christian Kästner. “Semistructured merge”. In: _Proceedings of the 19th ACM SIGSOFT symposium and the 13th European conference on Foundations of software engineering - SIGSOFT/FSE ’11_ . the 19th ACM SIGSOFT symposium and the 13th European conference (Szeged, Hungary). Ed. by Tibor Gyimóthy and Andreas Zeller. New York, New York, USA: ACM Press, 2011, p. 190. ISBN: 9781450304436. DOI: `10.1145/2025113.2025141` . 

- [App04] Apple Inc. _Technical Note TN1150_ . HFS Plus Volume Format. 2004. URL: `https : / / developer.apple.com/legacy/library/technotes/tn/tn1150.html` . 

- [App06] Apple Inc. _OS X 10.9 Man Pages: chflags_ . 2006. URL: `https://developer.apple.com/ legacy/library/documentation/Darwin/Reference/ManPages/man1/chflags.1. html` . 

- [App10] Apple Inc. _OS X 10.9 Man Pages: xattr_ . 2010. URL: `https://developer.apple.com/ legacy/library/documentation/Darwin/Reference/ManPages/man1/xattr.1. html` . 

- 180 _BIBLIOGRAPHY_ [App17] Apple Inc. _Apple File System Guide - FAQs_ . 2017. URL: `https://developer.apple.com/ library/content/documentation/FileManagement/Conceptual/APFS_Guide/FAQ/ FAQ.html` . 

- [Art+06] Thomas Arts, John Hughes, Joakim Johansson, and Ulf Wiger. “Testing telecoms software with quviq QuickCheck”. In: _Proceedings of the 2006 ACM SIGPLAN workshop on Erlang_ . Portland, Oregon, USA: ACM, 2006, pp. 2–10. ISBN: 1-59593-490-1. DOI: `10.1145/1159789. 1159792` . 

- [ASB15] Paulo Sérgio Almeida, Ali Shoker, and Carlos Baquero. “Efficient State-Based CRDTs by Delta-Mutation”. In: _Networked Systems_ . Ed. by Ahmed Bouajjani and Hugues Fauconnier. Cham: Springer International Publishing, 2015, pp. 62–76. ISBN: 978-3-319-26850-7. 

- [AT16] Marcos K. Aguilera and Douglas B. Terry. “The Many Faces of Consistency”. In: _IEEE Data Eng. Bull._ 39 (1 2016), pp. 3–13. 

- [Bal+16] Valter Balegas, Cheng Li, Mahsa Najafzadeh, Daniel Porto, Allen Clement, Sérgio Duarte, Carla Ferreira, Johannes Gehrke, João Leitão, Nuno Preguiça, Rodrigo Rodrigues, Marc Shapiro, and Viktor Vafeiadis. “Geo-Replication. Fast If Possible, Consistent If Necessary”. In: _IEEE Data Engineering Bulletin_ 39 (1 2016). Computer Science [cs]/Other [cs.OH]Journal articles, p. 12. URL: `https://hal.inria.fr/hal-01350652` . 

- [Bao+11] X. Bao, N. Xiao, W. Shi, F. Liu, H. Mao, and H. Zhang, eds. _SyncViews: Toward Consistent User Views in Cloud-Based File Synchronization Services_ . 2011 Sixth Annual Chinagrid Conference. 2011 Sixth Annual Chinagrid Conference. 2011. 89-96. ISBN: 1949-131X. DOI: `10. 1109/ChinaGrid.2011.35` . 

- [Bas18] Basho. _RIAK Key Value Database Homepage_ . 2018. URL: `http://basho.com/products/ riak-kv/` . 

- [BDM15] Enrico Bocchi, Idilio Drago, and Marco Mellia. “Personal Cloud Storage Benchmarks and Comparison”. In: _IEEE Transactions on Cloud Computing_ 5 (4 2015). Oct, pp. 751–764. ISSN: 2168-7161. DOI: `10.1109/TCC.2015.2427191` . 

- [BF93] Steve Benford and Lennart Fahlén. “A Spatial Model of Interaction in Large Virtual Environments”. In: _Proceedings of the Third Conference on European Conference on ComputerSupported Cooperative Work_ . ECSCW’93. Norwell, MA, USA: Kluwer Academic Publishers, 1993, pp. 109–124. ISBN: 0-7923-2447-1. URL: `http://dl.acm.org/citation.cfm?id= 1241934.1241942` . 

- [BHT97] Richard Bentley, Thilo Horstmann, and Jonathan Trevor. “The World Wide Web as Enabling Technology for CSCW. The Case of BSCW”. In: _Computer Supported Cooperative Work (CSCW)_ 6 (2 1997). Jun, pp. 111–134. ISSN: 1573-7551. DOI: `10.1023/A:1008631823217` . URL: `https://doi.org/10.1023/A:1008631823217` . 

- [Bil05] Philip Bille. “A survey on tree edit distance and related problems”. In: _Theoretical Computer Science_ 337 (1-3 2005), pp. 217–239. ISSN: 03043975. DOI: `10.1016/j.tcs.2004.12.030` . 

- [Bjø07] Nikolaj Bjørner. “Models and Software Model Checking of a Distributed File Replication System”. In: _Formal Methods and Hybrid Real-Time Systems_ . Ed. by Cliff B. Jones, Zhiming Liu, and Jim Woodcock. Vol. 4700. Lecture Notes in Computer Science. Berlin, Heidelberg: Springer Berlin Heidelberg, 2007, pp. 1–23. ISBN: 978-3-540-75220-2. DOI: `10.1007/9783-540-75221-9_1` . 

- [BK08] Christel Baier and Joost-Pieter Katoen. _Principles of model checking_ . Cambridge, Mass.: MIT Press, 2008. 1 online resource (xvii, 975. ISBN: 978-0-262-02649-9. 

- [BMM94] T. Berners-Lee, L. Masinter, and M. McCahill. _Uniform Resource Locators (URL)_ . RFC 1738 (Proposed Standard) RFC dec, Obsoleted by RFCs 4248, 4266, updated by RFCs 1808, 2368, 2396, 3986, 6196, 6270, 8089. Fremont, CA, USA: RFC Editor and RFC Editor, 1994. DOI: `10. 17487/RFC1738` . URL: `https://www.rfc-editor.org/rfc/rfc1738.txt` . 

_BIBLIOGRAPHY_ 

181 

- [BMP17] Marina Billes, Anders Møller, and Michael Pradel. “Systematic black-box analysis of collaborative web applications”. In: _Proceedings of the 38th ACM SIGPLAN Conference on Programming Language Design and Implementation - PLDI 2017_ . the 38th ACM SIGPLAN Conference (Barcelona, Spain). Ed. by Albert Cohen and Martin Vechev. New York, New York, USA: ACM Press, 2017, pp. 171–184. ISBN: 9781450349888. DOI: `10.1145/3062341. 3062364` . 

- [BP98] S. Balasubramaniam and Benjamin C. Pierce. “What is a File Synchronizer?” In: _Proceedings of the 4th Annual ACM/IEEE International Conference on Mobile Computing and Networking_ . MobiCom ’98. New York, NY, USA: ACM, 1998, pp. 98–108. ISBN: 1-58113-035-X. DOI: `10.1145/288235.288261` . URL: `http://doi.acm.org/10.1145/288235.288261` . 

- [BR94] Gordon S. Blair and Tom Rodden. “The Challenges of CSCW for Open Distributed Processing”. In: _Proceedings of the IFIP TC6/WG6.1 International Conference on Open Distributed Processing II_ . Amsterdam, The Netherlands, The Netherlands: North-Holland Publishing Co, 1994, pp. 127–140. ISBN: 0-444-81861-8. URL: `http://dl.acm.org/citation.cfm? id=648101.748729` . 

- [Bre00] Eric A. Brewer. “Towards robust distributed systems (abstract)”. In: _Proceedings of the nineteenth annual ACM symposium on Principles of distributed computing_ . Portland, Oregon, USA: ACM, 2000, p. 7. ISBN: 1-58113-183-6. DOI: `10.1145/343477.343502` . 

- [Bre12] E. Brewer. “CAP twelve years later: How the rules have changed”. In: _Computer_ 45 (2 2012), pp. 23–29. ISSN: 0018-9162. DOI: `10.1109/MC.2012.37` . 

- [Cel+16] Antonio Celesti, Maria Fazio, Massimo Villari, and Antonio Puliafito. “Adding long-term availability, obfuscation, and encryption to multi-cloud storage systems”. In: _Journal of Network and Computer Applications_ 59 (2016), pp. 208–218. ISSN: 10848045. DOI: `10.1016/j. jnca.2014.09.021` . 

- [CH06] Yek Loong Chong and Youssef Hamadi. “Distributed log-based reconciliation”. In: _ECAI_ . Vol. 141. 2006, pp. 108–112. 

- [CJ05] Russ Cox and William Josephson. _File Synchronization with Vector Time Pairs_ . 2005. [CKD10] George F. Coulouris, Tim Kindberg, and Jean Dollimore. _Distributed systems. Concepts and design_ . 4. ed., 6. impr. International computer science series. Harlow [u.a.]: Addison-Wesley, 2010. XIV, 927 S. ISBN: 0321263545. 

- [Cla08] Edmund M. Clarke. “The Birth of Model Checking”. In: _25 Years of Model Checking: History, Achievements, Perspectives_ . Ed. by Orna Grumberg and Helmut Veith. Berlin, Heidelberg: Springer Berlin Heidelberg, 2008, pp. 1–26. ISBN: 978-3-540-69850-0. DOI: `10.1007/9783-540-69850-0_1` . URL: `https://doi.org/10.1007/978-3-540-69850-0_1` . 

- [CLD16] Y. Cui, Z. Lai, and N. Dai. “A first look at mobile cloud storage services. Architecture, experimentation, and challenges”. In: _IEEE Network_ 30 (4 2016), pp. 16–21. ISSN: 0890-8044. DOI: `10.1109/MNET.2016.7513859` . 

- [Cra08] Matt Craighead. _Windows vs. Unix File System Semantics_ . 2008. URL: `http : / / www . conifersystems.com/2008/10/21/windows-vs-unix-file-system-semantics/` . 

- [Csi16] Elod Csirmaz. _Algebraic File Synchronization. Adequacy and Completeness_ . jan, 2016. URL: `https://arxiv.org/pdf/1601.01736.pdf` . 

- [CSS09] Federico Cabitza, Carla Simone, and Marcello Sarini. “Leveraging Coordinative Conventions to Promote Collaboration Awareness”. In: _Comput. Supported Coop. Work_ 18 (4 2009), pp. 301–330. ISSN: 0925-9724. DOI: `10.1007/s10606-009-9093-z` . 

- [DB92] Paul Dourish and Victoria Bellotti. “Awareness and coordination in shared workspaces”. In: _Proceedings of the 1992 ACM conference on Computer-supported cooperative work - CSCW ’92_ . the 1992 ACM conference (Toronto, Ontario, Canada). Ed. by Marilyn Mantel and Ron Baecker. New York, New York, USA: ACM Press, 1992, pp. 107–114. ISBN: 0897915429. DOI: `10.1145/143457.143468` . 

_BIBLIOGRAPHY_ 

182 

- [DeC+07] Giuseppe DeCandia, Deniz Hastorun, Madan Jampani, Gunavardhan Kakulapati, Avinash Lakshman, Alex Pilchin, Swaminathan Sivasubramanian, Peter Vosshall, and Werner Vogels. “Dynamo. Amazon’s highly available key-value store”. In: _Proceedings of twenty-first ACM SIGOPS symposium on Operating systems principles - SOSP ’07_ . twenty-first ACM SIGOPS symposium (Stevenson, Washington, USA). Ed. by Thomas C. Bressoud and M. Frans Kaashoek. New York, New York, USA: ACM Press, 2007, p. 205. ISBN: 9781595935915. DOI: `10.1145/1294261.1294281` . 

- [Dou96] James Paul Dourish. “Open implementation and flexibility in CSCW toolkits”. phd. London: University of London, 1996. 

- [DP02] Brian A. Davey and Hilary A. Priestley. _Introduction to lattices and order_ . Cambridge university press, 2002. 

- [DP08] David Dearman and Jeffery S. Pierce. “It’s on my other computer! Computing with multiple devices”. In: _Proceedings of the SIGCHI Conference on Human Factors in Computing Systems_ . Florence, Italy: ACM, 2008, pp. 767–776. ISBN: 978-1-60558-011-1. DOI: `10.1145/1357054. 1357177` . 

- [Dro16] Dropbox Inc. _Celebrating half a billion users_ . 2016. URL: `https://blogs.dropbox.com/ dropbox/2016/03/500-million/` . 

- [Dro17] Dropbox Inc. _Dropbox for HTTP Developers. The Dropbox API v2_ . 2017. URL: `https://www. dropbox.com/developers/documentation/http/overview` . 

- [Dro18] Dropbox Inc. _Dropbox Homepage_ . 2018. URL: `https://www.dropbox.com` . [DSL02] Aguido Horatio Davis, Chengzheng Sun, and Junwei Lu. “Generalizing operational transformation to the standard general markup language”. In: _Proceedings of the 2002 ACM conference on Computer supported cooperative work - CSCW ’02_ . the 2002 ACM conference (New Orleans, Louisiana, USA). Ed. by Elizabeth F. Churchill, Joe McCarthy, Christine Neuwirth, and Tom Rodden. New York, New York, USA: ACM Press, 2002, p. 58. ISBN: 1581135602. DOI: `10.1145/587078.587088` . 

- [Dus07] L. Dusseault. _HTTP Extensions for Web Distributed Authoring and Versioning (WebDAV)_ . RFC 4918 (Proposed Standard) RFC jun, Updated by RFC 5689. Fremont, CA, USA: RFC Editor and RFC Editor, 2007. DOI: `10.17487/RFC4918` . URL: `https://www.rfc-editor.org/ rfc/rfc4918.txt` . 

- [EG89] C. A. Ellis and S. J. Gibbs. “Concurrency control in groupware systems”. In: _Proceedings of the 1989 ACM SIGMOD international conference on Management of data_ . Portland, Oregon, USA: ACM, 1989, pp. 399–407. ISBN: 0-89791-317-5. DOI: `10.1145/67544.66963` . 

- [EN15] Ramez Elmasri and Shamkant B. Navathe. _Fundamentals of database systems_ . Pearson, 2015. ISBN: 0133970779. 

- [Ene+18] Vitor Enes, Paulo Sérgio Almeida, Carlos Baquero, and João Leitão. “Efficient Synchronization of State-based CRDTs”. In: _CoRR_ abs/1803.02750 (2018). 

- [EYL13] Frank I. Elijorde, Hyunho Yang, and Jaewan Lee. “Ubiquitous Workspace Synchronization in a Cloud-based Framework”. In: _Journal of Korean Society for Internet Information_ 14 (1 2013), pp. 53–62. ISSN: 1598-0170. DOI: `10.7472/jksii.2013.14.53` . 

- [FLP85] Michael J. Fischer, Nancy A. Lynch, and Michael S. Paterson. “Impossibility of distributed consensus with one faulty process”. In: _J. ACM_ 32 (2 1985), pp. 374–382. ISSN: 0004-5411. DOI: `10.1145/3149.214121` . 

- [Fos+07] J. Nathan Foster, Michael B. Greenwald, Christian Kirkegaard, Benjamin C. Pierce, and Alan Schmitt. “Exploiting schemas in data synchronization”. In: _Journal of Computer and System Sciences_ 73 (4 2007), pp. 669–689. ISSN: 00220000. DOI: `10.1016/j.jcss.2006.10.024` . 

_BIBLIOGRAPHY_ 

183 

- [FPP95] Ludwin Fuchs, Uta Pankoke-Babatz, and Wolfgang Prinz. “Supporting Cooperative Awareness with Local Event Mechanisms. The GroupDesk System”. In: _Proceedings of the Fourth European Conference on Computer-Supported Cooperative Work ECSCW ’95: 10-14 September, 1995, Stockholm, Sweden_ . Ed. by Hans Marmolin, Yngve Sundblad, and Kjeld Schmidt. Dordrecht: Springer Netherlands, 1995, pp. 247–262. ISBN: 978-94-011-0349-7. DOI: `10 . 1007/978- 94- 011- 0349- 7_16` . URL: `https://doi.org/10.1007/978- 94- 0110349-7_16` . 

- [Fra09] Neil Fraser. “Differential synchronization”. In: _Proceedings of the 9th ACM symposium on Document engineering_ . Munich, Germany: ACM, 2009, pp. 13–20. ISBN: 978-1-60558-575-8. DOI: `10.1145/1600193.1600198` . 

- [Gam+77] Erich Gamma, Richard Helm, Ralph E. Johnson, and John Vlissides. _Design patterns. Elements of reusable object-oriented software_ . Forty-fourth printing. Addison-Wesley professional computing series. Gamma, Erich, (author.) Helm, Richard (author.) Johnson, Ralph E (author.) Vlissides, John, (author.) Boston, Mass.: Addison-Wesley, 1977. xv, 395. ISBN: 9780201633610. 

- [GG02] Carl Gutwin and Saul Greenberg. “A Descriptive Framework of Workspace Awareness for Real-Time Groupware”. In: _Comput. Supported Coop. Work_ 11 (3 2002). nov, pp. 411–446. ISSN: 0925-9724. DOI: `10.1023/A:1021271517844` . URL: `https://doi.org/10.1023/A: 1021271517844` . 

- [GHJ07] Alex Groce, Gerard Holzmann, and Rajeev Joshi. “Randomized Differential Testing as a Prelude to Formal Verification”. In: _Proceedings of the 29th international conference on Software Engineering_ . IEEE Computer Society, 2007, pp. 621–631. ISBN: 0-7695-2828-7. DOI: `10. 1109/ICSE.2007.68` . 

- [Goo18] Google Inc. _Backup and Sync Homepage_ . 2018. URL: `https://www.google.com/drive/ download/backup-and-sync/` . 

- [Got+16] Alexey Gotsman, Hongseok Yang, Carla Ferreira, Mahsa Najafzadeh, and Marc Shapiro. “’Cause I’m strong enough. Reasoning about consistency choices in distributed systems”. In: _Proceedings of the 43rd Annual ACM SIGPLAN-SIGACT Symposium on Principles of Programming Languages_ . St. Petersburg, FL, USA: ACM, 2016, pp. 371–384. ISBN: 978-1-45033549-2. DOI: `10.1145/2837614.2837625` . 

- [GPP93] R. G. Guy, G. J. Popek, and T. W. Page. “Consistency algorithms for optimistic replication”. In: _1993 International Conference on Network Protocols_ . 1993 International Conference on Network Protocols (San Francisco, CA, USA). IEEE Comput. Soc. Press, 1993, pp. 250–261. ISBN: 0-8186-3670-X. DOI: `10.1109/ICNP.1993.340912` . 

- [Gri14] Erin Griffith. _Who’s winning the consumer cloud storage wars?_ 2014. URL: `http://fortune. com/2014/11/06/dropbox-google-drive-microsoft-onedrive/` . 

- [Guy+99] Richard Guy, Peter Reiher, David Ratner, Michial Gunter, Wilkie Ma, and Gerald Popek. “Rumor. Mobile Data Access Through Optimistic Peer-to-Peer Replication”. In: _Advances in Database Technologies_ . Ed. by Yahiko Kambayashi, Dik Lun Lee, Ee-Peng Lim, Mukesh Kumar Mohania, and Yoshifumi Masunaga. Berlin, Heidelberg: Springer Berlin Heidelberg, 1999, pp. 254–265. ISBN: 978-3-540-49121-7. 

- [Guy91] Richard Guy. “Ficus: A Very Large Scale Reliable Desitributed File System”. Ph.D. dissertation. UCLA, 1991. 

- [Hac17] Mark Hachmann. _Google Drive is being replaced by Backup and Sync: What to expect_ . 2017. URL: `https://www.pcworld.com/article/3223136/data-center-cloud/googledrive-is-being-replaced-by-backup-and-sync-what-to-expect.html` (visited on 07/21/2019). 

- [Han+16] S. Han, H. Shen, T. Kim, A. Krishnamurthy, T. Anderson, and D. Wetherall. “MetaSync. Coordinating Storage across Multiple File Synchronization Services”. In: _IEEE Internet Computing_ 20 (3 2016), pp. 36–44. ISSN: 1089-7801. DOI: `10.1109/MIC.2016.44` . 

_BIBLIOGRAPHY_ 

184 

- [Her+12] Frank Hermann, Hartmut Ehrig, Claudia Ermel, and Fernando Orejas. “Concurrent Model Synchronization with Conflict Resolution Based on Triple Graph Grammars”. In: _Fundamental Approaches to Software Engineering: 15th International Conference, FASE 2012, Held as Part of the European Joint Conferences on Theory and Practice of Software, ETAPS 2012, Tallinn, Estonia, March 24 - April 1, 2012. Proceedings_ . Ed. by Juan de Lara and Andrea Zisman. Berlin, Heidelberg: Springer Berlin Heidelberg, 2012, pp. 178–193. ISBN: 978-3-64228872-2. DOI: `10.1007/978-3-642-28872-2_13` . URL: `http://dx.doi.org/10.1007/ 978-3-642-28872-2_13` . 

- [HO48] Carl G. Hempel and Paul Oppenheim. “Studies in the Logic of Explanation”. In: _Philosophy of science_ 15 (2 1948), pp. 135–175. 

- [How93] J. H. Howard, ed. _Using reconciliation to share files between occasionally connected computers_ . Proceedings of IEEE 4th Workshop on Workstation Operating Systems. WWOS-III. Proceedings of IEEE 4th Workshop on Workstation Operating Systems. WWOS-III. 1993. 56-60. DOI: `10.1109/WWOS.1993.348172` . 

- [Hug+16] John Hughes, Benjamin C. Pierce, Thomas Arts, and Ulf Norell. “Mysteries of DropBox: Property-Based Testing of a Distributed Synchronization Service”. In: _Proceedings, 2016 IEEE International Conference on Software Testing, Verification and Validation. 10-15 April 2016, Chicago, Illinois_ . 2016 IEEE International Conference on Software Testing, Verification and Validation (ICST) (Chicago, IL, USA). Los Alamitos, California: IEEE Computer Society, Conference Publishing Services, 2016, pp. 135–145. ISBN: 978-1-5090-1827-7. DOI: `10.1109/ICST.2016.37` . 

- [HW90] Maurice P. Herlihy and Jeannette M. Wing. “Linearizability. A correctness condition for concurrent objects”. In: _ACM Trans. Program. Lang. Syst._ 12 (3 1990), pp. 463–492. ISSN: 01640925. DOI: `10.1145/78969.78972` . 

- [JLP13] Nils Jeners, Oleksandr Lobunets, and Wolfgang Prinz. “What groupware functionality do users really use? A study of collaboration within digital ecosystems”. In: _2013 7th IEEE International Conference on Digital Ecosystems and Technologies (DEST)_ . July, 2013, pp. 49–54. DOI: `10.1109/DEST.2013.6611328` . 

- [JOO15] Tero Jokela, Jarno Ojala, and Thomas Olsson. “A Diary Study on Combining Multiple Information Devices in Everyday Activities and Tasks”. In: _Proceedings of the 33rd Annual ACM Conference on Human Factors in Computing Systems_ . Seoul, Republic of Korea: ACM, 2015, pp. 3903–3912. ISBN: 978-1-4503-3145-6. DOI: `10.1145/2702123.2702211` . 

- [JP14] Nils Jeners and Wolfgang Prinz. “Metrics for Cooperative Systems”. In: _Proceedings of the 18th International Conference on Supporting Group Work_ . Sanibel Island, Florida, USA: ACM, 2014, pp. 91–99. ISBN: 978-1-4503-3043-5. DOI: `10.1145/2660398.2660407` . 

- [JPV02] Trevor Jim, Benjamin C. Pierce, and Jérôme Vouillon. _How to build a file synchronizer_ . 2002. URL: `https://www.cis.upenn.edu/~bcpierce/courses/dd/papers/unisonimpl.ps` (visited on 07/21/2019). 

- [JT75] Paul R. Johnson and Robert Thomas. _Maintenance of duplicate databases_ . 1975. URL: `https://tools.ietf.org/html/rfc677` . 

- [KA10] Bettina Kemme and Gustavo Alonso. “Database replication. A tale of research across communities”. In: _Proceedings of the VLDB Endowment_ 3 (1-2 2010), pp. 5–12. ISSN: 21508097. DOI: `10.14778/1920841.1920847` . 

- [KB17] Martin Kleppmann and Alastair R. Beresford. “A Conflict-Free Replicated JSON Datatype”. In: _IEEE Transactions on Parallel and Distributed Systems_ 28 (10 2017), pp. 2733–2746. ISSN: 1045-9219. DOI: `10.1109/TPDS.2017.2697382` . 

- [Ker+01] Anne-Marie Kermarrec, Antony Rowstron, Marc Shapiro, and Peter Druschel. “The IceCube approach to the reconciliation of divergent replicas”. In: _Proceedings of the twentieth annual ACM symposium on Principles of distributed computing_ . Newport, Rhode Island, USA: ACM, 2001, pp. 210–218. ISBN: 1-58113-383-9. DOI: `10.1145/383962.384020` . 

_BIBLIOGRAPHY_ 

185 

- [KK17] Abdullah Talha Kabakus and Resul Kara. “A Performance Evaluation of Dropbox in the light of Personal Cloud Storage Systems”. In: _International Journal of Computer Applications_ 163 (5 2017), pp. 6–11. 

- [KKP07] Sanjeev Khanna, Keshav Kunal, and Benjamin C. Pierce. “A Formal Investigation of Diff3”. In: _FSTTCS 2007: Foundations of Software Technology and Theoretical Computer Science: 27th International Conference, New Delhi, India, December 12-14, 2007. Proceedings_ . Ed. by V. Arvind and Sanjiva Prasad. Berlin, Heidelberg: Springer Berlin Heidelberg, 2007, pp. 485– 496. ISBN: 978-3-540-77050-3. DOI: `10.1007/978- 3- 540- 77050- 3_40` . URL: `https: //doi.org/10.1007/978-3-540-77050-3_40` . 

- [Kol16] Felix Kollmar. _The Cloud Storage Report – Dropbox Owns Cloud Storage on Mobile_ . 2016. URL: `https://blog.cloudrail.com/cloud-storage-report-dropbox-owns-cloudstorage-mobile/` (visited on 07/21/2019). 

- [KS91] P. Kumar and M. Satyanarayanan. _Log-Based Directory Resolution in the Coda File System_ . School of Computer Science, Carnegie Mellon University, Pittsburgh, PA 15213, 1991. 

- [KS92] James J. Kistler and M. Satyanarayanan. “Disconnected operation in the Coda File System”. In: _ACM Transactions on Computer Systems_ 10 (1 1992), pp. 3–25. ISSN: 07342071. DOI: `10. 1145/146941.146942` . 

- [KWK03] Brent Byunghoon Kang, R. Wilensky, and J. Kubiatowicz, eds. _The hash history approach for reconciling mutual inconsistency_ . 23rd International Conference on Distributed Computing Systems, 2003. Proceedings. 23rd International Conference on Distributed Computing Systems, 2003. Proceedings. 2003. 670-677. ISBN: 1063-6927. DOI: `10.1109/ICDCS.2003. 1203518` . 

- [Lam78] Leslie Lamport. “Time, clocks, and the ordering of events in a distributed system”. In: _Commun. ACM_ 21 (7 1978), pp. 558–565. ISSN: 0001-0782. DOI: `10.1145/359545.359563` . 

- [Li+12a] Q. Li, L. Zhu, S. Zeng, and W. Q. Shang, eds. _An Improved File System Synchronous Algorithm_ . 2012 Eighth International Conference on Computational Intelligence and Security. 2012 Eighth International Conference on Computational Intelligence and Security. 2012. 520-524. DOI: `10.1109/CIS.2012.123` . 

- [Li+12b] Qiang Li, Ligu Zhu, Wenqian Shang, and Saifeng Zeng. “CloudSync: Multi-nodes Directory Synchronization”. In: _International Conference on Industrial Control and Electronics Engineering (ICICEE 2012), 2012. Xi’an, China, 23 - 25 Aug. 2012_ . 2012 International Conference on Industrial Control and Electronics Engineering (ICICEE) (Xi’an, China). International Conference on Industrial Control and Electronics Engineering and ICICEE. Piscataway, NJ and Piscataway, NJ: IEEE, 2012, pp. 1470–1473. ISBN: 978-1-4673-1450-3. DOI: `10.1109/ ICICEE.2012.386` . 

- [Lin01] Tancred Lindholm. “A 3-way Merging Algorithm for Synchronizing Ordered Trees. The 3DM merging and differencing tool for XML”. masterthesis. Helsinki, Finland: Helsinki University of Technology, 2001. URL: `https://www.cs.hut.fi/~ctl/3dm/thesis.pdf` . 

- [Lin04] Tancred Lindholm. “A three-way merge for XML documents”. In: _Proceedings of the 2004 ACM symposium on Document engineering_ . Milwaukee, Wisconsin, USA: ACM, 2004, pp. 1– 10. ISBN: 1-58113-938-1. DOI: `10.1145/1030397.1030399` . 

- [LKT05] Tancred Lindholm, Jaakko Kangasharju, and Sasu Tarkoma. “A hybrid approach to optimistic file system directory tree synchronization”. In: _The 4th ACM international workshop on Data engineering for wireless and mobile access_ (Baltimore, MD, USA). Ed. by Vijay Kumar, Arkady Zaslavsky, Ugur Cetintemel, and Alexandros Labrinidis. New York, NY, USA: ACM, 2005, pp. 49–56. ISBN: 1-59593-088-4. DOI: `10.1145/1065870.1065879` . 

- [LM10] Avinash Lakshman and Prashant Malik. “Cassandra. A decentralized structured storage system”. In: _ACM SIGOPS Operating Systems Review_ 44 (2 2010), p. 35. ISSN: 01635980. DOI: `10.1145/1773912.1773922` . 

|186|_BIBLIOGRAPHY_|
|---|---|
|[LS90]|Eliezer Levy and Abraham Silberschatz. “Distributed file systems. Concepts and examples”.<br>In:_ACM Computing Surveys_22 (4 1990), pp. 321–374.ISSN: 03600300.DOI:`10.1145/98163.`<br>`98169`.|
|[LSL04]|R. Li, C. Sun, and D. Li. “A Time Interval Based Consistency Control Algorithm for Inter-<br>active Groupware Applications”. In:_Parallel and Distributed Systems, International Confer-_<br>_ence on(ICPADS)_. Vol. 00. 07. 2004, p. 429. DOI:`10.1109/ICPADS.2004.1316123`. URL:<br>`doi.ieeecomputersociety.org/10.1109/ICPADS.2004.1316123`.|
|[Lv+17]|Xiao Lv, Fazhi He, Weiwei Cai, Yuan Cheng, and Yiqi Wu. “CRDT-based Conflict Detection<br>and Resolution for Massive-scale Real-time Collaborative CAD systems”. In:_Proceedings of_<br>_the 12th Chinese Conference on Computer Supported Cooperative Work and Social Comput-_<br>_ing - ChineseCSCW ’17_. the 12th Chinese Conference (Chongqing, China). Ed. by Zili Zhang,<br>Ning Gu, Shaozi Li, Tun Lu, and Li Li. New York, New York, USA: ACM Press, 2017, pp. 185–<br>188. ISBN: 9781450353526. DOI:`10.1145/3127404.3127436`.|
|[Lv+18]|Xiao Lv, Fazhi He, Yuan Cheng, and Yiqi Wu. “A novel CRDT-based synchronization method<br>for real-time collaborative CAD systems”. In:_Advanced Engineering Informatics_38 (2018),<br>pp. 381–391. ISSN: 14740346. DOI:`10.1016/j.aei.2018.08.008`.|
|[LZD13]|Z. Li, Z. L. Zhang, and Y. Dai. “Coarse-grained cloud synchronization mechanism design<br>may lead to severe traffic overuse”. In:_Tsinghua Science and Technology_18 (3 2013), pp. 286–<br>297. DOI:`10.1109/TST.2013.6522587`.|
|[Mat+89]|Friedemann Mattern et al. “Virtual time and global states of distributed systems”. In:_Par-_<br>_allel and Distributed Algorithms_1 (23 1989), pp. 215–226.|
|[Meh+14]|Ahmed-Nacer Mehdi, Pascal Urso, Valter Balegas, Nuno Pergui&#231, and a. “Merging OT<br>and CRDT algorithms”. In:_Proceedings of the First Workshop on Principles and Practice of_<br>_Eventual Consistency_. Amsterdam, The Netherlands: ACM, 2014, pp. 1–4. ISBN: 978-1-4503-<br>2716-9. DOI:`10.1145/2596631.2596636`.|
|[Men02]|T. Mens. “A state-of-the-art survey on software merging”. In:_IEEE Transactions on Software_<br>_Engineering_28 (5 2002), pp. 449–462. ISSN: 0098-5589. DOI:`10.1109/TSE.2002.1000449`.|
|[Mic06]|Microsoft Inc. _How To Use NTFS Alternate Data Streams_. 2006. URL:`https://support.`<br>`microsoft.com/en-us/help/105763/how-to-use-ntfs-alternate-data-streams`.|
|[Mic18a]|Microsoft Inc._MSDN documentation: File Attribute Constants_. 2018. URL:`https://docs.`<br>`microsoft.com/en-us/windows/win32/fileio/file-attribute-constants`.|
|[Mic18b]|Microsoft Inc. _MSDN documentation: Naming Files, Paths, and Namespaces_. 2018. URL:<br>`https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file`.|
|[Mic19]|Microsoft Inc._OneDrive Homepage_. 2019. URL:`https://onedrive.live.com`.|
|[Mol+03]|Pascal Molli, Gérald Oster, Hala Skaf-Molli, and Abdessamad Imine. “Using the transfor-<br>mational approach to build a safe and generic data synchronizer”. In: _Proceedings of the_<br>_2003 international ACM SIGGROUP conference on Supporting group work_. Sanibel Island,<br>Florida, USA: ACM, 2003, pp. 212–220. ISBN: 1-58113-693-5. DOI: `10 . 1145 / 958160 .`<br>`958194`.|
|[Mor+86]|James H. Morris, Mahadev Satyanarayanan, Michael H. Conner, John H. Howard, David S.<br>Rosenthal, and F. Donelson Smith. “Andrew. A Distributed Personal Computing Environ-<br>ment”. In:_Commun. ACM_ 29 (3 1986). mar, pp. 184–201. ISSN: 0001-0782. DOI:`10.1145/`<br>`5666.5671`. URL:`http://doi.acm.org/10.1145/5666.5671`.|
|[MT07]|Dahlia Malkhi and Doug Terry. “Concise version vectors in WinFS”. In: _Distributed Com-_<br>_puting_20 (3 2007). Oct, pp. 209–219.ISSN: 1432-0452.DOI:`10.1007/s00446-007-0044-y`.<br>URL:`https://doi.org/10.1007/s00446-007-0044-y`.|
|[MT94]|M. S. Mazer and J. J. Tardo, eds. _A Client-Side-Only Approach to Disconnected File Access_.<br>1994 First Workshop on Mobile Computing Systems and Applications. 1994 First Workshop<br>on Mobile Computing Systems and Applications. 1994. 104-110. DOI: `10.1109/WMCSA.`<br>`1994.1`.|



_BIBLIOGRAPHY_ 

187 

- [Mye86] Eugene W. Myers. “An O(ND) difference algorithm and its variations”. In: _Algorithmica_ 1 (1-4 1986), pp. 251–266. ISSN: 0178-4617. DOI: `10.1007/BF01840446` . 

- [Naj16] Mahsa Najafzadeh. “The Analysis and Co-design of Weakly-Consistent Applications”. en. Université Pierre et Marie Curie, 2016. URL: `https://hal.inria.fr/tel-01351187/ document` (visited on 07/21/2019). 

- [Nex18] Nextcloud GmbH. _Homepage_ . 2018. URL: `https://nextcloud.com/` . [Nic+16] Petru Nicolaescu, Kevin Jahns, Michael Derntl, and Ralf Klamma. _Near Real-Time Peer-toPeer Shared Editing on Extensible Data Types_ . 2016. DOI: `10.1145/2957276.2957310` . URL: `http://dl.acm.org/ft_gateway.cfm?id=2957310&type=pdf` . 

- [Nid12] Srinivas Nidhra. “Black Box and White Box Testing Techniques - A Literature Review”. In: _International Journal of Embedded Systems and Applications_ 2 (2 2012), pp. 29–50. ISSN: 18395171. DOI: `10.5121/ijesa.2012.2204` . 

- [NS16] Agustina Ng and Chengzheng Sun. “Operational Transformation for Real-time Synchronization of Shared Workspace in Cloud Storage”. In: _Proceedings of the 19th International Conference on Supporting Group Work_ . Sanibel Island, Florida, USA: ACM, 2016, pp. 61–70. ISBN: 978-1-4503-4276-6. DOI: `10.1145/2957276.2957278` . 

- [NSE18] Mahsa Najafzadeh, Marc Shapiro, and Patrick Eugster. “Co-Design and Verification of an Available File System”. In: _Verification, Model Checking, and Abstract Interpretation_ . Ed. by Isil Dillig and Jens Palsberg. Cham: Springer International Publishing, 2018, pp. 358–381. ISBN: 978-3-319-73721-8. 

- [Ntz16] Gian Ntzik. “Reasoning about POSIX File Systems”. PhD thesis, Imperial College London, 2016. URL: `https://www.doc.ic.ac.uk/~pg/publications/Ntzik2017Reasoning. pdf` . 

- [Orb18] OrbiTeam Software GmbH & Co KG. _BSCW Social_ . 2018. URL: `https://www.bscw.de/ social/` . 

- [Ost+06a] Gérald Oster, Hala Skaf-Molli, Pascal Molli, and Hala Naja-Jazzar. _Supporting Collaborative Writing of XML Documents_ . English. Computer Science [cs]/Other [cs.OH]Reports. 2006. URL: `https://hal.inria.fr/inria-00108996` . 

- [Ost+06b] Gérald Oster, Pascal Urso, Pascal Molli, and Abdessamad Imine. “Data consistency for P2P collaborative editing”. In: _Proceedings of the 2006 20th anniversary conference on Computer supported cooperative work - CSCW ’06_ . the 2006 20th anniversary conference (Banff, Alberta, Canada). Ed. by Pamela Hinds and David Martin. New York, New York, USA: ACM Press, 2006, p. 259. ISBN: 1595932496. DOI: `10.1145/1180875.1180916` . 

- [own19] ownCloud GmbH. _ownCloud Homepage_ . 2019. URL: `https://owncloud.org` . [Par+83] D. S. Parker, G. J. Popek, G. Rudisin, A. Stoughton, B. J. Walker, E. Walton, J. M. Chow, D. Edwards, S. Kiser, and C. Kline. “Detection of Mutual Inconsistency in Distributed Systems”. In: _IEEE Transactions on Software Engineering_ SE-9 (3 1983), pp. 240–247. ISSN: 0098-5589. DOI: `10.1109/TSE.1983.236733` . 

- [PB03] Rachel A. Pottinger and Philip A. Bernstein. “Merging models based on given correspondences”. In: _Proceedings of the 29th international conference on Very large data bases - Volume 29_ . Berlin, Germany: VLDB Endowment, 2003, pp. 862–873. ISBN: 0-12-722442-4. 

- [Pie18] Benjamin Pierce. _Unison File Synchronizer - Homepage_ . 2018. URL: `https://www.cis. upenn.edu/~bcpierce/unison/` . 

- [Pop02] Karl Popper. _The logic of scientific discovery_ . London: Routledge Classics, 2002. 513 pp. ISBN: 0-415-27844-9. 

_BIBLIOGRAPHY_ 

188 

- [Pre+09] Nuno Preguica, Joan Manuel Marques, Marc Shapiro, and Mihai Letia. “A Commutative Replicated Data Type for Cooperative Editing”. In: _2009 29th IEEE International Conference on Distributed Computing Systems_ . 2009 29th IEEE International Conference on Distributed Computing Systems (ICDCS) (Montreal, Quebec, Canada). IEEE, 2009, pp. 395–403. DOI: `10.1109/ICDCS.2009.20` . 

- [Pre18] Nuno M. Preguiça. “Conflict-free Replicated Data Types. An Overview”. In: _CoRR_ abs/1806.10254 (2018). 

- [Pri17] Rob Price. _Google Drive now hosts more than 2 trillion files_ . 2017. URL: `http : / / www . businessinsider . de / 2 - trillion - files - google - drive - exec - prabhakar - raghavan-2017-5` (visited on 07/21/2019). 

- [Pri93] Wolfgang Prinz. “TOSCA Providing Organisational Information to CSCW Applications”. In: _Proceedings of the Third Conference on European Conference on Computer-Supported Cooperative Work_ . ECSCW’93. Norwell, MA, USA: Kluwer Academic Publishers, 1993, pp. 139– 154. ISBN: 0-7923-2447-1. URL: `http://dl.acm.org/citation.cfm?id=1241934. 1241944` . 

- [PSG04] Benjamin C. Pierce, Alan Schmitt, and Michael B. Greenwald. _Bringing Harmony to Optimism - An Experiment in Synchronizing Heterogeneous Tree-Structured Data_ . 2004. 

- [PV04] Benjamin C. Pierce and Jérôme Vouillon. _What’s in Unison? A Formal Specification and Reference Implementation of a File Synchronizer_ . 2004. 

- [Qia04] Yuechen Qian. “Data synchronization and browsing for home environments”. Eindhoven University of Technology, 2004. 

- [RC01] Norman Ramsey and Elod Csirmaz. “An algebraic approach to file synchronization”. In: _the 8th European software engineering conference held jointly with 9th ACM SIGSOFT international symposium_ (Vienna, Austria). Ed. by A. Min Tjoa and Volker Gruhn. 2001, p. 175. DOI: `10.1145/503209.503233` . 

- [Rei+94] Peter Reiher, John Heidemann, David Ratner, Greg Skinner, and Gerald Popek. “Resolving file conflicts in the Ficus file system”. In: _Proceedings of the USENIX Summer 1994 Technical Conference on USENIX Summer 1994 Technical Conference - Volume 1_ . Boston, Massachusetts: USENIX Association, 1994, p. 12. 

- [Rid+15] Tom Ridge, David Sheets, Thomas Tuerk, Andrea Giugliano, Anil Madhavapeddy, and Peter Sewell. “SibylFS. Formal specification and oracle-based testing for POSIX and real-world file systems”. In: _Proceedings of the 25th Symposium on Operating Systems Principles_ . Monterey, California: ACM, 2015, pp. 38–53. ISBN: 978-1-4503-3834-9. DOI: `10.1145/2815400. 2815411` . 

- [Rij18] André dos Reis Martins Rijo. “Building Tunable CRDTs”. 2018. URL: `https://run.unl. pt/handle/10362/55171` . 

- [Roh+11] Hyun-Gul Roh, Myeongjae Jeon, Jin-Soo Kim, and Joonwon Lee. “Replicated abstract data types. Building blocks for collaborative applications”. In: _Journal of Parallel and Distributed Computing_ 71 (3 2011), pp. 354–368. ISSN: 07437315. DOI: `10.1016/j.jpdc.2010.12.006` . 

- [RSK04] Pamela Ravasio, Sissel Guttormsen Schär, and Helmut Krueger. “In pursuit of desktop evolution”. In: _ACM Transactions on Computer-Human Interaction_ 11 (2 2004), pp. 156–180. ISSN: 10730516. DOI: `10.1145/1005361.1005363` . 

- [Sat+90] M. Satyanarayanan, J. J. Kistler, P. Kumar, M. E. Okasaki, E. H. Siegel, and D. C. Steere. “Coda. A highly available file system for a distributed workstation environment”. In: _IEEE Transactions on Computers_ 39 (4 1990), pp. 447–459. ISSN: 00189340. DOI: `10.1109/12.54838` . 

- [SCF97] Maher Suleiman, Michèle Cart, and Jean Ferrié. “Serialization of Concurrent Operations in a Distributed Collaborative Environment”. In: _Proceedings of the International ACM SIGGROUP Conference on Supporting Group Work: The Integration Challenge_ . GROUP ’97. New York, NY, USA: ACM, 1997, pp. 435–445. ISBN: 0-89791-897-5. DOI: `10 . 1145 / 266838 . 267369` . URL: `http://doi.acm.org/10.1145/266838.267369` . 

_BIBLIOGRAPHY_ 

189 

- [SE98] Chengzheng Sun and Clarence Ellis. “Operational transformation in real-time group editors. Issues, algorithms, and achievements”. In: _Proceedings of the 1998 ACM conference on Computer supported cooperative work_ . Seattle, Washington, USA: ACM, 1998, pp. 59–68. ISBN: 1-58113-009-0. DOI: `10.1145/289444.289469` . 

- [Sha+11a] Marc Shapiro, Nuno Preguiça, Carlos Baquero, and Marek Zawirski. _A comprehensive study of Convergent and Commutative Replicated Data Types_ . English. This research was supported in part by ANR project ConcoRDanT (ANR-10-BLAN 0208), and a Google Research Award 2009. Marek Zawirski is a recipient of the Google Europe Fellowship in Distributed Computing, and this research is supported in part by this Google Fellowship. Carlos Baquero is partially supported by FCT project Castor (PTDC/EIA-EIA/104022/2008).) Computer Science [cs]/Other [cs.OH]Reports INRIA UNL U Minho LIP6. Inria – Centre ParisRocquencourt and INRIA, 2011. URL: `https://hal.inria.fr/inria-00555588` . 

- [Sha+11b] Marc Shapiro, Nuno Preguiça, Carlos Baquero, and Marek Zawirski. “Conflict-Free Replicated Data Types”. In: _Stabilization, Safety, and Security of Distributed Systems: 13th International Symposium, SSS 2011, Grenoble, France, October 10-12, 2011. Proceedings_ . Ed. by Xavier Défago, Franck Petit, and Vincent Villain. Berlin, Heidelberg: Springer Berlin Heidelberg, 2011, pp. 386–400. ISBN: 978-3-642-24550-3. DOI: `10.1007/978-3-642-24550-3_29` . URL: `https://doi.org/10.1007/978-3-642-24550-3_29` . 

- [She19] Marius Shekow. “Syncpal: A simple and iterative reconciliation algorithm for file synchronizers”. In: _Distributed Applications and Interoperable Systems - IFIP International Federation for Information Processing, DAIS 2019, Held as Part of the 14th International Federated Conference on Distributed Computing Techniques, DisCoTec 2019, Lingby, Denmark, June 18-21, 2019, Proceedings_ . Ed. by José Pereira and Laura Ricci. 2019. 

- [SLS14] Andreas Spillner, Tilo Linz, and Hans Schaefer. _Software testing foundations. A study guide for the certified tester exam_ . Rocky Nook, Inc, 2014. 

- [SM02] Torsten Suel and Nasir Memon. “Algorithms for Delta Compression and Remote File Synchronization”. In: _Lossless Compression Handbook_ . Ed. by Khalid Sayood. Academic Press, 2002. 

- [SM94] Reinhard Schwarz and Friedemann Mattern. “Detecting causal relationships in distributed computations. In search of the holy grail”. In: _Distributed Computing_ 7 (3 1994). Mar, pp. 149–174. ISSN: 1432-0452. DOI: `10.1007/BF02277859` . URL: `https://doi.org/10. 1007/BF02277859` . 

- [SP19] Marius Shekow and Wolfgang Prinz. “A capability analysis of groupware, cloud and desktop file systems for file synchronization”. In: _Proceedings of 17th European Conference on Computer-Supported Cooperative Work-Exploratory Papers. The International Venue on Practice-centred Computing an the Design of Cooperation Technologies_ . European Society for Socially Embedded Technologies (EUSSET). 2019. DOI: `10.18420/ecscw2019_ep06` . URL: `http://dx.doi.org/10.18420/ecscw2019_ep06` . 

- [SRK00] Marc Shapiro, Antony Rowstron, and Anne-Marie Kermarrec. “Application-independent reconciliation for nomadic applications”. In: _Proceedings of the 9th workshop on ACM SIGOPS European workshop: beyond the PC: new challenges for the operating system_ . Kolding, Denmark: ACM, 2000, pp. 1–6. DOI: `10.1145/566726.566728` . 

- [SS05] Yasushi Saito and Marc Shapiro. “Optimistic replication”. In: _ACM Computing Surveys_ 37 (1 2005), pp. 42–81. ISSN: 03600300. DOI: `10.1145/1057977.1057980` . 

- [SS09] David Sun and Chengzheng Sun. “Context-Based Operational Transformation in Distributed Collaborative Editing Systems”. In: _IEEE Transactions on Parallel and Distributed Systems_ 20 (10 2009), pp. 1454–1470. ISSN: 1045-9219. DOI: `10.1109/TPDS.2008.240` . 

- [Sto15] Storage Networking Industry Association SNMP. _CDMI: Cloud Data Management Interface, v1.1_ . 2015. URL: `https://www.snia.org/cdmi` . 

_BIBLIOGRAPHY_ 

190 

- [Sun+04] David Sun, Steven Xia, Chengzheng Sun, and David Chen. “Operational Transformation for Collaborative Word Processing”. In: _Proceedings of the 2004 ACM Conference on Computer Supported Cooperative Work_ . CSCW ’04. New York, NY, USA: ACM, 2004, pp. 437–446. ISBN: 1-58113-810-5. DOI: `10.1145/1031607.1031681` . URL: `http://doi.acm.org/10.1145/ 1031607.1031681` . 

- [Sun+18] Chengzheng Sun, David Sun, Agustina, and Weiwei Cai. “Real Differences between OT and CRDT for Co-Editors”. In: _CoRR_ abs/1810.02137 (2018). 

- [Sun+98] Chengzheng Sun, Xiaohua Jia, Yanchun Zhang, Yun Yang, and David Chen. “Achieving Convergence, Causality Preservation, and Intention Preservation in Real-time Cooperative Editing Systems”. In: _ACM Transactions on Computer-Human Interaction_ 5 (1 1998). mar, pp. 63– 108. ISSN: 10730516. DOI: `10.1145/274444.274447` . URL: `http://doi.acm.org/10. 1145/274444.274447` . 

- [Sur17] Surur. _Microsoft celebrates 10 years of OneDrive, promises many more_ . 2017. URL: `https:// mspoweruser.com/microsoft-celebrates-10-years-onedrive-promises-many/` . 

- [SW13] Stephanie Santosa and Daniel Wigdor. “A field study of multi-device workflows in distributed workspaces”. In: _Proceedings of the 2013 ACM international joint conference on Pervasive and ubiquitous computing_ . Zurich, Switzerland: ACM, 2013, pp. 63–72. ISBN: 9781-4503-1770-2. DOI: `10.1145/2493432.2493476` . 

- [Tan+15] Haowen Tang, Fangming Liu, Guobin Shen, Yuchen Jin, and Chuanxiong Guo. “UniDrive. Synergize Multiple Consumer Cloud Storage Services”. In: _Proceedings of the 16th Annual Middleware Conference_ . Vancouver, BC, Canada: ACM, 2015, pp. 137–148. ISBN: 978-1-45033618-5. DOI: `10.1145/2814576.2814729` . 

- [TBM13] John Tang, Jed Brubaker, and Catherine Marshall. “What do you see in the cloud? Understanding the cloud-based user experience through practices”. In: _14th International Conference on Human-Computer Interaction (INTERACT)_ . Springer. 2013, pp. 678–695. 

- [Ter+13] Doug Terry, Vijayan Prabhakaran, Ramakrishna Kotla, Mahesh Balakrishnan, Marcos K. Aguilera, and Hussam Abu-Libdeh. “Consistency-based service level agreements for cloud storage”. In: _Proceedings ACM Symposium on Operating Systems Principles_ . November. ACM, 2013. URL: `https://www.microsoft.com/en- us/research/publication/ consistency-based-service-level-agreements-for-cloud-storage/` . 

- [Ter+94] D. B. Terry, A. J. Demers, K. Petersen, M. J. Spreitzer, M. M. Theimer, and B. B. Welch. “Session guarantees for weakly consistent replicated data”. In: _Proceedings of 3rd International Conference on Parallel and Distributed Information Systems_ . Sep, 1994, pp. 140–149. DOI: `10.1109/PDIS.1994.331722` . 

- [Ter+95] D. B. Terry, M. M. Theimer, Karin Petersen, A. J. Demers, M. J. Spreitzer, and C. H. Hauser. “Managing update conflicts in Bayou, a weakly connected replicated storage system”. In: _Proceedings of the fifteenth ACM symposium on Operating systems principles - SOSP ’95_ . the fifteenth ACM symposium (Copper Mountain, Colorado, United States). Ed. by Michael B. Jones. New York, New York, USA: ACM Press, 1995, pp. 172–182. ISBN: 0897917154. DOI: `10. 1145/224056.224070` . 

- [TH10] Linus Torvalds and Junio Hamano. _Git: Distributed Version Control_ . 2010. URL: `https:// git-scm.com` . 

- [TIH19] Tasuku Takahashi, Kengo Imae, and Naohiro Hayashibara. “Conflict-free Multi-user Collaborative Editing System for 3D Models”. In: _Complex, Intelligent, and Software Intensive Systems_ . Ed. by Leonard Barolli, Nadeem Javaid, Makoto Ikeda, and Makoto Takizawa. Cham: Springer International Publishing, 2019, pp. 269–279. ISBN: 978-3-319-93659-8. 

- [TM+96] Andrew Tridgell, Paul Mackerras, et al. “The rsync algorithm”. In: (1996). [TRN15] Vinh Tao, Vianney Rancurel, and João Neto. “A Name Is Not A Name”. In: _the 6th Asia-Pacific Workshop_ (Tokyo, Japan). Ed. by Kenji Kono and Takahiro Shinagawa. 2015, pp. 1–8. DOI: `10.1145/2797022.2797034` . 

_BIBLIOGRAPHY_ 

191 

- [TSR15] Vinh Tao, Marc Shapiro, and Vianney Rancurel. “Merging Semantics for Conflict Updates in Geo-distributed File Systems”. In: _Proceedings of the 8th ACM International Systems and Storage Conference_ . SYSTOR ’15. New York, NY, USA: ACM, 2015, 10:1–10:12. ISBN: 978-14503-3607-9. DOI: `10.1145/2757667.2757683` . URL: `http://doi.acm.org/10.1145/ 2757667.2757683` . 

- [UFB10] Sandesh Uppoor, Michail D. Flouris, and Angelos Bilas. “Cloud-based synchronization of distributed file system hierarchies”. In: _2010 IEEE International Conference On Cluster Computing Workshops and Posters (CLUSTER WORKSHOPS)_ . 2010, pp. 1–4. ISBN: -2005. DOI: `10.1109/CLUSTERWKSP.2010.5613087` . URL: `http://ieeexplore.ieee.org/stamp/ stamp.jsp?arnumber=5613087` . 

- [Vid+00] Nicolas Vidot, Michelle Cart, Jean Ferrié, and Maher Suleiman. “Copies convergence in a distributed real-time collaborative environment”. In: _Proceedings of the 2000 ACM conference on Computer supported cooperative work - CSCW ’00_ . the 2000 ACM conference (Philadelphia, Pennsylvania, United States). Ed. by Wendy Kellogg and Steve Whittaker. New York, New York, USA: ACM Press, 2000, pp. 171–180. ISBN: 1581132220. DOI: `10 . 1145 / 358916.358988` . 

- [vLP16] Albert van der Linde, João Leitão, and Nuno Preguiça. “Delta-CRDTs. Making delta-CRDTs delta-based”. In: _Proceedings of the 2nd Workshop on the Principles and Practice of Consistency for Distributed Data - PaPoC ’16_ . the 2nd Workshop (London, United Kingdom). Ed. by Peter Alvaro and Alysson Bessani. New York, New York, USA: ACM Press, 2016, pp. 1–4. ISBN: 9781450342964. DOI: `10.1145/2911151.2911163` . 

- [Voi+06] Stephen Voida, W. Keith Edwards, Mark W. Newman, Rebecca E. Grinter, and Nicolas Ducheneaut. “Share and Share Alike. Exploring the User Interface Affordances of File Sharing”. In: _Proceedings of the SIGCHI Conference on Human Factors in Computing Systems_ . CHI ’06. New York, NY, USA: ACM, 2006, pp. 221–230. ISBN: 1-59593-372-7. DOI: `10.1145/1124772. 1124806` . URL: `http://doi.acm.org/10.1145/1124772.1124806` . 

- [VV16] Paolo Viotti and Marko Vukoli´c. “Consistency in Non-Transactional Distributed Storage Systems”. In: _ACM Computing Surveys_ 49 (1 2016), pp. 1–34. ISSN: 03600300. DOI: `10.1145/ 2926965` . 

- [Wal+83] Bruce Walker, Gerald Popek, Robert English, Charles Kline, and Greg Thiel. “The LOCUS distributed operating system”. In: _Proceedings of the ninth ACM symposium on Operating systems principles - SOSP ’83_ . the ninth ACM symposium (Bretton Woods, New Hampshire, United States). Ed. by Jerome Saltzer, Roy Levin, and David Redell. New York, New York, USA: ACM Press, 1983, pp. 49–70. ISBN: 0897911156. DOI: `10.1145/800217.806615` . 

- [Wan+16] Haiyang Wang, Xiaoqiang Ma, Feng Wang, Jiangchuan Liu, Bharath Kumar Bommana, and Xin Liu. “Diving into cloud-based file synchronization with user collaboration”. In: _2016 IEEE/ACM 24th International Symposium on Quality of Service (IWQoS)_ (Beijing, China). New York, NY, USA: IEEE, 2016, pp. 1–9. DOI: `10.1109/IWQoS.2016.7590396` . 

- [Wik17] Wikipedia. _Comparison of file systems_ . 2017. URL: `https://en.wikipedia.org/wiki/ Comparison_of_file_systems` . 

- [WRB01] An-I A. Wang, Peter Reiher, and Rajive Bagrodia. _Understanding the Behavior of the ConflictRate Metric in Optimistic Peer Replication_ . Submitted for publication. 2001. 

- [Yan+16] Chao-Tung Yang, Wen-Chung Shih, Chih-Lin Huang, Fuu-Cheng Jiang, and William ChengChung Chu. “On construction of a distributed data storage system in cloud”. In: _Computing_ 98 (1 2016), pp. 93–118. ISSN: 1436-5057. DOI: `10.1007/s00607-014-0399-4` . URL: `https: //doi.org/10.1007/s00607-014-0399-4` . 

- [Zha+14] Yupu Zhang, Chris Dragga, Andrea C. Arpaci-Dusseau, and Remzi H. Arpaci-Dusseau. “ViewBox. Integrating local file systems with cloud storage services”. In: _FAST’14 Proceedings of the 12th USENIX conference on File and Storage Technologies_ . 2014, pp. 119–132. 

193 

## **Appendix A** 

# **Appendix** 

### **A.1 File system meta-data handling** 

#### **A.1.1 System-generated meta-data** 

Meta-data is data that provides further information about objects. Conceptually, each object has a keyvalue store attached to it, representing its meta-data. Meta-data is not stored as part of the object, but at a separate location. Each file system examined in section 3.1 provides a different set of meta-data, where some values are managed (i.e. changed) only by the system, and some can be altered by the user. The following table gives an overview which system-generated meta-data can be retrieved from each file system. 

_APPENDIX A. APPENDIX_ 

194 

||Windows|macOS|WebDAV<sup>1</sup>|BSCW|Dropbox|
|---|---|---|---|---|---|
|Device id|✓|✓|X|X|X|
|Object id|✓<br>(file ID)|✓<br>(inode)|X|✓<br>(oid)|✓<br>(id)|
|Type|✓|✓|✓<br>(Resource<br>type)|✓<br>(Resource<br>type and<br>_class_,<br>indicates e.g.<br>calendar or<br>other types)|✓|
|Create /<br>last-modified<br>timestamps|✓|✓|✓|✓|✓<sup>2</sup>|
|File size|✓|✓|✓|✓|✓|
|Read-only<br>attribute|✓|✓|X|X|X|
|Hidden<br>attribute|✓|✓|X|X|X|
|Other<br>attributes or<br>meta-data|archived,<br>compressed,<br>encrypted,<br>omit content<br>search<br>indexing,<br>system file,<br>temporary<br>file, others<br>[Mic18a]|archived,<br>nodump,<br>opaque,<br>append<br>[App06]|Content-<br>type<sup>3</sup>,<br>ETag<sup>4</sup>|MD5<br>checksum<br>(for files),<br>various<br>others, such<br>as owner’s<br>username, or<br>event history|SHA-256<br>checksum,<br>revision<br>(unique<br>string for a<br>specific file,<br>used to<br>detect<br>changes)|



Row hints: 

- **Device id** : unique identifier of the volume on which an object resides 

- **Object id** : unique identifier of an object itself. When the identifier has a special name in the context of the file system, it is mentioned in parentheses. 

- **Type** : indicates whether the object is a file, directory, or of another type (e.g. symbolic link) 

- **Attributes** : a bitmask of flags. Some of the flags can be changed by the user and user-level applications, others are managed by the file system 

   - Read-only / immutable: when set, applications cannot alter the content of a file 

   - Hidden / invisible: indicates to file managers to hide the file from the user 

#### **A.1.2 Writing arbitrary meta-data** 

Aside from reading system-generated meta-data, most storage systems support one or more ways to write arbitrary, user-defined meta-data. The following table summarizes this capability: 

> 1See section 15 of RFC 4918 for more details. Meta-data enforced by the server is referred to as _live properties_ in the RFC. 

> 2Separate last modified server + client timestamp. 

> 3HTTPs Content-Type header which contains the MIME type, e.g. _application/json_ 

> 4Entity tag, contains arbitrary content used to determine whether the content of a file changed. 

_A.1. FILE SYSTEM META-DATA HANDLING_ 

195 

|Windows:✓|Extended Attributes (EA)<sup>5</sup>: Attribute data is stored in the master file table (MFT),<br>values are limited to 64 KB in length,per entry.|
|---|---|
||Alternate Data Streams (ADS) [Mic06]: ADS allows to attach additional text files<br>(name and content) of arbitrarylength to an existingfile (of arbitrarytype).|
|macOS:✓|xattr [App10]: xattr allows to set key-valuepairs. Values can be text or binarydata.<br>Resource forks<sup>6</sup>: Similar to ADS on Windows, macOS allows to store alternative<br>data streams for a file. The main content is the_data fork_, whereas additional<br>(named) streams are_resourceforks_.|
|WebDAV:✓|Using the PROPPATCH command (see section 9.2 of [Dus07]), most servers<br>support setting arbitrary meta-data (referred to as_dead properties_) for any<br>resource in XML format.|
|BSCW:✓|Via WebDAV’s PROPPATCH.|
|Dropbox:X|Writing arbitrary meta-data is not supported. Note: the documentation mentions<br>an alpha-stage_properties_API, which allows to define a property template (which<br>likely is the property’s schema) and then use it to set, update, or remove instances<br>of the template. However, there is no API function to add new templates.|



#### **A.1.3 Authorization** 

Authorization limits actions an authenticated<sup>7</sup> user is allowed to perform on file system objects. We assume that a user is always authenticated by some means provided by the file system API. 

- 5See `https://github.com/jschicht/EaTools` for a summary of the mechanism, or e.g. `https://msdn.microsoft.` 

- `com/en-us/library/windows/hardware/ff625895` for a concrete API function. Retrieved July 21, 2019. 

   - 6See `http://xahlee.info/UnixResource_dir/macosx.html` , retrieved July 21, 2019. 

- 7Authentication refers to unique _identifying_ a user, whereas authorization defines what a specific, identified user is _allowed_ 

- _to do_ . 

_APPENDIX A. APPENDIX_ 

196 

|Windows|Access control list (ACL) mechanism that allows to selectively grant or deny specific users or<br>groups (identified by the_security identifier_, or_SID_) a set of permissions<sup>8</sup>. When setting new<br>permissions for a directory, one can define whether these are inherited to sub-elements.|
|---|---|
|macOS|UNIX permissions: a mechanism available to UNIX systems and its descendant such as<br>macOS. The OS has a set of users and groups. Users can be in one or more groups. Each<br>object in the file system is assigned a specific owner (user) and group. Each object has three<br>permission masks assigned to it, one that applies to the assigned owner, one to the assigned<br>group and one to everyone else. The permissions that can be set in a permission mask are<br>_Read_,_Write_and_Execute_.<br>• For files, the meaning of each permission is self-explanatory._Execute_means that the<br>file is an executable file and the caller is allowed to execute its binary code or script<br>content. If the_Write_permission is not set, this means the content of the file cannot be<br>changed, but the file can still be moved or deleted!<br>• For directories_Read_means that the user is able to list the contents of the dir,_Write_<br>means that the user may move the dir or create, move/rename or delete immediate<br>children inside of it._Execute_allows to change to the directory and list its contents.|
||ACL: macOS has an ACL mechanism that is similar to the one of Windows. See the note below<br>regardingACLs vs. UNIXpermissions.|
|WebDAV|WebDAV doesn’t specify how authentication and authorization has to be implemented.<br>WebDAV is typically offered as a machine-to-machine interface by a backend system that<br>then actually implements user and permission management, such as BSCW or owncloud.<br>Since WebDAV is implemented over HTTP, clients typically authenticate themselves using the<br>_WWW-Authenticate_header to transmit their credentials (see RFC 2617) or by sending session<br>cookies. Authorization mechanisms are then left to the implementation. However, to allow<br>onlyspecific users to modifyresources, section 6.2 of [Dus07] advises to use_shared locks_.|
|BSCW|BSCW offers an elaborate user and permission management system via the_workspaces_and<br>_roles_concepts. A directory becomes a_workspace_if there is at least one user assigned to it via<br>a_role_. A_role_is a set of permissions. There are a few pre-installed roles, such as “Member”<br>(has most permissions, like read and write) or “Restricted member” (has only read<br>permissions) to choose from, and server administrators can modify these roles or create new<br>ones. Roles and their permissions are automatically inherited to sub-objects, unless a<br>sub-object has another role assigned to it for the authenticated user.|
|Dropbox|While the user always has full permissions for objects in her own directories, Dropbox offers a<br>“can view”permission when_sharing_a folder with another user.|



ACLs are generally more powerful than UNIX permissions because permissions can be granted or denied for _multiple_ specific users or groups in different ways, whereas permissions can only be set for the owning user and _one_ specific group. While ACLs under Windows and macOS are similar, their implementations do differ in detail. The available permissions are slightly different, and on macOS ACL entries cannot automatically be inherited for new objects created within a dir which already has an ACL entry. 

> 8The available permissionsre are can be found at `https://technet.microsoft.com/de-de/library/ cc753525(v=ws.10).aspx` , retrieved July 21, 2019. 

_A.2. ALTERNATIVE FILE SYSTEM DEFINITIONS_ 

197 

### **A.2 Alternative file system definitions** 

#### **A.2.1 File system definition for H-All** 

This section formally defines the file system _H-All_ from table 2.1 on page 16. The main differences to the _NH-MD_ file system we formally defined in section 3.2 are: 

- _Directories_ are objects that store a list of ( _ID_ , _name_ ) tuples, 

- _Files_ can have one or more incoming edges in the arborescence, 

- The _name_ is no longer an attribute of the node but of the edge, 

- There are two new operations, _link_ and _unlink_ , that add or remove links (edges) between an existing directory and file. _unlink_ replaces _delete f ile_ or _deletedir_ . 

We refer to table A.1 for redefined basic functions. 

|Function|Description|
|---|---|
|_list_(_i_)|Returns the set of (_j_,_ω_) tuples of the_immediate_child nodes for the directory node with ID_i_.<br>_j ∈I_ are the IDs of the child nodes,_ω ∈_Σ<sup>_+_ </sup>are the correspondingnames, withΣ<sup>_+ _</sup>_=_Σ<sup>_∗_</sup>\{_ϵ_}.|
|_id_(_iparent_,_ω_)|Helper function used in_id_(_path_). Returns_i_ if (_i_,_ω_)_∈list_(_i parent_),_error_otherwise.|
|_name_(_i_)|No longer defined.|
|_path_(_i_)|No longer defined.|
|_paths_(_i_)|Performs a tree search and returns the set ofpaths<br>�<br>_pathk_<br>�_n_<br>_k=_1 <sup>s.t.</sup><sup>_∀k_ :</sup><sup>_id_(</sup><sup>_pathk_)</sup><sup>_= i_.</sup>|
|_basename_(_path_)|Returns the last list element of_split(path)_, e.g._basename(’/home/user/foo’) = ’foo’_. Formally,<br>if we can split_path = π_/_ω_s.t._π = parent_(_path_) and_ω = basename_(_path_) then the<br>following holds:<br>_path = π_/_ω ⇐⇒∃i_,_u ∈I_,_ω ∈_Σ<sup>_+_ </sup>: (_i_,_ω_)_∈list_(_u_)_∧_{_π_}_= paths_(_u_)|



Table A.1: Functions for working with file system IDs (H-All) 

All invariants from section 3.2.1 have to be adapted, except for eq. 3.6 which remains the same. The adapted invariants are as follows: 

|_∀i_,_j ∈I_,_ω ∈_Σ<sup>_+_ </sup>: (_i_,_ω_)_∈list_(_j_) _=⇒t ype_(_j_)_= dir_|(A.1)|
|---|---|
|_∀i ∈I_,_ω ∈_Σ<sup>_+_ </sup>: (_i_,_ω_)_∉list_(_i_)|(A.2)|
|_∀i_,_j_,_k ∈I_,_ω_,_ν ∈_Σ<sup>_+_ </sup>: _j̸ = k ∧t ype_(_i_)_= dir ∧_(_i_,_ω_)_∈list_(_j_) _=⇒_(_i_,_ν_)_∉list_(_k_)|(A.3)|
|_∀i ∈I_,_ω ∈_Σ<sup>_+_ </sup>: (_iroot_,_ω_)_∉list_(_i_)|(A.4)|
|_∀i ∈I_\{_iroot_} : _t ype_(_i_)_̸ = error ⇐⇒ancestor_(_iroot_,_i_)|(A.5)|
|_∀i_,_j_,_k ∈I_,_ω_,_ν ∈_Σ<sup>_+_ </sup>: _j̸ = k ∧_(_j_,_ω_)_∈list_(_i_)_∧_(_k_,_ν_)_∈list_(_i_) _=⇒ω̸ = ν_|(A.6)|



With an adapted version of _ancestor_ ( _i_ , _j_ ): 

 _tr ue ∃ω ∈_ Σ<sup>_+_</sup> : ( _j_ , _ω_ ) _∈ list_ ( _i_ ) _ancestor_ ( _i_ , _j_ ) _= tr ue ∃ω ∈_ Σ<sup>_+_</sup> , _k ∈ I_ : ( _k_ , _ω_ ) _∈ list_ ( _i_ ) _∧ ancestor_ ( _k_ , _j_ )  _f alse_ otherwise 

The list of operations is shown in table A.2. 

_APPENDIX A. APPENDIX_ 

198 

|Operation|Description,pre- andpost-conditions|
|---|---|
|_link_(_path_,_newpath_),<br>_link_(_i_,_u_,_name_)|For the existing file with ID_i = id_(_path_) this operation creates a new link with<br>name_ω = basename_(_newpath_), located in the parent directory node with ID<br>_u = id_(_parent_(_newpath_)).<br>Precondition:<br>_t ype_(_i_)_= f ile ∧ancestor_(_iroot_,_u_)_∧t ype_(_u_)_= dir ∧id_(_u_,_ω_)_= error_<br>Postcondition: (_i_,_ω_)_∈list_(_u_)|
|_unlink_(_path_),<br>_unlink_(_i_,_u_,_name_)|Removes the link to the node with ID_i_ and name_ω = basename_(_path_) from<br>parent directory node with ID_u = id_(_parent_(_path_)). If_t ype_(_i_)_= dir_, the directory<br>must be empty!<br>Precondition:_id_(_path_)_̸ = error ∧_(_i_,_ω_)_∈list_(_u_)<br>_∧_(_t ype_(_i_)_= f ile ∨_[_t ype_(_i_)_= dir ∧list_(_i_)_=_{}])<br>Postcondition: (_i_,_ω_)_∉list_(_u_)_∧id_(_path_)_= error_|
|_createdir_(_path_),<br>_createdir_(_i_,_u_,_name_)|See table 3.6|
|_create f ile_(_path_),<br>_createf ile_(_i_,_u_,_name_)|See table 3.6|
|_move_(_source_,_dest_),<br>_move_(_i_,_u_,_v_,_name_)|Moves a directory node with ID_i = id_(_source_). This may change the parent from ID<br>_u = id_(_parent_(_source_)) to_v = id_(_parent_(_dest_)) when moving it to a different<br>directory, or the name from_ω = basename_(_source_) to_ν = basename_(_dest_), or<br>both.<br>Precondition: _t ype_(_u_)_= dir ∧_(_i_,_ω_)_∈list_(_u_)_∧t ype_(_v_)_= dir_<br>_∧t ype_(_i_)_= dir ∧id_(_dest_)_= error ∧¬ancestor_(_i_,_v_)<br>Explanation:_¬ancestor_(_i_,_v_) ensures that the user cannot move a directory to a<br>destination dir below it, e.g. _source =_’/A’ cannot be moved to_dest = ’/A/x’._<br>Postcondition:_id_(_source_)_= error ∧id_(_dest_)_= i_|
|_edit_(_path_,_op_),<br>_edit_(_i_,_op_)|See table 3.6|



Table A.2: File system operations 

#### **A.2.2 Conflict definitions for H-All** 

The following list specifies all conflicts that apply to this file system definition. Only those aspects are explained which deviate from section 5.5 describing the conflicts of the _NH-MD_ file system. For simplicity, we assume that object IDs are equal on both replicas, and we ignore optimizations such as summarizing a _deletefile_ and a _createfile_ operation to _edit_ or considering two _createdir_ operations as pseudoconflicting. 

- **Create-Create:** See _NH-MD_ . 

   - Definition: _create X_ ( _i X_ , _uX_ , _name X_ ) _⊗ createY_ ( _iY_ , _uY_ , _nameY_ ) _=_ [ _uX = uY_ ] _∧_ [ _name X = nameY_ ] _∧_ [ _t ype X_ ( _i X_ ) _= dir ∨ t ypeY_ ( _iY_ ) _= dir ∨ contentX_ ( _i X_ ) _̸ = contentY_ ( _iY_ )] 

- **Link-Link:** 

   - **Description:** On both replicas a new link to an already synchronized file is created with the same name under the same parent directory. 

   - **Associated pattern:** Name clash conflict 

   - **Definition:** _linkX_ ( _i X_ , _uX_ , _name X_ ) _⊗ linkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X̸ = iY_ ) _∧_ ( _uX = uY_ ) _∧_ ( _name X = nameY_ ) 

   - **Violated precondition:** _id_ ( _u_ , _ω_ ) _= error_ 

   - **Resolution:** See name clash conflict. For any _name clash_ conflict in the _H-All_ file system we suggest that the loser operation is also chosen based on the amount of work of the operations. Exemplary, the increasing order may be: unlink, link, move, create/edit. 

_A.2. ALTERNATIVE FILE SYSTEM DEFINITIONS_ 

199 

- **Link-Unlink:** 

   - **Description:** On one replica all links of a synchronized file were removed (s.t. the file is deleted), on the other replica an additional link is created for the same file. 

   - **Associated pattern:** Delete conflict 

   - **Definition:** _linkX_ ( _i X_ , _uX_ , _name X_ ) _⊗ unlinkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X = iY_ ) _∧ pathsY_ ( _iY_ ) _= �_ 

   - **Violated precondition:** _t ype_ ( _i_ ) _= f ile_ 

   - **Resolution:** See delete conflict, where occurrences of _edit_ or _move_ are to be replaced with _link_ . 

- **Create-Link:** 

   - **Description:** On one replica a new file or dir with name _name_ is created in parent dir _v_ , on the other replica a new link for a synchronized file is created with name _name_ in _v_ . 

   - **Associated pattern:** Name clash conflict 

   - **Definition:** _create X_ ( _i X_ , _uX_ , _name X_ ) _⊗ linkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X̸ = iY_ ) _∧_ ( _uX = uY_ ) _∧_ ( _name X = nameY_ ) with _create_ : _= createdir ∨ create f ile_ 

   - **Violated precondition:** _id_ ( _u_ , _ω_ ) _= error_ 

   - **Resolution:** See name clash conflict 

- **Move-Link:** 

   - **Description:** On one replica a directory is moved to directory _v_ with new name _name_ , on the other replica a new link for a synchronized file is created with name _name_ in _v_ . 

   - **Associated pattern:** Name clash conflict 

   - **Definition:** _move X_ ( _i X_ , _uX_ , _v X_ , _name X_ ) _⊗ linkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X̸ = iY_ ) _∧_ ( _v X = uY_ ) _∧_ ( _name X = nameY_ ) 

   - **Violated precondition:** _id_ ( _u_ , _ω_ ) _= error_ 

   - **Resolution:** See name clash conflict 

- **Edit-Edit:** See _NH-MD_ . 

- **Move-Create:** See _NH-MD_ . Only affects moved directories. 

- **Edit-Delete:** 

   - **Description:** On one replica the content of an already synchronized file was changed, on the other replica all links to that file were removed. 

   - **Definition:** _editX_ ( _i X_ , _op X_ ) _⊗ unlinkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X = iY_ ) _∧ pathsY_ ( _iY_ ) _= �_ 

- **Move-Delete:** 

   - **Description:** On one replica a directory was moved, on the other replica the only existing link to that directory was removed. 

   - **Definition:** _move X_ ( _i X_ , _uX_ , _v X_ , _name X_ ) _⊗ unlinkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _i X = iY_ ) 

- **Move-Move (Source):** See _NH-MD_ . Only affects moved directories. 

- **Move-Move (Dest):** See _NH-MD_ . Only affects moved directories. 

- **Move-ParentDelete:** 

   - **Description:** On one replica a dir was deleted, on the other replica an object was moved to be an immediate child of the corresponding dir. 

_APPENDIX A. APPENDIX_ 

200 

   - **Definition:** _move X_ ( _i X_ , _uX_ , _v X_ , _name X_ ) _⊗ unlinkY_ ( _iY_ , _uY_ , _nameY_ ) _= pathsY_ ( _i X_ ) _̸ = �∧_ ( _v X = iY_ ) 

- **Create-ParentDelete:** 

   - **Description:** On one replica the only existing link to a dir _d_ was removed, on the other replica a file or directory was created as an immediate children of _d_ . 

   - **Definition:** _create X_ ( _i X_ , _uX_ , _name X_ ) _⊗ unlinkY_ ( _iY_ , _uY_ , _nameY_ ) _=_ ( _uX = iY_ ) with _create_ : _= createdir ∨ create f ile_ 

- **Move-Move (Cycle):** See _NH-MD_ . Only affects moved directories. 

#### **A.2.3 File system definition for NED-All** 

The _NED-All_ file system consists of a _non-hierarchical_ set of _files_ where each file has a ID _i ∈ I_ , a path _p ∈ P_ , byte-content _b ∈ B_ and the _lastmodified_ meta-datum _l ∈ L_ . _I_ is the set of unique IDs, _P_ is the set of valid paths, _B_ is the set of arbitrary byte sequences and _L_ is the set of all valid lastmodified meta-datum values (e.g. N). The file system _FS_ is thus a set of ( _i_ , _p_ , _b_ , _l_ ) tuples. Directories are not part of the model and can be considered to be just a visualization computed at run-time by a file manager that helps the user to locate files. We define the following helper predicates and functions: 



The following two invariants hold for _NED-All_ : 





with _R_ being the set of _relative paths_ (i.e., paths that do not start with _’/’_ ), such as _’file.ext’_ or _’somedir/file.ext’_ . The first invariant expresses that every file must have a different path and ID, while the second one illustrates that if a file exists in _FS_ with path _p_ , there cannot be another file with a path that starts with _p_ but has an non-empty suffix _r_ , because that would mean that _p_ is both a directory and file path at the same time. 

The list of operations is shown in table A.3. 

_A.2. ALTERNATIVE FILE SYSTEM DEFINITIONS_ 

201 

|Operation|Description,pre- andpost-conditions|
|---|---|
|_create f ile_(_path_)|Creates a file at_path_, generating its ID_i_ and_lastmodified_meta-datum_l_. Its<br>byte-content_b = �_is empty.<br>Precondition:_i ∉FS ∧_[_∀r ∈R_∄_q ∈P_:_q ∈FS ∧_(_q = path_/_r ∨path = q_/_r_)]<br>Postcondition: (_i_,_path_,_b_,_l_)_∈FS_|
|_move f ile_(_source_,_dest_),<br>_move f ile_(_i_,_dest_)|Moves the file with ID_i = id_(_source_) to destination path_dest_<br>Precondition:_i̸ = error ∧dest ∉FS_<br>_∧_[_∀r ∈R_∄_q ∈P_:_q ∈FS ∧_(_q = dest_/_r ∨dest = q_/_r_)]<br>Postcondition:_id_(_source_)_= error ∧id_(_dest_)_= i_|
|_delete f ile_(_path_),<br>_delete f ile_(_i_)|Deletes the file with ID_i = id_(_path_)<br>Precondition:_i̸ = error_<br>Postcondition: _path ∉FS_|
|_edit_(_path_,_op_),<br>_edit_(_i_,_op_)|Opens a handle for the file with ID_i = id_(_path_) for_writing_, performs the<br>operation_op_ (e.g. adding, removing or changing bytes at specific positions<br>within the file), then closes the handle again.<br>Precondition:_i̸ = error_. Let_lpre = lastmodi f ied_(_i_)<br>Postcondition:_id_(_path_)_̸ = error ∧lastmodif ied_(_i_)_̸ = lpre_|



Table A.3: File system operations 

#### **A.2.4 Conflict definitions for NED-All** 

The following list provides the formal specification of all conflicts that apply to the NED-All file system definition. Descriptions, associated pattern, resolution and other aspects are not repeated, see section 5.5. 

- **Create-Create:** _create f ile X_ ( _pathX_ ) _⊗ create f ileY_ ( _pathY_ ) _=_ ( _pathX = pathY_ ) _∧_ [ _contentX_ ( _pathX_ ) _̸ = contentY_ ( _pathY_ )] 

- **Edit-Edit:** See _NH-MD_ . 

- **Move-Create:** _create f ile X_ ( _path_ ) _⊗ move f ileY_ ( _i_ , _dest_ ) _=_ ( _path = dest_ ) 

- **Edit-Delete:** _editX_ ( _i X_ , _op X_ ) _⊗ delete f ileY_ ( _iY_ ) _=_ ( _i X = iY_ ) 

- **Move-Delete:** _move f ile X_ ( _i X_ , _destX_ ) _⊗ delete f ileY_ ( _iY_ ) _=_ ( _i X = iY_ ) 

- **Move-Move (Source):** _move f ile X_ ( _i X_ , _destX_ ) _⊗ move f ileY_ ( _iY_ , _destY_ ) _=_ ( _i X = iY_ ) _∧_ ( _destX̸ = destY_ ) 

- **Move-Move (Dest):** _move f ile X_ ( _i X_ , _destX_ ) _⊗ move f ileY_ ( _iY_ , _destY_ ) _=_ ( _destX = destY_ ) 

- **Node-typing:** 

   - **Description:** On replica _X_ the user creates or moves a file to path _p_ , on replica _Y_ the user creates/moves a file to some path _q_ where _q = p_ /... . Consequently, _p_ points to a file on _X_ but to a directory on _Y_ . 

   - **Associated pattern:** Indirect conflict 

   - **Definition:** _createor move X_ ( _pathX_ ) _⊗ createor moveY_ ( _pathY_ ) _= ∃r ∈ R_ : ( _pathX = pathY_ / _r_ ) _∨_ ( _pathY = pathX_ / _r_ ) with _createor move_ ( _path_ ) : _= create f ile_ ( _path_ ) _∨ move f ile_ ( _i_ , _path_ ) 

   - **Violated precondition:** _∀r ∈ R_ ∄ _q ∈ P_ : _q ∈ FS ∧_ ( _q = dest_ / _r ∨ dest = q_ / _r_ ) 

   - **Resolution:** One of the paths has to be automatically renamed. We suggest to rename the shorter of the two paths, because this gives higher priority (and therefore stability) to directory paths. 

_A.3. PROOF FOR IMPOSSIBLE MOVE OPERATION CYCLES_ 

203 

### **A.3 Proof for impossible move operation cycles** 

This section addresses pure move operation cycles (see section 4.2.7.2 on page 57). It proves that it is impossible to have cycles that exclusively consist of _move_ operations connected only by order dependency rule 8. 

To reiterate, rule 8 states that a _move_ operation _oi_ affecting directory _A_ has to be executed _after_ another _move_ operation _o j_ affecting directory _B_ iff in the database snapshot, _B_ is below _A_ , and in the current snapshot, _A_ is below _B_ . 

Formally, let _O=compute_ops(db, snapshot)_ , where _db_ and _snapshot_ are taken for replica _X_ at times _t_ 1 and _t_ 2 respectively. Let _O_<sup>¯</sup> be a _list_ created from set _O_ . Then rule 8 formally states the following: 



_⇐⇒ ancestor_ ( _db_ , _id_ ( _oi_ ), _id_ ( _o j_ )) _∧ ancestor_ ( _snapshot_ , _id_ ( _o j_ ), _id_ ( _oi_ )) (A.9) Figure A.1 illustrates an example, where _o j_ affects path ’a/b’ (moved to ’b’) and _oi_ affects path ’a’ (moved to ’b/a’), therefore, according to equation A.9, _O_<sup>¯</sup> _=_ � _o j_ , _oi_ � must hold. 



<!-- Start of picture text -->
X X<br>a 1. b<br>b 2. a<br>8<br>move('a/b', 'b') move('a', 'b/a')<br>8<br><!-- End of picture text -->

Figure A.1: Move operation cycle example 

**Theorem 9.** _It is impossible to build a cycle C_<sup>¯</sup> _=_ [ _ok_ ]<sup>_n_</sup> _k=_ 1<sup>_whichisasubsetofO,_¯</sup><sup>_s.t.∀ok∈C_¯:</sup><sup>_ok∈O_¯</sup><sup>_∧_</sup> _t ype_ ( _ok_ ) _= move where equation A.9 is true for each pair of adjacent operations_ ( _o j_ , _oi_ ) _∈ C_<sup>¯</sup> _with_ 



Informally: it is impossible to have move operation cycles whose move operations are connected only by rule 8. 

_Proof._ by contradiction: suppose you built _C_<sup>¯</sup> _=_ [ _o_ 1,..., _on_ ] s.t. it contains _n_ move operations where equation A.9 holds for every pair ( _o_ 1, _o_ 2), ..., ( _on−_ 1, _on_ ). At this point _C_<sup>¯</sup> is still a chain, not a cycle. Since the _ancestor_ () predicate is _transitive_<sup>9</sup> , and since equation A.9 holds for these pairs (as stated above) then 



must also hold. To turn _C_ ¯ into a _closed_ cycle, equation A.9 would also have to hold for one specific _r_ s.t. ( _on_ , _or_ ), _r ∈_ [1,..., _n −_ 1], i.e., _ancestor_ ( _db_ , _id_ ( _or_ ), _id_ ( _on_ )) would have to hold. This contradicts with equation A.10, because it’s impossible for _ancestor_ ( _db_ , _id_ ( _or_ ), _id_ ( _on_ )) and _ancestor_ ( _db_ , _id_ ( _on_ ), _id_ ( _ok=r_ )) to be true at the same time. 

9 _ancestor_ ( _idX_ , _idY_ ) _∧ ancestor_ ( _idY_ , _idc_ ) _=⇒ ancestor_ ( _idX_ , _idc_ ) 

_A.4. OPERATION REORDERING METHODS_ 

205 

### **A.4 Operation reordering methods** 

Here we present algorithms 5-12. Each function detects and reorders incorrectly ordered operations. 

- 1 **def** fix_delete_before_move (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == delete : 

- 3 foreach move_op **in** ops where optype (move_op) == move: 

- 4 delete_parent_id = **id** (db, parent ( path (op) ) ) 

- 5 ( source , dest ) = path (move_op) 

- 6 move_dest_parent_id = **id** ( snapshot , parent ( dest ) ) 

- 7 **i f** delete_parent_id == move_dest_parent_id : 

- 8 **i f** basename( path (op) ) == basename( dest ) : 

- 9 **i f** sorted_ops . index (op) > sorted_ops . index (move_op) : 

- 10 move_first_after_second (move_op, op, sorted_ops ) 

**Algorithmus 5 :** Pseudo-code for _fix_delete_before_move()_ 

- 1 **def** fix_move_before_create (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == move: 

- 3 foreach create_op **in** ops where optype ( create_op ) == create : 

- 4 ( source , dest ) = path (op) 

- 5 source_parent_id = **id** (db, parent ( source ) ) 

- 6 create_parent_id = **id** ( snapshot , parent ( path ( create_op ) ) ) 

- 7 **i f** source_parent_id == create_parent_id : 

- 8 **i f** basename( source ) == basename( path ( create_op ) ) : 

- 9 **i f** sorted_ops . index (op) > sorted_ops . index ( create_op ) : 

- 10 move_first_after_second ( create_op , op, sorted_ops ) 

**Algorithmus 6 :** Pseudo-code for _fix_move_before_create()_ 

- 1 **def** fix_move_before_delete (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == delete **and type** (op) == **dir** : 

- 3 foreach move_op **in** ops where optype (move_op) == move: 

- 4 ( source , dest ) = path (move_op) 

- 5 delete_dir_path = path (op) 

- 6 **i f** source . startswith ( delete_dir_path + ’ / ’ ) : 

- 7 **i f** sorted_ops . index (move_op) > sorted_ops . index (op) : 8 move_first_after_second (op, move_op, sorted_ops ) 

**Algorithmus 7 :** Pseudo-code for _fix_move_before_delete()_ 

- 1 **def** fix_create_before_move (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == create **and type** (op) == **dir** : 

- 3 foreach move_op **in** ops where optype (move_op) == move: 

- 4 ( source , dest ) = path (move_op) 

- 5 move_dest_parent_id = **id** ( snapshot , parent ( dest ) ) 

- 6 **i f** move_dest_parent_id == **id** (op) : 

- 7 **i f** sorted_ops . index (op) > sorted_ops . index (move_op) : 8 move_first_after_second (move_op, op, sorted_ops ) 

   - **Algorithmus 8 :** Pseudo-code for _fix_create_before_move()_ 

_APPENDIX A. APPENDIX_ 

206 

- 1 **def** fix_delete_before_create (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == delete : 

- 3 foreach create_op **in** ops where optype ( create_op ) == create : 

- 4 delete_parent_id = **id** (db, parent ( path (op) ) ) 

- 5 create_parent_id = **id** ( snapshot , parent ( path ( create_op ) ) ) 

- 6 **i f** delete_parent_id == create_parent_id : 

- 7 **i f** basename( path (op) ) == basename( path ( create_op ) ) : 

- 8 **i f** sorted_ops . index (op) > sorted_ops . index ( create_op ) : 

- 9 move_first_after_second ( create_op , op, sorted_ops ) 

**Algorithmus 9 :** Pseudo-code for _fix_delete_before_create()_ 

- 1 **def** fix_move_before_move_occupied (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == move: 

- 3 foreach move_op **in** ops where optype (move_op) == move: 

- 4 **i f** op == move_op: 

- 5 **continue** 

- 6 ( source , dest ) = path (move_op) 

- 7 source_parent_id = **id** (db, parent ( source ) ) 

- 8 ( other_source , other_dest ) = path (move_op) 

- 9 move_dest_parent_id = **id** ( snapshot , parent ( other_dest ) ) 

- 10 **i f** source_parent_id == move_dest_parent_id : 

- 11 **i f** basename( source ) == basename( other_dest ) : 

- 12 **i f** sorted_ops . index (op) > sorted_ops . index (move_op) : 

- 13 move_first_after_second (move_op, op, sorted_ops ) 

**Algorithmus 10 :** Pseudo-code for _fix_move_before_move_occupied()_ 

- 1 **def** fix_create_before_create (ops , sorted_ops , db, snapshot ) : 

- 2 foreach op **in** ops where optype (op) == create : 

- 3 foreach cd_op **in** ops where optype (cd_op) == create **and type** (cd_op) == **dir** : 

- 4 **i f** op == cd_op : 

- 5 **continue** 

- 6 **i f** parent ( path (op) ) == path (cd_op) : 

- 7 **i f** sorted_ops . index (cd_op) > sorted_ops . index (op) : 

- 8 move_first_after_second (op, cd_op , sorted_ops ) 

**Algorithmus 11 :** Pseudo-code for _fix_create_before_create()_ 

_A.4. OPERATION REORDERING METHODS_ 

207 

- 1 **def** fix_move_before_move_parent_child_flip (ops , sorted_ops , db, snapshot ) : 

- 2 foreach x_op **in** ops where optype (x_op) == move **and type** (x_op) == **dir** : 

- 3 foreach y_op **in** ops where optype (y_op) == move **and type** (y_op) == **dir** : 4 **i f** x_op == y_op : 

- 5 **continue** 

- 6 ( x_source , x_dest ) = path (x_op) 

- 7 ( y_source , y_dest ) = path (y_op) 

- 8 is_x_below_y = x_dest . startswith ( y_dest + ’ / ’ ) 9 **i f not** is_x_below_y : 

- 10 **continue** 

- 11 is_y_below_x_in_db = y_source . startswith ( x_source + ’ / ’ ) 12 **i f** is_y_below_x_in_db : 

- 13 **i f** sorted_ops . index (y_op) > sorted_ops . index (x_op) : 

- 14 move_first_after_second (x_op , y_op , sorted_ops ) 

**Algorithmus 12 :** Pseudo-code for _fix_move_before_move_parent_child_flip()_ 

_A.5. FINDING CONFLICTS IN SNAPSHOTS_ 

209 

### **A.5 Finding conflicts in snapshots** 

#### **A.5.1 Corresponding object id** 

The following algorithm of function `corresponding_object_id(i, r)` which is given an ID `i` of an object on replica `r` , and finds the ID of the corresponding object (should it exist) on the other replica by using the replica-specific IDs and the closest _idb_ . Ideally, the object with ID `i` is known in the database. If it is not, the algorithm finds the closest parent directory known in the database and attempts to find the corresponding parent, using object _names_ to fill the gaps. 

- 1 Global data : snapshot_x , snapshot_y , db: the current state of each replica **and** the database state 

- 2 Input : i , r : ID **and** replica identifier **for** which the ID **is** given 

- 3 Output : corresponding ID on the other replica , **or** error 

- 4 

- 5 **i f** r **is** X: 

- 6 snapshot = snapshot_x 7 snapshot_other = snapshot_y 8 other_replica = Y 

- 9 **else** : 

- 10 snapshot = snapshot_y 11 snapshot_other = snapshot_x 12 other_replica = X 

- 13 

14 id_db = dbid (db, i , r ) 15 **i f** id_db **is** error : 16 names = [ ] _# i n i t i a l i z e empty l i s t_ 17 current_path = path ( snapshot , i ) 

- 18 _# Find parent dir known in the database :_ 19 **while** [ current_id_db = dbid (db, **id** ( snapshot , current_path ) , r ) ] **is** error : 20 names += basename( current_path ) 21 current_path = parent ( current_path ) 

- 22 

- 23 names = reverse (names) 

- 24 _# builds string of all names with ’ / ’ in−between_ 25 relative_traversed_path = ’ / ’ . join (names) 

- 26 

27 corresponding_parent_id = **id** (db, current_id_db , other_replica ) 

- 28 corresponding_parent_path = path ( snapshot_other , corresponding_parent_id ) 

- 29 **i f** corresponding_parent_path **is** error : 30 **return** error 

31 corresponding_path = corresponding_parent_path + ’ / ’ + relative_traversed_path 32 corresponding_id = **id** ( snapshot_other , corresponding_path ) _# may be error_ 

- 33 **return** corresponding_id 

- 34 

- 35 **else** : 

36 other_site_id = **id** (db, id_db , other_replica ) 

- 37 **return** other_site_id 

#### **A.5.2 Corresponding object id (direct)** 

This section presents function `cid(i, r)` , a variant of the above algorithm that requires that the object for which to find the corresponding object in the other replica is known to the database. 

- 1 Global data : db: the database state 

_APPENDIX A. APPENDIX_ 

210 

- 2 Input : i , r : ID **and** replica identifier **for** which the ID **is** given 

- 3 Output : corresponding ID on the other replica , **or** error 

- 4 

- 5 **i f** r **is** X: 6 other_replica = Y 7 **else** : 8 other_replica = X 9 

- 10 id_db = dbid (db, i , r ) 11 **i f** id_db **is** error : 12 **return** error 

- 13 other_site_id = **id** (db, id_db , other_replica ) 

- 14 **return** other_site_id 

_A.6. FINDING CONFLICTS IN UPDATE TREES_ 

211 

### **A.6 Finding conflicts in update trees** 

#### **A.6.1 Finding conflicts** 

The following algorithm is given the update tree structures from the local and remote replica and finds conflicts by iterating over all nodes in a breadth-first approach: 

1 Input : local_update_tree , remote_update_tree 2 Output : **l i s t** of conflict objects to be **sorted and** resolved by the resolver 

- 3 

- 4 conflicts = [ ] 

- 5 local_move_dir_nodes = [ ] 

- 6 remote_move_dir_nodes = [ ] 

- 7 

- 8 foreach node **in** ( local_update_tree + remote_update_tree ) : 

- 9 **i f** current_node . is_dir **and** ChangeEvent .Move **in** current_node . events : 

- 10 **i f** current_node . side **is** local : 11 local_move_dir_nodes += current_node 

- 12 **else** : 

- 13 remote_move_dir_nodes += current_node 

14 

- 15 **i f** ChangeEvent . Create **in** node . events **and** ConflictType . Create_Create **not in** node . conflicts_already_considered : 

- 16 conflicts += check_create_create_conflict (node) 

- 17 **i f** ChangeEvent . Edit **in** node . events **and** ConflictType . Edit_Edit **not in** node . conflicts_already_considered : 

- 18 conflicts += check_edit_edit_conflict (node) 

- 19 **i f** ChangeEvent . Delete **in** node . events : 20 **i f** node . is_dir : 

- 21 conflicts += check_move_parentdelete_conflict (node) 

- 22 conflicts += check_create_parentdelete_conflict (node) 

- 23 conflicts += check_move_delete_conflict (node 

- 24 conflicts += check_edit_delete_conflict (node) 

- 25 **i f** ChangeEvent .Move **in** node . events 

- 26 conflicts += check_move_create_conflict (node 

- 27 **i f** ConflictType .Move_Move_Dest **not in** node . conflicts_already_considered : 28 conflicts += check_move_move_dest_conflict (node 

- 29 **i f** ConflictType . Move_Move_Source **not in** node . conflicts_already_considered 30 conflicts += check_move_move_source_conflict (node 31 

- 32 conflicts += determine_move_move_cycle_conflicts ( local_move_dir_nodes , remote_move_dir_nodes) 

Each _Conflict_ data structure consists of the conflict type, and the local and remote nodes whose operations are conflicting. The `conflicts_already_considered` list is maintained for every node, to avoid that _symmetric_ conflicts, such as Edit-Edit or Move-Move(Source) conflicts are detected twice. The function `corresponding_node_in_other_tree()` is implemented exactly like `corresponding_object_id()` in section A.5.1, but is adapted to work on _update trees_ instead of _snapshots_ . `corresponding_node_direct()` works like `cid()` in section A.5.2, also adapted to work on _update tree_ nodes. The algorithms that find individual conflicts, e.g. `check_create_create_conflict()` , are explained in the subsequent subsections. 

#### **A.6.2 Create-Create conflict** 

1 Input : create_node 2 Output : conflict ( optional ) 3 

- 4 **i f** first_sync : 

- 5 corresponding_parent_node = corresponding_node_in_other_tree ( create_node . parent ) 

- 6 **else** : 

- 7 corresponding_parent_node = corresponding_node_direct ( create_node . parent ) 

- 8 **i f** corresponding_parent_node == error : 

- 9 **return** 

_APPENDIX A. APPENDIX_ 

212 

- 10 child_request = ChildNodeRequest . ReturnOnlyNonDelete 

- 11 corresponding_create_node = corresponding_parent_node . get_child ( create_node .name, child_request ) 

12 

- 13 **i f** corresponding_create_node != error **and** ChangeEvent . Create **in** corresponding_create_node . events : 

- 14 **i f** ! is_pseudo_conflict (node) : 

- 15 conflict = make_create_create_conflict ( create_node , corresponding_create_node ) 16 corresponding_create_node . conflicts_already_considered += ConflictType . Create_Create 17 **return** conflict 

Finding Create-Create conflicts is straightforward and shown in the algorithm above. Instead of providing pseudo-code for `is_pseudo_conflict()` we refer to the FOL definition in section 5.5.1 on page 80 for more details. 

#### **A.6.3 Edit-Edit conflict** 

1 Input : edit_node 2 Output : conflict ( optional ) 

3 

- 4 corresponding_node = corresponding_node_direct ( edit_node ) 

- 5 **i f** corresponding_node != error **and** ChangeEvent . Edit **in** corresponding_node . events : 6 **i f** ! is_pseudo_conflict ( edit_node ) : 

- 7 conflict = make_edit_edit_conflict ( edit_node , corresponding_node ) 8 corresponding_node . conflicts_already_considered += ConflictType . Edit_Edit 9 **return** conflict 

#### **A.6.4 Move-Create** 

1 Input : move_node 2 Output : conflict ( optional ) 3 

- 4 move_parent_node = move_node. parent 

- 5 corresponding_parent_node = corresponding_node_direct (move_parent_node) 

6 **i f** corresponding_parent_node != error : 

7 child_request = ChildNodeRequest . ReturnOnlyNonDelete 8 potential_create_child_node = corresponding_parent_node . get_child (move_node.name, child_request ) 9 **i f** potential_create_child_node != error **and** ChangeEvent . Create **in** potential_create_child_node . events : 10 conflict = make_move_create_conflict (move_node, potential_create_child_node ) 11 **return** conflict 

#### **A.6.5 Edit-Delete** 

1 Input : delete_node 2 Output : conflict ( optional ) 3 

- 4 **i f** delete_node . is_dir : 

- 5 **return** 

6 

- 7 corresponding_edit_node = corresponding_node_direct ( delete_node ) 

8 **i f** corresponding_edit_node != error **and** ChangeEvent . Edit **in** corresponding_edit_node . events : 9 conflict = make_edit_edit_conflict ( delete_node , corresponding_edit_node ) 10 **return** conflict 

#### **A.6.6 Move-Delete** 

1 Input : delete_node 2 Output : conflict ( optional ) 

- 3 

- 4 corresponding_move_node = corresponding_node_direct ( delete_node ) 

_A.6. FINDING CONFLICTS IN UPDATE TREES_ 

213 

- 5 **i f** corresponding_move_node == error **or** ChangeEvent .Move **not in** corresponding_move_node . events : 

- 6 **return** 

- 7 

- 8 conflict = make_move_delete_conflict ( delete_node , corresponding_move_node) 9 **return** conflict 

#### **A.6.7 Move-ParentDelete** 

To find Move-ParentDelete conflicts we iterate over all those sub-nodes of the corresponding node of the deleted dir which have the Move change-event. If at least one such move node exists, a MoveParentDelete conflict is found. 

1 Input : delete_node ( only directory nodes) 2 Output : conflict ( optional ) 

- 3 

- 4 corresponding_dir_node = corresponding_node_direct ( delete_node ) 

- 5 

- 6 **i f** corresponding_dir_node != error : 

- 7 **i f** ChangeEvent . Delete **in** corresponding_dir_node . events : 8 **return** 

- 9 

- 10 move_nodes = [ ] 

- 11 foreach sub_node **in** corresponding_dir_node . children : 

12 **i f** ChangeEvent .Move **in** sub_node . events : 13 move_nodes += move_node 14 

15 **i f not** move_nodes .empty() : 16 conflict = make_move_parentdelete_conflict ( delete_node , move_nodes) 17 **return** conflict 

#### **A.6.8 Create-ParentDelete** 

Finding Create-ParentDelete conflicts works very similar to finding Move-ParentDelete conflicts. Instead of iterating over sub-nodes with a _Move_ change-event, we iterate over those with a _Create_ changeevent. 

1 Input : delete_node ( only directory nodes) 2 Output : conflict ( optional ) 3 

- 4 corresponding_dir_node = corresponding_node_direct ( delete_node ) 

- 5 **i f** corresponding_dir_node != error : 

- 6 **i f** ChangeEvent . Delete **in** corresponding_dir_node . events : 7 **return** 

- 8 

- 9 create_nodes = [ ] 

- 10 foreach sub_node **in** corresponding_dir_node . children : 11 **i f** ChangeEvent . Create **in** sub_node . events : 12 create_nodes += sub_node 

13 

- 14 **i f not** create_nodes .empty() : 15 conflict = make_create_parentdelete_conflict ( delete_node , create_nodes ) 16 **return** conflict 

#### **A.6.9 Move-Move (Source)** 

1 Input : move_node 2 Output : conflict ( optional ) 

- 3 

- 4 corresponding_move_node = corresponding_node_direct (move_node) 

_APPENDIX A. APPENDIX_ 

214 

- 5 **i f** ChangeEvent .Move **not in** corresponding_move_node . events : 

- 6 **return** 

- 7 **i f** corresponding_move_node != error : 

- 8 corresponding_move_node . conflicts_already_considered . append( ConflictType . Move_Move_Source) 

- 9 **i f** move_node.name != corresponding_move_node .name **or** move_node. parent != corresponding_move_node . parent : 

10 conflict = make_move_move_source_conflict (move_node, corresponding_move_node) 

- 11 **return** conflict 

#### **A.6.10 Move-Move (Dest)** 

- 1 Input : move_node 

2 Output : conflict ( optional ) 

- 3 

- 4 node_parent_in_other_tree = corresponding_node_direct (move_node. parent ) 

- 5 **i f** node_parent_in_other_tree != error : 

- 6 child_request = ChildNodeRequest . ReturnOnlyNonDelete 7 potential_move_child = node_parent_in_other_tree . get_child (move_node.name, child_request= child_request ) 

- 8 **i f** potential_move_child != error && ChangeEvent .Move **in** potential_move_child . events && potential_move_child . i_db != move_node. i_db : 

- 9 conflict = make_move_move_dest_conflict (move_node, potential_move_child ) 

- 10 potential_move_child . conflicts_already_considered . append( ConflictType .Move_Move_Dest) 11 **return** conflict 

#### **A.6.11 Move-Move (Cycle)** 

1 Input : local_move_dir_nodes , remote_move_dir_nodes 2 Output : **l i s t** of conflicts ( optional ) 

- 3 

- 4 conflicts = [ ] 

- 5 

6 **for** local_node **in** local_move_dir_nodes : 

- 7 **for** remote_node **in** remote_move_dir_nodes : 

- 8 **i f** local_node . i_db == remote_node . i_db : 9 **continue** 

- 10 local_db_path = local_db_snapshot . get_relative_path_for_db_id ( local_node . i_db ) 11 remote_db_path = remote_db_snapshot . get_relative_path_for_db_id (remote_node . i_db ) 

- 12 **i f** local_db_path . startswith (remote_db_path + ’ / ’ ) **or** remote_db_path . startswith ( local_db_path + ’ / ’ ) : 

- 13 **continue** 

- 14 _# The paths are independent . Check i f they are now in a cyclic relationship_ 

- 15 corresponding_local_node = corresponding_node_direct (remote_node) 

- 16 corresponding_remote_node = corresponding_node_direct ( local_node ) 

- 17 **i f** is_a_below_b (a=local_node , b=corresponding_local_node ) **and** is_a_below_b (a=remote_node , b= corresponding_remote_node ) : 

18 conflicts += make_move_move_cycle_conflict ( local_node , remote_node) 19 

- 20 **return** conflicts 

_A.7. RESOLVING CONFLICTS IN UPDATE TREES_ 

215 

### **A.7 Resolving conflicts in update trees** 

#### **A.7.1 Undoing a move** 

The `undo_move(move_node)` function is used in the resolution of Move-Delete, Move-Move(Source), Move-Move(Cycle) and Move-ParentDelete conflicts. Table A.4 shows caveats that need to be considered. They are illustrated by example that starts with the initial situation shown in figure A.2, attempting to undo the operation _move(“A/B”, “B_moved”)_ . 



<!-- Start of picture text -->
S<br>1 2<br>            A: Dir                                 f: File<br>3<br>            B: Dir<br><!-- End of picture text -->

Figure A.2: Initial situation for illustrating problems for `undo_move()` 

The algorithm solving all these issues is shown below: 

Pseudo-code for `undo_move()` : 

1 Input : move_node, well _−_ known root_dir_id 

- 2 Output : a Move operation that undoes the move of move_node 

- 3 

- 4 

- 5 origin_dir_node = move_node. get_move_origin_parent_node () 

- 6 move_origin_filename = basename(move_node. move_origin ) 

- 7 is_undoing_move_possible = True 

- 8 **i f** is_a_below_b (a=origin_dir_node , b=move_node) : 

- 9 is_undoing_move_possible = False 

- 10 **e l i f** ChangeEvent . Delete **in** origin_dir_node . events : 

- 11 is_undoing_move_possible = False 

- 12 **else** : 

- 13 potential_origin_node = origin_dir_node . get_child ( move_origin_filename , child_request= ChildNodeRequest . ReturnOnlyNonDelete) 

- 14 **i f** potential_origin_node != error **and** potential_origin_node . change_events . contains_any ( [ ChangeEvent . Create , ChangeEvent .Move] ) : 

- 15 is_undoing_move_possible = False 

- 16 

- 17 target_parent_id = origin_dir_node **i f** is_undoing_move_possible **else** root_dir_id 

- 18 target_name = move_origin_filename **i f** is_undoing_move_possible **else** add_conflict_suffix ( move_origin_filename ) 

- 19 also_update_db = **not** is_undoing_move_possible 

- 20 

- 21 **return** MoveOperation(move_node, target_parent_id , target_name , also_update_db ) 

#### **A.7.2 Move-Delete** 

- 1 Input : move_node, delete_node 

- 2 Output : MoveOperation **or** DeleteOperation that resolves the conflict 

- 3 

- 4 **i f** configured option == Move Wins : 5 deleted_child_nodes = delete_node . get_child_nodes_recursively () 

- 6 deleted_child_node_db_ids = [ ] 

- 7 foreach node **in** deleted_child_nodes : 

_APPENDIX A. APPENDIX_ 

216 



<!-- Start of picture text -->
Caveat Illustration Resolution<br>L<br>1 3 2<br>            A_moved: Dir            Move ’A’ (P-ID: -1)                     B_moved: Dir            Move ’A/B’ (P-ID: 1)                     f: File<br>Path of move Use IDs to locate the<br>origin parent dir current path of the<br>has changed move origin parent<br>dir, instead of the<br>path from the<br>snapshot<br>L<br>3<br>2<br>            B_moved: Dir                         f: File<br>Move ’A/B’ (P-ID: 1)<br>1<br>            A_moved: Dir<br>Move ’A’ (P-ID: -1)<br>Move origin parent Move the object<br>dir is now below whose move should<br>the move node be undone to the<br>root directory,<br>L appending a unique<br>conflict suffix.<br>3 1 2<br>            B_moved: Dir                         A: Dir                         f: File<br>Move ’A/B’ (P-ID: 1)         Delete<br>Move origin parent<br>dir was deleted<br>L<br>3<br>1<br>            B_moved: Dir                         A: Dir<br>Move ’A/B’ (P-ID: 1)<br>2<br>            B: File<br>Move ’f’ (P-ID: -1)<br>The original name<br>of the object under<br>the origin parent<br>dir is already in use<br><!-- End of picture text -->

Table A.4: Caveats to consider in `undo_move()` 

_A.7. RESOLVING CONFLICTS IN UPDATE TREES_ 

217 

11 all_child_node_db_ids = database . get_child_node_db_ids ( delete_node . i_db ) 12 orphan_node_db_ids = all_child_node_db_ids _−_ deleted_child_node_db_ids 13 **for** o_db_id **in** orphan_node_db_ids : 14 orphan_node = delete_node . root . get_node_by_db_id ( o_db_id ) 15 new_name = orphan_node .name() + get_conflict_suffix () 

25 **return** undo_move(move_node) 

26 **else** : 

27 db_modifications = database . build_delete_rows_query ( delete_node . i_db ) 

- 8 deleted_child_node_db_ids += node . i_db 

- 9 db_modifications = [ ] 

10 

   - **i f** delete_node . is_dir : 

      - new_name = orphan_node .name() + get_conflict_suffix () 

- 16 db_modifications += database . build_update_path_query_for_db_id ( o_db_id , new_name) 

17 winner_side = delete_node . side 

18 conflict_resolver . register_orphans (orphan_node_db_ids , winner_side ) 

19 db_modifications += database . build_delete_rows_query ( delete_node . i_db ) 

20 db_modifications += database . build_delete_rows_query ( deleted_child_node_db_ids ) 

21 **return** DeleteOperation ( delete_node , db_modifications , omit=True) 

- 22 **else** : 

23 _# delete wins_ 

- 24 **i f** delete_node . is_dir : 

28 **return** DeleteOperation ( delete_node , db_modifications , omit=False ) 

The necessary steps for resolving Move-Delete conflicts are shown in the above algorithm. While the steps are straightforward in case the strategy is “delete wins”, the steps for “move wins” are more involved. As outlined in subsection 5.5.5, the goal is that if the _delete_ replica moved objects from below _delete_node_ to a location outside of _delete_node_ prior to deleting the directory, that these moves should be synchronized on the mover’s replica eventually. 

To realize this, our implementation starts by marking the rows for _delete_node_ and all those child nodes that were actually deleted on the deleter’s replica to be removed from the database (removal of these rows is executed by our propagator component when executing the `DeleteOperation` ). Those corresponding files and dirs that still physically exist on the mover’s replica will therefore be detected as new and be synchronized in subsequent sync iterations. The deletions of rows is problematic, however, in cases where the delete replica moved objects outside of _delete_node_ . Their rows should remain in the database, but they would become _orphaned_ , as they lack some of their parent paths. Not fixing these orphan paths can lead to multiple issues in consecutive synchronization iterations. We therefore bend the paths of orphans to a unique path (with conflict suffix) on the _root_ level in the database. For instance, an orphan node with path “/dir_that_deleter_replica_deleted/some_dir” is set to “/some_dirconflict-<datetime>-<random string>”. This will cause a Move-Move(Source) conflict in the subsequent sync iteration for each orphan node, because physically the corresponding file or dir isn’t at this location on either replica. Since our goal is that the orphans end up in the same location as they are on the deleter’s replica, we temporarily register the affected nodes in an in-memory _registry_ so that the resolver automatically resolves them in favor of the deleter’s replica (because it finds the nodes the registry). This overrules the default resolution option for Move-Move (Source) conflicts in this case. Move-Move (Source) conflicts resolved in this way are not presented as conflicts to the user, because it wasn’t the user who produced them, but the synchronizer. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

219 

### **A.8 File synchronizer comparative test details** 

#### **A.8.1 Conflict-free operations** 

##### **A.8.1.1 Result remarks** 

- A) OneDrive’s macOS _remote_ client does not pick up the individual _delete_ operation while the client is _online_ in a timely manner. The operation is only synchronized either when waiting for 30-120 seconds, restarting the remote client, or when performing additional _create_ or _move_ operations on the _remote replica_ . 

- B) Low network traffic indicates that _move_ operations are detected by the remote and local Dropbox client correctly, but the local client does generally _not_ move objects (in any test!). It instead creates a copy of the object at the destination, followed by deleting the source (or moving it to an internal cache). This behavior is particularly inefficient if the _move_ operation targets a large file or a directory with many (large) sub-files. 

- C) When the _local_ client of Google Backup and Sync is on a macOS VM and the _move_ operation targets a non-empty _directory_ , the local client does not move it but instead creates a new directory at the target location, moves all immediate child objects of the source directory into the target location and finally deletes the source directory. If the local client is on a Windows VM, or when moving a _file_ , the client moves the object as expected. 

- D) This test (and follow-up tests) shows that Unison does not support _move_ operations, which is also described in their corresponding paper [BP98]. The _remote_ client detects the _move_ operation as _delete_ operation of the source file and an additional _create_ operation at the destination path. Similar to Dropbox (see remark B), Unison maintains a temporary cache of files during synchronization, s.t. the _local client_ actually _copies_ the moved object instead of deleting and retransmitting it. 

- E) Various issues occur, depending on the concrete test. For some tests, file system objects have a “ (1)” appended to their name in one replica but not in the other. In other tests, the remote client GUI indicates errors but states that the cause is “unknown”. In some tests, the replica’s final state only becomes synchronized when restarting both clients. More detailed results are found in the appendix section A.8.1.2. 

- F) Dropbox duplicates directory structures, see appendix section A.8.1.3. 

- G) Final file system structure contains two files: the original file at the destination path and the updated file at the source path. 

- H) Since the _remote_ client synchronizes the _move_ operation as _delete_ + _create_ operation to the server replica, the _local client_ detects an _Edit-Delete_ conflict for that file and asks the user to resolve the conflict. 

##### **A.8.1.2 Complex single-replica operations** 

Here we present the five concrete tests whose collection we labeled “Complex single-replica operations”, together with the detailed results. 

**A.8.1.2.1 Test description** The following two tables provide a description of the five tests: 

_APPENDIX A. APPENDIX_ 

220 

|Name|Move-Swap|Move-Chain|
|---|---|---|
|Description|Two objects are swapped by applying<br>three_move_operations. A state-based<br>update detector computes only_two_<br>operations, which the client cannot<br>apply to the other replica without<br>special handling.|The names of three objects are<br>shifted along the alphabet using<br>chained move operations. The client<br>on the other replica must apply the<br>operation chain in the same order.<br>We tested_forward_and_backward_<br>shifting to find out whether internal<br>client sorting correctly deals with<br>both directions.|
|Parameters|O, F|O, F|
||S|S|
|Base scenario|||
||1<br>x: File<br>2<br>y: File|1<br>b: File<br>2<br>c: File<br>3<br>d: File|
||Note, for F=dir, “x” and “y” are<br>directories with each having a subfile<br>“x” and “y” respectively.|Note, for F=dir, all root-level objects<br>are directories with each having a<br>subfile “b”, “c” and “d” respectively.|
|Applied<br>operations|_Move(x, temp)_,_Move(y, x)_,<br>_Move(temp, y)_|_Move(d, e)_,_Move(c, d)_,_Move(b, c)_|
|Computed<br>operations<br>(state-based)|_Move(x, y)_,_Move(y, x)_|_Move(d, e)_,_Move(c, d)_,_Move(b, c)_|
||S|S|
|Expected result|||
||2<br>x: File<br>1<br>y: File|1<br>c: File<br>2<br>d: File<br>3<br>e: File|



The remaining three tests are labeled _“Move-Occupied”_ because they involve _move_ operations to destinations that are already occupied in the replica. The applied operations are challenging for a synchronizer to apply, in particular if parameter O= _offline_ . 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

221 

|Name|Move-Occupied 1|Move-Occupied 2|Move-Occupied 3|
|---|---|---|---|
|Parameters|O|O|O|
||1<br>S<br>A: Dir|1<br>S<br>A: Dir|1<br>S<br>2|
|Base scenario|2<br>B: Dir|2<br>B: Dir|a: Dir<br>3<br>d: File|
||3<br>file: File|3<br>file: File|subfile: File|
|Applied<br>operations|_Move (A, temp)_,<br>_CreateDir(A)_,<br>_Move(temp, A/B)_|_Move(A, temp)_,<br>_Move(temp/B, A)_,<br>_Delete(temp)_|_Move(d, a/a)_,_CreateDir(d)_,<br>_Move(a, d/a)_|
|Computed<br>operations<br>(state-based)|_Move(A, A/B)_,<br>_CreateDir(A)_|_Move(A/B, A)_,_Delete(A)_|_CreateDir(d)_,_Move(a, d/a)_,<br>_Move(d, d/a/a)_|
||S<br>4<br>A: Dir|S|S<br>4<br>|
|Expected<br>result|1<br>B: Dir|2<br>A: Dir|1<br>d: Dir|
||2<br>B: Dir|3<br>file: File|a: Dir<br>3<br>2|
||3<br>file: File||subfile: File<br><br>a: File|



Refer to figure 4.1 on page 51 for an explanation of the update tree figures used throughout this chapter. 

**A.8.1.2.2 Results** The following table shows detailed results. It only contains those file synchronizers which showed unexpected behavior in at least one of the tests. 

_APPENDIX A. APPENDIX_ 

222 

|Name|Param.|Dropbox|Backupand Sync|NextCloud|
|---|---|---|---|---|
|Move-Swap, see<br>fiure A3 for|O=online,<br>F=file|✓<sup>A)</sup><br>|X<br>Name mismatch|✓<sup>B)</sup>|
|ig .<br>details|O=online,<br>F=dir|✓<sup>A)</sup><br>|X<br>Name mismatch|✓<br>|
||O=offline,<br>F=file|✓<sup>A)</sup>|X<br>Out of sync, error<br>message <sup>C)</sup>|✓<sup>D)</sup>|
||O=offline,<br>F=dir|✓<sup>A)</sup>|Name mismatch|✓|
|Move-Chain,<br>fi A4|O=online,<br>F=file|✓<sup>A)</sup>|X<br>Name mismatch|✓<sup>D)</sup>|
|see igure .<br>for details|O=online,<br>F=dir|✓<sup>A)</sup>|X<br>Name mismatch|✓<sup>D)</sup>|
||O=offline,<br>F=file|X<br>Out of sync|X/✓<br>Requires client<br>restart <sup>E)</sup>|X<br>Out of sync|
||O=offline,<br>F=dir|X<br>Out of sync|X<br>Name mismatch|X<br>Out of sync|
|Move-Occupied|O=online|✓<sup>A)</sup>|X|✓|
|1, see figure A.5|||Name mismatch||
|for details|O=offline|✓<sup>A)</sup>|X<br>Out of sync, remote<br>client detects no<br>changes <sup>F)</sup>|✓|
|Move-Occupied|O=online|✓<sup>A)</sup>|X|✓|
|2, see figure A.6|||Name mismatch||
|for details|O=offline|✓<sup>A)</sup>|X<br>Name mismatch<br>(macOS) / client<br>crash (Windows)|✓|
|Move-Occupied<br>3, see figure A.7|O=online|✓<sup>A)</sup>|X<br>Name mismatch|✓<sup>B)</sup>|
|for details|O=offline|✓<sup>A)</sup>|X/✓<br>Requires client<br>restart <sup>G)</sup>|✓<sup>D)</sup>|



✓ indicates that the final file system structure matches the expected one and that no file payloads were unnecessarily transmitted (unless a remark states otherwise). 

Remarks: 

- _Name mismatch_ : indicates that the final file system structure on the _local_ and _remote_ replica is generally equal, but the _names_ of some individual nodes on the _local_ replica do not match the corresponding ones on the _remote_ replica. 

- A) Although the final structure matches the expected one, this was _not_ achieved by the local client _moving_ the same set of objects on the _local replica_ as our test code did on the _remote replica_ . Instead, the local client _copies_ files on the _local_ replica, and leaves directories in place, only moving sub-files where necessary. 

- B) The _remote_ client correctly uploads just meta-data (containing the _move_ operations), but the _local_ client re-downloads file payloads of both files (which is inefficient). 

- C) The _remote_ client GUI displays an error message “Can’t sync 2 files: an unknown error occurred.”, where a error-details-dialog reveals an “Upload Error” for both files. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

223 

- D) The _remote_ client uploads the payload of all files (inefficient), consequently the _local_ client also re-downloads file payloads. 

- E) After starting the _remote_ client, it only transmits a sub-set of the operations (which ones depend on the host-platform and whether the chain applies a forward or backward shift) to the _server_ replica. The GUI displays an error message “Can’t sync 2 files”. The synchronization only recovers (and produces a correct result) after restarting the remote client. This includes (re-)transmission of file payloads by both the _local_ and _remote_ client. 

- F) The _remote_ client does not detect (or transmit) any changes. Consequently the _local_ replica remains unchanged. 

- G) After starting the _remote_ client, it only transmits the _Move(d, a/a)_ operation. The synchronization only recovers (and produces a correct result) after restarting the remote client. 



<!-- Start of picture text -->
L<br>2 1<br>x: File y (1): File<br><!-- End of picture text -->

Figure A.3: Incorrect result for test Move-Swap, Backup and Sync 

The result shows that the local Backup and Sync client attempts the _Move(x, y)_ operation, but y is already occupied, thus it falls back to _Move(“x”, “y (1)”)_ , followed by _Move(“y”, “x”)_ . The local client keeps this inconsistent “y (1)” name indefinitely. The _remote_ replica remains unchanged. Note: any _red_ nodes shown in the remainder of this chapter indicate incorrect nodes. 



<!-- Start of picture text -->
L L<br>L 2 3 4<br>c: File d: File e: File c: File d: File e: File<br>1 2 3<br>c (1): File d: File e: File<br>Old content Old content<br>(a) Dropbox (b) Backup and Sync (c) NextCloud<br><!-- End of picture text -->

Figure A.4: Incorrect results for test Move-Chain 

Subfigures a-c show the incorrect results on the _local_ replica of the Move-Chain test for the corresponding clients. Subfigure _a_ lacks IDs because Dropbox never moves objects but instead copies them. Consequently, files lose their (file system) ID in any case. The corresponding _remote_ replica remains unchanged. 

_APPENDIX A. APPENDIX_ 

224 



<!-- Start of picture text -->
L<br>4<br>A (1): Dir<br>1<br>B: Dir<br>2<br>B: Dir<br>3<br>file: File<br><!-- End of picture text -->

Figure A.5: Incorrect results for test Move-Occupied 1, Backup and Sync 

We note that the corresponding _remote_ replica remains unchanged. 



<!-- Start of picture text -->
L<br>2<br>A (1): Dir<br>3<br>file: File<br>(a) Name mis- (b) O=Offline (Windows) crash dialog<br>match<br><!-- End of picture text -->

Figure A.6: Incorrect results for test Move-Occupied 2, Backup and Sync 

Subfigure _a_ shows the name mismatch produced for parameter value O=online (for _local_ client running on macOS or Windows) or O=offline (given that the _local_ client runs on macOS). The corresponding _remote_ replica remains unchanged. Subfigure _b_ shows the crash dialog of the _local_ client if O=offline and _local_ client runs on Windows. In this case, both _local_ and _remote_ replica remain unchanged and are permanently out of sync. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

225 



<!-- Start of picture text -->
L<br>4<br>d (1): Dir<br>1<br>a: Dir<br>2 3<br>a: File subfile: File<br><!-- End of picture text -->

Figure A.7: Incorrect results for test Move-Occupied 3, Backup and Sync 

We note that the corresponding _remote_ replica remains unchanged. 

##### **A.8.1.3 Multi-level operations** 

Here we present the three concrete tests whose collection we labeled “Multi-level operations”, together with the detailed results. 

- **A.8.1.3.1 Test description** The following two tables provide a description of the tests: 

_APPENDIX A. APPENDIX_ 

226 



<!-- Start of picture text -->
Name Rename parent move outside to inside<br>Description One operation set renames the parent directory at path “d”, the other<br>operation set moves another directory “od” into the parent directory.<br>If parameter D=distributed, the  rename  operation is applied to the  remote<br>replica  and the  move  operation to the  local replica .<br>Parameters O, D<br>S<br>1 2<br>Base scenario d: Dir od: Dir<br>3 4<br>file: File otherfile: File<br>Operation set 1 Move(od, d/odMoved)<br>Operation set 2 Move(d, dRenamed)<br>S<br>1<br>dRenamed: Dir<br>Expected result<br>2 3<br>odMoved: Dir file: File<br>4<br>otherfile: File<br><!-- End of picture text -->

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

227 

|Name|Rename parent and sub-file|Rename par<br>sub-file|ent and update/create|
|---|---|---|---|
|Description|One operation set renames the<br>parent directory at path “d”, the other<br>operation renames a sub-file within<br>the parent directory.<br>If parameter value D=distributed, the<br>parent_rename_operation is applied to<br>the_remote_replica and the sub-file<br>_rename_operation to the_local_replica.|One operatio<br>parent direct<br>_local_replica,<br>edits the con   i<br>creates a ne<br>directory, on|n set renames the<br>ory at path “d” on the<br>the other operation set<br>tent of a sub-file and<br>w one within the parent<br>the_remote_replica.|
|Parameters|O, D|O||
|Base scenario|1<br><br>d:|S<br>Dir||
||2<br>file:<br>"a|File<br>bc"||
|Operation set 1|_Move(d, dRenamed)_|||
|Operation set 2|_Move(d/file, d/fileRenamed)_|_Edit(d/file)_,_C_|_reateFile(d/newfile)_|
||S||S|
|Expected state|1<br>dRenamed: Dir|1<br>dRena|med: Dir|
||2<br>fileRenamed: File<br>"abc"|2<br>file: File<br>"xyz"|3<br>new: File<br>"pqr"|



**A.8.1.3.2 Results** The following table shows detailed results. It only contains those file synchronizers which showed unexpected behavior in at least one of the tests. 

_APPENDIX A. APPENDIX_ 

228 

|Name|Param.|Dropbox|Backup and<br>Sync|NextCloud|Unison|
|---|---|---|---|---|---|
|Rename parent<br>move outside to<br>inside, see|O=any,<br>D=same<br>replica|✓|✓|✓|✓|
|figure A.8 for<br>details|O=any,<br>D=distributed|X<br>Duplicated<br>structure|X/✓<br>Requires<br>client restart<br>A)|X<br>Out of sync <sup>B)</sup>|X<br>Detects<br>Create-<br>ParentDelete<br>conflict <sup>C)</sup>|
|Rename parent<br>and sub-file, see<br>figure A.9 for|O=any,<br>D=same<br>replica|✓|✓|✓|✓|
|details|O=any,<br>D=distributed|X<br>Duplicated<br>structure|✓|X<br>Duplicated<br>structure|X<br>Duplicated<br>structure|
|Rename parent<br>and<br>update/create<br>sub-file, see<br>figure A.10 for<br>details|–|X<br>Duplicated<br>structure|✓|✓|X<br>Duplicated<br>structure|



Remarks: 

- A) After starting the _local_ client, it only deletes “od”. The synchronization only recovers (and produces a correct result) after restarting the local client. 

- B) The _local_ client displays an error “od: Unknown error” for a brief period. But the error message then disappears, leaving the _local_ replica in an inconsistent state. 

- C) The _local_ client detects “od” as remotely deleted and “d” as locally deleted but remotely as “changed” (due to the remotely applied _move_ operation into “d”). This is equivalent to detecting a _Create-ParentDelete_ conflict. The final result depends on how the user chooses to resolve the conflict. 



<!-- Start of picture text -->
L<br>L<br>d: Dir dRenamed: Dir<br>2 1<br>odMoved: Dir file: File od: Dir dRenamed: Dir<br>4 3<br>otherfile: File otherfile: File file: File<br>(a) Dropbox (b) NextCloud<br><!-- End of picture text -->

Figure A.8: Incorrect results for test ’Rename parent move outside to inside’ 

Subfigures a-b show the incorrect results on the _local_ replica of the test _Rename parent move outside to inside_ for the corresponding clients. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

229 



<!-- Start of picture text -->
L<br>L<br>dRenamed: Dir<br>dRenamed: Dir d: Dir<br>file: File fileRenamed: File file: File fileRenamed: File<br>(a) Dropbox, Unison (b) NextCloud<br><!-- End of picture text -->

Figure A.9: Incorrect results for test ’Rename parent and sub-file’ 

Subfigures a-b show the incorrect results on the _local_ replica of the test _Rename parent and sub-file_ for the corresponding clients. 



<!-- Start of picture text -->
L<br>d: Dir dRenamed: Dir<br>new: File file: File file: File<br>"pqr" "xyz" "abc"<br><!-- End of picture text -->

Figure A.10: Incorrect results for test ’Rename parent and update/create sub-file’ 

Shows the incorrect results on the _local_ replica of the test _Rename parent and sub-file_ for the Dropbox and Unison clients, which coincidentally produce the same result. 

#### **A.8.2 Conflict operations** 

##### **A.8.2.1 Expected result alternatives** 

|Conflict name|Expected result alternatives|
|---|---|
|Create-Create||
||• _Rename_: One of the objects is renamed.|
||• _Merge_: only one object remains. In case of<br>F=directory-directory this means that directory contents are<br>recursively merged. In case of F=file-file and R=deterministic,<br>this implies that no data needs to be transmitted over the<br>network, because the client can detect that file payloads are<br>equal based on its check sum.|



_APPENDIX A. APPENDIX_ 

230 

|Conflict name|Expected result alternatives|
|---|---|
|Edit-Edit||
||• _Overwrite_: changes of one replica are overwritten by the<br>changes of the other replica (with a prior back up to avoid<br>losing changes).<br>• _Merge_: only when R=deterministic: the file remains<br>unchanged, no data is transmitted over the network.<br>• _Duplicate_: one of the files is coped to a “conflict” file name<br>placed next to the original file (e.g. “file” is copied to “file<br><hostname>’s conflicting copy”). Only makes sense if the<br>content of both files is different. Two sub-variants are possible:<br>**–** _Sync_: the conflicting copy is synchronized to all other<br>users.<br>**–** _HostOnly_: the conflicting copy remains only on the host<br>where it was created.|
|Name clash|• _Rename_: one of the objects is renamed, e.g. by appending a<br>“conflict” string.<br>• _Merge_: only for parameter F=directory-directory. The contents<br>of both directories are merged.|
|Edit-Delete of file|• _Restore_: the_edit_operation takes precedence and the file is<br>restored on the replica where it was deleted.<br>• _Delete_: the_delete_operation takes precedence, the edited file is<br>deleted on all replicas.<br>• _Local_,_Remote_: whichever operation was done on the remote<br>(or local) replica takes precedence.|
|Move-Delete|• _Restore_: the_move_operation takes precedence and the file (or<br>directory) is restored on the replica where it was deleted (with<br>all sub-contents in case of a directory).<br>• _Delete_: the_delete_operation takes precedence, the moved file<br>or directory is deleted on all replicas.<br>• _Local_,_Remote_: whichever operation was done on the remote<br>(or local) replica takes precedence.|



_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

231 

|Conflict name|Expected result alternatives|
|---|---|
|Move-ParentDeleted|• _Restore_: the_move_operation takes precedence. The directory<br>“test” is recreated to make the_move_operation possible. Other<br>sub-objects of “test” (that were not manipulated by the replica<br>on which the_move_operation was executed) are deleted.<br>• _Delete_: the_delete_operation takes precedence. The_move_<br>operation is first undone, then “test” is deleted.<br>• _Local_,_Remote_: whichever operation was done on the remote<br>(or local) replica takes precedence.|
|Create-ParentDeleted|• _Restore_: see Move-ParentDeleted. Replace “move” with<br>“create” or “edit” respectively.<br>• _Delete_: the_delete_operation takes precedence. To avoid data<br>loss, all created objects or updated files are backed up prior to<br>executing the delete operation.<br>• _Local_,_Remote_: whichever operation was done on the remote<br>(or local) replica takes precedence.|
|Move-Move (Source)|• _Local_: the_move_operation applied to the_local replica_takes<br>precedence.<br>• _Remote_: the_move_operation applied to the_remote replica_takes<br>precedence.<br>• _Duplicate_: the file or directory is copied, s.t. both “testMoved”<br>and “testOtherMoved” exist. Note that this is not a favorable<br>outcome!|
|Move-Move (Cycle)|• _Local_: the final file system structure resembles the one of the<br>_local_replica. Ideally, the synchronization client undoes the<br>conflicting_move_operation detected on the_remote_replica and<br>then applies the_local_replica’s_move_operation.<br>• _Remote_: the final file system structure resembles the one of the<br>_remote_replica.<br>• _Duplicate_: the directories are copied, s.t. the directories “A”,<br>“A/B_moved”, “B” and “B/A_moved” exist. Note that this is not<br>a favorable outcome!|



##### **A.8.2.2 Result remarks** 

- A) “test” is renamed to “test-conflict-<date and time>-<5 random characters>” (our implementation). 

_APPENDIX A. APPENDIX_ 

232 

- B) “test” is renamed to “test (<hostname>’s conflicted copy <date>)” (Dropbox). 

- C) “test” is renamed to “test (1)” (Backup and Sync). 

- D) “test” is renamed to “test-<hostname>” (OneDrive). 

- E) “test” is renamed to “test_conflict-<date and time>” (NextCloud). 

- F) Unison generally does not automatically _resolve_ conflicts. It _detects_ the conflict and prompts the user for a choice which operation should take precedence in its graphical user interface. 

- G) NextCloud does re-download the file from the _server_ replica but then correctly merges the result. In case of F=dir-dir, the client re-downloads the directory’s sub-files. 

- H) The _local_ and _remote_ replica keep their respective file or directory. Windows client does not show any error message, while the macOS client shows a notification that there are problems with synchronizing files. 

- I) _Local_ client renames one directory to “test (1)” in the _local_ replica. After both _local_ and _remote_ client have finished synchronizing changes, the directory “test (1)” of the _local_ replica corresponds to “test” on the _remote_ replica, and “test” of the _local_ replica corresponds to “test (1)” on the _remote_ replica. Using the Google Drive’s web interface we found that both directories are named “test”, which clarifies that Google Drive does not follow the same namespace limitation rules as Windows or macOS disks, where a name may only be used once within a directory. 

- J) The _local_ client moves the locally created conflicting object “dest” to “dest_conflict-<date and time>”. However, the client implementation generally seems to _remove_ all objects that end with this conflict-pattern from the server and all other client replicas. 

- K) Detects the conflict as _Create-Create_ conflict, due to the inability to understand _move_ operations. 

- L) A restart of both clients is required to establish a stable synchronization in case the _delete_ operation is applied to the _remote_ replica. In this case, the _entire_ “test” directory is retransmitted to the server replica. 

- M) Unison detects the conflict as expected. If the user selects the _move_ / _content-update_ operation to have precedence, the _entire_ “test” directory is retransmitted to the server replica, i.e., Unison does not just create the missing “test” directory itself but also sub-objects that previously existed within “test”. 

- N) Once our test code established the concurrent changes on the local and server replica and started the local Backup And Sync client, its GUI immediately shows an error “Can’t sync 1 item”. In case the _delete_ operation was performed on the _local_ replica, the client immediately recovers from this error (it disappears) and sends the _delete_ operation to the server replica. But if instead the _delete_ operation was applied to the _remote_ replica, the error remains until the local client is restarted manually. 

- O) If the _local_ client runs on Windows, the remote _move_ operation takes precedence. If it runs on macOS, a duplicate is created. This indicates that both implementations are not using the same code base and have implemented different conflict resolution options. 

- P) See figures A.11 and A.12 for more details. 

- Q) Unison detects two _Create-ParentDelete_ conflicts and resolves them as chosen by the user, see remark _M_ . 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

233 



<!-- Start of picture text -->
L R<br>1 2<br>A: Dir B: Dir<br>S<br>2 3 1 4<br>1 2 B_moved: Dir subA: File A_moved: Dir subB: File<br>A: Dir B: Dir Move: /B Move: /A<br>3 4 4 3<br>subA: File subB: File subB: File subA: File<br>(a) Initial situation (b) Local replica changes (c) Remote replica changes<br><!-- End of picture text -->

Figure A.11: Initial situation for test ’Move-Move Cycle’ 

Subfigures a-c show the initial file system tree and the concurrent changes made on the _local_ and _remote_ replica. 

_APPENDIX A. APPENDIX_ 

234 



<!-- Start of picture text -->
L R<br>a: Dir B: Dir A: Dir B: Dir<br>B_moved: Dir A_moved: Dir B_moved: Dir A_moved: Dir<br>subB: File subA: File subB: File subA: File<br>(a) Dropbox<br>L R<br>1 2<br>A_moved: Dir B_moved: Dir<br>3 2 4 1<br>subA: File B_moved: Dir subB: File A_moved: Dir<br>4 3<br>subB: File subA: File<br>(b) Backup and Sync<br>R L<br>2 1<br>B: Dir A: Dir<br>1 4 2 3<br>A_moved: Dir subB: File B_moved: Dir subA: File<br>3 4<br>subA: File subB: File<br>(c) NextCloud<br><!-- End of picture text -->

Figure A.12: Incorrect results for test ’Move-Move Cycle’ 

Subfigures a-c show the incorrect results for the respective clients. Dropbox almost achieves consistency by _duplicating_ the directory structures, but inexplicably uses a lower-case name for the directory “A” on just one replica. Google’s Backup and Sync never achieves consistency. Its _local_ client keeps retrying an upload-operation of “B_moved”, which continuously fails. For NextCloud, neither the _local_ nor _remote_ replica are changed by the respective clients. The _local_ client’s tray icon indicates an error and a brief error message is shown, indicating that _A_ could not be synchronized due to an error. _A_ also appears in the UI’s _Not synced_ tab, with “Unknown error” being shown as rationale for not synchronizing the directory. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

235 

#### **A.8.3 Cross-platform issues** 

##### **A.8.3.1 Result remarks** 

- _Remote rename_ : here the _remote_ client automatically applies _move_ operations that rename objects. In the event of a _case-sensitivity_ or _Unicode normalization clash_ this means that _one_ of the objects keeps the name that the user intended it to have, while the other(s) are renamed by the _remote_ client. In the event of reserved characters or names, the objects are renamed to replace invalid with valid characters or names. The advantage of this approach is that both the _local_ and _remote_ replica finally contain an _equal_ set of files and directories. The disadvantage is that this approach assumes the lowest common feature set of _all_ potentially existing replicas. For instance, Dropbox and our implementation assumes that file systems are case- _in_ sensitive in general, only because there _may_ be Windows clients where this is the case. If all replicas, however, were installed on a case-sensitive macOS disk volume, these rename operations would have been inadequate. 

- _Unchanged_ / _No conflict_ : All objects are transmitted from the remote to the local replica as they are. No automatic renaming or omission of objects is applied. This is only possible if all replicas have equal capabilities, e.g. when both local and remote replica are on the same operating system. 

- SFN = Short File Name. 

- A) The configuration assistant of the macOS OneDrive client does not allow to choose a directory on a case-sensitive disk volume. 

- B) Unisons remote client skips the synchronization of both the upper- and lower-case object. The following message is shown: “Two or more files on a case-sensitive system have names identical except for case. They cannot be synchronized to a case-insensitive file system. No updates to propagate”. Given that _server_ replica is on a case-sensitive Linux disk volume, this behavior is sub-optimal. 

- C) The object using upper-case on macOS (“FILE”, “DIR”) is named “FILE (1)”, “DIR (1)” on the _local_ replica on Windows. 

- D) The _remote_ client uploads both objects to the server replica. The _local_ client downloads the lower-case object and then fails to download the upper-case object. The client’s tray icon briefly indicates an error, and the user can inspect the error details in the UI (“Not synced” tab). 

- E) The object using lower-case on macOS (“file”) is named “file (1)” on the _local replica_ on Windows. 

- F) The _local_ client on Windows does not detect the conflict. The client’s tray icon and UI show the same behavior as in remark _D_ . 

- G) Unison generally does not automatically resolve conflicts. The _local_ client detects the conflict and prompts the user for a choice which operation should take precedence in its graphical user interface. 

- H) Only the file encoded using the NFC-normalization is sent to the _server_ replica and downloaded to the _local_ replica. No notification is shown to the user. 

- I) Like in remark _B_ , the remote client skips the synchronization of both “ä” files. It also shows the exact same error message, which is obviously incorrect, because the files do not differ by _case_ but by _Unicode normalization_ . 

- J) On the _remote_ replica, both files keep their name. On the _local_ replica the client creates files “ä” and “ä (1)”, where the latter corresponds to the NFC-normalized file on the _remote_ replica. 

- K) The file is automatically renamed by the _remote_ client to NFC normalization on the _remote_ replica. Consequently, it is also created using NFC normalization on the _local_ replica. 

_APPENDIX A. APPENDIX_ 

236 



Figure A.13: OneDrive macOS client cross-platform issues information 

Information window shown by the OneDrive client on macOS while running the test _Reserved characters and names_ . Assuming that these hints are given to achieve compatibility with Windows, several hints are incorrect. (1) Windows does allow files to _begin_ with spaces (but they may not _end_ with them). (2) Windows files may _begin_ with “..” as long as there are valid follow-up characters. For instance, “..test” is a valid name. However, “..” is not. 

- L) The file “ä” keeps the NFD normalization on the _remote_ replica but is converted to NFC normalization on the _local_ replica. 

- M) This leads to problems in practice. A Windows user can concurrently create a file “ä” which leads to having two files whose name _look exactly equal_ in the Windows file manager. Users of an early BSync version (which did not handle this case yet) were confronted with this issue. 

- N) The _remote_ client renames the file “b “ to “b” on the _remote_ replica, but keeps the names of all other files. After the rename operation, the client synchronizes all files to the _server_ replica. If the _local_ client is on macOS, all files are downloaded to the _local_ replica. 

- O) The _remote_ client synchronizes files with reserved _names_ (“LPT1”, “LPT1.foo.bar”) to the _server_ replica, but _skips_ synchronization for the three files we created whose names contain reserved _characters_ (or end with spaces and “.”). The _remote_ client notifies the user that these files were not synchronized and how she can rename them herself to make them synchronizable. Interestingly, some of that advice is technically incorrect. See figure A.13 for more details. If the _local_ replica is on _Windows_ , files with reserved _name_ (that do exist on the _server_ replica) are not downloaded, without any notification shown to the user. If the _local_ replica is on _macOS_ , these files are downloaded. 

- P) None of the files that have reserved characters or names are downloaded to the _local_ replica by the Windows client. The user is not notified. Dropbox offers a _Check bad files_ tool<sup>10</sup> , a web site that allows Windows users to inspect the list of files that exist on the server but cannot be synchronized to their Windows machine. 

> 10See `https://www.dropbox.com/bad_files_check` , retrieved July 21, 2019. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

237 

- Q) The _remote_ client synchronizes _all_ files to the _server_ replica. The _local_ client on Windows automatically renames files that contain reserved _characters_ by replacing special characters with “_”, or renaming files that end with space or “.” s.t. they end with “_” on the _local_ replica. For reserved _names_ , the Windows client uses the _UNC path_ technique<sup>11</sup> to create those files anyway! This is problematic, because such files and directories cannot be opened, moved or deleted by the user using Windows explorer. Special tools or expert command-line knowledge is required to delete these objects. Interestingly, the Backup and Sync Windows client does _not_ seem to use _UNC paths_ when _deleting_ objects. Consequently, the user cannot remove such files by removing them on Google Drive (or any macOS client), because the file will still remain on the Windows machine. 

- R) The _remote_ client transmits _all_ files to the _server_ replica. The _local_ client in turn does not download any of the files that contain reserved characters or names. 

- S) The synchronization fails silently, causing both the _local_ and _remote_ replica to remain unchanged. 

- T) After the synchronization finished, both files exist on both replicas, but with different names. Each replica keeps its originally created file at the original path. On the _local_ replica, the remotely created file is found with name “a....a (1)” (length of 255 characters), i.e. the client applies name truncation. On the _remote_ replica, the locally created file is found with name “a...a” with an inexplicable length of _224_ characters. 

- U) If our test performs the operations on the _remote_ replica while the _remote_ client is _offline_ , then the _remote_ client hangs after starting it, showing the status as “synchronizing” for an indefinite time period. If our test instead performs the operations while the remote client is _online_ , the operations are successfully synchronized with the _server_ replica. In this case the _local_ client hangs indefinitely. 

- V) The _remote_ client automatically renames the file on the _remote_ replica, replacing the “~” character with “-”. 

- W) The _local_ client does not recognize the issue that the short file name cannot be created during _update detection_ , but only recognizes this during _propagation_ . It displays an incorrect error message “Destination updated during synchronization: The file aaaaaa~1 has been created”. The _local_ client most likely assumes that the file with the short name really exists and that it was created after the update detection phase had finished. 

> 11While Windows APIs forbid operations such as _CreateFile(“C:\LPT1”)_ , it does permit _CreateFile(“\\?\C:\LPT1”)_ . The UNC name prefix [Mic18b] disables any parsing of the path, making such operations possible. 

_APPENDIX A. APPENDIX_ 

238 



<!-- Start of picture text -->
L R L R<br>2 1 2 1 2<br>aaaaaaaaaa: File aaaaaa~1: File aaaaaaaaaa: File aaaaaa~1: File aaaaaaaaaa: File<br>(a) Initial situation (b) Dropbox / NextCloud / Unison<br>S<br>L R<br>1 2 1 2 1 2<br>aaaaaa~1 (1): File aaaaaaaaaa: File aaaaaa~1: File aaaaaaaaaa: File aaaaaa-1: File aaaaaaaaaa: File<br>(c) Backup and Sync (d) OneDrive / Our implementation<br><!-- End of picture text -->

Figure A.14: Results for Windows 8.3 file names - distributed creation 

Subfigure a shows the initial situation created by the test. Subfigures b-d show the final file system structure once each implementation has completed the synchronization procedure. Note the replacement of “~” with “-” in subfigure d. Regarding subfigure b, we should point out that Windows’ file system API only forbids the operation sequence _CreateFile(“aaaaaaaaaa”)_ + _CreateFile(“aaaaaa~1”)_ , but allows _CreateFile(“aaaaaa~1”)_ + _CreateFile(“aaaaaaaaaa”)_ . Consequently, the _remote_ replica can have _both_ files. 

_A.8. FILE SYNCHRONIZER COMPARATIVE TEST DETAILS_ 

239 



<!-- Start of picture text -->
S R S<br>1 2 1 2<br>aaaaaaaaaa: File b: File aaaaaaaaaa: File aaaaaa~1: File 1 2<br>aaaaaaaaaa: File aaaaaa-1: File<br>Move /b<br>(a) Initial situation (b) Our implementation<br>L R<br>1 1 2<br>aaaaaaaaaa: File aaaaaaaaaa: File aaaaaa~1: File<br>(c) Dropbox<br>L R<br>1 2 1 2<br>aaaaaaaaaa: File aaaaaa~1 (1): File aaaaaaaaaa: File aaaaaa~1: File<br>(d) Backup and Sync<br>S L R<br>2 1 2 1 2<br>aaaaaaaaaa: File aaaaaaaaaa: File b: File aaaaaaaaaa: File aaaaaa~1: File<br>(e) OneDrive (f) NextCloud / Unison<br><!-- End of picture text -->

Figure A.15: Results for Windows 8.3 file names - macOS clash 

Subfigure a shows the initial situation created by the test, as well as the _move_ operation applied on the remote replica. Subfigures b-f show the final file system structure once each implementation has completed the synchronization procedure. Note that in subfigure e the original file “aaaaaaaaaa” with ID=1 has been completely removed from all replicas unexpectedly. We observed that the _local_ client first deleted the file “aaaaaaaaaa”, followed by executing a _Move(b, aaaaaaaaaa)_ operation. The _local_ client showed a confusing (and obviously incorrect) error message: “You now have two copies of a file; We couldn’t merge the changes in aaaaaa~1-<local-host-hame> so we created another copy of it.”. Because the _delete_ and _move_ operations are synchronized to all other replicas, this leads to the complete loss of the file with the long name (ID=1). 

241 

# **Publications of the author** 

### **Publications related to this thesis** 

Marius Shekow. “Syncpal: A simple and iterative reconciliation algorithm for file synchronizers”. In: _Distributed Applications and Interoperable Systems - IFIP International Federation for Information Processing, DAIS 2019, Held as Part of the 14th International Federated Conference on Distributed Computing Techniques, DisCoTec 2019, Lingby, Denmark, June 18-21, 2019, Proceedings_ . Ed. by José Pereira and Laura Ricci. 2019 

Marius Shekow and Wolfgang Prinz. “A capability analysis of groupware, cloud and desktop file systems for file synchronization”. In: _Proceedings of 17th European Conference on Computer-Supported Cooperative Work-Exploratory Papers. The International Venue on Practicecentred Computing an the Design of Cooperation Technologies. European Society for Socially Embedded Technologies (EUSSET). 2019_ . DOI: 10.18420/ecscw2019_ep06. URL: `http://dx.doi.org/10.18420/ecscw2019_ep06` . 

### **Further publications** 

Marius Shekow and Leif Oppermann. “On maximum geometric finger-tip recognition distance using depth sensors”. In: _WSCG 2014: communication papers proceedings: 22nd International Conference in Central Europeon Computer Graphics, Visualization and Computer Vision. In co-operation with EUROGRAPHICS Association_ . WSCG. Ed. by Václav Skala. Václav Skala - UNION Agency, 2014, pp. 83–89. ISBN: 978-80-86943-71-8. URL: `https://dspace5.zcu.cz/handle/11025/26381` . 

Leif Oppermann, Marius Shekow, and Deniz Bicer. “Mobile cross-media visualisations made from building information modelling data”. In: _Proceedings of the 18th International Conference on HumanComputer Interaction with Mobile Devices and Services Adjunct - MobileHCI ’16. the 18th International Conference (Florence, Italy)_ . Ed. by Fabio Paternò and Kaisa Väänänen. New York, New York, USA: ACM Press, 2016, pp. 823–830. ISBN: 9781450344135. DOI: 10.1145/2957265.2961852. 

Leif Oppermann, Lisa Blum, and Marius Shekow. “Playing on AREEF”. In: _Proceedings of the 18th International Conference on Human-Computer Interaction with Mobile Devices and Services - MobileHCI ’16. the 18th International Conference (Florence, Italy)_ . Ed. by Fabio Paternò and Kaisa Väänänen. New York, New York, USA: ACM Press, 2016, pp. 330–340. ISBN: 9781450344081. DOI: 10.1145/2935334.2935368. 

243 

# **Curriculum Vitae** 

||**Personal information**|
|---|---|
|Name|Marius Alwin Shekow|
|Date & place of birth|September 6, 1985, Tettnang, Germany|



||**Education**|
|---|---|
|10/2010 - 03/2013|RWTH Aachen University<br>M.Sc. Media Informatics, B-IT|
|10/2006 - 09/2010|Karlsruhe University of Applied Sciences|
||B.Sc. Computer Science|
|2002 - 2005|Claude Dornier Schule Friedrichshafen<br>Secondary school, Abitur (German university entrance diploma)|



||**Professional experience**|
|---|---|
|02/2013 - now|Fraunhofer FIT, MARS group, Sankt Augustin, Germany<br>Research associate, Software engineer with focus on Distributed<br>Systems (File Synchronization) and Virtual/Augmented Reality|
|02/2012 - 02/2013|Fraunhofer FIT, MARS group, Sankt Augustin, Germany<br>Working student, Software engineer and M.Sc. thesis in Computer<br>Vision (hand gesture recognition)|
|09/2011 - 01/2012|Fraunhofer FIT, UCC group, Sankt Augustin, Germany<br>Working student, Software engineer with focus on Distributed<br>Systems and Android|
|03/2010 - 08/2010|CERN, Meyrin, Switzerland<br>Technical student program, B.Sc. thesis on automation software for<br>linear particle accelerator|
|03/2008 - 08/2008|Cluetec GmbH, Karlsruhe, Germany<br>Working student, Enterprise Java software, J2ME|
|07/2007 - 02/2008|Webzooms AG (now Citrix), Karlsruhe, Germany<br>Working student, web development|




