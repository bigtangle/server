package net.bigtangle.docker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.bigtangle.shell.Commandline;

public class VmDeployImpl implements IVmDeploy {
    private final Log LOG = LogFactory.getLog(getClass().getName());

    public String shellExecute(String command) throws Exception {
        // using the command
        LOG.debug("shellExecute = " + command);

        return shellExecuteDo(command);
    }

    public String shellExecuteDo(String command) throws Exception {
        // using the command
        LOG.debug("Commandline cmd " + command);
      
        Commandline cmd = new Commandline();
        cmd.setExecutable(command);
        StringWriter swriter = new StringWriter();
        Process process = cmd.execute();
        process.waitFor();
        Reader reader = new InputStreamReader(process.getInputStream());

        char[] chars = new char[16];
        int read = -1;
        while ((read = reader.read(chars)) > -1) {
            swriter.write(chars, 0, read);
        }
        LOG.debug("Commandline cmd output " + swriter.toString().trim());
        LOG.debug("Finish Commandline cmd " + command);

        return swriter.toString().trim();
    }
    
    public String shellExecuteDo1(String command) throws Exception {
        StringBuffer output = new StringBuffer();
        Process p;

        p = Runtime.getRuntime().exec(command);
        p.waitFor();
        String line = "";
        if (p.exitValue() == 0) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            return output.toString();
        } else {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                output.append(line + "\n");
            }
            errorReader.close();
            throw new RuntimeException(output.toString());
        }

    }

    public String shellExecute(ShellExecute aShellExecute) throws Exception {
        LOG.debug(" create file at filelocation = " + aShellExecute.getFilelocation());

        File f = new File(aShellExecute.getFilelocation());
        f.delete();
        f.getParentFile().mkdirs();
        // write it to filelocation
        FileOutputStream stream = new FileOutputStream(f);
        try {
            stream.write(aShellExecute.getFile());
        } finally {
            stream.close();
        }
        LOG.debug(" Finish create file  at filelocation");

        return shellExecute(aShellExecute.getCmd());
    }

   

}
