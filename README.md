# AfterMe Backend

Spring Boot 기반 AfterMe 백엔드 프로젝트입니다.

## 퀴즈 구현 현황

`/api/handovers/{projectId}/quizzes` 아래 목록 조회, 응시 시작(`/{quizSetId}/attempts`),
문항 조회(`/{quizSetId}/attempts/{attemptId}`), 답안 제출(`.../submission`), 결과 조회(`.../result`)를 구현했습니다.
목록은 `page`(0부터), `size`(기본 20, 최대 100)를 받습니다. 응시는 활성 후임자 본인만 접근합니다.
문항 응답에는 정답·해설을 포함하지 않으며 제출 후 결과에 문서 버전·항목 근거와 영역별 정답 수를 반환합니다.

제출 예시 (`selectedOption`은 0부터 시작):

```json
{"answers":[{"questionId":1,"selectedOption":0},{"questionId":2,"selectedOption":1}]}
```

모든 문항에 한 번씩 답해야 하며 누락·중복·다른 퀴즈 문항·선택지 범위 오류는 400입니다.
제출 완료 후 재제출, 결과 제출 전 조회, 재응시 제한은 409입니다.
진행 중인 응시는 시작 요청 시 이어서 반환합니다. 미통과 시 명세의 3일 후 재시험 규칙을 적용합니다(제출 시각 + 3일).
문서에 없는 통과 후 재응시 금지는 적용하지 않습니다. 점수 표시는 정수 내림, 통과 판정은 반올림 전 정답률을 사용합니다.

### 남은 연동 및 정책

- 생성 요청 API와 영속 작업 큐를 구현했습니다. 실제 AI 호출 워커·문서 어댑터 연결 전에는 생성 작업이 자동 실행되지 않습니다.
- 생성 연동에서는 공개 문서 접근권한과 버전·섹션 소속을 검증한 후 `QuizSet.ready`로 불변 스냅샷을 저장해야 합니다.
- 문서 테이블 도입 후 `version_id`, `section_id` 외래키 및 문서 공개 상태 검증을 연결해야 합니다.
- 현재 문항은 객관식이며 한 문항에 근거 섹션 하나를 저장합니다. 단답형·복수 근거는 추가 설계가 필요합니다.
- 새 응시는 프로젝트 커트라인(미설정 시 70점)을 저장하고 그 기준으로 채점합니다. 시작·문항·결과 응답의 `passingScore`가 해당 응시의 기준입니다.
- 목록의 `passingScore`는 현재 프로젝트 기준입니다. 기준 변경은 새 응시에 적용하며 진행·제출된 응시의 기준과 결과는 유지합니다.
- 재시험 대기시간은 명세의 3일을 제출 시각부터 계산합니다.
- 체크리스트·완성도 조회·완료 요청·승인 API와 개인별 네 영역 각 25% 계산을 구현했습니다. 실제 문서 데이터 연결이 필요합니다. [연동 계약](docs/learning-integration.md)을 참고합니다.

### 퀴즈 검증

단위 테스트는 `./gradlew test --tests '*QuizAttemptTest' --tests '*QuizServiceTest'`로 실행합니다.
전체 테스트는 별도 PostgreSQL DB를 준비하고 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`을 설정하여 `./gradlew test`로 실행합니다.
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`를 설정하면 Flyway 스키마와 Entity 매핑도 검증합니다.
2026-11-25 검증: PostgreSQL 15의 격리 DB에서 V1~V6 적용 및 전체 51개 테스트 통과(퀴즈 12개 포함).
프로젝트 권장 PostgreSQL 17 환경에서의 확인은 남아 있습니다.

인수인계 생성 요청은 선택 필드 `organizationName`, `affiliationName`을 받습니다. 두 값은 프로젝트에 저장되어
생성 및 상세 응답에 포함됩니다. 값이 없는 기존 프로젝트는 `roleName`으로 표시할 수 있습니다.

업무 첨부는 `DELETE /api/handovers/{projectId}/tasks/{taskId}/attachments/{attachmentId}`로 삭제합니다.
활성 전임자만 호출할 수 있고, 성공 시 `204 No Content`를 반환합니다.

## Q&A 구현 현황

| 기능 | 경로 | 권한 | 성공 |
|---|---|---|---|
| 질문 가능 섹션 | `GET /api/handovers/{projectId}/questions/sections` | 프로젝트 접근 권한 | 200 |
| 질문 등록 | `POST /api/handovers/{projectId}/questions` | 활성 후임자 | 201 |
| 질문 수정 | `PATCH /api/handovers/{projectId}/questions/{threadId}` | 작성자 본인인 활성 후임자 | 200 |
| 질문 목록 | `GET /api/handovers/{projectId}/questions` | 프로젝트 접근 권한 | 200 |
| 질문·답변 상세 | `GET /api/handovers/{projectId}/questions/{threadId}` | 프로젝트 접근 권한 | 200 |
| 답변 등록 | `POST /api/handovers/{projectId}/questions/{threadId}/replies` | 활성 전임자 | 201 |
| 질문 첨부 업로드 | `POST /api/handovers/{projectId}/questions/{threadId}/attachments` | 질문 작성자 본인인 활성 후임자 | 201 |
| 질문 첨부 다운로드 | `GET /api/handovers/{projectId}/questions/{threadId}/attachments/{attachmentId}` | 프로젝트 접근 권한 | 200 |
| 질문 첨부 삭제 | `DELETE /api/handovers/{projectId}/questions/{threadId}/attachments/{attachmentId}` | 질문 작성자 본인인 활성 후임자 | 204 |

등록 본문은 `{"sectionId":4,"title":"질문 제목","content":"질문 내용"}`, 수정은 `{"revision":1,"title":"수정 제목","content":"수정 내용"}`,
답변은 `{"questionRevision":1,"content":"답변 내용"}`입니다. 작성자 id는 JWT에서 결정합니다.
제목은 선택 문자열이며 입력 시 공백만 보낼 수 없고 최대 255자입니다. 수정 시 제목을 생략하거나 `null`을 보내면 기존 제목을 유지합니다. 제목이 없는 질문은 응답의 `title`이 `null`입니다.
내용은 필수이며 최대 10,000자입니다. 목록은 `status=OPEN|RESOLVED`, `page`(0부터), `size`(기본 20, 최대 100)를 받습니다.
상세는 `{data:{question:{...},replies:[...],attachments:[...]}}`, 목록은 `{data:{items:[...],page,size,totalElements,totalPages}}`입니다.
질문 응답에 `sectionTitle`, `sectionDisplayOrder`가 포함됩니다. 질문과 답변에는 작성자의 `authorName`, `authorRole`이 포함되며, 상세의 `sectionContent`는 질문이 참조하는 공개 버전의 섹션 원문입니다. 섹션 목록은 문서별 최신 공개 버전의
`sectionId`, `versionId`, `taskId`, `title`, `displayOrder`를 반환하며 표시 순서는 문서마다 1부터 시작합니다.
첨부는 질문 등록 후 `multipart/form-data`의 `file` 파트로 한 건씩 업로드합니다.
PDF·PNG·JPEG·GIF를 파일당 최대 10 MiB까지 지원하며, 상세의 `downloadUrl`에는 인증 헤더를 포함해 요청합니다.

답변 등록 시 명세에 따라 자동으로 `RESOLVED`가 됩니다. 질문 내용을 변경하면 revision이 증가하며, 기존 해결 상태는 유지합니다.
기존 답변에는 답변 당시 질문 내용과 revision이 보존됩니다. 오래된 revision으로 수정·답변하면 409를 반환합니다.
수정과 답변은 동일 질문 행을 잠가 처리합니다. 답변 수정 API는 원 API 명세에 없어 추가하지 않았습니다.
문서 보완 반영 여부나 완성도 점수는 아직 계산하지 않으며, 문서에 반영되었다는 안내도 생성하지 않습니다.

### 문서·알림 연동 상태

- 활성 전임자는 `POST /api/handovers/{projectId}/documents/{documentId}/versions/{versionId}/publication`에
  초안의 `revision`을 `expectedVersion`으로 보내 문서를 공개할 수 있습니다.
- Q&A는 요청 프로젝트에 속한 공개 섹션만 허용하며, 초안이나 다른 프로젝트의 섹션은 404로 처리합니다.
  기존 질문은 새 버전 공개 이후에도 등록 당시의 공개 섹션을 참조합니다.
- 질문·답변 저장과 같은 트랜잭션에서 `qa_outbox_event`에 `QUESTION_CREATED` / `ANSWER_CREATED` 이벤트를 저장합니다.
  이벤트에는 고유 `dedupe_key`가 있습니다. **알림 발송 워커·알림센터 연결은 미구현**입니다.
- 알림 담당 모듈은 미처리 이벤트를 소비하고, 질문 등록은 전임자에게, 답변 등록은 질문 작성자에게 전달해야 합니다.
  알림 저장 시 `dedupe_key`로 중복을 막고 `processed_at`을 기록하는 처리가 필요합니다.
- 문서 통합 테스트에서 실제 공개 섹션으로 질문을 등록하고 첨부를 조회·다운로드·삭제하는 흐름을 검증합니다.

2026-11-25 검증: 격리된 PostgreSQL 15에서 V7 적용과 JPA 스키마 검증을 완료하고 전체 71개 테스트가 통과했습니다.
Q&A 테스트 20개에는 역할·작성자 권한, 공개 항목 검증 경계, 수정 버전 충돌, HTTP 인증·입력 검증,
실제 DB 저장·조회·상태 필터·답변 원문 보존·알림 이벤트 저장 검증이 포함됩니다.

## 완료 기준 및 본인 확인

| 기능 | 경로 | 권한 | 상태 |
|---|---|---|---|
| 최소 완성도·퀴즈 커트라인 설정 | `PATCH /api/handovers/{projectId}/completion/criteria` | 활성 전임자 | 구현 |
| 전임자·후임자 본인 확인 | `PUT /api/handovers/{projectId}/completion/confirmations/me` | 요청한 역할로 참여하는 활성 사용자 본인 | 구현 |
| 기준·확인·완료 요건·승인 상태 조회 | `GET /api/handovers/{projectId}/completion` | 프로젝트 접근 권한 | 구현, 실제 문서 연결 필요 |
| 완성도와 미충족 요건 조회 | `GET /api/handovers/{projectId}/completeness` | 프로젝트 접근 권한 | 개인별 25% 계산 구현, 실제 데이터 연결 필요 |
| 완료 요청 | `POST /api/handovers/{projectId}/completion` | 활성 전임자 | 요건 검증·요청 이력 저장 |
| 최종 승인 | `POST /api/handovers/{projectId}/completion/approval` | 활성 조직 관리자 | 요건 재검증·양측 확인·프로젝트 완료·감사 이력 저장 |

미설정 기본값은 최소 완성도 80%, 퀴즈 커트라인 70점, 기준 revision 0입니다. 조회만으로 DB에 기본값을 저장하지 않습니다.
기준 설정은 아래처럼 요청하며, 변경할 수치 중 하나만 보내도 됩니다. 최소 완성도는 80~100, 커트라인은 70~100 범위입니다.

```json
{"minimumCompleteness":85,"quizPassingScore":80,"revision":0}
```

GET 응답의 `criteria.revision`을 PATCH의 `revision`에 보냅니다. 실제 값이 바뀌면 revision이 증가하며,
같은 값을 보내면 revision을 유지합니다. 이전 revision으로 기준을 수정하면 409입니다.
기준 변경과 확인 처리는 프로젝트 행을 잠가 직렬화합니다. 완료된 프로젝트의 새 기준 변경·새 확인은 409입니다.

본인 확인 요청 예시:

```json
{"role":"SUCCESSOR"}
```

인증 사용자에게 실제 배정된 역할만 확인할 수 있습니다. 같은 구성원·역할의 재요청은 원래 확인 기록을 200으로 반환합니다.
명세에 없는 기준 변경 시 재확인 규칙은 적용하지 않습니다. 기존 본인 확인은 기준 변경 후에도 유지합니다.
응답의 `criteriaRevision`은 확인 등록 당시 기준의 참고 정보이며, 확인 만료나 재확인 조건이 아닙니다.
전임자 또는 후임자가 배정되지 않은 경우 해당 역할의 확인 완료는 false입니다. 여러 명이면 해당 역할의 활성 참여자 전원이 확인해야 true입니다.

상태 조회에는 현재 기준, 프로젝트 상태, 완료 시각, 참여자별 확인 여부, `predecessorConfirmed`, `successorConfirmed`를 반환합니다.
**양측 확인만으로 완료·승인을 처리하지 않습니다.** 필수 업무·답변, 문서 숙지, 버전별 퀴즈, 체크리스트, 미해결 Q&A와 현재 기준을 검사합니다.
문서 어댑터가 없거나 점수 계산에 필요한 데이터가 없으면 점수는 null, `eligibilityEvaluated=false`이며 요청·승인은 409입니다.
추가 필수 조건으로 활성 후임자마다 최소 1회 질문과 전체 페이지 열람을 검사합니다.
문서 열람률·체크리스트 완료율·퀴즈 점수·Q&A 해결률을 각 25%로 계산합니다. 후임자별 점수와 영역별 수치를 반환하며 최종 정수 점수는 내림합니다.
페이지 열람은 숙지 확인과 별도 데이터가 필요하며 연동 전에는 완료를 차단합니다. 여러 후임자의 프로젝트 집계와 체크리스트 0건 정책은 아직 미정이므로 해당 경우 프로젝트 점수 산정을 보류합니다.
승인 순간 조건을 다시 검사하고 프로젝트 완료와 승인·감사 이력을 같은 트랜잭션에서 저장합니다.

체크리스트는 `PUT /api/handovers/{projectId}/documents/{documentId}/versions/{versionId}/checklist-items/{itemId}/completion`에
`{"completed":true}`를 보냅니다. 활성 후임자 본인의 기록만 변경하며 공개 문서 소속을 검사합니다.
퀴즈 생성은 `POST /api/handovers/{projectId}/quiz-generation-jobs`에 `{"versionId":10,"idempotencyKey":"quiz-version-10"}`를 보내며 202를 반환합니다.
같은 키·버전은 같은 작업을 반환하고 다른 버전으로 재사용하면 409입니다. 외부 작업 ID는 `quiz-숫자` 형식입니다.
생성 문서 원문을 보존하고, 결과 저장 시 근거와 공개 버전을 다시 검증합니다. AI 워커와 공통 작업 조회 API 연결은 남아 있습니다.

V8은 기준·확인 이력을 저장하고, 기존 퀴즈 응시의 커트라인을 생성 당시 퀴즈 값으로 이관합니다.
이후 새 응시는 현재 프로젝트 커트라인을 저장합니다. 커트라인 변경으로 기존 제출 결과를 소급 변경하지 않습니다.
새 프로젝트 기준이 이전 퀴즈 생성 기준보다 높거나 낮더라도 새 응시는 프로젝트 기준을 따릅니다.

2026-11-25 명세 대조 후 검증: 격리된 PostgreSQL 15에서 V8 적용·JPA 스키마 검증과 전체 94개 테스트가 통과했습니다.
기준 범위·부분 수정·권한·revision 충돌, 중복 확인, 기준 변경 후 본인 확인 유지,
통과 후 재응시, 질문 수정 후 기존 상태 유지, HTTP 요청 검증, 응시별 커트라인과 기존 결과 보존을 검증했습니다.
실제 개발 중 발생한 문제와 해결 과정은 [트러블슈팅](docs/troubleshooting.md)에 정리했습니다.
첨부 명세와 코드의 대응 관계, 문서 간 충돌 처리, 남은 연동은 [명세 적용 기준](docs/requirements.md)에 정리했습니다.

추가 구현 검증: PostgreSQL 15에서 V9와 JPA 스키마 검증을 포함하여 전체 107개 테스트가 통과했습니다(실패·오류·건너뜀 0).
완료 요청·승인의 중복 방지, 승인 시 새 질문·기준 변경 재검증, 권한·확인 누락 차단,
체크리스트 저장, 생성 작업 중복·원문 보존·잘못된 근거 차단을 검증했습니다.
문서와 산식은 테스트 대역을 사용했으며 실제 외부 모듈 통합 완료를 의미하지 않습니다.

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
JWT_SECRET=32자_이상의_임의_문자열
```

`JWT_SECRET`은 액세스 토큰 서명 키입니다. 32바이트 미만이면 애플리케이션이 시작되지 않습니다.
PowerShell에서 임의 값 생성:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
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
- 환경 변수: 아래 4개를 각각 등록

| 이름 | 값 |
| --- | --- |
| DB_NAME | `.env`의 DB_NAME 값 |
| DB_USERNAME | `.env`의 DB_USERNAME 값 |
| DB_PASSWORD | `.env`의 DB_PASSWORD 값 |
| JWT_SECRET | `.env`의 JWT_SECRET 값 |

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

## 공통 응답 및 예외 처리

### 성공 응답

컨트롤러에서 `ApiResponse.of(data)`로 데이터를 감싸 반환합니다.
HTTP 상태는 컨트롤러에서 지정합니다.

```java
return ResponseEntity.ok(ApiResponse.of(userResponse));
```

응답 예시:

```json
{
  "data": {
    "id": 1,
    "name": "홍길동"
  }
}
```

`204 No Content`를 반환할 때는 응답 본문을 포함하지 않습니다.

### 비즈니스 예외

서비스에서 비즈니스 오류가 발생하면 `BusinessException`을 사용합니다.

```java
throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
```

`GlobalExceptionHandler`가 에러 코드에 정의된 HTTP 상태와 메시지를 반환합니다.

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "요청한 대상을 찾을 수 없습니다.",
  "errors": []
}
```

### 입력값 검증

요청 DTO에 검증 어노테이션을 선언하고,
컨트롤러의 요청 본문에 `@Valid`를 적용합니다.

```java
public record CreateUserRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
}
```

```java
@PostMapping
public ResponseEntity<ApiResponse<UserResponse>> create(
        @Valid @RequestBody CreateUserRequest request
) {
    // 생성 처리
}
```

검증 실패 시 HTTP `400`과 필드별 오류를 반환합니다.

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값을 확인해주세요.",
  "errors": [
    {
      "field": "name",
      "message": "이름은 필수입니다."
    }
  ]
}
```

### 에러 처리 규칙

| 상황 | HTTP 상태 | 에러 코드 |
|---|---|---|
| 요청 DTO 필드 검증 실패 | 400 | INVALID_INPUT |
| 요청한 대상 없음 | 404 | RESOURCE_NOT_FOUND |
| 현재 상태와 충돌 | 409 | CONFLICT |
| 잘못된 JSON 등 Spring MVC 기본 오류 | 오류에 맞는 상태 | HTTP_{상태 코드} |
| 예상하지 못한 서버 오류 | 500 | INTERNAL_SERVER_ERROR |

- 비즈니스 오류는 `ErrorCode`에 정의하고 `BusinessException`으로 전달합니다.
- 예상하지 못한 예외의 상세 정보는 서버 로그에 기록하고 응답에 노출하지 않습니다.
- 컨트롤러마다 동일한 예외 처리를 반복하지 않고 전역 예외 처리기를 사용합니다.
## 인증 / 회원 API

| 기능 | 메서드 · 경로 | 인증 | 성공 응답 |
|---|---|---|---|
| 회원가입 | `POST /api/auth/signup` | X | 201 `{ data: { id, email, name } }` |
| 로그인 | `POST /api/auth/login` | X | 200 `{ data: TokenResponse }` |
| 내 프로필 조회 | `GET /api/users/me` | O | 200 `{ data: { id, email, name, createdAt } }` |
| 내 프로필 수정 (이름) | `PATCH /api/users/me` | O | 200 `{ data: { id, email, name, createdAt } }` |

- `TokenResponse`: `{ accessToken, refreshToken, tokenType: "Bearer", expiresIn }` (`expiresIn`은 초 단위)
- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 보냅니다.

### 토큰 정책

| 항목 | 값 | 설정 키 |
|---|---|---|
| 액세스 토큰 유효기간 | 30분 | `jwt.access-token-ttl` |
| 리프레시 토큰 유효기간 | 14일 | `jwt.refresh-token-ttl` |
| 로그인 실패 잠금 | 5회 실패 시 | `auth.login.max-failed-attempts` |
| 잠금 시간 | 30분 (회의 후 확정) | `auth.login.lock-duration` |

리프레시 토큰은 DB에 원문 대신 SHA-256 해시로 저장합니다.

### 인증 오류 코드

| 상황 | HTTP 상태 | 에러 코드 |
|---|---|---|
| 토큰 없이 인증 API 호출 | 401 | UNAUTHORIZED |
| 액세스 토큰 만료·위조 | 401 | INVALID_ACCESS_TOKEN |
| 이메일 또는 비밀번호 불일치 | 401 | INVALID_CREDENTIALS |
| 로그인 실패 횟수 초과 | 423 | ACCOUNT_LOCKED |
| 중복 이메일 가입 | 409 | DUPLICATE_EMAIL |
| 회원 없음 | 404 | USER_NOT_FOUND |
