set -x
#docker network create --driver bridge   bigtangle-bridged-network

export BIGTANGLEVERSION=0.3.6
export DBHOST=test-bigtangle-mysql
export SERVERHOST=test-bigtangle
export REQUESTER=https://test.bigtangle.info:8089
export SERVER_MINERADDRESS=1LLtbSLJJn1D2churfWG55aDYqQQTu4eqH
export KAFKA=test.kafka.bigtangle.de:9092
export TOPIC_OUT_NAME=bigtangle
export SERVER_NET=Test
export SSL=true
export KEYSTORE=/app/bigtangle-server/ca.pkcs12
export SERVICE_MINING=true
export DB_PASSWORD=test1234
export SERVERPORT=8089
export SERVERPORT SERVICE_MINING_RATE=136000
 
docker rm -f $DBHOST 
rm -fr /data/vm/$DBHOST/*
docker run -d  -t --net=bigtangle-bridged-network     \
-v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql   \
-e MYSQL_ROOT_PASSWORD=$DB_PASSWORD   \
-e MYSQL_DATABASE=info  --name=$DBHOST  -h $DBHOST   mysql:8.0.23 


docker rm -f $SERVERHOST 
docker  run -d -t --net=bigtangle-bridged-network   --link $DBHOST \
-p $SERVERPORT:8088 --name  $SERVERHOST \
-e DB_PASSWORD=$DB_PASSWORD -e SERVER_PORT=8088  -e DB_NAME=info \
-e DB_HOSTNAME=$DBHOST  -e SERVICE_MCMC_RATE=1000 \
-e SERVER_MINERADDRESS=$SERVER_MINERADDRESS -e SERVERMODE= \
-e BOOT_STRAP_SERVERS=$KAFKA    -e TOPIC_OUT_NAME=$TOPIC_OUT_NAME \
-e CONSUMERIDSUFFIX=$SERVERHOST.$HOSTNAME \
-e REQUESTER=$REQUESTER -e CHECKPOINT=$CHECKPOINT \
-e SERVICE_MINING=$SERVICE_MINING -e SERVICE_MCMC=true \
-e SERVER_NET=$SERVER_NET -e SSL=$SSL -e KEYSTORE=$KEYSTORE \
-h $SERVERHOST  j0904cui/bigtangle:$BIGTANGLEVERSION


  
 