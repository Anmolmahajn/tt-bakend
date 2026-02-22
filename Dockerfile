# ─── Stage 1: Build the JAR ───────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first so Maven downloads dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Run the JAR ─────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR from Stage 1
COPY --from=build /app/target/*.jar app.jar

# Expose the port Spring Boot listens on
EXPOSE 8080

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"]