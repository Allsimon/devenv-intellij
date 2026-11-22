package com.allsimon.intellij.nixlang;

/** Refreshes the hashes of the package around the caret at the version it has, or the commit it is pinned to. */
public final class NixRefreshHashesIntention extends NixUpdatePackageIntention {
    public NixRefreshHashesIntention() {
        super(true);
    }
}
