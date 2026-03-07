package p2p.main;

import java.util.List;
import java.util.Scanner;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import p2p.gui.MainWindow;
import p2p.network.DiscoveryService;
import p2p.network.PeerClient;
import p2p.network.PeerServer;

public class Main {
    public static void main(String[] args) {
        // Kiểm tra xem có flag --cli không
        boolean cliMode = false;
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                cliMode = true;
                break;
            }
        }

        if (cliMode) {
            // Chạy CLI mode (code cũ)
            startCLIMode();
        } else {
            // Chạy GUI mode (mặc định)
            startGUIMode();
        }
    }

    /**
     * Khởi động GUI mode
     */
    private static void startGUIMode() {
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

    /**
     * Khởi động CLI mode (code cũ)
     */
    private static void startCLIMode() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Nhập cổng để chạy peer server: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        // chạy server
        PeerServer server = new PeerServer(port);
        new Thread(server::start).start();

        // Chạy discovery
        DiscoveryService discoveryService = new DiscoveryService(port);
        discoveryService.startListening();

        // Đợi listener khởi động
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Tìm peer ngay khi khởi động
        discoveryService.sendDiscoveryRequest();

        PeerClient client = new PeerClient();
        while (true) {
            System.out.println("\n═══════════════════════════════");
            System.out.println("Chọn: ");
            System.out.println("1. Gửi file");
            System.out.println("2. Tìm peer lại");
            System.out.println("3. Xem danh sách peer");
            System.out.println("4. Thoát");
            System.out.print("Lựa chọn của bạn: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                // Lấy danh sách peer đã tìm thấy
                List<String> peers = discoveryService.getDiscoveredPeers();

                if (peers.isEmpty()) {
                    System.out.println("\nChưa tìm thấy peer nào!");
                    System.out.println("Đang tìm kiếm peer...");
                    discoveryService.sendDiscoveryRequest();

                    // Đợi quá trình tìm kiếm hoàn tất
                    try {
                        Thread.sleep(6000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    peers = discoveryService.getDiscoveredPeers();
                    if (peers.isEmpty()) {
                        System.out.println("Không tìm thấy peer nào. Vui lòng thử lại sau.");
                        continue;
                    }
                }

                // Hiển thị danh sách peer để chọn
                System.out.println("\n╔═══════════════════════════════╗");
                System.out.println("║     DANH SÁCH PEER ONLINE     ║");
                System.out.println("╠═══════════════════════════════╣");
                for (int i = 0; i < peers.size(); i++) {
                    System.out.println("║  " + (i + 1) + ". " + peers.get(i) + "           ║");
                }
                System.out.println("║  0. Quay lại                  ║");
                System.out.println("╚═══════════════════════════════╝");

                System.out.print("Chọn peer để gửi file (1-" + peers.size() + "): ");
                int peerChoice = scanner.nextInt();
                scanner.nextLine();

                if (peerChoice == 0) {
                    continue;
                }

                if (peerChoice < 1 || peerChoice > peers.size()) {
                    System.out.println("Lựa chọn không hợp lệ!");
                    continue;
                }

                // Lấy thông tin peer đã chọn
                String selectedPeer = peers.get(peerChoice - 1);
                String[] peerParts = selectedPeer.split(":");
                String host = peerParts[0];
                int peerPort = Integer.parseInt(peerParts[1]);

                System.out.println("\nĐã chọn peer: " + selectedPeer);
                System.out.print("Nhập đường dẫn file cần gửi: ");
                String path = scanner.nextLine();

                // Kiểm tra file tồn tại
                java.io.File file = new java.io.File(path);
                if (!file.exists()) {
                    System.out.println("File không tồn tại: " + path);
                    continue;
                }

                System.out.println("Đang gửi file '" + file.getName() + "' đến " + selectedPeer + "...");
                try {
                    client.sendFile(host, peerPort, path);
                } catch (Exception e) {
                    System.out.println("Lỗi khi gửi file: " + e.getMessage());
                }

            } else if (choice == 2) {
                System.out.println("\nĐang tìm kiếm peer...");
                discoveryService.sendDiscoveryRequest();

            } else if (choice == 3) {
                // Xem danh sách peer
                List<String> peers = discoveryService.getDiscoveredPeers();
                System.out.println("\n╔═══════════════════════════════╗");
                System.out.println("║     DANH SÁCH PEER ONLINE     ║");
                System.out.println("╠═══════════════════════════════╣");
                if (peers.isEmpty()) {
                    System.out.println("║  (Chưa tìm thấy peer nào)     ║");
                } else {
                    for (int i = 0; i < peers.size(); i++) {
                        System.out.println("║  " + (i + 1) + ". " + peers.get(i) + "           ║");
                    }
                }
                System.out.println("╚═══════════════════════════════╝");
                System.out.println("Tổng số peer: " + peers.size());

            } else if (choice == 4) {
                System.out.println("Đang tắt peer...");
                discoveryService.stop();
                System.exit(0);
            } else {
                System.out.println("Lựa chọn không hợp lệ!");
            }
        }
    }
}
