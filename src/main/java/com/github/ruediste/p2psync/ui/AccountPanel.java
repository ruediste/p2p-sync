package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Node;

/** Account and node settings, rendered inline; file selection uses the platform chooser. */
final class AccountPanel extends JPanel {
    private final Node node;
    private final Path mountPoint;
    private final Consumer<String> status;
    private final Runnable onChanged;
    private Node.UserStatus user;

    private final JLabel userName = Theme.heading("");
    private final JLabel userMeta = Theme.muted("");
    private final JTextField nameField = Theme.textField(22);
    private final JTextField exportPath = Theme.textField(26);
    private final JPasswordField exportPassword = Theme.passwordField(18);
    private final JLabel nodeMeta = Theme.mono("");
    private String defaultExportPathValue;

    AccountPanel(Node node, Node.UserStatus user, Path mountPoint, Consumer<String> status, Runnable onChanged) {
        this.node = node;
        this.user = user;
        this.mountPoint = mountPoint;
        this.status = status;
        this.onChanged = onChanged;
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        render();
    }

    void setUser(Node.UserStatus user) {
        boolean changed = this.user == null || !this.user.userId().equals(user.userId())
                || !this.user.localName().equals(user.localName());
        this.user = user;
        updateLabels();
        if (changed) {
            nameField.setText(user.localName());
            String newDefault = defaultCredentialPath(user).toString();
            if (exportPath.getText().isBlank() || exportPath.getText().equals(defaultExportPathValue))
                exportPath.setText(newDefault);
            defaultExportPathValue = newDefault;
        }
    }

    private Path defaultCredentialPath(Node.UserStatus user) {
        String home = System.getProperty("user.home");
        Path base = home == null || home.isBlank() ? mountPoint.getParent() : Path.of(home);
        if (base == null)
            base = Path.of(".");
        return base.resolve(user.localName() + ".userkey");
    }

    private void render() {
        JPanel content = Theme.column();
        content.setBackground(Theme.PAPER);

        JPanel identity = Theme.card();
        identity.setAlignmentX(LEFT_ALIGNMENT);
        identity.add(userName);
        identity.add(Box.createVerticalStrut(2));
        identity.add(userMeta);
        identity.add(Box.createVerticalStrut(12));
        JPanel rename = Theme.row(6);
        rename.setAlignmentX(LEFT_ALIGNMENT);
        rename.add(Theme.muted("Local name"));
        rename.add(nameField);
        JButton save = Theme.primaryButton("Rename");
        save.addActionListener(event -> rename());
        rename.add(save);
        identity.add(rename);
        identity.add(Box.createVerticalStrut(8));
        JButton open = Theme.secondaryButton("Open root view");
        open.setAlignmentX(LEFT_ALIGNMENT);
        open.addActionListener(event -> openFolder());
        identity.add(open);
        identity.add(Box.createVerticalStrut(4));
        identity.add(Theme.muted("The mounted user directory contains the home tree and labeled shared folders."));
        content.add(identity);
        content.add(Box.createVerticalStrut(12));

        JPanel export = Theme.card();
        export.setAlignmentX(LEFT_ALIGNMENT);
        export.add(Theme.subheading("Protected credential"));
        export.add(Box.createVerticalStrut(4));
        export.add(Theme.muted("Share the credential file and its passphrase separately. "
                + "It may also be copied to the clipboard."));
        export.add(Box.createVerticalStrut(8));
        JPanel exportRow = Theme.row(6);
        exportRow.setAlignmentX(LEFT_ALIGNMENT);
        exportRow.add(Theme.muted("Save to"));
        exportRow.add(exportPath);
        JButton browseExport = Theme.secondaryButton("Browse...");
        browseExport.addActionListener(event -> {
            Path selected = Theme.chooseSaveFile(exportPath, "Save protected credential",
                    exportPath.getText().isBlank() ? user.localName() + ".userkey" : exportPath.getText());
            if (selected != null)
                exportPath.setText(selected.toString());
        });
        exportRow.add(browseExport);
        export.add(exportRow);
        export.add(Box.createVerticalStrut(6));
        JPanel exportActions = Theme.row(6);
        exportActions.setAlignmentX(LEFT_ALIGNMENT);
        exportActions.add(Theme.muted("Passphrase (export / copy)"));
        exportActions.add(exportPassword);
        JButton exportButton = Theme.secondaryButton("Export file");
        exportButton.addActionListener(event -> exportCredential());
        exportActions.add(exportButton);
        export.add(exportActions);
        export.add(Box.createVerticalStrut(6));
        JPanel clipboardRow = Theme.row(6);
        clipboardRow.setAlignmentX(LEFT_ALIGNMENT);
        JButton copyCredential = Theme.secondaryButton("Copy credential to clipboard");
        copyCredential.addActionListener(event -> copyCredential());
        clipboardRow.add(copyCredential);
        export.add(clipboardRow);
        content.add(export);
        content.add(Box.createVerticalStrut(12));

        JPanel publicIdentity = Theme.card();
        publicIdentity.setAlignmentX(LEFT_ALIGNMENT);
        publicIdentity.add(Theme.subheading("Public identity"));
        publicIdentity.add(Box.createVerticalStrut(4));
        publicIdentity.add(Theme.muted("The user ID is safe to share. It is used for invitations and contacts."));
        publicIdentity.add(Box.createVerticalStrut(8));
        JButton copyId = Theme.secondaryButton("Copy user ID");
        copyId.setAlignmentX(LEFT_ALIGNMENT);
        copyId.addActionListener(event -> {
            copyToClipboard(HexFormat.of().formatHex(user.userId().bytes()), "User ID copied");
        });
        publicIdentity.add(copyId);
        content.add(publicIdentity);
        content.add(Box.createVerticalStrut(12));

        JPanel nodeCard = Theme.card();
        nodeCard.setAlignmentX(LEFT_ALIGNMENT);
        nodeCard.add(Theme.subheading("This node"));
        nodeCard.add(Box.createVerticalStrut(4));
        nodeCard.add(nodeMeta);
        nodeCard.add(Box.createVerticalStrut(8));
        JPanel nodeActions = Theme.row(6);
        nodeActions.setAlignmentX(LEFT_ALIGNMENT);
        JButton copyPeer = Theme.secondaryButton("Copy peer ID");
        copyPeer.addActionListener(event -> copyToClipboard(node.peerId.toBase58(), "Peer ID copied"));
        nodeActions.add(copyPeer);
        JButton copyMount = Theme.secondaryButton("Copy mount path");
        copyMount.addActionListener(event -> copyToClipboard(mountPoint.toString(), "Mount path copied"));
        nodeActions.add(copyMount);
        nodeCard.add(nodeActions);
        content.add(nodeCard);

        add(Theme.verticalScroll(content), BorderLayout.CENTER);
        updateLabels();
        nameField.setText(user.localName());
        defaultExportPathValue = defaultCredentialPath(user).toString();
        if (exportPath.getText().isBlank())
            exportPath.setText(defaultExportPathValue);
    }

    private void updateLabels() {
        if (user == null)
            return;
        userName.setText(user.localName());
        userMeta.setText(user.activeNodeCount() + " active node" + (user.activeNodeCount() == 1 ? "" : "s")
                + " · " + user.pendingLocalOperationCount() + " local pending · "
                + user.pendingIncomingOperationCount() + " incoming pending");
        nodeMeta.setText("<html>Peer " + Theme.escape(node.peerId.toBase58())
                + "<br>Mount " + Theme.escape(mountPoint.toString())
                + "<br>User " + HexFormat.of().formatHex(user.userId().bytes()) + "</html>");
    }

    private void rename() {
        PubKey userId = user.userId();
        String newName = nameField.getText().trim();
        Background.run("rename user", () -> node.renameUser(userId, newName), () -> {
            status.accept("User renamed");
            onChanged.run();
        }, error -> status.accept("Could not rename user: " + error.getMessage()));
    }

    private void openFolder() {
        Path folder = mountPoint.resolve(user.localName());
        Background.run("open folder", () -> {
            Desktop.getDesktop().open(folder.toFile());
            return null;
        }, null, error -> status.accept("Could not open folder: " + error.getMessage()));
    }

    private void exportCredential() {
        char[] password = exportPassword.getPassword();
        String pathText = exportPath.getText().trim();
        if (pathText.isEmpty()) {
            Arrays.fill(password, '\0');
            exportPassword.setText("");
            status.accept("Enter a credential file path");
            return;
        }
        Path target;
        try {
            target = Path.of(pathText);
        } catch (RuntimeException error) {
            Arrays.fill(password, '\0');
            exportPassword.setText("");
            status.accept("Could not export credential: " + error.getMessage());
            return;
        }
        PubKey userId = user.userId();
        Runnable clearPassword = () -> {
            Arrays.fill(password, '\0');
            exportPassword.setText("");
        };
        Background.run("export credential", () -> {
            node.exportUserKey(userId, target, password);
            return null;
        }, ignored -> {
            clearPassword.run();
            status.accept("Protected credential exported");
        }, error -> {
            clearPassword.run();
            status.accept("Could not export credential: " + error.getMessage());
        });
    }

    private void copyCredential() {
        char[] password = exportPassword.getPassword();
        PubKey userId = user.userId();
        Runnable clearPassword = () -> {
            Arrays.fill(password, '\0');
            exportPassword.setText("");
        };
        Background.run("copy credential", () -> node.exportUserKeyToClipboard(userId, password), credential -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(credential), null);
                status.accept("Encrypted credential copied to the clipboard");
            } catch (Exception error) {
                status.accept("Could not copy credential: " + error.getMessage());
            } finally {
                clearPassword.run();
            }
        }, error -> {
            clearPassword.run();
            status.accept("Could not copy credential: " + error.getMessage());
        });
    }

    private void copyToClipboard(String text, String message) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            status.accept(message);
        } catch (Exception error) {
            status.accept("Could not copy to clipboard: " + error.getMessage());
        }
    }
}
