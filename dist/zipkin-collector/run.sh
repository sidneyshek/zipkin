#!/bin/bash

exec java -cp collector-service/libs -jar collector-service/zipkin-collector-service-1.2.0-SNAPSHOT.jar -f collector-service/config/collector-redis-env.scala
