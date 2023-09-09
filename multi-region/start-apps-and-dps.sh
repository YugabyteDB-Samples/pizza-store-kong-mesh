#! /bin/bash

set -e # stop executing the script if any command fails

echo Starting Kitchen Service

cd ../kitchen

mvn spring-boot:run > ../multi-region/logs/kitchen-service.log 2>&1 &

cd ..

echo Generating Kitchen Data Plane Token

kumactl generate dataplane-token \
    --tag kuma.io/service=kitchen-service \
    --valid-for=720h > /tmp/kuma-token-kitchen-service

echo Starting Kitchen Data Plane

kuma-dp run \
    --cp-address=https://localhost:5678 \
    --dataplane-file=standalone/kitchen-dp-config.yaml \
    --dataplane-token-file=/tmp/kuma-token-kitchen-service \
    > multi-region/logs/kitchen-dp.log 2>&1 &

echo Starting Tracker Service

cd tracker

mvn spring-boot:run > ../multi-region/logs/tracker-service.log 2>&1 &

cd ..

echo Generating Tracker Data Plane Token

kumactl generate dataplane-token \
    --tag kuma.io/service=tracker-service \
    --valid-for=720h > /tmp/kuma-token-tracker-service

echo Starting Tracker Data Plane

kuma-dp run \
    --cp-address=https://localhost:5678 \
    --dataplane-file=standalone/tracker-dp-config.yaml \
    --dataplane-token-file=/tmp/kuma-token-tracker-service \
    > multi-region/logs/tracker-dp.log 2>&1 &

