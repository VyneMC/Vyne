/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.install.InstallEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.stolym.vyne.install.gui.InstallSwingKit.*;

public final class VyneInstallerGui {
    private JFrame frame;
    private JPanel cards;
    private final CardLayout cardLayout = new CardLayout();

    private final DefaultListModel<LauncherProfile> profilesModel = new DefaultListModel<>();
    private JList<LauncherProfile> profilesList;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea logView;
    private JLabel successSubtitle;
    private JLabel successPath;
    private LauncherProfile selectedProfile;
    private volatile InstallEngine.InstallResult lastInstall;
    private InstallEngine.Mode selectedMode = InstallEngine.Mode.AUTO;
    private boolean replaceFabricLoader = true;

    private final JLabel[] stepBullets = new JLabel[3];
    private final JLabel[] stepTexts   = new JLabel[3];

    public static void launch(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            dev.stolym.vyne.install.VyneInstaller.run(args);
            return;
        }
        applyUiDefaults();
        SwingUtilities.invokeLater(() -> new VyneInstallerGui().show());
    }

    private static void applyUiDefaults() {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void show() {
        frame = new JFrame("Vyne Installer · " + VyneLauncher.VERSION);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Image appIcon = appIconImage();
        if (appIcon != null) frame.setIconImage(appIcon);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1000, 640);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildSidebar(), BorderLayout.WEST);

        cards = new JPanel(cardLayout);
        cards.setBackground(BG);
        cards.add(buildTargetScreen(), "target");
        cards.add(buildProgressScreen(), "progress");
        cards.add(buildSuccessScreen(), "success");
        root.add(cards, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);

        new SwingWorker<List<LauncherProfile>, Void>() {
            @Override protected List<LauncherProfile> doInBackground() {
                return LauncherDetector.detectAll();
            }
            @Override protected void done() {
                try { populateProfiles(get()); }
                catch (Exception ex) { statusLabel.setText("Detection failed: " + ex.getMessage()); }
            }
        }.execute();
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(0x0F, 0x11, 0x14));
        side.setPreferredSize(new Dimension(260, 0));
        side.setBorder(new EmptyBorder(26, 24, 22, 24));

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel logoImg = new JLabel(GuiAssets.icon("vyne_logo.png", 36));
        JLabel logoText = new JLabel("VYNE");
        logoText.setForeground(TEXT);
        logoText.setFont(logoText.getFont().deriveFont(Font.BOLD, 24f));
        logoRow.add(logoImg);
        logoRow.add(logoText);
        side.add(logoRow);

        JLabel sub = new JLabel("Every Mod. One Loader.");
        sub.setForeground(TEXT_DIM);
        sub.setFont(sub.getFont().deriveFont(12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(Box.createRigidArea(new Dimension(0, 8)));
        side.add(sub);

        side.add(Box.createRigidArea(new Dimension(0, 34)));
        side.add(sidebarStep(0, "Pick target"));
        side.add(Box.createRigidArea(new Dimension(0, 10)));
        side.add(sidebarStep(1, "Install"));
        side.add(Box.createRigidArea(new Dimension(0, 10)));
        side.add(sidebarStep(2, "Launch"));
        setActiveStep(0);

        side.add(Box.createVerticalGlue());

        JLabel divider = new JLabel(" ");
        divider.setPreferredSize(new Dimension(0, 1));
        side.add(divider);

        JLabel v = new JLabel("v" + VyneLauncher.VERSION + " · MC " + VyneLauncher.TARGET_MC);
        v.setForeground(TEXT_FAINT);
        v.setFont(v.getFont().deriveFont(11f));
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(v);

        JLabel brand = new JLabel("Stolym Studios");
        brand.setForeground(TEXT_FAINT);
        brand.setFont(brand.getFont().deriveFont(11f));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(Box.createRigidArea(new Dimension(0, 4)));
        side.add(brand);
        return side;
    }

    private JComponent sidebarStep(int index, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel bullet = new JLabel();
        bullet.setPreferredSize(new Dimension(8, 8));
        bullet.setOpaque(true);
        JLabel l = new JLabel(label);
        row.add(bullet);
        row.add(l);
        stepBullets[index] = bullet;
        stepTexts[index] = l;
        return row;
    }

    private void setActiveStep(int active) {
        for (int i = 0; i < stepBullets.length; i++) {
            boolean isActive = (i == active);
            JLabel bullet = stepBullets[i];
            JLabel text = stepTexts[i];
            if (bullet == null || text == null) continue;
            bullet.setBackground(isActive ? ACCENT : BORDER);
            bullet.setBorder(new RoundedBorder(isActive ? ACCENT : BORDER, 1, 4));
            text.setForeground(isActive ? TEXT : TEXT_DIM);
            text.setFont(text.getFont().deriveFont(isActive ? Font.BOLD : Font.PLAIN, 13f));
            bullet.repaint();
            text.repaint();
        }
    }

    private void showCard(String name) {
        cardLayout.show(cards, name);
        switch (name) {
            case "target"   -> setActiveStep(0);
            case "progress" -> setActiveStep(1);
            case "success"  -> setActiveStep(2);
            default         -> {  }
        }
    }

    private JComponent buildTargetScreen() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(30, 34, 24, 34));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Where should Vyne be installed?");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel desc = new JLabel("<html><body style='width:640px'>"
            + "Pick a detected launcher instance, choose a server directory, or point Vyne at any "
            + "folder. We copy the loader into <code>mods/</code> and write a ready-to-go config."
            + "</body></html>");
        desc.setForeground(TEXT_DIM);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        desc.setFont(desc.getFont().deriveFont(13f));
        header.add(title);
        header.add(Box.createRigidArea(new Dimension(0, 6)));
        header.add(desc);
        panel.add(header, BorderLayout.NORTH);

        profilesList = new JList<>(profilesModel);
        profilesList.setCellRenderer(new ProfileCellRenderer());
        profilesList.setBackground(BG);
        profilesList.setFixedCellHeight(72);
        profilesList.setBorder(null);
        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedProfile = profilesList.getSelectedValue();
                statusLabel.setText(selectedProfile == null ? " " : describe(selectedProfile));
            }
        });
        profilesList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectedProfile != null) startInstall();
            }
        });

        JScrollPane scroll = new JScrollPane(profilesList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new RoundedBorder(BORDER, 1, 10));
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUI(new GreenScrollBarUI());
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(16, 0));
        south.setOpaque(false);
        statusLabel = new JLabel("Scanning launchers…");
        statusLabel.setForeground(TEXT_DIM);
        south.add(statusLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(ghostButton("Choose folder", e -> browseForCustomDir()));
        buttons.add(ghostButton("Server folder", e -> pickServerDir()));
        buttons.add(primaryButton("Install", e -> startInstall()));
        south.add(buttons, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void populateProfiles(List<LauncherProfile> detected) {
        profilesModel.clear();
        if (detected == null || detected.isEmpty()) {
            statusLabel.setText("No launcher instances detected — use Choose folder or Server folder.");
        } else {
            for (LauncherProfile p : detected) profilesModel.addElement(p);
            statusLabel.setText(detected.size() + " instance(s) found. Pick one to continue.");
        }
    }

    private void browseForCustomDir() {
        Path initial = selectedProfile != null ? selectedProfile.instanceDir() : null;
        Path p = VyneDirectoryPicker.pickFolder(frame,
            "Choose launcher instance or folder", initial);
        if (p == null) {
            return;
        }
        selectedProfile = LauncherProfile.of(LauncherProfile.Kind.CUSTOM,
            p.getFileName() + "", p, null, null);
        profilesModel.addElement(selectedProfile);
        profilesList.setSelectedValue(selectedProfile, true);
    }

    private void pickServerDir() {
        Path initial = selectedProfile != null ? selectedProfile.instanceDir() : null;
        Path p = VyneDirectoryPicker.pickFolder(frame,
            "Choose server directory (minecraft_server + start scripts)", initial);
        if (p == null) {
            return;
        }
        selectedProfile = LauncherProfile.of(LauncherProfile.Kind.SERVER,
            p.getFileName() + "", p, VyneLauncher.TARGET_MC, "Dedicated server");
        profilesModel.addElement(selectedProfile);
        profilesList.setSelectedValue(selectedProfile, true);
    }

    private String describe(LauncherProfile p) {
        return p.displayName() + " — " + p.subtitle()
            + "   [" + p.instanceDir().toAbsolutePath() + "]";
    }

    private JComponent buildProgressScreen() {
        JPanel panel = new JPanel(new BorderLayout(0, 22));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(44, 44, 44, 44));

        JLabel title = new JLabel("Installing Vyne…");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBackground(BG_CARD);
        progressBar.setForeground(ACCENT);
        progressBar.setBorder(new RoundedBorder(BORDER, 1, 8));
        progressBar.setPreferredSize(new Dimension(0, 28));
        progressBar.setFont(progressBar.getFont().deriveFont(Font.BOLD, 12f));
        progressBar.setUI(new GreenProgressBarUI());

        logView = new JTextArea();
        logView.setEditable(false);
        logView.setBackground(BG_CARD);
        logView.setForeground(TEXT);
        logView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logView.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane logScroll = new JScrollPane(logView);
        logScroll.setBorder(new RoundedBorder(BORDER, 1, 10));
        logScroll.getVerticalScrollBar().setUI(new GreenScrollBarUI());

        JPanel inner = new JPanel(new BorderLayout(0, 16));
        inner.setOpaque(false);
        inner.add(progressBar, BorderLayout.NORTH);
        inner.add(logScroll, BorderLayout.CENTER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildSuccessScreen() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(56, 44, 32, 44));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel check = new JLabel(buildCheckIcon(40));
        JLabel successTitle = new JLabel("Vyne installed");
        successTitle.setForeground(TEXT);
        successTitle.setFont(successTitle.getFont().deriveFont(Font.BOLD, 28f));
        titleRow.add(check);
        titleRow.add(successTitle);

        successSubtitle = new JLabel(" ");
        successSubtitle.setForeground(TEXT_DIM);
        successSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        successSubtitle.setFont(successSubtitle.getFont().deriveFont(14f));

        successPath = new JLabel(" ");
        successPath.setForeground(TEXT_FAINT);
        successPath.setAlignmentX(Component.LEFT_ALIGNMENT);
        successPath.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        header.add(titleRow);
        header.add(Box.createRigidArea(new Dimension(0, 10)));
        header.add(successSubtitle);
        header.add(Box.createRigidArea(new Dimension(0, 12)));
        header.add(successPath);
        panel.add(header, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(ghostButton("Install another", e -> showCard("target")));
        buttons.add(ghostButton("Open folder", e -> openOnDesktop(lastInstall == null ? null : lastInstall.modsDir())));
        buttons.add(primaryButton("Done", e -> frame.dispose()));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void startInstall() {
        if (selectedProfile == null) {
            JOptionPane.showMessageDialog(frame, "Pick a launcher instance first, or use Choose folder.",
                "No target selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showCard("progress");
        progressBar.setValue(0);
        progressBar.setString("Starting…");
        logView.setText("");
        appendLog("Target: " + describe(selectedProfile));

        final InstallEngine.Mode mode = selectedMode;
        final boolean replaceFabric = replaceFabricLoader;
        appendLog("Mode  : " + describeMode(mode, selectedProfile.kind())
            + (mode != InstallEngine.Mode.LEGACY_MODS && mode != InstallEngine.Mode.SERVER
                && replaceFabric
                ? "  (Fabric Loader will be disabled in mmc-pack.json)" : ""));

        new SwingWorker<InstallEngine.InstallResult, Object[]>() {
            @Override protected InstallEngine.InstallResult doInBackground() throws Exception {
                return InstallEngine.installTo(selectedProfile, mode, replaceFabric,
                    (pct, msg) -> publish(new Object[]{pct, msg}));
            }
            @Override protected void process(List<Object[]> chunks) {
                for (Object[] c : chunks) {
                    int pct = (Integer) c[0];
                    String msg = (String) c[1];
                    progressBar.setValue(pct);
                    progressBar.setString(msg);
                    appendLog("[" + pct + "%] " + msg);
                }
            }
            @Override protected void done() {
                try {
                    lastInstall = get();
                    successSubtitle.setText(successMessage(lastInstall, selectedProfile));
                    successPath.setText(lastInstall.loaderJar().toAbsolutePath().toString());
                    if (lastInstall.summary() != null) {
                        for (String line : lastInstall.summary()) appendLog(line);
                    }
                    showCard("success");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    appendLog("\nFAILED: " + cause);
                    JOptionPane.showMessageDialog(frame,
                        cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                        "Install failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String successMessage(InstallEngine.InstallResult r, LauncherProfile p) {
        return switch (r.mode()) {
            case COMPONENT -> "Vyne added as a Prism component in " + p.displayName()
                + ". Launch the instance — the new 'Vyne Loader' patch will appear above Fabric.";
            case GLOBAL -> "Vyne registered globally. In Prism, click 'Add Component' on any "
                + "instance and pick 'Vyne Loader'.";
            case SERVER -> "Server ready. Run ./start.sh (Linux/macOS) or start.bat (Windows) "
                + "inside " + p.instanceDir().getFileName() + "/.";
            default -> p.kind() == LauncherProfile.Kind.MODRINTH
                ? "Vyne set up for the Modrinth App — see the details below for what changed."
                : "Vyne copied into " + p.displayName() + "/mods/. Launch the instance with "
                + "a Fabric " + VyneLauncher.TARGET_MC + " profile active.";
        };
    }

    private String describeMode(InstallEngine.Mode mode, LauncherProfile.Kind kind) {
        InstallEngine.Mode effective = (mode == InstallEngine.Mode.AUTO)
            ? switch (kind) {
                case PRISM, MULTIMC, POLYMC -> InstallEngine.Mode.COMPONENT;
                case SERVER -> InstallEngine.Mode.SERVER;
                default -> InstallEngine.Mode.LEGACY_MODS;
            }
            : mode;
        return switch (effective) {
            case COMPONENT -> "Prism component (patches/ + mmc-pack.json)";
            case LEGACY_MODS -> "Legacy — copy jar to mods/";
            case SERVER -> "Server (downloads MC server + writes start.sh/start.bat)";
            case GLOBAL -> "Global launcher patch (meta/)";
            default -> "Auto";
        };
    }

    private void openModeDialog() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0x1D, 0x1F, 0x23));
        panel.setBorder(new EmptyBorder(18, 22, 10, 22));

        JLabel title = new JLabel("How should Vyne be installed?");
        title.setForeground(new Color(0xF4, 0xF5, 0xF7));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        ButtonGroup group = new ButtonGroup();
        JRadioButton auto = modeRadio("Auto (recommended)",
            "Prism/MultiMC → component patch, others → mods/ folder",
            selectedMode == InstallEngine.Mode.AUTO);
        JRadioButton comp = modeRadio("Prism component",
            "Writes patches/ + updates mmc-pack.json. Treats Vyne as a loader, not a mod.",
            selectedMode == InstallEngine.Mode.COMPONENT);
        JRadioButton legacy = modeRadio("Legacy — copy to mods/",
            "Drops vyne-loader-*.jar into the instance mods/ folder.",
            selectedMode == InstallEngine.Mode.LEGACY_MODS);
        JRadioButton global = modeRadio("Patch launcher globally",
            "Registers Vyne in the launcher's meta/ so it appears in 'Add Component' "
                + "for every instance.",
            selectedMode == InstallEngine.Mode.GLOBAL);

        for (JRadioButton rb : new JRadioButton[]{auto, comp, legacy, global}) {
            group.add(rb);
            panel.add(rb);
            panel.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        JCheckBox disableFabric = new JCheckBox("Disable Fabric Loader in mmc-pack.json "
            + "(Vyne replaces it)", replaceFabricLoader);
        disableFabric.setOpaque(false);
        disableFabric.setForeground(new Color(0xF4, 0xF5, 0xF7));
        disableFabric.setAlignmentX(Component.LEFT_ALIGNMENT);
        disableFabric.setFont(disableFabric.getFont().deriveFont(12f));
        disableFabric.setToolTipText("Only applies to Component / Global installs. Leave enabled "
            + "to let Vyne fully stand in for Fabric Loader — this is the recommended layout.");
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(disableFabric);

        int choice = JOptionPane.showConfirmDialog(frame, panel,
            "Install mode", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        if (auto.isSelected())       selectedMode = InstallEngine.Mode.AUTO;
        else if (comp.isSelected())  selectedMode = InstallEngine.Mode.COMPONENT;
        else if (legacy.isSelected())selectedMode = InstallEngine.Mode.LEGACY_MODS;
        else if (global.isSelected())selectedMode = InstallEngine.Mode.GLOBAL;
        replaceFabricLoader = disableFabric.isSelected();

        statusLabel.setText("Mode: " + describeMode(selectedMode,
            selectedProfile == null ? LauncherProfile.Kind.CUSTOM : selectedProfile.kind()));
    }

    private JRadioButton modeRadio(String label, String tip, boolean selected) {
        JRadioButton rb = new JRadioButton("<html><b>" + label + "</b><br>"
            + "<span style='color:#9DA2AB;font-weight:normal'>" + tip + "</span></html>");
        rb.setSelected(selected);
        rb.setOpaque(false);
        rb.setForeground(new Color(0xF4, 0xF5, 0xF7));
        rb.setAlignmentX(Component.LEFT_ALIGNMENT);
        rb.setFont(rb.getFont().deriveFont(13f));
        return rb;
    }

    private void appendLog(String line) {
        logView.append(line + "\n");
        logView.setCaretPosition(logView.getDocument().getLength());
    }

    private void openOnDesktop(Path p) {
        if (p == null) return;
        try {
            if (!Files.exists(p)) Files.createDirectories(p);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile());
        } catch (IOException ignored) {
        }
    }

    private static Image appIconImage() {
        BufferedImage raw = GuiAssets.load("vyne_logo.png");
        if (raw != null) return raw;
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1D, 0x1F, 0x23));
        g.fillRoundRect(2, 2, 20, 20, 6, 6);
        g.fillRoundRect(26, 2, 20, 20, 6, 6);
        g.fillRoundRect(2, 26, 20, 20, 6, 6);
        g.setColor(ACCENT);
        g.fillRoundRect(26, 26, 20, 20, 6, 6);
        g.dispose();
        return img;
    }

    private static ImageIcon buildCheckIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ACCENT);
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(2f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = size / 4;
        g.drawLine(pad, size / 2, pad + size / 6, size - pad - 2);
        g.drawLine(pad + size / 6, size - pad - 2, size - pad, pad);
        g.dispose();
        return new ImageIcon(img);
    }

    private static final class ProfileCellRenderer extends JPanel
        implements ListCellRenderer<LauncherProfile> {
        private final JLabel logo = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel sub = new JLabel();
        private final JLabel hint = new JLabel();
        private final JPanel badgeWrap = new JPanel(new BorderLayout());

        ProfileCellRenderer() {
            super(new BorderLayout(16, 0));
            setBorder(new EmptyBorder(10, 16, 10, 16));
            badgeWrap.setPreferredSize(new Dimension(48, 48));
            badgeWrap.setBorder(new RoundedBorder(BORDER, 1, 10));
            badgeWrap.add(logo, BorderLayout.CENTER);
            logo.setHorizontalAlignment(SwingConstants.CENTER);
            logo.setVerticalAlignment(SwingConstants.CENTER);

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
            sub.setFont(sub.getFont().deriveFont(12f));
            hint.setFont(hint.getFont().deriveFont(11f));
            text.add(name);
            text.add(Box.createRigidArea(new Dimension(0, 3)));
            text.add(sub);

            add(badgeWrap, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(hint, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LauncherProfile> list,
                                                      LauncherProfile value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            name.setText(value.displayName());
            sub.setText(value.subtitle());
            hint.setText(shortHint(value));

            logo.setIcon(GuiAssets.launcherIcon(value.kind(), 36));
            badgeWrap.setBackground(BG_CARD);

            setBackground(isSelected ? BG_SEL : BG);
            name.setForeground(isSelected ? Color.WHITE : TEXT);
            sub.setForeground(TEXT_DIM);
            hint.setForeground(isSelected ? ACCENT_HI : TEXT_FAINT);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(isSelected ? ACCENT : new Color(0, 0, 0, 0), 1, 10),
                new EmptyBorder(10, 16, 10, 16)));
            return this;
        }

        private static String shortHint(LauncherProfile p) {
            return switch (p.kind()) {
                case PRISM      -> "Prism";
                case MULTIMC    -> "MultiMC";
                case POLYMC     -> "PolyMC";
                case ATLAUNCHER -> "ATLauncher";
                case MODRINTH   -> "Modrinth";
                case CURSEFORGE -> "CurseForge";
                case VANILLA    -> "Vanilla";
                case SERVER     -> "Server";
                default         -> "Custom";
            };
        }
    }
}
