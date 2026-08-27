FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
WORKDIR /workspace/services/order-service
RUN GRADLE_ZIP="$(tr -d '\r' < gradle/wrapper/gradle-wrapper.properties | sed -n 's#^distributionUrl=.*/\([^/]*\.zip\)$#\1#p')" \
    && if [ -n "$GRADLE_ZIP" ] && [ -f "/workspace/$GRADLE_ZIP" ]; then \
        sed -i "s#^distributionUrl=.*#distributionUrl=file:/workspace/$GRADLE_ZIP#" gradle/wrapper/gradle-wrapper.properties; \
    fi \
    && chmod +x ./gradlew \
    && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8083
COPY --from=build /workspace/services/order-service/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
