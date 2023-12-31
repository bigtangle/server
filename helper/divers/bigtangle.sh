 
export BIGTANGLEVERSION=0.3.6
export DBHOST=bigtangle-mysql
export SERVERHOST=bigtangle
export REQUESTER=https://bigtangle.org:8088
export KAFKA=bigtangle.de:9092
export SERVER_MINERADDRESS=1CWxNAAAmTVRqodSSXTatxSopKEAD9EJw8
export TOPIC_OUT_NAME=bigtangle
export SERVER_NET=Mainnet
export SSL=true
export KEYSTORE=/app/bigtangle-server/ca.pkcs12
export SERVICE_MINING=false
export DB_PASSWORD=test1234
export SERVICE_MINING_RATE=15000
export SERVERPORT=8088
export SERVICE_MCMC=true
export SERVICE_INITSYNC=true
 


docker rm -f $SERVERHOST 

docker  run -d -t --net=cc-bridged-network   --link $DBHOST  \
-p $SERVERPORT:8088 --name  $SERVERHOST \
-e JAVA_OPTS="-Ddebug  -Xmx5028m --add-exports java.base/sun.nio.ch=ALL-UNNAMED -Dfile.encoding=UTF-8" \
-e DB_PASSWORD=$DB_PASSWORD -e SERVER_PORT=8088  -e DB_NAME=info \
-e DB_HOSTNAME=$DBHOST  -e SERVICE_MCMC_RATE=$SERVICE_MINING_RATE -e SERVICE_INITSYNC=$SERVICE_INITSYNC \
-e SERVER_MINERADDRESS=$SERVER_MINERADDRESS -e JAVA_OPTS=$JAVA_OPTS \
-e BOOT_STRAP_SERVERS=$KAFKA    -e TOPIC_OUT_NAME=$TOPIC_OUT_NAME \
-e CONSUMERIDSUFFIX=$SERVERHOST.$HOSTNAME \
-e REQUESTER=$REQUESTER  \
-e SERVICE_MINING=$SERVICE_MINING -e SERVICE_MCMC=$SERVICE_MCMC \
-e SERVER_NET=$SERVER_NET -e SSL=$SSL -e KEYSTORE=$KEYSTORE \
-h $SERVERHOST  j0904cui/bigtangle:$BIGTANGLEVERSION

 