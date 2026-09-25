# ---------- Stage 1: build everything with Maven (which drives npm/ng too) ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy POMs first for better layer caching
COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
COPY frontend/pom.xml frontend/pom.xml

# Copy sources
COPY backend backend
COPY frontend frontend

# Builds frontend module first (npm install + ng build), then backend
# (copies compiled Angular assets in, compiles Scala, assembles fat jar)
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: slim runtime image ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/backend/target/backend-*.jar app.jar

# Render (and most PaaS) injects PORT at runtime; default kept for local `docker run`
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
