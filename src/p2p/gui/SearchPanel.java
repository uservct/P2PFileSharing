package p2p.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Panel giao diện cho chức năng tìm kiếm file.
 * Chỉ chịu trách nhiệm layout; logic tìm kiếm vẫn nằm ở MainWindow.
 */
public class SearchPanel extends JPanel {

    public SearchPanel(JTextField txtSearchKeyword,
            JButton btnSearch,
            JTable tblSearchResults,
            JButton btnDownload) {
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
}
