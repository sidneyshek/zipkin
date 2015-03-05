FROM lukasz/docker-scala

ADD . /src
WORKDIR /src

CMD bin/sbt zipkin-web/package-dist &&\
    bin/sbt zipkin-query-service/package-dist &&\
    bin/sbt zipkin-collector-service/package-dist
