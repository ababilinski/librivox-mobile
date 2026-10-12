package com.librivox.mobile.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.librivox.mobile.model.ProfileRepository
import kotlinx.coroutines.runBlocking

class ResetOnboardingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESET_ONBOARDING) return
        runBlocking {
            ProfileRepository(context.applicationContext).signOut()
        }
        Log.i(TAG, "Onboarding completion reset for debug build.")
    }

    private companion object {
        const val ACTION_RESET_ONBOARDING = "com.librivox.mobile.DEBUG_RESET_ONBOARDING"
        const val TAG = "ResetOnboarding"
    }
}
