package com.simpragma.samplep2p;

import com.cloudant.sync.notifications.ReplicationCompleted;
import com.cloudant.sync.notifications.ReplicationErrored;
import com.cloudant.sync.replication.ErrorInfo;
import com.google.common.eventbus.Subscribe;

import java.util.concurrent.CountDownLatch;

/**
 * A {@code ReplicationListener} that sets a latch when it's told the
 * replication has finished.
 */
public class Listener {

    private final CountDownLatch latch;
    public ErrorInfo error = null;
    public int documentsReplicated;
    public int batchesReplicated;

    public Listener(CountDownLatch latch) {
        this.latch = latch;
    }

    @Subscribe
    public void complete(ReplicationCompleted event) {
        this.documentsReplicated = event.documentsReplicated;
        this.batchesReplicated = event.batchesReplicated;
        latch.countDown();
    }

    @Subscribe
    public void error(ReplicationErrored event) {
        this.error = event.errorInfo;
        latch.countDown();
    }
}