package com.tongji.cst3492.jentor.llm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationTokenTest {
    @Test
    void invokesEachCallbackOnceEvenWhenCancelIsRepeated() {
        CancellationToken token = new CancellationToken();
        AtomicInteger invocations = new AtomicInteger();

        token.onCancel(invocations::incrementAndGet);
        token.cancel();
        token.cancel();

        assertTrue(token.isCancelled());
        assertEquals(1, invocations.get());
    }

    @Test
    void invokesCallbacksRegisteredAfterCancellationImmediately() {
        CancellationToken token = new CancellationToken();
        AtomicInteger invocations = new AtomicInteger();
        token.cancel();

        token.onCancel(invocations::incrementAndGet);

        assertEquals(1, invocations.get());
    }
}
