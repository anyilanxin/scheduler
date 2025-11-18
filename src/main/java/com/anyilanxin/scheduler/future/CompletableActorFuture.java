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
package com.anyilanxin.scheduler.future;

import com.anyilanxin.scheduler.ActorTask;
import com.anyilanxin.scheduler.ActorThread;
import com.anyilanxin.scheduler.FutureUtil;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Completable future implementation that is garbage free and reusable */
@SuppressWarnings("restriction")
public class CompletableActorFuture<V> implements ActorFuture<V> {
  private static final VarHandle STATE_VAR_HANDLE;

  private static final int AWAITING_RESULT = 1;
  private static final int COMPLETING = 2;
  private static final int COMPLETED = 3;
  private static final int COMPLETED_EXCEPTIONALLY = 4;
  private static final int CLOSED = 5;

  private final ManyToOneConcurrentLinkedQueue<ActorTask> blockedTasks =
      new ManyToOneConcurrentLinkedQueue<>();

  private volatile int state = CLOSED;

  private final ReentrantLock completionLock = new ReentrantLock();
  private Condition isDoneCondition;

  protected V value;
  protected String failure;
  protected Throwable failureCause;

  static {
    try {
      STATE_VAR_HANDLE =
          MethodHandles.lookup().findVarHandle(CompletableActorFuture.class, "state", int.class);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public CompletableActorFuture() {
    setAwaitingResult();
  }

  private CompletableActorFuture(final V value) {
    this.value = value;
    state = COMPLETED;
  }

  @Override
  public V join(final long timeout, final TimeUnit timeUnit) {
    return FutureUtil.join(this, timeout, timeUnit);
  }

  private CompletableActorFuture(final Throwable throwable) {
    ensureValidThrowable(throwable);
    failure = throwable.getMessage();
    failureCause = throwable;
    state = COMPLETED_EXCEPTIONALLY;
  }

  private void ensureValidThrowable(final Throwable throwable) {
    if (throwable == null) {
      throw new NullPointerException("Throwable must not be null.");
    }
  }

  public void setAwaitingResult() {
    state = AWAITING_RESULT;
    isDoneCondition = completionLock.newCondition();
  }

  public static <V> CompletableActorFuture<V> completed(final V result) {
    return new CompletableActorFuture<>(result); // cast for null result
  }

  public static CompletableActorFuture<Void> completed() {
    return CompletableActorFuture.completed(null);
  }

  public static <V> CompletableActorFuture<V> completedExceptionally(final Throwable throwable) {
    return new CompletableActorFuture<>(throwable);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    final int state = this.state;
    return state == COMPLETED || state == COMPLETED_EXCEPTIONALLY;
  }

  @Override
  public boolean isCompletedExceptionally() {
    return state == COMPLETED_EXCEPTIONALLY;
  }

  public boolean isAwaitingResult() {
    return state == AWAITING_RESULT;
  }

  @Override
  public void block(final ActorTask onCompletion) {
    blockedTasks.add(onCompletion);
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    try {
      return get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(final long timeout, final TimeUnit unit)
      throws ExecutionException, TimeoutException, InterruptedException {
    if (ActorThread.current() != null) {
      if (!isDone()) {
        throw new IllegalStateException(
            "Actor call get() on future which has not completed. "
                + "Actors must be non-blocking. Use actor.runOnCompletion().");
      }
    } else {
      // blocking get for non-actor threads
      completionLock.lock();
      try {
        long remaining = unit.toNanos(timeout);

        while (!isDone()) {
          if (remaining <= 0) {
            throw new TimeoutException("Timeout after: " + timeout + " " + unit);
          }
          remaining = isDoneCondition.awaitNanos(unit.toNanos(timeout));
        }
      } finally {
        completionLock.unlock();
      }
    }

    if (isCompletedExceptionally()) {
      throw new ExecutionException(failure, failureCause);
    } else {
      return value;
    }
  }

  @Override
  public void complete(final V value) {
    if (STATE_VAR_HANDLE.compareAndSet(this, AWAITING_RESULT, COMPLETING)) {
      this.value = value;
      state = COMPLETED;
      notifyBlockedTasks();
    } else {
      final String err =
          "Cannot complete future, the future is already completed "
              + (state == COMPLETED_EXCEPTIONALLY
                  ? ("exceptionally with " + failure + " ")
                  : " with value " + value);

      throw new IllegalStateException(err);
    }
  }

  @Override
  public void completeExceptionally(final String failure, final Throwable throwable) {
    // important for other actors that consume this by #runOnCompletion
    ensureValidThrowable(throwable);

    if (STATE_VAR_HANDLE.compareAndSet(this, AWAITING_RESULT, COMPLETING)) {
      this.failure = failure;
      failureCause = throwable;
      state = COMPLETED_EXCEPTIONALLY;
      notifyBlockedTasks();
    } else {
      final String err =
          "Cannot complete future, the future is already completed "
              + (state == COMPLETED_EXCEPTIONALLY
                  ? ("exceptionally with '" + failure + "' ")
                  : " with value " + value);
      throw new IllegalStateException(err);
    }
  }

  @Override
  public void completeExceptionally(final Throwable throwable) {
    ensureValidThrowable(throwable);
    completeExceptionally(throwable.getMessage(), throwable);
  }

  private void notifyBlockedTasks() {
    notifyAllInQueue(blockedTasks);

    try {
      completionLock.lock();
      isDoneCondition.signalAll();
    } finally {
      completionLock.unlock();
    }
  }

  private void notifyAllInQueue(final Queue<ActorTask> tasks) {
    while (!tasks.isEmpty()) {
      final ActorTask task = tasks.poll();

      if (task != null) {
        task.tryWakeup();
      }
    }
  }

  @Override
  public V join() {
    return FutureUtil.join(this);
  }

  /** future is reusable after close */
  public boolean close() {
    final int prevState = (int) STATE_VAR_HANDLE.getAndAdd(this, CLOSED);

    if (prevState != CLOSED) {
      value = null;
      failure = null;
      failureCause = null;
      notifyBlockedTasks();
    }

    return prevState != CLOSED;
  }

  public boolean isClosed() {
    return state == CLOSED;
  }

  @Override
  public Throwable getException() {
    if (!isCompletedExceptionally()) {
      throw new IllegalStateException(
          "Cannot call getException(); future is not completed exceptionally.");
    }

    return failureCause;
  }

  public void completeWith(final CompletableActorFuture<V> otherFuture) {
    if (!otherFuture.isDone()) {
      throw new IllegalArgumentException(
          "Future is not completed, can't complete this future with uncompleted future.");
    }

    if (otherFuture.isCompletedExceptionally()) {
      completeExceptionally(otherFuture.failureCause);
    } else {
      complete(otherFuture.value);
    }
  }

  @Override
  public String toString() {
    return "CompletableActorFuture{"
        + (isDone()
            ? (state == COMPLETED ? "value= " + value : "failure= " + failureCause)
            : " not completed (state " + state + ")")
        + "}";
  }
}
