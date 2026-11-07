This is a p2p file storage and synchronization project. It is based on IPFS, but does not use an existing library.
The Maven parent project is at the repository root, with `p2p-sync` and `jreact` child modules.
The application sources and documentation live in `p2p-sync/src` and `p2p-sync/doc`.

If there is an issue with generating the protobuf java files, a `mvn clean` sometimes helps.

## Tests
When writing a test, add a short description of the test's purpose and how it verifies the expected behavior.

If possible, use a tool to run the java tests via the IDE/language server. If a tool is present, avoid running the maven tests.

Running maven Tests:
- `mvn test` runs the fast unit tests only. Integration tests (`*IT.java` and tests tagged `@Tag("integration")`, currently the slow mDNS loopback tests `MDnsDiscoveryIT` and `MDnsWireUpIT` plus any test method taking longer than one second) are excluded by default.
- `mvn test -Pit` runs only the integration tests. They require working multicast networking and take several seconds each; they skip automatically (JUnit assumptions) when multicast is unavailable.
- When changing test wiring, verify both: `mvn test` and `mvn test -Pit`.

## Initial Exploration of the Code Base
When starting a new task, avoid inspecting the whole codebase. Use a focused exploration instead. Do NOT instruct a sub agent to explore all files (or all java files).
Never scan the whole local maven repository for file contents. Searching just for `.jar`s matching a certain name is ok.

Do not explore the git history unless explicitly instructed to do so. Also do not look at staged changes. The current working directory contains the relevant information. Required information is typically given. Do not explore the `/bin/` directory either, unless there is a specific reason to do so.

## Asking Questions
This is not an automated test. It is fine to ask the user questions.

## DataStore File Versions
This project is in early development. Do not update the file version number, and do not care about backward compatibility of the DataStore files.

## Interacting with the Running Application
By default, the development instance is started with drive port `9222` enabled. This allows UI inspection, interaction and taking screenshots, similar to the capabilities of playwright. See [Swing Drive](p2p-sync/doc/swingdrive.md) for details. This can also be used to start a client and interact with it.