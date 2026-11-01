package com.campuspulse.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** ৪টা thread একই counter বাড়ায়। synchronized ছাড়া সংখ্যা কম আসে (race condition)। */
public class ThreadDemo {

    private static int unsafeCounter = 0;
    private static int safeCounter = 0;
    private static final Object LOCK = new Object();

    /** Main thread এ ডেকো না, এটা অপেক্ষা করে। */
    public static String run() throws InterruptedException {
        unsafeCounter = 0;
        safeCounter = 0;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.execute(() -> {
                for (int i = 0; i < 50_000; i++) {
                    unsafeCounter++;                  // read-modify-write, নিরাপদ নয়
                    synchronized (LOCK) { safeCounter++; }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        return "expected 200000 | without sync = " + unsafeCounter + " | with synchronized = " + safeCounter;
    }
}