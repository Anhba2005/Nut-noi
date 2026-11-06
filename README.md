# Nut-noi

Ứng dụng Android mẫu hiển thị nút nổi dạng bong bóng truy cập nhanh trên các ứng dụng khác.

## Tính năng
- Hỗ trợ Android 9 (API 28) trở lên.
- Yêu cầu người dùng cấp quyền **Hiển thị trên các ứng dụng khác** (`SYSTEM_ALERT_WINDOW`) qua trang cài đặt hệ thống.
- Bật/tắt nút nổi từ ứng dụng hoặc thông báo dịch vụ.
- Kéo thả nút; vị trí được lưu để dùng lại lần sau.
- Dịch vụ tiền cảnh và thông báo thường trực giúp Android giữ dịch vụ chạy ổn định hơn.

## Chạy ứng dụng
1. Mở repo bằng Android Studio và chờ Gradle Sync.
2. Dùng JDK 17 và Android SDK 34.
3. Chạy ứng dụng trên thiết bị/emulator Android 9+.
4. Nhấn **Cấp quyền overlay**, bật **Hiển thị trên các ứng dụng khác** trong Cài đặt, rồi quay lại ứng dụng.
5. Nhấn **Bật nút nổi**. Chạm nút để thử; kéo để di chuyển. Tắt bằng nút trong ứng dụng hoặc thông báo.

## Lưu ý
Android không cấp quyền overlay tự động. Dịch vụ chỉ bắt đầu sau thao tác bật rõ ràng của người dùng và dừng qua giao diện hoặc thông báo. Một số hãng có thể áp dụng giới hạn chạy nền riêng; nếu thiết bị tự dừng dịch vụ, kiểm tra cài đặt pin/tự khởi chạy của hãng.
