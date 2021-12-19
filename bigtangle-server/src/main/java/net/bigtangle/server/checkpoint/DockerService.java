package net.bigtangle.server.checkpoint;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.docker.DockerHelper;
import net.bigtangle.docker.ResultExecution;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.config.ServerConfiguration;

/*
 * service to control the docker container via shell
 */
@Service
public class DockerService {

    private final Log LOG = LogFactory.getLog(getClass().getName());
   
    @Autowired
    private ServerConfiguration serverConfiguration;

    @Autowired
    private DBStoreConfiguration dbStoreConfiguration;

    /*
     * create a executable file on the vm and calculate the sha256sum
     */
    public String mysqldumpCheck() {
        String re = " mysqldump --complete-insert --skip-dump-date -u " + dbStoreConfiguration.getUsername() + " -p"
                + dbStoreConfiguration.getPassword() + " --databases " + dbStoreConfiguration.getDbName()
                + " >  /tmp/bigtangle-database.sql    ";
        re += " && sha256sum /tmp/bigtangle-database.sql ";
        return re;
    }

  
    public String importDB() {
        return "  docker exec " + serverConfiguration.getDockerDBHost() + " /bin/sh -c \" " + " mysql -u "
                + dbStoreConfiguration.getUsername() + " -p" + dbStoreConfiguration.getPassword() + " --databases "
                + dbStoreConfiguration.getDbName() + " < " + "/temp/" + "bigtangle-database.sql" + "\"";
    }

    public String docker(String command) {
        return "  docker exec " + serverConfiguration.getDockerDBHost() + " /bin/sh -c \" "
                + command.replace("\"", "\\\"") + " \"";
    }

 
    public ResultExecution dockerExec(String vmname, String command) throws Exception {
        return new DockerHelper()
                .shellExecute(" docker  exec   " + vmname + " /bin/sh -c \" " + command.replace("\"", "\\\"") + "\"");
    }
 
    /*
     * remove None images
     */
    public void removeNone() throws Exception {
        List<String> cs = new ArrayList<String>();
        // cs.add(" docker pause " + getContainername(v));
        cs.add("docker rmi $(docker images | grep \"^<none>\" | awk '{print $3}')");
        // cs.add(" docker unpause " + getContainername(v));
        new DockerHelper().shellExecute(cs);
    }

}
