package p2p.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import p2p.model.FileSearchResult;
import p2p.model.PeerInfo;
import p2p.network.DiscoveryService;
import p2p.network.PeerClient;
import p2p.util.FileManager;

/**
 * Panel giao diện và xử lý cho chức năng tìm kiếm file.
 */
public class SearchPanel extends JPanel {

    private final DiscoveryService discoveryService;
    private final PeerClient peerClient;
    private final FileManager fileManager;
    private final Runnable onDownloadSuccess;
    private final Consumer<String> logger;

    private JTextField txtSearchKeyword;
    private JButton btnSearch;
    private JTable tblSearchResults;
    private DefaultTableModel searchTableModel;
    private JButton btnDownload;

    public SearchPanel(DiscoveryService discoveryService,
            PeerClient peerClient,
            FileManager fileManager,
            Runnable onDownloadSuccess,
            Consumer<String> logger) {
        this.discoveryService = discoveryService;
        this.peerClient = peerClient;
        this.fileManager = fileManager;
        this.onDownloadSuccess = onDownloadSuccess;
        this.logger = logger;

        initializeComponents();
        setupLayout();
    }

    private void initializeComponents() {
        txtSearchKeyword = new JTextField();
        txtSearchKeyword.setToolTipText("Nhập tên hoặc từ khóa của file cần tìm");

        btnSearch = new JButton("Tìm kiếm");

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
        btnDownload.setEnabled(false);
        btnDownload.addActionListener(e -> downloadSelectedFile());

        // Bật nút Tải về khi chọn hàng
        tblSearchResults.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnDownload.setEnabled(tblSearchResults.getSelectedRow() >= 0);
            }
        });
    }

    private void setupLayout() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: thanh tìm kiếm
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBorder(BorderFactory.createTitledBorder("Tìm kiếm file trên tất cả Peers"));

        searchBar.add(new JLabel("  Từ khóa: "), BorderLayout.WEST);
        searchBar.add(txtSearchKeyword, BorderLayout.CENTER);
        searchBar.add(btnSearch, BorderLayout.EAST);

        // Center: bảng kết quả
        JScrollPane scrollPane = new JScrollPane(tblSearchResults);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Kết quả tìm kiếm"));

        // Bottom: nút Tải về
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnDownload.setPreferredSize(new Dimension(120, 32));
        bottomBar.add(btnDownload);

        add(searchBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);
    }

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
                        JOptionPane.showMessageDialog(SearchPanel.this,
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
                    JOptionPane.showMessageDialog(SearchPanel.this,
                            "Tải file thành công!\n" +
                                    "File: " + fileName + "\n" +
                                    "Lưu tại: " + savePath,
                            "Tải về thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                    if (onDownloadSuccess != null) {
                        onDownloadSuccess.run();
                    }
                } else {
                    log("Lỗi khi tải \"" + fileName + "\": " + errorMessage);
                    JOptionPane.showMessageDialog(SearchPanel.this,
                            "Lỗi khi tải file: " + errorMessage,
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
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
