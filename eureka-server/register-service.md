Để triển khai **Service Discovery** (Phát hiện dịch vụ) bằng Eureka, thực hiện 5 bước:

---

### 1. Thiết lập Eureka Server (Trung tâm điều phối)
Đây là "danh bạ" trung tâm nơi tất cả các microservices sẽ đăng ký thông tin.
* **Thêm Dependency:** Trong tệp `pom.xml`, thêm thư viện `spring-cloud-starter-netflix-eureka-server`.
* **Kích hoạt:** Thêm annotation `@EnableEurekaServer` vào class chạy chính của ứng dụng Spring Boot.
* **Cấu hình (`application.yml`):**
    * Đặt cổng mặc định là `8761`.
    * Thiết lập `register-with-eureka: false` & `fetch-registry: false` để server không tự đăng ký chính nó.



---

### 2. Cấu hình Microservices thành Eureka Clients
Các dịch vụ như "Customer" (Khách hàng) hay "Fraud" (Gian lận) cần được kết nối vào hệ thống.
* **Thêm Dependency:** Thêm thư viện `spring-cloud-starter-netflix-eureka-client` vào từng microservice.
* **Kích hoạt:** Thêm annotation `@EnableEurekaClient` vào class chính của dịch vụ.
* **Khai báo địa chỉ Server:** Trong tệp cấu hình của client, cung cấp URL dẫn đến Eureka Server (ví dụ: `http://localhost:8761/eureka`) để client biết nơi gửi thông tin IP & Port của mình.

---

### 3. Thay thế URL "cứng" bằng Tên dịch vụ
Đây là bước quan trọng nhất để loại bỏ việc quản lý thủ công các địa chỉ IP.
* **Cơ chế:** Thay vì gọi API bằng địa chỉ cụ thể như `http://localhost:8081/api/...`, bạn sẽ sử dụng tên định danh của dịch vụ đã đăng ký trên Eureka, ví dụ: `http://FRAUD/api/...`.
* **Lợi ích:** Khi dịch vụ "Fraud" thay đổi IP / Port, Eureka sẽ tự động cập nhật & dịch vụ "Customer" vẫn gọi được mà không cần sửa code.



---

### 4. Triển khai Load Balancing (Cân bằng tải)
Khi 1 dịch vụ có nhiều bản thực thi (instance) đang chạy cùng lúc để chia tải.
* **Annotation `@LoadBalanced`:** Khi khởi tạo Bean cho `RestTemplate`, bạn cần thêm annotation này.
* **Hoạt động:** Spring Cloud sẽ chặn các yêu cầu gửi đi, tra cứu danh sách các instance hiện có của dịch vụ đó trên Eureka & tự động điều phối lưu lượng (thường theo thuật toán xoay vòng - Round Robin) để tránh làm quá tải 1 instance duy nhất.

---

### 5. Kiểm tra qua Dashboard của Eureka
Eureka cung cấp 1 giao diện web trực quan để quản lý.
* **Truy cập:** Mở trình duyệt & vào địa chỉ `http://localhost:8761`.
* **Nội dung:** Bạn sẽ thấy danh sách các microservice đang ở trạng thái "Up" (đang chạy), số lượng instance của mỗi loại & các thông tin kỹ thuật như dung lượng bộ nhớ, CPU đang sử dụng.

