#!/bin/bash
set -e
cd /home/ec2-user/builds
docker build -f prod.dockerfile -t washer-backend-v2:latest .
docker run -d \
  --name washer-backend-v2 \
  -p 8080:8080 \
  --restart unless-stopped \
  washer-backend-v2:latest
docker image prune -f
docker ps | grep washer-backend-v2