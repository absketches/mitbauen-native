FROM node:22-bookworm-slim AS frontend-base

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./

FROM frontend-base AS dev

EXPOSE 5173

CMD ["npm", "run", "dev"]

FROM mcr.microsoft.com/playwright:v1.62.1-noble AS browser-test

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./

CMD ["npm", "run", "test:browser"]
