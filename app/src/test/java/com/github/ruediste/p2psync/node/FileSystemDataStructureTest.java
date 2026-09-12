package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        }
    }

    static class Directory extends Entry {

        private FileSystem fs;

        public Directory(FileSystem fs) {
            this.fs = fs;

        }

        Map<String, Entry> entries = new HashMap<>();

        Entry loadEntry(String path) {
            String[] parts = path.split("/");
            Entry current = this;
            var currentPath = new StringBuilder();
            for (var part : parts) {
                if (!currentPath.isEmpty())
                    currentPath.append("/");
                currentPath.append(part);
                if (!(current instanceof Directory)) {
                    throw new IllegalArgumentException("Expected directory, found file at " + currentPath);
                }
                current = ((Directory) current).entries.get(part);
                if (current == null) {
                    throw new IllegalArgumentException("no entry found for " + currentPath);
                }

            }
            return current;
        }

        Directory loadDir(String path) {
            return (Directory) loadEntry(path);
        }

        File loadFile(String path) {
            return (File) loadEntry(path);

        }

        File addFile(String name, String content) {
            var file = new File(fs);
            file.versions.add(new FileVersion(fs.nodeNr, content));
            entries.put(name, file);
            return file;
        }

        Directory addSubDirectory(String name) {
            var dir = new Directory(fs);
            entries.put(name, dir);
            return dir;
        }
    }

    static class Entry {
    }

    static class File extends Entry {
        private FileSystem fs;
        public List<FileVersion> versions = new ArrayList<>();

        public File(FileSystem fs) {
            this.fs = fs;
        }

        public String getContent() {
            return getSingleVersion().content;
        }

        public void setContent(String value) {
            getSingleVersion().content = value;
        }

        private FileVersion getSingleVersion() {
            if (versions.size() > 1) {
                throw new RuntimeException("More than one version");
            }
            var version = versions.get(0);
            return version;
        }

        public Set<String> getContents() {
            return versions.stream().map(x -> x.content).collect(Collectors.toSet());
        }
    }

    static class FileVersion {
        public int nodeNr;
        public String content = "";

        public FileVersion(int nodeNr, String content) {
            this.nodeNr = nodeNr;
            this.content = content;
        }
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
            var a = fs2.root.loadFile("a");
            assertEquals(2, a.versions.size());
            assertEquals(Set.of("foo2", "bar"), a.getContent());
        }
    }
}
