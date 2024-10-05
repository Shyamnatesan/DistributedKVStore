package com.shyamnatesan.server;

import com.shyamnatesan.ExecutorServiceManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import picocli.CommandLine;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Server {
    @CommandLine.Option(names = "--node-id", required = true, description = "current node id")
    private int serverId;

    @CommandLine.Option(names = "-h", required = true, description = "current node host")
    private String host;

    @CommandLine.Option(names = "-p", required = true, description = "current node port")
    private int port;

    @CommandLine.Option(names = "--peers", split = ",", description = "peer addresses")
    private String[] peerAddresses;

    private Raft raft;


    public void startTCP() {
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Starting server at " + this.host + ":" + this.port + "...");
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Accepted connection from " +
                            clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    ExecutorServiceManager.getExecutorService().submit(() -> handleClientConnection(clientSocket));
                }catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            throw new RuntimeException("Failed to start TCP server", e);
        }
    }


    public void startNodeRegistry() throws RemoteException {
        Registry registry = LocateRegistry.createRegistry(this.port + 1010);
        this.raft = new Raft();
        this.raft.setServerId(this.serverId);
        registry.rebind("Peer", this.raft);
        System.out.println("RMI server started and waiting for connections...");
    }

    // connect the registries
    public void connectWithPeers() {
        for (String peerAddress : this.peerAddresses) {
            String[] peerHostAndPort = peerAddress.split(":");
            String peerHost = peerHostAndPort[0];
            int peerPort = Integer.parseInt(peerHostAndPort[1]);
            int attempts = 0;
            boolean connected = false;

            while (!connected && attempts < 5) {
                try {
                    // Attempt to connect to the registry
                    Registry registry = LocateRegistry.getRegistry(peerHost, peerPort);
                    PeerConnectionInterface peerInterface = (PeerConnectionInterface) registry.lookup("Peer");
                    this.raft.populatePeerConnections(peerAddress, peerInterface);
                    System.out.println("Connected to peer at " + peerAddress);
                    connected = true; // Set connected to true on successful connection
                }catch (Exception e) {
                    attempts++;
                    long waitTime = (long) Math.pow(2, attempts) * 100; // Exponential backoff
                    System.err.printf("Error connecting to peer at %s (attempt %d): %s%n", peerAddress, attempts, e.getMessage());
                    if (attempts < 5) {
                        try {
                            Thread.sleep(waitTime); // Wait before retrying
                        }catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread was interrupted during backoff wait.");
                        }
                    }else{
                        System.err.println("Max connection attempts reached for peer at " + peerAddress);
                    }

                }
            }

            // If connection is not established after all attempts, throw an exception
            if (!connected) {
                throw new RuntimeException("Failed to connect to peer at " + peerAddress + " after 5 attempts.");
            }
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        System.out.println("handling connection from " +
                clientSocket.getInetAddress() + ":" + clientSocket.getPort());
    }

}
