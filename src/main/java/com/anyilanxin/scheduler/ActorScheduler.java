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

import com.anyilanxin.scheduler.clock.ActorClock;
import com.anyilanxin.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ActorScheduler {
  private final AtomicReference<SchedulerState> state = new AtomicReference<>();
  private final ActorExecutor actorTaskExecutor;

  public ActorScheduler(final ActorSchedulerBuilder builder) {
    state.set(SchedulerState.NEW);
    actorTaskExecutor = builder.getActorExecutor();
  }

  /**
   * Submits an non-blocking, CPU-bound actor.
   *
   * @param actor the actor to submit
   */
  public ActorFuture<Void> submitActor(final Actor actor) {
    return actorTaskExecutor.submitCpuBound(actor.actor.task);
  }

  /**
   * Submits an actor providing hints to the scheduler about how to best schedule the actor. Actors
   * must always be non-blocking. On top of that, the scheduler distinguishes
   *
   * <ul>
   *   <li>CPU-bound actors: actors which perform no or very little blocking I/O. It is possible to
   *       specify a priority.
   *   <li>I/O-bound actors: actors where the runtime is dominated by performing <strong>blocking
   *       I/O</strong> (usually filesystem writes). It is possible to specify the I/O device used
   *       by the actor.
   * </ul>
   *
   * Scheduling hints can be created using the {@link SchedulingHints} class.
   *
   * @param actor the actor to submit
   * @param schedulingHints additional scheduling hint
   */
  public ActorFuture<Void> submitActor(final Actor actor, final int schedulingHints) {
    final ActorTask task = actor.actor.task;

    final ActorFuture<Void> startingFuture;
    if (SchedulingHints.isCpuBound(schedulingHints)) {
      task.setPriority(SchedulingHints.getPriority(schedulingHints));
      startingFuture = actorTaskExecutor.submitCpuBound(task);
    } else {
      startingFuture = actorTaskExecutor.submitIoBoundTask(task);
    }
    return startingFuture;
  }

  public void start() {
    if (state.compareAndSet(SchedulerState.NEW, SchedulerState.RUNNING)) {
      actorTaskExecutor.start();
    } else {
      throw new IllegalStateException("Cannot start scheduler already started.");
    }
  }

  public Future<Void> stop() {
    if (state.compareAndSet(SchedulerState.RUNNING, SchedulerState.TERMINATING)) {

      return actorTaskExecutor
          .closeAsync()
          .thenRun(
              () -> {
                state.set(SchedulerState.TERMINATED);
              });
    } else {
      throw new IllegalStateException("Cannot stop scheduler not running");
    }
  }

  public void setBlockingTasksShutdownTime(final Duration shutdownTime) {
    actorTaskExecutor.setBlockingTasksShutdownTime(shutdownTime);
  }

  public static ActorSchedulerBuilder newActorScheduler() {
    return new ActorSchedulerBuilder();
  }

  public static ActorScheduler newDefaultActorScheduler() {
    return new ActorSchedulerBuilder().build();
  }

  public static class ActorSchedulerBuilder {
    private String schedulerName = "";
    private ActorClock actorClock;

    private int cpuBoundThreadsCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    private ActorThreadGroup cpuBoundActorGroup;
    private final double[] priorityQuotas = new double[] {0.60, 0.30, 0.10};

    private int ioBoundThreadsCount = 2;
    private ActorThreadGroup ioBoundActorGroup;

    private ActorThreadFactory actorThreadFactory;
    private ThreadPoolExecutor blockingTasksRunner;
    private Duration blockingTasksShutdownTime = Duration.ofSeconds(15);
    private ActorExecutor actorExecutor;

    private ActorTimerQueue actorTimerQueue;

    public ActorSchedulerBuilder setActorTimerQueue(final ActorTimerQueue actorTimerQueue) {
      this.actorTimerQueue = actorTimerQueue;
      return this;
    }

    public ActorSchedulerBuilder setActorClock(final ActorClock actorClock) {
      this.actorClock = actorClock;
      return this;
    }

    public ActorSchedulerBuilder setCpuBoundActorThreadCount(final int actorThreadCount) {
      cpuBoundThreadsCount = actorThreadCount;
      return this;
    }

    public ActorSchedulerBuilder setIoBoundActorThreadCount(final int ioBoundActorsThreadCount) {
      ioBoundThreadsCount = ioBoundActorsThreadCount;
      return this;
    }

    public ActorSchedulerBuilder setActorThreadFactory(
        final ActorThreadFactory actorThreadFactory) {
      this.actorThreadFactory = actorThreadFactory;
      return this;
    }

    public ActorSchedulerBuilder setBlockingTasksShutdownTime(
        final Duration blockingTasksShutdownTime) {
      this.blockingTasksShutdownTime = blockingTasksShutdownTime;
      return this;
    }

    public ActorSchedulerBuilder setSchedulerName(final String schedulerName) {
      this.schedulerName = schedulerName;
      return this;
    }

    public String getSchedulerName() {
      return schedulerName;
    }

    public ActorClock getActorClock() {
      return actorClock;
    }

    public ActorTimerQueue getActorTimerQueue() {
      return actorTimerQueue;
    }

    public int getCpuBoundActorThreadCount() {
      return cpuBoundThreadsCount;
    }

    public int getIoBoundActorThreadCount() {
      return ioBoundThreadsCount;
    }

    public double[] getPriorityQuotas() {
      return Arrays.copyOf(priorityQuotas, priorityQuotas.length);
    }

    public ActorThreadFactory getActorThreadFactory() {
      return actorThreadFactory;
    }

    public ThreadPoolExecutor getBlockingTasksRunner() {
      return blockingTasksRunner;
    }

    public Duration getBlockingTasksShutdownTime() {
      return blockingTasksShutdownTime;
    }

    public ActorExecutor getActorExecutor() {
      return actorExecutor;
    }

    public ActorThreadGroup getCpuBoundActorThreads() {
      return cpuBoundActorGroup;
    }

    public ActorThreadGroup getIoBoundActorThreads() {
      return ioBoundActorGroup;
    }

    private void initActorThreadFactory() {
      if (actorThreadFactory == null) {
        actorThreadFactory = new DefaultActorThreadFactory();
      }
    }

    private void initBlockingTaskRunner() {
      if (blockingTasksRunner == null) {
        blockingTasksRunner =
            new ThreadPoolExecutor(
                1,
                Integer.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new BlockingTasksThreadFactory(schedulerName));
      }
    }

    private void initIoBoundActorThreadGroup() {
      if (ioBoundActorGroup == null) {
        ioBoundActorGroup = new IoThreadGroup(this);
      }
    }

    private void initCpuBoundActorThreadGroup() {
      if (cpuBoundActorGroup == null) {
        cpuBoundActorGroup = new CpuThreadGroup(this);
      }
    }

    private void initActorExecutor() {
      if (actorExecutor == null) {
        actorExecutor = new ActorExecutor(this);
      }
    }

    public ActorScheduler build() {
      initActorThreadFactory();
      initBlockingTaskRunner();
      initCpuBoundActorThreadGroup();
      initIoBoundActorThreadGroup();
      initActorExecutor();
      return new ActorScheduler(this);
    }
  }

  public interface ActorThreadFactory {
    ActorThread newThread(
        String name,
        int id,
        ActorThreadGroup threadGroup,
        TaskScheduler taskScheduler,
        ActorClock clock,
        ActorTimerQueue timerQueue);
  }

  public static class DefaultActorThreadFactory implements ActorThreadFactory {
    @java.lang.Override
    @Override
    public ActorThread newThread(
        final String name,
        final int id,
        final ActorThreadGroup threadGroup,
        final TaskScheduler taskScheduler,
        final ActorClock clock,
        final ActorTimerQueue timerQueue) {
      return new ActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
    }
  }

  public static class BlockingTasksThreadFactory implements ThreadFactory {
    final AtomicLong idGenerator = new AtomicLong();
    private final String schedulerName;

    public BlockingTasksThreadFactory(final String schedulerName) {
      this.schedulerName = schedulerName;
    }

    @Override
    public Thread newThread(final Runnable r) {
      final Thread thread = new Thread(r);
      thread.setName(
          "zb-blocking-task-runner-" + idGenerator.incrementAndGet() + "-" + schedulerName);
      return thread;
    }
  }

  private enum SchedulerState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // scheduler is not reusable
  }
}
