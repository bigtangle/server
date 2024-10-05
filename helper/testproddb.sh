set -x
docker network create --driver bridge   bigtangle-bridged-network

export BIGTANGLEVERSION=0.5.0
export DBHOST=testprod-bigtangle-mysql
export DB_PASSWORD=test1234
 
docker rm -f $DBHOST   
sudo rm -fr /data/vm/$DBHOST/*
docker run -d  -t --net=bigtangle-bridged-network   -p 3308:3306 \
-v /data/vm/$DBHOST/var/lib/mysql:/var/lib/mysql   \
-e MYSQL_ROOT_PASSWORD=$DB_PASSWORD   \
-e MYSQL_DATABASE=info  --name=$DBHOST  -h $DBHOST   mysql:8.0.23 
 
 