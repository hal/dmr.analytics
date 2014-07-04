package org.jboss.dmr.analytics

import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala._

object Main {

  def main(args: Array[String]) {
    val client = connect()
    val sequencer = new Sequencer(client)
    val attributes = sequencer.read("subsystem" -> "transactions")
    client.close()

    println(s"\n\nRead ${attributes.size} attributes:")
    val attributeStrings = attributes map (attribute => {
      s"${attribute.name.padTo(40, ' ')}: ${attribute.`type`.`type`.padTo(10, ' ')} @ ${attribute.address}"
    })

    attributeStrings.sorted.foreach(println(_))
  }
}
