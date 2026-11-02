package com.github.ruediste.p2psync.ui.driver;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.text.JTextComponent;

/**
 * Converts the live Swing component hierarchy into JSON-friendly descriptions
 * and resolves Playwright-style selectors against it.
 */
final class SwingUiInspector {
    private static final int MAX_LIST_ITEMS = 200;
    private static final int MAX_TABLE_ROWS = 200;

    private SwingUiInspector() {
    }

    record Match(Component component, String path) {
    }

    static List<Match> matches(Component root, Map<String, Object> selector) {
        var result = new ArrayList<Match>();
        if (root == null)
            return result;
        collect(root, "0", selector, result);
        Object index = selector.get("index");
        int selected = -1;
        if (index instanceof Number number)
            selected = number.intValue();
        else if (index instanceof String text && !text.isBlank()) {
            try {
                selected = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        if (selected >= 0) {
            if (selected >= result.size())
                return List.of();
            return List.of(result.get(selected));
        }
        return result;
    }

    private static void collect(Component component, String path, Map<String, Object> selector,
            List<Match> result) {
        if (matchesOne(component, path, selector))
            result.add(new Match(component, path));
        if (component instanceof Container container && !isOpaqueModel(component)) {
            Component[] children = container.getComponents();
            for (int index = 0; index < children.length; index++)
                collect(children[index], path + "/" + index, selector, result);
        }
    }

    private static boolean isOpaqueModel(Component component) {
        return component instanceof JList<?> || component instanceof JTable || component instanceof JComboBox<?>;
    }

    private static boolean matchesOne(Component component, String path, Map<String, Object> selector) {
        String id = string(selector.get("id"));
        if (id != null && !normalizePath(id).equals(path))
            return false;
        String role = string(selector.get("role"));
        if (role != null && !roleMatches(component, role))
            return false;
        String name = string(selector.get("name"));
        if (name != null && !name.equals(component.getName()))
            return false;
        String text = string(selector.get("text"));
        if (text != null) {
            String actual = componentText(component);
            if (actual == null || !actual.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))
                return false;
        }
        String exactText = string(selector.get("exactText"));
        if (exactText != null && !exactText.equals(componentText(component)))
            return false;
        return true;
    }

    private static String normalizePath(String path) {
        String value = path.trim();
        while (value.startsWith("/"))
            value = value.substring(1);
        if (value.startsWith("root/"))
            value = value.substring("root/".length());
        return value.equals("root") ? "0" : value;
    }

    static String role(Component component) {
        if (component instanceof JPasswordField)
            return "password";
        if (component instanceof JTextComponent)
            return component instanceof javax.swing.JTextArea ? "textarea" : "textbox";
        if (component instanceof javax.swing.JCheckBox)
            return "checkbox";
        if (component instanceof javax.swing.JRadioButton)
            return "radio";
        if (component instanceof AbstractButton)
            return "button";
        if (component instanceof JLabel)
            return "label";
        if (component instanceof JList<?>)
            return "list";
        if (component instanceof JComboBox<?>)
            return "combobox";
        if (component instanceof JTable)
            return "table";
        if (component instanceof JTabbedPane)
            return "tabbedpane";
        if (component instanceof JFrame)
            return "window";
        if (component instanceof javax.swing.JScrollPane)
            return "scrollpane";
        if (component instanceof javax.swing.JSplitPane)
            return "splitpane";
        if (component instanceof javax.swing.JPanel)
            return "panel";
        if (component instanceof javax.swing.JPopupMenu)
            return "popupmenu";
        if (component instanceof javax.swing.JMenuItem)
            return "menuitem";
        if (component instanceof javax.swing.JTree)
            return "tree";
        if (component instanceof javax.swing.JProgressBar)
            return "progressbar";
        if (component instanceof javax.swing.JSlider)
            return "slider";
        if (component instanceof javax.swing.JSpinner)
            return "spinner";
        if (component instanceof javax.swing.JToolBar)
            return "toolbar";
        if (component instanceof javax.swing.JScrollBar)
            return "scrollbar";
        return component.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static boolean roleMatches(Component component, String wanted) {
        String actual = role(component);
        String expected = wanted.toLowerCase(Locale.ROOT);
        return actual.equals(expected)
                || component.getClass().getSimpleName().toLowerCase(Locale.ROOT).equals(expected);
    }

    static String componentText(Component component) {
        if (component instanceof JTextComponent text)
            return text.getText();
        if (component instanceof AbstractButton button)
            return plain(button.getText());
        if (component instanceof JLabel label)
            return plain(label.getText());
        if (component instanceof JFrame frame)
            return frame.getTitle();
        if (component instanceof JComboBox<?> combo)
            return plain(String.valueOf(combo.getSelectedItem()));
        if (component instanceof JTabbedPane tabs && tabs.getSelectedIndex() >= 0)
            return tabs.getTitleAt(tabs.getSelectedIndex());
        return null;
    }

    private static String plain(String text) {
        if (text == null)
            return null;
        if (!text.regionMatches(true, 0, "<html", 0, 5))
            return text;
        String result = text.replaceAll("(?is)<[^>]+>", " ");
        return result.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    static String listItemText(JList<?> list, int index) {
        Object value = list.getModel().getElementAt(index);
        return renderedText(list.getCellRenderer(), list, value, index, value);
    }

    static String comboItemText(JComboBox<?> combo, int index) {
        Object value = combo.getItemAt(index);
        @SuppressWarnings("unchecked")
        ListCellRenderer<? super Object> renderer = (ListCellRenderer<? super Object>) combo.getRenderer();
        return renderedText(renderer, combo, value, index, value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static String renderedText(ListCellRenderer renderer, Component parent, Object value, int index,
            Object fallback) {
        if (renderer != null) {
            try {
                Component rendered = renderer.getListCellRendererComponent(
                        parent instanceof JList ? (JList) parent : new JList<>(new Object[] { value }),
                        value, index, false, false);
                String text = collectComponentText(rendered);
                if (text != null && !text.isBlank())
                    return text;
            } catch (RuntimeException ignored) {
                // Fall through to the value's string representation.
            }
        }
        return plain(String.valueOf(fallback));
    }

    private static String collectComponentText(Component component) {
        var texts = new ArrayList<String>();
        collectComponentText(component, texts);
        return texts.isEmpty() ? null : String.join(" ", texts);
    }

    private static void collectComponentText(Component component, List<String> texts) {
        String text = componentText(component);
        if (text != null && !text.isBlank())
            texts.add(text.trim());
        if (component instanceof Container container)
            for (Component child : container.getComponents())
                collectComponentText(child, texts);
    }

    static Map<String, Object> snapshot(Component root) {
        return describe(root, "0");
    }

    static Map<String, Object> describe(Component component, String path) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", path);
        result.put("role", role(component));
        result.put("class", component.getClass().getName());
        if (component.getName() != null)
            result.put("name", component.getName());
        String text = componentText(component);
        if (text != null)
            result.put("text", text);
        result.put("visible", component.isVisible());
        result.put("showing", component.isShowing());
        result.put("enabled", component.isEnabled());
        Rectangle bounds = component.getBounds();
        result.put("bounds", Map.of("x", bounds.x, "y", bounds.y, "width", bounds.width, "height", bounds.height));

        if (component instanceof AbstractButton button) {
            result.put("selected", button.isSelected());
        }
        if (component instanceof JTextComponent textComponent) {
            result.put("editable", textComponent.isEditable());
        }
        if (component instanceof JList<?> list) {
            result.put("itemCount", list.getModel().getSize());
            result.put("selectedIndex", list.getSelectedIndex());
            var items = new ArrayList<String>();
            for (int index = 0; index < Math.min(list.getModel().getSize(), MAX_LIST_ITEMS); index++)
                items.add(listItemText(list, index));
            result.put("items", items);
            int selected = list.getSelectedIndex();
            if (selected >= 0 && selected < items.size())
                result.put("selectedText", items.get(selected));
        }
        if (component instanceof JComboBox<?> combo) {
            int selected = combo.getSelectedIndex();
            result.put("itemCount", combo.getItemCount());
            result.put("selectedIndex", selected);
            result.put("selectedText", selected >= 0 && selected < combo.getItemCount()
                    ? comboItemText(combo, selected)
                    : null);
            var items = new ArrayList<String>();
            for (int index = 0; index < Math.min(combo.getItemCount(), MAX_LIST_ITEMS); index++)
                items.add(comboItemText(combo, index));
            result.put("items", items);
        }
        if (component instanceof JTable table) {
            result.put("rowCount", table.getRowCount());
            result.put("columnCount", table.getColumnCount());
            result.put("selectedRow", table.getSelectedRow());
            var columns = new ArrayList<String>();
            for (int column = 0; column < table.getColumnCount(); column++)
                columns.add(table.getColumnName(column));
            result.put("columns", columns);
            var rows = new ArrayList<List<Object>>();
            for (int row = 0; row < Math.min(table.getRowCount(), MAX_TABLE_ROWS); row++) {
                var values = new ArrayList<Object>();
                for (int column = 0; column < table.getColumnCount(); column++)
                    values.add(String.valueOf(table.getValueAt(row, column)));
                rows.add(values);
            }
            result.put("rows", rows);
        }
        if (component instanceof JTabbedPane tabs) {
            result.put("selectedIndex", tabs.getSelectedIndex());
            var titles = new ArrayList<String>();
            for (int index = 0; index < tabs.getTabCount(); index++)
                titles.add(tabs.getTitleAt(index));
            result.put("tabs", titles);
        }

        if (component instanceof Container container && !isOpaqueModel(component)) {
            var children = new ArrayList<Map<String, Object>>();
            Component[] components = container.getComponents();
            for (int index = 0; index < components.length; index++)
                children.add(describe(components[index], path + "/" + index));
            result.put("children", children);
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static boolean isActionable(Component root, Match match) {
        Component component = match.component();
        if (!component.isEnabled() || !component.isVisible())
            return false;
        return !root.isShowing() || component.isShowing();
    }
}
