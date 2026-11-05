# JReact: Concept Documentation & Architecture

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

### Layer 1: Component Model & AOT Around Advice

Components are written as standard Java methods. An Ahead-Of-Time (AOT) build step or annotation processor wraps these methods with a lightweight interceptor ("around advice") that manages the component's lifecycle.

```java
// What the developer writes:
public void CounterComponent() {
    int count = Hook.useState(0);

    Div(style -> style.flexDirection(Column).padding(16), () -> {
        Text("Current count: " + count);
    });
}

```

### Layer 2: Hooks via `ThreadLocal` & Call-Order Indexing

JReact mimics React’s core runtime behavior without complex JavaScript-style closures.

1. **The Cursor Reset:** When a component renders, the AOT wrapper sets the active `Fiber` context and resets the hook cursor index to `0`.
2. **Sequential State Resolution:** Every time `Hook.useState()` is invoked, it reads from the current fiber's state slot array using the incrementing cursor.
3. **The Rules of Hooks:** Because state is mapped strictly by call order, hooks must not be placed inside conditional statements or loops, ensuring deterministic indexing.

```java
public class Hook {
    public static <T> T useState(T initialValue) {
        FiberContext.ThreadContext context = FiberContext.current();
        int index = context.incrementAndGetIndex();
        StateSlot slot = context.fiber.getOrCreateStateSlot(index, initialValue);
        return (T) slot.value;
    }
}

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