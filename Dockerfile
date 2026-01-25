# 1. Uzmi Java sliku
FROM eclipse-temurin:17-jdk-alpine
# 2. Postavi radni direktorij
WORKDIR /app

# 3. Kopiraj JAR iz targeta (ovdje pazi na naziv)
COPY target/*.jar app.jar

# 4. Kopiraj Firebase ključ da bude unutra
COPY src/main/resources/serviceAccount.json /app/config/serviceAccount.json

# 5. Pokreni
ENTRYPOINT ["java", "-jar", "app.jar"]