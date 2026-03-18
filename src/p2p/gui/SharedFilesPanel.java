package p2p.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import p2p.network.PeerClient;
import p2p.util.FileManager;

public class SharedFilesPanel extends JPanel {
    private final PeersPanel peersPanel;
    private final FileManager fileManager;
    private final PeerClient peerClient;
    private final Consumer<String> logger;
    private JList<String> lstFiles;
    private DefaultListModel<String> fileListModel;
    private JButton btnRefreshFiles;
    private JButton btnSendFile;
    private JButton btnSendOtherFile;

    public SharedFilesPanel(PeersPanel peersPanel,
            FileManager fileManager,
            PeerClient peerClient,
            Consumer<String> logger) {
        this.peersPanel = peersPanel;
        this.fileManager = fileManager;
        this.peerClient = peerClient;
        this.logger = logger;
        initializeComponents();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("File trong thư mục chia sẻ"));

        fileListModel = new DefaultListModel<>();
        lstFiles = new JList<>(fileListModel);
        JScrollPane scrollPane = new JScrollPane(lstFiles);

        JPanel btnPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        btnRefreshFiles = new JButton("Làm mới danh sách file");
        btnRefreshFiles.addActionListener(e -> refreshFileList());

        btnSendFile = new JButton("Gửi file đã chọn");
        btnSendFile.addActionListener(e -> sendSelectedFile());

        btnSendOtherFile = new JButton("Gửi file khác...");
        btnSendOtherFile.addActionListener(e -> sendOtherFile());

        btnPanel.add(btnRefreshFiles);
        btnPanel.add(btnSendFile);
        btnPanel.add(btnSendOtherFile);

        add(scrollPane, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public void setFileList(List<String> items) {
        fileListModel.clear();
        if (items == null) {
            return;
        }
        for (String s : items) {
            fileListModel.addElement(s);
        }
    }

    public int getSelectedFileIndex() {
        return lstFiles.getSelectedIndex();
    }

    public void refreshFileList() {
        List<File> files = fileManager.getSharedFiles();
        fileListModel.clear();
        for (File file : files) {
            fileListModel.addElement(file.getName() + " (" + formatFileSize(file.length()) + ")");
        }
        log("Có " + files.size() + " file trong thư mục chia sẻ");
    }

    private void sendSelectedFile() {
        if (peersPanel == null || !peersPanel.hasSelection()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn peer để gửi file!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedFileIndex = getSelectedFileIndex();
        if (selectedFileIndex == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn file để gửi!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String peerIp = peersPanel.getSelectedPeerIp();
        Integer peerPort = peersPanel.getSelectedPeerPort();
        if (peerIp == null || peerPort == null) {
            JOptionPane.showMessageDialog(this, "Không lấy được thông tin peer để gửi file.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<File> files = fileManager.getSharedFiles();
        if (selectedFileIndex < 0 || selectedFileIndex >= files.size()) {
            JOptionPane.showMessageDialog(this, "File đã chọn không hợp lệ.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File fileToSend = files.get(selectedFileIndex);
        sendFile(peerIp, peerPort, fileToSend.getAbsolutePath());
    }

    private void sendOtherFile() {
        if (peersPanel == null || !peersPanel.hasSelection()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn peer để gửi file!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            String peerIp = peersPanel.getSelectedPeerIp();
            Integer peerPort = peersPanel.getSelectedPeerPort();
            if (peerIp == null || peerPort == null) {
                JOptionPane.showMessageDialog(this, "Không lấy được thông tin peer để gửi file.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            sendFile(peerIp, peerPort, selectedFile.getAbsolutePath());
        }
    }

    private void sendFile(String host, int port, String filePath) {
        log("Đang gửi file đến " + host + ":" + port + "...");
        setSendButtonsEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String errorMessage = null;

            @Override
            protected Void doInBackground() {
                try {
                    peerClient.sendFile(host, port, filePath);
                } catch (IOException e) {
                    errorMessage = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                setSendButtonsEnabled(true);

                if (errorMessage == null) {
                    log("Đã gửi file thành công!");
                    JOptionPane.showMessageDialog(SharedFilesPanel.this,
                            "Đã gửi file thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    log("Lỗi khi gửi file: " + errorMessage);
                    JOptionPane.showMessageDialog(SharedFilesPanel.this,
                            "Lỗi khi gửi file: " + errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public void setSendButtonsEnabled(boolean enabled) {
        btnSendFile.setEnabled(enabled);
        btnSendOtherFile.setEnabled(enabled);
    }

    public void setRefreshEnabled(boolean enabled) {
        btnRefreshFiles.setEnabled(enabled);
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        else if (size < 1024 * 1024)
            return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024L * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        else
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
