package fr.magistry.taigime2

import android.content.Context
import android.graphics._
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.Log

import scala.collection.JavaConverters._

/**
  * Created by pierre on 6/26/16.
  */
//class CustomKeyboardView(ctxt: Context, attributeSet: AttributeSet, defStyleAttr: Int, defStyleRes: Int) extends KeyboardView(ctxt, attributeSet, defStyleAttr, defStyleRes) {

class CustomKeyboardView(ctxt: Context, attributeSet: AttributeSet, defStyleAttr: Int, defStyleRes: Int) extends KeyboardView(ctxt, attributeSet, defStyleAttr, defStyleRes) {
  def this(ctxt: Context, attributeSet: AttributeSet, defStyleAttr: Int) = this(ctxt, attributeSet, defStyleAttr, 0)
  def this(ctxt: Context, attributeSet: AttributeSet) = this(ctxt, attributeSet, R.attr.keyboardViewStyle)
  Log.v("IME", "Custom KBDView")
  val paint = new Paint()
  paint.setColor(Color.WHITE)
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)
  paint.setTextAlign(Paint.Align.CENTER)
  def setFont(font: Typeface): Unit = {
    paint.setTypeface(font)
  }

  //paint.setTextSize((getResources.getDisplayMetrics.scaledDensity)  *  getResources.getDimension(R.dimen.candidate_font_height) )
  override def onDraw(canvas: Canvas): Unit = {
    super.onDraw(canvas)
    //invalidateAllKeys()
    getKeyboard.getKeys.asScala.foreach(k => {
      if(k.label != null) {
        val label = k.label.toString
        val ratio = (k.width * 0.6F) / paint.measureText("空")
        if (math.abs( 1 - ratio) > 0.01)
          paint.setTextSize(paint.getTextSize * ratio)
        canvas.drawText(label, k.x + (k.width / 2) , k.y + (k.height / 2) + paint.descent(), paint)
      }
     /* else {
        k.icon.setBounds(k.x,k.y, k.x + k.icon.getIntrinsicWidth, k.y + k.icon.getIntrinsicHeight)
        k.icon.draw(canvas)
      }*/
    })
  }



}
