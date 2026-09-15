package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class FileSystemDataStructureTest {

    static class FileSystem {
        int nodeNr;
        Directory root;

        FileSystem(int nodeNr) {
            this.nodeNr = nodeNr;
            root = new Directory(this);
        }

        public void merge(FileSystem other) {
            root.merge(other.root);
        }
    }

    static abstract class Entry<TSelf extends Entry<TSelf>> {
        public abstract TSelf clone();

        public abstract void merge(TSelf other);
    }

    static class EntryVersion {
        int nodeNr;
        Entry<?> entry;

        public EntryVersion(int nodeNr, Entry<?> entry) {
            this.nodeNr = nodeNr;
            this.entry = entry;
        }

        public EntryVersion clone() {
            return new EntryVersion(nodeNr, entry.clone());
        }
    }

    static class Directory extends Entry<Directory> {

        private FileSystem fs;
        Map<String, List<EntryVersion>> entries = new HashMap<>();

        public Directory(FileSystem fs) {

            this.fs = fs;

        }

        // Merge the other directory into this directory
        public void merge(Directory other) {
            for (var entry : other.entries.entrySet()) {
                entries.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .addAll(entry.getValue().stream().map(x -> x.clone()).toList());
            }
        }

        public Directory clone() {
            var result = new Directory(fs);
            for (var e : entries.entrySet())
                result.entries.put(e.getKey(), new ArrayList<>(e.getValue().stream().map(x -> x.clone()).toList()));
            return result;
        }

        Entry<?> loadEntry(String path) {
            var versions = loadVersions(path);
            if (versions.size() == 1)
                return versions.get(0).entry;
            else
                throw new IllegalArgumentException("Multiple versions found for " + path);
        }

        static record NameAndNodeNr(String name, int nodeNr) {
        }

        private NameAndNodeNr parseNameAndNodeNr(String name) {
            var idx = name.indexOf(':');
            if (idx >= 0) {
                return new NameAndNodeNr(name.substring(0, idx), Integer.parseInt(name.substring(idx + 1)));
            } else
                return new NameAndNodeNr(name, -1);
        }

        List<EntryVersion> loadVersions(String path) {
            String[] parts = path.split("/");
            Directory current = this;
            var currentPath = new StringBuilder();
            for (var i = 0; i < parts.length - 1; i++) {
                var part = parts[i];
                if (!currentPath.isEmpty())
                    currentPath.append("/");
                currentPath.append(part);

                var nameAndNodeNr = parseNameAndNodeNr(part);
                part = nameAndNodeNr.name();

                var versions = current.entries.get(part);
                Entry<?> child;
                if (nameAndNodeNr.nodeNr() >= 0) {
                    child = versions.stream().filter(x -> x.nodeNr == nameAndNodeNr.nodeNr()).findFirst()
                            .map(x -> x.entry)
                            .orElse(null);
                } else {
                    child = versions.stream().findFirst().map(x -> x.entry).orElse(null);
                }

                if (child == null) {
                    throw new IllegalArgumentException("no entry found for " + currentPath);
                }
                if (!(child instanceof Directory dir)) {
                    throw new IllegalArgumentException("Expected directory, found file at " + currentPath);
                }
                current = dir;
            }

            {
                var nameAndNodeNr = parseNameAndNodeNr(parts[parts.length - 1]);
                var versions = current.entries.get(nameAndNodeNr.name());
                if (versions == null) {
                    throw new IllegalArgumentException("no entry found for " + path);
                }
                if (nameAndNodeNr.nodeNr() >= 0) {
                    return versions.stream().filter(x -> x.nodeNr == nameAndNodeNr.nodeNr()).toList();
                } else {
                    return versions;
                }
            }
        }

        Directory loadDir(String path) {
            return (Directory) loadEntry(path);
        }

        File loadFile(String path) {
            return (File) loadEntry(path);
        }

        File addFile(String name, String content) {
            var file = new File(fs);
            file.content = content;
            entries.computeIfAbsent(name, k -> new ArrayList<>()).add(new EntryVersion(fs.nodeNr, file));
            return file;
        }

        Directory addSubDirectory(String name) {
            var dir = new Directory(fs);
            entries.computeIfAbsent(name, k -> new ArrayList<>()).add(new EntryVersion(fs.nodeNr, dir));
            return dir;
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
    }

    static class File extends Entry<File> {
        private FileSystem fs;
        private String content = "";
        public Set<Object> eventSet = new HashSet<>();

        public File(FileSystem fs) {
            this.fs = fs;
        }

        @Override
        public File clone() {
            var result = new File(fs);
            result.content = content;
            return result;
        }

        @Override
        public void merge(File other) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'merge'");
        }

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
            this.eventSet.add(new Object());
        }
    }

    @Test
    public void generatedNamesAreUniqueAndDeterministic() {
        var fs = new FileSystem(1);
        var dir = fs.root;
        dir.entries.put("file", new ArrayList<>());
        dir.entries.put("file0", new ArrayList<>());
        dir.entries.put("file1", new ArrayList<>());

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
    public void concurrentFileModification() {
        var fs1 = new FileSystem(1);
        fs1.root.addFile("a", "foo");

        var fs2 = new FileSystem(2);
        fs2.merge(fs1);

        fs1.root.loadFile("a").setContent("foo2");
        fs2.root.loadFile("a").setContent("bar");

        fs2.merge(fs1);
        {
            var versions = fs2.root.loadVersions("a");
            assertEquals(2, versions.size());
            assertEquals(Set.of("foo2", "bar"),
                    versions.stream().map(x -> ((File) x.entry).getContent()).collect(Collectors.toSet()));
        }
    }

    static record Action(String name, double weight, Runnable action) {
    }

    @Test
    public void modelBasedTest() {
        int fileSystemsCount = 3;
        int testCount = 10;
        int iterationCount = 1000;

        for (int testNr = 0; testNr < testCount; testNr++) {
            // build file systems
            var fileSystems = new ArrayList<FileSystem>();
            var fs0 = new FileSystem(0);
            for (int i = 1; i < fileSystemsCount; i++) {
                var fs = new FileSystem(i);
                fs.root = fs0.root.clone();
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
                        var dirsToProcess = new ArrayList<Directory>();
                        dirsToProcess.add(fs.root);
                        while (!dirsToProcess.isEmpty()) {
                            var dir = dirsToProcess.remove(dirsToProcess.size() - 1);

                            // iterate over all entries in the directory
                            for (var entry : dir.entries.entrySet()) {
                                var name = entry.getKey();
                                for (var version : entry.getValue()) {
                                    var entryInstance = version.entry;
                                    if (entryInstance instanceof Directory subDir) {
                                        dirsToProcess.add(subDir);
                                    } else if (entryInstance instanceof File file) {
                                        actions.add(new Action("modify file " + name + " in fs" + i, 1.0, () -> {
                                            file.setContent(file.getContent() + "x");
                                        }));
                                    }
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

    private void verifyDirectory(Directory dir, String path) {
        // verify all entries of this directory, recursing into subdirectories
        for (var entry : dir.entries.entrySet()) {
            for (var version : entry.getValue()) {
                if (version.entry instanceof Directory subDir) {
                    verifyDirectory(subDir, path + "/" + entry.getKey() + ":" + version.nodeNr);
                }
            }

            // Check for all files, that the events of no file is a subset of
            // another. Otherwise, these two files should
            // have been merged
            var allFiles = entry.getValue().stream()
                    .<File>flatMap(x -> x.entry instanceof File f ? Stream.of(f) : Stream.of()).toList();
            for (int i = 0; i < allFiles.size(); i++) {
                var a = allFiles.get(i);
                for (int j = i + 1; j < allFiles.size(); j++) {
                    var b = allFiles.get(j);
                    if (b.eventSet.containsAll(a.eventSet)
                            || a.eventSet.containsAll(b.eventSet)) {
                        throw new RuntimeException("unmerged file found: " + path + "/" + entry.getKey());
                    }
                }
            }
        }
    }
}
