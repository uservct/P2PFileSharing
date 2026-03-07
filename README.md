# P2P File Sharing Application

Ứng dụng chia sẻ file ngang hàng (Peer-to-Peer) sử dụng TCP và UDP Discovery Mechanism.

## Tính năng

### Chức năng cơ bản
- ✅ **Kết nối P2P thuần túy**: Không cần server trung gian, các peer kết nối trực tiếp với nhau
- ✅ **UDP Discovery**: Tự động tìm kiếm các peer trong mạng LAN
- ✅ **Gửi/Nhận file qua TCP**: Transfer file trực tiếp giữa các peer
- ✅ **Giao diện GUI**: Giao diện đồ họa Swing dễ sử dụng
- ✅ **Quản lý thư mục chia sẻ**: Thiết lập thư mục để chia sẻ file
- ✅ **Hiển thị danh sách peer**: Xem các peer đang online
- ✅ **Hiển thị danh sách file**: Xem file trong thư mục chia sẻ

### Tính năng mở rộng (sẽ phát triển sau)
- 🔜 Tìm kiếm file theo tên
- 🔜 Phân tán dữ liệu (distributed storage)
- 🔜 Tìm kiếm nâng cao với metadata
- 🔜 Transfer progress bar chi tiết
- 🔜 Quản lý lịch sử transfer

## Cấu trúc dự án

```
P2PFileSharing/
├── src/
│   └── p2p/
│       ├── main/
│       │   └── Main.java              # Entry point (GUI/CLI mode)
│       ├── model/
│       │   └── PeerInfo.java          # Data model cho peer
│       ├── network/
│       │   ├── PeerClient.java        # TCP client để gửi file
│       │   ├── PeerServer.java        # TCP server để nhận file
│       │   └── DiscoveryService.java  # UDP discovery service
│       ├── util/
│       │   └── FileManager.java       # Quản lý thư mục chia sẻ
│       └── gui/
│           └── MainWindow.java        # Giao diện Swing
└── shared/                             # Thư mục chia sẻ mặc định
```

## Cách sử dụng

### Biên dịch

```bash
cd d:\Code\LTM\Project\P2PFileSharing
javac -d bin -sourcepath src src/p2p/main/Main.java
```

### Chạy GUI Mode (mặc định)

```bash
java -cp bin p2p.main.Main
```

Nhập cổng khi được yêu cầu (ví dụ: 5000, 5001, 5002...). Mỗi peer cần port khác nhau.

### Chạy CLI Mode

```bash
java -cp bin p2p.main.Main --cli
```

### Hướng dẫn sử dụng GUI

1. **Khởi động peer đầu tiên**:
   - Chạy ứng dụng, nhập port (ví dụ: 5000)
   - Chọn thư mục chia sẻ (hoặc để mặc định là `shared/`)
   - Copy file vào thư mục chia sẻ
   - Click "Làm mới danh sách file"

2. **Khởi động peer thứ hai** (terminal/window khác):
   - Chạy ứng dụng, nhập port khác (ví dụ: 5001)
   - Click "Làm mới danh sách" trong panel Peers
   - Peer 1 và Peer 2 sẽ tự động tìm thấy nhau

3. **Gửi file**:
   - Chọn peer đích trong bảng "Danh sách Peers"
   - Chọn file trong "File trong thư mục chia sẻ"
   - Click "Gửi file đã chọn"
   - Hoặc click "Gửi file khác..." để gửi file ngoài thư mục chia sẻ

4. **Nhận file**:
   - File nhận được tự động lưu vào thư mục `shared/`
   - Sẽ có thông báo trong Log khi nhận được file
   - Click "Làm mới danh sách file" để xem file mới nhận

## Kiến trúc kỹ thuật

### TCP Protocol (File Transfer)

Style đơn giản theo file mẫu với `InputStream`/`OutputStream`:

```
Client → Server:
1. Gửi tên file (UTF-8 text + '\n')
2. Gửi kích thước file (số dạng text + '\n')
3. Gửi dữ liệu file (binary bytes)
```

### UDP Protocol (Peer Discovery)

```
Discovery Request: "DISCOVER:<peerPort>:<discoveryPort>:<responsePort>"
Discovery Response: "RESPONSE:<peerPort>:<discoveryPort>"
```

- Mỗi peer listen trên port discovery: `8888 + (peerPort % 10000)`
- Discovery scan tất cả port 8888-8899 để tìm peer
- Peer timeout: 30 giây (tự động xóa nếu không phản hồi)

### Threading Model

- **Main Thread**: Swing Event Dispatch Thread (EDT) cho GUI
- **Server Thread**: Accept connections trong background
- **Handler Threads**: Mỗi file transfer có thread riêng
- **Discovery Thread**: UDP listener và scanner
- **SwingWorker**: Background file transfer với progress callback

## Code Style

Theo đúng yêu cầu file mẫu giảng viên:
- ✅ Sử dụng socket TCP thuần với `InputStream`/`OutputStream`
- ✅ Không dùng framework phức tạp
- ✅ Multi-threaded đơn giản với `new Thread(() -> ...).start()`
- ✅ Try-finally pattern để đóng socket
- ✅ Text-based protocol dễ debug

## Demo

### Chạy 3 peers trên cùng máy:

**Terminal 1:**
```bash
java -cp bin p2p.main.Main
# Nhập port: 5000
```

**Terminal 2:**
```bash
java -cp bin p2p.main.Main
# Nhập port: 5001
```

**Terminal 3:**
```bash
java -cp bin p2p.main.Main
# Nhập port: 5002
```

Cả 3 peer sẽ tự động discover nhau và có thể gửi file cho nhau.

## Troubleshooting

### Port already in use
- Chọn port khác (1024-65535)
- Kill process đang dùng port: `netstat -ano | findstr :<port>`

### Không tìm thấy peer
- Kiểm tra firewall có chặn UDP port 8888-8899 không
- Đảm bảo cả 2 peer đang chạy trên cùng mạng LAN
- Click "Làm mới danh sách" để scan lại

### File không gửi được
- Kiểm tra peer đích có online không
- Kiểm tra firewall có chặn TCP port không
- Xem log panel để biết lỗi chi tiết

## Tác giả

Project P2P File Sharing - Đề tài Lập Trình Mạng
