/*
 * Copyright (C) 2015 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.eventuate.crdt

import akka.actor._

import com.rbmhtechnology.eventuate.VectorTime

import scala.concurrent.Future

case class Counter[A : Integral](value: A) {
  def update(delta: A): Counter[A] =
    copy(value = implicitly[Integral[A]].plus(value, delta))
}

object Counter {
  def apply[A : Integral]: Counter[A] =
    Counter[A](implicitly[Integral[A]].zero)

  implicit def CounterServiceOps[A : Integral] = new CRDTServiceOps[Counter[A], A] {
    override def zero: Counter[A] =
      Counter.apply[A]

    override def value(crdt: Counter[A]): A =
      crdt.value

    override def update(crdt: Counter[A], operation: Any, vectorTimestamp: VectorTime, systemTimestamp: Long): Counter[A] = operation match {
      case UpdateOp(delta) => crdt.update(delta.asInstanceOf[A])
    }
  }
}

/**
 * Replicated [[Counter]] CRDT service.
 *
 * @param processId unique process id of this service replica.
 * @param log event log
 * @tparam A counter value type
 */
class CounterService[A](val processId: String, val log: ActorRef)(implicit system: ActorSystem, integral: Integral[A], val ops: CRDTServiceOps[Counter[A], A])
  extends CRDTService[Counter[A], A] {

  /**
   * Updates the counter identified by `id` with specified `delta` and returns the updated counter value.
   */
  def update(id: String, delta: A): Future[A] =
    op(id, UpdateOp(delta))

  start()
}

private case class UpdateOp(delta: Any)
