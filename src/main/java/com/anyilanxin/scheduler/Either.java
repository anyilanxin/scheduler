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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * Represents a discriminated union that can hold either a left value or a right value, but not
 * both.
 *
 * <p>This is commonly used for error handling where Left represents an error/failure case and Right
 * represents a success/value case. The API follows functional programming patterns similar to those
 * found in Scala's Either or Haskell's Either types.
 *
 * @param <L> the type of the left value (typically used for errors)
 * @param <R> the type of the right value (typically used for successful results)
 */
public sealed interface Either<L, R> {

  /**
   * Creates an Either instance containing a right value.
   *
   * @param right the right value to wrap
   * @param <L> the type of potential left values
   * @param <R> the type of the right value
   * @return an Either instance containing the right value
   */
  static <L, R> Either<L, R> right(final R right) {
    return new Right<>(right);
  }

  /**
   * Creates an Either instance containing a left value.
   *
   * @param left the left value to wrap
   * @param <L> the type of the left value
   * @param <R> the type of potential right values
   * @return an Either instance containing the left value
   */
  static <L, R> Either<L, R> left(final L left) {
    return new Left<>(left);
  }

  /**
   * Creates an EitherOptional wrapper around an Optional value.
   *
   * @param right the Optional value to wrap
   * @param <R> the type of the optional value
   * @return an EitherOptional instance that can be converted to Either
   */
  static <R> EitherOptional<R> ofOptional(final Optional<R> right) {
    return new EitherOptional<>(right);
  }

  /**
   * Creates a Collector that accumulates Either values into an Either containing lists of left and
   * right values. If any left values are present, the result will be Left with all left values;
   * otherwise Right with all right values.
   *
   * @param <L> the type of left values
   * @param <R> the type of right values
   * @return a Collector for Either values
   */
  static <L, R>
      Collector<Either<L, R>, Tuple<List<L>, List<R>>, Either<List<L>, List<R>>> collector() {
    return Collector.of(
        () -> new Tuple<>(new ArrayList<>(), new ArrayList<>()),
        (acc, next) ->
            next.ifRightOrLeft(right -> acc.getRight().add(right), left -> acc.getLeft().add(left)),
        (a, b) -> {
          a.getLeft().addAll(b.getLeft());
          a.getRight().addAll(b.getRight());
          return a;
        },
        acc -> {
          if (!acc.getLeft().isEmpty()) {
            return left(acc.getLeft());
          } else {
            return right(acc.getRight());
          }
        });
  }

  /**
   * Creates a Collector that folds Either values, accumulating right values into a list. If any
   * left value is encountered, it is preserved and right values collection stops.
   *
   * @param <L> the type of left values
   * @param <R> the type of right values
   * @return a Collector that folds Either values into Either<L, List<R>>
   */
  static <L, R>
      Collector<Either<L, R>, Tuple<Optional<L>, List<R>>, Either<L, List<R>>>
          collectorFoldingLeft() {
    return Collector.of(
        () -> new Tuple<>(Optional.empty(), new ArrayList<>()),
        (acc, next) ->
            next.ifRightOrLeft(
                right -> acc.getRight().add(right),
                left -> acc.setLeft(acc.getLeft().or(() -> Optional.of(left)))),
        (a, b) -> {
          if (a.getLeft().isEmpty() && b.getLeft().isPresent()) {
            a.setLeft(b.getLeft());
          }
          a.getRight().addAll(b.getRight());
          return a;
        },
        acc -> acc.getLeft().map(Either::<L, List<R>>left).orElse(Either.right(acc.getRight())));
  }

  /**
   * Checks if this Either contains a right value.
   *
   * @return true if this is a Right, false otherwise
   */
  boolean isRight();

  /**
   * Checks if this Either contains a left value.
   *
   * @return true if this is a Left, false otherwise
   */
  boolean isLeft();

  /**
   * Gets the right value if this is a Right, otherwise throws NoSuchElementException.
   *
   * @return the right value
   * @throws NoSuchElementException if this is a Left
   */
  R get();

  /**
   * Gets the right value if this is a Right, otherwise returns the provided default value.
   *
   * @param defaultValue the value to return if this is a Left
   * @return the right value or the default value
   */
  R getOrElse(R defaultValue);

  /**
   * Gets the left value if this is a Left, otherwise throws NoSuchElementException.
   *
   * @return the left value
   * @throws NoSuchElementException if this is a Right
   */
  L getLeft();

  /**
   * Maps the right value using the provided function if this is a Right.
   *
   * @param right the mapping function for right values
   * @param <T> the type of the new right value
   * @return a new Either with the mapped right value, or the same Left
   */
  <T> Either<L, T> map(Function<? super R, ? extends T> right);

  /**
   * Maps the left value using the provided function if this is a Left.
   *
   * @param left the mapping function for left values
   * @param <T> the type of the new left value
   * @return a new Either with the mapped left value, or the same Right
   */
  <T> Either<T, R> mapLeft(Function<? super L, ? extends T> left);

  /**
   * Flat maps the right value using the provided function if this is a Right.
   *
   * @param right the flat mapping function for right values
   * @param <T> the type of the new right value
   * @return the result of applying the function to the right value, or the same Left
   */
  <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> right);

  /**
   * Performs an action on the right value if this is a Right and returns this Either.
   *
   * @param action the action to perform on the right value
   * @return this Either instance
   */
  Either<L, R> thenDo(Consumer<R> action);

  /**
   * Performs an action on the right value if this is a Right.
   *
   * @param action the action to perform on the right value
   */
  void ifRight(Consumer<R> action);

  /**
   * Performs an action on the left value if this is a Left.
   *
   * @param action the action to perform on the left value
   */
  void ifLeft(Consumer<L> action);

  /**
   * Performs one of two actions depending on whether this is a Right or Left.
   *
   * @param rightAction the action to perform on the right value
   * @param leftAction the action to perform on the left value
   */
  void ifRightOrLeft(Consumer<R> rightAction, Consumer<L> leftAction);

  /**
   * Folds this Either into a single value by applying the appropriate function.
   *
   * @param leftFn the function to apply if this is a Left
   * @param rightFn the function to apply if this is a Right
   * @param <T> the type of the folded result
   * @return the result of applying the appropriate function
   */
  <T> T fold(Function<? super L, ? extends T> leftFn, Function<? super R, ? extends T> rightFn);

  /**
   * Represents an Either containing a right value. Typically used to represent successful results
   * or valid values.
   */
  record Right<L, R>(R value) implements Either<L, R> {
    @Override
    public boolean isRight() {
      return true;
    }

    @Override
    public boolean isLeft() {
      return false;
    }

    @Override
    public R get() {
      return value;
    }

    @Override
    public R getOrElse(final R defaultValue) {
      return value;
    }

    @Override
    public L getLeft() {
      throw new NoSuchElementException("Expected a left, but this is right");
    }

    @Override
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return Either.right(right.apply(value));
    }

    @Override
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return (Either<T, R>) this;
    }

    @Override
    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return right.apply(value);
    }

    @Override
    public Either<L, R> thenDo(final Consumer<R> action) {
      action.accept(value);
      return this;
    }

    @Override
    public void ifRight(final Consumer<R> right) {
      right.accept(value);
    }

    @Override
    public void ifLeft(final Consumer<L> action) {
      // do nothing
    }

    @Override
    public void ifRightOrLeft(final Consumer<R> rightAction, final Consumer<L> leftAction) {
      rightAction.accept(value);
    }

    @Override
    public <T> T fold(
        final Function<? super L, ? extends T> leftFn,
        final Function<? super R, ? extends T> rightFn) {
      return rightFn.apply(value);
    }
  }

  /**
   * Represents an Either containing a left value. Typically used to represent errors or exceptional
   * cases.
   */
  record Left<L, R>(L value) implements Either<L, R> {

    @Override
    public boolean isRight() {
      return false;
    }

    @Override
    public boolean isLeft() {
      return true;
    }

    @Override
    public R get() {
      throw new NoSuchElementException("Expected a right, but this is left");
    }

    @Override
    public R getOrElse(final R defaultValue) {
      return defaultValue;
    }

    @Override
    public L getLeft() {
      return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return (Either<L, T>) this;
    }

    @Override
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return Either.left(left.apply(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return (Either<L, T>) this;
    }

    @Override
    public Either<L, R> thenDo(final Consumer<R> action) {
      return this;
    }

    @Override
    public void ifRight(final Consumer<R> right) {
      // do nothing
    }

    @Override
    public void ifLeft(final Consumer<L> action) {
      action.accept(value);
    }

    @Override
    public void ifRightOrLeft(final Consumer<R> rightAction, final Consumer<L> leftAction) {
      leftAction.accept(value);
    }

    @Override
    public <T> T fold(
        final Function<? super L, ? extends T> leftFn,
        final Function<? super R, ? extends T> rightFn) {
      return leftFn.apply(value);
    }
  }

  record EitherOptional<R>(Optional<R> right) {
    public <L> Either<L, R> orElse(final L left) {
      return right.<Either<L, R>>map(Either::right).orElse(Either.left(left));
    }
  }
}
