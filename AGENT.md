This is a p2p file storage and synchronization project. It is based on IPFS, but does not use an existing library. Instead it ports parts of jvm-libp2p (`upstream/jvm-libp2p`) from Kotlin to java, and extends it with functionality from nabu (`upstream/nabu`). These projects are available for reference in the upstream folder.

The project documentation lives in `upstream/p2p-sync.wiki`.

Read `ImplementationPlan.md` if the user seems to reference to it, for example for implementation of a milestone.

If there is an issue with generating the protobuf java files, a `mvn clean` sometimes helps.

## Initial Exploration of the Code Base

When starting a new task, avoid inspecting the whole codebase. Use a focused exploration instead.

NEVER start a subagent with something along the lines of

```md
Explore the codebase at .... I need a thorough understanding of:

1. The full directory structure under ...
2. Every Java file that exists, with its package and brief description of what it does
3. The `pom.xml` dependencies
4. Look at all existing source files to understand patterns, naming conventions, and the APIs already in place

Return:

- A listing of all packages and files
- For each file, a one-line summary of its purpose
- The key classes and interfaces defined (fully qualified names)
- Any patterns you see (how interfaces are structured, test patterns used, etc.)
- The contents of all existing source files (read them all fully - I need to know every detail)
```

INSTEAD: Be more specific:

```
Explore the codebase at .... I need a thorough understanding of existing code relevant to the topic of ...
Only Explore relevant files.

Return:
- A listing of the most relevant packages and files
- For each relevant file, a one-line summary of its purpose
- The key classes and interfaces defined (fully qualified names)
- Any patterns you see which seem be relevant (how interfaces are structured, test patterns used, etc.)
```

Never scan the whole local maven repository for file contents. Searching just for `.jar`s matching a certain name is ok.
