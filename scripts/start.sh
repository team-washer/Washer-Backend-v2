#!/bin/bash
set -e
cd /app/washer

nohup bash -c '
  docker build -f stage.dockerfile -t washer-backend-v2:latest . > /tmp/washer-deploy.log 2>&1 && \
  docker run -d \
    --name washer-backend-v2 \
    --env-file /app/washer/.env.stage \
    -p 8080:8080 \
    --restart unless-stopped \
    washer-backend-v2:latest >> /tmp/washer-deploy.log 2>&1 && \
  docker image prune -f >> /tmp/washer-deploy.log 2>&1 && \
  echo "DEPLOY_SUCCESS" >> /tmp/washer-deploy.log || \
  echo "DEPLOY_FAILED" >> /tmp/washer-deploy.log
' > /dev/null 2>&1 &

echo "배포 시작 (PID: $!), 로그: /tmp/washer-deploy.log"
