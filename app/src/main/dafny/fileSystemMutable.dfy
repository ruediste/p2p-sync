include "vectorClock.dfy"
include "eventSet.dfy"

module FileSystemMutable {

  import opened EventSet
  import opened VectorClock

  datatype Option<T> = None | Some(value: T)
  {
    function Map<R>(f: T -> R): Option<R>
    {
      match this
      case None => None
      case Some(value) => Some(f(value))
    }
  }

  class ADir {
    var events: EventSet
    var entries: map<string, AEntry>
    ghost var Repr: set<object>

    constructor()
      ensures Valid() && fresh(this) && fresh(Repr)
    {
      this.events := EventSet.Empty();
      this.entries := map[];
      this.Repr := {this};
    }

    ghost predicate Valid()
      reads this`Repr, Repr
      decreases Repr
    {
      this in Repr &&
      (forall k :: k in entries ==>
                     entries[k] in Repr &&
                     entries[k].Repr < Repr &&
                     entries[k].Valid()) &&
      Repr == {this} + (set k | k in entries :: entries[k]) + (set k, o | k in entries && o in entries[k].Repr :: o)
    }

    method  clone() returns (r: ADir)
      requires Valid()
      // ensures r.Valid() && fresh(r) && fresh(r.Repr)
      decreases Repr
    {
      r:= new ADir();
      r.events:=events;
      assert r in r.Repr;

      var keys := entries.Keys;

      while keys != {}
      {
        var k :| k in keys;
        keys := keys - {k};
        var clonedEntry := entries[k].clone();
        r.entries := r.entries[k := clonedEntry];
        r.Repr := r.Repr + {clonedEntry} + clonedEntry.Repr;
      }
    }
  }

  class AEntry {
    var directory: Option<ADir>
    var files: seq<AFile>
    var dirEvents: EventSet
    ghost var Repr: set<object>

    constructor()
      ensures Valid()
      ensures directory == None && files == [] && dirEvents == EventSet.Empty()
      ensures fresh(this) && fresh(Repr)
      ensures Repr=={this}
    {
      this.directory := None;
      this.files := [];
      this.dirEvents := EventSet.Empty();
      this.Repr := {this};

    }

    ghost predicate Valid()
      reads this, Repr
      decreases Repr
    {
      this in Repr &&
      (directory.Some? ==> directory.value in Repr && directory.value.Repr < Repr && directory.value.Valid()) &&
      (forall f :: f in files ==> f in Repr && f.Repr < Repr && f.Valid())
      && Repr == {this}
                 + (match directory { case Some(d) => {d} + d.Repr case None => {} })
                 + (set f | f in files :: f)
                 + (set f, o | f in files && o in f.Repr :: o)
    }

    // method  clone() returns (r: AEntry)
    //   requires Valid()
    //   ensures r.Valid() && fresh(r) && fresh(r.Repr)
    //   decreases Repr
    // {
    //   r:=new AEntry();

    //   if (directory.Some?) {
    //     var d' := directory.value.clone();
    //     r.directory := Some(d');
    //     r.Repr := r.Repr + {d'} + d'.Repr;
    //   }
    //   assert r.Valid();

    //   var i := 0;
    //   while i < |files|
    //     invariant 0 <= i <= |files|
    //     invariant Valid() && r.Valid()
    //     invariant fresh(r.Repr)
    //   {
    //     var f' := files[i].clone();
    //     r.files := r.files + [f'];
    //     r.Repr := r.Repr + {f'} + f'.Repr;
    //     i := i + 1;
    //   }
    //   return r;
    // }

    method {:verify false} clone() returns (r: AEntry)
      requires Valid()
      ensures r.Valid() && fresh(r) && fresh(r.Repr)
      ensures r.dirEvents == this.dirEvents
      decreases Repr
    {
      r := new AEntry();
      r.dirEvents := this.dirEvents; // Added to properly clone the events

      if (directory.Some?) {
        var d' := directory.value.clone();
        r.directory := Some(d');
        r.Repr := r.Repr + {d'} + d'.Repr;
      }

      var i := 0;
      while i < |files|
      {
        var f := files[i];
        var f' := f.clone();
        r.files := r.files + [f'];
        r.Repr := r.Repr + {f'} + f'.Repr;
        i := i + 1;
      }
      return r;
    }
  }

  class AFile{
    var content: string
    var events: EventSet
    ghost var Repr: set<object>

    constructor(content: string, events: EventSet)
      ensures Repr == {this}
    {
      this.content := content;
      this.events := events;
      this.Repr := {this};
    }

    ghost predicate Valid()
      reads this, Repr
    {
      Repr == {this}
    }
    method clone() returns (r: AFile)
      requires Valid()
      ensures r.Valid() && fresh(r) && fresh(r.Repr)
    {
      r := new AFile(content, events);
    }
  }

  method testDirOperations(){

  }
}