#!/bin/bash
set -e

docker build -t zipkin-build .

docker rm zipkin-build-run >/dev/null || echo >/dev/null
docker run --name zipkin-build-run -it \
  -v $HOME/.ivy2:/root/.ivy2 \
  -v $HOME/.m2:/root/.m2 \
  zipkin-build

mkdir -p dist/zipkin
cd dist/zipkin

rm -f zipkin-query-service.zip
rm -rf query-service
docker cp zipkin-build-run:/src/zipkin-query-service/dist/zipkin-query-service.zip .
mkdir query-service
unzip zipkin-query-service.zip -d query-service

rm -f zipkin-web.zip
rm -rf web
docker cp zipkin-build-run:/src/zipkin-web/dist/zipkin-web.zip .
mkdir web
unzip zipkin-web.zip -d web
rm -rf zipkin-web/src/main
mkdir -p zipkin-web/src/main
mv web/resources zipkin-web/src/main

docker build -t zipkin .

mkdir -p ../zipkin-collector
cd ../zipkin-collector

rm -f zipkin-collector-service.zip
rm -rf collector-service
docker cp zipkin-build-run:/src/zipkin-collector-service/dist/zipkin-collector-service.zip .
mkdir collector-service
unzip zipkin-collector-service.zip -d collector-service

docker build -t zipkin-collector .
