#!/bin/bash
docker stop washer-backend-v2 || true
docker rm washer-backend-v2 || true
