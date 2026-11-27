# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 받아 두면 소스만 바뀐 경우 캐시를 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# 테스트는 CI(GitHub Actions)의 test 잡에서 실행한다.
RUN ./gradlew bootJar --no-daemon -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
USER spring

# t3.micro(1GB, 스왑 2GB)에서 Postgres·Nginx와 함께 동작하도록 힙을 고정한다.
# - Xmx384m: 앱 힙 상한 (프로세스 전체는 메타스페이스·스레드 포함 약 550MB)
# - SerialGC: 코어·메모리가 적은 환경에서 오버헤드가 가장 작다.
# - ExitOnOutOfMemoryError: OOM 시 즉시 종료 → restart 정책으로 재시작
# 필요하면 compose의 JAVA_OPTS 환경변수로 덮어쓴다.
ENV JAVA_OPTS="-Xms192m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
