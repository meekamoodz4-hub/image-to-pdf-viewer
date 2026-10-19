
package com.meekamoodz.imagepdfviewer

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(245, 247, 252))
        }

        val title = TextView(this).apply {
            text = "Image to PDF Viewer"
            textSize = 26f
            setTextColor(Color.rgb(35, 55, 100))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Convert your pictures into PDF files"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        layout.addView(title)
        layout.addView(subtitle)

        setContentView(layout)
    }
}
