# frontend - Next.js multi-stage image (Task 7.1)
# Build from the frontend folder:
#   docker build -t swiftdrop/frontend:local ./frontend
# Uses Debian (glibc) node images, not Alpine: package.json pins
# @tailwindcss/oxide-linux-x64-gnu, which needs glibc.

# ---------- Stage 1: install dependencies ----------
FROM node:22-bookworm-slim AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# ---------- Stage 2: build ----------
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_* values are baked into the browser bundle at build time.
# They must be URLs the user's BROWSER can reach (the gateway on the host).
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ARG NEXT_PUBLIC_WEBSOCKET_URL=http://localhost:8080/ws-swiftdrop
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_WEBSOCKET_URL=$NEXT_PUBLIC_WEBSOCKET_URL \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---------- Stage 3: small runtime image ----------
# "standalone" output (next.config.ts) contains only the files needed to run.
FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

# "node" is the non-root user that ships with the official image
USER node
EXPOSE 3000
CMD ["node", "server.js"]
