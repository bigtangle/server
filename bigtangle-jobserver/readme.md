start spark job server as
sbt


job-server-extras/reStart  /home/cui/git/bigdata/pn_discovery/gd_sparkserver/application.conf --- -Xdebug -Xrunjdwp:transport=dt_socket,address=15000,server=y,suspend=y


 curl -d 'jsonRequest={"operation":"registermysql", "table":"headers"}' 'localhost:8090/jobs?appName=test&classPath=de.gd.analytics.sparkjob.SqlJob&sync=true&context=mytestcontext'
 
 curl -d 'jsonRequest={"operation":"schema", "table":"headers" }' 'localhost:8090/jobs?appName=test&classPath=de.gd.analytics.sparkjob.SqlJob&sync=true&context=mytestcontext'
 
curl -d 'jsonRequest={"operation":"select", "table":"headers","columns":"*"}' 'localhost:8090/jobs?appName=test&classPath=de.gd.analytics.sparkjob.SqlJob&sync=true&context=mytestcontext'
 