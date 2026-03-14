package p2p.model;

/**
 * Lưu thông tin một kết quả tìm kiếm file từ một peer cụ thể.
 */
public class FileSearchResult {
    private final String fileName;
    private final long fileSize;
    private final String peerIp;
    private final int peerPort;

    public FileSearchResult(String fileName, long fileSize, String peerIp, int peerPort) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.peerIp = peerIp;
        this.peerPort = peerPort;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getPeerIp() {
        return peerIp;
    }

    public int getPeerPort() {
        return peerPort;
    }

    @Override
    public String toString() {
        return fileName + " (" + fileSize + " bytes) @ " + peerIp + ":" + peerPort;
    }
}
