package com.videomontage.core;

import java.util.UUID;

/** Short, stable ids for domain objects. Plain strings — the JNI boundary
 *  prefers primitive values, and wrapper classes buy nothing here. */
public final class Ids {
    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 13);
    }
}
