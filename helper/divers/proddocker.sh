 

## docker service

sudo docker network create --driver bridge   cc-bridged-network
 
export BIGTANGLEVERSION=0.3.5 
export DBHOST=bigtangle-mysql
export SERVERHOST=bigtangle
export REQUESTER=https://bigtangle.de:8088
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
 
sudo rm -fr /data/vm/$DBHOST/*
sudo  mkdir -p /data/vm/$DBHOST/var/lib/mysql
docker rm -f $DBHOST 

sudo docker run -d  -t --net=cc-bridged-network  \
-v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql  \
-e MYSQL_ROOT_PASSWORD=$DB_PASSWORD   \
-e MYSQL_DATABASE=info  --name=$DBHOST  -h $DBHOST   mysql:8.0.23 


docker rm -f $SERVERHOST 

docker  run -d -t --net=cc-bridged-network    \
-p $SERVERPORT:8088 --name  $SERVERHOST \
-e DB_PASSWORD=$DB_PASSWORD -e SERVER_PORT=8088  -e DB_NAME=info \
-e DB_HOSTNAME=$DBHOST  -e SERVICE_MCMC_RATE=1000 \
-e SERVER_MINERADDRESS=$SERVER_MINERADDRESS -e SERVERMODE= \
-e BOOT_STRAP_SERVERS=$KAFKA    -e TOPIC_OUT_NAME=$TOPIC_OUT_NAME \
-e CONSUMERIDSUFFIX=$SERVERHOST.$HOSTNAME \
-e REQUESTER=$REQUESTER -e CHECKPOINT=$CHECKPOINT \
-e SERVICE_MINING=$SERVICE_MINING -e SERVICE_MCMC=true \
-e SERVER_NET=$SERVER_NET -e SSL=$SSL -e KEYSTORE=$KEYSTORE \
-h $SERVERHOST  j0904cui/bigtangle

 
sleep 60s
docker exec  $SERVERHOST /bin/sh -c " tail -f /var/log/supervisor/serverstart-stdout*"
 
 
docker cp bigtangle-mysql.sql bigtangle-mysql:/root
docker exec -it bigtangle-mysql bash
mysql -u root -ptest1234 < /root/bigtangle-mysql.sql

mkdir /var/lib/mysql/backup

mysqldump -u root -ptest1234 --databases info | gzip -c > /var/lib/mysql/$(date +"%Y-%b-%d")_info-backup.sql.gz


sudo  rsync -avz -e "ssh -i /data/git/sshkeys/cui/id_rsa  "  \
  root@bigtangle.de:/data/vm/test-sync-mysql/var/lib/mysql/$(date +"%Y-%b-%d")_info-backup.sql.gz \
 /data/vm/bigtangle-mysql/var/lib/mysql/
  
  cd /var/lib/mysql/
  gzip -d  $(date +"%Y-%b-%d")_info-backup.sql.gz 
  docker cp $(date +"%Y-%b-%d")_info-backup.sql bigtangle-mysql:/root
 sudo mv $(date +"%Y-%b-%d")_info-backup.sql  /data/vm/bigtangle-mysql/var/lib/mysql/
  docker exec -it bigtangle-mysql bash
   mysql -u root -ptest1234 < /var/lib/mysql/$(date +"%Y-%b-%d")_info-backup.sql

 