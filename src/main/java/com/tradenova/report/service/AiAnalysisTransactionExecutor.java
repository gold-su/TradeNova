package com.tradenova.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Explicit proxy boundary for the short database phases around an external AI call.
 * The caller must invoke the LLM only after readOnly has returned.
 */
@Service
public class AiAnalysisTransactionExecutor {

    @Transactional(readOnly = true)
    public <T> T readOnly(Supplier<T> work) {
        return work.get();
    }

    @Transactional
    public <T> T write(Supplier<T> work) {
        return work.get();
    }
}
