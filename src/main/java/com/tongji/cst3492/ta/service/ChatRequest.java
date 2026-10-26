package com.tongji.cst3492.ta.service;

import com.tongji.cst3492.ta.llm.Answer;
import com.tongji.cst3492.ta.llm.CancellationToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class ChatRequest {
    private final CompletableFuture<Answer> future;
    private final Future<?> task;
    private final CancellationToken token;

    public ChatRequest(
            CompletableFuture<Answer> future,
            Future<?> task,
            CancellationToken token) {
        this.future = future;
        this.task = task;
        this.token = token;
    }

    public static ChatRequest failed(Throwable error) {
        CancellationToken token = new CancellationToken();
        return new ChatRequest(CompletableFuture.failedFuture(error), null, token);
    }

    public CompletableFuture<Answer> future() {
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
