# Package an already-built Spring Boot jar. Building the jar on the host (not in
# an image) keeps this build free of Maven/Gradle downloads and reusable with an
# offline Gradle cache. Run `make image` (or ./gradlew bootJar) first.
FROM eclipse-temurin:27-jre

ARG APP_JAR=build/libs/selfdrop-0.0.1-SNAPSHOT.jar

WORKDIR /app

# The base image already ships a uid/gid 1000 account (ubuntu), which matches
# the usual desktop user that owns ./data. Rename it for clarity and reuse it.
RUN groupmod -n selfdrop ubuntu \
    && usermod -l selfdrop -d /app ubuntu \
    && mkdir -p /app/data /var/tmp/selfdrop-uploads \
    && chown -R selfdrop:selfdrop /app /var/tmp/selfdrop-uploads

COPY ${APP_JAR} /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xmx256m"
EXPOSE 8080

USER selfdrop

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
