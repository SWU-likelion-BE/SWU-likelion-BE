# AfterMe Backend

Spring Boot 기반 AfterMe 백엔드 프로젝트입니다.

## 개발 환경

- Java 17
- Docker Desktop
- PostgreSQL 17
- Gradle Wrapper

## 로컬 실행 방법

### 1. 환경변수 파일 생성

프로젝트 루트의 `.env.example`을 복사하여 `.env`를 생성합니다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env`에 로컬 개발용 DB 정보를 입력합니다.

```dotenv
DB_NAME=afterme
DB_USERNAME=afterme
DB_PASSWORD=여기에_로컬_DB_비밀번호_입력
```

`.env`는 Git에서 제외됩니다.
`.env.example`에는 실제 비밀번호를 작성하지 않습니다.

### 2. PostgreSQL 실행

Docker Desktop을 실행한 뒤, 프로젝트 루트에서 실행합니다.

```powershell
docker compose up -d
```

실행 상태를 확인합니다.

```powershell
docker compose ps
```

postgres 서비스의 STATUS가 `Up`이면 실행된 상태입니다.

- 호스트: localhost
- PC 접속 포트: 15432
- 컨테이너 내부 포트: 5432

### 3. IntelliJ 실행 설정

상단 실행 구성 → 구성 편집 → AftermeBackendApplication을 선택합니다.

- 활성화된 프로파일: `local`
- 환경 변수: 아래 3개를 각각 등록

| 이름 | 값 |
| --- | --- |
| DB_NAME | `.env`의 DB_NAME 값 |
| DB_USERNAME | `.env`의 DB_USERNAME 값 |
| DB_PASSWORD | `.env`의 DB_PASSWORD 값 |

환경변수 이름 앞뒤에 공백을 넣지 않습니다.
Spring Boot는 기본적으로 `.env`를 자동으로 읽지 않으므로
IntelliJ 실행 설정에도 동일한 값을 등록해야 합니다.

### 4. Spring Boot 실행

IntelliJ에서 AftermeBackendApplication을 실행합니다.

콘솔에 `Started AftermeBackendApplication`이 표시되면
애플리케이션 시작이 완료된 상태입니다.

## DB 종료

```powershell
docker compose down
```

DB 데이터는 Docker 볼륨에 유지됩니다.
`docker compose down -v`는 DB 데이터도 삭제하므로 주의합니다.

## 참고 사항

- DB가 초기화된 이후 `.env`의 계정이나 비밀번호만 바꿔도
  기존 DB 계정 정보는 자동으로 변경되지 않습니다.
- `ddl-auto=validate`는 테이블을 생성하지 않습니다.
  Entity에 대응하는 테이블은 Flyway 마이그레이션으로 관리합니다.
- 비밀번호와 API 키는 Git에 커밋하지 않습니다.