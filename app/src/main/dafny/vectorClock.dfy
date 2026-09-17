function max(a: nat, b: nat): nat {
  if a >= b then a else b
}

datatype VectorClock = VectorClock(values: map<int, nat>)
{
  static function empty(): VectorClock {
    VectorClock(map[])
  }

  function inc(nr: int): VectorClock {
    VectorClock(values[nr := (if nr in values then values[nr] + 1 else 1)])
  }

  predicate isBefore(other: VectorClock) {
    values != other.values
    && (forall k | k in values :: k in other.values && values[k] <= other.values[k])
    && (forall k | k in other.values :: k in values ==> values[k] <= other.values[k])
  }

  predicate isConcurrent(other: VectorClock) {
    !isBefore(other) && !other.isBefore(this)
  }

  function merge(other: VectorClock): (r: VectorClock)
    ensures forall k | k in values :: k in r.values
    ensures forall k | k in values :: r.values[k] >= values[k]
    ensures forall k | k in r.values :: k in values ==> r.values[k] >= values[k]
    ensures (forall k | k in values :: k in other.values && values[k] <= other.values[k]) ==> r.values == other.values
    ensures (forall k | k in other.values :: k in values && other.values[k] <= values[k]) ==> r.values == values
    ensures !r.isBefore(this)
    ensures !r.isBefore(other)
    ensures isBefore(other) ==> r.values == other.values
    ensures other.isBefore(this) ==> r.values == values
  {
    VectorClock( map k | k in values.Keys + other.values.Keys ::
                   if k in values && k in other.values then max(values[k], other.values[k])
                   else if k in values then values[k] else other.values[k])
  }
}

class VectorClockImpl {
  var values: map<int, nat>
  ghost var clock: VectorClock

  ghost predicate Valid()
    reads this`values, this`clock
  {
    clock.values == values
  }

  constructor()
    ensures Valid()
    ensures clock == VectorClock.empty()
    ensures values == map[]
  {
    values := map[];
    clock := VectorClock.empty();
  }

  constructor FromMap(values: map<int, nat>)
    ensures Valid()
    ensures clock == VectorClock(values)
    ensures this.values == values
  {
    this.values := values;
    this.clock := VectorClock(values);
  }

  method inc(nr: int) returns (r: VectorClockImpl)
    requires Valid()
    ensures Valid()
    ensures fresh(r) && r.Valid()
    ensures r.clock == clock.inc(nr)
    ensures this.clock.isBefore(r.clock)
    ensures !r.clock.isBefore(this.clock)
    ensures r.values == values[nr := (if nr in values then values[nr] + 1 else 1)]
  {
    if nr in values {
      r := new VectorClockImpl.FromMap(values[nr := values[nr] + 1]);
    } else {
      r := new VectorClockImpl.FromMap(values[nr := 1]);
    }
  }

  method isBefore(other: VectorClockImpl) returns (r: bool)
    requires Valid() && other.Valid()
    ensures r == clock.isBefore(other.clock)
  {
    var keys := values.Keys;
    ghost var processed: set<int> := {};
    var different := false;

    while keys != {}
      decreases |keys|
      invariant forall x :: x in keys ==> x in values
      invariant forall k :: k in processed ==> k in values
      invariant forall k | k in values.Keys :: k in keys || k in processed
      invariant forall k :: k in processed ==> k in other.values && values[k] <= other.values[k]
      invariant (exists k :: k in processed && k in other.values && values[k] != other.values[k]) ==> different
    {
      var key :| key in keys;
      keys := keys - {key};
      processed := processed + {key};

      if key in other.values {
        if values[key] > other.values[key] {
          return false;
        }
        different := different || values[key] != other.values[key];
      } else {
        return false;
      }
    }

    assert (exists k :: k in values.Keys && k in other.values && values[k] != other.values[k]) ==> different;

    if values != other.values {
      return true;
    }

    return false;
  }

  method isConcurrent(other: VectorClockImpl) returns (r: bool)
    requires Valid() && other.Valid()
    ensures r == clock.isConcurrent(other.clock)
  {
    var a := isBefore(other);
    var b := other.isBefore(this);
    r := !a && !b;
  }

  function MergedValues(other: VectorClockImpl): (r: map<int, nat>)
    reads this`values, other`values
    ensures (forall k | k in values :: k in other.values && values[k] <= other.values[k]) ==> r == other.values
    ensures (forall k | k in other.values :: k in values && other.values[k] <= values[k]) ==> r == values
  {
    map k | k in values.Keys + other.values.Keys ::
      if k in values && k in other.values then max(values[k], other.values[k])
      else if k in values then values[k] else other.values[k]
  }

  method merge(other: VectorClockImpl) returns (r: VectorClockImpl)
    requires Valid() && other.Valid()
    ensures Valid()
    ensures fresh(r) && r.Valid()
    ensures r.clock == clock.merge(other.clock)
    ensures r.values == MergedValues(other)
    ensures forall k | k in values :: r.values[k] >= values[k]
    ensures forall k | k in r.values :: k in values ==> r.values[k] >= values[k]
    ensures !r.clock.isBefore(this.clock)
    ensures !r.clock.isBefore(other.clock)
    ensures clock.isBefore(other.clock) ==> r.values == other.values
    ensures other.clock.isBefore(this.clock) ==> r.values == values
  {
    r := new VectorClockImpl.FromMap(MergedValues(other));
  }
}
