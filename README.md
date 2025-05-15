# Docker 실행 가이드

## 💡 사전 준비

1.  **Java 17 설치:** (Amazon Corretto 권장)
2.  **Docker Desktop 설치:**
    *   👉 [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
3.  **Git 클론:** 프로젝트 코드를 로컬 머신에 받습니다.
    ```bash
    git clone https://github.com/uos-aPPLY/backend.git
    cd backend
    ```

4.  **`.env` 파일 생성 (⭐ 중요 ⭐):**
    *   프로젝트 루트 디렉토리(`backend` 폴더)에 있는 `.env.example` 파일을 복사하여 **같은 위치에 `.env` 라는 이름의 새 파일을 만듭니다.**
    *   `.env` 파일은 각종 비밀번호, API 키 등 민감한 정보를 담고 있습니다.
    *   **담당자에게 필요한 실제 값(DB 비밀번호, AWS 키, OAuth 클라이언트 정보, JWT 시크릿 등)을 전달받아** `.env` 파일 안의 해당 변수들에 채워넣으세요.
    *   **주의:** `.env` 파일은 **절대로 Git에 커밋하면 안 됩니다!** (`.gitignore`에 `.env`가 포함되어 있는지 확인하세요.)

## 🚀 실행 방법

### 💻 터미널에서 실행

1.  **프로젝트 루트 디렉토리 (`backend` 폴더)로 이동**합니다. (이미 이동했다면 생략)
2.  다음 명령어를 실행하여 Docker 컨테이너를 빌드하고 실행합니다.
    ```bash
    docker compose up --build
    ```
    *   *참고: 최신 Docker 버전에서는 `docker-compose` 대신 `docker compose` 사용을 권장합니다.*
    *   *(만약 `docker compose` 명령어가 없다면 이전처럼 `docker-compose up --build`를 사용하세요.)*

3.  실행 완료 후 접속 정보:
    *   **백엔드 서버:** [http://localhost:8080](http://localhost:8080)
    *   **Swagger 문서:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
    *   **카카오 로그인 테스트 URL:** [http://localhost:8080/oauth2/authorization/kakao](http://localhost:8080/oauth2/authorization/kakao)
    *   **구글 로그인 테스트 URL:** [http://localhost:8080/oauth2/authorization/google](http://localhost:8080/oauth2/authorization/google)

### 🐳 Docker Desktop에서 실행 (터미널 실행 후 관리)

1.  Docker Desktop 실행
2.  왼쪽 메뉴 → **Containers** 선택
3.  `diarypic-backend`, `diarypic-mysql` 컨테이너가 보이면 (이름 뒤에 숫자가 붙을 수도 있음):
    *   ▶️ 버튼 클릭 → 실행 (보통 터미널에서 이미 실행됨)
    *   ⏹️ 버튼 클릭 → 중지
    *   🗑️ 버튼 클릭 → 삭제 (컨테이너만 삭제, 데이터는 유지될 수 있음)
4.  `Logs` 탭에서 각 컨테이너의 로그 확인 가능

---

## 🧪 DB 접속 방법 (MySQL Workbench 또는 터미널)

**터미널:**

```bash
mysql -h 127.0.0.1 -P 13306 -u root -p
# 비밀번호를 물어보면 .env 파일의 MYSQL_ROOT_PASSWORD 값을 입력
```

| 항목     | 값             |
|----------|----------------|
| Host     | 127.0.0.1      |
| Port     | 13306          |
| Username | root           |
| Password | {MYSQL_ROOT_PASSWORD} |
| DB Name  | diarypic_db    |

---

## 🛑 실행 중지

### 터미널에서 종료

1.  컨테이너를 실행했던 터미널에서 `Ctrl + C`를 누릅니다.
2.  **프로젝트 루트 디렉토리 (`backend` 폴더)** 에서 다음 명령어를 실행하여 컨테이너와 네트워크를 완전히 중지하고 제거합니다.
    ```bash
    docker compose down
    ```
    *   *(데이터를 보존하는 볼륨은 기본적으로 삭제되지 않습니다. 볼륨까지 삭제하려면 `docker compose down -v`)*

### Docker Desktop에서 종료

*   Containers 탭에서 해당 컨테이너들의 중지(⏹️) 버튼 또는 삭제(🗑️) 버튼 클릭

---
