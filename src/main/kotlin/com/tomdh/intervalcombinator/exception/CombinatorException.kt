package com.tomdh.intervalcombinator.exception

/**
 * Base exception for all errors originating from the interval-combinator library.
 *
 * @param message A human-readable description of the error.
 * @param cause An optional underlying cause.
 */
public open class CombinatorException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when the solver configuration is invalid and cannot produce results.
 *
 * Examples include providing empty groups or conflicting time bounds.
 */
public class InvalidConfigurationException(
    message: String,
    cause: Throwable? = null
) : CombinatorException(message, cause)
