class Event{
  constructor(){}
}

datatype EventSet=EventSet(events: set<Event>){
  static function Empty(): EventSet {
    EventSet({})
  }

  method Add() returns (r: EventSet)
    ensures IsBefore(r)
    ensures fresh(r.events - events)
  {
    var e := new Event();
    r := EventSet(events + {e});
  }

  function Merge(other: EventSet): EventSet {
    EventSet(events + other.events)
  }

  predicate IsBefore(other: EventSet){
    this!=other && forall e :: e in events ==> e in other.events
  }

  predicate IsConcurrent(other: EventSet){
    !this.IsBefore(other) && !other.IsBefore(this)
  }
}


