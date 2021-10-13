FROM alpine:3.9

LABEL maintainer="info@redmic.es"

RUN apk add --no-cache \
		curl=7.64.0-r5

COPY script /

COPY src /redmic-nifi-conf

ENTRYPOINT ["/entrypoint.sh"]
