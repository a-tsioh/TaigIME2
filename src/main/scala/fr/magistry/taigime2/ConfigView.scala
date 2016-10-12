package fr.magistry.taigime2

import Message.Config

import android.content.Context
import android.util.Log
import android.view.{LayoutInflater, View}
import android.view.View.OnClickListener
import android.widget.{Button, CheckBox, Switch}




/**
  * Created by pierre on 8/25/16.
  */
class ConfigView(context: Context, ime: TaigIME)  {
  val keyFuzzyLookup = "fuzzyLookup"
  val keyPojOrTL = "POJorTL"

  val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  val view = inflater.inflate(R.layout.config, null)
  val cbFuzzy = view.findViewById(R.id.cbFuzzy).asInstanceOf[CheckBox]
  val swRomanization = view.findViewById(R.id.swPOJTL).asInstanceOf[Switch]
  setValues()

  view
    .findViewById(R.id.acceptConfig)
    .asInstanceOf[Button]
    .setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        context.getSharedPreferences("TaigIME2",0)
          .edit()
          .putBoolean(keyFuzzyLookup, cbFuzzy.isChecked)
          .putBoolean(keyPojOrTL,swRomanization.isChecked)
          .apply()
        Log.v("IME","validate config")
        ime.message(Message.ConfigChanged(Config(cbFuzzy.isChecked, swRomanization.isChecked)))
        ime.message(Message.ChangeKeyboard)
      }
    })

  def setValues(): Unit = {
    val prefs = context.getSharedPreferences("TaigIME2", 0)
    val fuzzy = prefs.getBoolean(keyFuzzyLookup,true)
    val pojtr = prefs.getBoolean(keyPojOrTL, false)
    cbFuzzy.setChecked(fuzzy)
    swRomanization.setChecked(pojtr)
  }


}

