package p2p.gui;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import p2p.model.PeerInfo;
import p2p.network.DiscoveryService;
import p2p.network.PeerClient;
import p2p.network.PeerServer;
import p2p.util.FileManager;

/**
 * Giao diện chính cho ứng dụng P2P File Sharing
 */
public class MainWindow extends JFrame {
    private int peerPort;
    private PeerServer peerServer;
    private DiscoveryService discoveryService;
    private PeerClient peerClient;
    private FileManager fileManager;

    // GUI Components
    private JLabel lblSharedFolder;
    private JButton btnChooseFolder;
    private JTable tblPeers;
    private DefaultTableModel peerTableModel;
    private JButton btnRefreshPeers;
    private JList<String> lstFiles;
    private DefaultListModel<String> fileListModel;
    private JButton btnSendFile;
    private JButton btnRefreshFiles;
    private JTextArea txtLog;
    private JButton btnSendOtherFile;

    public MainWindow(int port) {
        this.peerPort = port;
        this.peerClient = new PeerClient();

        // Yêu cầu người dùng chọn thư mục chia sẻ ngay từ đầu
        String selectedFolder = promptForSharedFolder();
        if (selectedFolder == null) {
            // Người dùng hủy, thoát ứng dụng
            System.exit(0);
            return;
        }

        this.fileManager = new FileManager(selectedFolder);

        // Thiết lập listener để xử lý từ chối và retry
        peerClient.setTransferStatusListener(new PeerClient.TransferStatusListener() {
            @Override
            public void onTransferRejected(String fileName, String reason) {
                SwingUtilities.invokeLater(() -> {
                    log("Gửi thất bại: " + reason + " - File: " + fileName);
                });
            }

            @Override
            public boolean onRetryPrompt(String fileName) {
                final int[] choice = new int[1];
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        choice[0] = JOptionPane.showConfirmDialog(
                                MainWindow.this,
                                "Peer từ chối nhận file: " + fileName + "\n\nBạn có muốn gửi lại không?",
                                "Gửi lại?",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                return (choice[0] == JOptionPane.YES_OPTION);
            }
        });

        initializeComponents();
        initializeNetwork();
        setVisible(true);
    }

    private String promptForSharedFolder() {
        JOptionPane.showMessageDialog(null,
                "Vui lòng chọn thư mục để chia sẻ file.",
                "Chọn thư mục chia sẻ",
                JOptionPane.INFORMATION_MESSAGE);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn thư mục chia sẻ");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private void initializeComponents() {
        setTitle("P2P File Sharing - Port: " + peerPort);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel - Shared folder selection
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel - Split between peers and files
        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplitPane.setLeftComponent(createPeerPanel());
        centerSplitPane.setRightComponent(createFilePanel());
        centerSplitPane.setDividerLocation(400);
        mainPanel.add(centerSplitPane, BorderLayout.CENTER);

        // Bottom panel - Log
        JPanel bottomPanel = createLogPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Thư mục chia sẻ"));

        lblSharedFolder = new JLabel(fileManager.getSharedFolderPath());
        lblSharedFolder.setPreferredSize(new Dimension(500, 25));

        btnChooseFolder = new JButton("Chọn thư mục");
        btnChooseFolder.addActionListener(e -> chooseSharedFolder());

        panel.add(new JLabel("Thư mục: "));
        panel.add(lblSharedFolder);
        panel.add(btnChooseFolder);

        return panel;
    }

    private JPanel createPeerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Danh sách Peers"));

        // Peer table
        String[] columns = { "IP", "Port", "Username", "Trạng thái" };
        peerTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblPeers = new JTable(peerTableModel);
        JScrollPane scrollPane = new JScrollPane(tblPeers);

        // Refresh button
        btnRefreshPeers = new JButton("Làm mới danh sách");
        btnRefreshPeers.addActionListener(e -> refreshPeers());

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(btnRefreshPeers, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("File trong thư mục chia sẻ"));

        // File list
        fileListModel = new DefaultListModel<>();
        lstFiles = new JList<>(fileListModel);
        JScrollPane scrollPane = new JScrollPane(lstFiles);

        // Button panel
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

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Log"));
        panel.setPreferredSize(new Dimension(0, 150));

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(txtLog);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void initializeNetwork() {
        log("Đang khởi động P2P network trên cổng " + peerPort + "...");

        // Start server in background với thư mục chia sẻ
        peerServer = new PeerServer(peerPort, fileManager.getSharedFolderPath());

        // Listener để xác nhận nhận file
        peerServer.setFileReceiveRequestListener((fileName, senderIp, fileSize) -> {
            final int[] choice = new int[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    choice[0] = JOptionPane.showConfirmDialog(
                            MainWindow.this,
                            "Nhận file từ " + senderIp + "?\n" +
                                    "Tên file: " + fileName + "\n" +
                                    "Kích thước: " + formatFileSize(fileSize),
                            "Xác nhận nhận file",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                });
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            boolean accepted = (choice[0] == JOptionPane.YES_OPTION);
            if (accepted) {
                log("Chấp nhận nhận file: " + fileName + " từ " + senderIp);
            } else {
                log("Từ chối nhận file: " + fileName + " từ " + senderIp);
            }
            return accepted;
        });

        peerServer.setFileReceivedListener((fileName, senderIp, fileSize) -> {
            SwingUtilities.invokeLater(() -> {
                log("Đã nhận file: " + fileName + " từ " + senderIp +
                        " (" + formatFileSize(fileSize) + ")");

                // Hiển thị thông báo nhận file thành công
                JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "Nhận file thành công!\n" +
                                "Tên file: " + fileName + "\n" +
                                "Kích thước: " + formatFileSize(fileSize) + "\n" +
                                "Từ: " + senderIp,
                        "Nhận file thành công",
                        JOptionPane.INFORMATION_MESSAGE);

                refreshFileList();
            });
        });

        new Thread(() -> peerServer.start()).start();
        log("Peer server đã khởi động trên cổng " + peerPort);

        // Start discovery service
        discoveryService = new DiscoveryService(peerPort);
        discoveryService.startListening();
        log("Discovery service đã khởi động");

        // Wait for discovery to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Initial peer discovery
        refreshPeers();

        // Initial file list
        refreshFileList();
    }

    private void chooseSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(fileManager.getSharedFolderPath()));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            fileManager.setSharedFolder(selectedFolder.getAbsolutePath());
            lblSharedFolder.setText(fileManager.getSharedFolderPath());
            log("Đã chọn thư mục chia sẻ: " + selectedFolder.getAbsolutePath());
            refreshFileList();
        }
    }

    private void refreshPeers() {
        log("Đang tìm kiếm peers...");
        btnRefreshPeers.setEnabled(false);

        new Thread(() -> {
            discoveryService.sendDiscoveryRequest();

            // Wait for responses
            try {
                Thread.sleep(5500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            SwingUtilities.invokeLater(() -> {
                updatePeerTable();
                btnRefreshPeers.setEnabled(true);
            });
        }).start();
    }

    private void updatePeerTable() {
        peerTableModel.setRowCount(0);
        List<PeerInfo> peers = discoveryService.getPeerInfoList();

        for (PeerInfo peer : peers) {
            Object[] row = {
                    peer.getIpAddress(),
                    peer.getPort(),
                    peer.getUsername(),
                    "Online"
            };
            peerTableModel.addRow(row);
        }

        log("Tìm thấy " + peers.size() + " peer(s)");
    }

    private void refreshFileList() {
        fileListModel.clear();
        List<File> files = fileManager.getSharedFiles();

        for (File file : files) {
            fileListModel.addElement(file.getName() + " (" + formatFileSize(file.length()) + ")");
        }

        log("Có " + files.size() + " file trong thư mục chia sẻ");
    }

    private void sendSelectedFile() {
        int selectedPeerRow = tblPeers.getSelectedRow();
        if (selectedPeerRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn peer để gửi file!",
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedFileIndex = lstFiles.getSelectedIndex();
        if (selectedFileIndex == -1) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn file để gửi!",
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String peerIp = (String) peerTableModel.getValueAt(selectedPeerRow, 0);
        int peerPort = (Integer) peerTableModel.getValueAt(selectedPeerRow, 1);

        List<File> files = fileManager.getSharedFiles();
        File fileToSend = files.get(selectedFileIndex);

        sendFile(peerIp, peerPort, fileToSend.getAbsolutePath());
    }

    private void sendOtherFile() {
        int selectedPeerRow = tblPeers.getSelectedRow();
        if (selectedPeerRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn peer để gửi file!",
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();

            String peerIp = (String) peerTableModel.getValueAt(selectedPeerRow, 0);
            int peerPort = (Integer) peerTableModel.getValueAt(selectedPeerRow, 1);

            sendFile(peerIp, peerPort, selectedFile.getAbsolutePath());
        }
    }

    private void sendFile(String host, int port, String filePath) {
        log("Đang gửi file đến " + host + ":" + port + "...");
        btnSendFile.setEnabled(false);
        btnSendOtherFile.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String errorMessage = null;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    peerClient.sendFile(host, port, filePath);
                } catch (IOException e) {
                    errorMessage = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                btnSendFile.setEnabled(true);
                btnSendOtherFile.setEnabled(true);

                if (errorMessage == null) {
                    log("Đã gửi file thành công!");
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Đã gửi file thành công!",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    log("Lỗi khi gửi file: " + errorMessage);
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Lỗi khi gửi file: " + errorMessage,
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append("[" + getCurrentTime() + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private String getCurrentTime() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        else if (size < 1024 * 1024)
            return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        else
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
