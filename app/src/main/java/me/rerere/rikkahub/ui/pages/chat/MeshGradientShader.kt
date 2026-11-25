package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

internal fun interface MeshShader {
    fun draw(scope: DrawScope, first: MeshLight, second: MeshLight, third: MeshLight, fourth: MeshLight): Boolean
}

@RequiresApi(33)
internal fun createMeshShaderOrNull(): MeshShader? = try {
    AgslMeshShader(RuntimeShader(MESH_SHADER))
} catch (_: RuntimeException) {
    null
}

@RequiresApi(33)
private class AgslMeshShader(private val program: RuntimeShader) : MeshShader {
    private val paint = Paint().apply { shader = program }
    private var failed = false

    override fun draw(scope: DrawScope, first: MeshLight, second: MeshLight, third: MeshLight, fourth: MeshLight): Boolean {
        if (failed) return false
        var drawn = false
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (!native.isHardwareAccelerated) return@drawIntoCanvas
            try {
                setLight("center0", "color0", first)
                setLight("center1", "color1", second)
                setLight("center2", "color2", third)
                setLight("center3", "color3", fourth)
                native.drawRect(0f, 0f, scope.size.width, scope.size.height, paint)
                drawn = true
            } catch (_: RuntimeException) {
                failed = true
            }
        }
        return drawn
    }

    private fun setLight(center: String, color: String, light: MeshLight) {
        program.setFloatUniform(center, light.center.x, light.center.y, light.radius)
        val c = light.color
        program.setFloatUniform(color, c.red, c.green, c.blue, c.alpha)
    }
}

// The base gradient and blob geometry are shared with the Kotlin Canvas renderer.
private const val MESH_SHADER = """
    uniform float3 center0;
    uniform float3 center1;
    uniform float3 center2;
    uniform float3 center3;
    uniform float4 color0;
    uniform float4 color1;
    uniform float4 color2;
    uniform float4 color3;

    half4 overLight(half4 under, float2 pixel, float3 center, float4 color) {
        half a = half(color.a * max(0.0, 1.0 - distance(pixel, center.xy) / center.z));
        return half4(half3(color.rgb) * a, a) + under * (1.0 - a);
    }
    half4 main(float2 pixel) {
        half4 light = overLight(half4(0), pixel, center0, color0);
        light = overLight(light, pixel, center1, color1);
        light = overLight(light, pixel, center2, color2);
        return overLight(light, pixel, center3, color3);
    }
"""
