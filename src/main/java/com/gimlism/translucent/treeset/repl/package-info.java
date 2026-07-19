/**
 * The TreeSet's terminal REPL command layer: a pure interpreter that parses one command line and
 * applies it to a {@link com.gimlism.translucent.treeset.core.TeachingTreeSet}, returning a shared
 * {@link com.gimlism.translucent.substrate.repl.CommandResult}. Server-agnostic — the browser
 * controls slice reuses the same {@code execute} entry point.
 */
package com.gimlism.translucent.treeset.repl;
