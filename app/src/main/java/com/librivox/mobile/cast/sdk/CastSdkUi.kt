package com.librivox.mobile.cast.sdk

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.media.MediaRouteSelector
import com.librivox.mobile.cast.diagnostics.CastDiagnostics
import com.google.android.gms.cast.CastMediaControlIntent

/**
 * Helpers that let the rest of the app open the Cast SDK's stock UI surfaces
 * — used when "Google default Cast UI" is selected in Cast settings.
 */
object CastSdkUi {
    private const val TAG = "CastSdkUi"

    /** Opens the SDK MediaRouteChooserDialog so the user can pick a Cast device. */
    fun showRouteChooserDialog(activity: FragmentActivity, receiverAppId: String) {
        runCatching {
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(receiverAppId))
                .build()
            val fragment = MediaRouteChooserDialogFragment().apply {
                routeSelector = selector
            }
            fragment.show(activity.supportFragmentManager, "cast_route_chooser")
        }.onFailure { error ->
            CastDiagnostics.get()?.e(TAG, "Unable to show MediaRouteChooserDialog", error)
        }
    }

    /** Opens [AudiobookExpandedControllerActivity] for an active Cast session. */
    fun openExpandedController(context: Context) {
        val intent = Intent(context, AudiobookExpandedControllerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                CastDiagnostics.get()?.e(TAG, "Unable to open expanded controller", error)
            }
    }
}
