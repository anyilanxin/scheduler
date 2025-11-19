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

import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Control interface to schedule tasks or follow-up tasks such that different tasks scheduled via
 * the same {@code ConcurrencyControl} object are never executed concurrently
 */
public interface ConcurrencyControl extends Executor {

  /**
   * Schedules a callback to be invoked after the future has completed
   *
   * @param future the future whose completion is awaited
   * @param callback the callback to call after the future has completed
   * @param <T> result type of the future
   */
  <T> void runOnCompletion(final ActorFuture<T> future, final BiConsumer<T, Throwable> callback);

  /**
   * Invoke the callback when the given futures are completed (successfully or exceptionally). This
   * call does not block the actor.
   *
   * <p>The callback is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback The throwable is <code>null</code> when all futures are completed successfully.
   *     Otherwise, it holds the exception of the last completed future.
   */
  <T> void runOnCompletion(
      final Collection<ActorFuture<T>> futures, final Consumer<Throwable> callback);

  /**
   * Schedules an action to be invoked (must be called from an actor thread)
   *
   * @param action action to be invoked
   */
  void run(final Runnable action);

  /**
   * Schedules a callable to be executed
   *
   * @param callable callable to be executed
   * @param <T> type of the result
   * @return a future with the result
   */
  <T> ActorFuture<T> call(final Callable<T> callable);

  /** Schedule a task to be executed after a delay */
  ScheduledTimer schedule(final Duration delay, final Runnable runnable);

  /**
   * Create a new future object
   *
   * @param <V> value type of future
   * @return new future object
   */
  default <V> ActorFuture<V> createFuture() {
    return new CompletableActorFuture<>();
  }

  /**
   * Create a new completed future object
   *
   * @param <V> value type of future
   * @return new completed future object
   */
  default <V> ActorFuture<V> createCompletedFuture() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  default void execute(@NotNull final Runnable command) {
    run(command);
  }
}
