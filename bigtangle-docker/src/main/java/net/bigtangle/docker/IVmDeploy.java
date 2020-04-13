package net.bigtangle.docker;

public interface IVmDeploy {
    /*
     * execute remote command and wait the result as output
     */
    public abstract String shellExecute(String remoteCommand) throws Exception;

    public abstract String shellExecute(ShellExecute aShellExecute) throws Exception;

}
