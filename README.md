# Installing

You have two options, the preferred option is that you compile yourself. The second option is that you utilize the provided jar, which is released regularly (when new updates occur) here: [ Releases](https://).


### Compiling yourself  

Make sure to have Java 8 installed on your computer.

#### To compile & package
```
$ git clone https://git.dasimi.com/digi/bigtangle.git
$ cd bigtangle
$ ./gradlew build 

```


#For development using the latest eclipse
checkout this project and import this project. It may require the configure of the  project as gradle project.

use the format preference-> java -> code style -> formatter import the file helper/eclipse.xml



#test of client needs the server to be started
com.bigtangle.server.ServerStart


## docker service

docker network create --driver bridge   bigtangle-bridged-network


export BIGTANGLEVERSION=0.3.5
export DBHOST=test-mysql39-hs 
export SERVERHOST=test39-bigtangle-de
export REQUESTER=https://test.bigtangle.info:8089
export SERVER_MINERADDRESS=1LLtbSLJJn1D2churfWG55aDYqQQTu4eqH
export KAFKA=test.kafka.bigtangle.de:9092
export TOPIC_OUT_NAME=bigtangle
export SERVER_NET=Test
export SSL=true
export KEYSTORE=/app/bigtangle-server/ca.pkcs12
export SERVICE_MINING=true
export DB_PASSWORD=test1234

docker rm -f $DBHOST 
rm -fr /data/vm/$DBHOST/*
docker run -d --net=bigtangle-bridged-network     \
-v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql   \
-e MYSQL_ROOT_PASSWORD=$DB_PASSWORD -e REPLICATION_MASTER=true   \
-e REPLICATION_USER=replica2008 -e REPLICATION_PASS=replica2008   \
-e MYSQL_DATABASE=info  --name=$DBHOST  -h $DBHOST.hs.dasimi.com registry.dasimi.com/mysql 


 
