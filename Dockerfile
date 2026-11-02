FROM eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
RUN groupadd --system --gid 10001 logitrack \
    && useradd --system --uid 10001 --gid logitrack --home-dir /app --shell /usr/sbin/nologin logitrack
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/api-gateway-0.3.0-SNAPSHOT.jar app.jar
COPY --from=build --chown=10001:10001 /workspace/target/classes/com/logitrack_atom/api_gateway/health/HealthProbe.class healthcheck/com/logitrack_atom/api_gateway/health/HealthProbe.class
USER 10001:10001
EXPOSE 8066
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD ["java", "-cp", "/app/healthcheck", "com.logitrack_atom.api_gateway.health.HealthProbe"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
