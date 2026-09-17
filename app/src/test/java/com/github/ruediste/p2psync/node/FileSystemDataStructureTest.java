package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

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
            root.merge(other.root);
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
        VectorClock dirClock;

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
    }

    static record FsPathPart(String name, Optional<Integer> nodeNr) {
        public static FsPathPart parse(String name) {
            var idx = name.indexOf(':');
            if (idx >= 0) {
                return new FsPathPart(name.substring(0, idx), Optional.of(Integer.parseInt(name.substring(idx + 1))));
            } else
                return new FsPathPart(name, Optional.empty());
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
    }

    static class FsDirectory extends FsElementBase<FsDirectory> {

        private EventSet events;
        private VectorClock clock;
        private FileSystem fs;
        Map<String, DirEntry> entries = new HashMap<>();

        public FsDirectory(int sourceNodeNr, FileSystem fs) {
            super(sourceNodeNr);
            this.fs = fs;
        }

        private void addEvent(String event) {
            clock.increment(fs.nodeNr);
            events.add(event);
        }

        public static FsDirectory empty(FileSystem fs) {
            var result = new FsDirectory(fs.nodeNr, fs);
            result.clock = VectorClock.empty();
            result.clock.increment(fs.nodeNr);
            result.events = new EventSet();
            result.events.add("created");
            return result;
        }

        // Merge the other directory into this directory
        public void merge(FsDirectory other) {
            var modified = false;
            // remove own entries, which have been deleted in other
            {
                var entriesToRemove = entries.entrySet().stream()
                        .filter(e -> !other.entries.containsKey(e.getKey())
                                && e.getValue().dirClock.isBeforeOrEqual(other.clock))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                for (var key : entriesToRemove) {
                    modified = true;
                    entries.remove(key);
                }
            }

            // add data from the other directory into this one
            for (var otherEntry : other.entries.entrySet()) {

                var ownEntry = entries.get(otherEntry.getKey());
                if (ownEntry == null) {
                    entries.put(otherEntry.getKey(), otherEntry.getValue().clone(fs));
                    modified = true;
                    continue;
                }

                // remove files that are before any FileEntry in otherVersions
                {
                    var oldSize = ownEntry.files.size();
                    ownEntry.files.removeIf(f -> otherEntry.getValue().files.stream()
                            .anyMatch(of -> f.tag == of.tag && f.isBefore(of)));
                    modified |= oldSize != ownEntry.files.size();
                }

                // only add the other files if it is not obsolete
                for (var otherFile : otherEntry.getValue().files) {
                    if (!ownEntry.files.stream()
                            .anyMatch(f -> otherFile.tag == f.tag && otherFile.isBefore(f))) {
                        ownEntry.files.add(otherFile.clone(fs));
                        modified = true;
                    }
                }

                // merge the subdirectories recursively
                if (otherEntry.getValue().directory.isPresent()) {
                    var otherDir = otherEntry.getValue().directory.get();
                    if (ownEntry.directory.isPresent()) {
                        ownEntry.directory.get().merge(otherDir);
                    } else {
                        // We have no local entry. If if the entry is before the own clock,
                        // we must have deleted it locally
                        if (!otherEntry.getValue().dirClock.isBefore(clock))
                            ownEntry.directory = Optional.of(otherDir.clone(fs));
                    }
                }
            }
            if (modified)
                addEvent("merged");
        }

        public FsDirectory clone(FileSystem fs) {
            var result = new FsDirectory(sourceNodeNr, fs);
            result.clock = clock.clone();
            result.events = events.clone();
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
        }
    }

    static class EventSet {
        private class Event {
            public String name;

            @Override
            public String toString() {
                return name;
            }
        }

        private Set<Event> events = new HashSet<>();

        public void add(String name) {
            var event = new Event();
            event.name = name;
            events.add(event);
        }

        public EventSet clone() {
            var result = new EventSet();
            result.events.addAll(events);
            return result;
        }

        public boolean isSubsetOf(EventSet other) {
            return events.stream().allMatch(x -> other.events.contains(x));
        }

        public void assertIsSubsetOf(EventSet other) {
            var missingEvents = events.stream().filter(x -> !other.events.contains(x)).toList();
            if (!missingEvents.isEmpty()) {
                throw new AssertionError("Events " + missingEvents + " are not in the other set");
            }
        }
    }

    static class FsFile extends FsElementBase<FsFile> {
        private FileSystem fs;
        private String content = "";
        public Object tag;
        public VectorClock clock;
        // ghost field to track events related to this file entry
        public EventSet eventSet = new EventSet();

        public boolean isBefore(FsFile other) {
            var result = this.clock.isBefore(other.clock);
            if (result)
                eventSet.assertIsSubsetOf(other.eventSet);
            return result;
        }

        private FsFile(int sourceNodeNr, FileSystem fs) {
            super(sourceNodeNr);
            this.fs = fs;
        }

        public static FsFile createNewFile(int sourceNodeNr, FileSystem fs) {
            var result = new FsFile(sourceNodeNr, fs);
            result.clock = VectorClock.empty();
            result.clock.increment(fs.nodeNr);
            result.eventSet.add("created");
            return result;
        }

        @Override
        public FsFile clone(FileSystem fs) {
            var result = new FsFile(sourceNodeNr, fs);
            result.content = content;
            result.clock = clock.clone();
            result.eventSet = eventSet.clone();
            return result;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
            this.clock.increment(fs.nodeNr);
            this.eventSet.add("setContent " + value);
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

    static record Action(String name, double weight, Runnable action) {
    }

    @Test
    public void modelBasedTest() {
        int fileSystemsCount = 3;
        int testCount = 10;
        int iterationCount = 10;

        for (int testNr = 0; testNr < testCount; testNr++) {
            // build file systems
            var fileSystems = new ArrayList<FileSystem>();
            var fs0 = new FileSystem(0);
            for (int i = 1; i < fileSystemsCount; i++) {
                var fs = new FileSystem(i);
                fs.root = fs0.root.clone(fs);
                fileSystems.add(fs);
            }

            // iterate over a number of iterations, randomly modifying the file systems and
            // merging them
            var random = new java.util.Random(testNr);
            var history = new StringBuilder();
            for (int iterationNr = 0; iterationNr < iterationCount; iterationNr++) {
                // collect possible actions
                var actions = new ArrayList<Action>();

                for (int i = 0; i < fileSystems.size(); i++) {
                    var fs = fileSystems.get(i);

                    // merge with another random file system
                    {
                        var otherFs = fileSystems.get(random.nextInt(fileSystems.size()));
                        if (otherFs != fs) {
                            actions.add(
                                    new Action("merge fs" + i + " from fs" + otherFs.nodeNr, 1.0,
                                            () -> fs.merge(otherFs)));
                        }
                    }

                    // iterate over all directories and files and add actions for modifying them
                    {
                        var dirsToProcess = new ArrayList<FsDirectory>();
                        dirsToProcess.add(fs.root);
                        while (!dirsToProcess.isEmpty()) {
                            var dir = dirsToProcess.remove(dirsToProcess.size() - 1);

                            // iterate over all entries in the directory
                            for (var entry : dir.entries.entrySet()) {
                                var name = entry.getKey();
                                entry.getValue().directory.ifPresent(dirsToProcess::add);
                                for (var file : entry.getValue().files) {
                                    actions.add(new Action("modify file " + name + " in fs" + i, 1.0, () -> {
                                        file.setContent(file.getContent() + "x");
                                    }));
                                }
                            }

                            // add file to directory
                            actions.add(new Action("add file to dir in fs" + i, 1.0, () -> {
                                var newFileName = dir.chooseNewName("file");
                                dir.addFile(newFileName, "content");
                            }));

                            // add subdirectory to directory
                            actions.add(new Action("add subdir to dir in fs" + i, 1.0, () -> {
                                var newDirName = dir.chooseNewName("dir");
                                dir.addSubDirectory(newDirName);
                            }));
                        }
                    }

                }

                // choose random action (weighted) and execute it
                var totalWeight = actions.stream().mapToDouble(x -> x.weight).sum();
                var r = random.nextDouble() * totalWeight;
                double currentWeight = 0;
                for (var a : actions) {
                    currentWeight += a.weight;
                    if (currentWeight >= r) {
                        history.append("Executing action: " + a.name + "\n");
                        a.action.run();
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
                    if (b.eventSet.isSubsetOf(a.eventSet)
                            || a.eventSet.isSubsetOf(b.eventSet)) {
                        throw new RuntimeException("unmerged file found: " + path + "/" + entry.getKey());
                    }
                }
            }
        }
    }
}
