package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitId;

/** Shared inline form for accepting a pending SyncUnit invitation. */
final class InvitationAcceptance {
    private InvitationAcceptance() {
    }

    static JPanel build(Node node, Node.UserStatus user, SyncUnitId id, Consumer<String> status,
            Runnable onAccepted) {
        JPanel panel = Theme.card(Theme.GREEN_SOFT);
        panel.add(Theme.subheading("SyncUnit invitation"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(Theme.muted("Leave the peer ID blank to probe known peers, or enter a node hosting the invitation."));
        panel.add(Box.createVerticalStrut(6));
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JTextField peer = Theme.textField(24);
        row.add(peer, BorderLayout.CENTER);
        JButton join = Theme.primaryButton("Accept invitation");
        join.addActionListener(event -> {
            String peerText = peer.getText().trim();
            Background.run("accept invitation", () -> {
                if (peerText.isEmpty())
                    node.joinSyncUnit(id, user.userId());
                else
                    node.joinSyncUnit(PeerId.fromBase58(peerText), id, user.userId());
                return null;
            }, ignored -> {
                status.accept("Invitation accepted");
                onAccepted.run();
            }, error -> status.accept("Could not accept invitation: " + error.getMessage()));
        });
        row.add(join, BorderLayout.EAST);
        panel.add(row);
        return panel;
    }
}
