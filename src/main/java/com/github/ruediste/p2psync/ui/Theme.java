package com.github.ruediste.p2psync.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;
import com.github.ruediste.p2psync.node.SyncUnitId;
import com.github.ruediste.p2psync.proto.Sync;

/** Shared colors, typography, and small component factories for the desktop UI. */
public final class Theme {
    public static final Color INK = new Color(28, 38, 42);
    public static final Color SIDEBAR = new Color(23, 32, 36);
    public static final Color SIDEBAR_HOVER = new Color(36, 48, 53);
    public static final Color SIDEBAR_ACTIVE = new Color(47, 67, 72);
    public static final Color PAPER = new Color(244, 247, 244);
    public static final Color CARD = Color.WHITE;
    public static final Color LINE = new Color(214, 223, 219);
    public static final Color GREEN = new Color(36, 124, 91);
    public static final Color GREEN_DARK = new Color(27, 99, 72);
    public static final Color GREEN_SOFT = new Color(216, 236, 226);
    public static final Color MUTED = new Color(102, 117, 119);
    public static final Color MUTED_LIGHT = new Color(164, 179, 181);
    public static final Color DANGER = new Color(166, 58, 58);
    public static final Color INCOMING_BUBBLE = Color.WHITE;
    public static final Color OUTGOING_BUBBLE = new Color(214, 236, 224);

    public static final Font TITLE = new Font(Font.SERIF, Font.BOLD, 27);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font SUBHEADING = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE);
        label.setForeground(INK);
        return label;
    }

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(HEADING);
        label.setForeground(INK);
        return label;
    }

    public static JLabel subheading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SUBHEADING);
        label.setForeground(INK);
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setForeground(MUTED);
        return label;
    }

    public static JLabel mono(String text) {
        JLabel label = new JLabel(text);
        label.setFont(MONO);
        label.setForeground(MUTED);
        return label;
    }

    public static JButton primaryButton(String text) {
        return button(text, GREEN, Color.WHITE, GREEN_DARK);
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SUBHEADING);
        button.setForeground(INK);
        button.setBackground(new Color(236, 241, 237));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(7, 12, 7, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton dangerButton(String text) {
        return button(text, new Color(250, 236, 236), DANGER, new Color(240, 214, 214));
    }

    public static JButton ghostButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setForeground(MUTED);
        button.setBackground(PAPER);
        button.setBorder(new EmptyBorder(5, 7, 5, 7));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton button(String text, Color background, Color foreground, Color hover) {
        JButton button = new JButton(text);
        button.setFont(SUBHEADING);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (button.isEnabled())
                    button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setBackground(background);
            }
        });
        return button;
    }

    public static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(BODY);
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(7, 9, 7, 9)));
        return field;
    }

    public static JPasswordField passwordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(BODY);
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(7, 9, 7, 9)));
        return field;
    }

    public static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(BODY);
        area.setForeground(INK);
        area.setBackground(Color.WHITE);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 9, 8, 9));
        return area;
    }

    public static JScrollPane scroll(Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.setBackground(PAPER);
        scroll.getViewport().setBackground(PAPER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static JScrollPane scrollBorderless(Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(PAPER);
        scroll.getViewport().setBackground(PAPER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static RoundedPanel card() {
        return card(CARD);
    }

    /** A vertical scroll pane whose content keeps the viewport width and stays top-aligned. */
    public static JScrollPane verticalScroll(Component content) {
        JPanel wrapper = new ScrollablePanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.setBackground(PAPER);
        wrapper.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(PAPER);
        scroll.getViewport().setBackground(PAPER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    public static RoundedPanel card(Color fill) {
        RoundedPanel panel = new RoundedPanel(fill, 14) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(18, 20, 18, 20));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    public static JPanel row(int gap) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        row.setOpaque(false);
        return row;
    }

    public static JPanel column() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        return column;
    }

    public static JPanel emptyState(String title, String message) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = heading(title);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel text = muted("<html><div style='text-align:center;width:340px'>" + escape(message) + "</div></html>");
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(Box.createVerticalGlue());
        panel.add(heading);
        panel.add(Box.createVerticalStrut(6));
        panel.add(text);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    public static JPanel formField(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(muted(label), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String shortKey(PubKey key) {
        return HexFormat.of().formatHex(key.bytes(), 0, 6);
    }

    public static String shortId(SyncUnitId id) {
        return HexFormat.of().formatHex(id.bytes(), 0, 6);
    }

    public static String unitType(SyncUnitAuthorization.Type type) {
        return switch (type) {
            case SHARED_FOLDER -> "Shared folder";
            case TODO_LIST -> "Todo list";
            case MESSAGE_CHANNEL -> "Message channel";
            case USER -> "User";
        };
    }

    public static String shortUnitType(SyncUnitAuthorization.Type type) {
        return switch (type) {
            case SHARED_FOLDER -> "Folder";
            case TODO_LIST -> "Todo";
            case MESSAGE_CHANNEL -> "Channel";
            case USER -> "User";
        };
    }

    public static Path chooseOpenFile(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    public static List<Path> chooseOpenFiles(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION)
            return List.of();
        return java.util.Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList();
    }

    public static Path chooseSaveFile(Component parent, String title, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        if (defaultName != null && !defaultName.isBlank())
            chooser.setSelectedFile(new File(defaultName));
        return chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    public static JPanel attachmentList(List<Sync.SyncUnitAttachment> attachments, Consumer<String> status) {
        JPanel panel = column();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (var attachment : attachments)
            panel.add(attachmentChip(attachment, status));
        return panel;
    }

    public static JPanel attachmentChip(Sync.SyncUnitAttachment attachment, Consumer<String> status) {
        JPanel chip = row(6);
        chip.setBackground(new Color(240, 245, 241));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(3, 8, 3, 4)));
        JLabel name = new JLabel(attachment.getName());
        name.setFont(SMALL);
        name.setForeground(INK);
        chip.add(name);
        JButton save = ghostButton("Save");
        save.addActionListener(event -> {
            try {
                String fileName = Path.of(attachment.getName()).getFileName().toString();
                if (fileName.isBlank() || fileName.equals(".") || fileName.equals(".."))
                    fileName = "attachment";
                Path target = chooseSaveFile(chip, "Save attachment", fileName);
                if (target == null)
                    return;
                Files.write(target, attachment.getContent().toByteArray());
                status.accept("Saved attachment to " + target);
            } catch (Exception error) {
                status.accept("Could not save attachment: " + error.getMessage());
            }
        });
        chip.add(save);
        return chip;
    }

    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** A flat panel with rounded corners and a subtle border. */
    public static class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;

        public RoundedPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(fill);
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            copy.setColor(LINE);
            copy.setStroke(new BasicStroke(1f));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }
}
