package fr.magistry.taigime2

import android.content.{Context, ContextWrapper}
/**
  * Created by pierre on 8/16/16.
  */
abstract class Resources(context: Context) extends ContextWrapper(context) {
  def rGetInt = getResources().getInteger(_)
  def rGetString = getResources().getString(_: Int)
}
