#!/bin/bash
set -e
cd /app/washer
docker build -f stage.dockerfile -t washer-backend-v2:latest .
docker run -d \
  --name washer-backend-v2 \
  --env-file /app/washer/.env.stage \
  --network host \
  --restart unless-stopped \
  washer-backend-v2:latest
docker image prune -f
docker ps | grep washer-backend-v2
