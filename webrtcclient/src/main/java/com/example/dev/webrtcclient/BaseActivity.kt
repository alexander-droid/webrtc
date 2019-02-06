package com.example.dev.webrtcclient

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.util.DisplayMetrics
import android.view.Display
import android.support.v4.content.ContextCompat.getSystemService
import android.view.WindowManager



abstract class BaseActivity: AppCompatActivity() {

    fun showMessage(message: String?, textColor: Int = Color.WHITE, bg: Int = Color.DKGRAY, anchor: View? = null) {
        message?: return

        val toast = Toast(this)
        toast.setGravity(Gravity.BOTTOM, 0, 0)

        if (anchor != null) {
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            toast.setGravity(Gravity.BOTTOM, 0, height - location[1] + 100)
        }

        val inflate = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v = inflate.inflate(R.layout.toast, null, false)
        v.backgroundTintList = ColorStateList.valueOf(bg)
        val tv = v.findViewById(R.id.message) as TextView
        tv.text = message
        tv.setTextColor(textColor)

        toast.view = v
        toast.duration = Toast.LENGTH_SHORT
        toast.show()
    }
}