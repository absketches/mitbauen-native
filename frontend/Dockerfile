FROM node:22-bookworm-slim AS frontend-base

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . ./

FROM frontend-base AS dev

EXPOSE 5173

CMD ["npm", "run", "dev"]

FROM mcr.microsoft.com/playwright:v1.59.1-noble AS browser-test

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . ./

CMD ["npm", "run", "test:browser"]
