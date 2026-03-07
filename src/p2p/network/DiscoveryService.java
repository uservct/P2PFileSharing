package p2p.network;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
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
    private InetAddress broadcastAddress;

    public DiscoveryService(int peerPort) {
        this.peerPort = peerPort;
        this.discoveryPort = BASE_PORT + (peerPort % 1000);
        this.broadcastAddress = detectBroadcastAddress();
    }

    /**
     * Tự động phát hiện broadcast address của LAN
     */
    private InetAddress detectBroadcastAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        System.out.println("✓ Detected broadcast address: " + broadcast.getHostAddress()
                                + " on " + networkInterface.getDisplayName());
                        return broadcast;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error detecting broadcast: " + e.getMessage());
        }

        try {
            System.out.println("⚠ Using fallback: 255.255.255.255");
            return InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void startListening() {
        if (running)
            return;
        running = true;

        // Thread để lắng nghe discovery messages
        new Thread(() -> {
            try {
                socket = new DatagramSocket(discoveryPort);
                socket.setBroadcast(true); // Enable broadcast cho LAN
                socket.setSoTimeout(1000);
                System.out
                        .println("✓ Discovery listening on port: " + discoveryPort + " (peer port: " + peerPort + ")");
                System.out.println("✓ Broadcast enabled for LAN");

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

                // Không thêm chính mình (check cả port lẫn IP)
                if (port == this.peerPort && isLocalAddress(senderIp)) {
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

    /**
     * Kiểm tra xem IP có phải local không
     */
    private boolean isLocalAddress(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) {
                return true;
            }

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement().equals(addr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private void broadcastPresence() {
        if (socket == null || socket.isClosed())
            return;

        String message = "PEER:" + peerPort;
        byte[] data = message.getBytes();

        // 1. Broadcast tới localhost
        broadcastToLocalhost(data);

        // 2. Broadcast tới LAN
        if (broadcastAddress != null) {
            broadcastToLAN(data);
        }
    }

    /**
     * Broadcast tới tất cả ports trên localhost
     */
    private void broadcastToLocalhost(byte[] data) {
        for (int port = BASE_PORT; port < BASE_PORT + 100; port++) {
            if (port == discoveryPort)
                continue;

            try {
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName("127.0.0.1"),
                        port);
                socket.send(packet);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Broadcast tới LAN (tất cả discovery ports)
     */
    private void broadcastToLAN(byte[] data) {
        for (int port = BASE_PORT; port < BASE_PORT + 100; port++) {
            try {
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        broadcastAddress,
                        port);
                socket.send(packet);
            } catch (Exception e) {
                // Ignore
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

    /**
     * Get current broadcast address (for debugging)
     */
    public String getBroadcastAddress() {
        return broadcastAddress != null ? broadcastAddress.getHostAddress() : "None";
    }
}