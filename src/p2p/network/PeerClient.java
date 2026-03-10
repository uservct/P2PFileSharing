package p2p.network;

import java.io.*;
import java.net.Socket;

public class PeerClient {
    public final static String SERVER_IP = "127.0.0.1";
    private TransferStatusListener statusListener;

    public interface TransferStatusListener {
        void onTransferRejected(String fileName, String reason);

        boolean onRetryPrompt(String fileName);
    }

    public void setTransferStatusListener(TransferStatusListener listener) {
        this.statusListener = listener;
    }

    public void sendFile(String host, int port, String filePath) throws IOException {
        boolean retry = true;

        while (retry) {
            retry = false;
            Socket socket = null;
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    throw new IOException("File không tồn tại!");
                }

                socket = new Socket(host, port);
                System.out.println("Kết nối tới peer: " + host + ":" + port);

                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();

                // 1. Gửi tên file (theo style code mẫu: write text + newline)
                String fileName = file.getName();
                os.write((fileName + "\n").getBytes("UTF-8"));

                // 2. Gửi kích thước file
                long fileSize = file.length();
                os.write((fileSize + "\n").getBytes("UTF-8"));
                os.flush();

                System.out.println("Đang chờ peer xác nhận...");

                // Đợi phản hồi từ peer
                String response = readLine(is);

                if ("REJECT".equals(response)) {
                    System.out.println("Gửi thất bại: Peer từ chối nhận file");

                    // Thông báo cho listener và hỏi có muốn gửi lại không
                    if (statusListener != null) {
                        statusListener.onTransferRejected(fileName, "Peer từ chối nhận file");
                        retry = statusListener.onRetryPrompt(fileName);
                    }

                    socket.close();

                    if (retry) {
                        System.out.println("Đang thử gửi lại...");
                        Thread.sleep(1000); // Đợi 1 giây trước khi gửi lại
                    } else {
                        throw new IOException("Peer từ chối nhận file");
                    }
                    continue;
                }

                if (!"ACCEPT".equals(response)) {
                    socket.close();
                    throw new IOException("Phản hồi không hợp lệ từ peer: " + response);
                }

                System.out.println("Peer đã chấp nhận. Đang gửi file...");

                // 3. Gửi dữ liệu file
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalSent = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // Hiển thị tiến trình
                    int progress = (int) ((totalSent * 100) / fileSize);
                    if (progress % 10 == 0) {
                        System.out.print("\rĐã gửi: " + progress + "%");
                    }
                }

                System.out.println("\nĐã gửi file thành công: " + fileName + " (" + fileSize + " bytes)");
                fis.close();
                socket.close();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (socket != null) {
                    socket.close();
                }
                throw new IOException("Bị gián đoạn khi gửi file", e);
            } catch (IOException ie) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        // Ignore
                    }
                }
                if (!retry) {
                    throw ie;
                }
            }
        }
    }

    private String readLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = is.read()) != -1) {
            if (ch == '\n') {
                break;
            }
            sb.append((char) ch);
        }
        return sb.toString();
    }
}