# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /app

# Override for slow networks, e.g. China:
#   --build-arg NPM_REGISTRY=https://registry.npmmirror.com
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package*.json ./
# npm 11+ rejects `npm config set supportedArchitectures.*`; alpine is already linux/musl/x64.
RUN echo "registry=$NPM_REGISTRY" > .npmrc \
    && npm ci --no-audit --no-fund

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
