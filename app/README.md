# p2p-sync app

Java implementation of the p2p-sync peer-to-peer file storage and synchronization protocol. It ports parts of jvm-libp2p from Kotlin to Java and extends it with functionality from nabu (reference sources in `../upstream/`). Project documentation lives in `../upstream/p2p-sync.wiki`.

## Requirements

- Java 21
- Maven

## Building

```bash
mvn compile
```

If protobuf code generation misbehaves, a `mvn clean` usually helps.

## Running tests

### Unit tests

```bash
mvn test
```

Runs the fast unit tests (excludes integration tests). Takes roughly 10 seconds.

### Integration tests

Integration tests are classes named `*IT.java` (currently the slow mDNS loopback tests: `MDnsDiscoveryIT`, `MDnsWireUpIT`). They need working multicast networking and rely on JmDNS's multi-second probe/announce state machine, so they are not part of the default test run.

Run them with the `it` profile:

```bash
mvn test -Pit
```

To run everything (unit + integration tests):

```bash
mvn test test -Pit
```

Integration tests skip gracefully (via JUnit assumptions) when multicast is not available in the environment.
