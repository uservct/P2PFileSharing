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

    // GUI Panels – chia theo chức năng
    private SharedFolderPanel sharedFolderPanel;
    private PeersPanel peersPanel;
    private SharedFilesPanel sharedFilesPanel;
    private SearchPanel searchPanel;

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
        tabbedPane.addTab("Peers & Files", createPeersFilesPanel());
        tabbedPane.addTab("Tìm kiếm File", createSearchPanel());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        add(mainPanel);
    }

    // ------------------------------------------------------------------
    // TOP PANEL
    // ------------------------------------------------------------------
    private JPanel createTopPanel() {
        sharedFolderPanel = new SharedFolderPanel(
                fileManager.getSharedFolderPath(),
                this::onSharedFolderChanged);
        return sharedFolderPanel;
    }

    // ------------------------------------------------------------------
    // TAB 1: PEERS & FILES (giữ nguyên giao diện cũ)
    // ------------------------------------------------------------------
    private JPanel createPeersFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        peersPanel = new PeersPanel(this::refreshPeers);

        sharedFilesPanel = new SharedFilesPanel(new SharedFilesPanel.Listener() {
            @Override
            public void onRefreshFiles() {
                refreshFileList();
            }

            @Override
            public void onSendSelectedFile() {
                sendSelectedFile();
            }

            @Override
            public void onSendOtherFile() {
                sendOtherFile();
            }
        });

        centerSplitPane.setLeftComponent(peersPanel);
        centerSplitPane.setRightComponent(sharedFilesPanel);
        centerSplitPane.setDividerLocation(430);
        panel.add(centerSplitPane, BorderLayout.CENTER);

        return panel;
    }

    // ------------------------------------------------------------------
    // TAB 2: TÌM KIẾM FILE
    // ------------------------------------------------------------------
    private JPanel createSearchPanel() {
        // Tạo component tìm kiếm như cũ
        txtSearchKeyword = new JTextField();
        txtSearchKeyword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtSearchKeyword.setToolTipText("Nhập tên hoặc từ khóa của file cần tìm");

        btnSearch = new JButton("Tìm kiếm");
        btnSearch.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Nhấn Enter cũng tìm kiếm
        txtSearchKeyword.addActionListener(e -> performSearch());
        btnSearch.addActionListener(e -> performSearch());

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

        btnDownload = new JButton("Tải về");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDownload.setEnabled(false);
        btnDownload.addActionListener(e -> downloadSelectedFile());

        // Bật nút Tải về khi chọn hàng
        tblSearchResults.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnDownload.setEnabled(tblSearchResults.getSelectedRow() >= 0);
            }
        });

        // Giao layout cho SearchPanel
        searchPanel = new SearchPanel(txtSearchKeyword, btnSearch, tblSearchResults, btnDownload);
        return searchPanel;
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
    private void onSharedFolderChanged(String newPath) {
        fileManager.setSharedFolder(newPath);
        if (peerServer != null) {
            peerServer.setFileManager(fileManager);
        }
        log("Đã chọn thư mục chia sẻ: " + newPath);
        refreshFileList();
    }

    private void refreshPeers() {
        log("Đang tìm kiếm peers...");
        if (peersPanel != null) {
            peersPanel.setRefreshEnabled(false);
        }

        new Thread(() -> {
            discoveryService.sendDiscoveryRequest();

            try {
                Thread.sleep(5500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            SwingUtilities.invokeLater(() -> {
                updatePeerTable();
                if (peersPanel != null) {
                    peersPanel.setRefreshEnabled(true);
                }
            });
        }).start();
    }

    private void updatePeerTable() {
        List<PeerInfo> peers = discoveryService.getPeerInfoList();
        if (peersPanel != null) {
            peersPanel.setPeers(peers);
        }
        log("Tìm thấy " + peers.size() + " peer(s)");
    }

    private void refreshFileList() {
        List<File> files = fileManager.getSharedFiles();
        List<String> labels = new ArrayList<>();
        for (File file : files) {
            labels.add(file.getName() + " (" + formatFileSize(file.length()) + ")");
        }
        if (sharedFilesPanel != null) {
            sharedFilesPanel.setFileList(labels);
        }
        log("Có " + files.size() + " file trong thư mục chia sẻ");
    }

    private void sendSelectedFile() {
        if (peersPanel == null || !peersPanel.hasSelection()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn peer để gửi file!",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (sharedFilesPanel == null) {
            return;
        }

        int selectedFileIndex = sharedFilesPanel.getSelectedFileIndex();
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
        if (sharedFilesPanel != null) {
            sharedFilesPanel.setSendButtonsEnabled(false);
        }

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
                if (sharedFilesPanel != null) {
                    sharedFilesPanel.setSendButtonsEnabled(true);
                }

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

        SwingWorker<List<FileSearchResult>, Void> worker = new SwingWorker<List<FileSearchResult>, Void>() {

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
                    } catch (InterruptedException ignored) {
                    }
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
                            searchTableModel.addRow(new Object[] {
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
        if (row < 0)
            return;

        String fileName = (String) searchTableModel.getValueAt(row, 0);
        String peerIp = (String) searchTableModel.getValueAt(row, 2);
        int peerPort = (Integer) searchTableModel.getValueAt(row, 3);
        String savePath = fileManager.getSharedFolderPath();

        // Kiểm tra file đã có cục bộ chưa
        if (fileManager.hasFile(fileName)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "File \"" + fileName + "\" đã tồn tại trong thư mục chia sẻ.\n" +
                            "Bạn có muốn ghi đè không?",
                    "File đã tồn tại",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION)
                return;
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
        System.out.println("[" + getCurrentTime() + "] " + message);
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
