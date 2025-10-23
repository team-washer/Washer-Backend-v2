FROM gradle:8.10-jdk25-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon


FROM amazoncorretto:25-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup


COPY --from=build /app/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

CMD ["java", "-jar", "-Dspring.profiles.active=stage", "app.jar"]