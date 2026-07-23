#!/usr/bin/env bash
# Rebuild archops-* images on a host that already has older compose-* images cached,
# without pulling from Docker Hub. Expects:
#   /opt/archops/backend/target/*.jar
#   /opt/archops/frontend/dist + nginx.conf
#   local images: compose-backend:latest, compose-frontend:latest (or set BASE_*)
set -euo pipefail

ROOT="${ROOT:-/opt/archops}"
BASE_BACKEND="${BASE_BACKEND:-compose-backend:latest}"
BASE_FRONTEND="${BASE_FRONTEND:-compose-frontend:latest}"
JAR="$(ls "$ROOT"/backend/target/*.jar | head -1)"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

cat >"$tmpdir/Dockerfile.backend" <<EOF
FROM ${BASE_BACKEND}
WORKDIR /app
COPY $(basename "$JAR") app.jar
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"
ENTRYPOINT ["sh", "-c", "exec java \$JAVA_OPTS -jar app.jar"]
EOF

cp "$JAR" "$tmpdir/"
docker build -t archops-backend:latest -f "$tmpdir/Dockerfile.backend" "$tmpdir"

mkdir -p "$tmpdir/fe/dist"
cp "$ROOT/frontend/nginx.conf" "$tmpdir/fe/"
cp -a "$ROOT/frontend/dist/." "$tmpdir/fe/dist/"
cat >"$tmpdir/Dockerfile.frontend" <<EOF
FROM ${BASE_FRONTEND}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EOF
docker build -t archops-frontend:latest -f "$tmpdir/Dockerfile.frontend" "$tmpdir/fe"

echo "Built archops-backend:latest and archops-frontend:latest"
