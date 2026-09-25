# The relay has no dependencies, so this is a base image and one file.
#
# Deliberately no build step and no npm install: server.mjs uses only Node
# built-ins, which is also why it starts in milliseconds and why there is
# nothing here to keep patched beyond the base image itself.
FROM node:22-alpine

WORKDIR /app
COPY server.mjs ./

# Managed hosts inject PORT; this is only the fallback for `docker run` with no
# environment set.
ENV PORT=8787
EXPOSE 8787

# Run unprivileged. The relay never touches the filesystem.
USER node

# The platform's health check should hit /healthz; this one covers plain Docker.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.mjs"]
