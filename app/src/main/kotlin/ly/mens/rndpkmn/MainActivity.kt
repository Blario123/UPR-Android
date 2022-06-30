package ly.mens.rndpkmn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        val channel = NotificationChannel(
                CHANNEL_ID.toString(),
                getString(R.string.action_batch_random),
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.desc_batch)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        setContent { RandomizerApp() }
    }

    override fun onDestroy() {
        if (isFinishing) {
            filesDir.listFiles()?.forEach {
                if (it.isDirectory) {
                    it.deleteRecursively()
                }
            }
        }
        super.onDestroy()
    }

}
