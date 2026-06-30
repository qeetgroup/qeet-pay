# Backend image: build the Spring Boot fat jar, run it on a JRE 21 (TAD §3.2).
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 4201
ENTRYPOINT ["java", "-jar", "/app/app.jar"]