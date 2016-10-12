package fr.magistry.taigime2

/**
  * Created by pierre on 8/20/16.
  */
object Message {
  case class Config(fuzzy: Boolean, poj: Boolean)

  sealed abstract class Msg
  case object LoadingStarted extends Msg
  case object LoadingFinished extends Msg
  case object ChangeKeyboard extends Msg
  case object SwapTsuIm extends Msg
  case object SwapWithSymbols extends  Msg
  case class ComposingTextUpdated(text: String, context: String) extends Msg
  case class SelectCandidate(compositionItem: CompositionItem) extends Msg
  case class SelectCorrection(text: String, source: String) extends Msg
  case object KeyShift extends Msg
  case object KeyDone extends Msg
  case object KeyCancel extends Msg
  case object KeyDelete extends Msg
  case class Key(char: Char) extends Msg
  case class CursorMoved(oldStart: Int, oldEnd: Int, start: Int, end: Int) extends Msg
  case object DisplayConfig extends Msg
  case class ConfigChanged(cfg: Config) extends Msg
  case object DisplayDict extends Msg

}
