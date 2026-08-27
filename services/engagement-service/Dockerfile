FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
WORKDIR /workspace/services/engagement-service
RUN GRADLE_ZIP="$(tr -d '\r' < gradle/wrapper/gradle-wrapper.properties | sed -n 's#^distributionUrl=.*/\([^/]*\.zip\)$#\1#p')" \
    && if [ -n "$GRADLE_ZIP" ] && [ -f "/workspace/$GRADLE_ZIP" ]; then \
        sed -i "s#^distributionUrl=.*#distributionUrl=file:/workspace/$GRADLE_ZIP#" gradle/wrapper/gradle-wrapper.properties; \
    fi \
    && chmod +x ./gradlew \
    && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/uploads/reviews
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8086
ENV APP_UPLOAD_ROOT_DIR=/app/uploads
ENV APP_UPLOAD_REVIEW_DIR=/app/uploads/reviews
COPY --from=build /workspace/services/engagement-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
