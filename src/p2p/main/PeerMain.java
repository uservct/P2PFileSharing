package p2p.main;

import p2p.network.*;

import java.util.Scanner;

public class PeerMain {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Nhập port của bạn: ");
        int myPort = scanner.nextInt();
        scanner.nextLine();

        PeerServer server = new PeerServer(myPort);
        new Thread(server).start();

        while (true) {

            System.out.println("\n1. Gửi message");
            System.out.println("2. Thoát");
            System.out.print("Chọn: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {

                System.out.print("Nhập IP peer: ");
                String ip = scanner.nextLine();

                System.out.print("Nhập port peer: ");
                int port = scanner.nextInt();
                scanner.nextLine();

                System.out.print("Nhập nội dung: ");
                String msg = scanner.nextLine();

                PeerClient.sendMessage(ip, port, msg);
            } else {
                System.exit(0);
            }
        }
    }
}