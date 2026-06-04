FROM amazoncorretto:25-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY build/libs/app.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+UseStringDeduplication", "-XX:+ExitOnOutOfMemoryError", "-jar", "-Dspring.profiles.active=stage", "app.jar"]
