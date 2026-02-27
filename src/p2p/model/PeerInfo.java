package p2p.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp chứa thông tin về một peer trong mạng P2P
 */
public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String peerId;      // ID duy nhất của peer
    private String host;        // Địa chỉ IP/hostname
    private int port;           // Port lắng nghe
    private List<FileInfo> sharedFiles; // Danh sách file chia sẻ
    
    public PeerInfo(String peerId, String host, int port) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.sharedFiles = new ArrayList<>();
    }
    
    // Getters
    public String getPeerId() {
        return peerId;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public List<FileInfo> getSharedFiles() {
        return sharedFiles;
    }
    
    // Thêm file vào danh sách chia sẻ
    public void addSharedFile(FileInfo fileInfo) {
        sharedFiles.add(fileInfo);
    }
    
    // Xóa danh sách file
    public void clearSharedFiles() {
        sharedFiles.clear();
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s:%d) - %d files", peerId, host, port, sharedFiles.size());
    }
}
