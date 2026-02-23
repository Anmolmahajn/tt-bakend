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

# Set timezone to IST
ENV TZ=Asia/Kolkata
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Copy the built JAR from Stage 1
COPY --from=build /app/target/*.jar app.jar

# Expose the port Spring Boot listens on
EXPOSE 8080

# Start the app with IST timezone
# Use shell form so $PORT env var is expanded at runtime (required for Back4App)
CMD java -Duser.timezone=Asia/Kolkata -Dserver.port=${PORT:-8080} -jar app.jar