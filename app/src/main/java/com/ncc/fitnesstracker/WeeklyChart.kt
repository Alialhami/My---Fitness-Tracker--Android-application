package com.ncc.fitnesstracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A native chart backed by the same recorded minutes used in the accessible description. */
class WeeklyChart @JvmOverloads constructor(context:Context,private val values:List<Int> = List(7){0}):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {contentDescription="Daily minutes, oldest to newest: ${values.joinToString()}. Total ${values.sum()} minutes.";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);val density=resources.displayMetrics.density;val bottom=height-28*density
        val step=width/7f;val max=(values.maxOrNull()?:1).coerceAtLeast(1)
        values.forEachIndexed {i,value->
            val h=(bottom-30*density)*value/max;val x=step*i+step*.2f
            paint.color=if(i==6)Color.rgb(23,107,81) else Color.rgb(188,220,165)
            canvas.drawRoundRect(x,bottom-h, x+step*.6f,bottom,5*density,5*density,paint)
            paint.color=Color.rgb(24,46,39);paint.textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,11f,resources.displayMetrics);paint.textAlign=Paint.Align.CENTER
            canvas.drawText(value.toString(),x+step*.3f,bottom-h-6*density,paint)
            val day=LocalDate.now().minusDays((6-i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.getDefault())
            canvas.drawText(day,x+step*.3f,height-6*density,paint)
        }
    }
}
