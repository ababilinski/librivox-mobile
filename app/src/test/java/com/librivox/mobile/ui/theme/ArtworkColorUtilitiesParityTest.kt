package com.librivox.mobile.ui.theme

import com.librivox.mobile.colorutilities.Hct
import com.librivox.mobile.colorutilities.SchemeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorUtilitiesParityTest {
    @Test
    fun appOwnedSnapshotPreservesEveryArtworkColorRole() {
        val referenceHct = Class.forName("com.google.android.material.color.utilities.Hct")
        val referenceScheme = Class.forName("com.google.android.material.color.utilities.SchemeContent")
        val getters = SchemeContent::class.java.methods.filter {
            it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
        }
        assertTrue("Compare the full Material color scheme", getters.size >= 40)
        for (seed in listOf(0xff006973.toInt(), 0xffb3261e.toInt(), 0xff625b71.toInt(), 0xff808080.toInt())) {
            for (dark in listOf(false, true)) {
                val own = SchemeContent(Hct.fromInt(seed), dark, 0.0)
                val original = referenceScheme.getConstructor(referenceHct, Boolean::class.javaPrimitiveType, Double::class.javaPrimitiveType)
                    .newInstance(referenceHct.getMethod("fromInt", Int::class.javaPrimitiveType).invoke(null, seed), dark, 0.0)
                for (getter in getters) {
                    assertEquals("${getter.name}, seed=$seed, dark=$dark", referenceScheme.getMethod(getter.name).invoke(original), getter.invoke(own))
                }
            }
        }
    }
}
