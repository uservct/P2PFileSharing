package p2p.network;

import java.io.IOException;
import java.net.*;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private final int peerPort;
    private volatile boolean running = false;
    private Thread listenerThread;
    private DatagramSocket socket;

    public DiscoveryService(int peerPort) {
        this.peerPort = peerPort;
    }
    public void startListening() {
        if (running) return;
        running = true;

        listenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(DISCOVERY_PORT));
                socket.setBroadcast(true);

                System.out.println("Discovery listener đang chạy trên cổng " + DISCOVERY_PORT);

                while (running) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    if ("DISCOVER_P2P".equals(message)) {

                        // bỏ qua nếu là chính mình gửi
                        // if (packet.getAddress().equals(InetAddress.getLocalHost())) {
                        //     continue;
                        // }

                        System.out.println("Nhận yêu cầu discovery từ: " + packet.getAddress().getHostAddress());
                        String response = "P2P_RESPONSE:" + peerPort;

                        DatagramPacket responsePacket = new DatagramPacket(
                                response.getBytes(),
                                response.length(),
                                packet.getAddress(),
                                packet.getPort()
                        );

                        socket.send(responsePacket);
                    }
                }

            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
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

        System.out.println("Discovery listener đã dừng.");
    }

    public void sendDiscoveryRequest() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {

                socket.setBroadcast(true);

                String message = "DISCOVER_P2P";
                byte[] buffer = message.getBytes();

                DatagramPacket packet = new DatagramPacket(
                        buffer,
                        buffer.length,
                        InetAddress.getByName("192.168.1.255"),
                        DISCOVERY_PORT
                );

                socket.send(packet);
                System.out.println("Đã gửi broadcast tìm peer");

                socket.setSoTimeout(5000);

                while (true) {

                    byte[] responseBuffer = new byte[1024];
                    DatagramPacket responsePacket =
                            new DatagramPacket(responseBuffer, responseBuffer.length);

                    socket.receive(responsePacket);

                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength());

                    if (response.startsWith("P2P_RESPONSE:")) {
                        // bỏ qua nếu là chính mình gửi
                        if (responsePacket.getAddress().equals(InetAddress.getLocalHost())) {
                            continue;
                        }

                        System.out.println("Tìm thấy peer: "
                                + responsePacket.getAddress().getHostAddress()
                                + " - " + response);
                    }
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Hoàn tất discovery.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
}

