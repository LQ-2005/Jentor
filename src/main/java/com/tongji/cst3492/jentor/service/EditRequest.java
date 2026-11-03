package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.llm.EditProposalParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** Cancellable background request for one AI code-edit proposal. */
public final class EditRequest {
    private final CompletableFuture<EditProposalParser.Result> future;
    private final Future<?> task;
    private final CancellationToken token;

    public EditRequest(
            CompletableFuture<EditProposalParser.Result> future,
            Future<?> task,
            CancellationToken token) {
        this.future = future;
        this.task = task;
        this.token = token;
    }

    public static EditRequest failed(Throwable error) {
        return new EditRequest(
                CompletableFuture.failedFuture(error), null, new CancellationToken());
    }

    public CompletableFuture<EditProposalParser.Result> future() {
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
