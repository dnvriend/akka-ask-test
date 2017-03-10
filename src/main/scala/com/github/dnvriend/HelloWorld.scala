/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend

import java.util.UUID

import akka.actor.{ Actor, ActorPath, ActorRef, ActorSystem, Props }
import com.github.dnvriend.MyActor.Message

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

import scala.util.Random

object HelloWorld extends App {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val timeout: Timeout = 10.seconds
  val idService: IdService = new DefaultIdService(system)

  (for {
    xs <- Future.sequence(List.fill(4)(idService.getId()))
    _ = println(s"Received: $xs")
    _ <- system.terminate()
  } yield ()).recover {
    case t: Throwable =>
      t.printStackTrace()
      system.terminate()
  }
}

trait IdService {
  def getId(): Future[String]
}

class DefaultIdService(system: ActorSystem)(implicit ec: ExecutionContext, timeout: Timeout) extends IdService {
  val ref = system.actorOf(Props(classOf[MyActor], ec))
  private def randomId() = UUID.randomUUID.toString
  override def getId(): Future[String] = (ref ? randomId()).mapTo[String]
}

object MyActor {
  case class Message(delay: FiniteDuration, msg: Any, replyTo: ActorRef, serializedRef: String)
}
class MyActor(implicit ec: ExecutionContext) extends Actor {
  override def receive: Receive = {
    case m @ Message(_, msg, replyRef, serializedRef) =>
      println("===> Replying: " + m)
      context.actorSelection(ActorPath.fromString(serializedRef)) ! msg
    //      replyRef ! msg
    case msg =>
      println("===> Scheduling: " + msg)
      val delay = Random.nextInt(1000).millis
      context.system.scheduler.scheduleOnce(delay, self, Message(delay, msg, sender(), sender().path.toSerializationFormat))
  }
}
