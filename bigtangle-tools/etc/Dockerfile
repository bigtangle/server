FROM registry.dasimi.com/java8
WORKDIR /app
COPY bigtangle-tools-0.1.0.tar /app/bigtangle-tools.tar

RUN tar -xvf /app/bigtangle-tools.tar

COPY entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
