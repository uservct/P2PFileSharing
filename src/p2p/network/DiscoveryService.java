package p2p.network;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import p2p.model.PeerInfo;

public class DiscoveryService {
    private final int peerPort;
    private final int discoveryPort; // Mỗi peer có port discovery riêng
    private volatile boolean running = false;
    private Thread listenerThread;
    private DatagramSocket socket;
    private ConcurrentHashMap<String, PeerInfo> discoveredPeers = new ConcurrentHashMap<>();
    private static final long PEER_TIMEOUT = 30000; // 30 giây

    public DiscoveryService(int peerPort) {
        this.peerPort = peerPort;
        this.discoveryPort = 8888 + (peerPort % 10000); // Tránh collision, mở rộng range
    }

    public void startListening() {
        if (running)
            return;
        running = true;

        listenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(discoveryPort);

                System.out.println("Discovery listener: port " + discoveryPort + " (peer: " + peerPort + ")");

                while (running) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();

                    System.out.println("Nhận: " + message);

                    // Format: DISCOVER:<peerPort>:<discoveryPort>:<responsePort>
                    if (message.startsWith("DISCOVER:")) {
                        String[] parts = message.split(":");
                        if (parts.length != 4)
                            continue;

                        int senderPeerPort = Integer.parseInt(parts[1]);
                        int responsePort = Integer.parseInt(parts[3]);

                        if (senderPeerPort == peerPort)
                            continue; // bỏ qua nếu là chính mình

                        System.out.println("From peer:" + senderPeerPort);

                        // Send response
                        String response = "RESPONSE:" + peerPort + ":" + discoveryPort;
                        DatagramPacket responsePacket = new DatagramPacket(
                                response.getBytes(),
                                response.length(),
                                InetAddress.getByName("127.0.0.1"),
                                responsePort);

                        socket.send(responsePacket);
                        System.out.println("Đã gửi response đến port " + responsePort);
                    }
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("✗ Listener error: " + e.getMessage());
                }
            }
        });
        listenerThread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void sendDiscoveryRequest() {
        new Thread(() -> {
            DatagramSocket responseSocket = null;

            try {
                // Tạo socket để nhận phản hồi
                responseSocket = new DatagramSocket();
                int responsePort = responseSocket.getLocalPort();
                responseSocket.setSoTimeout(5000);

                System.out.println("\nScanning ports 8888-8899...");

                String message = "DISCOVER:" + peerPort + ":" + discoveryPort + ":" + responsePort;

                // Gửi yêu cầu đến tất cả port discovery (8888-8899)
                for (int port = 8888; port <= 8899; port++) {
                    if (port == discoveryPort)
                        continue; // bỏ qua chính mình

                    try {
                        DatagramSocket sendSocket = new DatagramSocket();
                        DatagramPacket packet = new DatagramPacket(
                                message.getBytes(),
                                message.length(),
                                InetAddress.getByName("127.0.0.1"),
                                port);
                        sendSocket.send(packet);
                        sendSocket.close();
                    } catch (Exception e) {
                        // Port not open, skip
                    }
                }

                // Đợi phản hồi trong 5 giây
                long startTime = System.currentTimeMillis();
                discoveredPeers.clear();

                while (System.currentTimeMillis() - startTime < 5000) {
                    try {
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                        responseSocket.receive(packet);
                        String response = new String(packet.getData(), 0, packet.getLength()).trim();

                        System.out.println("Response: " + response);

                        // Format: RESPONSE:<peerPort>:<discoveryPort>
                        if (response.startsWith("RESPONSE:")) {
                            String[] parts = response.split(":");
                            if (parts.length >= 2) {
                                int peerPortNum = Integer.parseInt(parts[1]);
                                String peerIp = "127.0.0.1";
                                String peerAddress = peerIp + ":" + peerPortNum;

                                // Tạo PeerInfo object
                                PeerInfo peerInfo = new PeerInfo(
                                        "peer_" + peerPortNum,
                                        peerIp,
                                        peerPortNum,
                                        "Peer-" + peerPortNum);
                                peerInfo.updateLastSeen();

                                if (!discoveredPeers.containsKey(peerAddress)) {
                                    discoveredPeers.put(peerAddress, peerInfo);
                                    System.out.println("Đã tìm thấy peer: " + peerAddress);
                                } else {
                                    // Cập nhật lastSeen nếu đã tồn tại
                                    discoveredPeers.get(peerAddress).updateLastSeen();
                                }
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }

                System.out.println("─────────────────────────────");
                System.out.println("Found " + discoveredPeers.size() + " peer(s)");
                if (!discoveredPeers.isEmpty()) {
                    System.out.println("Peers:");
                    for (PeerInfo peer : discoveredPeers.values()) {
                        System.out.println("  - " + peer.getAddress());
                    }
                }
                System.out.println();

            } catch (Exception e) {
                System.err.println("✗ Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (responseSocket != null)
                    responseSocket.close();
            }
        }).start();
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
        removeTimeoutPeers(); // Xóa peer timeout trước khi trả về
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