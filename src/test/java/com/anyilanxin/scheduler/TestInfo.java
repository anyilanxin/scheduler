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
package com.anyilanxin.scheduler;

import com.anyilanxin.scheduler.start.Content;
import com.anyilanxin.scheduler.start.UserInfoStart;
import com.anyilanxin.scheduler.start.UserInfoStartTwo;
import com.anyilanxin.scheduler.startup.StartupProcess;
import com.anyilanxin.scheduler.startup.StartupStep;

import java.util.List;

/**
 *
 * @author zxuanhong
 * @date 2025-11-19 15:15
 * @since
 */
public class TestInfo extends Actor {

    @Override
    protected void onActorStarting() {
        System.out.println("----onActorStarting------");
    }

    @Override
    protected void onActorStarted() {
        System.out.println("-----onActorStarted-----");
        actor.submit(this::handleTest);
    }

    @Override
    protected void onActorClosing() {
        System.out.println("-----onActorClosing-----");
    }

    @Override
    protected void onActorClosed() {
        System.out.println("------onActorClosed----");
    }

    public void handleTest() {
        System.out.println("-handleTest---");
        final List<StartupStep<Content>> startupSteps = List.of(new UserInfoStart(), new UserInfoStartTwo());
        final StartupProcess<Content> startupProcess = new StartupProcess<>(startupSteps);
        startupProcess.startup(this, new Content(actor));
    }

    static void main() {
        try {
            final ActorScheduler build = ActorScheduler.newActorScheduler().setIoBoundActorThreadCount(2)
                    .setCpuBoundActorThreadCount(2)
                    .build();
            build.start();
            build.submitActor(new TestInfo());
        } catch (final Exception e) {
            e.printStackTrace();
        }

    }
}
