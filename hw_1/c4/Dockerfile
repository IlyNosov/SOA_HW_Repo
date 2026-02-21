# Используем базовый образ Node.js
FROM node:18-slim

RUN npm install -g likec4

RUN npx playwright install --with-deps chromium

WORKDIR /workspace

COPY my_arc.likec4 ./

CMD ["likec4", "export", "png", "my_arc.likec4", "-o", "/output"]