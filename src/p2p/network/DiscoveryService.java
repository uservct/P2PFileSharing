package p2p.network;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import p2p.model.PeerInfo;

public class DiscoveryService {
    private final int peerPort;
    private final int discoveryPort;
    private volatile boolean running = false;
    private DatagramSocket socket;
    private final ConcurrentHashMap<String, PeerInfo> discoveredPeers = new ConcurrentHashMap<>();
    private static final long PEER_TIMEOUT = 30000; // 30 giây
    private static final int BASE_PORT = 9000;

    public DiscoveryService(int peerPort) {
        this.peerPort = peerPort;
        this.discoveryPort = BASE_PORT + (peerPort % 1000);
    }

    public void startListening() {
        if (running)
            return;
        running = true;

        // Thread để lắng nghe discovery messages
        new Thread(() -> {
            try {
                socket = new DatagramSocket(discoveryPort);
                System.out
                        .println("✓ Discovery listening on port: " + discoveryPort + " (peer port: " + peerPort + ")");

                while (running) {
                    byte[] buffer = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength()).trim();
                        handleMessage(message, packet.getAddress().getHostAddress());
                    } catch (SocketTimeoutException e) {
                        // Timeout, continue
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("✗ Discovery listener error: " + e.getMessage());
                }
            }
        }).start();

        // Thread để broadcast presence định kỳ
        new Thread(() -> {
            try {
                Thread.sleep(500); // Chờ listener start
            } catch (InterruptedException e) {
                return;
            }

            while (running) {
                try {
                    broadcastPresence();
                    Thread.sleep(3000); // Broadcast mỗi 3 giây
                } catch (Exception e) {
                    if (running) {
                        System.err.println("✗ Broadcast error: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void handleMessage(String message, String senderIp) {
        // Format: PEER:<port>
        if (message.startsWith("PEER:")) {
            try {
                int port = Integer.parseInt(message.substring(5));

                // Không thêm chính mình
                if (port == this.peerPort) {
                    return;
                }

                String peerKey = senderIp + ":" + port;

                PeerInfo peerInfo = discoveredPeers.get(peerKey);
                if (peerInfo == null) {
                    peerInfo = new PeerInfo("peer_" + port, senderIp, port, "Peer-" + port);
                    discoveredPeers.put(peerKey, peerInfo);
                    System.out.println("✓ Discovered peer: " + peerKey);
                } else {
                    peerInfo.updateLastSeen();
                }

            } catch (Exception e) {
                System.err.println("✗ Error handling message: " + e.getMessage());
            }
        }
    }

    private void broadcastPresence() {
        if (socket == null || socket.isClosed())
            return;

        String message = "PEER:" + peerPort;
        byte[] data = message.getBytes();

        // Broadcast tới tất cả discovery ports (9000-9099)
        for (int port = BASE_PORT; port < BASE_PORT + 100; port++) {
            if (port == discoveryPort)
                continue; // Skip chính mình

            try {
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName("127.0.0.1"),
                        port);
                socket.send(packet);
            } catch (Exception e) {
                // Ignore - port có thể không có peer
            }
        }
    }

    public void sendDiscoveryRequest() {
        // Method này giờ chỉ trigger một broadcast ngay lập tức
        System.out.println("Manually triggering discovery...");
        try {
            broadcastPresence();
        } catch (Exception e) {
            System.err.println("✗ Manual discovery error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Xóa các peer đã timeout (không phản hồi trong 30 giây)
     */
    public void removeTimeoutPeers() {
        long currentTime = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (String key : discoveredPeers.keySet()) {
            PeerInfo peer = discoveredPeers.get(key);
            if (currentTime - peer.getLastSeen() > PEER_TIMEOUT) {
                toRemove.add(key);
            }
        }

        for (String key : toRemove) {
            discoveredPeers.remove(key);
            System.out.println("Removed timeout peer: " + key);
        }
    }

    /**
     * Lấy danh sách PeerInfo đã tìm được
     */
    public List<PeerInfo> getPeerInfoList() {
        removeTimeoutPeers();
        return new ArrayList<>(discoveredPeers.values());
    }

    /**
     * Lấy danh sách peer dạng String (để tương thích với code cũ)
     */
    public List<String> getDiscoveredPeers() {
        removeTimeoutPeers();
        List<String> peerList = new ArrayList<>();
        for (PeerInfo peer : discoveredPeers.values()) {
            peerList.add(peer.getAddress());
        }
        return peerList;
    }

    /**
     * Lấy thông tin peer theo địa chỉ
     */
    public PeerInfo getPeerInfo(String address) {
        return discoveredPeers.get(address);
    }
}