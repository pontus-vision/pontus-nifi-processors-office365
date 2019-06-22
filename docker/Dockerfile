FROM maven:3.6-jdk-8-alpine as builder
RUN apk add git && \
    cd / && \
    git  clone --depth 1  --single-branch --branch master https://github.com/pontus-vision/pontus-nifi-processors-office365.git && \
    cd pontus-nifi-processors-office365 && \
    mvn clean package -U -DskipTests

FROM alpine
RUN   mkdir -p /opt/nifi/nifi-current/lib
COPY --from=builder /pontus-nifi-processors-office365/*/target/*nar /opt/nifi/nifi-current/lib/

