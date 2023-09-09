#! /bin/bash

mkdir  logs 2> /dev/null || true

set -e # stop executing the script if any command fails

KUMA_MODE=global \
KUMA_DIAGNOSTICS_SERVER_PORT=6680 \
KUMA_API_SERVER_HTTP_PORT=6681 \
KUMA_API_SERVER_HTTPS_PORT=6682 \
KUMA_INTER_CP_SERVER_PORT=6683 \
kuma-cp run > logs/global-cp.log 2>&1 &
