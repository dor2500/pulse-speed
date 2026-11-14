package com.dorp.pulse.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The phone's retail name ("Pixel 10 Pro XL", "Samsung Galaxy S24 Ultra") rather than its build code
 * ("SM-S928B", "sdk_gphone64_x86_64"). Android has no single API for this, so it tries, in order:
 * the marketing-name system properties several makers set, Samsung's default device name, and finally
 * the build model itself.
 */
object DeviceName {

    fun current(context: Context): String {
        if (isEmulator()) return "Android Emulator"
        val maker = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()

        marketingProperty()?.let { return withBrand(maker, it) }

        // Samsung (and a few others) don't publish a marketing property, but the default device name in
        // Settings › About is the retail name. Only trust it when it still looks like one: people rename
        // their phone to things like "Dor's phone", which must not end up in a shared result.
        if (model.matches(Regex("^[A-Z]{1,3}-?[A-Z0-9]{3,}.*"))) {
            deviceNameSetting(context)?.let { return withBrand(maker, it) }
        }
        return withBrand(maker, model)
    }

    /** Cleans names saved by older versions (e.g. "Google sdk_gphone64_x86_64"). */
    fun display(stored: String?): String? {
        val s = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if ("sdk_gphone" in s || "Android SDK built for" in s || s.contains("emulator", ignoreCase = true)) return "Android Emulator"
        return s.removePrefix("Google ").takeIf { it.startsWith("Pixel") } ?: s
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.startsWith("sdk_gphone") || Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk_")

    private fun withBrand(maker: String, name: String): String = when {
        name.startsWith("Pixel") -> name // Google's own phones: the model already is the retail name
        name.startsWith(maker, ignoreCase = true) -> name
        maker.equals("samsung", ignoreCase = true) && name.startsWith("Galaxy") -> "Samsung $name"
        else -> "$maker $name"
    }

    @SuppressLint("PrivateApi")
    private fun marketingProperty(): String? = runCatching {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        listOf(
            "ro.product.marketname", "ro.product.vendor.marketname", "ro.vendor.product.marketname",
            "ro.product.odm.marketname", "ro.config.marketing_name", "ro.vendor.oplus.market.name",
            "ro.product.oplus.marketname",
        ).firstNotNullOfOrNull { key -> (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() } }
    }.getOrNull()

    private fun deviceNameSetting(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.trim()
    }.getOrNull()?.takeIf { n ->
        n.isNotEmpty() && n.length <= 40 && '\'' !in n && '’' !in n &&
            listOf("Galaxy", "Xperia", "Redmi", "Moto", "Nokia", "OnePlus", "Nothing", "Pixel").any { n.contains(it, ignoreCase = true) }
    }
}
