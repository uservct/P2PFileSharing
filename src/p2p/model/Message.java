package p2p.model;

import java.io.Serializable;

/**
 * Lớp đại diện cho message trao đổi giữa các peer
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Các loại message trong hệ thống P2P
    public enum Type {
        HELLO,          // Chào hỏi khi kết nối
        GOODBYE,        // Ngắt kết nối
        FILE_LIST,      // Danh sách file của peer
        FILE_REQUEST,   // Yêu cầu tải file
        FILE_RESPONSE,  // Phản hồi yêu cầu file (có/không)
        FILE_DATA,      // Dữ liệu file
        SEARCH_REQUEST, // Tìm kiếm file
        SEARCH_RESPONSE // Kết quả tìm kiếm
    }
    
    private Type type;
    private String senderId;    // ID của peer gửi
    private Object payload;     // Dữ liệu đính kèm
    private long timestamp;
    
    public Message(Type type, String senderId, Object payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public Type getType() {
        return type;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("Message[%s từ %s: %s]", type, senderId, payload);
    }
}
