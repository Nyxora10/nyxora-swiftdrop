# auth-service - multi-stage image (Task 7.1)
# Build from the REPO ROOT, because the service depends on shared/security-common:
#   docker build -f services/auth-service/Dockerfile -t swiftdrop/auth-service:local .

# ---------- Stage 1: build the jar ----------
# maven:3.9-eclipse-temurin-21 = the eclipse-temurin:21-jdk image with Maven added
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Shared library first, then this service only
COPY shared/security-common/pom.xml shared/security-common/pom.xml
COPY shared/security-common/src shared/security-common/src
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/auth-service/src services/auth-service/src

# Tests and the JaCoCo gate run in CI, so the image build skips compiling and running them.
# The cache mount keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f shared/security-common/pom.xml install -Dmaven.test.skip=true && \
    mvn -B -ntp -f services/auth-service/pom.xml package -Dmaven.test.skip=true && \
    cp services/auth-service/target/*.jar /workspace/app.jar

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user
RUN addgroup -S swiftdrop && adduser -S swiftdrop -G swiftdrop

COPY --from=build --chown=swiftdrop:swiftdrop /workspace/app.jar /app/app.jar
USER swiftdrop

EXPOSE 8081
# Use up to 75% of the container's memory limit for the Java heap
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
