/*
 * Copyright © 2025 anyilanxin zxh (anyilanxin@aliyun.com)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anyilanxin.scheduler.startup;

import static java.util.Collections.singletonList;

import com.anyilanxin.scheduler.ConcurrencyControl;
import com.anyilanxin.scheduler.exception.StartupProcessException;
import com.anyilanxin.scheduler.exception.StartupProcessShutdownException;
import com.anyilanxin.scheduler.exception.StartupProcessStepException;
import com.anyilanxin.scheduler.future.ActorFuture;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the startup and shutdown process of components in a sequential manner.
 *
 * <p>This class orchestrates a series of {@link StartupStep} instances, executing them in order
 * during startup and in reverse order during shutdown. It provides error handling and ensures
 * proper cleanup even if failures occur during the process.
 *
 * @param <CONTEXT> The context type that is passed through the startup/shutdown steps
 */
public final class StartupProcess<CONTEXT> {

  private final Logger logger;
  private final Queue<StartupStep<CONTEXT>> steps;
  private final Deque<StartupStep<CONTEXT>> startedSteps = new ArrayDeque<>();

  private boolean startupCalled = false;
  private ActorFuture<CONTEXT> shutdownFuture;
  private ActorFuture<CONTEXT> startupFuture;

  public StartupProcess(final List<StartupStep<CONTEXT>> steps) {
    this(LoggerFactory.getLogger(StartupProcess.class), steps);
  }

  public StartupProcess(final Logger logger, final List<? extends StartupStep<CONTEXT>> steps) {
    this.steps = new ArrayDeque<>(Objects.requireNonNull(steps));
    this.logger = Objects.requireNonNull(logger);
  }

  /**
   * Initiates the startup process by executing all registered steps in order.
   *
   * @param concurrencyControl the concurrency control to use for asynchronous execution
   * @param context the initial context to pass through the startup steps
   * @return a future that completes with the final context when startup is complete, or
   *     exceptionally if startup fails
   */
  public ActorFuture<CONTEXT> startup(
      final ConcurrencyControl concurrencyControl, final CONTEXT context) {
    final var result = concurrencyControl.<CONTEXT>createFuture();
    concurrencyControl.run(() -> startupSynchronized(concurrencyControl, context, result));

    return result;
  }

  /**
   * Initiates the shutdown process by executing all previously started steps in reverse order.
   *
   * @param concurrencyControl the concurrency control to use for asynchronous execution
   * @param context the context to pass through the shutdown steps
   * @return a future that completes with the final context when shutdown is complete, or
   *     exceptionally if shutdown fails
   */
  public ActorFuture<CONTEXT> shutdown(
      final ConcurrencyControl concurrencyControl, final CONTEXT context) {
    final var result = concurrencyControl.<CONTEXT>createFuture();
    concurrencyControl.run(() -> shutdownSynchronized(concurrencyControl, context, result));

    return result;
  }

  /**
   * Synchronized method to initiate the startup process.
   *
   * @param concurrencyControl the concurrency control for managing execution
   * @param context the initial context
   * @param startupFuture the future to complete when startup finishes
   */
  private void startupSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> startupFuture) {
    logger.debug("Startup was called with context: {}", context);
    if (startupCalled) {
      throw new IllegalStateException("startup(...) must only be called once");
    }
    startupCalled = true;
    this.startupFuture = startupFuture;

    // reset future when we are done
    concurrencyControl.runOnCompletion(startupFuture, (result, error) -> this.startupFuture = null);

    final var stepsToStart = new ArrayDeque<>(steps);

    proceedWithStartupSynchronized(concurrencyControl, stepsToStart, context, startupFuture);
  }

  /**
   * Proceeds with executing the remaining startup steps.
   *
   * @param concurrencyControl the concurrency control for managing execution
   * @param stepsToStart queue of remaining steps to execute
   * @param context the current context
   * @param startupFuture the future to complete when all steps are finished
   */
  private void proceedWithStartupSynchronized(
      final ConcurrencyControl concurrencyControl,
      final Queue<StartupStep<CONTEXT>> stepsToStart,
      final CONTEXT context,
      final ActorFuture<CONTEXT> startupFuture) {
    if (stepsToStart.isEmpty()) {
      startupFuture.complete(context);
      logger.debug("Finished startup process");
    } else if (shutdownFuture != null) {
      logger.info("Aborting startup process because shutdown was called");
      startupFuture.completeExceptionally(
          new StartupProcessShutdownException(
              "Aborting startup process because shutdown was called"));
    } else {
      final var stepToStart = stepsToStart.poll();
      startedSteps.push(stepToStart);

      logCurrentStepSynchronized("Startup", stepToStart);

      final var stepStartupFuture = stepToStart.startup(context);

      concurrencyControl.runOnCompletion(
          stepStartupFuture,
          (contextReturnedByStep, error) -> {
            if (error != null) {
              completeStartupFutureExceptionallySynchronized(startupFuture, stepToStart, error);
            } else {
              proceedWithStartupSynchronized(
                  concurrencyControl, stepsToStart, contextReturnedByStep, startupFuture);
            }
          });
    }
  }

  /**
   * Completes the startup future exceptionally when a step fails.
   *
   * @param startupFuture the future to complete exceptionally
   * @param stepToStart the step that failed
   * @param error the exception that caused the failure
   */
  private void completeStartupFutureExceptionallySynchronized(
      final ActorFuture<CONTEXT> startupFuture,
      final StartupStep<CONTEXT> stepToStart,
      final Throwable error) {
    logger.warn(
        "Aborting startup process due to exception during step {}", stepToStart.getName(), error);
    startupFuture.completeExceptionally(
        aggregateExceptionsSynchronized(
            "Startup",
            singletonList(new StartupProcessStepException(stepToStart.getName(), error))));
  }

  /**
   * Synchronized method to initiate the shutdown process.
   *
   * @param concurrencyControl the concurrency control for managing execution
   * @param context the context for shutdown
   * @param resultFuture the future to complete when shutdown finishes
   */
  private void shutdownSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> resultFuture) {
    logger.debug("Shutdown was called with context: {}", context);
    if (shutdownFuture == null) {
      shutdownFuture = resultFuture;

      if (startupFuture != null) {
        concurrencyControl.runOnCompletion(
            startupFuture,
            (contextReturnedByStartup, error) -> {
              final var contextForShutdown = error == null ? contextReturnedByStartup : context;
              proceedWithShutdownSynchronized(
                  concurrencyControl, contextForShutdown, shutdownFuture, new ArrayList<>());
            });
      } else {
        proceedWithShutdownSynchronized(
            concurrencyControl, context, shutdownFuture, new ArrayList<>());
      }
    } else {
      logger.info("Shutdown already in progress");

      concurrencyControl.runOnCompletion(
          shutdownFuture,
          (contextReturnedByShutdown, error) -> {
            if (error != null) {
              resultFuture.completeExceptionally(error);
            } else {
              resultFuture.complete(contextReturnedByShutdown);
            }
          });
    }
  }

  /**
   * Proceeds with executing the remaining shutdown steps in reverse order.
   *
   * @param concurrencyControl the concurrency control for managing execution
   * @param context the current context
   * @param shutdownFuture the future to complete when all steps are finished
   * @param collectedExceptions list of exceptions collected during shutdown
   */
  private void proceedWithShutdownSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> shutdownFuture,
      final List<StartupProcessStepException> collectedExceptions) {
    if (startedSteps.isEmpty()) {
      completeShutdownFutureSynchronized(context, shutdownFuture, collectedExceptions);
    } else {
      final var stepToShutdown = startedSteps.pop();

      logCurrentStepSynchronized("Shutdown", stepToShutdown);

      final var shutdownStepFuture = stepToShutdown.shutdown(context);

      concurrencyControl.runOnCompletion(
          shutdownStepFuture,
          (contextReturnedByShutdown, error) -> {
            final CONTEXT contextToUse;
            if (error != null) {
              collectedExceptions.add(
                  new StartupProcessStepException(stepToShutdown.getName(), error));
              contextToUse = context;
            } else {
              contextToUse = contextReturnedByShutdown;
            }

            proceedWithShutdownSynchronized(
                concurrencyControl, contextToUse, shutdownFuture, collectedExceptions);
          });
    }
  }

  /**
   * Completes the shutdown future with either success or aggregated exceptions.
   *
   * @param context the final context after all steps
   * @param shutdownFuture the future to complete
   * @param collectedExceptions list of exceptions that occurred during shutdown
   */
  private void completeShutdownFutureSynchronized(
      final CONTEXT context,
      final ActorFuture<CONTEXT> shutdownFuture,
      final List<StartupProcessStepException> collectedExceptions) {
    if (collectedExceptions.isEmpty()) {
      shutdownFuture.complete(context);
      logger.debug("Finished shutdown process");
    } else {
      final var umbrellaException =
          aggregateExceptionsSynchronized("Shutdown", collectedExceptions);
      shutdownFuture.completeExceptionally(umbrellaException);
      logger.warn(umbrellaException.getMessage(), umbrellaException);
    }
  }

  /**
   * Aggregates multiple exceptions into a single exception with suppressed exceptions.
   *
   * @param operation the operation name (Startup or Shutdown)
   * @param exceptions the list of exceptions to aggregate
   * @return a single exception containing all provided exceptions as suppressed exceptions
   */
  private Throwable aggregateExceptionsSynchronized(
      final String operation, final List<StartupProcessStepException> exceptions) {
    final var failedSteps =
        exceptions.stream().map(StartupProcessStepException::getStepName).toList();
    final var message =
        String.format(
            "%s failed in the following steps: %s. See suppressed exceptions for details.",
            operation, failedSteps);

    final var exception = new StartupProcessException(message);
    exceptions.forEach(exception::addSuppressed);
    return exception;
  }

  /**
   * Logs the current step being executed.
   *
   * @param process the process name (Startup or Shutdown)
   * @param step the current step being executed
   */
  private void logCurrentStepSynchronized(final String process, final StartupStep<CONTEXT> step) {
    logger.info("{} {}", process, step.getName());
  }
}
