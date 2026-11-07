# JReact

JReact is an implemented Java 21+ declarative UI framework backed by Swing and Java2D.
It includes a component runtime, hooks, typed styles and scoped selectors, a pure-Java
Flexbox subset, a reusable control library, and an interactive desktop storybook.

See [Architecture](ARCHITECTURE.md) for the core mechanisms and runtime lifecycle.

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

## Actual Java API

```java
import static com.github.ruediste.jreact.Ui.*;
import com.github.ruediste.jreact.*;
import com.github.ruediste.jreact.components.*;

public class Counter {
    public static void CounterView() {
                State<Integer> count = Hooks.useState(0);
                Components controls = new Components(Theme.light());
                div(style -> style.flexDirection(Style.Direction.COLUMN).padding(16), () -> {
                        text("Count: " + count.get());
                        controls.button("Increment", () -> count.update(value -> value + 1));
                });
        }
}
```

Install the agent before loading component classes with `JReactAgent.install()`, then
mount on the Swing event dispatch thread with `new JReactPanel(() -> Counter.CounterView())`.
Close the panel when its owning window is disposed so effects are cleaned up. It can
also be embedded in an existing Swing UI.

### Runtime Component Boundaries

`JReactAgent.install()` attaches a Byte Buddy agent to the current JVM and transforms
component methods at runtime. By default, methods whose names start with an uppercase
letter are components. Call them normally; the agent gives each invocation a retained
hook and style scope, including overloaded methods with distinct identities.

`@JReactComponent` on a class or method explicitly enables transformation, including
for lowercase methods. `@NoJReactComponent` on a class or method disables it and takes
precedence over opt-ins. Set `-Djreact.requireComponentAnnotation=true` to disable
automatic uppercase matching and require an explicit `@JReactComponent` annotation.

Use explicit `Ui.component` boundaries for keyed dynamic lists and other cases where
the implementation type or instance key should not be inferred from a method call:

```java
component("Counter", () -> Counter.counter());
component("Row", rowId, () -> renderRow(rowId));
```

Components retain state by type and sibling position, or by type and explicit key.
Keys must be unique among siblings of the same type. Use stable keys for reorderable
lists, and put variable-length lists inside their own component boundary. Removing a
component disposes its state and effects; remounting creates fresh state.

### Hooks

- `Hooks.useState(initial)` returns `State<T>` with `get()`, `set(value)`, and `update(fn)`.
- `Hooks.useStateLazy(supplier)` initializes only on mount.
- `Hooks.useEffect(setup, dependencies...)` runs after commit; setup returns a cleanup
    Runnable or null. Cleanup runs before changed-dependency setup and on unmount.
    An empty dependency list means once per mount. Dependencies use `Arrays.deepEquals`;
    treat dependency objects as immutable snapshots.
- `Hooks.useMemo(factory, dependencies...)` caches a value.
- `Hooks.useRef(initial)` retains a mutable `current` field without scheduling a render.

Hook count and kind must be constant on every render of a component. Violations fail
fast. State updates during render and cross-thread updates are rejected. For mounted
Swing panels, marshal background results with `SwingUtilities.invokeLater`. Multiple
updates in one event are coalesced into a scheduled render. Effects may update state.
`RenderRoot` also supports synchronous, headless rendering on its creating thread.

### Styles and Layout

Primitives are lowercase Java DSL methods: `div`, `span`, `text`. Dimensions are
border-box logical pixels. Rows are the default; grow defaults to 0 and shrink to 1.
Text is measured using Java font metrics and wraps at words, breaking oversized words
without splitting Unicode surrogate pairs. Span shares inline flow across text and
nested spans; it does not establish a Flexbox container. It rejects block children.

```java
Styles.rules(Styles.Scope.TRANSITIVE,
        Styles.rule(Selector.className("item").hover(), style -> style.color(java.awt.Color.RED)));
div(style -> style.className("item").padding(12), () -> text("Example"));
```

Rules default to direct children of the declaring component. Transitive rules reach
nested components. IDs outrank classes/pseudo-classes, which outrank primitive types;
ties favor nearer components and later rules. Inline properties always win. Appearance
properties are not automatically inherited. Hover, active, focus, and disabled states
come from Swing interactions. See [Primitives and CSS Subset](PRIMITIVES_AND_CSS.md).

The layout implementation is a small pure-Java engine, not Yoga and not a full browser
CSS implementation. Unsupported CSS features remain unsupported. Rendering evaluates
the root synchronously, reconciles stable fibers, lays out the tree, and repaints changed
node bounds including old bounds and shadows. It does not yet skip unchanged component
bodies. Nested spans use enclosing rectangular paint bounds, not CSS fragment boxes.

### Component Library and Storybook

`components.Components` supplies themed buttons (primary/secondary/danger), checkboxes,
text fields, selects, sliders, tabs, badges, alerts, cards, progress bars, and stack/row
helpers. `Theme.light()` and `Theme.highContrast()` are supplied; custom themes use the
Theme record. Interactive controls expose callbacks, disabled state where applicable,
focus outlines, tooltips, and keyboard operation. Tab/Shift+Tab navigate, Enter/Space
activate, and sliders accept arrows/Home/End. Values are controlled by caller state.

`Ui.input` uses a native JTextField for selection, clipboard, caret and input methods;
`Ui.select` uses a native JComboBox. `Ui.icon` paints a Swing Icon. These extensions have
explicit Swing behavior; no HTML or web view is involved. Native editors are overlaid
on the canvas and should not be overlapped by painted siblings. Painted controls do not
yet expose a full screen-reader AccessibleContext tree. Use a JScrollPane and a suitable
panel preferred size for larger embedded canvases; the sample window is at least 400x760.

The six storybook views cover variants, a counter, an editable profile, feedback and
progress, adjustable layout, and a task board with adding, completion, and filtering.
This is an in-memory demonstration, not a persistent task manager.

## Architecture Background

The following sections describe the original architectural motivation. API snippets use
the implemented signatures; the runtime details and limitations above are authoritative.

See [Primitives and CSS Subset](PRIMITIVES_AND_CSS.md) for the proposed built-in components and layout-style contract.

## 1. Executive Summary

**JReact** is a lightweight, React-inspired, component-driven UI framework built for Java. It bridges the gap between modern declarative UI design and traditional desktop development by bringing React's component model, hooks, and a web-standard layout engine (Flexbox) directly to **Java and Swing**, completely bypassing web technologies while avoiding the boilerplate of traditional desktop toolkits.

---

## 2. Core Design Principles

* **Java-Only Ecosystem:** No web views, no HTML/JS bridges, and no complex native toolchains.
* **React-Inspired Declarative API:** Write components as plain Java methods enhanced with hooks (`useState`, `useEffect`).
* **Zero Heavy Bytecode Rewriting:** Avoids complex local variable manipulation or instruction frame rewriting. Instead, it uses **AOT-generated "around advice"** and a **ThreadLocal hook cursor** to track state based on call order.
* **Web-Standard Layout:** Uses a subset of CSS and Flexbox (powered by a layout engine like Yoga) to calculate positioning, making the mental model identical to web development.
* **Swing-Native Rendering:** Renders directly onto a customized Swing `JPanel` using Java2D (`Graphics2D`), providing seamless integration with existing Java enterprise applications.

---

## 3. High-Level Architecture

```
+------------------------------------------------------------+
| 1. Component Layer (Plain Java Methods + Around Advice)     |
+------------------------------------------------------------+
                              v
+------------------------------------------------------------+
| 2. Fiber Reconciliation & Hook Context (ThreadLocal)       |
+------------------------------------------------------------+
                              v
+------------------------------------------------------------+
| 3. Layout Engine (Flexbox subset / Yoga)                   |
+------------------------------------------------------------+
                              v
+------------------------------------------------------------+
| 4. Swing Rendering Surface (Java2D / Graphics2D JPanel)    |
+------------------------------------------------------------+

```

---

## 4. Architectural Layers

### Layer 1: Runtime Component Advice

Components are ordinary Java methods transformed by the Byte Buddy agent when the
class is loaded or retransformed. `JReactAgent.install()` installs the transformer
before component classes are loaded. Uppercase-leading methods are boundaries by
default; `@JReactComponent` opts in and `@NoJReactComponent` opts out. The advice opens
a child fiber on entry and restores the caller's context on exit, including exceptional
exit.

```java
// What the developer writes:
public void CounterComponent() {
    State<Integer> count = Hooks.useState(0);

    Ui.div(style -> style.flexDirection(Style.Direction.COLUMN).padding(16), () -> {
        Ui.text("Current count: " + count.get());
    });
}

```

### Layer 2: Hooks via `ThreadLocal` & Call-Order Indexing

JReact mimics React’s core runtime behavior without complex JavaScript-style closures.

1. **The Cursor Reset:** When a component method runs, runtime advice sets the active `Fiber` context and resets the hook cursor index to `0`.
2. **Sequential State Resolution:** Every time `Hook.useState()` is invoked, it reads from the current fiber's state slot array using the incrementing cursor.
3. **The Rules of Hooks:** Because state is mapped strictly by call order, hooks must not be placed inside conditional statements or loops, ensuring deterministic indexing.

```java
State<Integer> count = Hooks.useState(0);
// Event handlers update the retained slot and invalidate its root.
count.update(previous -> previous + 1);

```

### Layer 3: Layout Engine (Flexbox Subset)

Instead of manual pixel math or rigid Swing `LayoutManager` classes, JReact computes component bounds using a web-aligned layout model (e.g., Facebook's Yoga engine integrated via Java bindings).

* Supports a subset of CSS layout properties: `display: flex`, `flex-direction`, `justify-content`, `align-items`, `padding`, `margin`, `width`, and `height`.
* During a layout pass, text measurements are calculated via Java's `FontMetrics` to ensure accurate wrapping and box sizing before painting.

### Layer 4: Swing Rendering Surface

The final layout tree is painted onto a high-performance custom Swing component (`JReactPanel`).

* **Immediate-Style Java2D Painting:** The rendering loop traverses the calculated layout bounds and issues hardware-accelerated Java2D draw calls (`g2d.fillRect`, `g2d.drawString`, `g2d.drawRoundRect`).
* **Targeted Repainting:** Only dirty component nodes trigger a localized `repaint()` on the Swing panel, preventing unnecessary desktop flicker and CPU overhead.

---

## 5. Why JReact?

* **Familiarity:** Developers who know React and CSS can build robust desktop tools in Java instantly without learning Swing's notoriously verbose layout managers (`GridBagLayout`, `SpringLayout`).
* **Performance:** Bypassing standard Swing widget instantiation lightweight-renders nodes directly to a canvas, dramatically reducing memory overhead for complex UIs.
* **Maintainability:** Clean separation between state management, layout computation, and Swing paint routines ensures the codebase remains modular and easy to debug.