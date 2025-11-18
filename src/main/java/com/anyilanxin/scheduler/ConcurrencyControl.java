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
 * Concurrency control interface
 *
 * @author zxuanhong
 * @date 2025/11/18
 */
public interface ConcurrencyControl extends Executor {
  /**
   * Invoke the callback when the given future is completed (successfully or exceptionally). This
   * call does not block the actor. If close is requested the actor will not wait on this future, in
   * this case the callback is never called.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param future the future to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *                 </code> when the future is completed successfully.
   */
  <T> void runOnCompletion(final ActorFuture<T> future, final BiConsumer<T, Throwable> callback);

  /**
   * Invoke the callback when the given futures are completed (successfully or exceptionally). This
   * call does not block the actor.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback The throwable is <code>null</code> when all futures are completed successfully.
   *     Otherwise, it holds the exception of the last completed future.
   */
  <T> void runOnCompletion(
      final Collection<ActorFuture<T>> futures, final Consumer<Throwable> callback);

  /**
   * Runnables submitted by the actor itself are executed while the actor is in any of its lifecycle
   * phases.
   *
   * <p>Runnables submitted externally are executed while the actor is in the following actor
   * lifecycle phases: {@link ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param action
   */
  void run(final Runnable action);

  /**
   * Callables actions are called while the actor is in the following actor lifecycle phases: {@link
   * ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param callable
   * @return
   */
  <T> ActorFuture<T> call(final Callable<T> callable);

  /**
   * The runnable is is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorTask.ActorLifecyclePhase#STARTED}
   *
   * @param delay
   * @param runnable
   * @return
   */
  ScheduledTimer runDelayed(final Duration delay, final Runnable runnable);

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
