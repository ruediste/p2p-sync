include "eventSet.dfy"
include "vectorClock.dfy"

module EventSetTests {
  import opened EventSet
  import opened VectorClock
  method testBasics(){
    var a:= EventSet.Empty();
    var b:=a.Add();
    var c:=a.Add();
    var d:=b.Merge(c);
    assert b.IsConcurrent(c);
    assert a.IsBefore(b);
    assert b.IsBefore(d);
    assert c.IsBefore(d);
  }

  method testEquivalence(){
    var a:=EventSet.Empty();
    var ac:=VectorClock.empty();

    var b:=a.Add();
    var bc:=ac.inc(1);

    var c:=a.Add();
    var cc:=ac.inc(2);

    assert a.IsBefore(b)== ac.isBefore(bc);
    assert a.IsBefore(c)== ac.isBefore(cc);
    assert b.IsBefore(c)== bc.isBefore(cc);
  }

  datatype X=X(events:EventSet, clock: VectorClock){
  }

  class Node{
    var nr: int
    var x: set<X>

    constructor(nr: int)
      ensures this.nr == nr
      ensures this.x == {}
    {
      this.nr := nr;
      this.x := {};
    }

    method inc(x: X)returns (r:X)
      ensures fresh(r.events.events-x.events.events)
    {
      var e:=x.events.Add();
      return X(e, x.clock.inc(nr));
    }
  }

  class System{
    var nodes: set<Node>
    constructor(nodes: set<Node>)
      ensures this.nodes==nodes
    {
      this.nodes := nodes;
    }

    static method Empty() returns (r:System)
      ensures r.nodes == {}
      ensures fresh(r)
      ensures r.Valid()
    {
      r := new System({});
    }

    ghost predicate Valid()
      reads this,set a | a in nodes :: a
    {
      (forall a, b,ax,bx :: a in nodes && b in nodes  && ax in a.x && bx in b.x==>
                              (ax.events.IsBefore(bx.events) == ax.clock.isBefore(bx.clock)))
      && (forall n ::n in nodes ==> n.nr<|nodes| && !exists m :: m in nodes && m != n && n.nr == m.nr)
    }

    method addNode()
      requires Valid()
      ensures Valid()
      modifies this
    {
      var n:=new Node(|nodes|);
      nodes := nodes + {n};
    }
  }

  method testIncNode(){
  }
}