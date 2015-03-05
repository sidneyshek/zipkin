info:
	@echo "to run zipkin (query and web): make zipkin"

redis:
	docker rm -f redis 2>/dev/null || true
	docker run --name redis -d -p 6379:6379 redis

zipkin: redis
	docker rm -f zipkin 2>/dev/null || true
	docker run --name zipkin -d -p 8080:8080 -e ZIPKIN_REDIS_HOST=172.17.42.1 zipkin

zipkin-collector: redis
	docker rm -f zipkin-collector 2>/dev/null || true
	docker run --name zipkin-collector -d -p 8081:8080 -e ZIPKIN_REDIS_HOST=172.17.42.1 zipkin-collector
