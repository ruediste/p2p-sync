package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.clock.VectorClock;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.InvalidProtocolBufferException;

public class DirectoryHandle {
    public VectorClock clock;
    public List<DirectoryEntryHandle> directories;
    public List<FileEntryHandle> files;
    private NodeUserHandle userHandle;

    private DirectoryHandle(NodeUserHandle userHandle) {
        this.userHandle = userHandle;
    }

    public static DirectoryHandle empty(VectorClock clock, NodeUserHandle userHandle) {
        var result = new DirectoryHandle(userHandle);
        result.clock = clock.clone();
        result.directories = new ArrayList<>();
        result.files = new ArrayList<>();
        return result;
    }

    /**
     * Note: parts of other may be referenced after the merge. Do not modify other
     * afterward the merge
     */
    public void merge(DirectoryHandle other) {
        boolean hasLowerPeerId = userHandle.getNode().peerId.compareTo(other.userHandle.getNode().peerId) < 0;
        clock.merge(other.clock);

        // merge files
        {
            var otherFilesMap = new HashMap<>(other.files.stream().collect(Collectors.toMap(x -> x.name, x -> x)));

            var conflicts = new ArrayList<FileEntryHandle>();
            var newFiles = new ArrayList<FileEntryHandle>();
            for (var file : files) {
                var otherFile = otherFilesMap.remove(file.name);
                if (otherFile == null) {
                    // the file is not present in other
                    if (file.clock.isBefore(other.clock)) {
                        // The other directory has seen the file, but it is not there anymore. Thus it
                        // has been deleted.
                        // NOP
                    } else {
                        // the file has been modified locally, keep
                        newFiles.add(file);
                    }
                } else {
                    // the file is also present in other
                    switch (file.clock.compare(otherFile.clock)) {
                        case AFTER:
                            // our file is newer, keep our file
                            newFiles.add(file);
                            break;
                        case BEFORE:
                            // the other file is newer, keep other file
                            newFiles.add(otherFile);
                            break;
                        case CONCURRENT:
                            // both files have been modified, create conflicting versions
                            if (hasLowerPeerId) {
                                newFiles.add(file);
                                conflicts.add(otherFile);
                            } else {
                                newFiles.add(otherFile);
                                conflicts.add(file);
                            }
                            break;
                        case EQUAL:
                            // nothing changed, keep our file (could just as well keep the other file...)
                            newFiles.add(file);
                            break;
                        default:
                            break;
                    }
                }
            }

            // process remaining other files
            for (var otherFile : otherFilesMap.values()) {
                if (otherFile.clock.isBefore(clock)) {
                    // we have seen the other file, and it is not present anymore, delete.
                    // NOP
                } else {
                    newFiles.add(otherFile);
                }
            }

            // handle conflicts
            var newFileNames = new HashSet<>(newFiles.stream().map(x -> x.name).toList());
            conflicts.sort(Comparator.comparing(x -> x.name));
            for (var conflict : conflicts) {
                for (int i = 0;; i++) {
                    var name = conflict.name + "_conflict" + (i == 0 ? "" : "_" + i);
                    if (!newFileNames.contains(name)) {
                        newFileNames.add(name);
                        conflict.name = name;
                        newFiles.add(conflict);
                    }
                }
            }
            files = newFiles;
        }

        // merge directories
        {
            var otherDirectoriesMap = new HashMap<>(
                    other.directories.stream().collect(Collectors.toMap(x -> x.name, x -> x)));

            var newDirectories = new ArrayList<DirectoryEntryHandle>();
            for (var directory : directories) {
                var otherDirectory = otherDirectoriesMap.remove(directory.name);
                if (otherDirectory == null) {
                    // the directory is not present in other
                    if (directory.clock.isBefore(other.clock)) {
                        // The other directory has seen the directory, but it is not there anymore.
                        // Thus it has been deleted.
                        // NOP
                    } else {
                        // the directory has been modified locally, keep
                        newDirectories.add(directory);
                    }
                } else {
                    // the directory is also present in other
                    switch (directory.clock.compare(otherDirectory.clock)) {
                        case AFTER:
                            // our directory is newer, keep our directory
                            newDirectories.add(directory);
                            break;
                        case BEFORE:
                            // the other directory is newer, keep other directory
                            newDirectories.add(otherDirectory);
                            break;
                        case CONCURRENT:
                            // both directories have been modified, merge recursively
                            var merged = loadDirectory(directory.directoryId);
                            merged.merge(loadDirectory(otherDirectory.directoryId));
                            newDirectories.add(newEntry(directory.name, merged));
                            break;
                        case EQUAL:
                            // nothing changed, keep our directory (could just as well keep the
                            // other directory...)
                            newDirectories.add(directory);
                            break;
                        default:
                            break;
                    }
                }
            }

            // process remaining other directories
            for (var otherDirectory : otherDirectoriesMap.values()) {
                if (otherDirectory.clock.isBefore(clock)) {
                    // we have seen the other directory, and it is not present anymore, delete.
                    // NOP
                } else {
                    newDirectories.add(otherDirectory);
                }
            }

            directories = newDirectories;
        }
    }

    private DirectoryHandle loadDirectory(BlockId id) {
        try {
            return fromProto(Sync.Directory.parseFrom(userHandle.getNode().network.getBlock(id)), userHandle);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private BlockId storeDirectory(DirectoryHandle directory) {
        return userHandle.getNode().storage.store(directory.toProto());
    }

    private DirectoryEntryHandle newEntry(String name, DirectoryHandle directory) {
        var entry = new DirectoryEntryHandle();
        entry.clock = directory.clock;
        entry.name = name;
        entry.directoryId = storeDirectory(directory);
        return entry;
    }

    public Sync.Directory toProto() {
        return Sync.Directory.newBuilder()
                .setClock(clock.toProto())
                .addAllDirectories(directories.stream().map(DirectoryEntryHandle::toProto).collect(Collectors.toList()))
                .addAllFiles(files.stream().map(FileEntryHandle::toProto).collect(Collectors.toList()))
                .build();
    }

    public static DirectoryHandle fromProto(Sync.Directory proto, NodeUserHandle userHandle) {
        DirectoryHandle res = new DirectoryHandle(userHandle);
        res.clock = VectorClock.from(proto.getClock());
        res.directories = proto.getDirectoriesList().stream().map(DirectoryEntryHandle::fromProto)
                .collect(Collectors.toList());
        res.files = proto.getFilesList().stream().map(FileEntryHandle::fromProto).collect(Collectors.toList());
        return res;
    }

    public static class DirectoryEntryHandle {
        public VectorClock clock;
        public String name;
        public BlockId directoryId;

        public Sync.DirectoryEntry toProto() {
            return Sync.DirectoryEntry.newBuilder()
                    .setClock(clock.toProto())
                    .setName(name)
                    .setDirectoryId(directoryId.toProto())
                    .build();
        }

        public static DirectoryEntryHandle fromProto(Sync.DirectoryEntry proto) {
            DirectoryEntryHandle res = new DirectoryEntryHandle();
            res.clock = VectorClock.from(proto.getClock());
            res.name = proto.getName();
            res.directoryId = BlockId.fromProto(proto.getDirectoryId());
            return res;
        }
    }

    public static class FileEntryHandle {
        public VectorClock clock;
        public String name;
        public BlockId dataBlockId;

        public Sync.FileEntry toProto() {
            return Sync.FileEntry.newBuilder()
                    .setClock(clock.toProto())
                    .setName(name)
                    .setDataBlockId(dataBlockId.toProto())
                    .build();
        }

        public static FileEntryHandle fromProto(Sync.FileEntry proto) {
            FileEntryHandle res = new FileEntryHandle();
            res.clock = VectorClock.from(proto.getClock());
            res.name = proto.getName();
            res.dataBlockId = BlockId.fromProto(proto.getDataBlockId());
            return res;
        }
    }
}
