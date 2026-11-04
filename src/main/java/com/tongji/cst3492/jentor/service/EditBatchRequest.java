package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.model.EditBatchResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** Cancellable request that plans and generates all proposals for one edit request. */
public final class EditBatchRequest {
    private final CompletableFuture<EditBatchResult> future;
    private final Future<?> task;
    private final CancellationToken token;

    public EditBatchRequest(
            CompletableFuture<EditBatchResult> future,
            Future<?> task,
            CancellationToken token) {
        this.future = future;
        this.task = task;
        this.token = token;
    }

    public static EditBatchRequest failed(Throwable error) {
        return new EditBatchRequest(
                CompletableFuture.failedFuture(error), null, new CancellationToken());
    }

    public CompletableFuture<EditBatchResult> future() {
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
