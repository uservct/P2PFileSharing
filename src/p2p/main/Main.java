package p2p.main;

import java.util.Scanner;
import p2p.network.DiscoveryService;
import p2p.network.PeerClient;
import p2p.network.PeerServer;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Nhập cổng để chạy peer server: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        // chạy server
        PeerServer server = new PeerServer(port);
        new Thread(server::start).start();

        // Chạy discovery (tất cả dùng cùng multicast address)
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
            System.out.println("Chọn: ");
            System.out.println("1. Gửi file");
            System.out.println("2. Tìm peer lại");
            System.err.println("3. Thoát");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                System.out.print("Nhập IP peer: ");
                String host = scanner.nextLine();

                System.out.print("Nhập port peer: ");
                int peerPort = scanner.nextInt();
                scanner.nextLine();

                System.out.print("Nhập đường dẫn file: ");
                String path = scanner.nextLine();

                client.sendFile(host, peerPort, path);
            } else if (choice == 2) {
                discoveryService.sendDiscoveryRequest();
            } else if (choice == 3) {
                System.out.println("Đang tắt peer...");
                discoveryService.stop();
                System.exit(0);
            } else {
                System.out.println("Lựa chọn không hợp lệ");
            }
        }
    }
}
