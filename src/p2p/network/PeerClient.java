package p2p.network;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;

public class PeerClient {
    public void sendFile(String host, int port, String filePath) {
        try (
                Socket socket = new Socket(host, port);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                FileInputStream fis = new FileInputStream(filePath)) 
            {
                File file = new File(filePath);
                System.out.println("Kết nối tới peer: " + host + ":" + port);

                // 1. Gửi tên file
                dos.writeUTF(file.getName());

                // 2. Gửi kích thước file
                dos.writeLong(file.length());

                byte[] buffer = new byte[4096];
                int bytesRead;
                // 3. Gửi dữ liệu file
                while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                }

                dos.flush();
                System.out.println("Đã gửi file");
                socket.close();
        } catch (Exception e) {
        }
    }
}