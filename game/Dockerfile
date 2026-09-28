# Banner & Blade — the whole website in one container: the game page and the
# multiplayer server that plays it, on one port.
#
#   docker build -t banner-and-blade .
#   docker run -p 8787:8787 -v bb-data:/data banner-and-blade
#   open http://localhost:8787
#
# Stage 1 builds the single-file game; stage 2 is Node plus two files. The
# server has no dependencies at all, so there is nothing to install at runtime.

FROM node:22-slim AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build:single

FROM node:22-alpine
WORKDIR /app
COPY server/server.mjs ./server.mjs
COPY --from=build /src/dist/banner-and-blade.html ./public/index.html

# Community maps and player ratings live in /data — mount a volume there or
# they reset on every deploy. Managed hosts inject PORT.
ENV PORT=8787 NODE_ENV=production \
    GAME_HTML=/app/public/index.html \
    MAPS_FILE=/data/community-maps.json \
    PLAYERS_FILE=/data/players.json
RUN mkdir -p /data && chown node:node /data
VOLUME /data
EXPOSE 8787
USER node

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.mjs"]
