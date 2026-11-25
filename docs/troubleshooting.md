# 개발 및 테스트 트러블슈팅

## 테스트 실행 시 DataSource 생성 실패

- 현상: 전체 테스트 중 `DataSourceBeanCreationException`으로 Spring 컨텍스트가 시작되지 않았다.
- 원인: 기본 설정에는 DB 연결 값이 없고, 로컬 DB 설정은 `local` 프로파일에 있다. 프로파일·환경변수 없이 실행한 통합 테스트에 DB 정보가 전달되지 않았다.
- 해결: 별도 테스트 PostgreSQL을 준비하고 테스트 프로세스에 아래 환경변수를 전달했다. 개발·운영 DB는 사용하지 않았다.

| 환경변수 | 용도 |
|---|---|
| `SPRING_DATASOURCE_URL` | 테스트 DB JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | 테스트 DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | 테스트 DB 비밀번호 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate`로 Entity와 스키마 일치 여부 확인 |
| `SPRING_JPA_OPEN_IN_VIEW` | `false`로 서비스 트랜잭션 밖 지연 로딩 방지 |
| `JWT_SECRET` | 테스트 전용 32바이트 이상 서명 키 |

`./gradlew test`로 Flyway 마이그레이션, JPA 검증, 서비스·HTTP 테스트를 실행했다.
`.env`는 Spring Boot가 자동으로 읽지 않으므로 실행 프로세스에 직접 전달해야 한다.

## 예외를 던지는 Mockito mock을 재설정할 때 테스트 자체가 실패

- 현상: 문서 항목이 없을 때의 오류를 검증한 뒤 다른 프로젝트 문서 반환 상황으로 바꾸는 단위 테스트에서 예외가 발생했다.
- 원인: 이미 예외를 던지도록 설정한 메서드를 `given(mock.method())`로 다시 호출하면서, 새 stub을 설정하기 전에 기존 예외가 실행됐다.
- 해결: 재호출 없이 반환값을 교체하는 `doReturn(...).when(mock).method(...)`로 변경했다.

```java
given(sections.requirePublishedSection(1L, 4L))
        .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

// 기존 stub을 실행하지 않고 교체한다.
doReturn(new SectionReference(99L, 3L, 4L))
        .when(sections).requirePublishedSection(1L, 4L);
```

수정 후 문서 미존재와 다른 프로젝트 소속 문서가 모두 차단되는 것을 검증했다.

## PostgreSQL 17 이미지 다운로드 실패

- 현상: Docker 이미지 다운로드 중 `production.cloudfront.docker.com` DNS 조회가 실패했다.
- 조치: 이미 로컬에 있던 PostgreSQL 15 이미지로 별도 테스트 컨테이너를 만들고, `127.0.0.1:15433`에만 연결했다.
- 확인: V1~V8 마이그레이션 및 JPA 검증, 명세 대조 후 전체 94개 테스트가 통과했다. 검증 후 테스트 컨테이너를 중지했다.
- 남은 항목: 이 결과는 PostgreSQL 15에서의 검증이다. DNS 문제를 해결한 환경에서 프로젝트 권장 버전인 PostgreSQL 17 검증이 필요하다.
