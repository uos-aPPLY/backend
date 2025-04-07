# Docker 실행 가이드

## 💡 사전 준비

- Java 17 설치 (Amazon Corretto 권장)
- Docker Desktop 설치  
  👉 https://www.docker.com/products/docker-desktop
- Git 클론 후 프로젝트 폴더로 이동

```bash
git clone https://github.com/uos-aPPLY/backend.git
cd DiaryPicApplication
```

## 🚀 실행 방법

### 💻 터미널에서 실행

```bash
docker-compose up --build
```

- 백엔드 서버: [http://localhost:8080](http://localhost:8080)
- Swagger 문서: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- 카카오 로그인: [http://localhost:8080/oauth2/authorization/kakao](http://localhost:8080/oauth2/authorization/kakao)

### 🐳 Docker Desktop에서 실행

1. Docker Desktop 실행
2. 왼쪽 메뉴 → **Containers** 선택
3. `diarypic-backend-1`, `diarypic-mysql-1` 컨테이너가 보이면:
   - ▶️ 버튼 클릭 → 실행
   - ⏹️ 버튼 클릭 → 중지
   - 🗑️ 버튼 클릭 → 삭제 (원할 경우)
4. `Logs` 탭에서 서버 로그 확인 가능

---

## 🧪 DB 접속 방법 (MySQL Workbench)

```bash
mysql -h 127.0.0.1 -P 13306 -u root -p
```

| 항목     | 값             |
|----------|----------------|
| Host     | 127.0.0.1      |
| Port     | 13306          |
| Username | root           |
| Password | 5812           |
| DB Name  | diarypic_db    |

---

## 🛑 실행 중지

### 터미널에서 종료

```bash
Ctrl + C
docker-compose down
```

### Docker Desktop에서 종료

- Containers 탭에서 중지(⏹️) 버튼 클릭

---
