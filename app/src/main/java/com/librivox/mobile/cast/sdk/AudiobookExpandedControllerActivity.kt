package com.librivox.mobile.cast.sdk

import android.view.Menu
import com.librivox.mobile.R
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

/**
 * Cast SDK's stock expanded controller, used when the user opts into
 * "Google default Cast UI" from Cast settings. Hosts a [androidx.mediarouter.app.MediaRouteButton]
 * in the action bar so the user can pick a different receiver from inside the controller.
 */
class AudiobookExpandedControllerActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_menu, menu)
        menu?.findItem(R.id.media_route_menu_item)?.let { item ->
            CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        }
        return true
    }
}
