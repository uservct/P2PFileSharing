package p2p.main;
import java.util.Scanner;
import p2p.network.PeerClient;
import p2p.network.PeerServer;
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Nhập cổng để chạy peer server: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        //chạy server
        PeerServer server = new PeerServer(port);
        new Thread(server::start).start();

        PeerClient client = new PeerClient();
        while (true) {
            System.out.println("\n1. Gửi file");
            System.out.println("2. Thoát");
            System.out.print("Chọn: ");
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
            }
        }
    }
}
