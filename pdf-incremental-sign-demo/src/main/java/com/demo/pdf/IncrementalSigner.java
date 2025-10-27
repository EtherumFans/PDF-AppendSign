package com.demo.pdf;

/**
 * Backwards-compatible entry point that delegates to the Picocli-based CLI.
 */
public final class IncrementalSigner {

    private IncrementalSigner() {
    }

    public static void main(String[] args) {
        com.demo.App.main(args);
    }
}
