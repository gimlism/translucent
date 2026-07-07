package com.gimlism.translucent.hashmap.repl;

/**
 * The outcome of one command: the {@code message} to show the user, and whether the REPL should
 * {@code quit}. Returned by {@link MapCommandInterpreter#execute}; the front-end (terminal REPL
 * now, browser POST in a later slice) decides how to present the message.
 */
public record CommandResult(String message, boolean quit) {

    /** A normal result — show {@code message}, keep going. */
    public static CommandResult of(String message) {
        return new CommandResult(message, false);
    }

    /** A terminating result — show {@code message}, then quit. */
    public static CommandResult quitting(String message) {
        return new CommandResult(message, true);
    }
}
