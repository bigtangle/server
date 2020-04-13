package net.bigtangle.docker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

public class DockerHelper {

    public static void fillStackTrace(Throwable ex, PrintWriter pw) {
        if (null == ex) {
            return;
        }

        ex.printStackTrace(pw);

        if (ex instanceof ServletException) {
            Throwable cause = ((ServletException) ex).getRootCause();

            if (null != cause) {
                pw.println("Root Cause:");
                fillStackTrace(cause, pw);
            }
        } else {
            Throwable cause = ex.getCause();

            if (null != cause) {
                pw.println("Cause:");
                fillStackTrace(cause, pw);
            }
        }
    }

    public static List<ResultExecution> shellExecute(List<String> commands, IVmDeploy remoteVmDeploy) throws Exception {
        List<ResultExecution> re = new ArrayList<ResultExecution>();
        for (String command : commands) {
            re.add(shellExecute(command, remoteVmDeploy));
        }
        return re;
    }

    public static ResultExecution shellExecute(String command, IVmDeploy remoteVmDeploy) throws Exception {
        ResultExecution aResultExcecution = new ResultExecution();
        aResultExcecution.setCommandtext(command);
        aResultExcecution.setResult(remoteVmDeploy.shellExecute(command));

        aResultExcecution.checkError();
        return aResultExcecution;
    }

    public static ResultExecution shellExecute(ShellExecute aShellExecute, IVmDeploy remoteVmDeploy) throws Exception {
        ResultExecution aResultExcecution = new ResultExecution();
        aResultExcecution.setCommandtext(aShellExecute.getCmd());
        aResultExcecution.setResult(remoteVmDeploy.shellExecute(aShellExecute));
        // LOG.debug(aResultExcecution.toString());
        aResultExcecution.checkError();
        return aResultExcecution;

    }

    /*
     * remote copy of lv, check the result with md5 and rise a exception for
     * difference
     */
    public static void checkMd5sum(String m1, String m2) throws Exception {

    }

    public static String shellExecuteLocal(List<String> remoteCommandList) throws Exception {
        StringWriter re = new StringWriter();
        for (String s : remoteCommandList) {
            re.append(shellExecuteLocal(s));
        }
        return re.toString();
    }

    public static String shellExecuteLocal(String remoteCommand) throws Exception {

        StringBuffer output = new StringBuffer();
        Process p;

        p = Runtime.getRuntime().exec(remoteCommand);
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

    public static String shellExecuteLocal(ShellExecute aShellExecute) throws Exception {
        // LOG.debug(" create file at filelocation"
        // + aShellExecute.getFilelocation());

        // write it to filelocation
        FileOutputStream out = new FileOutputStream(new File(aShellExecute.getFilelocation()));
        try {
            out.write(aShellExecute.getFile());
        } finally {
            out.close();
        }
        // LOG.debug(" Finish create file at filelocation");

        return shellExecuteLocal(aShellExecute.getCmd());
    }

    public static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return encoding.decode(ByteBuffer.wrap(encoded)).toString();
    }

    public static String getStackTrace(Throwable ex) {

        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        DockerHelper.fillStackTrace(ex, pw);

        return writer.toString();
    }

}
