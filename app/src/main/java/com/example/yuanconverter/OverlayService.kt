package com.example.yuanconverter

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var expandedParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun showBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        var longPressTriggered = false
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressTriggered = true
                Toast.makeText(this@OverlayService, "Floating converter closed", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        })

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 300
        }

        windowManager.addView(bubbleView, bubbleParams)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        bubbleView?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams!!.x
                    initialY = bubbleParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    longPressTriggered = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (!longPressTriggered) {
                        bubbleParams!!.x = initialX - dx
                        bubbleParams!!.y = initialY + dy
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!longPressTriggered) {
                        if (!moved) {
                            toggleExpanded()
                        } else {
                            snapToEdge()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Keeps the bubble docked flush to the right edge after you drag it.
    private fun snapToEdge() {
        val params = bubbleParams ?: return
        params.x = 0
        windowManager.updateViewLayout(bubbleView, params)
    }

    private fun toggleExpanded() {
        if (isExpanded) collapseOverlay() else expandOverlay()
    }

    private fun expandOverlay() {
        bubbleView?.visibility = View.GONE
        expandedView = LayoutInflater.from(this).inflate(R.layout.overlay_expanded, null)

        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = bubbleParams?.x ?: 0
            y = bubbleParams?.y ?: 300
        }

        windowManager.addView(expandedView, expandedParams)
        isExpanded = true

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val yuanInput = expandedView!!.findViewById<EditText>(R.id.yuanInput)
        val multiplierInput = expandedView!!.findViewById<EditText>(R.id.multiplierInput)
        val resultText = expandedView!!.findViewById<TextView>(R.id.resultText)
        val closeButton = expandedView!!.findViewById<View>(R.id.closeButton)

        multiplierInput.setText(prefs.getFloat("multiplier", 15f).toString())

        fun recalc() {
            val yuan = yuanInput.text.toString().toDoubleOrNull()
            val mult = multiplierInput.text.toString().toDoubleOrNull()
            resultText.text = if (yuan != null && mult != null)
                "₹ ${"%.2f".format(yuan * mult)}"
            else
                "₹ 0.00"
        }

        yuanInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { recalc() }
            override fun afterTextChanged(s: Editable?) {}
        })

        multiplierInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                multiplierInput.text.toString().toFloatOrNull()?.let {
                    prefs.edit().putFloat("multiplier", it).apply()
                }
                recalc()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        closeButton.setOnClickListener { collapseOverlay() }
    }

    private fun collapseOverlay() {
        expandedView?.let { windowManager.removeView(it) }
        expandedView = null
        isExpanded = false
        bubbleView?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        expandedView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
    }
}
