FROM registry.dasimi.com/java8
WORKDIR /app
COPY bigtangle-emptyblock-0.1.0.tar /app/bigtangle-emptyblock.tar

RUN tar -xvf /app/bigtangle-emptyblock.tar

COPY entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
