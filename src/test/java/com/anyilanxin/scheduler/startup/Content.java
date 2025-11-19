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

import com.anyilanxin.scheduler.ConcurrencyControl;

/**
 *
 * @author zxuanhong
 * @date 2025-11-19 15:22
 * @since
 */
public class Content {
    private final ConcurrencyControl concurrencyControl;

    public Content(final ConcurrencyControl concurrencyControl) {
        this.concurrencyControl = concurrencyControl;
    }

    public ConcurrencyControl getConcurrencyControl() {
        return concurrencyControl;
    }
}
