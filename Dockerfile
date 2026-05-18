FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/bioafis-*.jar app.jar
EXPOSE 8021
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-jar", "app.jar"]
