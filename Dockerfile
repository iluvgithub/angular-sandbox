# --- Build stage -------------------------------------------------------------
# frontend-maven-plugin downloads its own local Node/npm, so all we need here
# is Maven + a JDK. It builds the Angular app and packages it onto the
# classpath of the fat jar as part of `mvn package`.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY . .
RUN mvn -q -B package -DskipTests

# --- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /build/target/uppercase-app-1.0.0-jar-with-dependencies.jar ./app.jar

# Render injects PORT at runtime; 8080 is just the local-dev default.
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
