/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class VyneDirectoryPicker {
    private static final Icon LIST_FOLDER = InstallSwingKit.folderGlyphIcon(18);

    private VyneDirectoryPicker() {}

    public static Path pickFolder(Window owner, String title, Path initialOrNull) {
        Picker dlg = new Picker(owner, title, resolveStart(initialOrNull));
        dlg.setVisible(true);
        return dlg.result;
    }

    private static Path resolveStart(Path start) {
        Path home = Path.of(System.getProperty("user.home"));
        if (start == null) {
            return home;
        }
        Path p = start.toAbsolutePath().normalize();
        while (p != null) {
            if (Files.isDirectory(p) && Files.isReadable(p)) {
                return p;
            }
            p = p.getParent();
        }
        return home;
    }

    private static final class Picker extends JDialog {
        private final DefaultListModel<Path> model = new DefaultListModel<>();
        private final JList<Path> list = new JList<>(model);
        private final JTextField pathField = new JTextField();
        private final JLabel hint = new JLabel(" ");
        private volatile Path current;
        Path result;

        Picker(Window owner, String title, Path start) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            current = start;
            JPanel root = new JPanel(new BorderLayout(0, 14));
            root.setBackground(InstallSwingKit.BG);
            root.setBorder(new EmptyBorder(20, 22, 18, 22));

            JLabel heading = new JLabel("Select a folder");
            heading.setForeground(InstallSwingKit.TEXT);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));

            JLabel sub = new JLabel("<html><body style='width:480px;color:#9DA2AB'>"
                + "Navigate like a file browser, then confirm the folder that should receive the "
                + "install.</body></html>");
            sub.setBorder(new EmptyBorder(0, 0, 4, 0));

            JPanel northStack = new JPanel();
            northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
            northStack.setOpaque(false);
            northStack.add(heading);
            northStack.add(Box.createRigidArea(new Dimension(0, 6)));
            northStack.add(sub);

            JPanel toolbar = new JPanel(new BorderLayout(10, 0));
            toolbar.setOpaque(false);

            JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            nav.setOpaque(false);
            nav.add(InstallSwingKit.ghostButton("Parent folder", e -> goUp()));
            nav.add(InstallSwingKit.ghostButton("Home", e -> navigateTo(Path.of(System.getProperty("user.home")))));

            pathField.setEditable(false);
            pathField.setBackground(InstallSwingKit.BG_CARD);
            pathField.setForeground(InstallSwingKit.TEXT);
            pathField.setCaretColor(InstallSwingKit.TEXT_DIM);
            pathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pathField.setBorder(BorderFactory.createCompoundBorder(
                new InstallSwingKit.RoundedBorder(InstallSwingKit.BORDER, 1, 8),
                new EmptyBorder(8, 10, 8, 10)));

            toolbar.add(nav, BorderLayout.WEST);
            toolbar.add(pathField, BorderLayout.CENTER);

            northStack.add(toolbar);

            list.setBackground(InstallSwingKit.BG);
            list.setSelectionBackground(InstallSwingKit.BG_SEL);
            list.setSelectionForeground(InstallSwingKit.TEXT);
            list.setCellRenderer(new DirCellRenderer());
            list.setFixedCellHeight(38);
            list.setBorder(null);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openSelected();
                    }
                }
            });
            list.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        openSelected();
                    }
                }
            });

            JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(new InstallSwingKit.RoundedBorder(InstallSwingKit.BORDER, 1, 10));
            scroll.getViewport().setBackground(InstallSwingKit.BG);
            scroll.getVerticalScrollBar().setUI(new InstallSwingKit.GreenScrollBarUI());

            hint.setForeground(InstallSwingKit.TEXT_DIM);
            hint.setFont(hint.getFont().deriveFont(12f));

            JPanel south = new JPanel(new BorderLayout(0, 10));
            south.setOpaque(false);
            south.add(hint, BorderLayout.WEST);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actions.setOpaque(false);
            actions.add(InstallSwingKit.ghostButton("Cancel", e -> dispose()));
            actions.add(InstallSwingKit.primaryButton("Use this folder", e -> confirm()));
            south.add(actions, BorderLayout.EAST);

            root.add(northStack, BorderLayout.NORTH);
            root.add(scroll, BorderLayout.CENTER);
            root.add(south, BorderLayout.SOUTH);
            setContentPane(root);
            setMinimumSize(new Dimension(560, 420));
            setSize(600, 460);
            setLocationRelativeTo(owner);
            if (owner instanceof Frame f) {
                Image ic = f.getIconImage();
                if (ic != null) {
                    setIconImage(ic);
                }
            }

            list.getSelectionModel().addListSelectionListener(this::onListSel);
            refreshListing();
        }

        private void onListSel(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }
            Path sel = list.getSelectedValue();
            if (sel != null && Files.isDirectory(sel)) {
                hint.setText("Double-click or Enter to open · " + sel.getFileName());
            } else {
                hint.setText(" ");
            }
        }

        private void goUp() {
            Path p = current.getParent();
            if (p != null) {
                navigateTo(p);
            }
        }

        private void navigateTo(Path dir) {
            Path resolved = resolveStart(dir);
            current = resolved;
            refreshListing();
        }

        private void openSelected() {
            Path sel = list.getSelectedValue();
            if (sel != null && Files.isDirectory(sel)) {
                navigateTo(sel);
            }
        }

        private void confirm() {
            if (Files.isDirectory(current) && Files.isReadable(current)) {
                result = current;
            }
            dispose();
        }

        private void refreshListing() {
            pathField.setText(current.toString());
            pathField.setCaretPosition(0);
            model.clear();
            hint.setText("Reading…");
            new SwingWorker<List<Path>, Void>() {
                @Override
                protected List<Path> doInBackground() {
                    Path dir = current;
                    List<Path> out = new ArrayList<>();
                    try (Stream<Path> stream = Files.list(dir)) {
                        stream.filter(Files::isDirectory)
                            .sorted(Comparator.comparing(
                                a -> a.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                            .forEach(out::add);
                    } catch (IOException ex) {
                        return List.of();
                    }
                    return out;
                }

                @Override
                protected void done() {
                    try {
                        List<Path> rows = get();
                        for (Path p : rows) {
                            model.addElement(p);
                        }
                        if (rows.isEmpty()) {
                            hint.setText("No subfolders here — pick this folder or go up.");
                        } else {
                            hint.setText(rows.size() + " folder(s) · Double-click to open");
                        }
                    } catch (Exception ex) {
                        hint.setText("Could not read this location.");
                    }
                    list.clearSelection();
                }
            }.execute();
        }
    }

    private static final class DirCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel lab = (JLabel) super.getListCellRendererComponent(list, value, index,
                isSelected, cellHasFocus);
            Path p = (Path) value;
            lab.setText(p.getFileName().toString());
            lab.setBorder(new EmptyBorder(5, 12, 5, 12));
            lab.setIcon(LIST_FOLDER);
            lab.setIconTextGap(10);
            lab.setVerticalTextPosition(SwingConstants.CENTER);
            lab.setHorizontalTextPosition(SwingConstants.RIGHT);
            lab.setOpaque(true);
            if (isSelected) {
                lab.setBackground(InstallSwingKit.BG_SEL);
                lab.setForeground(InstallSwingKit.TEXT);
            } else {
                lab.setBackground(InstallSwingKit.BG);
                lab.setForeground(InstallSwingKit.TEXT);
            }
            return lab;
        }
    }
}
