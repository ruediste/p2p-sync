package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.clock.VectorClock;
import com.github.ruediste.p2psync.proto.Sync;

public class FsDirectory {
    public VectorClock clock;
    public List<DirectoryEntry> directories;
    public List<FileEntry> files;

    private FsDirectory() {
    }

    public static FsDirectory empty(VectorClock clock) {
        var result = new FsDirectory();
        result.clock = clock.clone();
        result.directories = new ArrayList<>();
        result.files = new ArrayList<>();
        return result;
    }

    public Sync.Directory toProto() {
        return Sync.Directory.newBuilder()
                .setClock(clock.toProto())
                .addAllDirectories(directories.stream().map(DirectoryEntry::toProto).collect(Collectors.toList()))
                .addAllFiles(files.stream().map(FileEntry::toProto).collect(Collectors.toList()))
                .build();
    }

    public static FsDirectory fromProto(Sync.Directory proto) {
        FsDirectory res = new FsDirectory();
        res.clock = VectorClock.from(proto.getClock());
        res.directories = proto.getDirectoriesList().stream().map(DirectoryEntry::fromProto)
                .collect(Collectors.toList());
        res.files = proto.getFilesList().stream().map(FileEntry::fromProto).collect(Collectors.toList());
        return res;
    }

    public static class DirectoryEntry {
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

        public static DirectoryEntry fromProto(Sync.DirectoryEntry proto) {
            DirectoryEntry res = new DirectoryEntry();
            res.clock = VectorClock.from(proto.getClock());
            res.name = proto.getName();
            res.directoryId = BlockId.fromProto(proto.getDirectoryId());
            return res;
        }
    }

    public static class FileEntry {
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

        public static FileEntry fromProto(Sync.FileEntry proto) {
            FileEntry res = new FileEntry();
            res.clock = VectorClock.from(proto.getClock());
            res.name = proto.getName();
            res.dataBlockId = BlockId.fromProto(proto.getDataBlockId());
            return res;
        }
    }
}
