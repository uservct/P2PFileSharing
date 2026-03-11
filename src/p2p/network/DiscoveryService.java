package p2p.network;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import p2p.model.PeerInfo;

/**
 * TCP Discovery Service - Không dùng UDP broadcast
 * Mỗi peer mở TCP server và quét subnet để tìm peer khác
 */
public class DiscoveryService {
    private final int peerPort;
    private final int discoveryPort;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, PeerInfo> discoveredPeers = new ConcurrentHashMap<>();
    private static final long PEER_TIMEOUT = 30000; // 30 giây
    private static final int BASE_PORT = 9000;
    private static final int SCAN_TIMEOUT = 150; // Timeout khi quét mỗi IP (ms)
    private final List<String> subnetRanges = new ArrayList<>();
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(100);

    public DiscoveryService(int peerPort) {
        this.peerPort = peerPort;
        this.discoveryPort = BASE_PORT + (peerPort % 1000);

        System.out.println("\n========================================");
        System.out.println("TCP DISCOVERY SERVICE KHỞI ĐỘNG");
        System.out.println("Peer Port: " + peerPort);
        System.out.println("Discovery Port: " + discoveryPort);
        System.out.println("========================================\n");

        detectSubnetRanges();
    }

    /**
     * Phát hiện tất cả dải subnet cần quét
     */
    private void detectSubnetRanges() {
        System.out.println("\n=== PHÁT HIỆN DÃNG MẠNG ===");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress ip = addr.getAddress();

                    // Chỉ xử lý IPv4
                    if (!(ip instanceof Inet4Address)) {
                        continue;
                    }

                    short prefixLength = addr.getNetworkPrefixLength();
                    String ipStr = ip.getHostAddress();

                    System.out.println("\n--- Interface: " + ni.getDisplayName() + " ---");
                    System.out.println("  IP: " + ipStr);
                    System.out.println("  Prefix: /" + prefixLength);

                    // Tính subnet cho các prefix length phổ biến
                    String subnet = null;
                    if (prefixLength >= 16 && prefixLength <= 32) {
                        // Lấy 3 octet đầu cho /24, /22, /23, etc.
                        int lastDot = ipStr.lastIndexOf('.');
                        if (lastDot > 0) {
                            subnet = ipStr.substring(0, lastDot);
                            if (!subnetRanges.contains(subnet)) {
                                subnetRanges.add(subnet);
                                System.out.println("  ✓✓✓ THÊM SUBNET: " + subnet + ".1-254 ✓✓✓");
                            }
                        }
                    }
                }
            }

            if (subnetRanges.isEmpty()) {
                System.out.println("⚠ Không phát hiện subnet, sẽ quét 192.168.1");
                subnetRanges.add("192.168.1");
                subnetRanges.add("127.0.0");
            }

        } catch (SocketException e) {
            System.err.println("❌ Lỗi phát hiện subnet: " + e.getMessage());
            e.printStackTrace();
            subnetRanges.add("192.168.1");
            subnetRanges.add("127.0.0");
        }

        System.out.println("\n=== KẾT QUẢ SUBNET DETECTION ===");
        for (String subnet : subnetRanges) {
            System.out.println("  → Sẽ quét: " + subnet + ".1-254");
        }
        System.out.println("=================================\n");
    }

    /**
     * Khởi động TCP Discovery Service
     */
    public void startListening() {
        if (running)
            return;
        running = true;

        // Thread 1: TCP Server lắng nghe kết nối từ peer khác
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(discoveryPort);
                serverSocket.setSoTimeout(1000); // Timeout để có thể check running flag
                System.out.println("✓ TCP Discovery Server đang lắng nghe trên port: " + discoveryPort);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Xử lý kết nối trong thread riêng
                        scanExecutor.submit(() -> handleIncomingConnection(clientSocket));
                    } catch (SocketTimeoutException e) {
                        // Timeout bình thường, tiếp tục loop
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ Lỗi TCP Server: " + e.getMessage());
                }
            }
        }, "TCP-Discovery-Server").start();

        // Thread 2: Quét subnet định kỳ để tìm peer
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Đợi server khởi động
            } catch (InterruptedException e) {
                return;
            }

            System.out.println("✓ Bắt đầu quét subnet...\n");

            while (running) {
                try {
                    scanForPeers();
                    Thread.sleep(10000); // Quét lại mỗi 10 giây
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TCP-Discovery-Scanner").start();
    }

    /**
     * Xử lý kết nối TCP từ peer khác
     */
    private void handleIncomingConnection(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(2000);
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Đọc thông tin peer gửi đến
            String message = in.readLine();

            if (message != null && message.startsWith("PEER:")) {
                int port = Integer.parseInt(message.substring(5));

                // Không thêm chính mình
                if (port == this.peerPort && isLocalAddress(clientIp)) {
                    clientSocket.close();
                    return;
                }

                // Thêm peer vào danh sách
                String peerKey = clientIp + ":" + port;
                PeerInfo peerInfo = discoveredPeers.get(peerKey);

                if (peerInfo == null) {
                    peerInfo = new PeerInfo("peer_" + port, clientIp, port, "Peer-" + port);
                    discoveredPeers.put(peerKey, peerInfo);
                    System.out.println("✓ Phát hiện peer mới: " + peerKey);
                } else {
                    peerInfo.updateLastSeen();
                }

                // Gửi lại thông tin của mình
                out.println("PEER:" + this.peerPort);
            }

            clientSocket.close();

        } catch (IOException | NumberFormatException e) {
            // Ignore connection errors
        }
    }

    /**
     * Quét tất cả subnet để tìm peer
     */
    private void scanForPeers() {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║   BẮT ĐẦU QUÉT TÌM PEER (TCP SCAN)    ║");
        System.out.println("╚════════════════════════════════════════╝");

        for (String subnet : subnetRanges) {
            System.out.println("\n→ Đang quét dải: " + subnet + ".1 đến " + subnet + ".254");
            System.out.println("  Discovery Ports: " + BASE_PORT + " đến " + (BASE_PORT + 9));
            System.out.println("  Timeout: " + SCAN_TIMEOUT + "ms\n");

            // Quét song song với ThreadPool
            for (int i = 1; i <= 254; i++) {
                final String ip = subnet + "." + i;
                for (int portOffset = 0; portOffset < 10; portOffset++) {
                    final int targetPort = BASE_PORT + portOffset;
                    scanExecutor.submit(() -> tryConnectToPeer(ip, targetPort));
                }
            }
        }

        System.out.println("→ Đã gửi " + (subnetRanges.size() * 254 * 10) + " yêu cầu quét");
        System.out.println("  (Chờ kết quả...)\n");
    }

    /**
     * Thử kết nối TCP đến một IP để kiểm tra có peer không
     */
    private void tryConnectToPeer(String ip, int targetPort) {
        // Không block IP local nữa để peer trên cùng 1 máy tìm thấy nhau

        try (Socket socket = new Socket()) {
            // Thử kết nối TCP
            socket.connect(new InetSocketAddress(ip, targetPort), SCAN_TIMEOUT);

            System.out.println("  [SCAN] Kết nối TCP thành công đến " + ip + ":" + targetPort);

            // Kết nối thành công, gửi thông tin
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            socket.setSoTimeout(2000); // Timeout cho đọc response

            // Gửi thông tin của mình
            out.println("PEER:" + this.peerPort);
            System.out.println("  [SCAN] Đã gửi: PEER:" + this.peerPort + " đến " + ip);

            // Nhận thông tin peer
            String response = in.readLine();
            System.out.println("  [SCAN] Nhận được: " + response + " từ " + ip);

            if (response != null && response.startsWith("PEER:")) {
                int port = Integer.parseInt(response.substring(5));

                String peerKey = ip + ":" + port;
                PeerInfo peerInfo = discoveredPeers.get(peerKey);

                if (peerInfo == null) {
                    peerInfo = new PeerInfo("peer_" + port, ip, port, "Peer-" + port);
                    discoveredPeers.put(peerKey, peerInfo);
                    System.out.println("\n✓✓✓ TÌM THẤY PEER MỚI: " + peerKey + " ✓✓✓\n");
                } else {
                    peerInfo.updateLastSeen();
                    System.out.println("  [SCAN] Cập nhật peer: " + peerKey);
                }
            } else {
                System.out.println("  [SCAN] ⚠ Response không hợp lệ từ " + ip);
            }

        } catch (ConnectException e) {
            // Connection refused - không có peer
        } catch (SocketTimeoutException e) {
            // Timeout - không có peer hoặc chậm
        } catch (IOException | NumberFormatException e) {
            // Lỗi khác
            if (e.getMessage() != null && !e.getMessage().contains("Connection refused")
                    && !e.getMessage().contains("timed out")) {
                System.out.println("  [SCAN] Lỗi kết nối " + ip + ": " + e.getMessage());
            }
        }
    }

    /**
     * Kiểm tra xem IP có phải là địa chỉ local không
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
        } catch (SocketException | UnknownHostException e) {
            // Ignore
        }
        return false;
    }

    /**
     * Gửi yêu cầu discovery thủ công (trigger quét ngay)
     */
    public void sendDiscoveryRequest() {
        System.out.println("!!! Kích hoạt quét thủ công !!!");
        scanExecutor.submit(this::scanForPeers);
    }

    /**
     * Dừng Discovery Service
     */
    public void stop() {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdownNow();
        }

        System.out.println("\n✗ TCP Discovery Service đã dừng\n");
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
            System.out.println("⊗ Removed timeout peer: " + key);
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
     * Lấy danh sách subnet đang quét (để debug)
     */
    public String getBroadcastAddress() {
        return String.join(", ", subnetRanges);
    }
}
