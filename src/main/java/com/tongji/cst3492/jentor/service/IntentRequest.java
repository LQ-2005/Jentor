package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.intent.RouteDecision;
import com.tongji.cst3492.jentor.llm.CancellationToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** Cancellable semantic-routing request. */
public final class IntentRequest {
    private final CompletableFuture<RouteDecision> future;
    private final Future<?> task;
    private final CancellationToken token;

    public IntentRequest(
            CompletableFuture<RouteDecision> future,
            Future<?> task,
            CancellationToken token) {
        this.future = future;
        this.task = task;
        this.token = token;
    }

    public static IntentRequest failed(Throwable error) {
        return new IntentRequest(
                CompletableFuture.failedFuture(error), null, new CancellationToken());
    }

    public CompletableFuture<RouteDecision> future() {
        return future;
    }

    public boolean isCancelled() {
        return token.isCancelled();
    }

    public void cancel() {
        token.cancel();
        if (task != null) {
            task.cancel(true);
        }
        future.cancel(false);
    }
}
