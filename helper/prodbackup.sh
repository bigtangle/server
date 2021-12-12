 

## docker service

docker network create --driver bridge   bigtangle-bridged-network
 
export BIGTANGLEVERSION=11122021
export DBHOST=bigtangle-mysql-backup
export SERVERHOST=bigtangle-backup
export KAFKA=bigtangle.de:9092
export SERVER_MINERADDRESS=1CWxNAAAmTVRqodSSXTatxSopKEAD9EJw8
export REQUESTER=https://bigtangle.de:8088
export TOPIC_OUT_NAME=bigtangle
export SERVER_NET=Mainnet
export SSL=true
export KEYSTORE=/app/bigtangle-server/ca.pkcs12
export SERVICE_MINING=false
export DB_PASSWORD=test1234
export SERVICE_MINING_RATE=15000
export SERVERPORT=18088
 
# rm -fr /data/vm/$DBHOST/*
 
docker rm -f $DBHOST 

docker run -d  -t --net=bigtangle-bridged-network     \
-p 3306:3306  -v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql  \
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

 
sleep 60s
docker exec  $SERVERHOST /bin/sh -c " tail -f /var/log/supervisor/serverstart-stdout*"



 rsync -avz -e "ssh -i /data/git/sshkeys/cui/id_rsa  "  \
  root@bigtangle.de:/data/vm/bigtangle-mysql-backup/var/lib/mysql  \
  /data/vm/bigtangle-mysql-backup/var/lib/
  
  
  
   