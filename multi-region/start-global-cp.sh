#! /bin/bash

mkdir  logs 2> /dev/null || true

set -e # stop executing the script if any command fails

KUMA_MODE=global kuma-cp run > logs/global-cp.log 2>&1 &
