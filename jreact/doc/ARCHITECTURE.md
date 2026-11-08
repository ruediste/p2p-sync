# JReact Architecture

This document describes the current implementation, rather than the original
React/Yoga-inspired proposal. JReact is a synchronous Java component runtime with
a pure-Java layout engine and a Swing/Java2D host. It does not use a web view,
rewrite component method bodies, or implement React's concurrent scheduler.

For usage, see [README.md](README.md). For the style contract, see
[PRIMITIVES_AND_CSS.md](PRIMITIVES_AND_CSS.md).

## 1. The Two Trees

The central distinction is between component lifetime and rendered output.

| Concept | Responsibility | Lifetime |
| --- | --- | --- |
| Component | A Java render body that composes primitives and other components | Invoked on every root render while present |
| Fiber | Retained hook slots and child-component identities | Survives renders until its component is removed |
| Node | One primitive's content, styles, geometry, and callbacks | Recreated on every root render |
| RenderRoot | Owns the root fiber, current node tree, effect queue, and invalidation callback | Until the host closes it |
| JReactPanel | Hosts the runtime, layout, painting, input, and native editors | Until the owning Swing UI disposes it |

```mermaid
flowchart LR
    Body[Component render bodies] --> Ui[Ui component and primitive calls]
    Ui --> Fibers[Retained fiber tree]
    Ui --> Nodes[Fresh node tree]
    Fibers --> Hooks[Hook slots]
    Nodes --> Styles[Resolved styles]
    Styles --> Layout[Layout and text measurement]
    Layout --> Paint[Java2D painting and native editors]
```

A component boundary does not introduce a visual container. A component can emit
zero, one, or several primitive nodes. Conversely, a `div` introduces a node but
does not create a fiber or a new hook scope. The two trees therefore do not have
matching shapes.

For example, a `Counter` component nested in an `App` component may emit a `div`
and two children:

```text
Fiber tree                    Node tree
root                          synthetic root DIV
  App                           DIV emitted by Counter
    Counter                       TEXT
                                  DIV used as a button
                                    TEXT
```

Wrapping output in a `div` changes layout, not component identity. Establishing a
component boundary changes hook ownership, not the current node parent.

## 2. Components and Render Context

[Ui.java](src/main/java/com/github/ruediste/jreact/Ui.java) supplies the imperative
Java DSL used during declarative rendering. A component body is a `Runnable` that
calls primitives and establishes nested component boundaries:

```java
Ui.component("Counter", () -> renderCounter());
Ui.component("Row", rowId, () -> renderRow(rowId));
```

The `type` argument is an identity token, not necessarily a Java class. It and any
explicit key must have stable equality and hash codes across renders.

`Ui.CURRENT` is a thread-local `Context` containing:

- The owning `RenderRoot`.
- The active `Fiber`, whose cursors determine hook and child identity.
- The current component's style declaration and enclosing declaration chain.

Entering a component resets its fiber's cursors and installs a new context.
Returning validates its hook usage and reconciles its children. A `finally` block
restores the previous context, including when rendering fails. Calls that require
a render context fail when made outside a component render.

The root separately tracks the current primitive parent. Container primitives
temporarily replace that parent while running their child bodies. Component calls
leave it unchanged, so nested components emit directly into the surrounding
primitive tree.

## 3. Fibers and Reconciliation

[Fiber.java](src/main/java/com/github/ruediste/jreact/Fiber.java) stores the runtime
state for one component boundary:

- `hooks`: retained slots in hook-call order.
- `children`: child fibers indexed by `Identity(type, key)`.
- `seen`: child identities visited in the current render.
- `cursor`: next hook slot.
- `childCursor`: next sibling component-call position.
- `nodeCursor`: next primitive-call position within this component.
- `rendered`: whether the component has completed a render before.

At render entry, cursors reset to zero and `seen` is cleared. Each child component
call advances `childCursor`, including keyed calls. Without an explicit key,
identity uses `Position(siblingIndex)`; with a key, it uses that key. The distinct
`Position` type prevents explicit integer keys from colliding with implicit
positions. A duplicate identity in one parent render is rejected.

Reconciliation looks up or creates the child fiber immediately and executes its
body synchronously. At render completion, unseen children are removed and
disposed. There is no virtual-DOM diff that reuses node objects: reconciliation
retains fibers, while node output is rebuilt.

### Why Both Type and Key?

`type` answers **which component implementation?**; `key` answers **which instance
of that component?** For example:

```java
Ui.component("UserRow", user.id(), () -> renderUser(user));
```

`"UserRow"` identifies the component implementation, while `user.id()` identifies
the row whose state should survive reordering. Keeping both parts prevents state
from being reused by a different implementation with the same instance key:
`("UserRow", 42)` and `("GroupRow", 42)` select different fibers under the same
parent. Changing either part selects a different component lifetime.

Keys only need to be unique among siblings of the same type under the same parent,
not globally. With a null key, sibling component-call position identifies the
instance instead. The runtime trusts the supplied type token; it does not infer
the implementation from the render lambda, and changing only the body does not
change identity.

### Identity Consequences

- The same type and key under the same parent retains state after reordering.
- Changing type, key, or parent starts a different component lifetime.
- Removing and later reintroducing a component creates fresh hook state.
- Unkeyed components follow component-call positions, not labels or primitive
  positions.
- Changing the length of an inline component list can shift unkeyed components
  that follow it. Put variable-length lists in their own component boundary and
  use stable keys for their items.

The same rule applies to helper methods: a method that merely calls `div` does
not isolate child component identities. A helper that calls `Ui.component` does.

## 4. RenderRoot Lifecycle

[RenderRoot.java](src/main/java/com/github/ruediste/jreact/RenderRoot.java) separates
invalidation from execution. Invalidating marks the root dirty and invokes a host
callback; it does not render immediately. The host chooses when to call `render()`.
A root starts dirty, and an explicit `render()` runs even if it is already clean.

One render performs these steps:

1. Verify the creating thread, reject closed roots and reentrant rendering, clear
   queued effects, and reset the dirty flag.
2. Create a synthetic root `DIV` and make it the current primitive parent.
3. Execute the root body and nested components, reusing fibers and emitting nodes.
4. Resolve styles for the newly built node tree.
5. Publish that tree through `tree()`.
6. Execute queued effect callbacks.
7. Clear temporary render bookkeeping in `finally`.

This publication is the runtime's commit boundary. It is not a paint boundary:
effects run before the host's subsequent layout and repaint work. An effect may
invalidate the root again, leaving it dirty after an otherwise successful render.

The root itself does not calculate bounds or paint. It can be driven directly for
headless rendering, or hosted by `JReactPanel`.

### Failure and Disposal

A runtime exception or error leaves the root dirty and propagates to the caller.
If component execution fails before publication, the previous node tree remains
published. If an effect fails after publication, the new tree is already visible
to the host. Hook and fiber bookkeeping is not transactional: failed renders do
not roll back all mutations, and there is no error-boundary recovery mechanism.

Closing a root marks it closed, recursively disposes child fibers, disables state
assignments, and invokes effect cleanup. Cleanup attempts continue after runtime
exceptions, which are collected and rethrown with suppressed failures. Disposal
is not tied to garbage collection; the owning host must close the root or panel.

## 5. Thread Ownership

A `RenderRoot` belongs to the thread that created it. Rendering, viewport changes,
disposal, and changed-value state assignments must occur on that thread.
`JReactPanel` requires Swing's event dispatch thread (EDT), so a mounted UI's
component bodies, effects, and event callbacks normally execute there.

The thread-local context is for associating DSL calls with a component, not for
making roots thread-safe. Background work must hand results back to the EDT with
`SwingUtilities.invokeLater` before changing mounted state. Component bodies
should not block the EDT with network or disk operations.

## 6. Hooks and State

[Hooks.java](src/main/java/com/github/ruediste/jreact/Hooks.java) resolves each hook
against the active fiber's next slot. On the first render, requesting a new slot
creates its value. On later renders, the same slot is reused and its kind is
checked. Requesting an extra slot, changing a slot's kind, or finishing with
unconsumed slots raises a hook-order/count error.

```text
Counter fiber
  hooks[0] = State<Integer>
  hooks[1] = Ref<Connection>
  hooks[2] = Effect

Each render consumes those slots in that same order.
```

This checks slot count and runtime kind, not the semantic identity of individual
calls. Swapping two state hooks of the same kind is not detected. Hooks should be
unconditional within a component; conditional components are allowed because each
component boundary owns an independent hook sequence.

| Hook | Retained value | Evaluation and invalidation |
| --- | --- | --- |
| `useState` / `useStateLazy` | `State<T>` handle | Initializer is consumed on mount; changed-value assignments invalidate the root |
| `useRef` | Mutable `Ref<T>` handle | Assigning `current` does not schedule rendering |
| `useMemo` | Result plus dependencies | Factory executes during render on first use or changed dependencies |
| `useEffect` | Dependencies and cleanup callback | Setup is queued during render and executed after tree publication |

[State.java](src/main/java/com/github/ruediste/jreact/State.java) assigns changed
values immediately after validation. `Objects.equals` suppresses equal-value
assignments. `update(fn)` calculates a replacement from the current value; updates
are not queued React-style updater objects. Assignments through unmounted handles
are ignored. Changed-value assignments during component rendering are rejected;
effects execute outside that component context and may update state.

Memo and effect dependencies use `Arrays.deepEquals`, not reference-only React
dependency semantics. Only the outer dependency array is cloned, so mutable
objects and nested arrays must not be treated as independent historical snapshots.
An empty dependency list means once per mount for effects and a retained result
for memos; there is no separate omitted-dependencies mode that runs every render.

An effect setup returns a cleanup `Runnable` or null. Changed-dependency execution
clears and invokes the previous cleanup before running replacement setup. Unmount
also clears and invokes cleanup. Effects are not Swing layout effects and should
not assume bounds for the newly published tree have already been calculated.

## 7. Nodes and Primitive Identity

[Node.java](src/main/java/com/github/ruediste/jreact/Node.java) is a mutable bridge
between component output and the host's UI processing:

| Data | Used by |
| --- | --- |
| `kind`, `text`, children, icon, and select options | Layout, renderer, and native editors |
| `key` | Cross-render interaction/editor association and repaint comparisons |
| `inline` and enclosing style declarations | Cascade resolution |
| `style` | Resolved layout and paint properties |
| `bounds` and `lines` | Painting, hit testing, and editor placement |
| Hover, active, focus, disabled, and focusability flags | Pseudo-class matching and event handling |
| Click, pointer, key, and change callbacks | Host input dispatch |

Primitive calls create nodes and append them to the current parent's child list.
`DIV` is a flex container. `SPAN` groups shared inline flow without another flex
container. `TEXT` is measured content. `INPUT` and `SELECT` describe native Swing
editors. `ICON` carries a Swing icon to paint. Span creation rejects block
containers and native editors as direct children.

Node identity is `Key(owningFiber, primitiveCallPosition)`, not the node's style ID
or a component's explicit key. Retaining a keyed fiber retains the owner portion
of its primitive keys, but inserting or removing earlier primitives in that same
fiber can shift later positions. There is no independent user-supplied node-key
API; use component boundaries when stable identity matters.

### Why Associate Primitives Across Renders?

A new node object is a fresh description of UI, not necessarily a new UI element.
The host needs continuity even though the component runtime rebuilds node output.
Keys provide that continuity for three purposes:

- **Interaction state:** the panel retains hovered, pressed, and focused keys and
  resolves them against the current tree. For example, a rerender between mouse
  press and release can still identify the same button. State is recomputed on
  current nodes rather than copied wholesale from old node objects.
- **Native editors:** a compatible input/select node with the same key retains
  its existing Swing editor. Replacing the widget every render would needlessly
  disrupt focus, caret, and selection. Controlled value synchronization still
  updates the retained widget and may affect its editing state.
- **Repaint comparisons:** snapshots indexed by key identify changed content,
  styles, or geometry. The panel can repaint both old and new regions, clear
  removed output, and avoid repainting unchanged output merely because its node
  object was recreated.

For example, editing a text field may update component state and rebuild every
node. If the field's fiber and primitive-call position remain stable, its new
node points to the same retained editor while supplying the latest value and
callbacks. If the node disappears or changes to an incompatible editor kind,
the editor is removed or replaced.

This association is not node-object reuse, hook-state storage, or incremental
component execution. It trusts fiber/position identity rather than inferring
semantic correspondence from content. Shifting primitive positions can therefore
associate different output; component boundaries keep those identities stable.

Bounds are border-box rectangles in root-relative logical pixels. Text fragments
store horizontal offsets and baselines relative to the owning node. Nodes are not
Swing components, and holding an old node after a rerender does not give access to
its replacement. Current callbacks and content are taken from the current tree.

## 8. Scoped Styles and Selectors

[Style.java](src/main/java/com/github/ruediste/jreact/Style.java) records explicitly
set properties in a typed map. Defaults are supplied by accessors rather than
inserted into every declaration, so absent inline properties do not erase rule
values. Dimensions use logical pixels; auto width/height and normal line height
use internal `Double.NaN` sentinels. Numeric setters validate their supported
ranges. There is no CSS text parser or automatic property inheritance.

[Styles.java](src/main/java/com/github/ruediste/jreact/Styles.java) associates each
component with a `Declaration` whose anchor is the primitive parent at component
entry. The declaration is shared by the component's emitted nodes, and nested
component contexts extend the enclosing declaration chain. Consequently, rules
may be declared before or after the nodes they style: resolution happens later.

`DIRECT_CHILDREN` rules are eligible when `node.parent == declaration.anchor`.
They can therefore reach immediate output from a nested component at that same
anchor; they do not reach deeper primitive children merely because those children
were emitted by the declaring method. `TRANSITIVE` rules are eligible on every
node carrying that enclosing component declaration. A component does not attach
its new declaration to the preexisting anchor itself.

[Selector.java](src/main/java/com/github/ruediste/jreact/Selector.java) matches node
types, IDs, named classes, pseudo-classes, compounds, and ancestor relationships.
For `left.child(right)` or `left.descendant(right)`, the target matches `right`
and its parent or an ancestor must match `left`. Scope restricts eligible targets;
it does not change the selector's ancestor matching or specificity.

For each eligible target, resolution orders matching rules by:

1. ID count, then class/pseudo-class count, then primitive-type count.
2. Nearest declaring component for equal specificity.
3. Later rule declaration within that component.
4. Inline properties, which always override rule properties.

Each resolution pass builds a replacement resolved `Style`. In the Swing host,
pseudo-class flags are restored from interaction state before resolving again.
Changing hover or focus can therefore change styling and layout without rerunning
component bodies when the root is otherwise clean.

## 9. Layout and Painting

[LayoutEngine.java](src/main/java/com/github/ruediste/jreact/layout/LayoutEngine.java)
consumes the styled node tree through the [LayoutNode](src/main/java/com/github/ruediste/jreact/layout/LayoutNode.java)
interface implemented by [Node.java](src/main/java/com/github/ruediste/jreact/Node.java).
It supports block, flex, and grid layout with the CSS box model: margins,
padding, borders, sizing, positioning, wrapping, alignment, and gaps. The tree
is mutated only through `NodeImpl` for standalone use; the rendered tree keeps
its children in `Node` and exposes them read-only to the engine.

Text and icons participate through the measurement callback. After layout,
`Node.layout(width, height)` copies the computed root-relative geometry into
each node's bounds and lets textual nodes commit their wrapped lines. Text
measurement and wrapping live in [TextLayout.java](src/main/java/com/github/ruediste/jreact/TextLayout.java);
spans use normal wrapping flex layout rather than a separate inline pass.

[Renderer.java](src/main/java/com/github/ruediste/jreact/Renderer.java) consumes the
computed geometry. It paints backgrounds, rounded borders, text, icons, shadows,
slider thumbs, and focus outlines. Shadows use offscreen images and blur filters.
Opacity is multiplied through ancestors for each painted primitive; this is not a
full browser group-compositing implementation. Disposable `Graphics2D` copies
isolate paint state. The renderer does not mutate the node tree or invoke
component bodies, and Swing paints native editor content separately.

## 10. Swing Refresh and Interaction

[JReactPanel.java](src/main/java/com/github/ruediste/jreact/JReactPanel.java) joins
the runtime and visual stages. Root invalidations enqueue one pending refresh via
`SwingUtilities.invokeLater`; repeated updates before that callback runs are
coalesced. Event handlers and explicit calls can also refresh synchronously.

One refresh:

1. Captures previous paint-relevant node snapshots.
2. Supplies viewport dimensions, invalidating the root if they changed.
3. Renders the root only if dirty or no tree exists yet.
4. Reindexes current nodes by key and restores hover, pressed, and focus state.
5. Resolves styles with the updated interaction flags.
6. Calculates bounds and text fragments using font metrics.
7. Synchronizes native editors with node values and geometry.
8. Requests a full repaint on size change, or the union of changed old/new paint
   bounds, including shadow extents.

Paint-region optimization is separate from render optimization. A state update
reruns the root body and every reached component; the runtime does not skip
unchanged component bodies. A refresh of a clean tree can still resolve all
styles and perform layout. `paintComponent` refreshes when necessary and invokes
the renderer; the graphics clip limits the requested paint region.

### Input Dispatch

Hit testing examines current node bounds in reverse indexed traversal order.
Activation walks up from the hit node to the nearest interactive ancestor, so a
text label can activate its enclosing button. This is ancestor target selection,
not a DOM event-bubbling/capture system. Left-button release activates a click
callback only when press and release resolve to the same enabled target.

Hover is reported on the hit node and its ancestors. Active state follows the
pressed key; focus follows the selected focusable key and is cleared if that
target disappears or becomes disabled. Tab/Shift+Tab traverse enabled focusable
nodes, Enter/Space activate click callbacks, and pointer controls can handle keys.
Pointer callbacks receive a clamped horizontal fraction of the target's width.

### Native Editors

`INPUT` and `SELECT` nodes become `JTextField` and `JComboBox` panel children,
respectively. The editor map follows node keys, retaining an editor when its node
identity and kind remain compatible and removing it on unmount. Each refresh
synchronizes the controlled value, options, enabled state, font, bounds, and
accessible name. A synchronization guard prevents programmatic value updates
from recursively firing application change callbacks.

Native editors provide caret, clipboard, selection, input-method, and popup
behavior rather than reproducing it on the canvas. They paint above the canvas,
so applications must not overlap them with painted siblings. Painted controls do
not yet provide a complete screen-reader accessibility tree.

Effects that update state schedule another refresh; they do not trigger a
reentrant root render in this sequence. A synchronous event-handler refresh can
process the update before the already queued callback executes.

## 11. Runtime Component Boundaries

`JReactAgent` uses Byte Buddy advice to wrap eligible methods with a child fiber.
Method signatures provide stable component types, and sibling positions provide
implicit keys. Use explicit keyed `Ui.component(type, key, body)` boundaries for
reorderable collections. `@NoJReactComponent` takes precedence over
`@JReactComponent`; setting `jreact.requireComponentAnnotation` requires explicit
opt-in rather than automatic UpperCamel matching.

## 12. Source Map and Extension Boundaries

| Concern | Implementation |
| --- | --- |
| Render orchestration and lifecycle | [RenderRoot](src/main/java/com/github/ruediste/jreact/RenderRoot.java) |
| Retained identity and hook slots | [Fiber](src/main/java/com/github/ruediste/jreact/Fiber.java) |
| DSL and active component context | [Ui](src/main/java/com/github/ruediste/jreact/Ui.java) |
| Hook APIs and mutable state | [Hooks](src/main/java/com/github/ruediste/jreact/Hooks.java), [State](src/main/java/com/github/ruediste/jreact/State.java) |
| Rendered primitive data | [Node](src/main/java/com/github/ruediste/jreact/Node.java) |
| Typed properties and scoped cascade | [Style](src/main/java/com/github/ruediste/jreact/Style.java), [Styles](src/main/java/com/github/ruediste/jreact/Styles.java), [Selector](src/main/java/com/github/ruediste/jreact/Selector.java) |
| Block, flex, and grid geometry | [LayoutEngine](src/main/java/com/github/ruediste/jreact/layout/LayoutEngine.java), [LayoutNode](src/main/java/com/github/ruediste/jreact/layout/LayoutNode.java), [TextLayout](src/main/java/com/github/ruediste/jreact/TextLayout.java) |
| Java2D painting | [Renderer](src/main/java/com/github/ruediste/jreact/Renderer.java) |
| Swing scheduling, input, and editors | [JReactPanel](src/main/java/com/github/ruediste/jreact/JReactPanel.java) |
| Reusable themed controls | [Components](src/main/java/com/github/ruediste/jreact/components/Components.java), [Theme](src/main/java/com/github/ruediste/jreact/components/Theme.java) |

The component library composes this runtime; it is not an alternative renderer.
When adding a mechanism, keep ownership explicit:

- New hooks retain their slots on fibers, not nodes.
- New painted primitives need node data plus measurement and painting behavior.
- New interactive primitives need host dispatch and focus semantics as well.
- New native-editor kinds need controlled synchronization and lifecycle handling.
- New style properties need defaults, validation, cascade participation, and
  explicit layout or paint consumers.

The current architecture intentionally has no concurrent work scheduler,
automatic dependency tracking, incremental component-render skipping, DOM event
model, complete browser CSS engine, transactional render rollback, or automatic
disposal on panel removal. These are separate features rather than implied
consequences of the React-inspired API.