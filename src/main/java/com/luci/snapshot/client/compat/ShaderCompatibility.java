package com.luci.snapshot.client.compat;

import com.luci.snapshot.SnapshotInit;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class ShaderCompatibility {
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static Object irisApi;
    private static Method shaderPackInUse;
    private static Method shadowPass;
    private static boolean reflectionAttempted;

    private ShaderCompatibility() {
    }

    public static void initialize() {
        initializeIrisReflection();
        SnapshotInit.LOGGER.info("[Snapshot] Renderer compatibility: {}", rendererLabel());
    }

    public static boolean irisLoaded() {
        return IRIS_LOADED;
    }

    public static boolean shaderPackActive() {
        initializeIrisReflection();
        return invokeBoolean(shaderPackInUse);
    }

    public static boolean shadowPassActive() {
        initializeIrisReflection();
        return invokeBoolean(shadowPass);
    }

    public static String rendererLabel() {
        if (IRIS_LOADED && shaderPackActive()) {
            return SODIUM_LOADED ? "Iris shader + Sodium" : "Iris shader";
        }
        if (IRIS_LOADED) {
            return SODIUM_LOADED ? "Iris + Sodium" : "Iris";
        }
        return SODIUM_LOADED ? "Sodium" : "Vanilla";
    }

    private static void initializeIrisReflection() {
        if (!IRIS_LOADED || reflectionAttempted) {
            return;
        }
        reflectionAttempted = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = apiClass.getMethod("getInstance").invoke(null);
            shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            shadowPass = apiClass.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Iris was found, but its compatibility API was unavailable.", exception);
            irisApi = null;
            shaderPackInUse = null;
            shadowPass = null;
        }
    }

    private static boolean invokeBoolean(Method method) {
        if (irisApi == null || method == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(irisApi);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
