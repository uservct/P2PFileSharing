package p2p.gui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import p2p.model.PeerInfo;
import p2p.network.DiscoveryService;

public class PeersPanel extends JPanel {
    private final DiscoveryService discoveryService;
    private final Consumer<String> logger;
    private JTable tblPeers;
    private DefaultTableModel peerTableModel;
    private JButton btnRefreshPeers;

    public PeersPanel(DiscoveryService discoveryService, Consumer<String> logger) {
        this.discoveryService = discoveryService;
        this.logger = logger;
        initializeComponents();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Danh sách Peers"));

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

        add(scrollPane, BorderLayout.CENTER);
        add(btnRefreshPeers, BorderLayout.SOUTH);
    }

    public void refreshPeers() {
        if (discoveryService == null) {
            setPeers(new ArrayList<>());
            return;
        }

        log("Đang tìm kiếm peers...");
        setRefreshEnabled(false);

        new Thread(() -> {
            discoveryService.sendDiscoveryRequest();

            try {
                Thread.sleep(5500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            List<PeerInfo> peers = discoveryService.getPeerInfoList();
            SwingUtilities.invokeLater(() -> {
                setPeers(peers);
                setRefreshEnabled(true);
                log("Tìm thấy " + peers.size() + " peer(s)");
            });
        }).start();
    }

    public void setPeers(List<PeerInfo> peers) {
        peerTableModel.setRowCount(0);
        if (peers == null) {
            return;
        }
        for (PeerInfo peer : peers) {
            peerTableModel.addRow(new Object[] {
                    peer.getIpAddress(),
                    peer.getPort(),
                    peer.getUsername(),
                    "Online" });
        }
    }

    public boolean hasSelection() {
        return tblPeers.getSelectedRow() >= 0;
    }

    public String getSelectedPeerIp() {
        int row = tblPeers.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return (String) peerTableModel.getValueAt(row, 0);
    }

    public Integer getSelectedPeerPort() {
        int row = tblPeers.getSelectedRow();
        if (row < 0) {
            return null;
        }
        Object value = peerTableModel.getValueAt(row, 1);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public void setRefreshEnabled(boolean enabled) {
        btnRefreshPeers.setEnabled(enabled);
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
