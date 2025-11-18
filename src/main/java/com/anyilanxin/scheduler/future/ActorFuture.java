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

  /**
   * Completes this future with the given value.
   *
   * @param value the value to complete this future with
   */
  void complete(V value);

  /**
   * Registers a consumer to be notified when this future completes.
   *
   * @param consumer the consumer to be notified
   */
  void onComplete(BiConsumer<V, Throwable> consumer);

  /**
   * Registers a consumer to be notified when this future completes using the provided executor.
   *
   * @param consumer the consumer to be notified
   * @param executor the executor to run the consumer on
   */
  void onComplete(BiConsumer<V, Throwable> consumer, Executor executor);

  @Override
  default void accept(final V value, final Throwable throwable) {
    if (throwable != null) {
      completeExceptionally(throwable);
    } else {
      complete(value);
    }
  }

  /**
   * Completes this future exceptionally with the given failure message and throwable.
   *
   * @param failure the failure message
   * @param throwable the throwable causing the exception
   */
  void completeExceptionally(String failure, Throwable throwable);

  /**
   * Completes this future exceptionally with the given throwable.
   *
   * @param throwable the throwable causing the exception
   */
  void completeExceptionally(Throwable throwable);

  /**
   * Waits if necessary for this future to complete, and then returns its result.
   *
   * @return the computed result
   */
  V join();

  /**
   * Waits if necessary for at most the given time for this future to complete, and then returns its
   * result.
   *
   * @param timeout the maximum time to wait
   * @param timeUnit the time unit of the timeout argument
   * @return the computed result
   */
  V join(long timeout, TimeUnit timeUnit);

  /**
   * Blocks the given task until this future completes. To be used by scheduler only.
   *
   * @param onCompletion the task to block
   */
  void block(ActorTask onCompletion);

  /**
   * Returns true if this future completed exceptionally, false otherwise.
   *
   * @return true if this future completed exceptionally
   */
  boolean isCompletedExceptionally();

  /**
   * Returns the exception that caused this future to complete exceptionally, or null if it
   * completed normally.
   *
   * @return the exception, or null if none
   */
  Throwable getException();

  /**
   * Chains another future-producing operation to execute after this future completes.
   *
   * @param next supplier of the next future
   * @param executor the executor to run the next operation on
   * @param <U> the type of the result of the next future
   * @return a new future representing the result of the next operation
   */
  <U> ActorFuture<U> andThen(Supplier<ActorFuture<U>> next, Executor executor);

  /**
   * Chains another future-producing operation that takes the result of this future as input.
   *
   * @param next function that takes this future's result and produces the next future
   * @param executor the executor to run the next operation on
   * @param <U> the type of the result of the next future
   * @return a new future representing the result of the next operation
   */
  <U> ActorFuture<U> andThen(Function<V, ActorFuture<U>> next, Executor executor);

  /**
   * Chains another future-producing operation that takes both the result and exception of this
   * future.
   *
   * @param next function that takes this future's result and exception and produces the next future
   * @param executor the executor to run the next operation on
   * @param <U> the type of the result of the next future
   * @return a new future representing the result of the next operation
   */
  <U> ActorFuture<U> andThen(BiFunction<V, Throwable, ActorFuture<U>> next, Executor executor);

  /**
   * Transforms the result of this future using the provided function.
   *
   * @param next function to transform the result
   * @param executor the executor to run the transformation on
   * @param <U> the type of the transformed result
   * @return a new future representing the transformed result
   */
  <U> ActorFuture<U> thenApply(Function<V, U> next, Executor executor);

  /**
   * Transforms the result of this future using the provided function.
   *
   * @param next function to transform the result
   * @param <U> the type of the transformed result
   * @return a new future representing the transformed result
   */
  <U> ActorFuture<U> thenApply(final Function<V, U> next);

  /**
   * Registers a handler to be executed when this future completes successfully.
   *
   * @param handler the handler to execute on successful completion
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
   * Registers a handler to be executed when this future completes successfully using the provided
   * executor.
   *
   * @param handler the handler to execute on successful completion
   * @param executor the executor to run the handler on
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
   * Registers a handler to be executed when this future completes exceptionally.
   *
   * @param handler the handler to execute on exceptional completion
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
   * Registers a handler to be executed when this future completes exceptionally using the provided
   * executor.
   *
   * @param handler the handler to execute on exceptional completion
   * @param executor the executor to run the handler on
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
   * Converts this ActorFuture to a standard CompletableFuture.
   *
   * @return a CompletableFuture representing the same computation
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
        Runnable::run);
    return future;
  }
}
