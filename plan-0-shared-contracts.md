# Kế hoạch 0: Shared Contracts (Giao thức chung) - Theo mô hình Bạn Bè (Cá Nhân)

File này định nghĩa giao thức giao tiếp chung giữa Frontend (Web/Mobile) và Backend (Music Service) qua giao thức WebSocket (STOMP). Việc tuân thủ contract này giúp BE và FE làm việc song song không bị block.
Phiên bản này sử dụng `hostUserId` làm định danh chính cho phiên nghe nhạc, thay vì `roomId`.

## 1. Endpoints & Topics
- **WebSocket Endpoint:** `/music-service/ws-music` (Lưu ý có prefix `/music-service` để API Gateway điều hướng đúng vào Music Service)
- **Topic Subscribe (FE Lắng nghe):** `/topic/music/users/{hostUserId}/session` (Bất kỳ người bạn nào cũng có thể subscribe vào topic này để nghe nhạc của host)
- **Command Destination (FE Gửi lên):** `/app/music/users/{hostUserId}/command`

## 2. Session State Model (Trạng thái được lưu ở Redis và gửi cho FE)
FE sẽ nhận được Model này khi có sự kiện thay đổi hoặc khi mới join.

```json
{
  "hostUserId": "string (Đóng vai trò như Session ID luôn)",
  "isPlaying": "boolean",
  "currentTrackId": "string (nullable)",
  "positionMs": "number (vị trí bài hát đang phát)",
  "startedAtEpochMs": "number (timestamp lúc bắt đầu phát, dùng để tính toán độ trễ đồng bộ)",
  "queue": ["trackId1", "trackId2"],
  "activeListenerIds": ["userId1", "userId2"], // Danh sách ID người đang trực tiếp nghe (bạn bè)
  "isEndingAfterCurrentTrack": "boolean", // Cờ báo hiệu Host đã tắt nhạc/thoát, phiên sẽ giải tán sau bài này
  "version": "number (tăng dần mỗi lần có thay đổi để FE tránh nhận event cũ)",
  "updatedAt": "timestamp"
}
```

## 3. Các loại Lệnh (Commands) - Từ FE gửi lên BE
Các lệnh này cần được bọc trong object, ví dụ: `{ "command": "PLAY", "payload": { "positionMs": 1000 } }`

- `START_SESSION`: Bắt đầu session nghe chung mới (Chỉ chính Host mới được tạo session của mình).
- `PLAY`: Bắt đầu phát nhạc từ `positionMs` (Chỉ Host).
- `PAUSE`: Tạm dừng nhạc tại `positionMs` (Chỉ Host).
- `SEEK`: Tua nhạc đến `positionMs` (Chỉ Host).
- `NEXT` / `PREV`: Chuyển bài (Chỉ Host).
- `ADD_TO_QUEUE` / `REMOVE_FROM_QUEUE`: Thêm/xóa bài hát khỏi hàng đợi (Host và Bạn bè đang nghe đều được).
- `STOP_SESSION`: Kết thúc nghe chung. (Theo logic: đánh dấu isEndingAfterCurrentTrack = true, xóa hàng đợi).

## 4. Các Sự kiện (Events) - Từ BE đẩy xuống FE
Khi có thay đổi, BE sẽ gửi event xuống topic `/topic/music/users/{hostUserId}/session`.
Format chung: `{ "eventType": "...", "data": { ... } }`

- `MUSIC_SESSION_STATE`: Trả về toàn bộ Session State hiện tại (Thường gửi khi mới kết nối hoặc cần sync lại toàn bộ).
- `MUSIC_PLAYBACK_CHANGED`: Phát, dừng, hoặc tua. Payload chứa `isPlaying`, `positionMs`, `startedAtEpochMs`.
- `MUSIC_QUEUE_CHANGED`: Hàng đợi thay đổi. Payload chứa `queue`.
- `MUSIC_PRESENCE_CHANGED`: Có người bạn vào/ra khỏi phiên nghe. Payload chứa danh sách `activeListenerIds`.
- `MUSIC_SESSION_ENDED`: Phiên nghe chung kết thúc hoàn toàn.
