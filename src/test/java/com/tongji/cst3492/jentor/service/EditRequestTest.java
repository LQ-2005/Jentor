package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.llm.EditProposalParser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditRequestTest {
    @Test
    void cancellationCancelsTheTokenTaskAndFuture() {
        CompletableFuture<EditProposalParser.Result> result = new CompletableFuture<>();
        CompletableFuture<Void> task = new CompletableFuture<>();
        CancellationToken token = new CancellationToken();
        EditRequest request = new EditRequest(result, task, token);

        request.cancel();

        assertTrue(token.isCancelled());
        assertTrue(task.isCancelled());
        assertTrue(result.isCancelled());
        assertTrue(request.isCancelled());
    }
}
