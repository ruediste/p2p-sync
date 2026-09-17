// Optional helper datatype
datatype Option<T> = None | Some(value: T)

// Path handling structures
datatype FsPathPart = FsPathPart(name: string, nodeNr: Option<int>)

datatype FsPath = FsPath(parts: seq<FsPathPart>)
{
  function Last(): FsPathPart
    requires |parts| > 0
  {
    parts[|parts| - 1]
  }

  function RemoveLast(): FsPath
    requires |parts| > 0
  {
    FsPath(parts[..|parts| - 1])
  }
}

// File node representation
class FsFile {
  var sourceNodeNr: int
  var content: string
  var tag: int
  var clock: VectorClock
  var eventSet: EventSet

  ghost predicate Valid()
  {
    true
  }

  constructor(sourceNodeNr: int, nodeNr: int)
    ensures Valid()
  {
    this.sourceNodeNr := sourceNodeNr;
    this.content := "";
    this.tag := 0;
    var c := VectorClock.empty();
    this.clock := c.inc(nodeNr);
    var es := EventSet.empty();
    this.eventSet := es.add();
  }

  constructor Clone(other: FsFile)
    requires other.Valid()
    ensures Valid()
    ensures fresh(clock)
  {
    this.sourceNodeNr := other.sourceNodeNr;
    this.content := other.content;
    this.tag := other.tag;
    this.clock := VectorClock.FromMap(other.clock.values);
    this.eventSet := other.eventSet;
  }

  method GetContent() returns (r: string) {
    return content;
  }

  method SetContent(value: string, nodeNr: int)
    requires Valid()
    ensures Valid()
  {
    this.content := value;
    this.clock := this.clock.inc(nodeNr);
    this.eventSet := this.eventSet.add();
  }

  method IsBefore(other: FsFile) returns (r: bool)
    requires Valid() && other.Valid()
  {
    r := clock.isBefore(other.clock);
  }
}

// Directory entry mapping files and subdirectories
class DirEntry {
  var directory: Option<FsDirectory>
  var files: seq<FsFile>
  var dirClock: VectorClock

  ghost predicate Valid()
    reads this, dirClock
  {
    dirClock.Valid()
  }

  constructor(parentClock: VectorClock)
    requires parentClock.Valid()
    ensures Valid()
    ensures fresh(dirClock)
  {
    this.directory := None;
    this.files := [];
    this.dirClock := VectorClock.fromMap(parentClock.values);
  }

  method Clone(fsNodeNr: int) returns (r: DirEntry)
    requires Valid()
    ensures r.Valid()
    ensures fresh(r) && fresh(r.dirClock)
  {
    r := new DirEntry(this.dirClock);
    var clonedFiles: seq<FsFile> := [];
    var i := 0;
    while i < |files| {
      var cf := new FsFile.Clone(files[i]);
      clonedFiles := clonedFiles + [cf];
      i := i + 1;
    }
    r.files := clonedFiles;
    match directory {
      case None => r.directory := None;
      case Some(d) =>
        var cd := d.Clone(fsNodeNr);
        r.directory := Some(cd);
    }
  }
}

// Directory representation supporting CRDT/P2P merges
class FsDirectory {
  var sourceNodeNr: int
  var clock: VectorClock
  var events: EventSet
  var entries: map<string, DirEntry>

  ghost predicate Valid()
  {
    true
  }

  constructor(sourceNodeNr: int, nodeNr: int)
    ensures Valid()
  {
    this.sourceNodeNr := sourceNodeNr;
    var c := VectorClock.empty();
    this.clock := c.inc(nodeNr);
    this.events := EventSet.empty().add();
    this.entries := map[];
  }

  method AddEvent(nodeNr: int)
    requires Valid()
    ensures Valid()
  {
    this.clock := this.clock.inc(nodeNr);
    this.events := this.events.add();
  }

  method Clone(fsNodeNr: int) returns (r: FsDirectory)
    requires Valid()
    ensures r.Valid()
    ensures fresh(r) && fresh(r.clock)
  {
    r := new FsDirectory(sourceNodeNr, fsNodeNr);
    r.clock := new VectorClock.FromMap(this.clock.values);
    r.events := this.events;
    var newEntries: map<string, DirEntry> := map[];
    var keys := entries.Keys;
    while keys != {}
      decreases |keys|
    {
      var k :| k in keys;
      keys := keys - {k};
      var clonedEntry := entries[k].Clone(fsNodeNr);
      newEntries := newEntries[k := clonedEntry];
    }
    r.entries := newEntries;
  }

  method ChooseNewName(baseName: string) returns (candidate: string)
  {
    candidate := baseName;
    if candidate in entries {
      candidate := baseName + "_0";
    }
  }

  method AddFile(name: string, content: string, nodeNr: int) returns (f: FsFile)
    requires Valid()
    ensures Valid()
  {
    f := new FsFile(nodeNr, nodeNr);
    f.SetContent(content, nodeNr);
    var entry := new DirEntry(this.clock);
    entry.files := [f];
    entries := entries[name := entry];
  }

  method AddSubDirectory(name: string, nodeNr: int) returns (sub: FsDirectory)
    requires Valid()
    ensures Valid()
  {
    sub := new FsDirectory(nodeNr, nodeNr);
    var entry := new DirEntry(this.clock);
    entry.directory := Some(sub);
    entries := entries[name := entry];
  }

  method Remove(entryName: string)
  {
    entries := map k | k in entries.Keys && k != entryName :: entries[k];
  }

  method Merge(other: FsDirectory, fsNodeNr: int)
    requires Valid() && other.Valid()
    ensures Valid()
  {
    var modified := false;

    // 1. Remove entries deleted in 'other'
    var keysToRemove: set<string> := {};
    var ownKeys := entries.Keys;
    while ownKeys != {}
      decreases |ownKeys|
    {
      var k :| k in ownKeys;
      ownKeys := ownKeys - {k};
      if k !in other.entries {
        var entry := entries[k];
        var isBeforeOrEq := entry.dirClock.clock.isBefore(other.clock.clock) || entry.dirClock.clock.values == other.clock.clock.values;
        if isBeforeOrEq {
          keysToRemove := keysToRemove + {k};
        }
      }
    }

    var rem := keysToRemove;
    while rem != {}
      decreases |rem|
    {
      var k :| k in rem;
      rem := rem - {k};
      entries := map e | e in entries.Keys && e != k :: entries[e];
      modified := true;
    }

    // 2. Add or merge entries from 'other'
    var otherKeys := other.entries.Keys;
    while otherKeys != {}
      decreases |otherKeys|
    {
      var k :| k in otherKeys;
      otherKeys := otherKeys - {k};
      var otherEntry := other.entries[k];

      if k !in entries {
        var cloned := otherEntry.Clone(fsNodeNr);
        entries := entries[k := cloned];
        modified := true;
      } else {
        var ownEntry := entries[k];

        // Filter local files superseded by versions in 'other'
        var filteredFiles: seq<FsFile> := [];
        var i := 0;
        while i < |ownEntry.files| {
          var f := ownEntry.files[i];
          var shouldRemove := false;
          var j := 0;
          while j < |otherEntry.files| {
            var of := otherEntry.files[j];
            var before := f.IsBefore(of);
            if f.tag == of.tag && before {
              shouldRemove := true;
            }
            j := j + 1;
          }
          if !shouldRemove {
            filteredFiles := filteredFiles + [f];
          }
          i := i + 1;
        }
        if |filteredFiles| != |ownEntry.files| {
          modified := true;
        }
        ownEntry.files := filteredFiles;

        // Add incoming files that are not obsolete
        var m := 0;
        while m < |otherEntry.files| {
          var otherFile := otherEntry.files[m];
          var obsolete := false;
          var n := 0;
          while n < |ownEntry.files| {
            var f := ownEntry.files[n];
            var before := otherFile.IsBefore(f);
            if otherFile.tag == f.tag && before {
              obsolete := true;
            }
            n := n + 1;
          }
          if !obsolete {
            var clonedFile := new FsFile.Clone(otherFile);
            ownEntry.files := ownEntry.files + [clonedFile];
            modified := true;
          }
          m := m + 1;
        }

        // Merge subdirectories recursively
        match otherEntry.directory {
          case Some(otherDir) =>
            match ownEntry.directory {
              case Some(ownDir) =>
                ownDir.Merge(otherDir, fsNodeNr);
              case None =>
                var before := otherEntry.dirClock.clock.isBefore(this.clock.clock);
                if !before {
                  var clonedDir := otherDir.Clone(fsNodeNr);
                  ownEntry.directory := Some(clonedDir);
                }
            }
          case None =>
        }
      }
    }

    if modified {
      AddEvent(fsNodeNr);
    }
  }
}

// Top-level FileSystem structure
class FileSystem {
  var nodeNr: int
  var root: FsDirectory

  ghost predicate Valid()
    reads this, root, root.clock
  {
    root.Valid()
  }

  constructor(nodeNr: int)
    ensures Valid()
    ensures fresh(root) && fresh(root.clock)
  {
    this.nodeNr := nodeNr;
    this.root := new FsDirectory(nodeNr, nodeNr);
  }

  method Merge(other: FileSystem)
    requires Valid() && other.Valid()
    ensures Valid()
  {
    root.Merge(other.root, nodeNr);
  }
}
