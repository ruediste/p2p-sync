import { VectorClock } from "./vector-clock.js";

describe("VectorClock", () => {
  it("should initialize with empty entries", () => {
    const vc = new VectorClock();
    expect(Object.keys(vc.entries).length).toBe(0);
  });

  it("should increment a node version", () => {
    let vc = new VectorClock();
    vc = vc.increment(1);
    expect(vc.entries[1]).toBe(1);
    vc = vc.increment(1);
    expect(vc.entries[1]).toBe(2);
  });

  it("should merge two clocks", () => {
    const vc1 = new VectorClock().increment(1).increment(2);
    const vc2 = new VectorClock().increment(2).increment(2).increment(3);

    const merged = vc1.merge(vc2);
    expect(merged.entries[1]).toBe(1);
    expect(merged.entries[2]).toBe(2);
    expect(merged.entries[3]).toBe(1);
  });

  it("should compare clocks correctly", () => {
    const vc1 = new VectorClock().increment(1);
    const vc2 = new VectorClock().increment(1).increment(1);
    const vc3 = new VectorClock().increment(2);

    expect(vc1.compare(vc2)).toBe("before");
    expect(vc2.compare(vc1)).toBe("after");
    expect(vc1.compare(vc1)).toBe("equal");
    expect(vc2.compare(vc3)).toBe("concurrent");
  });
});
