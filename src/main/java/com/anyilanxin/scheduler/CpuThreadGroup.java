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
package com.anyilanxin.scheduler;

import com.anyilanxin.scheduler.ActorScheduler.ActorSchedulerBuilder;

/** Thread group for the non-blocking, CPU bound, tasks. */
public class CpuThreadGroup extends ActorThreadGroup {

  public CpuThreadGroup(final ActorSchedulerBuilder builder) {
    super(
        String.format("%s-%s", builder.getSchedulerName(), "zb-actors"),
        builder.getCpuBoundActorThreadCount(),
        builder.getPriorityQuotas().length,
        builder);
  }

  @Override
  protected TaskScheduler createTaskScheduler(
          final MultiLevelWorkstealingGroup tasks, final ActorSchedulerBuilder builder) {
    return new PriorityScheduler(tasks::getNextTask, builder.getPriorityQuotas());
  }

  @Override
  protected int getLevel(final ActorTask actorTask) {
    return actorTask.getPriority();
  }
}
