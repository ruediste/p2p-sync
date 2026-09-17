include "vectorClock.dfy"

class EventSet<T(==)>{
  var clock: VectorClock
  var values: set<T>
  constructor()
    ensures values=={}
    ensures clock==VectorClock.Empty()
  {
    clock:=VectorClock.Empty();
    values:={};
  }

  constructor from(other: EventSet<T>)
    ensures clock==other.clock
    ensures values==other.values
  {
    clock:=other.clock;
    values:=other.values;
  }

  method add(nr: int, event:T)
    requires event !in values
    modifies this
    ensures values==old(values) +{event}
    ensures old(clock).IsBefore(clock)
    ensures old(values)<=values
  {
    clock:= clock.Inc(nr);
    values:=values +{event};
  }

  method merge(other: EventSet<T>)
    modifies this
    ensures old(clock).IsBefore(other.clock) ==> clock.values==other.clock.values
    // ensures values==old(values) + old(other.values)
  {
    clock:= clock.Merge(other.clock);
    values:=values + other.values;
  }
}

method testConcurrentModification(){
  var a:=VectorClock.Empty();
  var b:=a.Inc(1);
  var c:=a.Inc(2);
  var isConcurrent := b.IsConcurrent(c);
  assert isConcurrent;
}



method test() {
  var a:=new EventSet<string>();
  a.add(1,"a");

  var b:=new EventSet<string>.from(a);
  b.add(2,"b");

  var t:= a.clock.IsBefore(b.clock);
  assert t;
}
