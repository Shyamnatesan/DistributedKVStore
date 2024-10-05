package com.shyamnatesan.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


@AllArgsConstructor
@Getter
@Setter
public class AppendEntries implements Serializable {
    private int leaderId;
    private int leaderTerm;
    private byte[] entries;
}
