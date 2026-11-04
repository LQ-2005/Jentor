package com.tongji.cst3492.jentor.service;

import com.tongji.cst3492.jentor.intent.RouteDecision;
import com.tongji.cst3492.jentor.llm.CancellationToken;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentRequestTest {
    @Test
    void cancellationCancelsTheTokenTaskAndFuture() {
        CompletableFuture<RouteDecision> result = new CompletableFuture<>();
        CompletableFuture<Void> task = new CompletableFuture<>();
        CancellationToken token = new CancellationToken();
        IntentRequest request = new IntentRequest(result, task, token);

        request.cancel();

        assertTrue(token.isCancelled());
        assertTrue(task.isCancelled());
        assertTrue(result.isCancelled());
        assertTrue(request.isCancelled());
    }
}
