package p2p.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SharedFolderPanel extends JPanel {
    public interface Listener {
        void onSharedFolderChanged(String newPath);
    }

    private final Listener listener;
    private JLabel lblSharedFolder;
    private JButton btnChooseFolder;
    private String currentPath;

    public SharedFolderPanel(String initialPath, Listener listener) {
        this.currentPath = initialPath;
        this.listener = listener;
        initializeComponents();
    }

    private void initializeComponents() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("Thư mục chia sẻ"));

        lblSharedFolder = new JLabel(currentPath);
        lblSharedFolder.setPreferredSize(new Dimension(500, 25));

        btnChooseFolder = new JButton("Chọn thư mục");
        btnChooseFolder.addActionListener(e -> chooseSharedFolder());

        add(new JLabel("Thư mục: "));
        add(lblSharedFolder);
        add(btnChooseFolder);
    }

    private void chooseSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (currentPath != null && !currentPath.isEmpty()) {
            chooser.setCurrentDirectory(new File(currentPath));
        } else {
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            currentPath = selectedFolder.getAbsolutePath();
            lblSharedFolder.setText(currentPath);
            if (listener != null) {
                listener.onSharedFolderChanged(currentPath);
            }
        }
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(String path) {
        this.currentPath = path;
        lblSharedFolder.setText(path);
    }
}
