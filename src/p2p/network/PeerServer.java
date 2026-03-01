package p2p.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer {
    private int port;

    public PeerServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Peer server đang chạy trên cổng " + port);
            while (true) {
                System.out.println("Đang chờ peer khác kết nối...");
                Socket socket = serverSocket.accept(); // chờ kết nối

                System.out.println("Đã có peer kết nối từ: " + socket.getInetAddress());
                // Tạo thread riêng để xử lý peer này
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                FileOutputStream fos = null) {
            // nhận tên file
            String fileName = dis.readUTF();

            // nhận kích thước file
            long fileSize = dis.readLong();

            System.out.println("Nhận file: " + fileName);
            System.out.println("Kích thước: " + fileSize + " bytes");

            File directory = new File("shared");
            if (!directory.exists()) {
            directory.mkdirs();
            }

            // tạo file để lưu
            File file = new File(directory, fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            // đọc dữ liệu từ peer và ghi vào file
            while (totalRead < fileSize &&
                    (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {

                fileOutputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            fileOutputStream.close();
            System.out.println("Đã nhận file");
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
