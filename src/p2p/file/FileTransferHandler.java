package p2p.file;

import p2p.model.FileInfo;
import java.io.*;
import java.net.Socket;

/**
 * Xử lý việc gửi và nhận file qua TCP socket
 */
public class FileTransferHandler {
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    
    /**
     * Gửi file qua socket
     */
    public static void sendFile(Socket socket, FileInfo fileInfo) throws IOException {
        File file = new File(fileInfo.getFilePath());
        
        if (!file.exists()) {
            throw new FileNotFoundException("File không tồn tại: " + fileInfo.getFilePath());
        }
        
        System.out.println("[Transfer] Bắt đầu gửi file: " + fileInfo.getFileName());
        
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             OutputStream os = socket.getOutputStream()) {
            
            // Gửi kích thước file trước
            DataOutputStream dos = new DataOutputStream(os);
            dos.writeLong(file.length());
            dos.flush();
            
            // Gửi dữ liệu file
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Hiển thị tiến trình
                int progress = (int) ((totalSent * 100) / file.length());
                if (totalSent % (BUFFER_SIZE * 10) == 0 || totalSent == file.length()) {
                    System.out.printf("[Transfer] Đang gửi: %d%% (%d/%d bytes)\r", 
                                    progress, totalSent, file.length());
                }
            }
            
            os.flush();
            System.out.println("\n[Transfer] Hoàn tất gửi file: " + fileInfo.getFileName());
        }
    }
    
    /**
     * Nhận file từ socket
     */
    public static void receiveFile(Socket socket, String fileName, String downloadFolder) throws IOException {
        File folder = new File(downloadFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        
        File outputFile = new File(folder, fileName);
        System.out.println("[Transfer] Bắt đầu nhận file: " + fileName);
        
        try (InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            // Đọc kích thước file
            DataInputStream dis = new DataInputStream(is);
            long fileSize = dis.readLong();
            
            // Nhận dữ liệu file
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalReceived = 0;
            
            while (totalReceived < fileSize && (bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
                
                // Hiển thị tiến trình
                int progress = (int) ((totalReceived * 100) / fileSize);
                if (totalReceived % (BUFFER_SIZE * 10) == 0 || totalReceived == fileSize) {
                    System.out.printf("[Transfer] Đang nhận: %d%% (%d/%d bytes)\r", 
                                    progress, totalReceived, fileSize);
                }
            }
            
            bos.flush();
            System.out.println("\n[Transfer] Hoàn tất nhận file: " + fileName);
            System.out.println("[Transfer] Lưu tại: " + outputFile.getAbsolutePath());
        }
    }
}
