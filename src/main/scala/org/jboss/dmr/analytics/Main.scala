package org.jboss.dmr.analytics

import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala._

object Main {

  def main(args: Array[String]) {
    val client = connect()
    val sequencer = new Sequencer(client)
    val attributes = sequencer.read("subsystem" -> "web")
    client.close()

    println(s"\n\nRead ${attributes.size} attributes:")
    val attributeStrings = attributes map (attribute => {
      val address = attribute.address.tuple map { case (key, value) => s"$key=$value"} mkString "/"
      s"${attribute.name.padTo(30, ' ')}: ${attribute.`type`.`type`.padTo(10, ' ')} @ /$address"
    })
    attributeStrings.foreach(println(_))
  }
}
