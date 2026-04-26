FROM amazoncorretto:25-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY app.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
CMD ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]