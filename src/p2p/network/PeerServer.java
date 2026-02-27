package p2p.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server lắng nghe kết nối từ các peer khác
 */
public class PeerServer implements Runnable {
    private int port;
    private Peer peer;
    private ServerSocket serverSocket;
    private boolean running;

    public PeerServer(int port, Peer peer) {
        this.port = port;
        this.peer = peer;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[Server] Đang lắng nghe trên port " + port + "\n");
            
            while (running) {
                // Chấp nhận kết nối mới
                Socket clientSocket = serverSocket.accept();
                System.out.println("\n[Server] Kết nối mới từ: " + 
                    clientSocket.getRemoteSocketAddress());
                
                // Chuyển socket cho Peer xử lý
                peer.handleIncomingConnection(clientSocket);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Server] Lỗi: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }
    
    /**
     * Dừng server
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}