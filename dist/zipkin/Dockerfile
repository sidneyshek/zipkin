FROM dockerfile/java:oracle-java8

ADD query-service /srv/query-service
ADD web /srv/web
ADD zipkin-web/src/main /srv/zipkin-web/src/main
ADD run.sh /srv/
WORKDIR /srv

CMD ./run.sh
