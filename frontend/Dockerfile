# Stage 1: Build da aplicação Angular
FROM node:20-alpine AS build
WORKDIR /app

ARG GOOGLE_API_CLIENT_ID

COPY package*.json ./
RUN npm install
COPY . .

RUN sed -i "s|CLIENT_ID|${GOOGLE_API_CLIENT_ID}|g" src/shared/envirement/config.env.ts
RUN npm run build -- --configuration production

# Stage 2: Servidor Nginx pra entregar a SPA
FROM nginx:alpine
COPY --from=build /app/dist/*/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]