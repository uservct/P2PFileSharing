package p2p.main;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import p2p.gui.MainWindow;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String portStr = JOptionPane.showInputDialog(
                    null,
                    "Nhập cổng để chạy peer server:",
                    "P2P File Sharing",
                    JOptionPane.QUESTION_MESSAGE);

            if (portStr == null || portStr.trim().isEmpty()) {
                System.out.println("Đã hủy khởi động.");
                System.exit(0);
                return;
            }

            try {
                int port = Integer.parseInt(portStr.trim());
                if (port < 1024 || port > 65535) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Cổng phải nằm trong khoảng 1024-65535!",
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                    return;
                }

                new MainWindow(port);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Cổng không hợp lệ: " + portStr,
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
