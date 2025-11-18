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
package com.anyilanxin.scheduler.channel;

import com.anyilanxin.scheduler.ActorCondition;
import com.anyilanxin.scheduler.ActorJob;
import com.anyilanxin.scheduler.ActorSubscription;
import com.anyilanxin.scheduler.ActorTask;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@SuppressWarnings("restriction")
public class ChannelConsumerCondition
    implements ActorCondition, ActorSubscription, ChannelSubscription {
  private static final VarHandle TRIGGER_COUNT_VAR_HANDLE;

  private final long triggerCount = 0;
  private long processedTiggersCount = 0;

  private final ConsumableChannel channel;
  private final ActorJob job;
  private final ActorTask task;

  static {
    try {
      TRIGGER_COUNT_VAR_HANDLE =
          MethodHandles.lookup()
              .findVarHandle(ChannelConsumerCondition.class, "triggerCount", long.class);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ChannelConsumerCondition(final ActorJob job, final ConsumableChannel channel) {
    this.job = job;
    task = job.getTask();
    this.channel = channel;
  }

  @Override
  public boolean poll() {
    final boolean hasAvailable = channel.hasAvailable();
    return triggerCount > processedTiggersCount || hasAvailable;
  }

  @Override
  public void signal() {
    TRIGGER_COUNT_VAR_HANDLE.getAndAdd(this, 1);
    task.tryWakeup();
  }

  @Override
  public void onJobCompleted() {
    processedTiggersCount++;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return true;
  }

  @Override
  public void cancel() {
    channel.removeConsumer(this);
    task.onSubscriptionCancelled(this);
  }
}
