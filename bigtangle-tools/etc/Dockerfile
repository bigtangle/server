FROM anapsix/alpine-java
WORKDIR /app

ADD build/libs/bigtangle-tools*.jar /app/bigtangle-tools.jar
COPY entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
