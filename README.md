# P2P File Sharing - Đề 12

Ứng dụng chia sẻ file ngang hàng (Peer-to-Peer File Sharing) cho môn Lập Trình Mạng.

## Đề tài

**Đề 12: Xây dựng ứng dụng chia sẻ file ngang hàng (P2P File Sharing)**

- **Mô tả:** Các client gửi/nhận file trực tiếp với nhau, **không có server trung tâm**.
- **Công nghệ:** Socket **TCP + Discovery mechanism**.
- **Mở rộng:** Tìm kiếm file, phân tán dữ liệu, giao diện đồ họa.

## Trạng thái hiện tại

### Chức năng cốt lõi (đã có)
- ✅ Kết nối P2P trực tiếp giữa các peer
- ✅ Gửi/Nhận file qua TCP
- ✅ Cơ chế xác nhận khi nhận file (Accept/Reject)
- ✅ Tự động phát hiện peer bằng discovery mechanism
- ✅ Giao diện đồ họa Swing (chọn thư mục, xem peers, gửi file)

### Chức năng mở rộng
- ✅ Tìm kiếm file theo từ khóa trên nhiều peer
- ✅ Tải file từ peer khác theo kết quả tìm kiếm
- 🔜 Phân tán dữ liệu (distributed storage)


## Kiến trúc kỹ thuật

### 1) File transfer (TCP)

Ứng dụng dùng kết nối TCP trực tiếp giữa 2 peer để gửi dữ liệu file.

Luồng gửi file cơ bản:

```
Client -> Server
1. Gửi tên file (text + '\n')
2. Gửi kích thước file (text + '\n')
3. Đợi phản hồi ACCEPT/REJECT
4. Nếu ACCEPT thì gửi binary data
```

### 2) Discovery mechanism (TCP-based)

Project hiện tại dùng **TCP discovery** (không phải UDP broadcast):

- Mỗi peer mở discovery server tại cổng: `9000 + (peerPort % 1000)`
- Quét các dải subnet nội bộ để tìm peer đang mở discovery port
- Làm mới danh sách peer theo chu kỳ + khi người dùng bấm "Làm mới danh sách"
- Peer không phản hồi quá 30 giây sẽ bị loại khỏi danh sách

### 3) Tìm kiếm và tải file

`PeerServer` hỗ trợ thêm các request dạng text:

- `SEARCH:<keyword>` -> trả về danh sách file khớp
- `SEND:<filename>` -> trả về nội dung file để peer khác tải về

## Cấu trúc dự án

```text
P2PFileSharing/
├── src/                                # Mã nguồn chính
│   └── p2p/
│       ├── main/
│       │   └── Main.java              # Điểm khởi chạy ứng dụng
│       ├── gui/                        # Giao diện Swing
│       │   ├── MainWindow.java        # Cửa sổ chính
│       │   ├── PeersPanel.java        # Panel danh sách peer + thao tác gửi
│       │   ├── SearchPanel.java       # Panel tìm kiếm & tải file
│       │   ├── SharedFilesPanel.java  # Hiển thị file đang chia sẻ
│       │   └── SharedFolderPanel.java # Chọn/thay đổi thư mục chia sẻ
│       ├── network/                    # Tầng mạng TCP
│       │   ├── PeerClient.java        # Client gửi request/gửi file
│       │   ├── PeerServer.java        # Server nhận request/file, xử lý SEARCH/SEND
│       │   └── DiscoveryService.java  # Discovery peer trong LAN (TCP-based)
│       ├── model/                      # Model dữ liệu
│       │   ├── PeerInfo.java          # Thông tin peer (ip/port/trạng thái)
│       │   └── FileSearchResult.java  # Kết quả tìm kiếm file từ peer
│       └── util/
│           └── FileManager.java       # Tiện ích thao tác file/thư mục chia sẻ
├── bin/                                # File .class sau khi biên dịch
└── README.md                           # Tài liệu mô tả project
```

## Cách build và chạy

### Biên dịch

```bash
cd d:\Code\LTM\Project\P2PFileSharing
javac -d bin -sourcepath src src/p2p/main/Main.java
```

### Chạy ứng dụng (GUI)

```bash
java -cp bin p2p.main.Main
```

> Khi chạy, chương trình sẽ yêu cầu nhập `port` (ví dụ: 5000, 5001, 5002). Mỗi peer dùng một port khác nhau.

## Hướng dẫn sử dụng nhanh

1. Mở peer A, nhập port (ví dụ `5000`), chọn thư mục chia sẻ.
2. Mở peer B, nhập port khác (ví dụ `5001`), chọn thư mục chia sẻ.
3. Ở panel **Danh sách Peers**, bấm **Làm mới danh sách** để discover peer.
4. Gửi file:
   - Chọn peer đích
   - Chọn file trong danh sách hoặc bấm **Gửi file khác...**
5. Tìm kiếm file:
   - Vào tab **Tìm kiếm File**
   - Nhập từ khóa, bấm **Tìm kiếm**
   - Chọn kết quả và bấm **Tải về**

## Lưu ý triển khai

- Hiện tại project chạy theo **GUI mode**.
- Chưa triển khai phần phân tán dữ liệu (distributed storage).
- Chức năng tìm kiếm là theo tên file (contains, không phân biệt hoa/thường).

## Troubleshooting

### Không bind được port
- Chọn port khác trong khoảng `1024-65535`
- Kiểm tra port đang bị chiếm trên Windows:
  `netstat -ano | findstr :<port>`

### Không thấy peer
- Đảm bảo các máy cùng LAN/subnet
- Kiểm tra firewall có cho phép TCP kết nối nội bộ
- Bấm lại nút **Làm mới danh sách**

### Gửi/tải file thất bại
- Kiểm tra peer đích còn online
- Kiểm tra thư mục chia sẻ có quyền ghi
- Xem log console để xác định lỗi cụ thể

## Tác giả

Project P2P File Sharing - Đề tài Lập Trình Mạng (Đề 12)
