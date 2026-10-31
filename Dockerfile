FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
COPY --from=frontend-build /workspace/frontend/dist/ backend/src/main/resources/static/
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/poultry-backend-0.1.0.jar app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
