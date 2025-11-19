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

import com.anyilanxin.scheduler.ActorTask.TaskSchedulingState;
import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.future.CompletableActorFuture;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.Async;
import org.slf4j.Logger;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class ActorJob {
  private static final Logger LOG = Loggers.ACTOR_LOGGER;

  TaskSchedulingState schedulingState;
  ActorTask task;
  private Callable<?> callable;
  private Runnable runnable;
  private ActorFuture resultFuture;
  private ActorSubscription subscription;
  private long scheduledAt = -1;

  public void onJobAddedToTask(final ActorTask task) {
    scheduledAt = System.nanoTime();
    this.task = task;
    schedulingState = TaskSchedulingState.QUEUED;
  }

  @Async.Execute
  void execute(final ActorThread runner) {
    try {
      invoke();
    } catch (final Throwable e) {
      LOG.error("actor job execute error.", e);
      task.onFailure(e);
    } finally {
      // in any case, success or exception, decide if the job should be resubmitted
      if (isTriggeredBySubscription() || runnable == null) {
        schedulingState = TaskSchedulingState.TERMINATED;
      } else {
        schedulingState = TaskSchedulingState.QUEUED;
        scheduledAt = System.nanoTime();
      }
    }
  }

  private void invoke() throws Exception {
    final Object invocationResult;
    if (callable != null) {
      invocationResult = callable.call();
    } else {
      invocationResult = null;
      // only tasks triggered by a subscription can "yield"; everything else just executes once
      if (!isTriggeredBySubscription()) {
        final Runnable r = runnable;
        runnable = null;
        r.run();
      } else {
        runnable.run();
      }
    }
    if (resultFuture != null) {
      resultFuture.complete(invocationResult);
      resultFuture = null;
    }
  }

  public void setRunnable(final Runnable runnable) {
    this.runnable = runnable;
  }

  public ActorFuture setCallable(final Callable<?> callable) {
    this.callable = callable;
    setResultFuture(new CompletableActorFuture<>());
    return resultFuture;
  }

  /** used to recycle the job object */
  void reset() {
    schedulingState = TaskSchedulingState.NOT_SCHEDULED;
    scheduledAt = -1;

    task = null;

    callable = null;
    runnable = null;

    resultFuture = null;
    subscription = null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ActorJob{");
    sb.append("schedulingState=").append(schedulingState);
    sb.append(", task=").append(task);
    if (callable != null) {
      sb.append(", callable=").append(callable);
    }
    if (runnable != null) {
      sb.append(", runnable=").append(runnable);
    }
    if (resultFuture != null) {
      sb.append(", resultFuture=").append(resultFuture);
    }
    if (subscription != null) {
      sb.append(", subscription=").append(subscription);
    }
    if (scheduledAt != -1) {
      sb.append(", scheduledAt=").append(scheduledAt);
    }
    sb.append('}');
    return sb.toString();
  }

  public boolean isTriggeredBySubscription() {
    return subscription != null;
  }

  public ActorSubscription getSubscription() {
    return subscription;
  }

  public void setSubscription(final ActorSubscription subscription) {
    this.subscription = subscription;
    task.addSubscription(subscription);
  }

  public ActorTask getTask() {
    return task;
  }

  public Actor getActor() {
    return task.actor;
  }

  public void setResultFuture(final ActorFuture resultFuture) {
    assert !resultFuture.isDone();
    this.resultFuture = resultFuture;
  }

  public void failFuture(final String reason) {
    failFuture(new RuntimeException(reason));
  }

  public void failFuture(final Throwable cause) {
    if (resultFuture != null) {
      resultFuture.completeExceptionally(cause);
    }
  }
}
