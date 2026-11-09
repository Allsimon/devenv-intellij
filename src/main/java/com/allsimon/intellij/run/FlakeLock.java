package com.allsimon.intellij.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where a flake.lock locks an input of the root flake, and at which revision. */
public final class FlakeLock {
    /** The input's node in the lock, its offset in the text, and the revision it is locked at, if any. */
    public record Entry(String node, int offset, @Nullable String rev) {}

    private FlakeLock() {
    }

    public static @Nullable Entry find(@NotNull String lock, @NotNull String input) {
        JsonObject nodes;
        String node;
        try {
            JsonObject root = JsonParser.parseString(lock).getAsJsonObject();
            nodes = root.getAsJsonObject("nodes");
            JsonObject inputs = nodes.getAsJsonObject(root.get("root").getAsString()).getAsJsonObject("inputs");
            JsonElement target = inputs == null ? null : inputs.get(input);
            if (target == null || !target.isJsonPrimitive()) {
                return null;
            }
            node = target.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
        int start = lock.indexOf("\"nodes\"");
        Matcher key = Pattern.compile("\"" + Pattern.quote(node) + "\"\\s*:\\s*\\{").matcher(lock);
        if (start < 0 || !key.find(start)) {
            return null;
        }
        JsonObject locked = nodes.has(node) ? nodes.getAsJsonObject(node).getAsJsonObject("locked") : null;
        JsonElement rev = locked == null ? null : locked.get("rev");
        return new Entry(node, key.start(), rev == null ? null : rev.getAsString());
    }
}
