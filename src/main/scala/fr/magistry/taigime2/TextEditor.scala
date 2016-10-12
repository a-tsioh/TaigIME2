package fr.magistry.taigime2

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.TextView

/**
 * Created by pierre on 8/25/16.
 */
class TextEditor extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.texteditor)
    findViewById(R.id.TEeditText).asInstanceOf[TextView].setTypeface(Typeface.createFromAsset(getAssets,"fonts/MOEDICT.ttf"))
  }
}
