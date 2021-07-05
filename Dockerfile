FROM maven:3.6-jdk-8-alpine as builder
COPY --from=pontusvisiongdpr/pontus-graphdb-lib /root/.m2/ /root/.m2/
ADD pom.xml /pontus-nifi-processors-office365/
ADD ./nifi-office365-api/pom.xml  /pontus-nifi-processors-office365/nifi-office365-api/
ADD ./nifi-office365-controllerservice/pom.xml  /pontus-nifi-processors-office365/nifi-office365-controllerservice/
ADD ./nifi-office365-nar/pom.xml  /pontus-nifi-processors-office365/nifi-office365-nar/
ADD ./nifi-office365-processors/pom.xml  /pontus-nifi-processors-office365/nifi-office365-processors/
WORKDIR /pontus-nifi-processors-office365
RUN mvn -q -B verify --fail-never
COPY . /pontus-nifi-processors-office365/
#RUN mvn -q package -U -DskipTests
RUN mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B clean package -U -DskipTests

FROM scratch
COPY --from=builder /pontus-nifi-processors-office365/*/target/*nar /opt/nifi/nifi-current/lib/

