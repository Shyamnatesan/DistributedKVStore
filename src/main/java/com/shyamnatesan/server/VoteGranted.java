package com.shyamnatesan.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@AllArgsConstructor
@Getter
@Setter
public class VoteGranted implements Serializable {
    private boolean voteGranted;
    private int updatedTerm;
}