include "vectorClock.dfy"
method testConcurrentModification(){
  var a:=VectorClock.empty();
  var b:=a.inc(1);
  var c:=a.inc(2);
  var isConcurrent := b.isConcurrent(c);
  assert isConcurrent;
}