package sastut

import java.io.File
import nak.data.Example


/**
 * A simple case class to store information associated with a Tweet.
 */
case class Tweet(val tweetid: String, val username: String, val content: String)

trait DatasetReader extends (File => Seq[Example[String,Tweet]])

class ImdbIndexedDatasetReader extends DatasetReader {
  
  // So this isn't the way you'd do it in general, but it makes it easy
  // to fit the indexed data into the existing classifier methods...
  def apply(file: File): Seq[Example[String,Tweet]] = {

    // Get the vocab so we can map the numbers back to words. Brittle, but 
    // it works for these purposes.
    val vocab = io.Source.fromFile(new File(file.getParentFile.getParentFile,"imdb.vocab")).getLines.toIndexedSeq  

    for (line <- io.Source.fromFile(file).getLines.toSeq) yield {
      val ratingRaw :: countsRaw = line.split(" ").toList
      val rating = ratingRaw.toInt

      val text = (for { 
	wordCountString <- countsRaw
	Array(wordId, wordCount) = wordCountString.split(":").map(_.toInt)
	word <- Array.fill(wordCount)(vocab(wordId))
      } yield word).mkString(" ")

      val label = if (rating > 5) "positive" else "negative"
      Example(label, Tweet("ignore","none",text))
    } 
  }
}

class ImdbRawDatasetReader extends DatasetReader {

  val mapLabel = Map("neg" -> "negative", "pos" -> "positive")

  def apply(topdir: File): Seq[Example[String,Tweet]] = {
    for {
      dir <- topdir.listFiles.toSeq.filter(_.isDirectory)
      dirLabel = dir.getName
      if dirLabel == "pos" | dirLabel == "neg"
      file <- dir.listFiles.toSeq
    } yield {
      val label =  mapLabel(dirLabel)
      val fileSource = io.Source.fromFile(file)
      val text = fileSource.mkString
      fileSource.close
      Example(label, Tweet(file.getName, "none", text))
    }

  }
}

/**
 * Read in a polarity labeled dataset from XML.
 */
class XmlDatasetReader extends DatasetReader {

  import scala.xml._

  // Allow NodeSeqs to implicitly convert to Strings when needed.
  implicit def nodeSeqToString(ns: NodeSeq) = ns.text

  def apply(file: File): Seq[Example[String, Tweet]] = {
    val itemsXml = XML.loadFile(file)

    (itemsXml \ "item").flatMap { itemNode =>
      val label: String = itemNode \ "@label"

      // We only want the positive, negative and neutral items.
      label match {
        case "negative" | "positive" | "neutral" =>
          // Note: the target is: (itemNode \ "@target").text,
          val tweet = Tweet(itemNode \ "@tweetid", itemNode \ "@username", itemNode.text.trim)

          // Uncomment these lines and comment out the Some(Example(label,tweet)) line if 
          // you want to just train on pos/neg examples from emoticons.
          //if (file.getPath == "data/emoticon/train.xml" && label == "neutral") None
          //else Some(Example(label,tweet))

          Some(Example(label,tweet))
            
        case _ => None
      }
    }
  }

}

object StanfordToXmlConverter {

  def main(args: Array[String]) {

    println("<dataset>")
    for (line <- io.Source.fromFile(args(0)).getLines) {
      val Array(polarityIndex, id, date, target, user, tweet) = line.split(";;")
      val polarity = polarityIndex match {
        case "0" => "negative"
        case "2" => "neutral"
        case "4" => "positive"
      }
   
      val itemXml = 
        <item tweetid={id} label={polarity} target={target} username={user}>
          <content>{tweet}</content>
        </item>
      
      println(itemXml)

    }
    
    println("</dataset>")

  }

}


object EmoticonToXmlConverter {

  import java.io.File

  def main(args: Array[String]) {
    val emoticonDir = new File(args(0))
    val files = List("happy.txt", "sad.txt", "neutral.txt").map(f=>new File(emoticonDir,f))
    
    val labels = List("positive","negative","neutral")
    println("<dataset>")
    for ((file,label) <- files.zip(labels))
      getItems(file,label).foreach(println)
    println("</dataset>")
  }

  val EmoItemRE = """^(\d+)\t(\d+)\t(.*)$""".r

  def getItems(file: File, label: String) = {
    for (line <- io.Source.fromFile(file).getLines) yield {
      //println("*************")
      //println("--- " + line.split("\\t").foreach(println))
      val EmoItemRE(tweetid, userid, tweet) = line
      <item tweetid={tweetid} label={label} target={"unknown"} username={userid}>
        <content>{tweet}</content>
      </item>
    }
  }

}
