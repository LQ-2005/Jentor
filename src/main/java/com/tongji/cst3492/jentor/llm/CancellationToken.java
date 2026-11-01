package com.tongji.cst3492.jentor.llm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (RuntimeException ignored) {
                    // Cancellation is best-effort; one failing callback must not block others.
                }
            }
            callbacks.clear();
        }
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Generation cancelled");
        }
    }

    public void onCancel(Runnable callback) {
        if (isCancelled()) {
            callback.run();
            return;
        }
        callbacks.add(callback);
        if (isCancelled() && callbacks.remove(callback)) {
            callback.run();
        }
    }
}
