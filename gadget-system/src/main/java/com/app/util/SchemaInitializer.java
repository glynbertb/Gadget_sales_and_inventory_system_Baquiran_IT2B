package com.app.util;

import com.app.dao.DatabaseInitializer;

/**
 * Idempotent safety net: creates the six tables only when they are missing,
 * so the app also starts on a brand-new database.db. Existing tables are untouched.
 */
public final class SchemaInitializer {



    private SchemaInitializer() { }

    public static void ensureSchema() {
        DatabaseInitializer.initialize();
    }
}
