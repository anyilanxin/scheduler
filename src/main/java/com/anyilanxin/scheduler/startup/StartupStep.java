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
package com.anyilanxin.scheduler.startup;

import com.anyilanxin.scheduler.future.ActorFuture;

/**
 * Represents a single step in the startup and shutdown process lifecycle.
 *
 * <p>Implementations of this interface define specific initialization and cleanup operations that
 * need to be performed during application startup and shutdown. Each step is executed sequentially
 * by the {@link StartupProcess} in the order they are registered during startup, and in reverse
 * order during shutdown.
 *
 * <p>The context parameter allows steps to pass information to subsequent steps, enabling flexible
 * configuration and state management throughout the process.
 *
 * @param <CONTEXT> The type of context object passed between steps
 */
public interface StartupStep<CONTEXT> {

  /**
   * Returns name for logging purposes
   *
   * @return name for logging purposes
   */
  String getName();

  /**
   * Executes the startup logic
   *
   * @param context the startup context at the start of this step
   * @return future with startup context at the end of this step
   */
  ActorFuture<CONTEXT> startup(final CONTEXT context);

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @return future with the shutdown context at the end of this step.
   */
  ActorFuture<CONTEXT> shutdown(final CONTEXT context);
}
