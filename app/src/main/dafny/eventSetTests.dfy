include "eventSet.dfy"
include "vectorClock.dfy"

method testBasics(){
  var a:= EventSet.empty();
  var b:=a.add();
  var c:=a.add();
  var d:=b.merge(c);
  assert b.isConcurrent(c);
  assert a.isBefore(b);
  assert b.isBefore(d);
  assert c.isBefore(d);
}

method testEquivalence(){
  var a:=EventSet.empty();
  var ac:=VectorClock.empty();

  var b:=a.add();
  var bc:=ac.inc(1);

  var c:=a.add();
  var cc:=ac.inc(2);

  assert a.isBefore(b)== ac.isBefore(bc);
  assert a.isBefore(c)== ac.isBefore(cc);
  assert b.isBefore(c)== bc.isBefore(cc);
}