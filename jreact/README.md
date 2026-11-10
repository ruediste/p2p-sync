# JReact

JReact is a Java 21+ declarative UI framework backed by Swing and Java2D.
It includes a component runtime, hooks, typed styles and scoped selectors, uses taffy for flex layout, a reusable control library, and an interactive desktop storybook.

See [architecture](doc/src/content/docs/reference/architecture.mdx)for the core mechanisms and runtime lifecycle.

## Build, Test, and Run

From the repository root:

```bash
mvn -pl jreact clean test
mvn -pl jreact -Pcoverage verify
mvn -pl jreact dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency
java -XX:+EnableDynamicAgentLoading -cp 'jreact/target/classes:jreact/target/dependency/*' \
    com.github.ruediste.jreact.storybook.Storybook
```

The last command opens a desktop window, not a web server. In VS Code, run
`com.github.ruediste.jreact.storybook.Storybook` as a Java main class after Maven import.
Coverage reports are written to each module's `target/site/jacoco/index.html`.
Storybook tests save twelve rendered previews to `jreact/target/storybook/`.
