# El JAR se construye ANTES de docker compose up con:
#   ./mvnw clean package -DskipTests
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
USER appuser

COPY --chown=appuser:appgroup target/discovery-server.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
