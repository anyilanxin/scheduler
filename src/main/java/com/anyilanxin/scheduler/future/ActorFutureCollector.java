/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.anyilanxin.scheduler.future;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;

import com.anyilanxin.scheduler.ConcurrencyControl;
import com.anyilanxin.scheduler.Either;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A collector implementation that aggregates multiple {@link ActorFuture} instances into a single
 * {@link ActorFuture}.
 *
 * <p>This collector collects a stream of {@link ActorFuture} objects and combines them into one
 * {@link ActorFuture} that completes when all the individual futures have completed. If all futures
 * complete successfully, the resulting future contains a list of all values. If any future
 * completes exceptionally, the resulting future completes exceptionally with suppressed exceptions
 * containing all errors.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * List<ActorFuture<String>> futures = ...;
 * ActorFuture<List<String>> aggregated = futures.stream().collect(new ActorFutureCollector<>(concurrencyControl));
 * }</pre>
 *
 * @param <V> the type of value contained in the futures being collected
 */
public final class ActorFutureCollector<V>
    implements Collector<ActorFuture<V>, List<ActorFuture<V>>, ActorFuture<List<V>>> {

  private final ConcurrencyControl concurrencyControl;

  public ActorFutureCollector(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = Objects.requireNonNull(concurrencyControl);
  }

  /**
   * Returns a supplier that creates a new empty list to accumulate {@link ActorFuture} instances.
   *
   * @return a supplier function that returns a new ArrayList
   */
  @Override
  public Supplier<List<ActorFuture<V>>> supplier() {
    return ArrayList::new;
  }

  /**
   * Returns a bi-consumer that adds {@link ActorFuture} instances to the accumulating list.
   *
   * @return a bi-consumer function that adds elements to the list
   */
  @Override
  public BiConsumer<List<ActorFuture<V>>, ActorFuture<V>> accumulator() {
    return List::add;
  }

  /**
   * Returns a binary operator that combines two lists of {@link ActorFuture} instances.
   *
   * @return a binary operator that merges two lists by adding all elements from the second list to
   *     the first
   */
  @Override
  public BinaryOperator<List<ActorFuture<V>>> combiner() {
    return (listA, listB) -> {
      listA.addAll(listB);
      return listA;
    };
  }

  /**
   * Returns a function that converts the accumulated list of {@link ActorFuture} instances into a
   * single {@link ActorFuture} that completes when all futures in the list complete.
   *
   * @return a function that creates a completion waiter and returns its aggregated future
   */
  @Override
  public Function<List<ActorFuture<V>>, ActorFuture<List<V>>> finisher() {
    return futures -> new CompletionWaiter<>(concurrencyControl, futures).get();
  }

  /**
   * Returns the characteristics of this collector.
   *
   * @return an empty set indicating this collector has no special characteristics
   */
  @Override
  public Set<Characteristics> characteristics() {
    return emptySet();
  }

  /**
   * Helper class that waits for all {@link ActorFuture} instances to complete and aggregates their
   * results.
   *
   * <p>This class manages the completion callbacks for all futures and constructs the final
   * aggregated result. It uses {@link Either} to track whether each future completed successfully
   * or with an error.
   */
  private static final class CompletionWaiter<V> implements Supplier<ActorFuture<List<V>>> {
    private final ConcurrencyControl concurrencyControl;
    private final List<ActorFuture<V>> pendingFutures;
    private final Either<Throwable, V>[] results;

    private ActorFuture<List<V>> aggregated;

    private CompletionWaiter(
        final ConcurrencyControl concurrencyControl, final List<ActorFuture<V>> pendingFutures) {
      this.concurrencyControl = concurrencyControl;
      this.pendingFutures = new ArrayList<>(pendingFutures);
      results = new Either[(pendingFutures.size())];
    }

    /**
     * Starts monitoring all pending futures and returns an aggregated future that completes when
     * all futures complete.
     *
     * @return an {@link ActorFuture} that will contain the list of results or complete
     *     exceptionally if any future fails
     */
    @Override
    public ActorFuture<List<V>> get() {
      aggregated = concurrencyControl.createFuture();

      if (pendingFutures.isEmpty()) {
        aggregated.complete(Collections.emptyList());
      } else {
        for (int index = 0; index < pendingFutures.size(); index++) {
          final var pendingFuture = pendingFutures.get(index);

          final var currentIndex = index;
          concurrencyControl.runOnCompletion(
              pendingFuture,
              (result, error) -> handleCompletion(pendingFuture, currentIndex, result, error));
        }
      }

      return aggregated;
    }

    /**
     * Handles the completion of an individual {@link ActorFuture}, storing its result and checking
     * if all futures are complete.
     *
     * @param pendingFuture the future that completed
     * @param currentIndex the index of the completed future in the original list
     * @param result the result value if the future completed successfully
     * @param error the error if the future completed exceptionally
     */
    private void handleCompletion(
        final ActorFuture<V> pendingFuture,
        final int currentIndex,
        final V result,
        final Throwable error) {
      pendingFutures.remove(pendingFuture);

      results[currentIndex] = error == null ? Either.right(result) : Either.left(error);

      if (pendingFutures.isEmpty()) {
        completeAggregatedFuture();
      }
    }

    /**
     * Completes the aggregated future with either the list of results or an exception containing
     * all errors.
     *
     * <p>If all futures completed successfully, completes with the list of values. Otherwise,
     * completes exceptionally with suppressed exceptions for each error that occurred.
     */
    private void completeAggregatedFuture() {
      final var aggregatedResult = stream(results).collect(Either.collector());

      if (aggregatedResult.isRight()) {
        aggregated.complete(aggregatedResult.get());
      } else {
        final var exception =
            new Exception("Errors occurred, see suppressed exceptions for details");

        aggregatedResult.getLeft().forEach(exception::addSuppressed);
        aggregated.completeExceptionally(exception);
      }
    }
  }
}
