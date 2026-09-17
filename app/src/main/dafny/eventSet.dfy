class Event{
  constructor(){}
}

datatype EventSet=EventSet(events: set<Event>){
  static function empty(): EventSet {
    EventSet({})
  }

  method add() returns (r: EventSet)
    ensures isBefore(r)
    ensures fresh(r.events - events)
  {
    var e := new Event();
    r := EventSet(events + {e});
  }

  function merge(other: EventSet): EventSet {
    EventSet(events + other.events)
  }

  predicate isBefore(other: EventSet){
    this!=other && forall e :: e in events ==> e in other.events
  }

  predicate isConcurrent(other: EventSet){
    !this.isBefore(other) && !other.isBefore(this)
  }
}


