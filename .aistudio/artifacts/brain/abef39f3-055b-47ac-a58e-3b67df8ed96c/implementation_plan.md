# Điều Khiển Quạt FujiHome - Hồng Ngoại NEC (IR Remote)

Ứng dụng Android điều khiển từ xa chuyên dụng cho quạt FujiHome sử dụng cảm biến phát hồng ngoại (ConsumerIrManager) với giao thức chuẩn NEC 38kHz, thiết kế tối giản tập trung thao tác nhanh, phản hồi rung haptic và hiệu ứng mô phỏng thông minh.

### User Review & Critical Decisions

> [!IMPORTANT]
> Dựa trên lựa chọn của bạn trong quá trình trao đổi:
> - **Phong cách giao diện**: Tối giản, tập trung thao tác nhanh với bố cục nút bấm công thái học, độ phản hồi tức thì.
> - **Phản hồi & Tương thích**: Tích hợp rung haptic, đèn LED hồng ngoại ảo chớp sáng khi phát lệnh và chế độ mô phỏng (Simulation Mode) tự động khi thiết bị không có phần cứng IR Blaster.
> - **Phạm vi tính năng**: Tập trung chuẩn xác vào 8 phím chức năng cốt lõi theo bảng đặc tả mã lệnh NEC đã cung cấp.

---

### 1. Overview & Core Concept

- **Mục tiêu**: Cung cấp giải pháp điều khiển quạt FujiHome trực tiếp từ điện thoại Android trang bị cổng hồng ngoại (IR Blaster) mà không cần qua Wi-Fi hay phụ kiện gắn ngoài.
- **Đối tượng người dùng**: Người dùng quạt đứng/quạt cây/quạt tháp FujiHome muốn bật tắt, chỉnh gió, đảo hướng và hẹn giờ nhanh chóng từ điện thoại.
- **Giá trị cốt lõi**:
  - Tuân thủ chính xác 100% chuẩn mã NEC: Tần số sóng mang 38,000 Hz, Address `0x00` (`~Addr: 0xFF`), Leader 9ms/4.5ms, Stop bit 560µs.
  - Hoạt động trơn tru trên mọi thiết bị: Tự nhận diện phần cứng hồng ngoại thật; nếu thiết bị không hỗ trợ IR, app tự kích hoạt chế độ mô phỏng kèm giải thích trực quan và xem mã tín hiệu.

---

### 2. User Experience & Visual Design

- **Aesthetic Direction**: Phong cách tối giản hiện đại (Modern Minimalist Utility) lấy cảm hứng từ các điều khiển cao cấp, nền tối sâu chống mỏi mắt ban đêm kết hợp các điểm nhấn màu sắc phân định theo nhóm tính năng.
- **Bảng màu & Cảm xúc**:
  - Nền chủ đạo: Màu xám than kỹ thuật số (`#121417` / `#1B1E23`).
  - Màu phím Nguồn (Tắt quạt): Đỏ san hô đậm (`#E53935` / `#FF5252`).
  - Màu phím Tốc độ & Turbo: Xanh ngọc mát lạnh (`#00897B` / `#26A69A` và Cyan `#00ACC1`).
  - Màu phím Đảo hướng & Chế độ: Xanh dương êm dịu (`#1E88E5`).
  - Đèn báo IR LED ảo: Đỏ hồng ngoại phát quang (`#FF1744`) chớp sáng chân thực khi phát sóng.
- **Bố cục 8 nút bấm công thái học**:
  1. **Thanh trạng thái đỉnh**: Logo thương hiệu FujiHome, Đèn LED phát IR chớp xung, Huy hiệu trạng thái phần cứng ("Mắt IR Sẵn sàng" hoặc "Chế độ mô phỏng").
  2. **Khu vực Nguồn**: Nút "Tắt quạt" (Power Off - `0x40`) to rõ, dễ bấm ngay khi cần.
  3. **Cụm điều khiển Tốc độ (Speed)**:
     - Tăng gió (Speed + - `0x46`)
     - Giảm gió (Speed - - `0x0C`)
     - Gió mạnh (Turbo - `0x45`)
  4. **Cụm chế độ & Hướng gió**:
     - Chế độ ngủ (Sleep - `0x47`)
     - Hẹn giờ (Timer - `0x43`)
     - Quay trái / phải (Horizontal - `0x16`)
     - Quay lên / xuống (Vertical - `0x4A`)
  5. **Thanh hiển thị mã lệnh tức thời**: Dải badge nhỏ gọn hiển thị mã Hex 32-bit vừa truyền (ví dụ `0x00FF46B9`) kèm thời gian gửi để người dùng xác nhận lệnh đã phát thành công.

---

### 3. Key Product Decisions & Trade-Offs

- **Xử lý phần cứng hồng ngoại (`ConsumerIrManager`)**:
  - *Phương án chọn*: Kiểm tra `hasIrEmitter()` khi mở app. Nếu có, phát xung trực tiếp qua `transmit(38000, pattern)`. Nếu không có hoặc trên máy ảo thử nghiệm, app kích hoạt "Simulation Mode" an toàn mà không bị crash, vẫn phát rung haptic và chớp LED mô phỏng.
  - *Lý do*: Đảm bảo app tương thích tốt, không bị lỗi ngoại lệ trên các dòng máy không có mắt hồng ngoại hoặc các phiên bản Android khác nhau.
- **Giải mã và mã hóa tín hiệu NEC chuẩn xác**:
  - *Phương án chọn*: Viết thuật toán tạo mảng xung thời gian thực (Microseconds array) chuẩn 67 phần tử:
    - Leader Mark: 9000µs, Leader Space: 4500µs
    - 32 bit dữ liệu LSB-first theo từng byte (Address `0x00`, Inverted Addr `0xFF`, Command, Inverted Cmd)
    - Bit 0: 560µs Mark + 560µs Space
    - Bit 1: 560µs Mark + 1690µs Space
    - Stop bit: 560µs Mark
  - *Lý do*: Tương thích 100% với các bộ thu hồng ngoại tiêu chuẩn của quạt FujiHome, tín hiệu phát sạch và chính xác.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────┐
│                       MainActivity                          │
│        (Window Insets, Edge-to-Edge, Haptic Feedback)       │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    FujiHomeRemoteScreen                     │
│  - Trạng thái IR Hardware (Available / Simulated)           │
│  - Đèn LED IR ảo nhấp nháy phát xung                        │
│  - Bảng 8 nút điều khiển tối giản (Power, Speed, Swing...)  │
│  - Khung hiển thị thông tin mã lệnh truyền gần nhất         │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    FujiHomeRemoteViewModel                  │
│  - Quản lý trạng thái: LastCommand, IsTransmitting, Mode    │
│  - Phát lệnh: sendCommand(fujiCommand)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                   NecIrProtocolTransmitter                  │
│  - Xây dựng mảng xung µs chuẩn NEC 38kHz (67 pulses)        │
│  - Giao tiếp ConsumerIrManager.transmit()                   │
│  - Kích hoạt rung Haptic (Vibrator)                         │
└─────────────────────────────────────────────────────────────┘
```

#### Bảng ánh xạ mã lệnh chi tiết trong mã nguồn

| Tên chức năng | Command Hex | 32-bit NEC Hex | Mô tả hành vi quạt |
| :--- | :---: | :---: | :--- |
| **Tắt quạt (Power Off)** | `0x40` | `0x00FF40BF` | Tắt quạt về chế độ chờ |
| **Tăng gió (Speed +)** | `0x46` | `0x00FF46B9` | Tăng 1 cấp độ gió |
| **Giảm gió (Speed -)** | `0x0C` | `0x00FF0CF3` | Giảm 1 cấp độ gió |
| **Gió mạnh (Turbo)** | `0x45` | `0x00FF45BA` | Chuyển ngay mức gió tối đa |
| **Chế độ ngủ (Sleep)** | `0x47` | `0x00FF47B8` | Chế độ quạt êm dịu ban đêm |
| **Quay trái / phải (Horizontal)** | `0x16` | `0x00FF16E9` | Bật/tắt đảo hướng xoay ngang |
| **Quay lên / xuống (Vertical)** | `0x4A` | `0x00FF4AB5` | Bật/tắt đảo hướng gật đầu dọc |
| **Hẹn giờ (Timer)** | `0x43` | `0x00FF43BC` | Tăng mốc hẹn giờ tắt |

#### Quyền hệ thống cần bổ sung (`AndroidManifest.xml`)
- `android.permission.TRANSMIT_IR`: Cho phép phát tín hiệu hồng ngoại.
- `android.permission.VIBRATE`: Rung phản hồi xúc giác khi chạm phím.
