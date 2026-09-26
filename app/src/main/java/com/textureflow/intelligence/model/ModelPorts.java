package com.textureflow.intelligence.model;

import android.content.Context;

import java.io.File;
import java.lang.reflect.Constructor;

/**
 * Production wiring: MediaPipe when the debug port and a model file are present,
 * otherwise {@link NoModelPort}.
 */
public final class ModelPorts {
    private ModelPorts() {}

    public static ModelPort open(Context context) {
        File model = ModelFiles.resolve(context);
        if (!ModelFiles.isPresent(model)) {
            return new NoModelPort();
        }
        ModelPort mediaPipe = tryMediaPipe(context, model);
        return mediaPipe != null ? mediaPipe : new NoModelPort();
    }

    public static ModelLifecycle lifecycle(Context context) {
        return new ModelLifecycle(
                open(context),
                ModelClock.SYSTEM,
                new AndroidDeviceGuard(context));
    }

    private static ModelPort tryMediaPipe(Context context, File model) {
        try {
            Class<?> type = Class.forName("com.textureflow.intelligence.model.MediaPipeModelPort");
            Constructor<?> ctor = type.getConstructor(Context.class, File.class);
            return (ModelPort) ctor.newInstance(context, model);
        } catch (ClassNotFoundException missing) {
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
