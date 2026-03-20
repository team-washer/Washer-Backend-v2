#!/bin/bash
set -e

echo "배포 완료 대기 중..."
for i in $(seq 1 60); do
  if grep -q "DEPLOY_SUCCESS" /tmp/washer-deploy.log 2>/dev/null; then
    echo "배포 성공"
    docker ps | grep washer-backend-v2
    exit 0
  fi
  if grep -q "DEPLOY_FAILED" /tmp/washer-deploy.log 2>/dev/null; then
    echo "배포 실패 - 로그:"
    cat /tmp/washer-deploy.log
    exit 1
  fi
  sleep 10
done

echo "타임아웃 - 로그:"
cat /tmp/washer-deploy.log
exit 1
