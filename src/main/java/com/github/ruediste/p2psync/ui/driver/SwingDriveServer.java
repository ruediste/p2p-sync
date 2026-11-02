package com.github.ruediste.p2psync.ui.driver;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import com.github.ruediste.p2psync.node.NodeFuseFS;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Small loopback HTTP server that inspects and drives the live Swing UI. The
 * feature is called <em>SwingDrive</em>; it is intended for development and
 * automated end-to-end checks only.
 */
public final class SwingDriveServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;
    private static final long EDT_TIMEOUT_SECONDS = 10;

    private final Supplier<? extends Component> rootSupplier;
    private final NodeFuseFS filesystem;
    private final HttpServer server;
    private final ExecutorService executor;

    private SwingDriveServer(Supplier<? extends Component> rootSupplier, NodeFuseFS filesystem, int port)
            throws IOException {
        this.rootSupplier = Objects.requireNonNull(rootSupplier, "rootSupplier");
        this.filesystem = Objects.requireNonNull(filesystem, "filesystem");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public static SwingDriveServer start(int port, Supplier<? extends Component> rootSupplier,
            NodeFuseFS filesystem) throws IOException {
        var result = new SwingDriveServer(rootSupplier, filesystem, port);
        result.server.start();
        return result;
    }

    /** The bound port, useful when the server was started with port {@code 0}. */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = normalizePath(exchange.getRequestURI().getPath());
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (path.equals("/") || path.equals("/help")) {
                sendJson(exchange, 200, Map.of(
                        "name", "SwingDrive",
                        "description", "Development HTTP bridge for the p2p-sync Swing UI",
                        "endpoints", List.of(
                                "GET /state",
                                "GET /ui",
                                "GET /screenshot",
                                "GET /ui/query?role=button&text=Sync%20now",
                                "POST /ui/click",
                                "POST /ui/type",
                                "POST /ui/select",
                                "POST /ui/key",
                                "GET /fs/list?path=/alice",
                                "GET /fs/stat?path=/alice/home",
                                "GET /fs/content?path=/alice/home/file.txt",
                                "PUT /fs/content?path=/alice/home/file.txt",
                                "POST /fs/file?path=/alice/home/new.txt",
                                "POST /fs/directory?path=/alice/home/new-dir",
                                "POST /fs/move?from=/a&to=/b",
                                "DELETE /fs?path=/a&recursive=true")));
                return;
            }
            if (path.equals("/state")) {
                requireMethod(method, "GET");
                sendJson(exchange, 200, state());
                return;
            }
            if (path.equals("/ui")) {
                sendJson(exchange, 200, tree());
                return;
            }
            if (path.equals("/screenshot")) {
                requireMethod(method, "GET");
                sendBytes(exchange, 200, "image/png", screenshot(query));
                return;
            }
            if (path.equals("/ui/query")) {
                sendJson(exchange, 200, queryUi(query));
                return;
            }
            if (path.equals("/ui/click")) {
                requireMethod(method, "POST");
                var request = readJson(exchange);
                sendJson(exchange, 200, click(request, query));
                return;
            }
            if (path.equals("/ui/type")) {
                requireMethod(method, "POST");
                var request = readJson(exchange);
                sendJson(exchange, 200, type(request, query));
                return;
            }
            if (path.equals("/ui/select")) {
                requireMethod(method, "POST");
                var request = readJson(exchange);
                sendJson(exchange, 200, select(request, query));
                return;
            }
            if (path.equals("/ui/key")) {
                requireMethod(method, "POST");
                var request = readJson(exchange);
                sendJson(exchange, 200, key(request, query));
                return;
            }
            if (path.equals("/fs/list")) {
                requireMethod(method, "GET");
                sendJson(exchange, 200, listFilesystem(query));
                return;
            }
            if (path.equals("/fs/stat")) {
                requireMethod(method, "GET");
                var stat = filesystem.stat(required(query, "path"));
                sendJson(exchange, 200, entryMap(stat));
                return;
            }
            if (path.equals("/fs/content")) {
                if (method.equals("GET")) {
                    byte[] content = filesystem.readFile(required(query, "path"));
                    sendBytes(exchange, 200, "application/octet-stream", content);
                    return;
                }
                if (method.equals("PUT")) {
                    byte[] content = readBody(exchange);
                    String file = required(query, "path");
                    filesystem.writeFile(file, content);
                    sendJson(exchange, 200, Map.of("ok", true, "path", file, "size", content.length));
                    return;
                }
                throw new ApiException(405, "Use GET or PUT for /fs/content");
            }
            if (path.equals("/fs/file")) {
                requireMethod(method, "POST");
                String file = required(query, "path");
                filesystem.createFile(file);
                sendJson(exchange, 200, entryMap(filesystem.stat(file)));
                return;
            }
            if (path.equals("/fs/directory")) {
                requireMethod(method, "POST");
                String directory = required(query, "path");
                filesystem.createDirectory(directory);
                sendJson(exchange, 200, entryMap(filesystem.stat(directory)));
                return;
            }
            if (path.equals("/fs/move")) {
                requireMethod(method, "POST");
                filesystem.move(required(query, "from"), required(query, "to"));
                sendJson(exchange, 200, Map.of("ok", true));
                return;
            }
            if (path.equals("/fs")) {
                requireMethod(method, "DELETE");
                String target = required(query, "path");
                filesystem.delete(target, Boolean.parseBoolean(query.getOrDefault("recursive", "false")));
                sendJson(exchange, 200, Map.of("ok", true, "path", target));
                return;
            }
            throw new ApiException(404, "Unknown endpoint " + path);
        } catch (ApiException error) {
            sendJson(exchange, error.status, Map.of("error", error.getMessage()));
        } catch (NodeFuseFS.FsException error) {
            sendJson(exchange, httpStatus(error.errno()), Map.of(
                    "error", "Filesystem error " + error.errno(),
                    "errno", error.errno()));
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, Map.of("error", error.getMessage() == null ? "Bad request" : error.getMessage()));
        } catch (Throwable error) {
            sendJson(exchange, 500, Map.of("error", error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "internal error" : error.getMessage())));
        } finally {
            exchange.close();
        }
    }

    private static int httpStatus(int errno) {
        if (errno == ru.serce.jnrfuse.ErrorCodes.ENOENT())
            return 404;
        if (errno == ru.serce.jnrfuse.ErrorCodes.EACCES() || errno == ru.serce.jnrfuse.ErrorCodes.EPERM())
            return 403;
        if (errno == ru.serce.jnrfuse.ErrorCodes.EEXIST()
                || errno == ru.serce.jnrfuse.ErrorCodes.ENOTEMPTY())
            return 409;
        if (errno == ru.serce.jnrfuse.ErrorCodes.EIO())
            return 500;
        return 400;
    }

    private Component root() {
        Component root = rootSupplier.get();
        if (root == null)
            throw new ApiException(503, "The application window is not available yet");
        return root;
    }

    private Object tree() {
        Component root = root();
        return onEdt(() -> SwingUiInspector.snapshot(root));
    }

    private byte[] screenshot(Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Component target = root;
            Map<String, Object> selector = selector(Map.of(), query);
            if (!selector.isEmpty()) {
                var matches = SwingUiInspector.matches(root, selector);
                if (matches.isEmpty())
                    throw new ApiException(404, "No component matches " + selector);
                if (matches.size() > 1)
                    throw new ApiException(409, "Selector matches " + matches.size()
                            + " components; add matchIndex or a more specific selector: "
                            + matches.stream().map(SwingUiInspector.Match::path).toList());
                target = matches.get(0).component();
            }
            int width = target.getWidth();
            int height = target.getHeight();
            if (width <= 0 || height <= 0) {
                target.doLayout();
                width = Math.max(width, target.getPreferredSize().width);
                height = Math.max(height, target.getPreferredSize().height);
            }
            if (width <= 0 || height <= 0)
                throw new ApiException(422, "Component has no drawable size");
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                Color background = target.getBackground();
                graphics.setColor(background == null ? Color.WHITE : background);
                graphics.fillRect(0, 0, width, height);
                target.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output))
                throw new ApiException(500, "No PNG image writer is available");
            return output.toByteArray();
        });
    }

    private Object state() {
        Component root = root();
        return onEdt(() -> {
            var result = new LinkedHashMap<String, Object>();
            var window = new LinkedHashMap<String, Object>();
            window.put("role", SwingUiInspector.role(root));
            window.put("showing", root.isShowing());
            window.put("visible", root.isVisible());
            if (root instanceof JFrame frame) {
                window.put("title", frame.getTitle());
                window.put("width", frame.getWidth());
                window.put("height", frame.getHeight());
            }
            result.put("window", window);

            findNamed(root, "status").map(SwingUiInspector::componentText).ifPresent(text -> result.put("status", text));
            findNamed(root, "pending").map(SwingUiInspector::componentText).ifPresent(text -> result.put("pending", text));
            findNamed(root, "peer").map(SwingUiInspector::componentText).ifPresent(text -> result.put("peer", text));
            findNamed(root, "users").ifPresent(component -> result.put("users", listState(component)));
            findNamed(root, "workspace-sections")
                    .ifPresent(component -> result.put("workspaceSections", listState(component)));
            findNamed(root, "sync-now")
                    .ifPresent(component -> result.put("syncEnabled", component.isEnabled()));
            return result;
        });
    }

    private static Map<String, Object> listState(Component component) {
        var result = new LinkedHashMap<String, Object>();
        if (component instanceof JList<?> list) {
            result.put("count", list.getModel().getSize());
            result.put("selectedIndex", list.getSelectedIndex());
            var items = new ArrayList<String>();
            for (int index = 0; index < list.getModel().getSize(); index++)
                items.add(SwingUiInspector.listItemText(list, index));
            result.put("items", items);
            if (list.getSelectedIndex() >= 0 && list.getSelectedIndex() < items.size())
                result.put("selectedText", items.get(list.getSelectedIndex()));
        } else {
            result.put("text", SwingUiInspector.componentText(component));
        }
        return result;
    }

    private static java.util.Optional<Component> findNamed(Component root, String name) {
        if (name.equals(root.getName()))
            return java.util.Optional.of(root);
        if (root instanceof java.awt.Container container)
            for (Component child : container.getComponents()) {
                var match = findNamed(child, name);
                if (match.isPresent())
                    return match;
            }
        return java.util.Optional.empty();
    }

    private Object queryUi(Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Map<String, Object> selector = selector(Map.of(), query);
            List<SwingUiInspector.Match> matches = SwingUiInspector.matches(root, selector);
            var described = new ArrayList<Map<String, Object>>();
            for (var match : matches)
                described.add(SwingUiInspector.describe(match.component(), match.path()));
            return Map.of("count", matches.size(), "matches", described);
        });
    }

    private Object click(Map<String, Object> request, Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Map<String, Object> selector = selector(request, query);
            SwingUiInspector.Match match = uniqueActionable(root, selector);
            Component component = match.component();
            if (!(component instanceof AbstractButton button))
                throw new ApiException(400, "Component " + match.path() + " is a "
                        + SwingUiInspector.role(component) + ", not a button");
            button.doClick();
            return actionResult(match, 1);
        });
    }

    private Object type(Map<String, Object> request, Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Map<String, Object> selector = selector(request, query);
            SwingUiInspector.Match match = uniqueActionable(root, selector);
            Component component = match.component();
            if (!(component instanceof JTextComponent text))
                throw new ApiException(400, "Component " + match.path() + " is a "
                        + SwingUiInspector.role(component) + ", not a text component");
            String value = actionValue(request, query, "value");
            if (value == null)
                throw new ApiException(400, "Missing 'value'");
            boolean clear = actionBoolean(request, query, "clear", true);
            if (clear)
                text.setText(value);
            else
                text.replaceSelection(value);
            boolean submit = actionBoolean(request, query, "submit", false);
            if (submit) {
                if (text instanceof JTextField field)
                    field.postActionEvent();
                else
                    dispatchKey(text, KeyEvent.VK_ENTER, 0);
            }
            return actionResult(match, 1);
        });
    }

    private Object select(Map<String, Object> request, Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Map<String, Object> selector = selector(request, query);
            SwingUiInspector.Match match = uniqueActionable(root, selector);
            Component component = match.component();
            Integer requestedIndex = actionInteger(request, query, "selectIndex", null);
            String requestedValue = actionValue(request, query, "value");
            if (component instanceof JList<?> list) {
                int index = requestedIndex != null ? requestedIndex : findListIndex(list, requestedValue);
                validateIndex(index, list.getModel().getSize());
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
            } else if (component instanceof JComboBox<?> combo) {
                int index = requestedIndex != null ? requestedIndex : findComboIndex(combo, requestedValue);
                validateIndex(index, combo.getItemCount());
                combo.setSelectedIndex(index);
            } else if (component instanceof JTable table) {
                int row = requestedIndex != null ? requestedIndex : findTableRow(table, requestedValue);
                validateIndex(row, table.getRowCount());
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
            } else if (component instanceof JTabbedPane tabs) {
                int index = requestedIndex != null ? requestedIndex : findTabIndex(tabs, requestedValue);
                validateIndex(index, tabs.getTabCount());
                tabs.setSelectedIndex(index);
            } else {
                throw new ApiException(400, "Component " + match.path() + " is a "
                        + SwingUiInspector.role(component) + ", not selectable");
            }
            return actionResult(match, 1);
        });
    }

    private Object key(Map<String, Object> request, Map<String, String> query) {
        Component root = root();
        return onEdt(() -> {
            Component target;
            if (hasSelector(request, query)) {
                target = uniqueActionable(root, selector(request, query)).component();
            } else {
                target = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (target == null)
                    throw new ApiException(409, "No component has focus and no selector was given");
            }
            String keyName = actionValue(request, query, "key");
            if (keyName == null)
                throw new ApiException(400, "Missing 'key'");
            int keyCode = keyCode(keyName);
            int modifiers = modifiers(actionValue(request, query, "modifiers"));
            dispatchKey(target, keyCode, modifiers);
            return Map.of("ok", true, "key", keyName, "target", SwingUiInspector.role(target));
        });
    }

    private static void dispatchKey(Component target, int keyCode, int modifiers) {
        long when = System.currentTimeMillis();
        target.requestFocusInWindow();
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, when, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, when, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static int keyCode(String name) {
        try {
            return Integer.parseInt(name.trim());
        } catch (NumberFormatException ignored) {
            String field = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (!field.startsWith("VK_"))
                field = "VK_" + field;
            try {
                return KeyEvent.class.getField(field).getInt(null);
            } catch (ReflectiveOperationException error) {
                throw new ApiException(400, "Unknown key '" + name + "'");
            }
        }
    }

    private static int modifiers(String names) {
        if (names == null || names.isBlank())
            return 0;
        int result = 0;
        for (String part : names.toUpperCase(Locale.ROOT).split("[,\\s+]+")) {
            if (part.isBlank())
                continue;
            result |= switch (part) {
                case "SHIFT" -> KeyEvent.SHIFT_DOWN_MASK;
                case "CTRL", "CONTROL" -> KeyEvent.CTRL_DOWN_MASK;
                case "ALT" -> KeyEvent.ALT_DOWN_MASK;
                case "META" -> KeyEvent.META_DOWN_MASK;
                case "ALT_GRAPH", "ALTGRAPH" -> KeyEvent.ALT_GRAPH_DOWN_MASK;
                default -> throw new ApiException(400, "Unknown modifier '" + part + "'");
            };
        }
        return result;
    }

    private static void validateIndex(int index, int size) {
        if (index < 0 || index >= size)
            throw new ApiException(400, "Index " + index + " is outside 0.." + Math.max(0, size - 1));
    }

    private static int findListIndex(JList<?> list, String requestedValue) {
        if (requestedValue == null)
            throw new ApiException(400, "Provide 'selectIndex' or 'value'");
        int contains = -1;
        for (int index = 0; index < list.getModel().getSize(); index++) {
            String text = SwingUiInspector.listItemText(list, index);
            if (text != null && text.equalsIgnoreCase(requestedValue))
                return index;
            if (contains < 0 && text != null && text.toLowerCase(Locale.ROOT)
                    .contains(requestedValue.toLowerCase(Locale.ROOT)))
                contains = index;
        }
        if (contains < 0)
            throw new ApiException(404, "No list item matches '" + requestedValue + "'");
        return contains;
    }

    private static int findComboIndex(JComboBox<?> combo, String requestedValue) {
        if (requestedValue == null)
            throw new ApiException(400, "Provide 'selectIndex' or 'value'");
        for (int index = 0; index < combo.getItemCount(); index++)
            if (SwingUiInspector.comboItemText(combo, index).equalsIgnoreCase(requestedValue))
                return index;
        throw new ApiException(404, "No combo-box item matches '" + requestedValue + "'");
    }

    private static int findTableRow(JTable table, String requestedValue) {
        if (requestedValue == null)
            throw new ApiException(400, "Provide 'selectIndex' or 'value'");
        for (int row = 0; row < table.getRowCount(); row++)
            for (int column = 0; column < table.getColumnCount(); column++)
                if (String.valueOf(table.getValueAt(row, column)).equalsIgnoreCase(requestedValue))
                    return row;
        throw new ApiException(404, "No table row matches '" + requestedValue + "'");
    }

    private static int findTabIndex(JTabbedPane tabs, String requestedValue) {
        if (requestedValue == null)
            throw new ApiException(400, "Provide 'selectIndex' or 'value'");
        for (int index = 0; index < tabs.getTabCount(); index++)
            if (tabs.getTitleAt(index).equalsIgnoreCase(requestedValue))
                return index;
        throw new ApiException(404, "No tab matches '" + requestedValue + "'");
    }

    private static SwingUiInspector.Match uniqueActionable(Component root, Map<String, Object> selector) {
        List<SwingUiInspector.Match> matches = SwingUiInspector.matches(root, selector);
        if (matches.isEmpty())
            throw new ApiException(404, "No component matches " + selector);
        List<SwingUiInspector.Match> actionable = matches.stream()
                .filter(match -> SwingUiInspector.isActionable(root, match))
                .toList();
        if (actionable.isEmpty())
            throw new ApiException(409, "All " + matches.size()
                    + " matching components are hidden or disabled; use /ui/query with the same selector to inspect them");
        if (actionable.size() > 1)
            throw new ApiException(409, "Selector matches " + actionable.size()
                    + " actionable components; add matchIndex or a more specific selector: "
                    + actionable.stream().map(match -> match.path() + " (" + SwingUiInspector.role(match.component())
                            + ")").toList());
        return actionable.get(0);
    }

    private static Map<String, Object> actionResult(SwingUiInspector.Match match, int matched) {
        var result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("id", match.path());
        result.put("role", SwingUiInspector.role(match.component()));
        result.put("class", match.component().getClass().getName());
        String text = SwingUiInspector.componentText(match.component());
        if (text != null)
            result.put("text", text);
        result.put("matched", matched);
        return result;
    }

    private Object listFilesystem(Map<String, String> query) {
        String path = required(query, "path");
        NodeFuseFS.FsEntry stat = filesystem.stat(path);
        var result = new LinkedHashMap<String, Object>();
        result.put("path", path);
        result.put("kind", stat.directory() ? "directory" : "file");
        result.put("size", stat.size());
        if (stat.directory()) {
            var entries = new ArrayList<Map<String, Object>>();
            for (var entry : filesystem.list(path))
                entries.add(entryMap(entry));
            result.put("entries", entries);
        }
        return result;
    }

    private static Map<String, Object> entryMap(NodeFuseFS.FsEntry entry) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", entry.name());
        result.put("kind", entry.directory() ? "directory" : "file");
        result.put("size", entry.size());
        return result;
    }

    private static boolean hasSelector(Map<String, Object> request, Map<String, String> query) {
        if (request.get("selector") != null)
            return true;
        for (String key : List.of("id", "role", "name", "text", "exactText", "matchIndex"))
            if (request.containsKey(key) || query.containsKey(key))
                return true;
        return false;
    }

    private static Map<String, Object> selector(Map<String, Object> request, Map<String, String> query) {
        var selector = new LinkedHashMap<String, Object>();
        Object nested = request.get("selector");
        if (nested instanceof Map<?, ?> map) {
            map.forEach((key, value) -> selector.put(String.valueOf(key), value));
        } else if (nested instanceof String id) {
            selector.put("id", id);
        } else if (nested != null) {
            throw new ApiException(400, "'selector' must be an object or an id string");
        } else {
            for (String key : List.of("id", "role", "name", "text", "exactText", "index"))
                if (request.containsKey(key))
                    selector.put(key, request.get(key));
        }
        for (String key : List.of("id", "role", "name", "text", "exactText"))
            if (query.containsKey(key))
                selector.put(key, query.get(key));
        if (request.containsKey("matchIndex"))
            selector.put("index", request.get("matchIndex"));
        if (query.containsKey("matchIndex"))
            selector.put("index", query.get("matchIndex"));
        return selector;
    }

    private static String actionValue(Map<String, Object> request, Map<String, String> query, String key) {
        Object value = request.get(key);
        if (value == null)
            value = query.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer actionInteger(Map<String, Object> request, Map<String, String> query, String key,
            Integer fallback) {
        Object value = request.get(key);
        if (value == null)
            value = query.get(key);
        if (value == null)
            return fallback;
        if (value instanceof Number number)
            return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new ApiException(400, "'" + key + "' must be an integer");
        }
    }

    private static boolean actionBoolean(Map<String, Object> request, Map<String, String> query, String key,
            boolean fallback) {
        Object value = request.get(key);
        if (value == null)
            value = query.get(key);
        if (value == null)
            return fallback;
        if (value instanceof Boolean flag)
            return flag;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        byte[] body = readBody(exchange);
        if (body.length == 0)
            return Map.of();
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            Map<String, Object> form = new LinkedHashMap<>();
            parseQuery(text).forEach(form::put);
            return form;
        }
        if (text.isEmpty())
            return Map.of();
        if (!text.startsWith("{"))
            throw new ApiException(400, "Request body must be a JSON object");
        final Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException error) {
            throw new ApiException(400, "Invalid JSON: " + error.getMessage());
        }
        return Json.object(parsed);
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        long declared = -1;
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null) {
            try {
                declared = Long.parseLong(length);
            } catch (NumberFormatException ignored) {
            }
        }
        if (declared > MAX_BODY_BYTES)
            throw new ApiException(413, "Request body exceeds " + MAX_BODY_BYTES + " bytes");
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readAllBytes();
            if (body.length > MAX_BODY_BYTES)
                throw new ApiException(413, "Request body exceeds " + MAX_BODY_BYTES + " bytes");
            return body;
        }
    }

    private static String required(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isEmpty())
            throw new ApiException(400, "Missing query parameter '" + key + "'");
        return value;
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equals(actual))
            throw new ApiException(405, "Use " + expected);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty())
            return "/";
        String result = path;
        while (result.length() > 1 && result.endsWith("/"))
            result = result.substring(0, result.length() - 1);
        return result;
    }

    private static Map<String, String> parseQuery(String raw) {
        var result = new LinkedHashMap<String, String>();
        if (raw == null || raw.isBlank())
            return result;
        for (String part : raw.split("&")) {
            if (part.isEmpty())
                continue;
            int separator = part.indexOf('=');
            String key = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        try {
            return task.get(EDT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "Interrupted while waiting for the Swing event thread");
        } catch (TimeoutException error) {
            throw new ApiException(504, "Timed out waiting for the Swing event thread");
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime)
                throw runtime;
            if (cause instanceof Error fatal)
                throw fatal;
            throw new ApiException(500, String.valueOf(cause));
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8",
                Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static final class ApiException extends RuntimeException {
        private final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
