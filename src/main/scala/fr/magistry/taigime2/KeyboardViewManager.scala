package fr.magistry.taigime2

import java.io.{IOException, InputStreamReader}

import android.content.Context
import android.graphics.{Canvas, Color, Typeface}
import android.inputmethodservice.{Keyboard, KeyboardView}
import android.provider.CalendarContract.Colors
import android.util.Log
import android.view.{LayoutInflater, View}
import android.widget.TextView

import scala.io.Source


/**
  * Control the InputView (bottom view) of the IME.
  * Mostly used for keyboards but may also display config options and dictionary results.
  * Created by pierre on 8/15/16.
  */
class KeyboardViewManager(context: Context, ime: TaigIME) extends Resources(context){

  /**
    * Different types of UI that can be used
    *
    * @param id
    */
  sealed abstract class ViewType(val id: Int)
  sealed abstract  class KbdView(id: Int) extends ViewType(id)
  case object KeyboardLatin extends KbdView(rGetInt(R.integer.KeyboardLatin))
  case object KeyboardTsuimInitials extends KbdView(rGetInt(R.integer.KeyboardTsuimInitials))
  case object KeyboardTsuimFinals extends KbdView(rGetInt(R.integer.KeyboardTsuimFinals))
  case object KeyboardSymbols extends KbdView(rGetInt(R.integer.KeyboardSymbols))
  case object ConfigView extends ViewType(rGetInt(R.integer.KbdConfigView))
  case object DictionaryView extends ViewType(rGetInt(R.integer.KbdDictView))
  case object LoadingView extends ViewType(rGetInt(R.integer.KbdLoadingData))

  def keyboardTypeFromId(id: Int): ViewType = id match  {
    case KeyboardLatin.id => KeyboardLatin
    case KeyboardTsuimInitials.id => KeyboardTsuimInitials
    case KeyboardTsuimFinals.id => KeyboardTsuimFinals
    case KeyboardSymbols.id => KeyboardSymbols
    case ConfigView.id => ConfigView
    case DictionaryView.id => DictionaryView
    case LoadingView.id => LoadingView
    case _ => KeyboardLatin // throw new IllegalArgumentException()
  }

  // actual Views for each KeyboardType

  var viewsMap = Map.empty[ViewType, View]

  protected def buildViewsMap(): Unit = {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val kbdView = inflater.inflate(R.layout.keyboard, null, false).asInstanceOf[CustomKeyboardView]
    kbdView.setOnKeyboardActionListener(ime)
    kbdView.setFont(Typeface.createFromAsset(context.getAssets, "fonts/bpm.ttf" ))
    val loadingView = inflater.inflate(R.layout.preview, null).asInstanceOf[TextView]
    val configView = new ConfigView(context, ime)
   /* val dictView = new TextView(context)
    dictView.setVerticalScrollBarEnabled(true)
    dictView.setBackgroundColor(Color.BLACK)
    dictView.setTextColor(Color.WHITE)
    dictView.setHeight(400)
    dictView.setMaxHeight(400) */
    viewsMap =
      Map(
        KeyboardLatin -> kbdView,
        KeyboardTsuimInitials -> kbdView,
        KeyboardTsuimFinals -> kbdView,
        KeyboardSymbols -> kbdView,
        LoadingView -> loadingView,
        ConfigView -> configView.view
        // TODO  ConfigView -> DictionaryView -> LoadingView ->
      )
  }

  var keyboardsMap = Map.empty[KbdView, Keyboard]

  protected def buildKeyboardsMap(): Unit = {
    keyboardsMap =
      Map(
        KeyboardLatin -> new Keyboard(context, R.xml.qwerty),
        KeyboardTsuimInitials -> new Keyboard(context, R.xml.zhuyininitials),
        KeyboardTsuimFinals -> new Keyboard(context, R.xml.zhuyinfinals),
        KeyboardSymbols -> new Keyboard(context, R.xml.symbols)
      )
  }

  var currentKeyboardType: ViewType = defaultKeyboardType

  def currentView: Option[View] = viewsMap.get(currentKeyboardType)

  def defaultKeyboardType: ViewType = {
    val kid = context.getSharedPreferences("TaigIME2", 0).getInt(rGetString(R.string.LastKeyboard), rGetInt(R.integer.KeyboardLatin))
    keyboardTypeFromId(kid)
  }

  def setDefaultKeyboardType(keyboardType: ViewType) = {
    context.getSharedPreferences("TaigIME2",0)
      .edit()
      .putInt(rGetString(R.string.LastKeyboard),keyboardType.id )
      .apply()
  }




  def setKeyboard(keyboardType: ViewType):Unit = {
    Log.i("KbdM", keyboardType.toString)
    keyboardType match {
      case t: KbdView =>
        viewsMap.get(t) foreach( kbdView =>
          keyboardsMap.get(t) foreach (keyboard => {
            kbdView.asInstanceOf[KeyboardView].setKeyboard(keyboard)
            kbdView.asInstanceOf[KeyboardView].postInvalidate()
          })

          )
      case _ => ()
    }
    (currentKeyboardType, keyboardType) match {
      case (_: KbdView, _: KbdView) => () // no need to change the inputView
      case _ =>
        if (currentKeyboardType != keyboardType) {
          Log.v("IME,", "???!!!" )
          ime.setView(viewsMap(keyboardType))
        }
    }
    currentKeyboardType = keyboardType
  }

  def initialize(): Unit = {
    buildViewsMap()
    buildKeyboardsMap()
    if (ime.isLoading)
      setKeyboard(LoadingView)
    else
      setKeyboard(defaultKeyboardType)
  }

  def view: View = viewsMap.get(currentKeyboardType).orNull


  def nextKeyboard(): Unit = currentKeyboardType match {
    case KeyboardLatin => setKeyboard(KeyboardTsuimInitials)
    case KeyboardTsuimInitials => setKeyboard(KeyboardTsuimFinals)
    case KeyboardTsuimFinals => setKeyboard(KeyboardLatin)
    case KeyboardSymbols => setKeyboard(defaultKeyboardType)
    case DictionaryView => setKeyboard(defaultKeyboardType)
    case ConfigView => setKeyboard(defaultKeyboardType)
    case LoadingView => () // not the way to finish loading
  }

  def swapSymbols(): Unit = currentKeyboardType match {
    case KeyboardLatin => setKeyboard(KeyboardSymbols)
    case KeyboardTsuimInitials => setKeyboard(KeyboardSymbols)
    case KeyboardTsuimFinals => setKeyboard(KeyboardSymbols)
    case KeyboardSymbols => setKeyboard(defaultKeyboardType)
    case _ => setKeyboard(defaultKeyboardType)
  }

  def toggleShift(): Boolean = currentKeyboardType match {
    case t: KbdView =>
      viewsMap.get(t) exists { v =>
        val kv = v.asInstanceOf[KeyboardView]
        kv.setShifted(!kv.isShifted)
        kv.isShifted
      }
    case _ => false
  }


  def swapTsuIm(): Unit = currentKeyboardType match {
    case KeyboardTsuimInitials => setKeyboard(KeyboardTsuimFinals)
    case KeyboardTsuimFinals => setKeyboard(KeyboardTsuimInitials)
    case _ => ()
  }


  def lookupDictionary(input: String, strict: Boolean): Unit = {
    def lookup(word: String): Option[String] = {

      val filename = s"koktai/${word.map(_.toInt).mkString("_")}"
      Log.v("Lookup for", filename)
      val assets = context.getAssets
      try {
        val src = Source.fromInputStream(assets.open(filename))
        val txt = src.getLines() mkString ("\n")
        src.close()
        Some(txt)
      }
      catch {
        case e:IOException =>
          if (!strict && word.length > 1)
            lookup(word.substring(1))
          else {
            Log.v("lookup", "NotFound")
            None
          }
      }
    }
    for (view <- viewsMap.get(DictionaryView)) {
      lookup(input) match {
        case Some(txt) =>
          Log.v("dico", txt)
          view.asInstanceOf[TextView].setText(txt)
        case None => ()
      }
    }
  }
}
