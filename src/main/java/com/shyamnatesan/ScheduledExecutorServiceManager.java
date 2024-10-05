package com.shyamnatesan;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class ScheduledExecutorServiceManager {
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public static ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public static void shutdown() {
        scheduledExecutorService.shutdown();
    }
}
