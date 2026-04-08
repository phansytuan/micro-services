Để triển khai Hàng đợi tin nhắn (Message Queues) bằng RabbitMQ, các bước:

___

### 1. Xác định nhu cầu về xử lý bất đồng bộ
Bước đầu tiên là nhận diện các điểm nghẽn nơi các cuộc gọi đồng bộ (request-response) đang gây chậm trễ. <br>Ví dụ: nếu dịch vụ "Customer" phải đợi dịch vụ "Notification" gửi xong email (có thể mất 10-20 giây), nó sẽ tạo ra trải nghiệm người dùng kém. Bạn nên xác định các tác vụ có thể trì hoãn / xử lý ngầm.

___

### 2. Thiết lập RabbitMQ cục bộ (Docker)
Hướng dẫn khuyên dùng **Docker** để thiết lập nhanh chóng & đồng nhất. Bạn cần tạo / cập nhật tệp `docker-compose.yaml` với các thông tin sau:
* **Image:** Sử dụng phiên bản bao gồm plugin quản lý (ví dụ: `rabbitmq:3.9.11-management-alpine`).
* **Ports (Cổng):** * `5672`: Cổng chính để ứng dụng giao tiếp.
    * `15672`: Cổng cho giao diện quản lý web (Management UI).

___

### 3. Cấu hình Exchange & Queues
Bên trong ứng dụng của bạn (video sử dụng Spring Boot làm ví dụ), bạn phải định nghĩa các thành phần cốt lõi của luồng tin nhắn:
* **Định nghĩa một Exchange:** Quyết định loại Exchange (Direct, Fanout, Topic, / Headers) dựa trên nhu cầu định tuyến.
* **Định nghĩa một Queue:** Đây là nơi tin nhắn sẽ "nằm chờ" cho đến khi được xử lý.
* **Tạo một Binding (Liên kết):** Kết nối Exchange với Queue thông qua một **Routing Key**.

___

### 4. Triển khai Producer (Người gửi)
Dịch vụ tạo ra dữ liệu (ví dụ: dịch vụ Customer) đóng vai trò là Producer. Nó gửi nội dung tin nhắn (payload) đến Exchange đã chỉ định cùng với Routing Key.

___

### 5. Triển khai Consumer (Người nhận)
Dịch vụ thực hiện tác vụ (ví dụ: dịch vụ Notification) đóng vai trò là Consumer. <br>Nó được cấu hình để lấy tin nhắn từ một Queue cụ thể. Consumer cũng nên được thiết lập để gửi **phản hồi xác nhận (acknowledgement)** lại cho broker sau khi tin nhắn đã được xử lý thành công để đảm bảo độ tin cậy.

___

### 6. Theo dõi qua Management UI
Sau khi mọi thứ đã chạy, bạn có thể đăng nhập vào `localhost:15672` (tài khoản mặc định: `guest`/`guest`) để trực quan hóa lưu lượng truy cập, kiểm tra xem tin nhắn có bị dồn ứ trong hàng đợi hay không & xác minh các Exchange có đang định tuyến chính xác hay không.