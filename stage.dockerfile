FROM gradle:jdk25-alpine AS build

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

ARG RDB_URL
ARG RDB_USERNAME
ARG RDB_PASSWORD
ARG REDIS_HOST
ARG REDIS_PORT
ARG REDIS_PASSWORD
ARG DISCORD_WEBHOOK_URL

ENV SPRING_DATASOURCE_URL=${RDB_URL}
ENV SPRING_DATASOURCE_USERNAME=${RDB_USERNAME}
ENV SPRING_DATASOURCE_PASSWORD=${RDB_PASSWORD}
ENV SPRING_DATA_REDIS_HOST=${REDIS_HOST}
ENV SPRING_DATA_REDIS_PORT=${REDIS_PORT}
ENV SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD}
ENV THIRD_PARTY_DISCORD_WEBHOOK_URL=${DISCORD_WEBHOOK_URL}

EXPOSE 8080

CMD ["java", "-jar", "-Dspring.profiles.active=stage", "app.jar"]