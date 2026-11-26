# 학습·완료 모듈 연동 계약

## 문서 담당 모듈

`LearningDocumentReader` Bean을 제공한다. 기본 구현은 공개 문서·항목 요청을 409로 차단하며 완료 평가에는 unavailable을 반환한다.

- `requirePublishedVersion`: 프로젝트에 속하고 접근 가능한 공개 버전의 문서 ID, 버전 ID, 섹션 원문을 반환한다. 소속·공개 여부를 반드시 검사한다.
- `requirePublishedChecklistItem`: URL의 프로젝트·문서·버전·체크리스트 항목 소속과 공개 여부를 검사한다.
- `evidence`: 실제 필수 업무 수·누락 수, 필수 질문/답변 수, 완료 대상 공개 버전, 모든 대상 버전을 숙지한 후임자 ID, 필수 체크리스트를 집계한다. 열람과 숙지는 구분한다.
- `pageViews(projectId, versionIds, successorIds)`: 전달된 모든 공개 버전의 모든 페이지를 실제 열람한 후임자 ID를 `PageViews`로 반환한다. 페이지 식별 단위와 버전별 이력은 문서 모듈에서 관리한다. 전체 페이지 열람이 확인된 사용자만 집합에 포함하며, 숙지 확인을 열람 이력 대신 사용하지 않는다.
- 점수 계산에는 `PageViews.fromProgress(Map<userId, PageCount(totalPages, viewedPages)>)`를 사용한다. 전달된 모든 대상 버전의 전체 페이지 수와 중복 제거한 열람 페이지 수를 후임자별로 집계한다. 완료 여부 집합만 반환하는 기존 생성자는 페이지 열람률 계산에 사용할 수 없다.
- `scoringChecklistItems(projectId, versionIds)`: 필수·선택 항목을 포함한 전체 평가 대상 목록을 반환한다. 필수 항목만으로 전체 완료율을 계산하지 않는다. 미연동은 Optional.empty(), 실제 0건은 Optional.of(빈 목록)으로 구분한다. 현재 0건 정책은 미정이므로 어느 경우든 점수 계산을 보류한다.
- 페이지 열람 어댑터의 기본 메서드는 unavailable이다. 연동 전에는 `PAGE_VIEW_DATA_UNAVAILABLE`로 완료를 차단한다. 조회 가능하나 일부 페이지를 열지 않은 후임자는 `ALL_PAGES_NOT_VIEWED`와 userId로 표시한다.
- Q&A에는 별도로 `PublishedSectionReader` Bean을 제공한다. 소속과 공개 버전을 검증한다.

잘못된 소속·없는 항목은 404로 처리한다. 문서 테이블 도입 후 현재 숫자로 저장한 문서·버전·섹션 참조의 외래키를 연결한다.
최종 승인과 데이터 변경의 경쟁을 막으려면 문서·업무·인터뷰·숙지 변경도 프로젝트 행을 먼저 잠근다.
현재 Q&A 쓰기, 퀴즈 제출, 체크리스트 변경, 기준·본인 확인·승인은 동일 프로젝트 잠금을 사용한다.

## 완성도 정책

`CompletenessPolicy.score(ScoringEvidence)`는 합의된 서버 계산식으로 0~100 정수 점수를 반환한다.
확정된 네 영역은 문서 열람률·체크리스트 완료율·퀴즈 점수·Q&A 해결률이며 각각 25%이다. 기본 Bean은 `EqualWeightCompletenessPolicy`이다.
개인 점수 = (열람 페이지/전체 페이지 ×100 + 완료 체크리스트/전체 체크리스트 ×100 + 버전별 최고 퀴즈 점수 평균 + 해결 질문/개인 전체 질문 ×100) /4.
미응시 버전의 퀴즈 점수는 0점, 개인 질문 0건의 해결률은 0%이다. 질문 0건은 필수 조건으로도 차단된다.
중간 비율은 10자리 소수로 표시하고 최종 정수 점수는 정확한 분수 합산 후 내림한다. AI 평가나 저장된 프로젝트 점수를 대신 사용하지 않는다.
`evidence.participantScores`에 후임자별 총점과 네 영역 수치를 반환한다. 후임자 1명이면 개인 점수가 프로젝트 점수다.
여러 후임자의 프로젝트 집계는 아직 미정이라 개인 점수만 제공하고 프로젝트 score=null을 반환한다. 체크리스트 0건 또는 열람 수치 누락도 임의 만점·가중치 재분배 없이 점수를 보류한다.
점수 산정 불가 시 score는 null, evaluated는 false이고 `COMPLETENESS_SCORE_UNAVAILABLE`이 미충족 사유에 포함된다.
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

완료 요청·최종 승인에는 최소 한 번의 질문, 모든 질문에 대한 답변, 모든 대상 페이지 열람도 필요하다. 기존 필수 조건을 대체하지 않고 함께 검사한다.
질문 0건이면 `QUESTION_REQUIRED`, 미답변 질문이 있으면 `UNANSWERED_QUESTIONS`로 차단한다. 점수가 기준 이상이어도 동일하다.
활성 후임자마다 최소 한 번 질문해야 한다. 프로젝트 전체 질문으로 대신하지 않으며 이전 임시 설정 `completion.require-question-per-successor`는 제거했다. 미충족 userId를 반환한다.
전체 페이지 열람은 현재 활성 후임자 각각을 검사한다. 다중 후임자 범위는 팀 확정 시 조정해야 한다.
최종 승인은 요청 시점 결과를 재사용하지 않고 페이지 열람과 질문을 포함한 현재 증거를 다시 검사한다.

## 알림 담당 모듈

Q&A 이벤트는 `qa_outbox_event`에 저장된다. 미처리 이벤트를 소비하여 dedupe_key로 중복을 방지하고 알림 저장 후 processed_at을 기록한다.
실제 알림 발송과 알림센터 연결은 별도 연동이 필요하다.
