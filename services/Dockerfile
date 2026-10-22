FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services ./services
ARG SERVICE_NAME
ARG GIT_COMMIT
ARG BUILD_TIME
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl "services/${SERVICE_NAME}" -am package -DskipTests \
      "-DgitCommit=${GIT_COMMIT}" "-DbuildTime=${BUILD_TIME}" \
    && cp "services/${SERVICE_NAME}/target/${SERVICE_NAME}-0.1.0-SNAPSHOT.jar" /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 campus \
    && adduser -S -u 10001 -G campus campus
WORKDIR /app
COPY --from=build --chown=campus:campus /app.jar ./app.jar
ARG GIT_COMMIT
ARG BUILD_TIME
LABEL org.opencontainers.image.revision=$GIT_COMMIT \
      org.opencontainers.image.created=$BUILD_TIME \
      org.opencontainers.image.source="https://github.com/Liinn0529/campus-resource-platform"
USER campus
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=6 \
    CMD wget -Y off -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
