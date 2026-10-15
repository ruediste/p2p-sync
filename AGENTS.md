This is a p2p file storage and synchronization project. It is based on IPFS, but does not use an existing library. Instead it ports parts of jvm-libp2p (`../upstream/jvm-libp2p`) from Kotlin to java, and extends it with functionality from nabu (`../upstream/nabu`). These projects are available for reference in the upstream folder.

The project documentation lives in `doc`.

Read `ImplementationPlan.md` if the user seems to reference to it (it might not exist), for example for implementation of a milestone.

If there is an issue with generating the protobuf java files, a `mvn clean` sometimes helps.

## Tests
When writing a test, add a short description of the test's purpose and how it verifies the expected behavior.

Running:
- `mvn test` runs the fast unit tests only. Integration tests (`*IT.java`, currently the slow mDNS loopback tests `MDnsDiscoveryIT` and `MDnsWireUpIT`) are excluded by default.
- `mvn test -Pit` runs only the integration tests. They require working multicast networking and take several seconds each; they skip automatically (JUnit assumptions) when multicast is unavailable.
- When changing test wiring, verify both: `mvn test` and `mvn test -Pit`.

## Initial Exploration of the Code Base
When starting a new task, avoid inspecting the whole codebase. Use a focused exploration instead. Do NOT instruct a sub agent to explore all files (or all java files).
Never scan the whole local maven repository for file contents. Searching just for `.jar`s matching a certain name is ok.

Do not explore the git history unless explicitly instructed to do so. Also do not look at staged changes. The current working directory contains the relevant information. Required information is typically given. Do not explore the `/bin/` directory either, unless there is a specific reason to do so.

## Asking Questions
This is not an automated test. It is fine to ask the user questions.
