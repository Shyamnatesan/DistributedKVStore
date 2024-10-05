package com.shyamnatesan.server;

import com.shyamnatesan.ExecutorServiceManager;
import com.shyamnatesan.ScheduledExecutorServiceManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Raft extends UnicastRemoteObject implements PeerConnectionInterface {

    private final int ELECTION_TIMEOUT_MIN = 150;
    private final int HEARTBEAT_INTERVAL = 75;

    private NodeState nodeState = NodeState.FOLLOWER;
    private int serverId;
    private int term;
    private final AtomicInteger votesReceived = new AtomicInteger(0);
    private int votedFor = -1; // -1 means the node hasn't voted yet in the current term
    private Duration electionTimeout;
    private Instant lastHeartbeatTime = Instant.now();
    private final Map<String, PeerConnectionInterface> peerConnections = new HashMap<>();

    @Override
    public VoteGranted voteRequested(VoteRequest voteRequest) {
        int candidateID = voteRequest.getCandidateId();
        int candidateTerm = voteRequest.getCandidateTerm();
        VoteGranted response;

        // If the candidate's term is less than this node's term, reject the vote
        if (candidateTerm < this.term) {
            System.out.println("Vote denied for candidate " + candidateID + " due to outdated term");
            response = new VoteGranted(false, this.term);
        }else {
            // If candidate's term is higher, update this node's term and reset votedFor
            if (candidateTerm > this.term) {
                this.term = candidateTerm;
                this.votedFor = -1;
                this.nodeState = NodeState.FOLLOWER;
            }
            // Check if this node has already voted for someone else in this term
            if (this.votedFor == -1 || this.votedFor == candidateID) {
                // Grant the vote
                this.votedFor = candidateID;
                System.out.println("Vote granted for candidate " + candidateID);
                response = new VoteGranted(true, this.term);
            }else {
                System.out.println("Vote denied for candidate " + candidateID + "(already voted for " + this.votedFor + ")");
                response = new VoteGranted(false, this.term);
            }
        }
        // Reset election timeout to avoid premature elections
        resetElectionTimeout();
        return response;

    }

    public void resetElectionTimeout() {
        // Update the lastHeartbeatTime to the current instant
        this.lastHeartbeatTime = Instant.now();
        System.out.println("Election timeout reset at " + this.lastHeartbeatTime);
    }

    @Override
    public void heartbeatReceived(AppendEntries appendEntries) {
        int leaderId = appendEntries.getLeaderId();
        int leaderTerm = appendEntries.getLeaderTerm();

        // If the leader's term is lower, ignore the heartbeat
        if (leaderTerm < this.term) {
            System.out.println("Heartbeat from leader " + leaderId + " ignored due to outdated term");
            return;
        }
        // If the leader's term is higher, update this node's term and follow the leader
        if (leaderTerm > this.term) {
            this.term = leaderTerm;
            this.nodeState = NodeState.FOLLOWER;
            this.votedFor = -1;
        }
        // Reset election timeout because we received a valid heartbeat
        resetElectionTimeout();
        System.out.println("Heartbeat received from leader " + leaderId + ", term " + leaderTerm);
    }

    public void populatePeerConnections(String peer, PeerConnectionInterface peerInterface) {
        this.peerConnections.put(peer, peerInterface);
    }


    public void startElectionTimeout() {
        ExecutorServiceManager.getExecutorService().submit(() -> {
            while (true) {
                try {
                    // Random between 150-300 ms
                    long timeout = ELECTION_TIMEOUT_MIN + ThreadLocalRandom.current().nextInt(0, ELECTION_TIMEOUT_MIN);
                    Thread.sleep(timeout);

                    if (Duration.between(this.lastHeartbeatTime, Instant.now()).toMillis() > timeout) {
                        System.out.println("Election timed out!!! Starting election...");
                        startElection();
                    }
                }catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Election timeout interrupted: " + e.getMessage());
                }
            }
        });
    }

    private void startElection() {
        this.nodeState = NodeState.CANDIDATE;
        this.term++;
        this.votedFor = this.serverId;

        System.out.println("Node " + this.serverId + " started an election for term " + this.term);
        votesReceived.set(1); // set to 1 for self vote

        for (Map.Entry<String, PeerConnectionInterface> peerEntry : this.peerConnections.entrySet()) {
            ExecutorServiceManager.getExecutorService().submit(() -> {
                try {
                    PeerConnectionInterface peer = peerEntry.getValue();
                    VoteRequest voteRequest = new VoteRequest(this.serverId, this.term);
                    VoteGranted voteGranted = peer.voteRequested(voteRequest);

                    if (voteGranted.isVoteGranted()) {
                        int currentVotes = votesReceived.incrementAndGet();
                        System.out.println("Node " + this.serverId + " received a vote. Total votes: " + currentVotes);

                        if (hasMajorityVotes()) {
                            becomeLeader();
                        }
                    }
                }catch (Exception e) {
                    System.err.println("Error requesting vote from " + peerEntry.getKey() + ": " + e.getMessage());
                }
            });
        }

        ExecutorServiceManager.getExecutorService().submit(() -> {
           try {
               Thread.sleep(300);
               if (!hasMajorityVotes()) {
                   this.nodeState = NodeState.FOLLOWER;
                   System.out.println("Node " + this.serverId + " failed to become leader for term " + this.term);
                   this.votedFor = -1;
                   resetElectionTimeout();
               }
           }catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               System.err.println("Election failure handling interrupted: " + e.getMessage());
           }
        });
    }

    private void becomeLeader() {
        this.nodeState = NodeState.LEADER;
        System.out.println("Node " + this.serverId + " has become the leader for term " + this.term);
        startSendingHeartbeats();
    }

    private boolean hasMajorityVotes() {
        // Calculate majority (total nodes / 2)
        int totalNodes = peerConnections.size() + 1; // +1 for self
        return this.votesReceived.get() > (totalNodes / 2);
    }

    private void startSendingHeartbeats() {
        ScheduledExecutorServiceManager
                .getScheduledExecutorService()
                .scheduleAtFixedRate(() -> {
                    for (Map.Entry<String, PeerConnectionInterface> peer : this.peerConnections.entrySet()) {
                        PeerConnectionInterface peerConnectionInterface = peer.getValue();
                        ExecutorServiceManager.getExecutorService().submit(() -> {
                            AppendEntries appendEntries = new AppendEntries(this.serverId, this.term, new byte[0]);
                            peerConnectionInterface.heartbeatReceived(appendEntries);
                        });
                    }
                }, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
}