#!/bin/bash
set -e

docker build -t zipkin-build -f Dockerfile-build .

docker rm zipkin-build-run >/dev/null || echo >/dev/null
docker run --name zipkin-build-run -it \
  -v $HOME/.ivy2:/root/.ivy2 \
  -v $HOME/.m2:/root/.m2 \
  zipkin-build

mkdir -p dist/zipkin
cd dist/zipkin

rm zipkin-query-service.zip
rm -r query-service
docker cp zipkin-build-run:/src/zipkin-query-service/dist/zipkin-query-service.zip .
mkdir query-service
unzip zipkin-query-service.zip -d query-service

rm zipkin-web.zip
rm -r web
docker cp zipkin-build-run:/src/zipkin-web/dist/zipkin-web.zip .
mkdir web
unzip zipkin-web.zip -d web
mkdir -p web/zipkin-web/src/main
mv web/resources web/zipkin-web/src/main

docker build -t zipkin .
#docker build -t zipkin-collector dist/zipkin-collector
