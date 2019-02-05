FROM alpine:3.9

LABEL maintainer="info@redmic.es"

COPY script /

COPY src /redmic-nifi-conf

ENTRYPOINT ["/entrypoint.sh"]
