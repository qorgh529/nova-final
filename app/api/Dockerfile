# E1-11: distroless 기반, non-root, 셸 없는 이미지
# (셸이 없어 "Java 프로세스가 셸을 실행" 같은 Falco 탐지가 명확해진다)
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle --no-daemon clean bootJar

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
