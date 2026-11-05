# SwingDrive development server

SwingDrive is a small HTTP server, built on the JDK `HttpServer`, that exposes
the live Swing UI for inspection and automation. It is a development tool for
driving the desktop application similarly to Playwright: read the component
tree, find components by role and text, then send clicks, text, selections, and
key events directly to them. It also exposes the combined file view that the
FUSE mount would show, so scenarios can be exercised without FUSE or `/dev/fuse`.

SwingDrive binds to the loopback interface only and is intended for local
development and end-to-end checks. It is not an authentication boundary.

## Starting

Run Maven commands below from the `p2p-sync` module directory.

Options may appear before or after the positional arguments:

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
  -Dexec.args="--no-mount --drive 9222 /absolute/path/node.db"
```

- `--no-mount` skips mounting the FUSE filesystem. The `NodeFuseFS` instance is
  still created and used by SwingDrive, so `/fs/...` behaves like the mounted
  combined root view.
- `--drive PORT` or `--drive=PORT` starts SwingDrive on `PORT`.
- `--drive` without a port uses `9222`.
- `--drive-port PORT` is equivalent to `--drive PORT`.
- Port `0` asks the operating system for a random free port; the startup line
  prints the actual URL:
  `SwingDrive: http://127.0.0.1:9222/`.

The window appears as usual. Requests made before the window exists return
`503` with an error body.

## UI model

`GET /ui` returns the component tree rooted at the application window. Each node
contains:

- `id`: stable path for the current tree, such as `0/0/1/2`
- `role`: normalized Swing role (`button`, `textbox`, `textarea`, `password`,
  `label`, `list`, `table`, `combobox`, `tabbedpane`, `checkbox`, `radio`,
  `window`, `panel`, ...)
- `class`: Java class name
- `name`: `Component.getName()` when set
- `text`: label, button text, field content, window title, and similar values
- `visible`, `showing`, `enabled`, `bounds`
- entity state where applicable: `selected`, `editable`,
  `itemCount`, `items`, `selectedIndex`, `selectedText`, `rowCount`,
  `columns`, `rows`, `tabs`
- `children`, recursively

Password fields are reported like other text components: their current text is
included and can be set through `/ui/type`. This is why SwingDrive must stay
bound to the loopback interface and should only be enabled during development.

The main window names these components for convenient selectors:

| Name | Component |
| --- | --- |
| `users` | user list |
| `workspace-sections` | workspace section list |
| `status` | status bar label |
| `pending` | pending-operation label |
| `peer` | peer ID label |
| `sync-now` | "Sync now" button |

`GET /state` is a compact summary of the same information: window title and
size, status/pending/peer text, users, workspace sections, whether the sync
button is enabled, and (when SwingDrive was started with a node) the full
`peerId` and `networkPaused` flag.

## Peer identity and network control

`GET /peer` returns the libp2p transport `PeerId` (base58 multihash) and the
current networking state. The application `NodeId` is a separate 32-byte
domain-separated hash used in operation identities:

```json
{"peerId":"12D3KooW...","started":true,"paused":false,"listenAddresses":["/ip4/127.0.0.1/tcp/40001"]}
```

Pause and resume remote connectivity with `POST /network/pause` and
`POST /network/resume`. Pausing closes the listening sockets, stops mDNS
discovery, and drops open connections; the node identity and configured peer
addresses are kept. Resuming listens again on the original address and restores
any known peer addresses:

```bash
curl -s http://127.0.0.1:9222/peer
curl -s -X POST http://127.0.0.1:9222/network/pause
curl -s -X POST http://127.0.0.1:9222/network/resume
```

Both actions return the same shape as `GET /peer` plus `"ok":true`. These
endpoints return `501` when SwingDrive was started without a node, and `409`
when remote networking has not been started by the application.

## Selectors

Selectors are JSON objects (or query parameters) with these optional fields:

| Field | Meaning |
| --- | --- |
| `id` | exact component path from `/ui`, for example `0/0/1` |
| `role` | normalized role name; class names such as `JButton` also work |
| `name` | exact `Component.getName()` |
| `text` | case-insensitive substring of the component text |
| `exactText` | case-sensitive exact component text |
| `matchIndex` | 0-based match when several components match (inside a JSON `selector` object use `index`) |

Selectors are evaluated against the whole tree. Actions require a unique,
enabled, visible component (and a showing component once the window is showing).
A selector that matches several actionable components returns `409` with the
candidate paths; add `matchIndex` or make the selector more specific. Hidden
CardLayout pages are still part of the tree, so prefer role plus text/name over
raw paths when the window changes.

## Endpoints

### Inspection

```bash
curl -s http://127.0.0.1:9222/state
curl -s http://127.0.0.1:9222/ui
curl -s 'http://127.0.0.1:9222/ui/query?role=button&text=Sync%20now'
curl -s 'http://127.0.0.1:9222/ui/query?name=users'
```

`/ui/query` accepts the selector fields as query parameters and returns
`{"count":..., "matches":[...]}`.

### Screenshots

`GET /screenshot` renders the application window to PNG. Add any selector field
to capture one component instead:

```bash
curl -s http://127.0.0.1:9222/screenshot -o window.png
curl -s 'http://127.0.0.1:9222/screenshot?name=users' -o users.png
curl -s 'http://127.0.0.1:9222/screenshot?role=button&text=Sync%20now' -o sync.png
```

Screenshots are rendered on the Swing event thread with `printAll`, so they work
for the live window and for components that are not currently shown (as long as
they have a size). A selector that matches several components returns `409`;
add `matchIndex` to choose one. If the selected component has no drawable size,
the server returns `422`.

### Interaction

Events are dispatched on the Swing event thread, directly to the selected
component. No synthetic OS mouse movement is required.

Click a button:

```bash
curl -s -X POST http://127.0.0.1:9222/ui/click \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"role":"button","text":"Sync now"}}'
```

Type into a text field or text area. `clear` defaults to `true`; `submit`
fires the field's action (Enter) and defaults to `false`:

```bash
curl -s -X POST http://127.0.0.1:9222/ui/type \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"role":"textbox"},"value":"Ada","clear":true,"submit":true}'
```

Select a list, combo box, table row, or tab by zero-based item index or by the
rendered item text:

```bash
curl -s -X POST http://127.0.0.1:9222/ui/select \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"name":"users"},"value":"alice"}'

curl -s -X POST http://127.0.0.1:9222/ui/select \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"name":"workspace-sections"},"selectIndex":1}'
```

Send a key event to a selected component or to the current focus owner. Key
names follow `java.awt.event.KeyEvent` constants (`ENTER`, `TAB`, `ESCAPE`,
`DOWN`, `A`, ...); modifiers are comma/space separated (`CTRL`, `SHIFT`,
`ALT`, `META`, `ALT_GRAPH`):

```bash
curl -s -X POST http://127.0.0.1:9222/ui/key \
  -H 'Content-Type: application/json' \
  -d '{"key":"ENTER"}'
```

Every interaction returns a small JSON result such as:

```json
{"ok":true,"id":"0/1/2","role":"button","class":"javax.swing.JButton","text":"Sync now","matched":1}
```

Query parameters work as a fallback for selector-only requests, for example
`/ui/click?role=button&text=Sync%20now`; action values such as `value`,
`clear`, `submit`, and `selectIndex` can also be supplied as query parameters.
Form-encoded POST bodies are accepted as well.

## Filesystem access

The filesystem endpoints operate on the combined root view exposed by
`NodeFuseFS`: `/<user>/<root>/<path>`, where roots are `home` and accepted shared
folders by label. These endpoints work whether or not the FUSE mount is active.
Paths must be URL-encoded when they contain spaces or special characters.

List a directory:

```bash
curl -s 'http://127.0.0.1:9222/fs/list?path=/alice'
curl -s 'http://127.0.0.1:9222/fs/list?path=/alice/home'
```

Read and write raw file content:

```bash
curl -s 'http://127.0.0.1:9222/fs/content?path=/alice/home/notes.txt'

curl -s -X PUT 'http://127.0.0.1:9222/fs/content?path=/alice/home/notes.txt' \
  --data-binary 'hello from curl'
```

Create, move, and delete entries:

```bash
curl -s -X POST 'http://127.0.0.1:9222/fs/directory?path=/alice/home/docs'
curl -s -X POST 'http://127.0.0.1:9222/fs/file?path=/alice/home/docs/empty.txt'
curl -s -X POST 'http://127.0.0.1:9222/fs/move?from=/alice/home/notes.txt&to=/alice/home/docs/notes.txt'
curl -s -X DELETE 'http://127.0.0.1:9222/fs?path=/alice/home/docs&recursive=true'
```

Stat one path:

```bash
curl -s 'http://127.0.0.1:9222/fs/stat?path=/alice/home/notes.txt'
```

Response bodies describe `kind` (`directory` or `file`) and `size`. Filesystem
failures use the FUSE errno and a matching HTTP status: `404` for `ENOENT`,
`409` for `EEXIST`/`ENOTEMPTY`, `403` for permission errors, and `400` for other
invalid requests. Request bodies are limited to 64 MiB.

## Typical end-to-end flow

```bash
# Inspect the window and find the Add user button
curl -s 'http://127.0.0.1:9222/ui/query?role=button&text=Add%20user'

# Open the Add user page
curl -s -X POST http://127.0.0.1:9222/ui/click \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"role":"button","text":"Add user"}}'

# Fill the local name and submit
curl -s -X POST http://127.0.0.1:9222/ui/type \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"role":"textbox"},"value":"alice","submit":false}'

curl -s -X POST http://127.0.0.1:9222/ui/click \
  -H 'Content-Type: application/json' \
  -d '{"selector":{"role":"button","text":"Create user"}}'

# Check the result and exercise the file view
curl -s http://127.0.0.1:9222/state
curl -s 'http://127.0.0.1:9222/fs/list?path=/alice/home'
```

# Missing Features
If you find any missing features, feel free to improve and extend the server. 