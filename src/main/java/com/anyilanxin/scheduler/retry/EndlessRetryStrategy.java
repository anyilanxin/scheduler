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

import com.anyilanxin.scheduler.ActorControl;
import com.anyilanxin.scheduler.Loggers;
import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.future.CompletableActorFuture;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public final class EndlessRetryStrategy implements RetryStrategy {

  private static final Logger LOG = Loggers.RETRY_LOGGER;

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;

  public EndlessRetryStrategy(final ActorControl actor) {
    this.actor = actor;
    retryMechanism = new ActorRetryMechanism();
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(final OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(
      final OperationToRetry callable, final BooleanSupplier condition) {
    currentFuture = new CompletableActorFuture<>();
    terminateCondition = condition;
    retryMechanism.wrap(callable, terminateCondition, currentFuture);

    actor.run(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      final var control = retryMechanism.run();
      if (control == ActorRetryMechanism.Control.RETRY) {
        actor.run(this::run);
        actor.yieldThread();
      }
    } catch (final Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        actor.run(this::run);
        actor.yieldThread();
        LOG.error(
            "Caught exception {} with message {}, will retry...",
            exception.getClass(),
            exception.getMessage(),
            exception);
      }
    }
  }
}
