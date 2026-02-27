package p2p.main;

import p2p.network.Peer;

public class Main {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java Main <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        Peer peer = new Peer(port);
        peer.start();
    }
}