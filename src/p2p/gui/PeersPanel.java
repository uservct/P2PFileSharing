package p2p.gui;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import p2p.model.PeerInfo;

public class PeersPanel extends JPanel {
    public interface Listener {
        void onRefreshPeers();
    }

    private final Listener listener;
    private JTable tblPeers;
    private DefaultTableModel peerTableModel;
    private JButton btnRefreshPeers;

    public PeersPanel(Listener listener) {
        this.listener = listener;
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
        btnRefreshPeers.addActionListener(e -> {
            if (listener != null) {
                listener.onRefreshPeers();
            }
        });

        add(scrollPane, BorderLayout.CENTER);
        add(btnRefreshPeers, BorderLayout.SOUTH);
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
}
