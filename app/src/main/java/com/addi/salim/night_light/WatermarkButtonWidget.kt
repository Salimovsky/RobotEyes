package com.addi.salim.night_light

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Custom image-and-text widget pairing some styling with handling of enabled/disabled state. */

class WatermarkButtonWidget(context: Context, attrs: AttributeSet?, defStyle: Int) : FrameLayout(context, attrs, defStyle, R.style.AppTheme_Widget_WatermarkButton) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_watermark_button, this, true)
    }

    private val text: TextView = findViewById(R.id.widget_text)
    private val secondaryText: TextView = findViewById(R.id.widget_secondary_text)
    private val watermark: ImageView = findViewById(R.id.widget_watermark)

    init {
        val style = context.obtainStyledAttributes(attrs, R.styleable.WatermarkButtonWidget, defStyle, 0)
        text.text = style.getString(R.styleable.WatermarkButtonWidget_text) ?: ""
        secondaryText.text = style.getString(R.styleable.WatermarkButtonWidget_secondaryText) ?: ""
        watermark.setImageDrawable(style.getDrawable(R.styleable.WatermarkButtonWidget_watermark))
        isEnabled = style.getBoolean(R.styleable.WatermarkButtonWidget_enabled, true)
        style.recycle()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        secondaryText.visibility = if (!enabled) VISIBLE else GONE
    }
}
