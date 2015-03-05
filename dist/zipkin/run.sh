#!/bin/bash

java -cp query-service/libs -jar query-service/zipkin-query-service-1.2.0-SNAPSHOT.jar -f query-service/config/query-redis-env.scala &

exec java -cp web/libs -jar web/zipkin-web-1.2.0-SNAPSHOT.jar
