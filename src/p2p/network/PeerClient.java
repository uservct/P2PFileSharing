package p2p.network;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import p2p.model.FileSearchResult;

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

    // -----------------------------------------------------------------------
    // 1. GỬI FILE (giao thức cũ, giữ nguyên để tương thích ngược)
    // -----------------------------------------------------------------------
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

                    if (statusListener != null) {
                        statusListener.onTransferRejected(fileName, "Peer từ chối nhận file");
                        retry = statusListener.onRetryPrompt(fileName);
                    }

                    socket.close();

                    if (retry) {
                        System.out.println("Đang thử gửi lại...");
                        Thread.sleep(1000);
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

    // -----------------------------------------------------------------------
    // 2. TÌM KIẾM FILE TRÊN MỘT PEER
    //    Gửi: SEARCH:[keyword]\n
    //    Nhận: RESULTS:[n]\n[name1]|[size1]\n...
    // -----------------------------------------------------------------------
    public List<FileSearchResult> searchFilesOnPeer(String host, int port, String keyword)
            throws IOException {
        List<FileSearchResult> results = new ArrayList<>();

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(3000); // timeout 3 giây

            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            // Gửi yêu cầu tìm kiếm
            os.write(("SEARCH:" + keyword + "\n").getBytes("UTF-8"));
            os.flush();

            // Đọc dòng đầu: RESULTS:[n]
            String firstLine = readLine(is);
            if (firstLine == null || !firstLine.startsWith("RESULTS:")) {
                return results;
            }

            int count = Integer.parseInt(firstLine.substring(8).trim());
            for (int i = 0; i < count; i++) {
                String line = readLine(is);
                if (line == null || line.isEmpty()) break;

                // Format: filename|filesize
                int sep = line.lastIndexOf('|');
                if (sep < 0) continue;

                String fileName = line.substring(0, sep);
                long fileSize = Long.parseLong(line.substring(sep + 1).trim());
                results.add(new FileSearchResult(fileName, fileSize, host, port));
            }
        }

        return results;
    }

    // -----------------------------------------------------------------------
    // 3. TẢI FILE TỪ PEER (download on demand)
    //    Gửi: SEND:[filename]\n
    //    Nhận: [filesize]\n[binary data]  hoặc ERROR:[msg]\n
    // -----------------------------------------------------------------------
    public void downloadFileFromPeer(String host, int port, String fileName, String savePath)
            throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(30000); // timeout 30 giây cho download

            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            // Gửi yêu cầu tải file
            os.write(("SEND:" + fileName + "\n").getBytes("UTF-8"));
            os.flush();

            // Đọc dòng đầu: kích thước file hoặc ERROR:...
            String firstLine = readLine(is);
            if (firstLine == null || firstLine.startsWith("ERROR:")) {
                String reason = (firstLine != null) ? firstLine.substring(6) : "Không có phản hồi";
                throw new IOException("Peer từ chối gửi file: " + reason);
            }

            long fileSize = Long.parseLong(firstLine.trim());
            System.out.println("Đang tải \"" + fileName + "\" (" + fileSize + " bytes) từ " + host + ":" + port);

            // Tạo thư mục nếu chưa có
            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            File outFile = new File(saveDir, fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            while (totalRead < fileSize &&
                    (bytesRead = is.read(buffer, 0,
                            (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                int progress = (int) ((totalRead * 100) / fileSize);
                if (progress % 10 == 0) {
                    System.out.print("\rĐã tải: " + progress + "%");
                }
            }

            fos.close();
            System.out.println("\nĐã tải xong: " + outFile.getAbsolutePath());
        }
    }

    // -----------------------------------------------------------------------
    // HELPER
    // -----------------------------------------------------------------------
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