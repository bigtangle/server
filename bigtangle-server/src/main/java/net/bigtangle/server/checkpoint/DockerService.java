package net.bigtangle.server.checkpoint;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.docker.DockerHelper;
import net.bigtangle.docker.IVmDeploy;
import net.bigtangle.docker.ResultExecution;
import net.bigtangle.docker.ShellExecute;
import net.bigtangle.docker.VM;
import net.bigtangle.docker.VmConfigfile;
import net.bigtangle.docker.Vmsystemportmap;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.shell.Hostparam;

/*
 * service to control the remote docker container via shell
 */
@Service
public class DockerService {

    private final Log LOG = LogFactory.getLog(getClass().getName());
    // default used registry for docker images
    public static final String Registry = "registry.dasimi.com";
 

    @Autowired
    private ServerConfiguration serverConfiguration;

    @Autowired
    private DBStoreConfiguration dbStoreConfiguration;

    

    /*
     * create a executable file on the vm and calculate the  sha256sum
     */
    public String mysqldumpCheck() { 
     String  re= " mysqldump -u " + dbStoreConfiguration.getUsername() + " -p" + dbStoreConfiguration.getPassword()
        + " --databases " + dbStoreConfiguration.getDbName() 
        + " >  /tmp/bigtangle-database.sql   \n";
       re += " sha256sum /tmp/bigtangle-database.sql " ;
       return re;
    }
    
    public String setupDB() {
        return " sudo docker exec " + serverConfiguration.getDockerDBHost() + " /bin/sh -c \" " + " mysql -u "
                + dbStoreConfiguration.getUsername() + " -p" + dbStoreConfiguration.getPassword() + " --databases "
                + dbStoreConfiguration.getDbName() + " < " + "/temp/" + "bigtangle-database.sql" + "\"";
    }

    public String docker(String command) {
        return " sudo docker exec " + serverConfiguration.getDockerDBHost() + " /bin/sh -c \" "
                + command.replace("\"", "\\\"") + " \"";
    }

    
    public String rbackup2(String path) throws Exception {
        return DockerHelper.readFile(path, Charset.forName("UTF-8"));

    }

    public String registry() {
        String r =  Registry ;
        if (r != null && !"".equals(r.trim())) {
            return r + "/";
        } else {
            return "";
        }
    }

    public String getImagename(VM v) {
        return registry() + v.name;
    }

    public String getContainername(VM v) {
        return v.name;
    }

    public void shutdown(VM v, IVmDeploy remoteVmDeploy) throws Exception {

        DockerHelper.shellExecute(" docker  stop " + v.name, remoteVmDeploy);
    }

    public void restart(VM v, IVmDeploy remoteVmDeploy) throws Exception {
        DockerHelper.shellExecute(" docker  restart " + getContainername(v), remoteVmDeploy);
    }

    public ResultExecution start(VM v, IVmDeploy remoteVmDeploy) throws Exception {
        return DockerHelper.shellExecute(" docker  start " + getContainername(v), remoteVmDeploy);
    }

    public void removeImage(VM v, IVmDeploy remoteVmDeploy, String version) throws Exception {
        DockerHelper.shellExecute(" docker  rmi " + getImagename(v) + ":" + version, remoteVmDeploy);
    }

    public ResultExecution dockerExec(VM v, IVmDeploy remoteVmDeploy, String command) throws Exception {
        return DockerHelper.shellExecute(
                " docker  exec   " + v.name + " /bin/sh -c \" " + command.replace("\"", "\\\"") + "\"",
                remoteVmDeploy);
    }

    public void removeRun(VM v, IVmDeploy remoteVmDeploy) throws Exception {
        DockerHelper.shellExecute(" docker  rm -f " + getContainername(v), remoteVmDeploy);
    }

    public void restore(VM vm, Hostparam h, IVmDeploy remoteVmDeploy, String version, String ip, String backupPort,
            String path) throws Exception {
        removeRun(vm, remoteVmDeploy);

        ShellExecute aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation("/tmp/restore-" + vm.name + ".sh");
        aShellExecute.setCmd(" chmod +x " + aShellExecute.getFilelocation());
        aShellExecute.setFileFromString(rbackup2(path));
        DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);

        String cmd = aShellExecute.getFilelocation() + "  /data/backup/" + h.publicIP + "/data/vm/" + vm.name + "/"
                + version + "/  " + ip + "  /data/vm/" + vm.name + " " + backupPort;
        LOG.info("cmd:" + cmd);
        List<ResultExecution> re = new ArrayList<ResultExecution>();
        DockerHelper.shellExecute(cmd, remoteVmDeploy);
        defineFromVM(vm, h, remoteVmDeploy, re, version);
        runDocker(vm, h, remoteVmDeploy, version, new ArrayList<ResultExecution>());
    }

    public void doShellExecute(IVmDeploy remoteVmDeploy, String cmd) throws Exception {
        LOG.info("cmd:" + cmd);
        DockerHelper.shellExecute(cmd, remoteVmDeploy);
    }

    public void doShellExecute(IVmDeploy remoteVmDeploy, List<String> cmds) throws Exception {
        LOG.info("cmd:" + cmds);
        DockerHelper.shellExecute(cmds, remoteVmDeploy);
    }

    /*
     * remove None images
     */
    public void removeNone(IVmDeploy remoteVmDeploy) throws Exception {
        List<String> cs = new ArrayList<String>();
        // cs.add(" docker pause " + getContainername(v));
        cs.add("docker rmi $(docker images | grep \"^<none>\" | awk '{print $3}')");
        // cs.add(" docker unpause " + getContainername(v));
        DockerHelper.shellExecute(cs, remoteVmDeploy);
    }

    /*
     * backup the docker images to repository using commit, default is pause
     * image and push into registry
     */
    public void backup(VM v, IVmDeploy remoteVmDeploy, String commit, String version, String user) throws Exception {
        List<String> cs = new ArrayList<String>();
        // cs.add(" docker pause " + getContainername(v));
        cs.add(commitString(v, remoteVmDeploy, commit, version, user));
        // cs.add(" docker unpause " + getContainername(v));
        DockerHelper.shellExecute(cs, remoteVmDeploy);
    }

    public void backupData(IVmDeploy remoteVmDeploy, String srcfile, String srcIp, String targetDir, String backupPort,
            String vmname, String path, String version) throws Exception {
        ShellExecute aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation("/tmp/rbackup2-" + vmname + ".sh");
        aShellExecute.setCmd(" chmod +x " + aShellExecute.getFilelocation());
        aShellExecute.setFileFromString(rbackup2(path));
        DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);

        List<String> cs = new ArrayList<String>();
        if (targetDir == null || "".equals(targetDir.trim())) {
            targetDir = "/data/backup";
        }
        String backupDataString = aShellExecute.getFilelocation() + " " + srcfile + " " + srcIp + " " + targetDir + " "
                + backupPort + " " + version + "  >> /root/backup.txt";
        cs.add(backupDataString);
        DockerHelper.shellExecute(cs, remoteVmDeploy);

    }

    public void push(VM v, IVmDeploy remoteVmDeploy, String version) throws Exception {
        List<String> cs = new ArrayList<String>();
        cs.add(" docker push " + getImagename(v) + ":" + version);
        DockerHelper.shellExecute(cs, remoteVmDeploy);
    }

    public String commitString(VM v, IVmDeploy remoteVmDeploy, String commit, String version, String user)
            throws Exception {
        return "docker  commit -m=\"" + commit + "\"" + " -a=\"" + user + "\" " + getContainername(v) + " "
                + getImagename(v) + ":" + version;
    }

    public void clone(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, String baseName, List<ResultExecution> re,
            String sshpublickey, String autostart) throws Exception {
        // setDockerVMProperties(null, aHostparam, remoteVmDeploy, null, v);
        cloneLocal(v, aHostparam, remoteVmDeploy, registry() + baseName, re, sshpublickey, autostart);
        deployConfig(v, remoteVmDeploy);
    }

    public void cloneLocal(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, String baseImageName,
            List<ResultExecution> re, String sshpublickey, String autostart) throws Exception {
        // problem of wait for each shell execute, use one execute of multi
        // commands

        List<String> cs = new ArrayList<String>();
        // use docker build of new image, for getting user data and config
        addFile(v, aHostparam, remoteVmDeploy, re, baseImageName, sshpublickey, autostart);
        // run it as new name and use commit to a image
        // cs.add(runDockerString(v, aHostparam, remoteVmDeploy, re, baseName));
        // cs.add(commitString(v, remoteVmDeploy, " copy from " + baseName,
        // ""));

        // run it from its own image
        cs.add(" docker  rm  -f " + v.name);
        cs.add(runDockerString(v, aHostparam, remoteVmDeploy, re, getImagename(v), baseImageName));

        DockerHelper.shellExecute(cs, remoteVmDeploy);

    }

    public void defineFromVM(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, List<ResultExecution> re,
            String version) throws Exception {

        re.add(DockerHelper.shellExecute(" docker pull " + getImagename(v) + ":" + version, remoteVmDeploy));

    }

    public void runDocker(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, String version,
            List<ResultExecution> re) throws Exception {
        runDocker(v, aHostparam, remoteVmDeploy, re, getImagename(v) + ":" + version);
    }

    public void runDocker(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, String image, String version,
            List<ResultExecution> re) throws Exception {
        runDocker(v, aHostparam, remoteVmDeploy, re, image + ":" + version);
    }

    public void runDocker(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, List<ResultExecution> re,
            String imagename) throws Exception {

        re.add(DockerHelper.shellExecute(runDockerString(v, aHostparam, remoteVmDeploy, re, imagename, ""),
                remoteVmDeploy));
        deployConfig(v, remoteVmDeploy);

    }

    public void addFile(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, List<ResultExecution> re,
            String baseImagename, String sshpublickey, String autostart) throws Exception {

        String tmpDir = "/tmp/" + v.name;
        DockerHelper.shellExecute(" mkdir -p " + tmpDir, remoteVmDeploy);

        ShellExecute aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation(tmpDir + "/authorized_keys");
        aShellExecute.setCmd(" cd " + tmpDir);
        aShellExecute.setFileFromString(sshpublickey);
        DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);

        aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation(tmpDir + "/autostart");
        aShellExecute.setCmd(" cd " + tmpDir);
        aShellExecute.setFileFromString(autostart);
        DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);

        aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation(tmpDir + "/Dockerfile");
        aShellExecute.setCmd(" cd " + tmpDir + " &&  " + " docker build -t " + getImagename(v) + "  . ");
        aShellExecute.setFileFromString(getDockerString(v, aHostparam, remoteVmDeploy, re, baseImagename));
        DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);

    }

    private String getDockerString(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, List<ResultExecution> re,
            String baseimagename) throws Exception {
        String dockerfile = "";
        dockerfile += " FROM  " + baseimagename + " \n ";
        dockerfile += " ADD autostart  /opt/java/autostart " + " \n ";
        dockerfile += " ADD authorized_keys /root/.ssh/authorized_keys   " + " \n ";
        dockerfile += "#To disable the root login use \n";
        dockerfile += " RUN   passwd -l root \n";
        return dockerfile;
    }

    public String runDockerString(VM v, Hostparam aHostparam, IVmDeploy remoteVmDeploy, List<ResultExecution> re,
            String imagename, String baseimagename) throws Exception {

        return " docker run " + " -h " + v.name + v.getNetworkName() + " -d " + " --name " + v.name
                + addVolume(v, aHostparam) + addPortMap(v, aHostparam) + addNetworkAndIP(v, aHostparam)
                + limitResource(v, aHostparam) + " " + " " + imagename;
    }

    public String addVolume(VM v, Hostparam aHostparam) {
        String re = "";
        for (String vol : v.getVolumns()) {
            re += " -v " + "/data/vm/" + v.name + "/" + vol + ":" + vol + " ";

        }

        return re;
    }

    public void deployConfig(VM v, IVmDeploy remoteVmDeploy) throws Exception {

        if (v.getListVmConfigfile() != null && !v.getListVmConfigfile().isEmpty()) {
            for (VmConfigfile vmConfigfile : v.getListVmConfigfile()) {
                deployConfig(vmConfigfile, v.name, remoteVmDeploy);

            }
        }

    }

    public ResultExecution deployConfig(VmConfigfile vmConfigfile, String vmname, IVmDeploy remoteVmDeploy)
            throws Exception {
        String tmpDir = "/tmp/" + vmname;
        ShellExecute aShellExecute = new ShellExecute();
        aShellExecute.setFilelocation(tmpDir + vmConfigfile.getFilename());
        String src = tmpDir + vmConfigfile.getFilename();
        aShellExecute.setCmd(" docker cp  " + src + " " + vmname + ":" + vmConfigfile.getFilename());
        aShellExecute.setFile(vmConfigfile.config);
        return DockerHelper.shellExecute(aShellExecute, remoteVmDeploy);
    }

    public String addNetworkAndIP(VM v, Hostparam aHostparam) {
        String re = "";
        if (v.getNetworkName() != null && v.getIp() != null) {

            re += " --net " + v.getNetworkName() + " --ip " + v.getIp();
        }

        return re;
    }

    // https://goldmann.pl/blog/2014/09/11/resource-management-in-docker/
    public String limitResource(VM v, Hostparam aHostparam) {
        String re = "";

        if (v.memoryMB != null) {
            Long g = v.memoryMB / 1024;

            re += " -m " + g + "g ";
        } else {
            re += " -m 4000m ";
        }

        return re;
    }

    public String addPortMap(VM v, Hostparam aHostparam) {
        String re = "";
        Vmsystemportmap aVmsystemportmap = new Vmsystemportmap();
        aVmsystemportmap.setHostname(aHostparam.hostname);
        aVmsystemportmap.setVmname(v.name);

        for (Vmsystemportmap vmsystemportmap : v.listVmsystemportmap) {
            re += " -p " + vmsystemportmap.getPublicport() + ":" + vmsystemportmap.getLocalport() + " ";
        }

        return re;
    }

}
