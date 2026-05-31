# Build the service fat jar from the reactor, then run it on a slim JRE.
# Build context is the java/ directory:
#   docker build -f service/Dockerfile -t gaslt-service .
#   docker run --rm -p 8080:8080 gaslt-service

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY service/pom.xml service/pom.xml
# Warm the dependency cache for the service and its core dependency.
RUN mvn -q -B -ntp -pl core,service dependency:go-offline || true
COPY core core
COPY service service
RUN mvn -q -B -ntp -pl service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/service/target/gaslt-service.jar ./gaslt-service.jar
ENV PORT=8080
ENV GASLT_RPC=https://api.mainnet-beta.solana.com
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gaslt-service.jar"]
