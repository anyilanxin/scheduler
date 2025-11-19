/*
 * Copyright © 2025 anyilanxin zxh(anyilanxin@aliyun.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.anyilanxin.scheduler;

import com.anyilanxin.scheduler.clock.ActorClock;
import java.util.concurrent.TimeUnit;

/**
 * A TimerSubscription based on a delay. Scheduling requires an external clock to determine the
 * exact timestamp. This type of timer subscription can be triggered once, or recur until canceled.
 */
public final class DelayedTimerSubscription implements TimerSubscription {
  private final ActorJob job;
  private final ActorTask task;
  private final TimeUnit timeUnit;
  private final long delay;
  private final boolean isRecurring;
  private volatile boolean isDone = false;
  private volatile boolean isCanceled = false;
  private long timerId = -1L;
  private ActorThread thread;
  private long timerExpiredAt;

  public DelayedTimerSubscription(
      final ActorJob job, final long delay, final TimeUnit timeUnit, final boolean isRecurring) {
    this.job = job;
    task = job.getTask();
    this.timeUnit = timeUnit;
    this.delay = delay;
    this.isRecurring = isRecurring;
  }

  @Override
  public boolean poll() {
    return isDone;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return isRecurring;
  }

  @Override
  public void onJobCompleted() {

    if (isRecurring && !isCanceled) {
      isDone = false;
      submit();
    }
  }

  @Override
  public void cancel() {
    if (!isCanceled && (!isDone || isRecurring)) {
      task.onSubscriptionCancelled(this);
      isCanceled = true;
      final ActorThread current = ActorThread.current();

      if (current != thread) {
        thread.submittedCallbacks.add(this);
      } else {
        run();
      }
    }
  }

  @Override
  public long getTimerId() {
    return timerId;
  }

  @Override
  public void setTimerId(final long timerId) {
    this.timerId = timerId;
  }

  @Override
  public void submit() {
    thread = ActorThread.current();
    thread.scheduleTimer(this);
  }

  @Override
  public long getDeadline(final ActorClock now) {
    return now.getTimeMillis() + timeUnit.convert(delay, timeUnit);
  }

  @Override
  public void onTimerExpired(final TimeUnit timeUnit, final long now) {
    if (!isCanceled) {
      isDone = true;
      timerExpiredAt = timeUnit.toNanos(now);
      task.tryWakeup();
    }
  }

  @Override
  public void run() {
    thread.removeTimer(this);
  }

  @Override
  public long getTimerExpiredAt() {
    return timerExpiredAt;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  @Override
  public String toString() {
    return "TimerSubscription{"
        + "timerId="
        + timerId
        + ", delay="
        + delay
        + ", timeUnit="
        + timeUnit
        + ", isRecurring="
        + isRecurring
        + ", isDone="
        + isDone
        + ", isCanceled="
        + isCanceled
        + ", thread="
        + thread
        + '}';
  }
}
