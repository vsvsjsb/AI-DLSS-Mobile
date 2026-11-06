package com.aidlss.mobile

/**
 * GPU fragment shader for post-processing a frame texture.
 *
 * This is the processing stage for a future captured-frame renderer.
 * It is not connected to Tomb Raider's renderer or a live frame source yet.
 */
data class ImageEffectSettings(
    val sharpness: Int = 35,
    val contrast: Int = 100,
    val shadows: Int = 15,
    val saturation: Int = 100,
    val brightness: Int = 100
) {
    fun sharpnessUniform(): Float = (sharpness.coerceIn(0, 100) / 100f) * 1.25f
    fun contrastUniform(): Float = contrast.coerceIn(50, 150) / 100f
    fun shadowsUniform(): Float = shadows.coerceIn(-50, 50) / 100f
    fun saturationUniform(): Float = saturation.coerceIn(0, 200) / 100f
    fun brightnessUniform(): Float = brightness.coerceIn(50, 150) / 100f
}

object ImageEffects {
    const val FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uSharpness;
        uniform float uContrast;
        uniform float uShadows;
        uniform float uSaturation;
        uniform float uBrightness;

        void main() {
            vec4 center = texture2D(uTexture, vTexCoord);
            vec3 north = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y)).rgb;
            vec3 south = texture2D(uTexture, vTexCoord - vec2(0.0, uTexelSize.y)).rgb;
            vec3 east  = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb;
            vec3 west  = texture2D(uTexture, vTexCoord - vec2(uTexelSize.x, 0.0)).rgb;

            vec3 blur4 = (north + south + east + west) * 0.25;
            vec3 color = center.rgb + (center.rgb - blur4) * clamp(uSharpness, 0.0, 1.25);

            color = (color - vec3(0.5)) * clamp(uContrast, 0.5, 1.5) + vec3(0.5);

            float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float shadowMask = (1.0 - clamp(luminance, 0.0, 1.0));
            shadowMask *= shadowMask;
            color += vec3(uShadows * shadowMask);

            luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
            color = mix(vec3(luminance), color, clamp(uSaturation, 0.0, 2.0));
            color *= clamp(uBrightness, 0.5, 1.5);

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), center.a);
        }
    """
}
