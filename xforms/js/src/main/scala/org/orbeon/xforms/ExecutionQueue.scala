/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import scala.scalajs.js
import enumeratum._

import scala.concurrent.Future

sealed abstract class ExecutionWait extends EnumEntry

object ExecutionWait extends Enum[ExecutionWait] {

   val values = findValues

   case object MinWait extends ExecutionWait
   case object MaxWait extends ExecutionWait
}

// Handle a queue of events to which events can be added, and where after a certain delay all the events are
// executed at once. Converted from JavaScript/CoffeeScript so as of 2017-03-09 is still fairly JavaScript-like.
class ExecutionQueue[T](execute: List[T] ⇒ Future[Unit]) {

  private var waitingCompletion = false
  private var delayFunctions = 0
  private var events: List[T] = Nil

  // Add an event to the queue and executes all the events in the queue either:
  //
  // a. If waitType is MinWait, after the specified delay unless another event is added to the queue before that.
  // b. If waitType is MaxWait, after the specified delay.
  def add(event: T, waitMs: Int, waitType: ExecutionWait): Unit = {
    events ::= event
    if (! waitingCompletion) {
      delayFunctions += 1
      js.timers.setTimeout(waitMs) {
        delayFunctions -= 1
        if ((waitType == ExecutionWait.MaxWait || delayFunctions == 0) && ! waitingCompletion)
          executeEvents()
      }
    }
  }

  // Call the `execute` function to run the events queued up so far.
  private def executeEvents(): Unit =
    if (events.nonEmpty) {
      waitingCompletion = true
      val localEvents = events
      events = Nil

      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      execute(localEvents) onComplete { _ ⇒
        waitingCompletion = false
        executeEvents()
      }
    }
}
