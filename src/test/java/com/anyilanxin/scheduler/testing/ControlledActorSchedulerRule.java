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
package com.anyilanxin.scheduler.testing;

import com.anyilanxin.scheduler.Actor;
import com.anyilanxin.scheduler.ActorScheduler;
import com.anyilanxin.scheduler.ActorScheduler.ActorSchedulerBuilder;
import com.anyilanxin.scheduler.ActorScheduler.ActorThreadFactory;
import com.anyilanxin.scheduler.ActorThread;
import com.anyilanxin.scheduler.ActorThreadGroup;
import com.anyilanxin.scheduler.ActorTimerQueue;
import com.anyilanxin.scheduler.TaskScheduler;
import com.anyilanxin.scheduler.clock.ActorClock;
import com.anyilanxin.scheduler.clock.ControlledActorClock;
import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

public class ControlledActorSchedulerRule extends ExternalResource {
  private final ActorScheduler actorScheduler;
  private final ControlledActorThread controlledActorTaskRunner;
  private final ThreadPoolExecutor blockingTasksRunner;
  private final ControlledActorClock clock = new ControlledActorClock();

  public ControlledActorSchedulerRule() {
    final ControlledActorThreadFactory actorTaskRunnerFactory = new ControlledActorThreadFactory();
    final ActorTimerQueue timerQueue = new ActorTimerQueue(clock, 1);
    final ActorSchedulerBuilder builder =
        ActorScheduler.newActorScheduler()
            .setActorClock(clock)
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setActorThreadFactory(actorTaskRunnerFactory)
            .setBlockingTasksShutdownTime(Duration.ofSeconds(0))
            .setActorTimerQueue(timerQueue);

    actorScheduler = builder.build();
    controlledActorTaskRunner = actorTaskRunnerFactory.controlledThread;
    blockingTasksRunner = builder.getBlockingTasksRunner();
  }

  @Override
  protected void before() {
    actorScheduler.start();
  }

  @Override
  protected void after() {
    actorScheduler.stop();
  }

    public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

    public void awaitBlockingTasksCompleted(final int i) {
    final long currentTimeMillis = System.currentTimeMillis();

    while (System.currentTimeMillis() - currentTimeMillis < 5000) {
      final long completedTaskCount = blockingTasksRunner.getCompletedTaskCount();
      if (completedTaskCount >= i) {
        return;
      }
    }

    Assert.fail("could not complete " + i + " blocking tasks within 5s");
  }

  public void workUntilDone() {
    controlledActorTaskRunner.workUntilDone();
  }

    public <T> ActorFuture<T> call(final Callable<T> callable) {
    final ActorFuture<T> future = new CompletableActorFuture<>();
    submitActor(new CallingActor(future, callable));
    return future;
  }

  static class CallingActor<T> extends Actor {
    private final ActorFuture<T> future;
    private final Callable<T> callable;

      CallingActor(final ActorFuture<T> future, final Callable<T> callable) {
      this.future = future;
      this.callable = callable;
    }

    @Override
    protected void onActorStarted() {
      actor.run(
          () -> {
            try {
              future.complete(callable.call());
            } catch (final Exception e) {
              future.completeExceptionally(e);
            }
          });
    }
  }

  static class ControlledActorThreadFactory implements ActorThreadFactory {
    private ControlledActorThread controlledThread;

    @Override
    public ActorThread newThread(
            final String name,
            final int id,
            final ActorThreadGroup threadGroup,
            final TaskScheduler taskScheduler,
            final ActorClock clock,
            final ActorTimerQueue timerQueue) {
      controlledThread =
          new ControlledActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
      return controlledThread;
    }
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
