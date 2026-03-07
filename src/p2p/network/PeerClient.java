package p2p.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class PeerClient {
    public final static String SERVER_IP = "127.0.0.1";

    public void sendFile(String host, int port, String filePath) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(host, port);
            System.out.println("Kết nối tới peer: " + host + ":" + port);

            OutputStream os = socket.getOutputStream();
            FileInputStream fis = new FileInputStream(filePath);

            File file = new File(filePath);

            // 1. Gửi tên file (theo style code mẫu: write text + newline)
            String fileName = file.getName();
            os.write((fileName + "\n").getBytes("UTF-8"));

            // 2. Gửi kích thước file
            String fileSize = file.length() + "\n";
            os.write(fileSize.getBytes("UTF-8"));

            // 3. Gửi dữ liệu file
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            os.flush();
            System.out.println("Đã gửi file: " + fileName + " (" + file.length() + " bytes)");
            fis.close();
        } catch (IOException ie) {
            System.out.println("Không thể kết nối tới peer: " + ie.getMessage());
            throw ie;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}