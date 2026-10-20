package com.tradenova.beta;

public record BetaAccessResponse(
        boolean publicBetaEnabled,
        boolean accessAllowed
) {}
