package p2p.network;

import p2p.model.*;
import p2p.file.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lớp đại diện cho một peer trong mạng P2P
 */
public class Peer {
    private String peerId;          // ID duy nhất của peer
    private int port;               // Port lắng nghe
    private PeerServer server;      // Server lắng nghe kết nối
    private FileManager fileManager; // Quản lý file chia sẻ
    
    // Danh sách các peer đã kết nối
    private Map<String, Socket> connectedPeers;
    
    // Danh sách file từ các peer khác
    private Map<String, List<FileInfo>> remoteFiles; // peerId -> danh sách file
    
    private ExecutorService executor;
    private boolean running;
    
    public Peer(int port) {
        this.port = port;
        this.peerId = "Peer-" + port;
        this.connectedPeers = new ConcurrentHashMap<>();
        this.remoteFiles = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.running = true;
        
        // Khởi tạo FileManager với thư mục shared
        this.fileManager = new FileManager("shared", peerId);
        
        // Khởi tạo server
        this.server = new PeerServer(port, this);
    }
    
    public void start() {
        System.out.println("==============================================");
        System.out.println("       P2P FILE SHARING - " + peerId);
        System.out.println("==============================================");
        System.out.println("Port: " + port);
        System.out.println("Thư mục chia sẻ: " + fileManager.getSharedFolder());
        System.out.println("Số file đang chia sẻ: " + fileManager.getFileList().size());
        System.out.println("==============================================\n");
        
        // Khởi động server
        new Thread(server).start();
        
        // Hiển thị menu CLI
        showMenu();
    }
    
    /**
     * Hiển thị menu CLI
     */
    private void showMenu() {
        Scanner scanner = new Scanner(System.in);
        
        while (running) {
            System.out.println("\n========== MENU ==========");
            System.out.println("1. Kết nối đến peer");
            System.out.println("2. Xem file của tôi");
            System.out.println("3. Tìm kiếm file trên mạng");
            System.out.println("4. Tải file");
            System.out.println("5. Xem peers đã kết nối");
            System.out.println("6. Refresh danh sách file");
            System.out.println("0. Thoát");
            System.out.print("Chọn: ");
            
            try {
                int choice = scanner.nextInt();
                scanner.nextLine(); // consume newline
                
                switch (choice) {
                    case 1:
                        connectToPeer(scanner);
                        break;
                    case 2:
                        showMyFiles();
                        break;
                    case 3:
                        searchFiles(scanner);
                        break;
                    case 4:
                        downloadFile(scanner);
                        break;
                    case 5:
                        showConnectedPeers();
                        break;
                    case 6:
                        fileManager.scanFiles();
                        System.out.println("✓ Đã cập nhật danh sách file");
                        break;
                    case 0:
                        shutdown();
                        return;
                    default:
                        System.out.println("Lựa chọn không hợp lệ!");
                }
            } catch (Exception e) {
                System.out.println("Lỗi: " + e.getMessage());
                scanner.nextLine(); // clear buffer
            }
        }
    }
    
    /**
     * Kết nối đến peer khác
     */
    private void connectToPeer(Scanner scanner) {
        System.out.print("Nhập host (localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";
        
        System.out.print("Nhập port: ");
        int peerPort = scanner.nextInt();
        scanner.nextLine();
        
        try {
            Socket socket = new Socket(host, peerPort);
            String peerKey = host + ":" + peerPort;
            
            // Lưu kết nối
            connectedPeers.put(peerKey, socket);
            
            // Gửi message HELLO
            sendMessage(socket, new Message(Message.Type.HELLO, peerId, 
                "Xin chào từ " + peerId));
            
            // Bắt đầu lắng nghe message từ peer này
            executor.submit(() -> handlePeerConnection(socket, peerKey));
            
            System.out.println("✓ Đã kết nối đến " + peerKey);
            
            // Tự động gửi yêu cầu lấy danh sách file
            requestFileList(socket);
            
        } catch (IOException e) {
            System.out.println("✗ Không thể kết nối: " + e.getMessage());
        }
    }
    
    /**
     * Hiển thị file của tôi
     */
    private void showMyFiles() {
        List<FileInfo> files = fileManager.getFileList();
        
        if (files.isEmpty()) {
            System.out.println("\nKhông có file nào trong thư mục chia sẻ.");
            System.out.println("Hãy copy file vào thư mục 'shared' và chọn '6. Refresh'");
            return;
        }
        
        System.out.println("\n========== FILE CỦA TÔI ==========");
        for (int i = 0; i < files.size(); i++) {
            FileInfo file = files.get(i);
            System.out.printf("%d. %s\n", i + 1, file);
        }
    }
    
    /**
     * Tìm kiếm file trên mạng
     */
    private void searchFiles(Scanner scanner) {
        if (connectedPeers.isEmpty()) {
            System.out.println("✗ Chưa kết nối đến peer nào!");
            return;
        }
        
        System.out.print("Nhập từ khóa tìm kiếm: ");
        String keyword = scanner.nextLine().trim();
        
        if (keyword.isEmpty()) {
            System.out.println("✗ Từ khóa không được rỗng!");
            return;
        }
        
        System.out.println("Đang tìm kiếm...");
        
        // Gửi yêu cầu tìm kiếm đến tất cả peer
        Message searchMsg = new Message(Message.Type.SEARCH_REQUEST, peerId, keyword);
        for (Socket socket : connectedPeers.values()) {
            sendMessage(socket, searchMsg);
        }
        
        // Đợi một chút để nhận kết quả
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Hiển thị kết quả
        showSearchResults(keyword);
    }
    
    /**
     * Hiển thị kết quả tìm kiếm
     */
    private void showSearchResults(String keyword) {
        System.out.println("\n========== KẾT QUẢ TÌM KIẾM ==========");
        boolean found = false;
        
        for (Map.Entry<String, List<FileInfo>> entry : remoteFiles.entrySet()) {
            String remotePeerId = entry.getKey();
            List<FileInfo> files = entry.getValue();
            
            for (FileInfo file : files) {
                if (file.getFileName().toLowerCase().contains(keyword.toLowerCase())) {
                    System.out.printf("- %s (từ %s)\n", file, remotePeerId);
                    found = true;
                }
            }
        }
        
        if (!found) {
            System.out.println("Không tìm thấy file nào!");
        }
    }
    
    /**
     * Tải file
     */
    private void downloadFile(Scanner scanner) {
        if (remoteFiles.isEmpty()) {
            System.out.println("✗ Chưa có thông tin file từ peer nào!");
            System.out.println("Hãy chọn '3. Tìm kiếm file' trước");
            return;
        }
        
        // Hiển thị tất cả file có thể tải
        System.out.println("\n========== FILE CÓ THỂ TẢI ==========");
        List<FileInfo> allFiles = new ArrayList<>();
        
        for (Map.Entry<String, List<FileInfo>> entry : remoteFiles.entrySet()) {
            String remotePeerId = entry.getKey();
            for (FileInfo file : entry.getValue()) {
                allFiles.add(file);
                System.out.printf("%d. %s (từ %s)\n", 
                    allFiles.size(), file, remotePeerId);
            }
        }
        
        if (allFiles.isEmpty()) {
            System.out.println("Không có file nào!");
            return;
        }
        
        System.out.print("Chọn số thứ tự file cần tải: ");
        int choice = scanner.nextInt();
        scanner.nextLine();
        
        if (choice < 1 || choice > allFiles.size()) {
            System.out.println("✗ Lựa chọn không hợp lệ!");
            return;
        }
        
        FileInfo selectedFile = allFiles.get(choice - 1);
        
        // Tìm socket của peer có file
        Socket targetSocket = findPeerSocket(selectedFile.getOwnerId());
        
        if (targetSocket == null) {
            System.out.println("✗ Không tìm thấy kết nối đến peer: " + selectedFile.getOwnerId());
            return;
        }
        
        // Gửi yêu cầu tải file
        sendMessage(targetSocket, new Message(Message.Type.FILE_REQUEST, peerId, 
            selectedFile.getFileName()));
        
        System.out.println("Đang tải file...");
    }
    
    /**
     * Tìm socket của peer theo ID
     */
    private Socket findPeerSocket(String targetPeerId) {
        // Trong trường hợp đơn giản, ta dùng peerKey
        // Sau này có thể cải thiện bằng cách lưu mapping peerId -> socket
        for (Socket socket : connectedPeers.values()) {
            return socket; // Tạm thời trả về socket đầu tiên
        }
        return null;
    }
    
    /**
     * Hiển thị danh sách peer đã kết nối
     */
    private void showConnectedPeers() {
        System.out.println("\n========== PEERS ĐÃ KẾT NỐI ==========");
        if (connectedPeers.isEmpty()) {
            System.out.println("(chưa có)");
        } else {
            connectedPeers.keySet().forEach(System.out::println);
        }
    }
    
    /**
     * Xử lý kết nối từ peer (được gọi bởi PeerServer hoặc khi connect)
     */
    public void handleIncomingConnection(Socket socket) {
        String peerKey = socket.getRemoteSocketAddress().toString();
        connectedPeers.put(peerKey, socket);
        executor.submit(() -> handlePeerConnection(socket, peerKey));
    }
    
    /**
     * Xử lý các message từ peer
     */
    private void handlePeerConnection(Socket socket, String peerKey) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (running) {
                Message msg = (Message) in.readObject();
                System.out.println("\n[" + peerKey + "] " + msg);
                
                // Xử lý message dựa trên loại
                switch (msg.getType()) {
                    case HELLO:
                        // Phản hồi bằng danh sách file của mình
                        sendFileList(socket);
                        break;
                        
                    case FILE_LIST:
                        // Lưu danh sách file từ peer
                        @SuppressWarnings("unchecked")
                        List<FileInfo> files = (List<FileInfo>) msg.getPayload();
                        remoteFiles.put(msg.getSenderId(), files);
                        System.out.println("✓ Đã nhận " + files.size() + " file từ " + msg.getSenderId());
                        break;
                        
                    case FILE_REQUEST:
                        // Gửi file được yêu cầu
                        String fileName = (String) msg.getPayload();
                        sendFileData(socket, fileName);
                        break;
                        
                    case SEARCH_REQUEST:
                        // Tìm kiếm và gửi kết quả
                        String keyword = (String) msg.getPayload();
                        List<FileInfo> results = fileManager.searchFiles(keyword);
                        sendMessage(socket, new Message(Message.Type.SEARCH_RESPONSE, 
                            peerId, results));
                        break;
                        
                    case SEARCH_RESPONSE:
                        // Nhận kết quả tìm kiếm
                        @SuppressWarnings("unchecked")
                        List<FileInfo> searchResults = (List<FileInfo>) msg.getPayload();
                        remoteFiles.put(msg.getSenderId(), searchResults);
                        break;
                        
                    case FILE_RESPONSE:
                        // Nhận phản hồi về file request
                        Boolean hasFile = (Boolean) msg.getPayload();
                        if (hasFile) {
                            System.out.println("✓ Peer có file, bắt đầu tải...");
                        } else {
                            System.out.println("✗ Peer không có file!");
                        }
                        break;
                }
            }
        } catch (EOFException e) {
            System.out.println("[" + peerKey + "] Ngắt kết nối");
        } catch (Exception e) {
            System.out.println("[" + peerKey + "] Lỗi: " + e.getMessage());
        } finally {
            connectedPeers.remove(peerKey);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Gửi message đến peer
     */
    private void sendMessage(Socket socket, Message message) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.out.println("Lỗi gửi message: " + e.getMessage());
        }
    }
    
    /**
     * Yêu cầu danh sách file từ peer
     */
    private void requestFileList(Socket socket) {
        sendMessage(socket, new Message(Message.Type.HELLO, peerId, null));
    }
    
    /**
     * Gửi danh sách file của mình
     */
    private void sendFileList(Socket socket) {
        List<FileInfo> files = fileManager.getFileList();
        sendMessage(socket, new Message(Message.Type.FILE_LIST, peerId, files));
    }
    
    /**
     * Gửi dữ liệu file
     */
    private void sendFileData(Socket socket, String fileName) {
        FileInfo fileInfo = fileManager.findFile(fileName);
        
        if (fileInfo == null) {
            sendMessage(socket, new Message(Message.Type.FILE_RESPONSE, peerId, false));
            return;
        }
        
        // Gửi xác nhận có file
        sendMessage(socket, new Message(Message.Type.FILE_RESPONSE, peerId, true));
        
        // Gửi file
        try {
            FileTransferHandler.sendFile(socket, fileInfo);
        } catch (IOException e) {
            System.out.println("Lỗi gửi file: " + e.getMessage());
        }
    }
    
    /**
     * Tắt peer
     */
    private void shutdown() {
        System.out.println("\nĐang tắt peer...");
        running = false;
        
        // Đóng tất cả kết nối
        for (Socket socket : connectedPeers.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        executor.shutdown();
        server.stop();
        
        System.out.println("Đã tắt. Tạm biệt!");
        System.exit(0);
    }
    
    public String getPeerId() {
        return peerId;
    }
}