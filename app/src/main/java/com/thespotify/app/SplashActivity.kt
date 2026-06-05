package com.thespotify.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 1900L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var advanced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        animateIn()
        handler.postDelayed({ goToMain() }, SPLASH_DURATION_MS)
    }

    private fun animateIn() {
        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val credit = findViewById<View>(R.id.splashCredit)

        logo.alpha = 0f; logo.scaleX = 0.55f; logo.scaleY = 0.55f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.6f)).setDuration(700L).start()

        for (v in listOf(title, credit)) { v.alpha = 0f; v.translationY = 48f }
        title.animate().alpha(1f).translationY(0f)
            .setInterpolator(DecelerateInterpolator()).setStartDelay(380L).setDuration(620L).start()
        credit.animate().alpha(1f).translationY(0f)
            .setInterpolator(DecelerateInterpolator()).setStartDelay(620L).setDuration(620L).start()
    }

    private fun goToMain() {
        if (advanced || isFinishing) return
        advanced = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        handler.removeCallbacksAndMessages(null)
        goToMain()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
