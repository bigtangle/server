FROM anapsix/alpine-java
WORKDIR /app

ARG VERSION
ARG APPNAME
ARG PROJECTGROUP
ARG PROJECTNAME

LABEL de.db.ri.group="${PROJECTGROUP}" de.db.ri.name="${PROJECTNAME}" de.db.ri.version="${VERSION}"

ADD build/libs/as-vdv-aus-builder*.jar /app/as-vdv-aus-builder.jar
COPY entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
