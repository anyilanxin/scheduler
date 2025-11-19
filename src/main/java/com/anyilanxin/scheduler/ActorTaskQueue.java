/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 * Copyright © 2025 anyilanxin zxh(anyilanxin@aliyun.com)
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
package com.anyilanxin.scheduler;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

@SuppressWarnings("restriction")
class ActorTaskQueueNode {
  protected static final VarHandle PREV_VAR_HANDLE;
  protected static final VarHandle NEXT_VAR_HANDLE;

  static {
    try {
      final MethodHandles.Lookup lookup = MethodHandles.lookup();
      PREV_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueNode.class, "prev", ActorTaskQueueNode.class);
      NEXT_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueNode.class, "next", ActorTaskQueueNode.class);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  volatile ActorTaskQueueNode next;
  volatile ActorTaskQueueNode prev;

  long stateCount;
  ActorTask task;

  void nextOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    NEXT_VAR_HANDLE.setRelease(this, t);
  }

  void prevOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    PREV_VAR_HANDLE.setRelease(this, t);
  }

  public void setTask(final ActorTask task) {
    this.task = task;
  }
}

@SuppressWarnings("restriction")
class ActorTaskQueuePadding1 {
  protected static final VarHandle HEAD_VAR_HANDLE;
  protected static final VarHandle TAIL_VAR_HANDLE;
  protected static final VarHandle PREV_VAR_HANDLE;
  protected static final VarHandle NEXT_VAR_HANDLE;
  protected static final VarHandle STATE_COUNT_VAR_HANDLE;

  static {
    try {
      final MethodHandles.Lookup lookup = MethodHandles.lookup();
      HEAD_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueHead.class, "head", ActorTaskQueueNode.class);
      TAIL_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueTail.class, "tail", ActorTaskQueueNode.class);
      PREV_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueNode.class, "prev", ActorTaskQueueNode.class);
      NEXT_VAR_HANDLE =
          lookup.findVarHandle(ActorTaskQueueNode.class, "next", ActorTaskQueueNode.class);
      STATE_COUNT_VAR_HANDLE =
          lookup.findVarHandle(ActorTask.class, "stateCount", AtomicLong.class);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @SuppressWarnings("unused")
  protected long p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;
}

/** Value for the tail that is expected to be padded. */
class ActorTaskQueueTail extends ActorTaskQueuePadding1 {
  protected volatile ActorTaskQueueNode tail;
}

/** Pad out a cache line between the tail and the head to prevent false sharing. */
class ActorTaskQueuePadding2 extends ActorTaskQueueTail {
  @SuppressWarnings("unused")
  protected long p16, p17, p18, p19, p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p30;
}

/** Value for the head that is expected to be padded. */
class ActorTaskQueueHead extends ActorTaskQueuePadding2 {
  protected volatile ActorTaskQueueNode head;
}

/** Adapted from Agrona's {@link ManyToOneConcurrentLinkedQueue}. */
@SuppressWarnings("restriction")
public class ActorTaskQueue extends ActorTaskQueueHead {
  @SuppressWarnings("unused")
  protected long p31, p32, p33, p34, p35, p36, p37, p38, p39, p40, p41, p42, p43, p44, p45;

  private final ActorTaskQueueNode empty = new ActorTaskQueueNode();

  public ActorTaskQueue() {
    headOrdered(empty);
    TAIL_VAR_HANDLE.setRelease(this, empty);
  }

  /** appends a task at the end (tail) of the list */
  public void append(final ActorTask task) {
    // TODO: make garbage free again
    final ActorTaskQueueNode tail = new ActorTaskQueueNode();
    tail.task = task;
    tail.stateCount = task.getStateCount();
    final ActorTaskQueueNode previousTail = swapTail(tail);
    previousTail.nextOrdered(tail);
    tail.prevOrdered(previousTail);
  }

  /**
   * Attempts to steal a task from this list. The operation does not actually remove the task.
   * Instead, it modifies the task's state.
   *
   * @return the actor which was stolen or null in case no actor is available
   */
  public ActorTask trySteal() {
    ActorTaskQueueNode node = tail;

    while (node != null && node != empty) {
      final ActorTask task = node.task;
      if (task.claim(node.stateCount)) {
        return task;
      }

      node = node.prev;
    }

    return null;
  }

  public ActorTask pop() {
    ActorTask task = null;
    ActorTaskQueueNode node = null;

    do {
      node = poll();

      if (node != empty && node != null) {
        final ActorTask t = node.task;
        if (t.claim(node.stateCount)) {
          task = t;
          break;
        }
      }
    } while (node != null && node != empty);

    return task;
  }

  private ActorTaskQueueNode poll() {
    ActorTaskQueueNode value = null;
    final ActorTaskQueueNode head = this.head;
    ActorTaskQueueNode next = head.next;

    if (null != next) {
      value = next;
      head.prevOrdered(null);
      head.nextOrdered(null);

      if (null == next.next) {
        final ActorTaskQueueNode tail = this.tail;
        if (tail == next && casTail(tail, empty)) {
          next = empty;
        }
      }

      headOrdered(next);
    }

    return value;
  }

  public ActorTaskQueueNode peek() {
    final ActorTaskQueueNode next = head.next;
    return next == empty ? null : next;
  }

  public boolean isEmpty() {
    return head == tail;
  }

  private void headOrdered(final ActorTaskQueueNode head) {
    HEAD_VAR_HANDLE.setRelease(this, head);
  }

  private ActorTaskQueueNode swapTail(final ActorTaskQueueNode newTail) {
    return (ActorTaskQueueNode) TAIL_VAR_HANDLE.getAndSet(this, newTail);
  }

  private boolean casTail(
      final ActorTaskQueueNode expectedNode, final ActorTaskQueueNode updateNode) {
    return TAIL_VAR_HANDLE.compareAndSet(this, expectedNode, updateNode);
  }
}
