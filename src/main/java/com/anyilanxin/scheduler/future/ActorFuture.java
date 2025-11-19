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
package com.anyilanxin.scheduler.future;

import com.anyilanxin.scheduler.ActorTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** interface for actor futures */
public interface ActorFuture<V> extends Future<V>, BiConsumer<V, Throwable> {
  void complete(V value);

  void completeExceptionally(String failure, Throwable throwable);

  void completeExceptionally(Throwable throwable);

  void onComplete(BiConsumer<V, Throwable> consumer);

  void onComplete(BiConsumer<V, Throwable> consumer, Executor executor);

  @Override
  default void accept(final V value, final Throwable throwable) {
    if (throwable != null) {
      completeExceptionally(throwable);
    } else {
      complete(value);
    }
  }

  V join();

  V join(long timeout, TimeUnit timeUnit);

  /** To be used by scheduler only */
  void block(ActorTask onCompletion);

  boolean isCompletedExceptionally();

  Throwable getException();

  <U> ActorFuture<U> andThen(Supplier<ActorFuture<U>> next, Executor executor);

  <U> ActorFuture<U> andThen(Function<V, ActorFuture<U>> next, Executor executor);

  /**
   * Similar to {@link ActorFuture#andThen(Function, Executor)}}, but it allows to return a future
   * even if this future completes exceptionally.
   *
   * @param next the function to apply to the result of this future.
   * @param executor the executor used to handle completion callbacks.
   * @param <U> the type of the new future
   * @return a new future that completes with the result of applying the function to the result of
   *     this future. The function is run even if this future completes exceptionally. This future
   *     can be used for further chaining, compared to the {@link
   *     ActorFuture#onComplete(BiConsumer)} method.
   */
  <U> ActorFuture<U> andThen(BiFunction<V, Throwable, ActorFuture<U>> next, Executor executor);

  /**
   * Similar to {@link CompletableFuture#thenApply(Function)} in that it applies a function to the
   * result of this future, allowing you to change types on the fly.
   *
   * <p>Implementations may be somewhat inefficient and create intermediate futures, schedule
   * completion callbacks on the provided executor etc. As such, it should normally be used for
   * orchestrating futures in a non-performance critical context, for example for startup and
   * shutdown sequence.
   *
   * @param next function to apply to the result of this future.
   * @param executor The executor used to handle completion callbacks.
   * @param <U> the type of the new future
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   */
  <U> ActorFuture<U> thenApply(Function<V, U> next, Executor executor);

  /**
   * Similar to {@link CompletableFuture#thenApply(Function)} in that it applies a function to the
   * result of this future, allowing you to change types on the fly.
   *
   * <p>Implementations may be somewhat inefficient and create intermediate futures, schedule
   * completion callbacks on the provided executor etc. As such, it should normally be used for
   * orchestrating futures in a non-performance critical context, for example for startup and
   * shutdown sequence.
   *
   * <p>If the caller is not an actor, {@link ActorFuture#thenApply(Function, Executor)} must be
   * used to avoid blocking the actor execution context.
   *
   * @param next function to apply to the result of this future.
   * @param <U> the type of the new future
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   */
  <U> ActorFuture<U> thenApply(final Function<V, U> next);

  /**
   * Runs a callback when the future terminates successfully If the caller is not an actor, the
   * consumer is executed in the actor which completes this future. If the caller is not an actor,
   * it is recommended to use {@link ActorFuture#onSuccess(Consumer, Executor)} instead.
   *
   * @param handler to run
   */
  default void onSuccess(final Consumer<V> handler) {
    onComplete(
        (v, error) -> {
          if (error == null) {
            handler.accept(v);
          }
        });
  }

  /**
   * Runs a callback when the future terminates successfully
   *
   * @param handler to run
   */
  default void onSuccess(final Consumer<V> handler, final Executor executor) {
    onComplete(
        (v, error) -> {
          if (error == null) {
            handler.accept(v);
          }
        },
        executor);
  }

  /**
   * Runs a callback when the future terminates exceptionally If the caller is not an actor, the
   * consumer is executed in the actor which completes this future. If the caller is not an actor,
   * it is recommended to use {@link ActorFuture#onError(Consumer, Executor)} instead.
   *
   * @param handler to run
   */
  default void onError(final Consumer<Throwable> handler) {
    onComplete(
        (v, error) -> {
          if (error != null) {
            handler.accept(error);
          }
        });
  }

  /**
   * Runs a callback when the future terminates exceptionally
   *
   * @param handler to run when
   */
  default void onError(final Consumer<Throwable> handler, final Executor executor) {
    onComplete(
        (v, error) -> {
          if (error != null) {
            handler.accept(error);
          }
        },
        executor);
  }

  /**
   * Utility method to convert this future to a {@link CompletableFuture}. The returned future will
   * be completed when this future is completed.
   *
   * @return a completable future
   */
  default CompletableFuture<V> toCompletableFuture() {
    final var future = new CompletableFuture<V>();
    onComplete(
        (status, error) -> {
          if (error == null) {
            future.complete(status);
          } else {
            future.completeExceptionally(error);
          }
        },
        // Since the caller is most likely not an actor, we have to pass an executor. We use
        // Runnable, so it executes in the same actor that completes this future. This is ok because
        // the consumer passed here is not doing much to block the actor.
        Runnable::run);
    return future;
  }
}
