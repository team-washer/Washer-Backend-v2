#!/bin/bash
set -e
cd /app/washer
if ! docker ps -q -f name=washer-redis | grep -q .; then
  if docker ps -aq -f name=washer-redis | grep -q .; then
    docker start washer-redis
  else
    docker run -d \
      --name washer-redis \
      --network host \
      --restart unless-stopped \
      redis:7-alpine \
      redis-server --requirepass rootpassword --port 29586
  fi
fi
docker build -f stage.dockerfile -t washer-backend-v2:latest .
docker run -d \
  --name washer-backend-v2 \
  --network host \
  --restart unless-stopped \
  washer-backend-v2:latest
docker image prune -f
docker ps | grep washer-backend-v2
