package com.allsimon.intellij.nixlang;

import org.affogato.parser.Bindings;

public final class NixExpandBindingIntention extends NixBindingIntention {
    public NixExpandBindingIntention() {
        super("Expand into a nested set", Bindings::expand);
    }
}
