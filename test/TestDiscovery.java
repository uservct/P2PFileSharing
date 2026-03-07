package test;

import java.util.List;
import p2p.model.PeerInfo;
import p2p.network.DiscoveryService;

/**
 * Test đơn giản để verify discovery mechanism
 */
public class TestDiscovery {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("========================================");
            System.out.println("P2P Discovery Test");
            System.out.println("========================================");
            System.out.println("Usage: java test.TestDiscovery <port>");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  Terminal 1: java -cp bin test.TestDiscovery 5000");
            System.out.println("  Terminal 2: java -cp bin test.TestDiscovery 5001");
            System.out.println("  Terminal 3: java -cp bin test.TestDiscovery 5002");
            System.out.println("========================================");
            return;
        }

        int port = Integer.parseInt(args[0]);
        System.out.println("\n========================================");
        System.out.println("Starting Discovery Test on Port: " + port);
        System.out.println("========================================\n");

        DiscoveryService discovery = new DiscoveryService(port);
        discovery.startListening();

        System.out.println("Discovery service started.");
        System.out.println("Listening for peers...");
        System.out.println("Broadcasting presence every 3 seconds...");
        System.out.println("\nPress Ctrl+C to exit\n");

        // Hiển thị peers mỗi 5 giây
        int count = 0;
        while (true) {
            Thread.sleep(5000);
            count++;

            List<PeerInfo> peers = discovery.getPeerInfoList();

            System.out.println("\n[" + count + "] === Discovered Peers: " + peers.size() + " ===");
            if (peers.isEmpty()) {
                System.out.println("  No peers found yet. Waiting...");
            } else {
                for (PeerInfo peer : peers) {
                    long secondsAgo = (System.currentTimeMillis() - peer.getLastSeen()) / 1000;
                    System.out.println("  ✓ " + peer.getIpAddress() + ":" + peer.getPort() +
                            " (seen " + secondsAgo + "s ago)");
                }
            }
            System.out.println("=====================================");
        }
    }
}
