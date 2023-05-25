

# build libs libsecp256k1

FROM ubuntu:20.04 AS build


ENV BIGTANGLEVERSION 0.3.5

RUN apt-get update && apt-get -y install git  
RUN apt-get install -y autoconf libtool make 
RUN  git clone https://github.com/bigtangle/secp256k1.git
RUN cd secp256k1/ && git checkout jni && ./autogen.sh \
 && ./configure --enable-jni --enable-experimental --enable-module-schnorr --enable-module-ecdh \
 && make &&  cp .libs/libsecp256k1.so /usr/lib/ 

RUN mkdir -p /app/bigtangle-server/
COPY   ca.pkcs12   /app/bigtangle-server/
COPY bigtangle-server-${BIGTANGLEVERSION}.tar \
     /app/bigtangle-server.tar

RUN tar -xvf  /app/bigtangle-server.tar -C  /app

RUN mv  /app/bigtangle-server-${BIGTANGLEVERSION}/* /app/bigtangle-server


FROM openjdk:11-jre-slim
RUN mkdir -p /app/bigtangle-server
COPY --from=build /app/bigtangle-server  /app/bigtangle-server
ENTRYPOINT ["/app/bigtangle-server/bin/bigtangle-server"]
