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
package com.anyilanxin.scheduler.retry;

import com.anyilanxin.scheduler.Actor;
import com.anyilanxin.scheduler.ActorControl;
import com.anyilanxin.scheduler.exception.RecoverableException;
import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RecoverableRetryStrategyTest {

  @Rule public ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private RecoverableRetryStrategy recoverableRetryStrategy;
  private ActorControl actorControl;
  private ActorFuture<Boolean> resultFuture;

  @Before
  public void setUp() {
    final ControllableActor actor = new ControllableActor();
    actorControl = actor.getActor();
    recoverableRetryStrategy = new RecoverableRetryStrategy(actorControl);

    schedulerRule.submitActor(actor);
  }

  @Test
  public void shouldRetryOnException() throws Exception {
    // given
    final AtomicInteger count = new AtomicInteger(0);
    final AtomicBoolean toggle = new AtomicBoolean(false);

    // when
    actorControl.run(
        () -> {
          resultFuture =
              recoverableRetryStrategy.runWithRetry(
                  () -> {
                    toggle.set(!toggle.get());
                    if (toggle.get()) {
                      throw new RecoverableException("expected");
                    }
                    return count.incrementAndGet() == 10;
                  });
        });

    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture.isDone()).isTrue();
    assertThat(resultFuture.get()).isTrue();
  }

    private static final class ControllableActor extends Actor {
    public ActorControl getActor() {
      return actor;
    }
  }
}
