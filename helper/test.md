# test of token creation

1) create single token

  a) check if the token is in UTXO
  b) create a same token is impossible with info

2) create market is same as token


3) create token in series

a) create first token is impossible, if the token id exists
b) number of signature  = 1
c) set stop the token, then create new token is not possible
d) create new token series, it is possible to use the last saved signature to create a new series, 
  check it is impossible to save to use new added address
  
e) 



4) test to transfer the UTXO with new created token,  multi sign  are required for transfer the UTXO




# test of market and order using new ordermatch

 
 [15:00, 1.7.2018] +49 1512 3282316: Ok
[18:29, 1.7.2018] cui: Auf facebook benutze mein Konto Login: cui@inasset.de   pw=StartBigTangle
[18:57, 1.7.2018] cui: Hier https://www.linkedin.com/
[18:58, 1.7.2018] cui: User=cui@inasset.de  pw=Start2018



 
 Execute order
 
http://web.archive.org/web/20110310171841/http://www.quantcup.org/home/spec
 
 select all order for each token
 
 calculate the price for execution: price= max volume of orders
 max(buy limit) <  min(  sell limit)
 
 example:
 	EURO Token
 		1) buy  5  limit  7
 		2)					sell 8 limit 6
 		3) buy  8  limit  7.1
 		4)					sell 10 limit 6.5	
 	execution price = 6.5 with volume= 8+5
 					remainder 			
 	
 clearing
  
  seller exchange buyer	
  seller exchange system user, buyer does same.
  
  
  $ docker run -it --rm --net vnet smizy/apache-phoenix:4.13.1-alpine sh
> bin/sqlline-thin.py http://61.181.128.236:8765
bin/sqlline-thin.py http://42.51.129.106:8765


master hbase-site.xml

vi /usr/local/hbase-1.3.1/conf/hbase-site.xml.mustache

<property>
  <name>hbase.master.loadbalancer.class</name>                                     
  <value>org.apache.phoenix.hbase.index.balancer.IndexLoadBalancer</value>
</property>

<property>
  <name>hbase.coprocessor.master.classes</name>
  <value>org.apache.phoenix.hbase.index.master.IndexMasterObserver</value>
</property>


RegionServer/master/queryserver-1 
  hbase-site.xml

vi /usr/local/hbase-1.3.1/conf/hbase-site.xml.mustache


<property> 
  <name>hbase.regionserver.wal.codec</name> 
  <value>org.apache.hadoop.hbase.regionserver.wal.IndexedWALEditCodec</value> 
</property>

 

Given the schema shown here:

CREATE TABLE my_table (k VARCHAR PRIMARY KEY, v1 VARCHAR, v2 BIGINT);

you'd create an index on the v1 column like this:

CREATE INDEX my_index ON my_table (v1);

error
 CREATE TABLE headerstest  (  hash BINARY(32) not null,   height bigint ,  header VARBINARY(4000) ,   wasundoable boolean ,
prevblockhash  BINARY(32) ,     prevbranchblockhash  BINARY(32) ,
 mineraddress VARBINARY(255),     tokenid VARBINARY(255),     blocktype bigint ,
 CONSTRAINT headers_pk PRIMARY KEY (hash)   );
                
CREATE LOCAL INDEX headers_prevblockhash_idx ON headers (prevblockhash);
CREATE LOCAL INDEX headers_prevbranchblockhash_idx ON headers (prevbranchblockhash);


to replace to char(32)


docker exec -it hmaster-1 bash
docker exec -it regionserver-1 bash
vi /usr/local/hbase-1.3.1/conf/hbase-site.xml

docker exec hmaster-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml
docker exec regionserver-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml
docker exec queryserver-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml

!indexs
docker restart zookeeper-1 namenode-1  
docker restart datanode-1
 docker restart hmaster-1 regionserver-1 queryserver-1 
 docker exec namenode-1  bash  -c " hdfs   dfsadmin -report"
 
docker logs -f  hmaster-1
 docker logs -f regionserver-1
  docker logs -f queryserver-1 
 
 $ docker run -it --rm --net vnet smizy/apache-phoenix:4.13.1-alpine sh
> bin/sqlline-thin.py http://61.181.128.236:8765


