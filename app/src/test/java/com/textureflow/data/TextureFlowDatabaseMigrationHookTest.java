package com.textureflow.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class TextureFlowDatabaseMigrationHookTest {
    @Test
    public void schemaVersionStaysAtOneUntilStreamC() throws Exception {
        Field version = TextureFlowDatabase.class.getDeclaredField("DATABASE_VERSION");
        version.setAccessible(true);
        assertEquals(1, version.getInt(null));
    }

    @Test
    public void upgradeChainReservesMigrateTo2() throws Exception {
        Method migrate = TextureFlowDatabase.class.getDeclaredMethod("migrateTo2", SQLiteDatabase.class);
        assertNotNull(migrate);
        assertEquals(void.class, migrate.getReturnType());
        assertTrueStatic(migrate);
    }

    private static void assertTrueStatic(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new AssertionError("migrateTo2 must be static so onUpgrade can call it without an instance");
        }
    }
}
