package com.allsimon.intellij;

import com.intellij.DynamicBundle;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.PropertyKey;

public final class MyMessageBundle {
    private static final String BUNDLE = "messages.MyMessageBundle";

    private static final DynamicBundle INSTANCE = new DynamicBundle(MyMessageBundle.class, BUNDLE);

    private MyMessageBundle() {
    }

    public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static Supplier<@Nls String> lazyMessage(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
