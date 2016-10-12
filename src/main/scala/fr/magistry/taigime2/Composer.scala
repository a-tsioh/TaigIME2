package fr.magistry.taigime2

import android.util.Log
import android.view.inputmethod.InputConnection

/**
  * Created by pierre on 8/20/16.
  */
class Composer(private var connection: Option[InputConnection], imeData: ImeDatabase, ime: TaigIME) {

  val Separators = Set(' ', ',', '.', '!', '?', '\n')
  var shifted = false

  def updateConnection(inputConnection: InputConnection): Unit = {
    connection = Some(inputConnection)
    updateComposingText(_.length = 0, true)
  }
  private val composingText = new StringBuilder()
  /**
    * Fonction to be called to make changes to the composingText
    * with update of the UI
    *
    *
    */
  def updateComposingText(edit: StringBuilder => Unit, cancelMessage: Boolean=false): Unit ={
    edit(composingText)
    for (inputConnection <- connection) {
      inputConnection.setComposingText(composingText, 1)
      if(!cancelMessage)
        ime.message(Message.ComposingTextUpdated(composingText.toString(), getLeftContext()))
    }
  }

  def commitComposition(): Unit = {
    if (composingText.length >0) {
      for (inputConnection <- connection) {
        inputConnection.commitText(composingText, 1)
        updateComposingText(_.length = 0)
      }
    }
  }

  def selectCandidate(candidate: CompositionItem) = {
    for (ic <- connection) {
      ic.commitText(candidate.label, 1)
      val newCompText = composingText.substring(candidate.consumedLength)
      updateComposingText(sb => {
        sb.length = 0
        sb.append(newCompText)
      })
    }
  }

  def startBatchEdit(): Unit = {
    connection.foreach(_.beginBatchEdit())
  }

  def endBatchEdit(): Unit = {
    connection.foreach(_.endBatchEdit())
  }

  def inputChar(c: Char): Unit = c match {
    case sep if (Separators.contains(sep)) =>
      commitComposition()
      for (ic <- connection) {
        ic.commitText(c.toString,1)
      }
    case tone if Set('1', '2', '3', '4', '5', '7', '8').contains(tone) =>
      composingText.append(tone)
      imeData.normalizeRomanization(composingText.toString(), ime.preferPOJ) match {
        case Some(norm) =>
          composingText.length = 0
          composingText.append(norm)
          //ime.message(Message.SwapWithSymbols)
        case None => ()
      }
      updateComposingText(_ => ())
    case _ =>
      if (shifted)
        updateComposingText(_.append(c.toUpper))
      else
        updateComposingText(_.append(c))

  }

  def deleteChar(): Unit = {
    if (composingText.length > 0) {
      updateComposingText(_.length -= 1)
    }
    else
      connection.foreach(_.deleteSurroundingText(1,0))
    ime.message(Message.ComposingTextUpdated(composingText.toString(), getLeftContext()))
  }

  def getComposingText = {composingText.toString()}

  /**
    * retrieve left context to feed the language model
    *
    * @return string from the begining of the current line up to the cursor (or "")
    */
  def getLeftContext(): String = {
    connection.map(ic =>{
      val fullContext = ic.getTextBeforeCursor(100, 0) match {
        case null => ""
        case notNull => notNull.toString
      }
      val composingLength = composingText.length
      val lastLine = fullContext.split('\n').lastOption.getOrElse("")
      lastLine.substring(0, lastLine.length - composingLength).split(Separators.toArray).lastOption.getOrElse("")
    }).getOrElse("")
  }

  def getSelection(): String = {
    connection.map(ic => {
      ic.getSelectedText(0).toString //0 to ignore style
    }).getOrElse("")
  }

  def correct(length: Int, correction: String): Unit = {
    for (ic <- connection) {
      ic.deleteSurroundingText(length, 0)
      composingText.length = 0
      composingText.append(correction)
      ic.setComposingText(correction, 1)
    }

  }

  def composingLength = composingText.length
}
