include "fileSystem.dfy"

// Property 1: Creation of files and subdirectories correctly updates directory entry maps.
ghost method Verify_CreateDirAndFile() {
  var fs := new FileSystem(1);
  var f1 := fs.root.AddFile("a", "foo", 1);
  var subDir := fs.root.AddSubDirectory("b", 1);
  var f2 := subDir.AddFile("c", "bar", 1);

  assert "a" in fs.root.entries;
  assert "b" in fs.root.entries;
  assert "c" in subDir.entries;
  assert f1.content == "foo";
  assert f2.content == "bar";
}

// Property 2: ChooseNewName yields a non-conflicting candidate name.
ghost method Verify_ChooseNewName() {
  var fs := new FileSystem(1);
  var f1 := fs.root.AddFile("file", "", 1);
  var candidate := fs.root.ChooseNewName("file");

  assert candidate !in fs.root.entries;
  assert candidate == "file_0";
}

// Property 3: Merging disjoint file systems incorporates new entries.
ghost method Verify_SimpleMerge() {
  var fs1 := new FileSystem(1);
  var f1 := fs1.root.AddFile("a", "foo", 1);

  var fs2 := new FileSystem(2);
  fs2.Merge(fs1);

  assert "a" in fs2.root.entries;
  assert |fs2.root.entries["a"].files| == 1;
  assert fs2.root.entries["a"].files[0].content == "foo";
}

// Property 4: Sequential updates across merges replace superseded file versions.
ghost method Verify_MergeModifyMerge() {
  var fs1 := new FileSystem(1);
  var f1 := fs1.root.AddFile("a", "foo", 1);

  var fs2 := new FileSystem(2);
  fs2.Merge(fs1);

  f1.SetContent("foo2", 1);
  fs2.Merge(fs1);

  assert "a" in fs2.root.entries;
  assert |fs2.root.entries["a"].files| == 1;
  assert fs2.root.entries["a"].files[0].content == "foo2";
}

// Property 5: Concurrent edits on distinct nodes result in conflict version sets.
ghost method Verify_ConcurrentFileModification() {
  var fs1 := new FileSystem(1);
  var f1 := fs1.root.AddFile("a", "foo", 1);

  var fs2 := new FileSystem(2);
  fs2.Merge(fs1);

  f1.SetContent("foo_from_fs1", 1);

  assert "a" in fs2.root.entries;
  var entryFs2 := fs2.root.entries["a"];
  assert |entryFs2.files| == 1;
  entryFs2.files[0].SetContent("bar_from_fs2", 2);

  fs2.Merge(fs1);

  var mergedEntry := fs2.root.entries["a"];
  assert |mergedEntry.files| == 2;
}

// Property 6: Local entry deletions propagate to remote instances upon merge.
ghost method Verify_RemoveAndMerge() {
  var fs1 := new FileSystem(1);
  var f1 := fs1.root.AddFile("a", "foo", 1);

  var fs2 := new FileSystem(2);
  fs2.Merge(fs1);

  fs1.root.Remove("a");
  assert "a" !in fs1.root.entries;

  fs2.Merge(fs1);
  assert "a" !in fs2.root.entries;
}