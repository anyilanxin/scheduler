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
package com.anyilanxin.scheduler.future;

import com.anyilanxin.scheduler.Loggers;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public class FutureUtil {
  private FutureUtil() {}

  private static final Logger LOG = Loggers.FUTURE_LOGGER;

  /**
   * Invokes Future.get() returning the result of the invocation. Transforms checked exceptions into
   * RuntimeExceptions to accommodate programmer laziness.
   */
  public static <T> T join(final Future<T> f) {
    try {
      return f.get();
    } catch (final Exception e) {
      // NOTE: here we actually want to use rethrowUnchecked
      LangUtil.rethrowUnchecked(e);
    }

    return null;
  }

  public static <T> T join(final Future<T> f, final long timeout, final TimeUnit timeUnit) {
    try {
      return f.get(timeout, timeUnit);
    } catch (final Exception e) {
      LOG.error("join timeout");
      // NOTE: here we actually want to use rethrowUnchecked
      LangUtil.rethrowUnchecked(e);
    }

    return null;
  }

  public static Runnable wrap(final Future<?> future) {
    return () -> {
      try {
        future.get();
      } catch (final Exception e) {
        LOG.error("wrap timeout");
        LangUtil.rethrowUnchecked(e);
      }
    };
  }
}
