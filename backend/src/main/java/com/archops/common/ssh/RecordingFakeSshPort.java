package com.archops.common.ssh;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default SSH adapter for CI / acceptance: records calls and returns scripted success/failure.
 */
@Component
@ConditionalOnProperty(name = "archops.ssh.mode", havingValue = "fake", matchIfMissing = true)
public class RecordingFakeSshPort implements ControlledSshPort {

    private final CopyOnWriteArrayList<SshCallRecord> calls = new CopyOnWriteArrayList<>();
    private final Set<String> failActions = ConcurrentHashMap.newKeySet();
    private final AtomicReference<CountDownLatch> blockLatch = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> enteredLatch = new AtomicReference<>();

    @Override
    public SshExecResult exec(SshExecRequest request) {
        CountDownLatch entered = enteredLatch.get();
        if (entered != null) {
            entered.countDown();
        }
        CountDownLatch block = blockLatch.get();
        if (block != null) {
            try {
                block.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return SshExecResult.fail("interrupted while blocked");
            }
        }

        boolean fail = failActions.contains(request.action());
        SshExecResult result = fail
                ? SshExecResult.fail("fake-ssh failure for action " + request.action())
                : SshExecResult.ok("fake-ok " + request.action());
        calls.add(new SshCallRecord(
                Instant.now(),
                request.hostId(),
                request.command(),
                request.action(),
                request.stepSeq(),
                request.context() == null ? Map.of() : Map.copyOf(request.context()),
                result.success(),
                result.exitCode(),
                result.failureReason()
        ));
        return result;
    }

    public List<SshCallRecord> recordedCalls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
        failActions.clear();
        blockLatch.set(null);
        enteredLatch.set(null);
    }

    public void failOnAction(String action) {
        failActions.add(action);
    }

    /**
     * Block the next (and subsequent) exec until {@link #releaseBlock()} — for Redis/in-memory lock tests.
     */
    public void armBlock(CountDownLatch entered, CountDownLatch release) {
        this.enteredLatch.set(entered);
        this.blockLatch.set(release);
    }

    public void releaseBlock() {
        CountDownLatch latch = blockLatch.getAndSet(null);
        if (latch != null) {
            while (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    public List<String> recordedCommands() {
        List<String> out = new ArrayList<>();
        for (SshCallRecord call : calls) {
            out.add(call.command());
        }
        return out;
    }
}
