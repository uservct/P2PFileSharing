package p2p.model;

import java.io.Serializable;

/**
 * Lớp chứa thông tin về một file trong hệ thống P2P
 */
public class FileInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String fileName;    // Tên file
    private long fileSize;      // Kích thước (bytes)
    private String filePath;    // Đường dẫn file trên peer
    private String ownerId;     // ID của peer sở hữu file
    
    public FileInfo(String fileName, long fileSize, String filePath, String ownerId) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.ownerId = ownerId;
    }
    
    // Getters
    public String getFileName() {
        return fileName;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public String getOwnerId() {
        return ownerId;
    }
    
    // Chuyển kích thước sang định dạng dễ đọc
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s)", fileName, getFormattedSize());
    }
}
