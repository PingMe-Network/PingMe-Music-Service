# Kế hoạch 2: Backend - Music WS & Session Engine (Mô hình Bạn Bè)

**Trách nhiệm của Music Service (Phần Session):** Mở cổng WebSocket độc lập, nhận lệnh điều khiển (Play/Pause), quản lý trạng thái đang phát (Session State) trên Redis và broadcast (phát sóng) trạng thái cho các bạn bè đang nghe.

## 1. WebSocket & STOMP Foundation
- Cấu hình `@EnableWebSocketMessageBroker` trong Music Service.
- Thiết lập endpoint `/music-service/ws-music` (hỗ trợ SockJS nếu cần). Đảm bảo API Gateway map đúng prefix này.
- Cấu hình `ChannelInterceptor` ở bước `CONNECT` để xác thực token JWT, lấy ra `userId` và gán vào `Principal` của session.

## 2. Quản lý Hiện diện (Presence / Active Listeners)
- Viết `ApplicationListener<SessionSubscribeEvent>`: 
  - Khi một người bạn subscribe vào `/topic/music/users/{hostUserId}/session`, gọi API nội bộ sang Core (REST Client hoặc FeignClient): `GET /api/users/{hostUserId}/is-friend/{userId}`.
  - Nếu kết quả trả về là `true` (hoặc `userId == hostUserId`), cho phép subscribe và thêm `userId` vào tập hợp `activeListenerIds` trong Redis Session State.
  - Nếu `false`, ném exception từ chối subscribe.
  - Gửi event `MUSIC_PRESENCE_CHANGED` cho toàn bộ phiên.
- Viết `ApplicationListener<SessionDisconnectEvent>` (hoặc Unsubscribe):
  - Khi user ngắt kết nối, xóa `userId` khỏi `activeListenerIds`.
  - Gửi event `MUSIC_PRESENCE_CHANGED`.
  - **[LOGIC TỰ GIẢI TÁN PHÒNG]**: Nếu user ngắt kết nối là `Host` (tức là `userId == hostUserId`) hoặc Host chủ động gửi `STOP_SESSION`:
    - KHÔNG xóa Session ngay lập tức.
    - Cập nhật state: `isEndingAfterCurrentTrack = true` và làm rỗng mảng `queue`.
    - Broadcast State mới. Frontend sẽ tự phát hết bài rồi tự ngắt kết nối.

## 3. Redis Session Engine
- Tạo class quản lý State: `MusicSessionRedisRepository`.
- Sử dụng Redis Hash hoặc chuỗi JSON để lưu `MusicSessionState`. Key format: `music_session:{hostUserId}`.
- Mỗi lần update State, tăng trường `version` lên 1.

## 4. Command Handlers
- Tạo các `@MessageMapping` tương ứng với giao thức ở `plan-0-shared-contracts.md` (Destination: `/app/music/users/{hostUserId}/command`).
- **Bảo mật / Phân quyền:**
  - Nếu `userId == hostUserId`, cho phép tất cả các lệnh (PLAY, PAUSE, SEEK, NEXT, PREV...).
  - Nếu `userId != hostUserId`, chỉ cho phép các lệnh tương tác chung như `ADD_TO_QUEUE` (nếu có tính năng thêm bài chung), nhưng CẦN kiểm tra qua Core API xem `userId` có còn là bạn bè với `hostUserId` không trước khi xử lý (có thể lưu cache kết quả check bạn bè trong Redis khoảng 5-10 phút để giảm tải gọi sang Core).

## Tiêu chí hoàn thành
- User A (Host) kết nối và tạo session thành công.
- User B (Bạn của A) kết nối thành công, Server gọi API Core xác nhận đúng là bạn và cho phép.
- User C (Người lạ) cố kết nối vào phiên của A sẽ bị Server chặn ngay lúc Subscribe.
