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
package com.anyilanxin.scheduler.lifecycle;

import com.anyilanxin.scheduler.future.CompletableActorFuture;
import com.anyilanxin.scheduler.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;

import static org.mockito.Mockito.*;

public class ActorLifecyclePhasesAndTimersTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteTimersInStartingPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
              actor.schedule(Duration.ofMillis(10), action);
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

  @Test
  public void shouldExecuteTimersSubmittedInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
              actor.schedule(Duration.ofMillis(10), action);
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(0)).run();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(action, times(1)).run();
  }

  @Test
  public void shouldExecuteTimersInStartedPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
              actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(1)).run();
  }

  @Test
  public void shouldNotExecuteTimersInCloseRequestedPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase();
              actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

  @Test
  public void shouldNotExecuteTimersInClosingPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            blockPhase();
              actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }
}
