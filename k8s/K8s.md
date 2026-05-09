Các thành phần & khái niệm của **Kubernetes (K8s)**:

* **Nodes/ Namespaces:** Các khung nét đứt bao quanh Customer, Fraud, RabbitMQ... đại diện cho sự phân chia logic các tài nguyên trong cụm.
* **Pods:** Các biểu tượng hình lập phương màu trắng bên trong mỗi khung đại diện cho các Pod đang chạy các container ứng dụng (như dịch vụ Java Spring Boot của bạn).
* **Services:** Các biểu tượng hình lục giác có sơ đồ phân nhánh (nối giữa các Pod) chính là các K8s Service, đóng vai trò làm đầu mối nhận và điều phối traffic nội bộ.
* **Ingress / Load Balancer:** "External LB" là thành phần tiếp nhận traffic từ ngoài Internet để dẫn vào trong Cluster.
* **Persistent Volumes (PV) & Claims (PVC):** Các biểu tượng hình trụ (database) kết nối với Pod cho thấy việc sử dụng bộ nhớ ngoài để lưu trữ dữ liệu bền vững cho Postgres và RabbitMQ.
* **Deployment/ Orchestration:** Biểu tượng mũi tên xoay tròn phía trên các khung (Customer, Fraud...) ám chỉ việc quản lý vòng đời, tự động restart hoặc cập nhật các Pod.
* **Control Plane (kubectl):** Hành động "kubectl apply" thể hiện việc tương tác với API Server để triển khai các file cấu hình YAML vào hệ thống.