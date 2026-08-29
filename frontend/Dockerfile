# Stage 1: Build da aplicação Angular
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci
COPY . .

RUN npm run build -- --configuration production

# Stage 2: Servidor Nginx pra entregar a SPA
FROM nginx:alpine
COPY --from=build /app/dist/*/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY env.template.js /usr/share/nginx/html/env.template.js
EXPOSE 80
CMD ["/bin/sh", "-c", "envsubst '$GOOGLE_API_CLIENT_ID' < /usr/share/nginx/html/env.template.js > /usr/share/nginx/html/env.js && exec nginx -g 'daemon off;'"]
