package p2p.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class SharedFilesPanel extends JPanel {
    public interface Listener {
        void onRefreshFiles();

        void onSendSelectedFile();

        void onSendOtherFile();
    }

    private final Listener listener;
    private JList<String> lstFiles;
    private DefaultListModel<String> fileListModel;
    private JButton btnRefreshFiles;
    private JButton btnSendFile;
    private JButton btnSendOtherFile;

    public SharedFilesPanel(Listener listener) {
        this.listener = listener;
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
        btnRefreshFiles.addActionListener(e -> {
            if (listener != null) {
                listener.onRefreshFiles();
            }
        });

        btnSendFile = new JButton("Gửi file đã chọn");
        btnSendFile.addActionListener(e -> {
            if (listener != null) {
                listener.onSendSelectedFile();
            }
        });

        btnSendOtherFile = new JButton("Gửi file khác...");
        btnSendOtherFile.addActionListener(e -> {
            if (listener != null) {
                listener.onSendOtherFile();
            }
        });

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

    public void setSendButtonsEnabled(boolean enabled) {
        btnSendFile.setEnabled(enabled);
        btnSendOtherFile.setEnabled(enabled);
    }

    public void setRefreshEnabled(boolean enabled) {
        btnRefreshFiles.setEnabled(enabled);
    }
}
