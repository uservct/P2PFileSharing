package p2p.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý thư mục chia sẻ và danh sách file
 */
public class FileManager {
    private File sharedFolder;

    public FileManager() {
        // Mặc định là thư mục "shared"
        this.sharedFolder = new File("shared");
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs();
        }
    }

    public FileManager(String folderPath) {
        setSharedFolder(folderPath);
    }

    /**
     * Thiết lập thư mục chia sẻ
     */
    public void setSharedFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.sharedFolder = folder;
        System.out.println("Đã thiết lập thư mục chia sẻ: " + folder.getAbsolutePath());
    }

    /**
     * Lấy đường dẫn thư mục chia sẻ
     */
    public String getSharedFolderPath() {
        return sharedFolder.getAbsolutePath();
    }

    /**
     * Lấy danh sách tất cả file trong thư mục chia sẻ
     */
    public List<File> getSharedFiles() {
        List<File> fileList = new ArrayList<>();
        if (sharedFolder.exists() && sharedFolder.isDirectory()) {
            File[] files = sharedFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    }
                }
            }
        }
        return fileList;
    }

    /**
     * Tìm file theo tên trong thư mục chia sẻ
     */
    public File getFileByName(String fileName) {
        if (sharedFolder.exists() && sharedFolder.isDirectory()) {
            File file = new File(sharedFolder, fileName);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Kiểm tra file có tồn tại trong thư mục chia sẻ
     */
    public boolean hasFile(String fileName) {
        return getFileByName(fileName) != null;
    }

    /**
     * Lấy số lượng file trong thư mục chia sẻ
     */
    public int getFileCount() {
        return getSharedFiles().size();
    }

    /**
     * Lấy tổng kích thước tất cả file (bytes)
     */
    public long getTotalSize() {
        long total = 0;
        for (File file : getSharedFiles()) {
            total += file.length();
        }
        return total;
    }

    /**
     * Tìm kiếm file theo từ khóa (khớp một phần, không phân biệt hoa/thường)
     */
    public List<File> searchFiles(String keyword) {
        List<File> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return result;
        }
        String lowerKeyword = keyword.trim().toLowerCase();
        for (File file : getSharedFiles()) {
            if (file.getName().toLowerCase().contains(lowerKeyword)) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Làm mới danh sách file (re-scan thư mục)
     */
    public void refresh() {
        // Trong implementation đơn giản, chỉ cần gọi lại getSharedFiles()
        System.out.println("Làm mới danh sách file: " + getFileCount() + " file(s)");
    }
}
