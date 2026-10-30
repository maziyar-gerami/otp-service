FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src/main src/main
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 otp && useradd --uid 10001 --gid otp --no-create-home otp
WORKDIR /app
COPY --from=build --chown=otp:otp /workspace/target/otp-service-1.0.0.jar app.jar
USER otp:otp
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
