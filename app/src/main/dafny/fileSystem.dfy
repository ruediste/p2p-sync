include "vectorClock.dfy"
include "eventSet.dfy"

// Optional helper datatype
datatype Option<T> = None | Some(value: T)

class Tag{}

// ---------------------------------------------------------------------------
// Abstract (mathematical) values of the file system structures. They are used
// to specify the behaviour of the heap based implementation below: every
// implementation class provides an `Abs()` function yielding its abstract
// value, and the mutating methods specify how that abstract value changes.
// ---------------------------------------------------------------------------

// datatype ADir = ADir(events: EventSet, entries: map<string, AEntry>){
// }


// datatype AFile = AFile( content: string, events: EventSet){
// }

// datatype AEntry = AEntry(directory: Option<ADir>, files: seq<AFile>, dirEvents: EventSet)
// {
// }


datatype ADir = ADir(
  events: EventSet,
  entries: map<string, AEntry>
)

datatype AFile = AFile(
  content: string,
  events: EventSet
)

datatype AEntry = AEntry(
  directory: Option<ADir>,
  files: seq<AFile>,
  dirEvents: EventSet
)


// A path from the root directory to a particular subdirectory.
//
// For each step, the location stores:
//   - the parent location
//   - the name of the selected entry
//   - the complete parent directory
//
// The selected entry itself is stored separately by Pointer.
datatype Location =
    Root
  | Child(
      parent: Location,
      name: string,
      parentDir: ADir
    )
{
  predicate Valid(){
    this.Child?==>(name in parentDir.entries
                   && parent.Valid())
  }

  function reconstructParent(dir: ADir): ADir
    requires Valid()
  {
    match this
    case Root =>
      dir

    case Child(parent, name, parentDir) =>
      var oldEntry := parentDir.entries[name];

      var newEntry := AEntry(
                        Some(dir),
                        oldEntry.files,
                        oldEntry.dirEvents
                      );

      ADir(
        parentDir.events,
        parentDir.entries[name := newEntry]
      )
  }

  function reconstruct(dir: ADir): ADir
    requires Valid()
  {
    match this
    case Root =>
      dir

    case Child(parent, name, parentDir) =>
      var oldEntry := parentDir.entries[name];

      var newEntry := AEntry(
                        Some(dir),
                        oldEntry.files,
                        oldEntry.dirEvents
                      );

      parent.reconstruct(
        ADir(
          parentDir.events,
          parentDir.entries[name := newEntry]
        )
      )
  }
}


// A pointer identifies a directory together with its location.
datatype Pointer = Pointer(
  location: Location,
  dir: ADir
)
{
  predicate Valid(){
    location.Valid()
  }

  // Descend into a named directory.
  function child(name: string): (result: Pointer)
    requires Valid()
    requires name in dir.entries
    requires dir.entries[name].directory.Some?
    ensures result.Valid()
    ensures result.reconstruct() == reconstruct()
  {
    var entry := dir.entries[name];
    var childDir := entry.directory.value;
    assert dir==ADir(dir.events, dir.entries[name:=AEntry(Some(childDir), entry.files, entry.dirEvents)]);
    Pointer(
      Child(location, name, dir),
      childDir
    )
  }

  // Move to the containing directory.
  function parent(): (result: Pointer)
    requires Valid()
    requires location != Root
    ensures result.Valid()
    ensures result.reconstruct() == reconstruct()
  {
    Pointer(location.parent, location.reconstructParent(dir))
  }

  // Replace the current directory.
  function replace(newDir: ADir): (result: Pointer)
    requires Valid()
    ensures result.Valid()
  {
    Pointer(location, newDir)
  }

  // Reconstruct the complete directory tree.
  function reconstruct(): ADir
    requires Valid()
  {
    location.reconstruct(dir)
  }
}

method testDirOperations()
{
  var file := AFile("hello", EventSet.Empty());

  var childDir := ADir(
    EventSet.Empty(),
    map[
      "file.txt" := AEntry(None, [file], EventSet.Empty())
    ]
  );

  var root := ADir(
    EventSet.Empty(),
    map[
      "home" := AEntry(Some(childDir), [], EventSet.Empty())
    ]
  );

  var p := Pointer(Root, root);

  assert p.child("home").dir == childDir;
  assert p.child("home").parent() == p;
  assert p.child("home").reconstruct() == root;

  var newDir := ADir(
    EventSet.Empty(),
    map[
      "file.txt" := AEntry(None, [], EventSet.Empty())
    ]
  );

  assert p.child("home").replace(newDir).reconstruct()
      == ADir(
           EventSet.Empty(),
           map[
             "home" := AEntry(Some(newDir), [], EventSet.Empty())
           ]
         );
}