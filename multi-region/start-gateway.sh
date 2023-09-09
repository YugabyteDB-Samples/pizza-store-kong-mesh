#! /bin/bash

set -e # stop executing the script if any command fails

echo Generating Gateway Token

kumactl generate dataplane-token \
    --tag kuma.io/service=mesh-gateway \
    --valid-for=720h > /tmp/kuma-token-mesh-gateway

echo Starting Gateway

kuma-dp run \
    --cp-address=https://localhost:5678/ \
    --dns-enabled=false \
    --dataplane-token-file=/tmp/kuma-token-mesh-gateway \
    --dataplane-file=../standalone/mesh-gateway-dp-config.yaml \
    > logs/gateway.log 2>&1 &