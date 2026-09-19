FROM eclipse-temurin:21-jdk

WORKDIR /app/backend

COPY backend/src ./src
COPY frontend /app/frontend

EXPOSE 8080

CMD ["sh", "-c", "mkdir -p out data uploads && javac -d out $(find src -name '*.java') && java -cp out com.usmconnect.Main"]