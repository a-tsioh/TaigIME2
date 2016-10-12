package fr.magistry.taigime2

import android.content.Context
import android.database.Cursor
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.os.AsyncTask
import android.util.Log
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import fr.magistry.nlp
import fr.magistry.nlp.LanguageModeling.{ArpaReader, BackoffLM}
import fr.magistry.nlp.lexicon.ScoredWordList
import fr.magistry.nlp.scripts.Han.{TokenHan, TokenHanSeq}
import fr.magistry.nlp.scripts.{Initial, Rime, Syl, Tone}
import fr.magistry.nlp.segmentation
import fr.magistry.nlp.tokenization.Token
import fr.magistry.utils.taigi.{CompositionItem => _, CorrectionItem => _, _}
//import fr.magistry.taigime.{CompositionItem, CorrectionItem}

import scala.io.Source
/**
  * Created by pierre on 6/28/16.
  */
class ImeDatabase(context: Context, name: String, version: Int, imeService: TaigIME) extends SQLiteAssetHelper(context, name, null, version) {
  val LMOrder = 3
  val ElOrder = 4
  setForcedUpgrade()

  def cached[A,R](size: Int, f: A => R): (A => R) = {
    val cache = scala.collection.mutable.HashMap.empty[A,R]
    val order = scala.collection.mutable.Queue.empty[A]
    def fun(arg:A):R = {
      cache.get(arg) match {
        case Some(r) => r
        case None =>
          //TODO: suboptimal, should use a cache tree
          if (order.size > size) {
            cache.remove(order.dequeue())
          }
          val r = f(arg)
          cache += arg -> r
          order.enqueue(arg)
          r
      }
    }
    fun
  }


  var loading = false

  // Thread to load the data in the background
  // if it
  val loadingThread = new Thread(new Runnable {
    override def run(): Unit = {
      loading = true
      imeService.messageFromThread(Message.LoadingStarted)
      val db = getReadableDatabase
      loading = false
      db.close()
      imeService.messageFromThread(Message.LoadingFinished)

      }
  })
  loadingThread.start()

  lazy val db = {
    val result = getReadableDatabase
    loading = false
    result
  }

  val poj = new POJ(JavaNormalizer)
  val trs = new TRS(JavaNormalizer)
  val bpm = new BPM(JavaNormalizer)
  val tokenizer = new TgTokenizer(poj)


  val AutonomyDict = new ScoredWordList[Seq[Token], Double] {

    val cachedGetScore = cached[(Seq[Token]),Option[Double]](1000,
      {case (word) =>
        val ngram = word.map(_.form) mkString "\ue000"
        val cursor = db.rawQuery("SELECT A FROM Autonomy WHERE ngram = ?", Array(ngram))
        val score =
          if(cursor.moveToNext())
            Some(cursor.getFloat(0).toDouble)
          else
            None
        cursor.close()
        score
      })

    override def getScore(word: Seq[Token]): Option[Double] = {
      cachedGetScore(word)
    }

    override def load(source: Source): Unit = {
      throw new NotImplementedError()
    }

    override def contains(key: Seq[Token]): Boolean = getScore(key).nonEmpty
  }


  val cachedP = cached[Seq[String],Option[Double]](1000, getPofHanlo)
  val cachedB = cached[Seq[String],Option[Double]](1000, getBofHanlo)
  val LM = new BackoffLM(cachedP, cachedB) //getPofHanlo, getBofHanlo)

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
   // todo
  }

  /**
    * Helper function to extracts results to a simple query
    * when we just want a List[String]
    *
    * @param cursor
    * @return
    */
  protected def consumeCursorAsStrings(cursor: Cursor): List[String] = {
    def fillList(buffer: List[String]): List[String] = {
      if (cursor.moveToNext())
        fillList(cursor.getString(0) :: buffer)
      else
        buffer
    }
    fillList(Nil)
  }

  protected def consumeCursorAsStringsCouple(cursor: Cursor): List[(String,String)] = {
    def fillList(buffer: List[(String,String)]): List[(String,String)] = {
      if (cursor.moveToNext())
        fillList((cursor.getString(0), cursor.getString(1)) :: buffer)
      else
        buffer
    }
    fillList(Nil)
  }

  protected def consumeCursorAsStringsFloats(cursor: Cursor): Stream[(String, Float)] = {
    if (cursor.moveToNext())
      (cursor.getString(0), cursor.getFloat(1)) +: consumeCursorAsStringsFloats(cursor)
    else
      Stream.empty
  }


  def getHanloTailoOfIpa(ipa: String, glob: Boolean=false): List[(String,String)] = {
    val cursor = db.rawQuery("SELECT hanlo, tailo FROM Conversions WHERE ipa %s ?".format(if (glob) "GLOB" else "="), Array(ipa))
    val result = consumeCursorAsStringsCouple(cursor)
    cursor.close()
    result
  }



  def getTailoOfHanlo(hanlo: String): List[String] = {
    val cursor = db.rawQuery("SELECT tailo FROM Conversions WHERE hanlo = ?", Array(hanlo))
    val result = consumeCursorAsStrings(cursor).take(20) //TODO: streamify
    cursor.close()
    result
  }

  def getPofHanlo(hanlo: Seq[String]): Option[Double] = {
    val cursor = db.rawQuery("SELECT P FROM LmHanlo WHERE hanlo = ?", Array(hanlo mkString " "))
    val p =
      if(cursor.moveToNext())
        Some(cursor.getFloat(0).toDouble)
      else {
        // fallback to <unk> in case of missing unigram
        if (hanlo.length == 1 && hanlo(0) != "<unk>")
          getPofHanlo(List("<unk>"))
        else None
      }
    cursor.close()
    p
  }

  protected def getBofHanlo(hanlo: Seq[String]): Option[Double] = {
    val cursor = db.rawQuery("SELECT B FROM LmHanlo WHERE hanlo = ?", Array(hanlo.mkString(" ")))
    val p =
      if(cursor.moveToNext())
        Some(cursor.getFloat(0).toDouble)
      else {
        if (hanlo.length == 1 && hanlo(0) != "<unk>")
          getBofHanlo(List("<unk>"))
        else None
      }
    cursor.close()
    p
  }

  protected def getHanloFromPrefix(hanlo: Seq[String]): List[String] = {
    if (!loading) {
      Log.v("DB-pfx:", hanlo mkString " / ")
      val smallerHanlo = hanlo.drop(math.max(0, hanlo.length - LMOrder)) // cp only LMOrder last tokens
      val cursor = db.rawQuery("SELECT hanlo, P FROM lmHanlo WHERE hanlo GLOB ? ORDER BY P DESC", Array((smallerHanlo  mkString " ") + " *"))
      val prefixLength = (smallerHanlo map {
        _.length
      }).foldLeft(0)(_ + _ + 1)
      val result = consumeCursorAsStringsFloats(cursor).filter({case (hl:String,_) => (!hl.contains("</s>")) && hl.count(_ == ' ') == smallerHanlo.length}).take(20).toList
      cursor.close()
      if (result.isEmpty && smallerHanlo.length > 1) {
        getHanloFromPrefix(smallerHanlo.tail)
      }
      else {
        Log.v("DB-pfx-result", result map { couple => "%s %f".format(couple._1, couple._2) } mkString " / ")
        result map {
          _._1.substring(prefixLength).replace(" ", "")
        }
      }
    }
    else
      Nil
  }

  /**
    * Return a clean romanization from a input romanized with numbers or in zhuyin
    * (TODO: Zhuyin)
    *
    * @param input
    * @return Some normalized string if parsing succeeded
    */
  def normalizeRomanization(input: String, inputIsPOJ: Boolean): Option[String] = {
    val syls = if (inputIsPOJ)
      poj.toIPA(poj.parseWord(input))
    else
      trs.toIPA(trs.parseWord(input))
    if (syls.isEmpty)
      None
    else
      (if (imeService.preferPOJ)
        Some(poj.toString(poj.ofIPA(syls)))
      else
        Some(trs.toString(trs.ofIPA(syls)))
      ).map(result => {
      if (input(0).isUpper)
        result.capitalize
      else
        result
    })
  }


  /**
    * compute all possible conversions of the current composition
    *
    * @param input
    * @return
    */
  def getConversions(input: String, fuzzyMatches: Boolean): Set[(String, Int)] = {
    def getSylsLength(syls:List[Syl]): Int = {
      syls.foldLeft(0)((total, s) => {
        total + s.i.s.length + s.r.n.length + s.r.f.length + s.sep.length
      })
    }

    val (syls,romanization) = bpm.parseWord(input) match {
      case Nil => // parse as zhuyin failed
        if (imeService.preferPOJ)
          (poj.parseWord(input), poj)
        else
          (trs.parseWord(input), trs)
      case something => (something, bpm)
    }
    if (syls.nonEmpty) {
      val tailo = romanization.toIPA(syls)
      Log.v("IME", tailo.toString())
      if (fuzzyMatches) {
        val tonelessIpa = tailo map { case Syl(sep, Initial(i), Rime(n, f), Tone(t)) => "%s.%s.%s.%s".format(i, n, f, if (t == 1 || t == 4) "?" else t.toString) }
        (tailo.length to 1 by -1).foldLeft(Set.empty[(String,Int)])((result, i) => {
          val query = tonelessIpa.slice(0,i) mkString " "
          val couples = getHanloTailoOfIpa(query, glob = true).map({case (hl, tl) => (hl, getSylsLength(syls.take(i)))})
          result ++ couples
        })
      }
      else {
        val ipa = tailo map { case Syl(sep, Initial(i), Rime(n, f), Tone(t)) => "%s.%s.%s.%d".format(i, n, f, t) } mkString " "
        getHanloTailoOfIpa(ipa).map({case (hl, tl) => (hl,getSylsLength(syls))}).toSet
      }
    }
    else {
      if (input.length == 0)
        Set.empty
      else
        getConversions(input.substring(0,input.length -1),fuzzyMatches)
    }
  }


  def tokenize(input:String): Seq[Token] = {
    tokenizer.linearize(tokenizer.tokenize(input))
  }

  def generateMask(input: Array[Token]): Array[segmentation.MaskValue] ={
    val nbests = (input.length / 2) + 1
    val segs = segmentation.maximizeScore(input, AutonomyDict, ElOrder, nbests)
    val bestScore = segs.head.score
    val minScore = math.min(bestScore, bestScore * 0.95)
    val results = segs.filter(_.score >= minScore)
    segmentation.createMask(results)
  }

  /**
    * rank conversions candidates according to the context
    *
    * @param conversions
    * @param context
    * @param cut
    * @return
    */
  def rankCandidates(conversions: Set[(String, Int)], context: String, cut: Int=50): List[(String, Int)] = {
    Log.v("ranking...", conversions mkString("; "))

    conversions.map({
      case (candidate, size) =>
        val sentence = context + candidate
        val tokens = tokenizer.linearize(tokenizer.tokenize(sentence))
        val score = tokens.map({
          case TokenHanSeq(_, subseq) =>
            val mask = generateMask(subseq.toArray)
            val result = segmentation.applyLM(subseq.map(_.form).toArray, LM, LMOrder, 4, Some(mask), false)
            Log.v("scoring...", s"${result.score} | ${result.words mkString " "}")
            result.score
          case TokenHan(form) =>
            segmentation.applyLM(Array(form),LM, LMOrder, 4, None, false).score
          case _ => 0.0
      }).sum
      (score, candidate, size)
    }).toList.sortBy(t => (t._3,t._1)).map(t => (t._2, t._3)).reverse
  }


  def computeCandidateList(input:String, context:String, fuzzy:Boolean, limit: Int=30): List[CompositionItem] = {
    val candidates: Set[(String, Int)] = input match {
      case "" =>
        if (context == "")
          Set.empty[(String, Int)]
        else {
          val toks = tokenize(context) flatMap {
            case TokenHanSeq(_, subseq) => subseq
            case tok => List(tok)
          }
          val mask = generateMask(toks.toArray)
          val seg = segmentation.applyLM(toks.map(_.form).toArray, LM, LMOrder, 4, Some(mask), false).words
          Log.v("comp next", seg.mkString(" "))
          getHanloFromPrefix(seg).map((_,0)).toSet
        }
      case _ => getConversions(input, fuzzy)
    }
    rankCandidates(candidates, context).map({ case (c, size) => CompositionItem(normalizeRomanization(c, false).getOrElse(c), size) })
  }


  def computeCorrections(input: String, strict: Boolean): List[CorrectionItem] = {
    if (strict)
      getTailoOfHanlo(input).map(c => CorrectionItem(normalizeRomanization(c, false).getOrElse(c), input))
    else {
      ((input.length - 1) to 0 by -1).foldLeft(Nil:List[CorrectionItem])((acc,start) => {
        val substr = input.substring(start)
        getTailoOfHanlo(substr).map(c => CorrectionItem(normalizeRomanization(c, false).getOrElse(c), substr)) ++ acc
      })
    }

  }


}
