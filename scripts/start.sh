#!/bin/bash
set -e
cd /app/washer
docker build -f stage.dockerfile -t washer-backend-v2:latest .
docker run -d \
  --name washer-backend-v2 \
  --env-file /app/washer/.env.stage \
  -p 8080:8080 \
  --restart unless-stopped \
  washer-backend-v2:latest
docker image prune -f
sleep 10
docker ps | grep washer-backend-v2
