package p2p.gui;

import java.awt.*;
import java.io.File;
import javax.swing.*;
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
        this.discoveryService = new DiscoveryService(peerPort);

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

        peersPanel = new PeersPanel(discoveryService, this::log);

        sharedFilesPanel = new SharedFilesPanel(peersPanel, fileManager, peerClient, this::log);

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
        searchPanel = new SearchPanel(
                discoveryService,
                peerClient,
                fileManager,
                this::refreshFileList,
                this::log);
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

        discoveryService.startListening();
        log("Discovery service đã khởi động");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (peersPanel != null) {
            peersPanel.refreshPeers();
        }
        refreshFileList();
    }

    // ------------------------------------------------------------------
    // ACTIONS
    // ------------------------------------------------------------------
    private void onSharedFolderChanged(String newPath) {
        fileManager.setSharedFolder(newPath);
        if (peerServer != null) {
            peerServer.setFileManager(fileManager);
        }
        log("Đã chọn thư mục chia sẻ: " + newPath);
        refreshFileList();
    }

    private void refreshFileList() {
        if (sharedFilesPanel != null) {
            sharedFilesPanel.refreshFileList();
        }
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
