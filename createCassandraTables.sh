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

echo "create table 'headers'"
execCqlsh "
CREATE TABLE headers (
    hash binary(32) not null,
    height bigint ,
    header binary(4000) ,
    wasundoable boolean ,
    prevblockhash  binary(32) ,
    prevbranchblockhash  binary(32) ,
    mineraddress binary(255),
    tokenid binary(255),
    blocktype bigint ,
    PRIMARY KEY (hash)  
)
WITH bloom_filter_fp_chance = 0.01
    AND caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
    AND compression = {'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND crc_check_chance = 1.0
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99PERCENTILE'
;"
 