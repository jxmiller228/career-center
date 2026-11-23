FROM node:20 AS front
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
COPY --from=front /app/src/main/resources/static src/main/resources/static
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
CMD ["sh","-c","java -jar app.jar --server.port=${PORT:-8080} --server.ssl.enabled=false"]
