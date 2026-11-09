package com.allsimon.intellij.nixlang;

import org.affogato.parser.Bindings;

public final class NixCollapseBindingIntention extends NixBindingIntention {
    public NixCollapseBindingIntention() {
        super("Collapse into an attribute path", Bindings::collapse);
    }
}
