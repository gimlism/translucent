package com.gimlism.translucent.substrate.repl;

/**
 * The outcome of one REPL command: the {@code message} to show the user, and whether the REPL
 * should {@code quit}. The shared result type for every teaching structure's command interpreter
 * (HashMap, ArrayList, Trie); the front-end (terminal REPL now, browser POST in a later slice)
 * decides how to present the message.
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
