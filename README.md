# Deploy and Development

 
##  start with docker build and Test
```
 cd helper/bigtangle
 sh build
 
 sh helper/testdocker.sh
 
```
 
## Production
```
 cd helper/divers
 sh db.sh
 sh bigtangle.sh
 
``` 


### Compiling yourself  

Make sure to have Java 17 installed on your computer.

#### To compile & package
```
$ git clone https://git.dasimi.com/digi/bigtangle.git
$ cd bigtangle
$ mvn clean install

```
#For development using the latest eclipse
checkout this project and import this project.  


# start the server
java  -Xmx5028m --add-exports java.base/sun.nio.ch=ALL-UNNAMED net.bigtangle.server.ServerStart

