package p2p.gui;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import p2p.model.FileSearchResult;
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

    // GUI Components – Peer & Files
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

    // GUI Components – File Search
    private JTextField txtSearchKeyword;
    private JButton btnSearch;
    private JTable tblSearchResults;
    private DefaultTableModel searchTableModel;
    private JButton btnDownload;

    public MainWindow(int port) {
        this.peerPort = port;
        this.peerClient = new PeerClient();

        // Yêu cầu người dùng chọn thư mục chia sẻ ngay từ đầu
        String selectedFolder = promptForSharedFolder();
        if (selectedFolder == null) {
            System.exit(0);
            return;
        }

        this.fileManager = new FileManager(selectedFolder);

        // Listener để xử lý từ chối và retry khi gửi file
        peerClient.setTransferStatusListener(new PeerClient.TransferStatusListener() {
            @Override
            public void onTransferRejected(String fileName, String reason) {
                SwingUtilities.invokeLater(() -> log("Gửi thất bại: " + reason + " - File: " + fileName));
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
        setSize(950, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top – shared folder selection
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);

        // Center – tabbed: [Peers & Files] [Tìm kiếm]
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("📁  Peers & Files", createPeersFilesPanel());
        tabbedPane.addTab("🔍  Tìm kiếm File", createSearchPanel());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom – Log
        mainPanel.add(createLogPanel(), BorderLayout.SOUTH);

        add(mainPanel);
    }

    // ------------------------------------------------------------------
    // TOP PANEL
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // TAB 1: PEERS & FILES (giữ nguyên giao diện cũ)
    // ------------------------------------------------------------------
    private JPanel createPeersFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplitPane.setLeftComponent(createPeerPanel());
        centerSplitPane.setRightComponent(createFilePanel());
        centerSplitPane.setDividerLocation(430);
        panel.add(centerSplitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPeerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Danh sách Peers"));

        String[] columns = { "IP", "Port", "Username", "Trạng thái" };
        peerTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblPeers = new JTable(peerTableModel);
        JScrollPane scrollPane = new JScrollPane(tblPeers);

        btnRefreshPeers = new JButton("Làm mới danh sách");
        btnRefreshPeers.addActionListener(e -> refreshPeers());

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(btnRefreshPeers, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("File trong thư mục chia sẻ"));

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

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------
    // TAB 2: TÌM KIẾM FILE
    // ------------------------------------------------------------------
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ------ Top: thanh tìm kiếm ------
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBorder(BorderFactory.createTitledBorder("Tìm kiếm file trên tất cả Peers"));

        txtSearchKeyword = new JTextField();
        txtSearchKeyword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtSearchKeyword.setToolTipText("Nhập tên hoặc từ khóa của file cần tìm");

        btnSearch = new JButton("🔍 Tìm kiếm");
        btnSearch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSearch.setPreferredSize(new Dimension(120, 32));

        // Nhấn Enter cũng tìm kiếm
        txtSearchKeyword.addActionListener(e -> performSearch());
        btnSearch.addActionListener(e -> performSearch());

        searchBar.add(new JLabel("  Từ khóa: "), BorderLayout.WEST);
        searchBar.add(txtSearchKeyword, BorderLayout.CENTER);
        searchBar.add(btnSearch, BorderLayout.EAST);

        // ------ Center: bảng kết quả ------
        String[] resultColumns = { "Tên File", "Kích thước", "Peer IP", "Peer Port" };
        searchTableModel = new DefaultTableModel(resultColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblSearchResults = new JTable(searchTableModel);
        tblSearchResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblSearchResults.getColumnModel().getColumn(0).setPreferredWidth(280);
        tblSearchResults.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblSearchResults.getColumnModel().getColumn(2).setPreferredWidth(130);
        tblSearchResults.getColumnModel().getColumn(3).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(tblSearchResults);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Kết quả tìm kiếm"));

        // ------ Bottom: nút Tải về ------
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnDownload = new JButton("⬇  Tải về");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDownload.setEnabled(false);
        btnDownload.addActionListener(e -> downloadSelectedFile());
        bottomBar.add(btnDownload);

        // Bật nút Tải về khi chọn hàng
        tblSearchResults.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnDownload.setEnabled(tblSearchResults.getSelectedRow() >= 0);
            }
        });

        panel.add(searchBar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------
    // LOG PANEL
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // NETWORK INITIALIZATION
    // ------------------------------------------------------------------
    private void initializeNetwork() {
        log("Đang khởi động P2P network trên cổng " + peerPort + "...");

        peerServer = new PeerServer(peerPort, fileManager.getSharedFolderPath());
        peerServer.setFileManager(fileManager);

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

        discoveryService = new DiscoveryService(peerPort);
        discoveryService.startListening();
        log("Discovery service đã khởi động");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        refreshPeers();
        refreshFileList();
    }

    // ------------------------------------------------------------------
    // ACTIONS – CŨ
    // ------------------------------------------------------------------
    private void chooseSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(fileManager.getSharedFolderPath()));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            fileManager.setSharedFolder(selectedFolder.getAbsolutePath());
            // Đồng bộ thư mục cho PeerServer (để trả lời SEND/SEARCH đúng thư mục mới)
            peerServer.setFileManager(fileManager);
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
            peerTableModel.addRow(new Object[]{
                    peer.getIpAddress(),
                    peer.getPort(),
                    peer.getUsername(),
                    "Online"
            });
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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn peer để gửi file!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedFileIndex = lstFiles.getSelectedIndex();
        if (selectedFileIndex == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn file để gửi!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn peer để gửi file!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
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
                btnSendFile.setEnabled(true);
                btnSendOtherFile.setEnabled(true);

                if (errorMessage == null) {
                    log("Đã gửi file thành công!");
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Đã gửi file thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    log("Lỗi khi gửi file: " + errorMessage);
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Lỗi khi gửi file: " + errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ------------------------------------------------------------------
    // ACTIONS – TÌM KIẾM FILE
    // ------------------------------------------------------------------

    /**
     * Tìm kiếm file trên tất cả các peer đang online
     */
    private void performSearch() {
        String keyword = txtSearchKeyword.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập từ khóa tìm kiếm!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<PeerInfo> peers = discoveryService.getPeerInfoList();
        if (peers.isEmpty()) {
            log("Tìm kiếm: Không có peer nào đang kết nối.");
            JOptionPane.showMessageDialog(this,
                    "Không có peer nào đang kết nối.\nHãy làm mới danh sách peer trước.",
                    "Không có peer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        searchTableModel.setRowCount(0);
        btnSearch.setEnabled(false);
        btnDownload.setEnabled(false);
        log("Đang tìm kiếm \"" + keyword + "\" trên " + peers.size() + " peer(s)...");

        SwingWorker<List<FileSearchResult>, Void> worker =
                new SwingWorker<List<FileSearchResult>, Void>() {

            @Override
            protected List<FileSearchResult> doInBackground() {
                List<FileSearchResult> allResults = new ArrayList<>();

                // Tìm kiếm song song trên từng peer
                List<Thread> threads = new ArrayList<>();
                List<List<FileSearchResult>> partials = new ArrayList<>();

                for (PeerInfo peer : peers) {
                    List<FileSearchResult> bucket = new ArrayList<>();
                    partials.add(bucket);
                    Thread t = new Thread(() -> {
                        try {
                            List<FileSearchResult> res = peerClient.searchFilesOnPeer(
                                    peer.getIpAddress(), peer.getPort(), keyword);
                            synchronized (bucket) {
                                bucket.addAll(res);
                            }
                        } catch (IOException ex) {
                            System.out.println("Không tìm được trên " +
                                    peer.getIpAddress() + ":" + peer.getPort() +
                                    " – " + ex.getMessage());
                        }
                    });
                    threads.add(t);
                    t.start();
                }

                for (Thread t : threads) {
                    try {
                        t.join(5000); // chờ tối đa 5 giây mỗi peer
                    } catch (InterruptedException ignored) {}
                }

                for (List<FileSearchResult> bucket : partials) {
                    allResults.addAll(bucket);
                }

                return allResults;
            }

            @Override
            protected void done() {
                btnSearch.setEnabled(true);
                try {
                    List<FileSearchResult> results = get();
                    if (results.isEmpty()) {
                        log("Tìm kiếm \"" + keyword + "\": Không tìm thấy kết quả nào.");
                        JOptionPane.showMessageDialog(MainWindow.this,
                                "Không tìm thấy file nào khớp với \"" + keyword + "\".",
                                "Không có kết quả", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        for (FileSearchResult r : results) {
                            searchTableModel.addRow(new Object[]{
                                    r.getFileName(),
                                    formatFileSize(r.getFileSize()),
                                    r.getPeerIp(),
                                    r.getPeerPort()
                            });
                        }
                        log("Tìm kiếm \"" + keyword + "\": Tìm thấy " + results.size() + " kết quả.");
                    }
                } catch (Exception ex) {
                    log("Lỗi khi tìm kiếm: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Tải file được chọn trong bảng kết quả tìm kiếm
     */
    private void downloadSelectedFile() {
        int row = tblSearchResults.getSelectedRow();
        if (row < 0) return;

        String fileName = (String) searchTableModel.getValueAt(row, 0);
        String peerIp   = (String) searchTableModel.getValueAt(row, 2);
        int peerPort    = (Integer) searchTableModel.getValueAt(row, 3);
        String savePath = fileManager.getSharedFolderPath();

        // Kiểm tra file đã có cục bộ chưa
        if (fileManager.hasFile(fileName)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "File \"" + fileName + "\" đã tồn tại trong thư mục chia sẻ.\n" +
                            "Bạn có muốn ghi đè không?",
                    "File đã tồn tại",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        log("Đang tải \"" + fileName + "\" từ " + peerIp + ":" + peerPort + "...");
        btnDownload.setEnabled(false);
        btnSearch.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String errorMessage = null;

            @Override
            protected Void doInBackground() {
                try {
                    peerClient.downloadFileFromPeer(peerIp, peerPort, fileName, savePath);
                } catch (IOException e) {
                    errorMessage = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                btnDownload.setEnabled(tblSearchResults.getSelectedRow() >= 0);
                btnSearch.setEnabled(true);

                if (errorMessage == null) {
                    log("Đã tải xong: \"" + fileName + "\" → " + savePath);
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Tải file thành công!\n" +
                                    "File: " + fileName + "\n" +
                                    "Lưu tại: " + savePath,
                            "Tải về thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshFileList();
                } else {
                    log("Lỗi khi tải \"" + fileName + "\": " + errorMessage);
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Lỗi khi tải file: " + errorMessage,
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------
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
        else if (size < 1024L * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        else
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
