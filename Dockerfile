FROM alpine:3.9

LABEL maintainer="info@redmic.es"

RUN apk add --no-cache --virtual \
		.build-deps \
		curl=7.64.0-r2

COPY script /

COPY src /redmic-nifi-conf

ENTRYPOINT ["/entrypoint.sh"]
