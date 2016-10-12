package fr.magistry.taigime2

import fr.magistry.utils.taigi._
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, Paint, Rect, Typeface}
import android.util.{DisplayMetrics, Log}
import android.view.View.MeasureSpec
import android.view.inputmethod.InputConnection
import android.view.{GestureDetector, MotionEvent, View}
import java.util.regex.Pattern



sealed abstract class CandidateViewItem
abstract class MenuItem(action: () => Unit) extends CandidateViewItem
case class TextMenuItem(label: () => String, action: () => Unit) extends MenuItem(action)
case class IconMenuItem(icon: () => Drawable, action: () => Unit) extends MenuItem(action)

abstract class TextItem extends CandidateViewItem
case class CompositionItem(label: String, consumedLength: Int) extends TextItem
case class CorrectionItem(label: String, correctedString: String) extends TextItem
/**
  * Created by pierre on 6/26/16.
  */
class CustomCandidateView(ctxt: Context) extends View(ctxt) {

  var ime: Option[TaigIME] = None
  var imeData: Option[ImeDatabase] = None



  val paint = new Paint()
  val drawSeparator = getResources.getDrawable(R.drawable.candidate_separator, null)
  val drawContainer = getResources.getDrawable(R.drawable.candidates_container_bg, null)
  val drawMenuItemContainer = getResources.getDrawable(R.drawable.menu_item_container_bg, null)
  val drawHighlight = getResources.getDrawable(R.drawable.candidate_highlight, null)
  var candidates = List.empty[TextItem]
  var predictions = List.empty[String]
  // limit on X axis and callbacks for clicks
  var callbacks = List.empty[(Int, () => Unit)]
  var maxWidth = 0
  var currentScrollX = 0
  var currentLetterKeyboard = "latin"
  var fuzzyMatches = false

  var menuItems = List(
    TextMenuItem(
      label = () => "換鍵盤",
      action = () => ime.map(_.message(Message.ChangeKeyboard))
    ),
    TextMenuItem(
      () => {"設定"},
      () => {ime.map(_.message(Message.DisplayConfig))}
    )/*,
    TextMenuItem(
      () => "辭典",
      () => {ime.map(_.message(Message.DisplayDict))}
      )*/
  )


  val gestureDetector = new GestureDetector(ctxt, new GestureDetector.SimpleOnGestureListener {
    override def onScroll(motionEvent1: MotionEvent, motionEvent2: MotionEvent, dX: Float, dY: Float): Boolean = {
      val prevX = getScrollX()
      val nextX = prevX + dX match {
        case x if x < 0  => 0
        case x if x > maxWidth => maxWidth
        case x => x
      }
      scrollTo(nextX.toInt, getScrollY())
      currentScrollX = nextX.toInt
      invalidate()
      true
    }

    override def onSingleTapConfirmed(motionEvent: MotionEvent): Boolean = {
      val x = motionEvent.getX().toInt
      Log.v("IME-touch", x.toString)
      onClickedAt(currentScrollX + x)
      true
    }



  })


  // Set font size according to screen
  paint.setTextSize(getResources.getDimension(R.dimen.candidate_font_height))
  paint.setColor(getResources.getColor(R.color.colorWhite))
  paint.setTypeface(Typeface.createFromAsset(ctxt.getAssets, "fonts/MOEDICT.ttf"))
  //setBackgroundColor(R.color.imeBackground)
  this.setVisibility(View.VISIBLE)
  setWillNotDraw(false)
  setVerticalScrollBarEnabled(false)
  requestLayout()

  def setImeData(imeDatabase: ImeDatabase): Unit = {
    imeData = Some(imeDatabase)
  }

  def setImeService(imeService: ImeService): Unit = {
    ime = Some(imeService)
  }

  override def onMeasure(width: Int, height: Int): Unit = {
    val padding = new Rect()
    drawContainer.getPadding(padding)
    setMeasuredDimension(MeasureSpec.getSize(width), paint.descent.toInt - paint.ascent.toInt +  padding.top + padding.bottom)
  }




  def computeCandidateWidth(txt: String) = {
    val padding = new Rect()
    drawContainer.getPadding(padding)
    paint.measureText(txt).toInt + padding.left + padding.right

  }


  def drawOneItem(txt: String, container: Drawable, canvas: Canvas, x: Int, xPadding: Int, y: Int): Int = {
    val width = computeCandidateWidth(txt)
    container.setBounds(x,0,x+width,getHeight)
    container.draw(canvas)
    canvas.drawText(txt, x + xPadding, y, paint)
    width
  }

  def drawOneTextItem(txt: String, canvas: Canvas, x: Int, xPadding: Int, y:Int) = drawOneItem(txt, drawContainer, canvas, x, xPadding, y)
  def drawOneMenuItem(txt: String, canvas: Canvas, x: Int, xPadding: Int, y:Int) = drawOneItem(txt, drawMenuItemContainer, canvas, x, xPadding, y)

  /**
    * Main drawing of the Candidates UI happens here
    * it also refresh the list of callbacks
    *
    * @param canvas
    */
  override def onDraw(canvas: Canvas): Unit = canvas match {
    case null => super.onDraw(canvas)
    case _ =>
      super.onDraw(canvas)
      val padding = new Rect()
      drawContainer.getPadding(padding)

      val y =  (padding.top - paint.ascent()).toInt
      var x = 0

      // 1 - Display conversion candidates
      val callbacksCandidates = candidates.map( {
        case c:CompositionItem =>
          val width = drawOneTextItem(c.label, canvas, x, padding.left, y)
          x += width
          (x, () => ime foreach(_.message(Message.SelectCandidate(c))))
        case CorrectionItem(correct, source) =>
          val txt = s"$source:$correct"
          val width = drawOneTextItem(txt, canvas, x, padding.left, y)
          x += width
          (x, () => ime foreach(_.message(Message.SelectCorrection(correct,source))))
        })
      maxWidth = x

      // 2 - predictions

      // 3 - Options

      val callbacksWithMenuItems =  callbacksCandidates :::   menuItems.map({
        case TextMenuItem(getLabel, action) =>
          val txt = getLabel()
          val width = drawOneMenuItem(txt, canvas, x, padding.left, y)
          x += width
          (x, action)
        case _ => (x, () => ())
      })

      maxWidth = x
      callbacks = callbacksWithMenuItems
  }

  def updateCandidates(newCandidates: List[TextItem]) = {
    candidates = newCandidates
    if (currentScrollX != 0) {
      scrollTo(0, getScrollY)
      currentScrollX = 0
    }
    invalidate()
  }
  def onClickedAt(position: Int): Unit = {
    def aux(rest: List[(Int, () => Unit)]): Unit = rest match {
      case Nil => ()
      case (nextWordAt, cb)::_ if nextWordAt > position => cb()
      case _::tail => aux(tail)
    }
    aux(callbacks)
  }


  override def onTouchEvent(motionEvent: MotionEvent): Boolean = {
    gestureDetector.onTouchEvent(motionEvent)
    true
  }


}
