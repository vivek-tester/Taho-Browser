package app.taho.browser.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface DefensiveIntentResult {
    data class SafeIntent(
        val intent: Intent,
        val targetAppPackage: String?,
        val displayLabel: String,
        val fallbackUrl: String?,
    ) : DefensiveIntentResult

    data class FallbackInBrowser(
        val fallbackUrl: String,
    ) : DefensiveIntentResult

    data class Blocked(
        val reason: String,
    ) : DefensiveIntentResult
}

object DefensiveIntentHandler {
    private val SAFE_SCHEMES = setOf("mailto", "tel", "sms", "geo", "https", "http")

    fun parse(rawUri: String, context: Context): DefensiveIntentResult {
        val trimmed = rawUri.trim()
        if (trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("file:", ignoreCase = true) ||
            trimmed.startsWith("content:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
        ) {
            return DefensiveIntentResult.Blocked("Dangerous scheme blocked")
        }

        if (trimmed.startsWith("intent:", ignoreCase = true)) {
            val parsed = runCatching {
                Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
            }.getOrNull() ?: return DefensiveIntentResult.Blocked("Malformed intent URI")

            // Defensive stripping of components and selectors to prevent unexported component attacks
            parsed.component = null
            parsed.selector = null

            // Clean flags and set browsable category
            parsed.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            parsed.addCategory(Intent.CATEGORY_BROWSABLE)

            // Extract fallback URL if present
            val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }

            // Ensure scheme is safe
            val scheme = parsed.scheme?.lowercase()
            if (scheme != null && scheme !in SAFE_SCHEMES && scheme != "market") {
                if (fallbackUrl != null) {
                    return DefensiveIntentResult.FallbackInBrowser(fallbackUrl)
                }
                return DefensiveIntentResult.Blocked("Untrusted intent scheme: $scheme")
            }

            val targetPackage = parsed.`package`
            val packageManager = context.packageManager
            val resolveInfo = runCatching {
                packageManager.resolveActivity(parsed, 0)
            }.getOrNull()

            if (resolveInfo == null) {
                if (fallbackUrl != null) {
                    return DefensiveIntentResult.FallbackInBrowser(fallbackUrl)
                }
                return DefensiveIntentResult.Blocked("No application found to handle intent")
            }

            val label = runCatching {
                resolveInfo.loadLabel(packageManager).toString()
            }.getOrNull() ?: targetPackage ?: "External Application"

            return DefensiveIntentResult.SafeIntent(
                intent = parsed,
                targetAppPackage = targetPackage,
                displayLabel = label,
                fallbackUrl = fallbackUrl,
            )
        }

        // Allowlisted standard schemes: mailto, tel, sms, geo
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return DefensiveIntentResult.Blocked("Invalid URI")

        val scheme = uri.scheme?.lowercase()
        val action = when (scheme) {
            "tel" -> Intent.ACTION_DIAL
            "mailto", "sms", "geo" -> Intent.ACTION_VIEW
            else -> return DefensiveIntentResult.Blocked("Unsupported scheme: $scheme")
        }

        val intent = Intent(action, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val packageManager = context.packageManager
        val resolveInfo = runCatching {
            packageManager.resolveActivity(intent, 0)
        }.getOrNull() ?: return DefensiveIntentResult.Blocked("No application found for $scheme")

        val label = runCatching {
            resolveInfo.loadLabel(packageManager).toString()
        }.getOrNull() ?: "External Application"

        return DefensiveIntentResult.SafeIntent(
            intent = intent,
            targetAppPackage = resolveInfo.activityInfo?.packageName,
            displayLabel = label,
            fallbackUrl = null,
        )
    }
}
