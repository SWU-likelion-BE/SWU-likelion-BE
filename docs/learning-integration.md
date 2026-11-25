# 학습·완료 모듈 연동 계약

## 문서 담당 모듈

`LearningDocumentReader` Bean을 제공한다. 기본 구현은 공개 문서·항목 요청을 409로 차단하며 완료 평가에는 unavailable을 반환한다.

- `requirePublishedVersion`: 프로젝트에 속하고 접근 가능한 공개 버전의 문서 ID, 버전 ID, 섹션 원문을 반환한다. 소속·공개 여부를 반드시 검사한다.
- `requirePublishedChecklistItem`: URL의 프로젝트·문서·버전·체크리스트 항목 소속과 공개 여부를 검사한다.
- `evidence`: 실제 필수 업무 수·누락 수, 필수 질문/답변 수, 완료 대상 공개 버전, 모든 대상 버전을 숙지한 후임자 ID, 필수 체크리스트를 집계한다. 열람과 숙지는 구분한다.
- Q&A에는 별도로 `PublishedSectionReader` Bean을 제공한다. 소속과 공개 버전을 검증한다.

잘못된 소속·없는 항목은 404로 처리한다. 문서 테이블 도입 후 현재 숫자로 저장한 문서·버전·섹션 참조의 외래키를 연결한다.
최종 승인과 데이터 변경의 경쟁을 막으려면 문서·업무·인터뷰·숙지 변경도 프로젝트 행을 먼저 잠근다.
현재 Q&A 쓰기, 퀴즈 제출, 체크리스트 변경, 기준·본인 확인·승인은 동일 프로젝트 잠금을 사용한다.

## 완성도 정책

`CompletenessPolicy.score(ScoringEvidence)`는 합의된 서버 계산식으로 0~100 정수 점수를 반환한다.
현재 문서에는 산식·가중치가 없으므로 기본 Bean은 `OptionalInt.empty()`를 반환한다. AI 평가나 저장된 프로젝트 점수를 대신 사용하지 않는다.
산식 미설정 시 응답의 score는 null, evaluated는 false이고 `COMPLETENESS_SCORE_UNAVAILABLE`이 미충족 사유에 포함된다.
문서 데이터가 없으면 `DOCUMENT_DATA_UNAVAILABLE`도 반환한다. 이 상태에서 완료 요청·승인은 409이다.
테스트에서 사용하는 95점 정책은 테스트용이며 운영 계산식이 아니다.

## AI 작업 담당 모듈

생성 요청은 `quiz_generation_job`에 QUEUED 작업과 공개 문서 스냅샷을 저장한다. API 요청 트랜잭션에서 AI를 호출하지 않는다.
워커는 QUEUED 작업을 찾아 `QuizGenerationService.claim(id)`로 확보하고, 반환된 원문으로 트랜잭션 밖에서 AI를 호출한다.
성공하면 `complete(id, output)`, 실패하면 `fail(id, errorCode)`를 호출한다. 실패 코드는 민감한 원문 대신 대문자 영문·숫자·밑줄의 식별자를 사용한다.
완료 시 객관식 입력, 원문·현재 공개 버전에 존재하는 근거 섹션, provider/model/promptVersion을 검증하고 퀴즈와 성공 상태를 함께 저장한다.
영역은 근거 섹션의 type으로 결정한다. AI가 임의로 지정하지 않는다.

외부 작업 ID는 `quiz-1`처럼 접두사가 있다. 공통 AI 작업 조회 담당자는 해당 접두사를 `status(userId, projectId, jobId)`로 연결한다.
이 메서드는 인증 사용자와 프로젝트 소속을 검사한다. 내부 claim/complete/fail을 일반 사용자 HTTP API로 노출하지 않는다.
현재 스케줄러·실제 AI 호출·공통 작업 조회 컨트롤러는 구현하지 않았다. RUNNING 작업의 장애 복구·재시도 운영 정책도 워커에서 확정해야 한다.

## 완료 흐름

전임자 요청은 현재 완료 요건이 충족되면 REQUESTED를 저장한다. 관리자는 요청 이후 모든 요건과 양측 본인 확인을 다시 검증한다.
승인은 프로젝트 상태·완료 시각·승인자·점수와 APPROVED·감사 기록을 함께 저장한다. 같은 요청과 승인 재호출은 중복 이력을 생성하지 않는다.
확인 기록은 기준 변경으로 무효화하지 않는다. requestRequirementsMet/approvalRequirementsMet은 조건 충족 여부이며 호출자 권한을 뜻하지 않는다.

## 알림 담당 모듈

Q&A 이벤트는 `qa_outbox_event`에 저장된다. 미처리 이벤트를 소비하여 dedupe_key로 중복을 방지하고 알림 저장 후 processed_at을 기록한다.
실제 알림 발송과 알림센터 연결은 별도 연동이 필요하다.
