# openHealth sync server. Build from the repository root:
#   docker build -f server/Dockerfile -t openhealth-server .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && \
    ./gradlew --configure-on-demand :server:installDist --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home /app openhealth
WORKDIR /app
COPY --from=build /src/server/build/install/server/ /app/
USER openhealth
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
