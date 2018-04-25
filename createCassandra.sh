#!/bin/bash
HOST=$1

echo running on $HOST

function execCqlsh() {
    docker-compose exec -T cassandra-as-vdv-aus cqlsh -e "$1" $HOST;
    if [ $? -ne 0 ]; then
        echo "Cqlsh execution failed! $1"
        exit 1
    fi
}

echo "drop und create keyspace 'bigtangle'"
execCqlsh "DROP KEYSPACE IF EXISTS bigtangle;";
execCqlsh "CREATE KEYSPACE bigtangle WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '3'}  AND durable_writes = true;";
 