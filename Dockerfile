FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /app/target/seatbook-0.1.0.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health/liveness || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
