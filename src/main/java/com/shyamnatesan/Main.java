package com.shyamnatesan;

import com.shyamnatesan.server.Server;
import picocli.CommandLine;
import java.rmi.RemoteException;

public class Main {
    /**
     *  1. PARSE THE CLI ARGS INTO THE SERVER CLASS
     *  2. START THE TCP SERVER ON A SEPARATE THREAD
     *  3. START THE REGISTRY AND CONNECT WITH PEER REGISTRIES
     **/
    public static void main(String[] args) {
        Server server = new Server();
        CommandLine cmd = new CommandLine(server);
        if (args.length == 0) {
            cmd.usage(System.out);
            return;
        }
        cmd.parseArgs(args);

        ExecutorServiceManager.getExecutorService().submit(server::startTCP);

        try {
            server.startNodeRegistry();
            server.connectWithPeers();
        }catch (RemoteException remoteException) {
            System.err.println("Error stating RMI server: " + remoteException.getMessage());
            throw new RuntimeException(remoteException);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down...");
            ExecutorServiceManager.shutDown();
        }));
    }
}