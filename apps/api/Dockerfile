FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY apps/api/gradlew apps/api/settings.gradle.kts apps/api/build.gradle.kts ./
COPY apps/api/gradle ./gradle
COPY apps/api/src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /workspace/build/libs/api.jar ./api.jar
USER 10001:10001
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
