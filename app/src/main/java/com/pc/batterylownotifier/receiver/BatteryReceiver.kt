package com.pc.batterylownotifier.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pc.batterylownotifier.R
import java.util.*


class BatteryReceiver : BroadcastReceiver {
    companion object {
        var post = false
        var okay = false
        var isRequestRunning = false
        val auth: FirebaseAuth = Firebase.auth
        val db: FirebaseFirestore = Firebase.firestore

        fun getBatteryPercentage(context: Context): Int {
            return if (Build.VERSION.SDK_INT >= 21) {
                val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, iFilter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = level / scale.toDouble()
                (batteryPct * 100).toInt();
            }
        }
        fun createNotification(context: Context){
            var channelId = context.getString(R.string.channel_id);
            var builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle("Low Battery")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            with(NotificationManagerCompat.from(context)) {
                // Set as Constant so it overwrites
                notify(10, builder.build())
            }
        }
    }

    constructor()

    override fun onReceive(contxt: Context?, intent: Intent?) {
        if(contxt == null)
            return;
        if (intent?.action == Intent.ACTION_BATTERY_LOW && okay) {
            okay = false;
            post = true;
        } else if (intent?.action == Intent.ACTION_BATTERY_OKAY && !okay) {
            okay = true;
            post = false;
        }

        if (!okay && post && !isRequestRunning) {
            isRequestRunning = true
            val time = Calendar.getInstance(TimeZone.getTimeZone("gmt")).timeInMillis
            val currentUser = auth.currentUser ?: return
            val userId = currentUser.uid
            val battery = getBatteryPercentage(contxt)
            val data = hashMapOf(
                "date" to time.toString(),
                "battery" to battery
            )

            db.collection("battery").document(userId).set(
                data, SetOptions.merge()
            ).addOnCompleteListener { _ ->
                run {
                    post = false
                    isRequestRunning = false
                    createNotification(contxt)
                }
            };
        }
    }
}
