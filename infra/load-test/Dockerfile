FROM grafana/k6:latest

WORKDIR /scripts

COPY scenarios/ /scripts/scenarios/
COPY run.sh /scripts/run.sh

ENTRYPOINT ["/bin/sh", "/scripts/run.sh"]
CMD ["concurrent-users"]
