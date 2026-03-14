package p2p.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import p2p.util.FileManager;

/**
 * PeerServer – lắng nghe các kết nối TCP từ peer khác.
 *
 * Giao thức (dòng đầu tiên xác định loại yêu cầu):
 *   TRANSFER:[filename]  → nhận file (giữ nguyên như cũ)
 *   SEARCH:[keyword]     → trả về danh sách file khớp keyword
 *   SEND:[filename]      → gửi file về cho peer yêu cầu (download)
 *   [filename]           → tương thích ngược: coi là TRANSFER
 */
public class PeerServer {
    public final static int SERVER_PORT = 7;
    private int port;
    private String sharedFolderPath;
    private FileManager fileManager;
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
        this.fileManager = new FileManager(sharedFolderPath);
    }

    public void setFileReceivedListener(FileReceivedListener listener) {
        this.listener = listener;
    }

    public void setFileReceiveRequestListener(FileReceiveRequestListener listener) {
        this.receiveRequestListener = listener;
    }

    /** Cập nhật FileManager khi người dùng đổi thư mục chia sẻ */
    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
        this.sharedFolderPath = fileManager.getSharedFolderPath();
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

            // Đọc dòng đầu tiên để phân biệt loại yêu cầu
            String firstLine = readLine(is);
            if (firstLine == null || firstLine.isEmpty()) {
                socket.close();
                return;
            }

            if (firstLine.startsWith("SEARCH:")) {
                // ------ TÌM KIẾM FILE ------
                String keyword = firstLine.substring(7);
                handleSearch(keyword, os);

            } else if (firstLine.startsWith("SEND:")) {
                // ------ GỬI FILE VỀ CHO PEER (DOWNLOAD) ------
                String fileName = firstLine.substring(5);
                handleSendFile(fileName, os);

            } else {
                // ------ NHẬN FILE (giao thức cũ + TRANSFER:) ------
                String fileName;
                if (firstLine.startsWith("TRANSFER:")) {
                    fileName = firstLine.substring(9);
                } else {
                    // Tương thích ngược: dòng đầu là tên file
                    fileName = firstLine;
                }
                handleReceiveFile(fileName, is, os, socket);
            }

            socket.close();
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    /**
     * Xử lý yêu cầu tìm kiếm SEARCH:[keyword]
     * Response: RESULTS:[n]\n[name1]|[size1]\n...
     */
    private void handleSearch(String keyword, OutputStream os) throws IOException {
        System.out.println("SEARCH request - keyword: \"" + keyword + "\"");
        List<java.io.File> matched = fileManager.searchFiles(keyword);

        PrintWriter out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
        out.println("RESULTS:" + matched.size());
        for (java.io.File f : matched) {
            out.println(f.getName() + "|" + f.length());
        }
        out.flush();
        System.out.println("SEARCH: trả về " + matched.size() + " kết quả cho keyword=\"" + keyword + "\"");
    }

    /**
     * Xử lý yêu cầu gửi file về cho peer (SEND:[filename])
     * Response: [filesize]\n[binary data]
     */
    private void handleSendFile(String fileName, OutputStream os) throws IOException {
        System.out.println("SEND request - file: " + fileName);
        java.io.File file = fileManager.getFileByName(fileName);

        PrintWriter out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
        if (file == null || !file.exists()) {
            out.println("ERROR:File not found");
            out.flush();
            System.out.println("SEND: file không tồn tại: " + fileName);
            return;
        }

        // Gửi kích thước, rồi gửi binary
        out.println(file.length());
        out.flush();

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.flush();
        fis.close();
        System.out.println("SEND: đã gửi file \"" + fileName + "\" (" + file.length() + " bytes)");
    }

    /**
     * Xử lý nhận file từ peer (giao thức gốc)
     */
    private void handleReceiveFile(String fileName, InputStream is, OutputStream os, Socket socket)
            throws IOException {
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
        boolean accepted = true;
        if (receiveRequestListener != null) {
            accepted = receiveRequestListener.onFileReceiveRequest(
                    fileName,
                    socket.getInetAddress().getHostAddress(),
                    fileSize);
        }

        if (accepted) {
            os.write("ACCEPT\n".getBytes());
            os.flush();

            java.io.File directory = new java.io.File(sharedFolderPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            java.io.File file = new java.io.File(directory, fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            while (totalRead < fileSize &&
                    (bytesRead = is.read(buffer, 0,
                            (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            fileOutputStream.close();
            System.out.println("Đã nhận file: " + fileName);

            if (listener != null) {
                listener.onFileReceived(fileName, socket.getInetAddress().getHostAddress(), fileSize);
            }
        } else {
            os.write("REJECT\n".getBytes());
            os.flush();
            System.out.println("Đã từ chối nhận file: " + fileName);
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
