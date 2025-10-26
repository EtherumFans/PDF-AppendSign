package com.demo.pdf;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Computes human-readable differences between two {@link PdfStructureDump} instances.
 */
public final class PdfStructureDiff {

    private PdfStructureDiff() {
    }

    public static DiffResult diff(PdfStructureDump golden, PdfStructureDump test) {
        Objects.requireNonNull(golden, "golden");
        Objects.requireNonNull(test, "test");
        SortedSet<String> goldFacts = new TreeSet<>(golden.getFacts());
        SortedSet<String> testFacts = new TreeSet<>(test.getFacts());
        SortedSet<String> onlyInGold = new TreeSet<>(goldFacts);
        onlyInGold.removeAll(testFacts);
        SortedSet<String> onlyInTest = new TreeSet<>(testFacts);
        onlyInTest.removeAll(goldFacts);
        return new DiffResult(onlyInGold, onlyInTest, new ArrayList<>(test.getAcrobatBlockers()));
    }

    public static boolean hasAcrobatBlockers(PdfStructureDump dump) {
        return dump != null && !dump.getAcrobatBlockers().isEmpty();
    }

    public static final class DiffResult {
        private final SortedSet<String> onlyInGold;
        private final SortedSet<String> onlyInTest;
        private final List<String> blockers;

        private DiffResult(SortedSet<String> onlyInGold, SortedSet<String> onlyInTest, List<String> blockers) {
            this.onlyInGold = onlyInGold;
            this.onlyInTest = onlyInTest;
            this.blockers = blockers;
        }

        public SortedSet<String> getOnlyInGold() {
            return Collections.unmodifiableSortedSet(onlyInGold);
        }

        public SortedSet<String> getOnlyInTest() {
            return Collections.unmodifiableSortedSet(onlyInTest);
        }

        public List<String> getBlockers() {
            return Collections.unmodifiableList(blockers);
        }

        public void printTo(PrintStream out) {
            Objects.requireNonNull(out, "out");
            out.println("== Structure Diff ==");
            if (onlyInGold.isEmpty() && onlyInTest.isEmpty()) {
                out.println("No structural differences between golden and test dumps.");
            } else {
                if (!onlyInGold.isEmpty()) {
                    out.println("-- Present only in golden reference --");
                    for (String fact : onlyInGold) {
                        out.println("  - " + fact);
                    }
                }
                if (!onlyInTest.isEmpty()) {
                    out.println("-- Present only in test PDF --");
                    for (String fact : onlyInTest) {
                        out.println("  + " + fact);
                    }
                }
            }
            if (!blockers.isEmpty()) {
                out.println("== Acrobat blockers detected in test PDF ==");
                for (String blocker : blockers) {
                    out.println("  * " + blocker);
                }
            } else {
                out.println("No Acrobat blockers detected in test PDF.");
            }
        }
    }
}
