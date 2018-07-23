# Installing

You have two options, the preferred option is that you compile yourself. The second option is that you utilize the provided jar, which is released regularly (when new updates occur) here: [ Releases](https://).



#### Locally

```
java -jar bigtangle-server.jar -Xms512m -Xmx1g 
```

### Compiling yourself  

Make sure to have Java 8 installed on your computer.

#### To compile & package
```
$ git clone https://git.dasimi.com/digi/bigtangle.git
$ cd bigtangle
$ ./gradlew build 

```


#For development using the latest eclipse
checkout this project and import this project. It may require the configure of the  project as gradle project.

use the format preference-> java -> code style -> formatter import the file designdoc/eclipse.xml



#test of client needs the server to be started
com.bigtangle.server.ServerStart



 
