FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY target/aggregation-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
