package net.bigtangle.server.checkpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.docker.DockerHelper;
import net.bigtangle.docker.ResultExecution;
import net.bigtangle.docker.ShellExecute;
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
    public String mysqldumpCheck(Long chain) {
        String file = "/var/lib/mysql/bigtangle-database-chain-" + chain + ".sql.gz";
        String re = " mysqldump --complete-insert --skip-dump-date -u " + dbStoreConfiguration.getUsername() + " -p"
                + dbStoreConfiguration.getPassword() + " --databases " + dbStoreConfiguration.getDbName()
                + "  | gzip -c >  " + file;
          re += " && sha256sum " + file + " | echo ";
        return re;
    }

    public String importDB() {
        return "  docker exec " + serverConfiguration.getDockerDBHost() + " /bin/sh -c \" " + " mysql -u "
                + dbStoreConfiguration.getUsername() + " -p" + dbStoreConfiguration.getPassword() + " --databases "
                + dbStoreConfiguration.getDbName() + " < " + "/temp/" + "bigtangle-database.sql" + "\"";
    }

    public ResultExecution dockerExec(String command) throws IOException, InterruptedException {

        return dockerExec(serverConfiguration.getDockerDBHost(), command);
    }

    public ResultExecution dockerExec(String vmname, String command) throws IOException, InterruptedException {

        ShellExecute shell = new ShellExecute();
        shell.setFile(command.getBytes());
        String filename = UUID.randomUUID().toString();
        shell.setFilelocation("/tmp/" + filename + ".sh");
        shell.setCmd(" docker cp  " + shell.getFilelocation() + " " + serverConfiguration.getDockerDBHost() + ":"
                + shell.getFilelocation());
        new DockerHelper().shellExecuteLocal(shell);
        ResultExecution re = new DockerHelper().shellExecute("  docker exec " + serverConfiguration.getDockerDBHost()
                + " /bin/sh -c  sh " + shell.getFilelocation() );
        new DockerHelper().shellExecuteLocal(" rm -f " + shell.getFilelocation());
    //    new DockerHelper().shellExecuteLocal("  docker exec " + serverConfiguration.getDockerDBHost()
    //    + " /bin/sh -c \" rm -f  " + shell.getFilelocation() + "\"");
        
        return re;

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
