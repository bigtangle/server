FROM anapsix/alpine-java
WORKDIR /app

ARG VERSION
ARG APPNAME
ARG PROJECTGROUP
ARG PROJECTNAME

LABEL net.bigtangle.ri.group="${PROJECTGROUP}" net.bigtangle.ri.name="${PROJECTNAME}" net.bigtangle.ri.version="${VERSION}"

ADD build/libs/bigtangle-server*.jar /app/bigtangle-server.jar
COPY entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
