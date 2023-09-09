#! /bin/bash

set -e # stop executing the script if any command fails

while getopts z: flag
do
    case "${flag}" in
        z) zone_name=${OPTARG};;
    esac
done

global_cp=$GLOBAL_CP_IP_ADDRESS

KUMA_MODE=zone \
KUMA_MULTIZONE_ZONE_NAME=$zone_name \
KUMA_MULTIZONE_ZONE_KDS_TLS_SKIP_VERIFY=true \
KUMA_DIAGNOSTICS_SERVER_PORT=6680 \
KUMA_API_SERVER_HTTP_PORT=6681 \
KUMA_API_SERVER_HTTPS_PORT=6682 \
KUMA_INTER_CP_SERVER_PORT=6683 \
KUMA_MULTIZONE_ZONE_GLOBAL_ADDRESS=grpcs://$global_cp:5685 \
kuma-cp run > logs/zone-cp.log 2>&1 &
