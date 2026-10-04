package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.github.ruediste.p2psync.clock.ClockRelation;
import com.github.ruediste.p2psync.clock.VectorClock;

public class FileSystemDataStructureTest {

    static class FileSystem {
        int nodeNr;
        FsDirectory root;

        FileSystem(int nodeNr) {
            this.nodeNr = nodeNr;
            root = FsDirectory.empty(this);
        }

        public void merge(FileSystem other) {
            root.merge(other.root, new FsPath());
        }
    }

    static abstract class FsElementBase<TSelf extends FsElementBase<TSelf>> {
        public int sourceNodeNr;

        public abstract TSelf clone(FileSystem fs);

        protected FsElementBase(int sourceNodeNr) {
            this.sourceNodeNr = sourceNodeNr;
        }
    }

    static class DirEntry {
        // invariant: either a directory or a file have to be present
        Optional<FsDirectory> directory = Optional.empty();
        List<FsFile> files = new ArrayList<>();
        FsClock dirClock;

        public static DirEntry empty(FsDirectory dir) {
            var result = new DirEntry();
            result.dirClock = dir.clock.clone();
            return result;
        }

        public DirEntry clone(FileSystem fs) {
            var result = new DirEntry();
            result.files.addAll(files.stream().map(x -> x.clone(fs)).toList());
            result.directory = directory.map(d -> d.clone(fs));
            result.dirClock = dirClock.clone();
            return result;
        }

        public DirEntry resetTag(FileSystem fs) {
            dirClock.resetTag(fs.nodeNr);
            directory.ifPresent(d -> d.resetTag());
            files.forEach(f -> f.resetTag());
            return this;
        }

        @Override
        public final String toString() {
            return "DirEntry{" +
                    "directory=" + (directory.isPresent() ? "<dir>" : "<empty>") +
                    ", files=" + files +
                    ", dirClock=" + dirClock +
                    '}';
        }
    }

    static record FsPathPart(String name, Optional<Integer> nodeNr) {
        public static FsPathPart parse(String name) {
            var idx = name.indexOf(':');
            if (idx >= 0) {
                return new FsPathPart(name.substring(0, idx), Optional.of(Integer.parseInt(name.substring(idx + 1))));
            } else
                return new FsPathPart(name, Optional.empty());
        }

        @Override
        public final String toString() {
            return nodeNr.map(n -> name + ":" + n).orElse(name);
        }
    }

    static class FsPath {
        public List<FsPathPart> parts = new ArrayList<>();

        public FsPathPart last() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("Path is empty");
            }
            return parts.get(parts.size() - 1);
        }

        public FsPathPart removeLast() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("Path is empty");
            }
            return parts.remove(parts.size() - 1);
        }

        public static FsPath parse(String path) {
            var fsPath = new FsPath();
            for (var part : path.split("/")) {
                var fsPathPart = FsPathPart.parse(part);
                fsPath.parts.add(fsPathPart);
            }
            return fsPath;
        }

        public FsPath clone() {
            var result = new FsPath();
            result.parts.addAll(parts);
            return result;
        }

        public void add(String name) {
            parts.add(new FsPathPart(name, Optional.empty()));
        }

        @Override
        public final String toString() {
            return "/" + parts.stream().map(FsPathPart::toString).collect(Collectors.joining("/"));
        }

        public FsPath resolve(String name) {
            var result = this.clone();
            result.add(name);
            return result;
        }
    }

    static <R> R errorMessage(String message, Supplier<R> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            throw new RuntimeException(message, t);
        }
    }

    static class FsDirectory extends FsElementBase<FsDirectory> {

        private FsClock clock;
        private FileSystem fs;
        Map<String, DirEntry> entries = new HashMap<>();

        public FsDirectory(int sourceNodeNr, FileSystem fs) {
            super(sourceNodeNr);
            this.fs = fs;
        }

        public static FsDirectory empty(FileSystem fs) {
            var result = new FsDirectory(fs.nodeNr, fs);
            result.clock = FsClock.create(fs.nodeNr);
            return result;
        }

        // Merge the other directory into this directory
        public void merge(FsDirectory other, FsPath path) {
            try {
                // remove own entries, which have been deleted in other
                {
                    var entriesToRemove = entries.entrySet().stream()
                            .filter(e -> !other.entries.containsKey(e.getKey())
                                    && errorMessage(
                                            "Compare dirClock of " + e.getKey() + " with clock of other directory",
                                            () -> e.getValue().dirClock.isBeforeOrEqual(other.clock)))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    for (var key : entriesToRemove) {
                        entries.remove(key);
                    }
                }

                // add data from the other directory into this one
                for (var otherEntry : other.entries.entrySet()) {
                    try {

                        var ownEntry = entries.get(otherEntry.getKey());
                        if (ownEntry == null) {
                            entries.put(otherEntry.getKey(), otherEntry.getValue().clone(fs).resetTag(fs));
                            continue;
                        }

                        // remove files that are before any FileEntry in otherVersions
                        {
                            ownEntry.files.removeIf(f -> otherEntry.getValue().files.stream()
                                    .anyMatch(of -> f.isBeforeOrEqual(of)));
                        }

                        // only add the other files if it is not obsolete
                        for (var otherFile : otherEntry.getValue().files) {
                            if (!ownEntry.files.stream()
                                    .anyMatch(f -> otherFile.isBeforeOrEqual(f))) {
                                ownEntry.files.add(otherFile.clone(fs).resetTag());
                            }
                        }

                        // merge the subdirectories recursively
                        if (otherEntry.getValue().directory.isPresent()) {
                            var otherDir = otherEntry.getValue().directory.get();
                            if (ownEntry.directory.isPresent()) {
                                var subPath = path.resolve(otherEntry.getKey());
                                ownEntry.directory.get().merge(otherDir, subPath);
                            } else {
                                // We have no local entry. If if the entry is before the own clock,
                                // we must have deleted it locally
                                if (!otherEntry.getValue().dirClock.isBefore(clock))
                                    ownEntry.directory = Optional.of(otherDir.clone(fs).resetTag());
                            }
                        }
                    } catch (Throwable e) {
                        throw new RuntimeException("Error merging entry: " + otherEntry.getKey(), e);
                    }
                }

                clock.merge(other.clock);
            } catch (Throwable e) {
                throw new RuntimeException("Error merging directories at path: " + path + " thisDirectory:\n" + this
                        + "\notherDirectory:\n" + other, e);
            }
        }

        private FsDirectory resetTag() {
            this.clock.resetTag(sourceNodeNr);
            entries.values().forEach(e -> e.resetTag(fs));
            return this;
        }

        public FsDirectory clone(FileSystem fs) {
            var result = new FsDirectory(sourceNodeNr, fs);
            result.clock = clock.clone();
            for (var e : entries.entrySet()) {
                result.entries.put(e.getKey(), e.getValue().clone(fs));
            }
            return result;
        }

        static record NameAndNodeNr(String name, int nodeNr) {
        }

        Optional<DirEntry> loadEntry(String path) {
            return loadEntry(FsPath.parse(path));
        }

        Optional<DirEntry> loadEntry(FsPath path) {
            FsDirectory current = this;
            var currentPath = new StringBuilder();
            // iterate through directories
            for (var i = 0; i < path.parts.size() - 1; i++) {
                var part = path.parts.get(i);
                if (!currentPath.isEmpty())
                    currentPath.append("/");
                currentPath.append(part);

                var childEntry = current.entries.get(part.name());
                if (childEntry == null) {
                    throw new IllegalArgumentException("no entry found for " + currentPath);
                }
                current = childEntry.directory
                        .orElseThrow(() -> new IllegalArgumentException("no directory found for " + currentPath));
            }

            // load last entry
            {
                var nameAndNodeNr = path.parts.get(path.parts.size() - 1);
                return Optional.ofNullable(current.entries.get(nameAndNodeNr.name()));
            }
        }

        FsDirectory loadDir(String path) {
            return loadDir(FsPath.parse(path));
        }

        FsDirectory loadDir(FsPath path) {
            return loadEntry(path).flatMap(e -> e.directory)
                    .orElseThrow(() -> new IllegalArgumentException("no directory found for " + path));
        }

        public boolean exists(String path) {
            return loadEntry(path).isPresent();
        }

        public FsFile loadFile(String path) {
            var pathParsed = FsPath.parse(path);
            var entry = loadEntry(pathParsed)
                    .orElseThrow(() -> new IllegalArgumentException("no entry found for " + path));
            var files = entry.files.stream()
                    .filter(x -> pathParsed.last().nodeNr.map(nr -> x.sourceNodeNr == nr).orElse(true)).toList();
            if (files.size() == 0) {
                throw new IllegalArgumentException("no file found for " + path);
            }
            if (files.size() > 1) {
                throw new IllegalArgumentException("multiple files found for " + path);
            }
            return files.get(0);
        }

        FsFile addFile(String name, String content) {
            var file = FsFile.createNewFile(fs.nodeNr, fs);
            file.content = content;
            createNewEntry(name).files.add(file);
            return file;
        }

        private DirEntry createNewEntry(String name) {
            var entry = entries.get(name);
            if (entry != null) {
                throw new IllegalArgumentException("entry already exists for " + name);
            }
            entry = DirEntry.empty(this);
            entries.put(name, entry);
            return entry;
        }

        FsDirectory addSubDirectory(String name) {
            var dir = FsDirectory.empty(fs);
            createNewEntry(name).directory = Optional.of(dir);
            return dir;
        }

        void move(String oldName, String newPath) {
            var newPathParsed = FsPath.parse(newPath);
            var newName = newPathParsed.removeLast();

            var newDir = fs.root.loadDir(newPathParsed);
            if (newDir.entries.containsKey(newName.name)) {
                throw new IllegalArgumentException("entry already exists for " + newName.name);
            }
            var entry = entries.get(oldName);
            if (entry == null) {
                throw new IllegalArgumentException("no entry found for " + oldName);
            }
            entries.remove(oldName);
            newDir.entries.put(newName.name, entry);
        }

        String chooseNewName(String baseName) {
            var candidate = baseName;
            var index = 0;
            while (entries.containsKey(candidate)) {
                candidate = baseName + index;
                index++;
            }
            return candidate;
        }

        public void remove(String entryName) {
            this.entries.remove(entryName);
            clock.increment(fs.nodeNr, "removed " + entryName);
        }

        void traverseDirs(BiConsumer<FsPath, FsDirectory> consumer) {
            traverseDirs(consumer, new FsPath());
        }

        private void traverseDirs(BiConsumer<FsPath, FsDirectory> consumer, FsPath path) {
            consumer.accept(path, this);
            for (var entry : entries.entrySet()) {
                if (entry.getValue().directory.isEmpty()) {
                    continue;
                }
                var entryPath = path.clone();
                entryPath.add(entry.getKey());
                entry.getValue().directory.get().traverseDirs(consumer, entryPath);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FsDirectory{");
            sb.append("clock=").append(clock).append(", ");
            sb.append("entries=").append(entries.entrySet().stream()
                    .map(e -> "\n  " + e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
            sb.append("}\n");
            return sb.toString();
        }
    }

    static class EventSet {
        private static AtomicInteger eventCounter = new AtomicInteger();

        private class Event {
            public final String name;

            public Event(String name) {
                this.name = name + "(" + eventCounter.getAndIncrement() + ")";
            }

            @Override
            public String toString() {
                return name;
            }
        }

        private Set<Event> events = new LinkedHashSet<>();

        public void add(String name) {
            var event = new Event(name);
            events.add(event);
        }

        public EventSet clone() {
            var result = new EventSet();
            result.events.addAll(events);
            return result;
        }

        public boolean isSubsetOf(EventSet other) {
            return events.stream().allMatch(x -> other.events.contains(x)) && events.size() < other.events.size();
        }

        public boolean isSubsetOfOrEqual(EventSet other) {
            return events.equals(other.events) || isSubsetOf(other);
        }

        public void assertIsSubsetOf(EventSet other) {
            var missingEvents = events.stream().filter(x -> !other.events.contains(x)).toList();
            if (!missingEvents.isEmpty()) {
                throw new AssertionError("Events " + missingEvents + " are not in the other set");
            }
        }

        @Override
        public String toString() {
            return "EventSet{" +
                    "events="
                    + events.stream()
                            .map(e -> e.name)
                            .collect(Collectors.joining(", "))
                    +
                    '}';
        }

        public ClockRelation compare(EventSet other) {
            if (this.events.equals(other.events))
                return ClockRelation.EQUAL;
            if (this.isSubsetOf(other))
                return ClockRelation.BEFORE;
            if (other.isSubsetOf(this))
                return ClockRelation.AFTER;
            return ClockRelation.CONCURRENT;
        }
    }

    static class FsClock {
        private VectorClock vectorClock;
        private EventSet eventSet;

        private FsClock() {
        }

        public void resetTag(int nodeNr) {
            vectorClock.resetTag(nodeNr);
        }

        public void merge(FsClock clock) {
            this.vectorClock.merge(clock.vectorClock);
            this.eventSet.events.addAll(clock.eventSet.events);
        }

        public static FsClock create(int nodeNr) {
            var result = new FsClock();
            result.eventSet = new EventSet();
            result.vectorClock = new VectorClock();
            result.increment(nodeNr, "created");
            return result;
        }

        public FsClock clone() {
            var result = new FsClock();
            result.vectorClock = vectorClock.clone();
            result.eventSet = eventSet.clone();
            return result;
        }

        public ClockRelation compare(FsClock other) {
            var result = this.vectorClock.compare(other.vectorClock);
            if (result != eventSet.compare(other.eventSet))
                throw new AssertionError(
                        "Inconsistent state: vectorClock.compare and eventSet.compare disagree\nownClock= "
                                + this.vectorClock
                                + "\notherClock= " + other.vectorClock + "\nownEventSet= " + this.eventSet
                                + "\notherEventSet= "
                                + other.eventSet + "\nvectorClockRelation=" + result + " eventSetRelation="
                                + eventSet.compare(other.eventSet));
            return result;
        }

        public boolean isBefore(FsClock other) {
            return compare(other) == ClockRelation.BEFORE;
        }

        public boolean isBeforeOrEqual(FsClock other) {
            var relation = compare(other);
            return relation == ClockRelation.BEFORE || relation == ClockRelation.EQUAL;
        }

        public boolean isConcurrent(FsClock other) {
            return !this.isBefore(other) && !other.isBefore(this);
        }

        public void increment(int nodeNr, String name) {
            vectorClock.increment(nodeNr);
            eventSet.add(name + "#" + nodeNr);
        }

        @Override
        public String toString() {
            return "FsClock{" +
                    ", vectorClock=" + vectorClock +
                    ", eventSet=" + eventSet +
                    '}';
        }
    }

    static class FsFile extends FsElementBase<FsFile> {
        private FileSystem fs;
        private String content = "";
        public FsClock clock;

        public boolean isBefore(FsFile other) {
            return this.clock.isBefore(other.clock);
        }

        public FsFile resetTag() {
            this.clock.resetTag(fs.nodeNr);
            return this;
        }

        public boolean isBeforeOrEqual(FsFile other) {
            return this.clock.isBeforeOrEqual(other.clock);
        }

        private FsFile(int sourceNodeNr, FileSystem fs) {
            super(sourceNodeNr);
            this.fs = fs;
        }

        public static FsFile createNewFile(int sourceNodeNr, FileSystem fs) {
            var result = new FsFile(sourceNodeNr, fs);
            result.clock = FsClock.create(fs.nodeNr);
            return result;
        }

        @Override
        public FsFile clone(FileSystem fs) {
            var result = new FsFile(sourceNodeNr, fs);
            result.content = content;
            result.clock = clock.clone();
            return result;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
            this.clock.increment(fs.nodeNr, "setContent " + value);
        }

        @Override
        public String toString() {
            return "FsFile{" +
                    "content='" + content + '\'' +
                    ", clock=" + clock +
                    '}';
        }
    }

    @Test
    public void generatedNamesAreUniqueAndDeterministic() {
        var fs = new FileSystem(1);
        var dir = fs.root;
        dir.addFile("file", "");
        dir.addFile("file0", "");
        dir.addFile("file1", "");

        assertEquals("file2", dir.chooseNewName("file"));
    }

    @Test
    public void createDirAndFile() {
        var fs = new FileSystem(1);
        fs.root.addFile("a", "foo");
        var b = fs.root.addSubDirectory("b");
        b.addFile("c", "bar");
        assertEquals("bar", fs.root.loadFile("b/c").getContent());
    }

    @Test
    public void mergeSimple() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        assertEquals("foo", fs2.root.loadFile("a").getContent());
    }

    @Test
    public void mergeModifyMerge() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        fs1.root.loadFile("a").setContent("foo2");
        fs2.merge(fs1);

        assertEquals("foo2", fs2.root.loadFile("a").getContent());
    }

    @Test
    public void mergeRecreateMerge() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        fs1.root.remove("a");
        fs1.root.addFile("a", "bar");

        fs2.merge(fs1);

        // `a` has been created twice. Thus there should be a conflict with two
        // versions.
        assertEquals(2, fs2.root.loadEntry("a").get().files.size());
    }

    @Test
    public void moveFile() {
        var fs = new FileSystem(1);
        fs.root.addFile("a", "foo");
        fs.root.addSubDirectory("b");
        fs.root.move("a", "b/c");
        assertEquals("foo", fs.root.loadFile("b/c").getContent());
    }

    @Test
    public void moveFileMerge() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        fs1.root.addSubDirectory("b");
        fs1.root.move("a", "b/c");
        fs2.merge(fs1);
        assertEquals("foo", fs2.root.loadFile("b/c").getContent());
        assertTrue(fs2.root.loadEntry("a").isEmpty());
    }

    @Test
    public void concurrentFileModification() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        fs1.root.loadFile("a").setContent("foo2");
        fs2.root.loadFile("a").setContent("bar");

        fs2.merge(fs1);
        {
            var versions = fs2.root.loadEntry("a").get().files;
            assertEquals(2, versions.size());
            assertEquals(Set.of("foo2", "bar"),
                    versions.stream().map(x -> ((FsFile) x).getContent()).collect(Collectors.toSet()));
        }
    }

    static class Var<T> {
        public T value;

        private Var(T value) {
            this.value = value;
        }

        public static <T> Var<T> of(T value) {
            return new Var<>(value);
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
    }

    enum ActionCategory {
        ENTRY_CHANGE(5),
        FS_MERGE(1.0);

        private ActionCategory(double weight) {
            this.weight = weight;
        }

        public final double weight;
    }

    static class Action {
        public String name;
        public double weight;
        public ActionCategory category;
        public Runnable action;

        public Action(String name, double weight, ActionCategory category, Runnable action) {
            this.name = name;
            this.weight = weight;
            this.category = category;
            this.action = action;
        }
    }

    @Test
    public void modelBasedTest() {
        int fileSystemsCount = 3;
        int testCount = 20;
        int iterationCount = 200;

        for (int testNr = 0; testNr < testCount; testNr++) {
            // build file systems
            var fileSystems = new ArrayList<FileSystem>();
            var fs0 = new FileSystem(0);
            for (int i = 0; i < fileSystemsCount; i++) {
                var fs = new FileSystem(i);
                fs.root = fs0.root.clone(fs);
                fileSystems.add(fs);
            }

            // iterate over a number of iterations, randomly modifying the file systems and
            // merging them
            var random = new java.util.Random(testNr);
            var history = new StringBuilder();
            try {
                for (int iterationNr = 0; iterationNr < iterationCount; iterationNr++) {
                    // collect possible actions
                    var actions = new ArrayList<Action>();

                    for (int i = 0; i < fileSystems.size(); i++) {
                        var fs = fileSystems.get(i);
                        var iFinal = i;

                        // merge with another random file system
                        {
                            var otherFs = fileSystems.get(random.nextInt(fileSystems.size()));
                            if (otherFs != fs) {
                                assertFalse(iFinal == otherFs.nodeNr);
                                actions.add(
                                        new Action("merge fs" + iFinal + " from fs" + otherFs.nodeNr, 1.0,
                                                ActionCategory.FS_MERGE,
                                                () -> fs.merge(otherFs)));
                            }
                        }

                        // iterate over all directories and files and add actions for modifying them
                        {
                            var growFsActions = new ArrayList<Action>();
                            var shrinkFsActions = new ArrayList<Action>();
                            var currentFsSize = Var.of(0);

                            fs.root.traverseDirs((path, dir) -> {
                                currentFsSize.value += dir.entries.size();

                                // add file to the directory
                                var newFileName = dir.chooseNewName("file");
                                growFsActions.add(new Action(
                                        "add file " + newFileName + " to dir " + path + " in fs" + iFinal, 1.0,
                                        ActionCategory.ENTRY_CHANGE, () -> {
                                            dir.addFile(newFileName, "content");
                                        }));
                                // add subdirectory to the directory
                                var newDirName = dir.chooseNewName("dir");
                                growFsActions.add(new Action(
                                        "add subdir " + newDirName + " to dir " + path + " in fs" + iFinal, 1.0,
                                        ActionCategory.ENTRY_CHANGE, () -> {
                                            dir.addSubDirectory(newDirName);
                                        }));
                                // remove entry from the directory
                                dir.entries.forEach((name, entry) -> {
                                    shrinkFsActions.add(new Action(
                                            "remove entry " + name + " from dir " + path + " in fs" + iFinal, 1.0,
                                            ActionCategory.ENTRY_CHANGE, () -> {
                                                dir.remove(name);
                                            }));
                                });
                            });

                            int targetFsSize = 20;

                            // adjust the weights
                            {
                                double growWeight = (currentFsSize.value < targetFsSize ? 1.0 : 0.5)
                                        / (growFsActions.size() + 1);
                                double shrinkWeight = (currentFsSize.value > targetFsSize ? 1.0 : 0.5)
                                        / (shrinkFsActions.size() + 1);

                                // weight by fs size as well, so that larger file systems have proportionally
                                // smaller action weights
                                growFsActions.forEach(a -> a.weight = growWeight / currentFsSize.value);
                                shrinkFsActions.forEach(a -> a.weight = shrinkWeight / currentFsSize.value);
                                actions.addAll(growFsActions);
                                actions.addAll(shrinkFsActions);
                            }
                        }

                    }

                    // choose random category (weighted)
                    var currentCategoryWeight = 0.0;
                    var categoryRandom = random.nextDouble()
                            * Stream.of(ActionCategory.values()).mapToDouble(x -> x.weight).sum();
                    for (var category : ActionCategory.values()) {
                        currentCategoryWeight += category.weight;
                        if (currentCategoryWeight >= categoryRandom) {
                            // choose random action (weighted) and execute it
                            var totalWeight = actions.stream().filter(x -> x.category == category)
                                    .mapToDouble(x -> x.weight).sum();
                            var r = random.nextDouble() * totalWeight;
                            double currentWeight = 0;
                            for (var a : actions.stream().filter(x -> x.category == category).toList()) {
                                currentWeight += a.weight;
                                if (currentWeight >= r) {
                                    history.append("Executing action: " + a.name + "\n");
                                    a.action.run();
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    // verify that all file systems are valid
                    for (int i = 0; i < fileSystems.size(); i++) {
                        var fs = fileSystems.get(i);
                        try {
                            verifyFileSystem(fs);
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "File system " + i + " is invalid after iteration " + iterationNr + " History:\n"
                                            + history,
                                    e);
                        }
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(
                        "Unexpected exception during test " + testNr + " History:\n" + history, e);
            }
        }
    }

    private void verifyFileSystem(FileSystem fs) {
        verifyDirectory(fs.root, "");
    }

    private void verifyDirectory(FsDirectory dir, String path) {
        // verify all entries of this directory, recursing into subdirectories
        for (var entry : dir.entries.entrySet()) {
            entry.getValue().directory.ifPresent(
                    subDir -> verifyDirectory(subDir, path + "/" + entry.getKey() + ":" + subDir.sourceNodeNr));

            // Check for all files, that the events of no file is a subset of
            // another. Otherwise, these two files should
            // have been merged
            var allFiles = entry.getValue().files.stream()
                    .<FsFile>flatMap(x -> x instanceof FsFile f ? Stream.of(f) : Stream.of()).toList();
            for (int i = 0; i < allFiles.size(); i++) {
                var a = allFiles.get(i);
                for (int j = i + 1; j < allFiles.size(); j++) {
                    var b = allFiles.get(j);
                    if (b.clock.compare(a.clock) != ClockRelation.CONCURRENT) {
                        throw new RuntimeException("unmerged file found: " + path + "/" + entry.getKey());
                    }
                }
            }
        }
    }
}
