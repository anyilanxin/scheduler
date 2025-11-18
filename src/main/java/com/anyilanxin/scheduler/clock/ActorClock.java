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
package com.anyilanxin.scheduler.clock;

import com.anyilanxin.scheduler.ActorThread;
import java.time.Instant;
import java.time.InstantSource;

public interface ActorClock extends InstantSource {
  boolean update();

  long getTimeMillis();

  long getNanosSinceLastMillisecond();

  long getNanoTime();

  static ActorClock current() {
    final ActorThread current = ActorThread.current();
    return current != null ? current.getClock() : null;
  }

  static long currentTimeMillis() {
    final ActorClock clock = current();
    return clock != null ? clock.getTimeMillis() : System.currentTimeMillis();
  }

  @Override
  default Instant instant() {
    return Instant.ofEpochMilli(getTimeMillis());
  }

  @Override
  default long millis() {
    return getTimeMillis();
  }
}
