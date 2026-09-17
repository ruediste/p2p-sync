include "eventSet.dfy"
include "vectorClock.dfy"

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