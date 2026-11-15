package io.niko.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.niko.utils.SendLog

class BlankActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // process crash log
        intent?.getStringExtra("sendLog")?.apply {
            // the report failing (no room for the log, the file provider) is no reason to crash once more
            try {
                SendLog.sendLog(this@BlankActivity, this)
            } catch (e: Throwable) {
                io.niko.ktx.Logs.e(e)
            }
        }

        finish()
    }

}