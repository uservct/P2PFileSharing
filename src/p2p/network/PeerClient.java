package p2p.network;

import java.io.*;
import java.net.*;

public class PeerClient {
    public static void sendMessage(String host, int port, String message) {
        try {
            Socket socket = new Socket(host, port);

            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            writer.println(message);

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
