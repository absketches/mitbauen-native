FROM node:22-bookworm-slim AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend-build /workspace/frontend/dist frontend/dist

RUN mvn -f backend/pom.xml -q -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=backend-build /workspace/backend/target/mitbauen-native-backend.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
