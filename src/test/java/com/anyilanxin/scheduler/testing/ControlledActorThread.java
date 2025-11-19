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

import com.anyilanxin.scheduler.ActorThread;
import com.anyilanxin.scheduler.ActorThreadGroup;
import com.anyilanxin.scheduler.ActorTimerQueue;
import com.anyilanxin.scheduler.TaskScheduler;
import com.anyilanxin.scheduler.clock.ActorClock;
import org.agrona.LangUtil;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ControlledActorThread extends ActorThread {
    private final CyclicBarrier barrier = new CyclicBarrier(2);

  public ControlledActorThread(
          final String name,
          final int id,
          final ActorThreadGroup threadGroup,
          final TaskScheduler taskScheduler,
          final ActorClock clock,
          final ActorTimerQueue timerQueue) {
    super(name, id, threadGroup, taskScheduler, clock, timerQueue);
    idleStrategy = new ControlledIdleStartegy();
  }

  class ControlledIdleStartegy extends ActorTaskRunnerIdleStrategy {
    @Override
    protected void onIdle() {
      super.onIdle();

      try {
        barrier.await();
      } catch (final InterruptedException | BrokenBarrierException e) {
        LangUtil.rethrowUnchecked(e);
      }
    }
  }

  public void workUntilDone() {
    try {
      barrier.await(); // work at least 1 full cycle until the runner becomes idle after having been
      while (barrier.getNumberWaiting() < 1) {
        // spin until thread is idle again
        Thread.yield();
      }
    } catch (final InterruptedException | BrokenBarrierException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }
}
