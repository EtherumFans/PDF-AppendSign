package com.demo.pdf;

/**
 * Backwards-compatible entry point that delegates to the current CLI implementation.
 *
 * <p>Older documentation referenced {@code com.demo.pdf.IncrementalSigner} as the
 * main class. The project now exposes a richer Picocli-based command-line interface
 * via {@link com.demo.App}, so this wrapper simply forwards the invocation.
 */
public final class IncrementalSigner {

    private IncrementalSigner() {
        // utility class
    }

    public static void main(String[] args) {
        com.demo.App.main(args);
    }
}
