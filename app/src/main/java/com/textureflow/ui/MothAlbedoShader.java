package com.textureflow.ui;

import android.annotation.SuppressLint;
import android.graphics.RuntimeShader;
import android.os.Build;

/**
 * AGSL light catch: moth texture is an albedo map only (not a drawn background).
 * Whiter map texels catch more colored light; base paper stays untouched.
 *
 * Point lights 0–2 (nav + cue). Ring emitters 0–2 follow rounded-rect outlines
 * so app-origin glow wraps the full card border and stays map-gated.
 */
final class MothAlbedoShader {
    static final int MAX_LIGHTS = 3;
    static final int MAX_RINGS = 3;

    private static final String AGSL = ""
            + "uniform shader albedoMap;\n"
            + "uniform float2 resolution;\n"
            + "uniform float2 texSize;\n"
            + "uniform float coverScale;\n"
            + "uniform float2 coverOffset;\n"
            + "uniform float3 baseColor;\n"
            + "uniform float albedoStrength;\n"
            + "uniform float albedoContrast;\n"
            + "uniform float2 lightPos0;\n"
            + "uniform float3 lightColor0;\n"
            + "uniform float lightRadius0;\n"
            + "uniform float lightIntensity0;\n"
            + "uniform float lightPower0;\n"
            + "uniform float2 lightPos1;\n"
            + "uniform float3 lightColor1;\n"
            + "uniform float lightRadius1;\n"
            + "uniform float lightIntensity1;\n"
            + "uniform float lightPower1;\n"
            + "uniform float2 lightPos2;\n"
            + "uniform float3 lightColor2;\n"
            + "uniform float lightRadius2;\n"
            + "uniform float lightIntensity2;\n"
            + "uniform float lightPower2;\n"
            + "uniform float2 ringCenter0;\n"
            + "uniform float2 ringHalf0;\n"
            + "uniform float ringCorner0;\n"
            + "uniform float ringReach0;\n"
            + "uniform float3 ringColor0;\n"
            + "uniform float ringIntensity0;\n"
            + "uniform float ringPower0;\n"
            + "uniform float2 ringCenter1;\n"
            + "uniform float2 ringHalf1;\n"
            + "uniform float ringCorner1;\n"
            + "uniform float ringReach1;\n"
            + "uniform float3 ringColor1;\n"
            + "uniform float ringIntensity1;\n"
            + "uniform float ringPower1;\n"
            + "uniform float2 ringCenter2;\n"
            + "uniform float2 ringHalf2;\n"
            + "uniform float ringCorner2;\n"
            + "uniform float ringReach2;\n"
            + "uniform float3 ringColor2;\n"
            + "uniform float ringIntensity2;\n"
            + "uniform float ringPower2;\n"
            + "\n"
            + "half4 sampleMap(float2 fragCoord) {\n"
            + "    float2 bitmapCoord = (fragCoord - coverOffset) / max(coverScale, 0.0001);\n"
            + "    bitmapCoord = clamp(bitmapCoord, float2(0.5), texSize - float2(0.5));\n"
            + "    return albedoMap.eval(bitmapCoord);\n"
            + "}\n"
            + "\n"
            + "float sdRoundBox(float2 p, float2 halfSize, float r) {\n"
            + "    float2 q = abs(p) - halfSize + float2(r);\n"
            + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "\n"
            + "float lightCatch(float2 fragCoord, float albedo,\n"
            + "        float2 pos, float radius, float intensity, float mapPower) {\n"
            + "    if (intensity <= 0.0001 || albedo <= 0.05) return 0.0;\n"
            + "    float d = length(fragCoord - pos);\n"
            + "    float envelope = clamp(1.0 - d / max(radius, 1.0), 0.0, 1.0);\n"
            + "    float irradiance = intensity * envelope * envelope * envelope;\n"
            + "    float gated = pow(albedo, max(mapPower, 1.0));\n"
            + "    return irradiance * gated * albedoStrength * 0.55;\n"
            + "}\n"
            + "\n"
            + "float ringCatch(float2 fragCoord, float albedo,\n"
            + "        float2 center, float2 halfSize, float corner,\n"
            + "        float reach, float intensity, float mapPower) {\n"
            + "    if (intensity <= 0.0001 || albedo <= 0.05) return 0.0;\n"
            + "    float sd = sdRoundBox(fragCoord - center, halfSize, max(corner, 0.0));\n"
            + "    float outside = max(sd, 0.0);\n"
            + "    // Soft map spill — deliberately quiet right next to the ring.\n"
            + "    float soft = exp(-outside / max(reach, 1.0));\n"
            + "    float nearFade = clamp(outside / max(reach * 0.45, 1.0), 0.0, 1.0);\n"
            + "    float envelope = soft * mix(0.22, 1.0, nearFade);\n"
            + "    float gated = pow(albedo, max(mapPower, 1.0));\n"
            + "    return intensity * envelope * gated * albedoStrength * 0.32;\n"
            + "}\n"
            + "\n"
            + "half4 main(float2 fragCoord) {\n"
            + "    half4 tex = sampleMap(fragCoord);\n"
            + "    float white = tex.r;\n"
            + "    float c = clamp(albedoContrast, 0.8, 4.0);\n"
            + "    float albedo = clamp(pow(white, c), 0.0, 1.0);\n"
            + "\n"
            + "    float3 reflected = float3(0.0);\n"
            + "    reflected += lightColor0 * lightCatch(fragCoord, albedo,\n"
            + "            lightPos0, lightRadius0, lightIntensity0, lightPower0);\n"
            + "    reflected += lightColor1 * lightCatch(fragCoord, albedo,\n"
            + "            lightPos1, lightRadius1, lightIntensity1, lightPower1);\n"
            + "    reflected += lightColor2 * lightCatch(fragCoord, albedo,\n"
            + "            lightPos2, lightRadius2, lightIntensity2, lightPower2);\n"
            + "    reflected += ringColor0 * ringCatch(fragCoord, albedo,\n"
            + "            ringCenter0, ringHalf0, ringCorner0,\n"
            + "            ringReach0, ringIntensity0, ringPower0);\n"
            + "    reflected += ringColor1 * ringCatch(fragCoord, albedo,\n"
            + "            ringCenter1, ringHalf1, ringCorner1,\n"
            + "            ringReach1, ringIntensity1, ringPower1);\n"
            + "    reflected += ringColor2 * ringCatch(fragCoord, albedo,\n"
            + "            ringCenter2, ringHalf2, ringCorner2,\n"
            + "            ringReach2, ringIntensity2, ringPower2);\n"
            + "\n"
            + "    float3 outColor = clamp(baseColor + reflected, 0.0, 1.0);\n"
            + "    return half4(outColor, 1.0);\n"
            + "}\n";

    private MothAlbedoShader() {}

    static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    @SuppressLint("NewApi")
    static RuntimeShader create() {
        return new RuntimeShader(AGSL);
    }
}
