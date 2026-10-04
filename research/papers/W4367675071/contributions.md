# W4367675071 — AMC: Trustworthy and Explorable CRDT Applications with the Automerge Model Checker

(paper.md in this directory). Automerge-adjacent experience paper.

## What it teaches (practical systems experience)

Documents real-world failure modes of a production CRDT library (Automerge):
despite fuzzing (libFuzzer/AFL) and thorough test suites, complex bugs persist;
application developers report writes disappearing, peer-sync issues, and
persistence problems (references to actual Automerge GitHub issues, including
one filed by Kleppmann on concurrently created objects under the same key).

- **AMC** is a model checker that explores the *high-level* behavior of a CRDT
  library and apps built on it (not just serialization parsing): arbitrary
  concurrent scenarios, checking convergence and application-observable
  behavior, in an explorable UI.
- Position in the ecosystem: Automerge and Yjs are the mainstream general
  CRDT cores with sync + persistence; tree CRDTs and filesystems cited as
  applications.

## Relation to the topic

- Engineering lesson for our design: convergence of the *algorithm* does not
  guarantee correct behavior of the *implementation*; randomized differential
  testing and model checking are the practical verification tools for a CRDT
  filesystem's tree/move layer.
- The cited issue "behaviour of concurrently created objects under the same
  key" is precisely the duplicate-name / duplicate-object conflict our topic
  must resolve (keep-both + disambiguation).
