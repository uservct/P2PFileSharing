package p2p.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lưu thông tin về một peer trong mạng P2P, bao gồm ID, địa chỉ IP, cổng
 */
public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String peerId;
    private String ipAddress;
    private int port;
    private String username;
    private long lastSeen;

    public PeerInfo() {
    }

    public PeerInfo(String peerId, String ipAddress, int port) {
        this.peerId = peerId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    public PeerInfo(String peerId, String ipAddress, int port, String username) {
        this.peerId = peerId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.username = username;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public String getAddress() {
        return ipAddress + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port &&
                Objects.equals(peerId, peerInfo.peerId) &&
                Objects.equals(ipAddress, peerInfo.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, ipAddress, port);
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId='" + peerId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
