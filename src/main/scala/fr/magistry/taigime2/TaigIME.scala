package fr.magistry.taigime2

import android.content.Context
import android.inputmethodservice.{InputMethodService, Keyboard, KeyboardView}
import android.os.Handler
import android.util.Log
import android.view.{KeyEvent, View}
import android.view.inputmethod.{InputConnection, InputMethodManager}
import fr.magistry.taigime2.Message.Config


/**
  * trait to store all application specific code to lighten the ImeService class and keep the code more readable
  * (but it should only be used by the class extending InputMethodServive)
  *
  * Created by pierre on 8/15/16.
  */


sealed abstract class KeyboardLayout(id: String)

case class LettersKeyboard(id: String, keyboard: Keyboard) extends KeyboardLayout(id)
case class SymbolsKeyboard(id: String, keyboard: Keyboard) extends  KeyboardLayout(id)

trait TaigIME  extends KeyboardView.OnKeyboardActionListener  {

  val tsuimInitials = Set('ㆠ','ㄌ','ㆣ','ㆡ','ㆢ','ㄫ','ㄅ','ㄉ','ㄍ','ㄗ','ㄐ','ㄆ','ㄊ','ㄎ','ㄘ','ㄑ','ㄇ','ㄋ','ㄏ','ㄙ','ㄒ')

  var inputMethodManager: Option[InputMethodManager] = None


  var keyboardViewManager: Option[KeyboardViewManager] = None
  var candidateView: Option[CustomCandidateView] = None
  var imeData: Option[ImeDatabase] = None
  var imeService: Option[InputMethodService] = None

  var currentIsSymbols = false
  var composer: Option[Composer] = None
  var fuzzy = true
  var preferPOJ = false


  def setConfig(): Unit = {
    for (is <- imeService;
         co <- composer) {
      preferPOJ = is.getBaseContext.getSharedPreferences("TaigIME2",0).getBoolean("POJorTL", false)
      fuzzy = is.getBaseContext.getSharedPreferences("TaigIME2",0).getBoolean("fuzzyLookup", true)
      val conn = is.getCurrentInputConnection()
      if (conn != null)
        co.updateConnection(conn)
      else
        Log.v("TIME", "InputConn is null")
    }
  }

  var composingTextUpdated = false
  private val handler = new Handler()
  private val updatingThread = new Runnable {
    override def run(): Unit = {
      if (composingTextUpdated) {
        Log.v("IME", "timer")
        composingTextUpdated = false
        for (db <- imeData;
             cv <- candidateView;
            cp <- composer) {
          val candidates = db.computeCandidateList(cp.getComposingText, cp.getLeftContext(), fuzzy)
          cv.updateCandidates(candidates)
        }
      }
      handler.postDelayed(this, 200)
    }
  }
  updatingThread.run()


  var movingCursor = false
  var ignoreNextMove = false
  /**
    * main control of the application behaviour
    * Any complex action should send a message to this function in order to dispatch effects
    *
    * @param msg
    */
  def message(msg: Message.Msg): Unit = {
    Log.i("moving?", s"$movingCursor")
    Log.i("IME", "message:  " + msg.toString)
    Log.v("IME", "loading: " + isLoading.toString)
    Log.v("IME", "default: " + keyboardViewManager.map(_.defaultKeyboardType.toString).getOrElse("???"))
    msg match {
      case Message.LoadingStarted =>
        keyboardViewManager.foreach(km => km.setKeyboard(km.LoadingView))
      case Message.LoadingFinished => keyboardViewManager.foreach(km => km.setKeyboard(km.defaultKeyboardType))
      case Message.ChangeKeyboard => if (!imeData.map(_.loading).getOrElse(true)) keyboardViewManager.foreach(_.nextKeyboard())
      case Message.SwapWithSymbols => keyboardViewManager.foreach(_.swapSymbols())
      case Message.DisplayConfig => if (!imeData.map(_.loading).getOrElse(true)) keyboardViewManager.foreach(km => km.setKeyboard(km.ConfigView))
      case Message.DisplayDict => if (!imeData.map(_.loading).getOrElse(true)) keyboardViewManager.foreach(km => km.setKeyboard(km.DictionaryView))

      case Message.Key(k) =>
        Log.d("IME", s"Message.Key($k)")
        for( c <- composer) {
          c.startBatchEdit()
          c.inputChar(k)
          if (tsuimInitials.contains(k))
            message(Message.SwapTsuIm)
          movingCursor = (c.composingLength == 0)
          c.endBatchEdit()
        }

      case Message.ComposingTextUpdated(text, context) =>
        movingCursor = false
        composingTextUpdated = true
        for (cv <- candidateView) {
          cv.updateCandidates(Nil)
        }

      case Message.ConfigChanged(Config(newFuzzy, newPoj)) =>
        fuzzy = newFuzzy
        preferPOJ = newPoj
      case Message.SelectCandidate(selected: CompositionItem) =>
        movingCursor = false

        for (
          c <- composer;
          km <- keyboardViewManager;
          cv <- candidateView;
          db <- imeData
        ) {
          c.startBatchEdit()
          c.selectCandidate(selected) // apply selection
          if (km.currentKeyboardType == km.KeyboardTsuimFinals)
            message(Message.SwapTsuIm) // reset zhuyin keyboard to initials
          // predict possible next word
          //val candidates = db.computeCandidateList(c.compi, c.getLeftContext(), fuzzy)
          //cv.updateCandidates(candidates)
          ignoreNextMove = true
          c.endBatchEdit()
        }

      case Message.SelectCorrection(text, source) =>
        for (comp <- composer) {
          Log.v("IME", "select correction")
          comp.startBatchEdit()
          comp.correct(source.length, text)
          message(Message.ComposingTextUpdated(text, comp.getLeftContext()))
          ignoreNextMove = true
          comp.endBatchEdit()
        }

      case Message.KeyCancel => ()
      case Message.KeyDone =>
        Log.d("IME","KeyDone")
        composer.foreach(_.commitComposition())
        imeService.foreach(_.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER))
      case Message.KeyDelete =>
        movingCursor = false
        for(c <- composer) {
          c.startBatchEdit()
          c.deleteChar()
          c.endBatchEdit()
        }
      case Message.KeyShift =>
        for (kvm <- keyboardViewManager ;
          c <- composer) {
          c.shifted = kvm.toggleShift
        }
      case Message.SwapTsuIm => keyboardViewManager.foreach(_.swapTsuIm())
      case Message.CursorMoved(oldStart, oldEnd, start, end) =>
        if (ignoreNextMove)
          ignoreNextMove = false
        else {
          if (movingCursor || start != end || math.abs(oldStart - start) > 1) {
            movingCursor = true
            for (
              comp <- composer;
              cv <- candidateView;
              db <- imeData;
              km <- keyboardViewManager
            ) {

              if (comp.composingLength > 0) {
                comp.commitComposition()
              }
              val candidates =
                if (end != start)
                  db.computeCorrections(comp.getSelection, true)
                else
                  db.computeCorrections(comp.getLeftContext(), false)
              cv.updateCandidates(candidates)
              if (km.currentKeyboardType == km.DictionaryView) {
                if (end != start)
                  km.lookupDictionary(comp.getSelection, true)
                else
                  km.lookupDictionary(comp.getLeftContext(), false)
              }
            }
          }
        }
    }
  }

  def messageFromThread(msg: Message.Msg): Unit = {
    keyboardViewManager.map(_.currentView.map(_.post(new Runnable {
      override def run(): Unit = {
        message(msg)
      }
    })))
  }

  /**
    *
    * @return the state of the database
    */
  def isLoading = imeData.map(_.loading).getOrElse(false)


  /**
    *
    * ask to replace the main InputView of the IME
    *
    * @param view
    */
  def setView(view: View) = {
    Log.v("GNU",view.toString)
    imeService.foreach(s => {
      Log.v("TaigIME", view.toString)
      s.setInputView(view)
      view.postInvalidate()
    })
  }



  // OnKeyboardActionListeners overrides :
  override def swipeRight(): Unit = {
    // TODO: (maybe nothing)
  }

  override def swipeLeft(): Unit = {
    // TODO: (maybe nothing)
  }


  override def onKey(i: Int, ints: Array[Int]): Unit = {
    for (is <- imeService) {
      val keySwapTsuIm = is.getResources.getInteger(R.integer.KeySwapTsuIm)
      i match {
        case Keyboard.KEYCODE_MODE_CHANGE => message(Message.SwapWithSymbols)
        case Keyboard.KEYCODE_SHIFT => message(Message.KeyShift)
        case Keyboard.KEYCODE_DELETE => message(Message.KeyDelete)
        case Keyboard.KEYCODE_DONE => message(Message.KeyDone)
        case Keyboard.KEYCODE_CANCEL => message(Message.KeyCancel)
        case 10 => message(Message.KeyDone)
        case k if k == keySwapTsuIm => message(Message.SwapTsuIm)
        case chr =>
          message(Message.Key(chr.toChar))
          for (km <- keyboardViewManager)
            if (chr == 45 && km.currentKeyboardType == km.KeyboardTsuimFinals)
              message(Message.SwapTsuIm)
      }

    }
  }



  override def onPress(i: Int): Unit = {
    // TODO: implements
  }

  override def onRelease(i: Int): Unit = {
    // TODO: implements
  }

  override def swipeUp(): Unit = {
    // TODO: implements
  }

  override def swipeDown(): Unit = {
    // TODO: implements
  }

  override def onText(charSequence: CharSequence): Unit = {
    // TODO: implements
  }
}
