package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.model.CodeContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class ChatRequest {
    private final CompletableFuture<Answer> future;
    private final CompletableFuture<CodeContext> effectiveContext;
    private final Future<?> task;
    private final CancellationToken token;

    public ChatRequest(
            CompletableFuture<Answer> future,
            Future<?> task,
            CancellationToken token) {
        this(future, task, token, CompletableFuture.completedFuture(CodeContext.empty()));
    }

    public ChatRequest(
            CompletableFuture<Answer> future,
            Future<?> task,
            CancellationToken token,
            CompletableFuture<CodeContext> effectiveContext) {
        this.future = future;
        this.task = task;
        this.token = token;
        this.effectiveContext = effectiveContext;
    }

    public static ChatRequest failed(Throwable error) {
        CancellationToken token = new CancellationToken();
        return new ChatRequest(CompletableFuture.failedFuture(error), null, token);
    }

    public CompletableFuture<Answer> future() {
        return future;
    }

    public CompletableFuture<CodeContext> effectiveContext() {
        return effectiveContext;
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
        effectiveContext.cancel(false);
    }
}
