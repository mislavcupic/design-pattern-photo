# 1. Koristimo standardni JDK (ne alpine!)
FROM eclipse-temurin:17-jdk

WORKDIR /app

# 2. Kopiramo JAR
COPY target/*.jar app.jar

# 3. Raspakiravamo JAR u 'target/dependency' direktorij
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../../app.jar)

# 4. Kopiramo Firebase ključ na fiksnu putanju unutar kontejnera
COPY src/main/resources/serviceAccount.json /app/config/serviceAccount.json

# 5. Pokrećemo aplikaciju
# PAŽNJA: Putanje u -cp moraju točno odgovarati onome gdje je 'jar -xf' izvukao datoteke
ENTRYPOINT ["java", "-cp", "target/dependency/BOOT-INF/classes:target/dependency/BOOT-INF/lib/*", "hr.algebra.nrako.photoapp_backend.PhotoappApplication"]