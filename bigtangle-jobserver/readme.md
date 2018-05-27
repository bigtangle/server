start spark job server sbt
install scalaide via eclipse marketplace

create eclipse project
sbt eclipse


then import this project
set the project property scala : version 2.11




job-server-extras/reStart  #path/application.conf --- -Xdebug -Xrunjdwp:transport=dt_socket,address=15000,server=y,suspend=y


 curl -d 'jsonRequest={"operation":"registermysql", "table":"headers"}' 'localhost:8090/jobs?appName=test&classPath=net.bigtangle.sparkjob.SqlJob&sync=true&context=mytestcontext'
 
 curl -d 'jsonRequest={"operation":"schema", "table":"headers" }' 'localhost:8090/jobs?appName=test&classPath=net.bigtangle.sparkjob.SqlJob&sync=true&context=mytestcontext'
 
curl -d 'jsonRequest={"operation":"select", "table":"headers","columns":"*"}' 'localhost:8090/jobs?appName=test&classPath=net.bigtangle.sparkjob.SqlJob&sync=true&context=mytestcontext'
 