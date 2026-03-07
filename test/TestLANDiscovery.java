package test;
import p2p.network.DiscoveryService;
import p2p.model.PeerInfo;
import java.net.*;
import java.util.Enumeration;
import java.util.List;

public class TestLANDiscovery {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("========================================");
            System.out.println("  LAN Discovery Test");
            System.out.println("========================================");
            System.out.println("Usage: java test.TestLANDiscovery <port>");
            System.out.println("Example: java test.TestLANDiscovery 5000");
            System.out.println();
            System.out.println("Run this on multiple computers in same LAN!");
            System.out.println("========================================");
            return;
        }

        int port = Integer.parseInt(args[0]);

        System.out.println("========================================");
        System.out.println("  LAN Discovery Test - Port " + port);
        System.out.println("========================================");

        // Hiển thị network info
        printNetworkInfo();

        System.out.println("\n✓ Starting peer on port: " + port);

        DiscoveryService discovery = new DiscoveryService(port);
        discovery.startListening();

        System.out.println("✓ Broadcast address: " + discovery.getBroadcastAddress());
        System.out.println("\n⟳ Discovering peers on LAN...");
        System.out.println("Press Ctrl+C to exit\n");

        // Hiển thị peers mỗi 5 giây
        int count = 0;
        while (true) {
            Thread.sleep(5000);
            count++;

            List<PeerInfo> peers = discovery.getPeerInfoList();

            System.out.println("\n[" + count + "] === Discovered Peers: " + peers.size() + " ===");
            if (peers.isEmpty()) {
                System.out.println("  ⊗ No peers found yet...");
                System.out.println("  ℹ Make sure:");
                System.out.println("    - Other peers running on same LAN");
                System.out.println("    - Firewall allows UDP 9000-9099");
                System.out.println("    - Network broadcast enabled");
            } else {
                for (PeerInfo peer : peers) {
                    long secondsAgo = (System.currentTimeMillis() - peer.getLastSeen()) / 1000;
                    System.out.println("  ✓ " + peer.getIpAddress() + ":" + peer.getPort()
                            + " (seen " + secondsAgo + "s ago)");
                }
            }
        }
    }

    private static void printNetworkInfo() {
        System.out.println("\n=== Network Information ===");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                System.out.println("\nInterface: " + ni.getDisplayName());

                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress ip = addr.getAddress();
                    if (ip instanceof Inet4Address) {
                        System.out.println("  IP: " + ip.getHostAddress());
                        if (addr.getBroadcast() != null) {
                            System.out.println("  Broadcast: " + addr.getBroadcast().getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("========================================");
    }
}
