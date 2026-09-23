#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
host="${ZHITI_DEPLOY_HOST:-ubuntu@43.136.39.211}"
remote="${ZHITI_REMOTE_DIR:-/opt/zhiti}"

test -f "$root/.secrets/server.env"
test -f "$root/.secrets/server.crt"
test -f "$root/content-pipeline/build/catalog.sig"

ssh "$host" "sudo mkdir -p '$remote/infra/runtime/data' '$remote/infra/runtime/content' '$remote/infra/runtime/certs' && sudo chown -R \$USER '$remote'"
rsync -az --delete "$root/server/" "$host:$remote/server/"
rsync -az "$root/infra/docker-compose.yml" "$host:$remote/infra/docker-compose.yml"
rsync -az "$root/content-pipeline/build/" "$host:$remote/infra/runtime/content/"
rsync -az "$root/.secrets/server.crt" "$root/.secrets/server.key" "$host:$remote/infra/runtime/certs/"
rsync -az "$root/.secrets/server.env" "$host:$remote/infra/.env"
ssh "$host" "cd '$remote/infra' && docker compose up -d --build"

