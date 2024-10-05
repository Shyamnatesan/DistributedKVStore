package com.shyamnatesan.server;

import java.rmi.Remote;

public interface PeerConnectionInterface extends Remote {
    VoteGranted voteRequested(VoteRequest voteRequest);
    void heartbeatReceived(AppendEntries appendEntries);
}
