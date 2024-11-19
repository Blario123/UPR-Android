package ly.mens.rndpkmn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs
import ly.mens.rndpkmn.ui.CHANNEL_ID
import ly.mens.rndpkmn.ui.RandomizerApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.action_batch_random),
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.desc_batch)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.deleteNotificationChannel("69420")
        setContent { RandomizerApp() }
        if (!Environment.isExternalStorageManager()) {
            val AppAccessCode = 501
            val i: Intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
            )
            startActivityForResult(i, AppAccessCode)
        }
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
