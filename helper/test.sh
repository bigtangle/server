

export BIGTANGLEVERSION=2022-01-29
export DBHOST=bigtangle-test-mysql
export SERVERHOST=bigtangle-test
#export KAFKA=bigtangle.de:9092
export SERVER_MINERADDRESS=1CWxNAAAmTVRqodSSXTatxSopKEAD9EJw8
#export REQUESTER=https://bigtangle.de:8088
export TOPIC_OUT_NAME=bigtangle
export SERVER_NET=Test
export SSL=true
export KEYSTORE=/app/bigtangle-server/ca.pkcs12
export SERVICE_MINING=false
export DB_PASSWORD=test1234
export SERVICE_MINING_RATE=15000
export SERVERPORT=18088
 
sudo rm -fr /data/vm/$DBHOST/*
 
docker rm -f $DBHOST 

docker run -d  -t     \
-v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql  \
-e MYSQL_ROOT_PASSWORD=$DB_PASSWORD   \
-e MYSQL_DATABASE=info  --name=$DBHOST  -h $DBHOST   mysql:8.0.23 


docker rm -f $SERVERHOST 

docker  run -d -t --link  $DBHOST \
  -v /data/vm/$SERVERHOST:/data/vm  -v /var/run/docker.sock:/var/run/docker.sock   \
-p $SERVERPORT:8088 --name  $SERVERHOST -e DOCKERCREATEDBHOST=true  -e CHECKPOINT=500000 \
-e DB_PASSWORD=$DB_PASSWORD -e SERVER_PORT=8088  -e DB_NAME=info \
-e DB_HOSTNAME=$DBHOST -e DOCKERDBHOST=$DBHOST -e SERVICE_MCMC_RATE=1000 \
-e SERVER_MINERADDRESS=$SERVER_MINERADDRESS -e SERVERMODE= \
-e BOOT_STRAP_SERVERS=1.1.0    -e TOPIC_OUT_NAME=$TOPIC_OUT_NAME \
-e CONSUMERIDSUFFIX=$SERVERHOST.$HOSTNAME \
-e REQUESTER=$REQUESTER -e CHECKPOINT=$CHECKPOINT \
-e SERVICE_MINING=$SERVICE_MINING -e SERVICE_MCMC=true \
-e SERVER_NET=$SERVER_NET -e SSL=$SSL -e KEYSTORE=$KEYSTORE \
-h $SERVERHOST  j0904cui/bigtangle:$BIGTANGLEVERSION

 
 docker logs -f $SERVERHOST
 