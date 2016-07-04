/*
 * Copyright 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
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

package com.rbmhtechnology.eventuate

import scala.collection.immutable.Seq

/**
 * Provider API.
 *
 * Event storage format. Fields `localLogId` and `localSequenceNr` differ among replicas, all other fields are not changed
 * during event replication.
 *
 * @param payload Application-defined event.
 * @param emitterId Id of emitter ([[EventsourcedActor]] or [[EventsourcedProcessor]]).
 * @param emitterAggregateId Aggregate id of emitter ([[EventsourcedActor]] or [[EventsourcedProcessor]]). This is also
 *                           the default routing destination of this event. If defined, the event is routed to event-
 *                           sourced actors, views, writers and processors with a matching `aggregateId`. In any case,
 *                           the event is routed to event-sourced actors, views, writers and processors with an undefined
 *                           `aggregateId`.
 * @param customDestinationAggregateIds Aggregate ids of additional, custom routing destinations. If non-empty, the event is
 *                                      additionally routed to event-sourced actors, views, writers and processors with a
 *                                      matching `aggregateId`.
 * @param systemTimestamp Wall-clock timestamp, generated by the source of concurrent activity that is identified by `processId`.
 * @param vectorTimestamp Vector timestamp, generated by the source of concurrent activity that is identified by `processId`.
 * @param processId Id of the causality-tracking source of concurrent activity. This is the id of the local event log that
 *                  initially wrote the event.
 * @param localLogId Id of the local event log.
 * @param localSequenceNr Sequence number in the local event log.
 * @param deliveryId Delivery id chosen by an application that persisted this event with [[ConfirmedDelivery.persistConfirmation]].
 * @param persistOnEventSequenceNr Sequence number of the event that caused the emission of this event in an event handler.
 *                                 Defined if an [[EventsourcedActor]] with a [[PersistOnEvent]] mixin emitted this event
 *                                 with `persistOnEvent`.
 */
case class DurableEvent(
  payload: Any,
  emitterId: String,
  emitterAggregateId: Option[String] = None,
  customDestinationAggregateIds: Set[String] = Set(),
  systemTimestamp: Long = 0L,
  vectorTimestamp: VectorTime = VectorTime.Zero,
  processId: String = DurableEvent.UndefinedLogId,
  localLogId: String = DurableEvent.UndefinedLogId,
  localSequenceNr: Long = DurableEvent.UndefinedSequenceNr,
  deliveryId: Option[String] = None,
  persistOnEventSequenceNr: Option[Long] = None) {

  import DurableEvent._

  /**
   * Unique event identifier.
   */
  def id: VectorTime =
    vectorTimestamp

  /**
   * Returns `true` if this event did not happen before or at the given `vectorTime`
   * and passes the given replication `filter`.
   */
  def replicable(vectorTime: VectorTime, filter: ReplicationFilter): Boolean =
    !before(vectorTime) && filter(this)

  /**
   * Returns `true` if this event happened before or at the given `vectorTime`.
   */
  def before(vectorTime: VectorTime): Boolean =
    vectorTimestamp <= vectorTime

  /**
   * The default routing destination of this event is its `emitterAggregateId`. If defined, the event is
   * routed to event-sourced actors, views, writers and processors with a matching `aggregateId`. In any case, the event is
   * routed to event-sourced actors, views, writers and processors with an undefined `aggregateId`.
   */
  def defaultDestinationAggregateId: Option[String] =
    emitterAggregateId

  /**
   * The union of [[defaultDestinationAggregateId]] and [[customDestinationAggregateIds]].
   */
  def destinationAggregateIds: Set[String] =
    if (defaultDestinationAggregateId.isDefined) customDestinationAggregateIds + defaultDestinationAggregateId.get else customDestinationAggregateIds

  /**
   * Prepares the event for writing to an event log.
   */
  private[eventuate] def prepare(logId: String, sequenceNr: Long, timestamp: Long): DurableEvent = {
    val id = if (processId == UndefinedLogId) logId else processId
    val vt = if (processId == UndefinedLogId) vectorTimestamp.setLocalTime(logId, sequenceNr) else vectorTimestamp
    copy(systemTimestamp = timestamp, vectorTimestamp = vt, processId = id, localLogId = logId, localSequenceNr = sequenceNr)
  }
}

object DurableEvent {
  val UndefinedLogId = ""
  val UndefinedSequenceNr = 0L

  def apply(emitterId: String): DurableEvent =
    apply(null, emitterId)
}

/**
 * Implemented by protocol messages that contain a [[DurableEvent]] sequence.
 */
trait DurableEventBatch {
  /**
   * Event sequence.
   */
  def events: Seq[DurableEvent]

  /**
   * Event sequence size.
   */
  def size: Int = events.size
}

/**
 * Implemented by protocol messages whose event sequence can be updated.
 */
trait UpdateableEventBatch[A <: UpdateableEventBatch[A]] extends DurableEventBatch {
  /**
   * Replaces this batch's events with the given `events`.
   */
  def update(events: Seq[DurableEvent]): A
}
