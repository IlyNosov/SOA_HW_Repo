FROM eclipse-temurin:25-jre

WORKDIR /app
ARG JAR_FILE=target/consumer-service-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
