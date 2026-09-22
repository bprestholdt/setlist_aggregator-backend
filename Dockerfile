# Build stage: compile the Spring Boot jar with the Maven wrapper
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Run stage: slim JRE only
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/setlistaggregator-0.0.1-SNAPSHOT.jar app.jar
# Tuned to fit a 512MB free-tier instance
ENV JAVA_OPTS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
