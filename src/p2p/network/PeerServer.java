package p2p.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer {
    public final static int SERVER_PORT = 7;
    private int port;
    private String sharedFolderPath;
    private FileReceivedListener listener;
    private FileReceiveRequestListener receiveRequestListener;

    public interface FileReceivedListener {
        void onFileReceived(String fileName, String senderIp, long fileSize);
    }

    public interface FileReceiveRequestListener {
        boolean onFileReceiveRequest(String fileName, String senderIp, long fileSize);
    }

    public PeerServer(int port) {
        this(port, "shared");
    }

    public PeerServer(int port, String sharedFolderPath) {
        this.port = port;
        this.sharedFolderPath = sharedFolderPath;
    }

    public void setFileReceivedListener(FileReceivedListener listener) {
        this.listener = listener;
    }

    public void setFileReceiveRequestListener(FileReceiveRequestListener listener) {
        this.receiveRequestListener = listener;
    }

    public void start() {
        ServerSocket serverSocket = null;
        try {
            System.out.println("Binding to port " + port + ", please wait  ...");
            serverSocket = new ServerSocket(port);
            System.out.println("Peer server đang chạy: " + serverSocket);
            System.out.println("Đang chờ peer khác kết nối...");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("Đã có peer kết nối từ: " + socket.getInetAddress());

                    // Tạo thread riêng để xử lý peer này
                    new Thread(() -> handleClient(socket)).start();
                } catch (IOException e) {
                    System.err.println(" Connection Error: " + e);
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            // Đọc tên file (text line)
            String fileName = readLine(is);
            if (fileName == null || fileName.isEmpty()) {
                socket.close();
                return;
            }

            // Đọc kích thước file (text line)
            String fileSizeStr = readLine(is);
            if (fileSizeStr == null || fileSizeStr.isEmpty()) {
                socket.close();
                return;
            }
            long fileSize = Long.parseLong(fileSizeStr);

            System.out.println("Yêu cầu nhận file: " + fileName);
            System.out.println("Kích thước: " + fileSize + " bytes");
            System.out.println("Từ: " + socket.getInetAddress().getHostAddress());

            // Yêu cầu xác nhận từ người dùng
            boolean accepted = true; // Mặc định chấp nhận nếu không có listener
            if (receiveRequestListener != null) {
                accepted = receiveRequestListener.onFileReceiveRequest(
                        fileName,
                        socket.getInetAddress().getHostAddress(),
                        fileSize);
            }

            // Gửi phản hồi về peer gửi
            if (accepted) {
                os.write("ACCEPT\n".getBytes());
                os.flush();

                File directory = new File(sharedFolderPath);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                // Tạo file để lưu
                File file = new File(directory, fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                // Đọc dữ liệu từ peer và ghi vào file
                while (totalRead < fileSize &&
                        (bytesRead = is.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                fileOutputStream.close();
                System.out.println("Đã nhận file: " + fileName);

                // Notify listener
                if (listener != null) {
                    listener.onFileReceived(fileName, socket.getInetAddress().getHostAddress(), fileSize);
                }
            } else {
                os.write("REJECT\n".getBytes());
                os.flush();
                System.out.println("Đã từ chối nhận file: " + fileName);
            }

            socket.close();
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
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
