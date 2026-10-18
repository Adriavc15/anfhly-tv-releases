package com.anfhly.streaming.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anfhly.streaming.recommendations.TvRecommendationsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "Device booted, updating TV recommendations...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TvRecommendationsManager.updateRecommendations(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error updating recommendations on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
