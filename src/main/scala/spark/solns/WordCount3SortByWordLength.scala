package spark.solns

import spark.util.{CommandLineOptions, Timestamp}
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

/**
 * Second, simpler implementation of Word Count.
 * Solution to the exercise that orders by word length. We'll add the length
 * to the output, as the 2nd field after the word itself.
 */
object WordCount3SortByWordLength {
  def main(args: Array[String]) = {

    // I extracted command-line processing code into a separate utility class,
    // an illustration of how it's convenient that we can mix "normal" code
    // with "big data" processing code. 
    val options = CommandLineOptions(
      this.getClass.getSimpleName,
      CommandLineOptions.inputPath("data/kjvdat.txt"),
      CommandLineOptions.outputPath("output/kjv-wc3-word-length.txt"),
      CommandLineOptions.master("local"),
      CommandLineOptions.quiet)

    val argz = options(args.toList)

    val sc = new SparkContext(argz("master").toString, "Word Count (3)")

    try {
      // Load the King James Version of the Bible, then convert 
      // each line to lower case, creating an RDD.
      val input = sc.textFile(argz("input-path").toString).map(line => line.toLowerCase)

      // Cache the RDD in memory for fast, repeated access.
      // You don't have to do this and you shouldn't unless the data IS reused.
      // Otherwise, you'll use RAM inefficiently.
      input.cache

      // Split on non-alphanumeric sequences of character as before. 
      // Rather than map to "(word, 1)" tuples, we treat the words by values
      // and count the unique occurrences.
      // Note that this implementation would not be a good choice at very
      // large scale, because countByValue forces it to a single, in-memory
      // collection on one node, after which we do further processing. 
      // Try redoing it with the WordCount3 "reduceByKey" approach. What
      // changes are required to the rest of the script?
      val wc2 = input
        .flatMap(line => line.split("""\W+"""))
        .countByValue()  // Returns a Map[T, Long]
        .toVector        // Extract into a sequence that can be sorted.
        .map{ case (word, count) => (word, word.length, count) } // add length
        .sortBy{ case (_, length, _) => -length }  // sort descending, ignore
                                                   // 1st, 3rd tuple elements!

      // Save to a file, but because we no longer have an RDD, we have to use
      // good 'ol Java File IO. Note that the output specifier is now a file, 
      // not a directory as before, the format of each line will be diffierent,
      val now = Timestamp.now()
      val outpath = s"${argz("output-path")}-$now"
      if (argz("quiet").toBoolean == false) 
        println(s"Writing output (${wc2.size} records) to: $outpath")
      import java.io._
      val out = new PrintWriter(outpath)
      wc2 foreach {
        case (word, length, count) => 
          out.println("%20s\t%3d\t%d".format(word, length, count))
      }
      // WARNING: Without this close statement, it appears the output stream is 
      // not completely flushed to disk!
      out.close()
    } finally {
      // Stop (shut down) the context.
      sc.stop()
    }

    // Exercise: Try different arguments for the input and output. 
    //   NOTE: I've observed 0 output for some small input files!
  }
}
