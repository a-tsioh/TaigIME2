package fr.magistry.taigime2

import android.content.Context
import android.graphics.Typeface
import android.inputmethodservice.{InputMethodService, Keyboard, KeyboardView}
import android.util.Log
import android.view.{KeyEvent, LayoutInflater, View}
import android.view.inputmethod.{CursorAnchorInfo, EditorInfo, InputConnection, InputMethodManager}
import android.widget.FrameLayout
import fr.magistry.taigime2.Message.KeyDelete

import scala.collection.JavaConverters._



/**
  * Main Service of the IME
  *
  */
class ImeService extends InputMethodService with TaigIME {

  //  setTheme(R.style.AppTheme)
//  override def onInitializeInterface() = {
//    Log.v("IME","initInterface")
//    keyboardViewManager foreach (_.initialize())
//
//  }

  override def onCreate() = {
    super.onCreate()
    imeService = Some(this)
    inputMethodManager =  getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager] match {
      case null => None
      case mngr =>
        Some(mngr)
    }
    val data = new ImeDatabase(getBaseContext(), "taigime.db", 3, this)
    imeData = Some(data)

    keyboardViewManager = Some(new KeyboardViewManager(getBaseContext(), this))
    keyboardViewManager.map(_.initialize())

    composer = Some(new Composer(None, data, this))
  }

  override def onCreateInputView(): View = {
    Log.v("IME", "onCreateInputView")
    keyboardViewManager match {
      case None => Log.i("IME","!!! view null")
        null
      case Some(km) => km.currentView match {
        case None =>
          Log.i("IME","!!! view null")
          null
        case Some(v) =>
          v.getParent() match {
            case p: FrameLayout =>
              // view already exists (orientation change ?
              // size should be re-computed
              Log.v("IME", p.toString)
              p.removeView(v)
              km.initialize()
              km.view
            case _ => ()
          }
          v
      }
    }
    //keyboardViewManager.map(_.view).orNull
  }

  override def onCreateCandidatesView(): View = {

    val cView = new CustomCandidateView(this)
    candidateView = Some(cView)
    imeData.foreach(cView.setImeData)
    cView.setImeService(this)
    setCandidatesViewShown(true)
    //imeData.map(_.db.isOpen) // to force lazy loading
    cView

  }

  override def onStartInput(attribute: EditorInfo, restarting: Boolean): Unit = {
    super.onStartInput(attribute, restarting)
    Log.v("IME", "onStartInput")
    /**
      * initialisation goes here
      */

    // TODO: deal with input method subtype (ie. tell KeyboardManager to set entry key action)
    //if (!restarting) {
    setConfig()
    //}

  }

  override def onStartInputView(attribute: EditorInfo, restarting: Boolean): Unit = {
    super.onStartInputView(attribute, restarting)
    Log.v("IME", "onStartInputView")
  }

  override def onFinishInput(): Unit = {
    super.onFinishInput()
    Log.v("IME", "onFinishInput")
  }

  override def onUpdateSelection(oldStart: Int, oldEnd: Int,  start:Int, end: Int, candStart: Int, candEnd: Int): Unit = {
    Log.v("IME", s"select change $oldStart -> $start ; $oldEnd -> $end ($candStart,$candEnd)")
    message(Message.CursorMoved(oldStart, oldEnd, start,end))

  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    Log.v("IME", "onDestroy")
  }

}