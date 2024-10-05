package com.shyamnatesan.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@AllArgsConstructor
@Getter
@Setter
public class VoteRequest implements Serializable {
    private int candidateId;
    private int candidateTerm;
}
