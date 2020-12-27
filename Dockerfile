FROM maven:3.6-jdk-8-alpine as builder
COPY . /pontus-nifi-processors-office365/
WORKDIR /pontus-nifi-processors-office365
RUN mvn -q clean package -U -DskipTests

FROM scratch
COPY --from=builder /pontus-nifi-processors-office365/*/target/*nar /opt/nifi/nifi-current/lib/

