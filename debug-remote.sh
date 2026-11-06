#!/bin/sh
docker restart jsherp-nginx
sleep 3
curl -s -D - -o /dev/null --max-time 10 -k https://127.0.0.1:9999/ | grep -iE 'HTTP|cache-control|clear-site'
echo '---DONE---'
