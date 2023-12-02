

FROM ubuntu:20.04 AS build



ENV BIGTANGLEVERSION 0.3.6

RUN apt-get update && apt-get -y install git  
RUN apt-get install -y autoconf libtool make 
RUN  git clone https://github.com/bigtangle/secp256k1.git
RUN cd secp256k1/ && git checkout jni && ./autogen.sh \
 && ./configure --enable-jni --enable-experimental --enable-module-schnorr --enable-module-ecdh \
 && make &&  cp .libs/libsecp256k1.so /usr/lib/ 

RUN git config --global http.sslVerify false
RUN  git clone https://cui:Kaicui1234!@bigtangle.de:1080/digi/bignetcoin.git  /root/bignetcoin
RUN cd /root/bignetcoin && git checkout master && mvn  -DskipTests=true clean install



## runtime
FROM   docker.io/openjdk:17-jdk-slim
 
ENV BIGTANGLEVERSION 0.3.6

RUN mkdir -p /app/bigtangle-server/
COPY   ca.pkcs12   /app/bigtangle-server/
COPY --from=builder /root/bignetcoin/bigtangle-server/target/bigtangle-server-${BIGTANGLEVERSION}-exec.jar \
     /app/bigtangle-server.jar
 
COPY --from=builder /usr/lib/libsecp256k1.so  /usr/lib/

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/bigtangle-server.jar"]