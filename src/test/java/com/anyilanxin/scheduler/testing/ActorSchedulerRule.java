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
package com.anyilanxin.scheduler.testing;

import com.anyilanxin.scheduler.Actor;
import com.anyilanxin.scheduler.ActorScheduler;
import com.anyilanxin.scheduler.ActorScheduler.ActorSchedulerBuilder;
import com.anyilanxin.scheduler.clock.ActorClock;
import com.anyilanxin.scheduler.future.ActorFuture;
import com.anyilanxin.scheduler.future.FutureUtil;
import org.junit.rules.ExternalResource;

public class ActorSchedulerRule extends ExternalResource {

  private final int numOfIoThreads;
  private final int numOfThreads;
  private final ActorClock clock;

  private ActorSchedulerBuilder builder;
  private ActorScheduler actorScheduler;

    public ActorSchedulerRule(final int numOfThreads, final ActorClock clock) {
    this(numOfThreads, 2, clock);
  }

    public ActorSchedulerRule(final int numOfThreads, final int numOfIoThreads, final ActorClock clock) {

    this.numOfIoThreads = numOfIoThreads;
    this.numOfThreads = numOfThreads;
    this.clock = clock;
  }

    public ActorSchedulerRule(final int numOfThreads) {
    this(numOfThreads, null);
  }

    public ActorSchedulerRule(final ActorClock clock) {
    this(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), clock);
  }

  public ActorSchedulerRule() {
    this(null);
  }

  @Override
  public void before() {
    builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numOfThreads)
            .setIoBoundActorThreadCount(numOfIoThreads)
            .setActorClock(clock);

    actorScheduler = builder.build();
    actorScheduler.start();
  }

  @Override
  public void after() {
    FutureUtil.join(actorScheduler.stop());
    actorScheduler = null;
    builder = null;
  }

    public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public ActorSchedulerBuilder getBuilder() {
    return builder;
  }
}
