package p2p.file;

import p2p.model.FileInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý các file trong thư mục chia sẻ
 */
public class FileManager {
    private String sharedFolder;    // Đường dẫn thư mục chia sẻ
    private String peerId;          // ID của peer sở hữu
    private List<FileInfo> fileList; // Danh sách file
    
    public FileManager(String sharedFolder, String peerId) {
        this.sharedFolder = sharedFolder;
        this.peerId = peerId;
        this.fileList = new ArrayList<>();
        scanFiles(); // Scan ngay khi khởi tạo
    }
    
    /**
     * Quét thư mục shared để lấy danh sách file
     */
    public void scanFiles() {
        fileList.clear();
        File folder = new File(sharedFolder);
        
        // Tạo thư mục nếu chưa tồn tại
        if (!folder.exists()) {
            folder.mkdirs();
            System.out.println("[FileManager] Đã tạo thư mục: " + sharedFolder);
            return;
        }
        
        // Quét các file
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    FileInfo fileInfo = new FileInfo(
                        file.getName(),
                        file.length(),
                        file.getAbsolutePath(),
                        peerId
                    );
                    fileList.add(fileInfo);
                }
            }
        }
        
        System.out.println("[FileManager] Đã quét " + fileList.size() + " file");
    }
    
    /**
     * Lấy danh sách tất cả file
     */
    public List<FileInfo> getFileList() {
        return new ArrayList<>(fileList);
    }
    
    /**
     * Tìm file theo tên
     */
    public FileInfo findFile(String fileName) {
        for (FileInfo file : fileList) {
            if (file.getFileName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }
    
    /**
     * Tìm kiếm file có chứa từ khóa
     */
    public List<FileInfo> searchFiles(String keyword) {
        List<FileInfo> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        for (FileInfo file : fileList) {
            if (file.getFileName().toLowerCase().contains(lowerKeyword)) {
                results.add(file);
            }
        }
        
        return results;
    }
    
    /**
     * Kiểm tra file có tồn tại không
     */
    public boolean hasFile(String fileName) {
        return findFile(fileName) != null;
    }
    
    public String getSharedFolder() {
        return sharedFolder;
    }
}
