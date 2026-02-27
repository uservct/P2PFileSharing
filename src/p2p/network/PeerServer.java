package p2p.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer implements Runnable{
    private int port;
    public PeerServer(int port) {
        this.port = port;
    }
    @Override
    public void run(){
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Peer đang lắng nghe tại port " + port);

            while (true) {
                // Chấp nhận kết nối từ các peer khác
                Socket socket = serverSocket.accept();
                System.out.println("Có peer kết nối từ: " + socket.getInetAddress());

                // Tạo một thread mới để xử lý kết nối này
                new Thread(() -> handlePeerConnection(socket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
    }
}
    private void handlePeerConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            System.out.println("Nhận được tin nhắn từ peer: " + message);

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
