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
package com.anyilanxin.scheduler.functional;

import com.anyilanxin.scheduler.Actor;
import com.anyilanxin.scheduler.ActorControl;
import com.anyilanxin.scheduler.ActorThread;
import com.anyilanxin.scheduler.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RunnableActionsTest {
  @Rule public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

  @Test
  public void shouldInvokeRunFromWithinActor() {
    // given
    final Runner runner =
        new Runner() {
          @Override
          protected void onActorStarted() {
              doRun();
          }
        };

    scheduler.submitActor(runner);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(runner.runs).isEqualTo(1);
  }

  @Test
  public void shouldInvokeRunFromNonActorThread() {
    // given
    final Runner runner = new Runner();
    scheduler.submitActor(runner);

    // when
    runner.doRun();
    scheduler.workUntilDone();

    // then
    assertThat(runner.runs).isEqualTo(1);
  }

  @Test
  public void shouldInvokeRunFromAnotherActor() {
    // given
    final Runner runner = new Runner();
    final Actor invoker =
        new Actor() {
          @Override
          protected void onActorStarted() {
            runner.doRun();
          }
        };

    scheduler.submitActor(runner);
    scheduler.submitActor(invoker);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(runner.runs).isEqualTo(1);
  }

  @Test
  public void shouldSubmitRunnableToCorrectActorTask() {
    // given
    final List<Actor> actorContext = new ArrayList<>();
    final Runner runner =
            new Runner(() -> actorContext.add(ActorThread.current().getCurrentTask().getActor()));

    final Actor invoker =
        new Actor() {
          @Override
          protected void onActorStarted() {
            runner.doRun();
          }
        };

    scheduler.submitActor(runner);
    scheduler.submitActor(invoker);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(actorContext).containsExactly(runner);
  }

  @Test
  public void shouldRunUntilDoneCalled() {
    // given
    final Runner actor = new Runner();
    final Consumer<ActorControl> runnable =
        (ctr) -> {
          if (actor.runs == 5) {
            ctr.done();
          } else {
              ctr.yieldThread();
          }
        };

    // when
    scheduler.submitActor(actor);
    actor.doRunUntilDone(runnable);
    scheduler.workUntilDone();

    // then
    assertThat(actor.runs).isEqualTo(5);
  }

  @Test
  public void shouldNotInterruptRunUntilDone() {
    // given
    final Runnable otherAction = mock(Runnable.class);
    final Runner actor = new Runner();
    final Consumer<ActorControl> runUntilDoneAction =
        spy(
            new Consumer<ActorControl>() {
              @Override
              public void accept(final ActorControl ctr) {
                ctr.run(otherAction); // does not interrupt this action

                if (actor.runs == 5) {
                  ctr.done();
                } else {
                    ctr.yieldThread();
                }
              }
            });
    doCallRealMethod().when(runUntilDoneAction).accept(any());

    // when
    scheduler.submitActor(actor);
    actor.doRunUntilDone(runUntilDoneAction);
    scheduler.workUntilDone();

    // then
    final InOrder inOrder = inOrder(runUntilDoneAction, otherAction);
    inOrder.verify(runUntilDoneAction, times(5)).accept(any());
    inOrder.verify(otherAction, times(5)).run();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSubmitFromAnotherActor() {
    // given
    final AtomicInteger invocations = new AtomicInteger();
    final AtomicBoolean exceptionOnSubmit = new AtomicBoolean(false);

    final Submitter submitter = new Submitter();

    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            try {
              submitter.submit(invocations::incrementAndGet);
            } catch (final Exception e) {
              exceptionOnSubmit.set(true);
            }
          }
        };

    scheduler.submitActor(submitter);
    scheduler.submitActor(actor);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(invocations).hasValue(1);
    assertThat(exceptionOnSubmit).isFalse();
  }

    static class Submitter extends Actor {
      public void submit(final Runnable r) {
          actor.submit(r);
    }
  }

    static class Runner extends Actor {
    int runs = 0;
    Runnable onExecution;

    Runner() {
      this(null);
    }

      Runner(final Runnable onExecution) {
      this.onExecution = onExecution;
    }

    public void doRun() {
      actor.run(
          () -> {
            if (onExecution != null) {
              onExecution.run();
            }
            runs++;
          });
    }

      public void doRunUntilDone(final Consumer<ActorControl> runnable) {
      actor.run(
          () -> {
            actor.runUntilDone(
                () -> {
                  runs++;
                  runnable.accept(actor);
                });
          });
    }
  }
}
