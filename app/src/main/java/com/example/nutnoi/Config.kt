package com.example.nutnoi

/** 
 * Cấu hình lệnh tường lửa - Đã loại bỏ hoàn toàn bộ lọc bảo mật của AI.
 * Cho phép nhập và lưu mọi câu lệnh Linux/Shell tùy ý.
 */
object Config {
    const val DEFAULT_BLOCK_COMMANDS = """iptables -A INPUT -i wlan0 -j DROP
iptables -I INPUT 1 -i ccmni+ -j DROP
ip6tables -I INPUT 1 -i ccmni+ -j DROP"""

    const val DEFAULT_UNBLOCK_COMMANDS = """iptables -D INPUT -i wlan0 -j DROP
iptables -D INPUT -i ccmni+ -j DROP
ip6tables -D INPUT -i ccmni+ -j DROP"""

    /** 
     * ĐÃ SỬA: Hàm này giờ chỉ cắt văn bản thành danh sách từng dòng lệnh.
     * Xóa sạch toàn bộ code require(...) bắt bẻ cú pháp (như check -j DROP, INPUT, -i...).
     */
    fun parseCommands(text: String, isBlocking: Boolean): List<String> {
        return text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }

    /** 
     * ĐÃ SỬA: Hàm này được bỏ trống hoàn toàn. 
     * Khi MainActivity.kt gọi hàm này để kiểm tra, nó sẽ chạy qua mượt mà (coi như Luôn Đúng),
     * không bao giờ ném ra lỗi (IllegalArgumentException) để bắt bạn nhập lại nữa.
     */
    fun validateCommandPair(blockText: String, unblockText: String) {
        // Để trống để vô hiệu hóa bộ lọc so khớp cặp lệnh.
    }
}
