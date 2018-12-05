FROM openjdk:10

RUN mkdir -p /opt/app/conf
COPY target/universal/stage/conf /opt/app/conf
RUN mkdir -p /opt/app/lib
COPY target/universal/stage/lib /opt/app/lib
COPY docker/run.sh /opt/app/run.sh
RUN chmod 755 /opt/app/run.sh

EXPOSE 9000
VOLUME ["/var/log/app", "/tmp/app", "/var/lib/app"]
HEALTHCHECK CMD curl http://localhost:9000/health || exit 1

WORKDIR /opt/app
ENTRYPOINT ["./run.sh"]
