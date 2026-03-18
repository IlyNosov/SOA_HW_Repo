FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY flight-service-proto/pom.xml flight-service-proto/
COPY flight-service/pom.xml flight-service/
COPY booking-service/pom.xml booking-service/

RUN mvn dependency:go-offline -pl flight-service-proto,flight-service -am -B || true

COPY flight-service-proto/ flight-service-proto/
COPY flight-service/ flight-service/

RUN mvn package -pl flight-service-proto,flight-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/flight-service/target/*.jar app.jar
EXPOSE 9090 8081
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]