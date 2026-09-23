# Используем официальный легковесный образ JRE 21 на базе Alpine Linux
FROM eclipse-temurin:21-jre-alpine

# Устанавливаем системные шрифты, chromium для 100% точной генерации PDF и tzdata для поддержки таймзон
RUN apk add --no-cache font-dejavu chromium harfbuzz nss freetype ttf-freefont tzdata

# Устанавливаем рабочую директорию внутри контейнера
WORKDIR /app

# Копируем скомпилированный исполняемый JAR-файл
# Версия узла назначается при выпуске; простой jar не собирается, файл один
COPY server/build/libs/server-*.jar app.jar

# Создаем директорию для базы данных SQLite, чтобы ее можно было монтировать как volume
RUN mkdir -p /app/data

# Рабочее место узла называется явно: настройки и база лежат в /app,
# и переезд рабочего каталога процесса их не потеряет
ENV SUPERKASSA_HOME=/app

# Настраиваем окружение для сохранения БД в примонтированной папке
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/core.db?busy_timeout=30000

# Открываем порт 8080 для веб-сервера
EXPOSE 8080

# Команда для запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]
