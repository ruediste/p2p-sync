package com.github.ruediste.p2psync.ui.driver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeFuseFS;

class SwingDriveServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Verifies that SwingDrive can inspect the component tree, dispatch actions
     * directly to Swing components, and read/write the unmounted FUSE view.
     */
    @Test
    void inspectsAndDrivesTheUiAndFilesystem() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var filesystem = new NodeFuseFS(node);
            node.createNewUser("alice");
            var clicks = new AtomicInteger();
            var submits = new AtomicInteger();
            var button = new JButton("Do it");
            var field = new JTextField(12);
            var password = new javax.swing.JPasswordField(12);
            var list = new JList<>(new String[] { "alpha", "beta" });
            var panel = new JPanel();
            SwingUtilities.invokeAndWait(() -> {
                button.addActionListener(event -> clicks.incrementAndGet());
                field.setName("name-field");
                field.addActionListener(event -> submits.incrementAndGet());
                password.setText("secret");
                list.setName("choice");
                panel.add(button);
                panel.add(field);
                panel.add(password);
                panel.add(list);
                panel.setSize(320, 180);
                panel.doLayout();
            });

            try (var server = SwingDriveServer.start(0, () -> panel, filesystem)) {
                String base = "http://127.0.0.1:" + server.port();

                assertTrue(get(base + "/ui").body().contains("\"text\":\"Do it\""));
                HttpResponse<byte[]> screenshot = getBytes(base + "/screenshot");
                assertEquals(200, screenshot.statusCode());
                assertEquals("image/png", screenshot.headers().firstValue("Content-Type").orElse(""));
                byte[] png = screenshot.body();
                assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G' },
                        new byte[] { png[0], png[1], png[2], png[3] });
                Map<String, Object> query = object(get(base + "/ui/query?role=button&text=Do%20it"));
                assertEquals(1, ((Number) query.get("count")).intValue());
                assertTrue(get(base + "/ui/query?role=password").body().contains("\"text\":\"secret\""));

                assertEquals(200, post(base + "/ui/click",
                        "{\"selector\":{\"role\":\"button\",\"text\":\"Do it\"}}").statusCode());
                assertEquals(1, clicks.get());
                assertEquals(200, post(base + "/ui/type",
                        "{\"selector\":{\"name\":\"name-field\"},\"value\":\"Ada\",\"submit\":true}").statusCode());
                assertEquals("Ada", field.getText());
                assertEquals(1, submits.get());
                assertEquals(200, post(base + "/ui/select",
                        "{\"selector\":{\"name\":\"choice\"},\"value\":\"beta\"}").statusCode());
                assertEquals(1, list.getSelectedIndex());

                assertEquals(200, put(base + "/fs/content?path=/alice/home/hello.txt", "hi").statusCode());
                assertEquals("hi", get(base + "/fs/content?path=/alice/home/hello.txt").body());
                assertTrue(get(base + "/fs/list?path=/alice/home").body().contains("\"hello.txt\""));
                assertEquals(200, post(base + "/fs/directory?path=/alice/home/docs", "").statusCode());
                assertEquals(200, post(base + "/fs/file?path=/alice/home/docs/note.txt", "").statusCode());
                assertEquals(200, post(base + "/fs/move?from=/alice/home/docs/note.txt&to=/alice/home/docs/moved.txt",
                        "").statusCode());
                assertTrue(get(base + "/fs/list?path=/alice/home/docs").body().contains("\"moved.txt\""));
                assertEquals(200, delete(base + "/fs?path=/alice/home/docs&recursive=true").statusCode());
                assertTrue(get(base + "/fs/list?path=/alice/home").body().contains("\"hello.txt\""));
            }
        }
    }

    private HttpResponse<String> get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> object(HttpResponse<String> response) {
        return Json.object(Json.parse(response.body()));
    }
}
