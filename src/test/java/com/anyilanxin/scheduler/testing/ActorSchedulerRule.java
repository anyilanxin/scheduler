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
import com.anyilanxin.scheduler.FutureUtil;
import com.anyilanxin.scheduler.clock.ActorClock;
import com.anyilanxin.scheduler.future.ActorFuture;
import org.junit.rules.ExternalResource;

public class ActorSchedulerRule extends ExternalResource {

  private final int numOfIoThreads;
  private final int numOfThreads;
  private final ActorClock clock;

  private ActorSchedulerBuilder builder;
  private ActorScheduler actorScheduler;

    public ActorSchedulerRule(final int numOfThreads, final ActorClock clock) {
    this(numOfThreads, 2, clock);
  }

    public ActorSchedulerRule(final int numOfThreads, final int numOfIoThreads, final ActorClock clock) {

    this.numOfIoThreads = numOfIoThreads;
    this.numOfThreads = numOfThreads;
    this.clock = clock;
  }

    public ActorSchedulerRule(final int numOfThreads) {
    this(numOfThreads, null);
  }

    public ActorSchedulerRule(final ActorClock clock) {
    this(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), clock);
  }

  public ActorSchedulerRule() {
    this(null);
  }

  @Override
  public void before() {
    builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numOfThreads)
            .setIoBoundActorThreadCount(numOfIoThreads)
            .setActorClock(clock);

    actorScheduler = builder.build();
    actorScheduler.start();
  }

  @Override
  public void after() {
    FutureUtil.join(actorScheduler.stop());
    actorScheduler = null;
    builder = null;
  }

    public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public ActorSchedulerBuilder getBuilder() {
    return builder;
  }
}
