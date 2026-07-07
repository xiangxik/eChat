FROM node:22-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/chat-web/package.json ./apps/chat-web/package.json
COPY apps/admin-web/package.json ./apps/admin-web/package.json
RUN npm ci

COPY apps/admin-web ./apps/admin-web
RUN npm run build:admin

FROM nginx:1.27-alpine

COPY apps/admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY apps/admin-web/docker-entrypoint.d/40-env.sh /docker-entrypoint.d/40-env.sh
RUN chmod +x /docker-entrypoint.d/40-env.sh
COPY --from=build /workspace/apps/admin-web/dist /usr/share/nginx/html

EXPOSE 80
