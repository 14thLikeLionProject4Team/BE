FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# 테스트는 로컬 MySQL이 필요해서 이미지 빌드에서는 jar만 만든다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
