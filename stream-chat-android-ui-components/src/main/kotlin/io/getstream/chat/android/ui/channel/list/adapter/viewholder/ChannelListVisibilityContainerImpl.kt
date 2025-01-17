/*
 * Copyright (c) 2014-2023 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.chat.android.ui.channel.list.adapter.viewholder

import com.getstream.sdk.chat.utils.ListenerDelegate
import io.getstream.chat.android.ui.channel.list.ChannelListView.ChannelOptionVisibilityPredicate

internal class ChannelListVisibilityContainerImpl(
    isMoreOptionsVisible: ChannelOptionVisibilityPredicate = ChannelOptionVisibilityPredicate.DEFAULT,
    isDeleteOptionVisible: ChannelOptionVisibilityPredicate = ChannelOptionVisibilityPredicate.DEFAULT,
) : ChannelListVisibilityContainer {

    override var isMoreOptionsVisible: ChannelOptionVisibilityPredicate by ListenerDelegate(isMoreOptionsVisible) { realPredicate ->
        ChannelOptionVisibilityPredicate { channel ->
            realPredicate().invoke(channel)
        }
    }

    override var isDeleteOptionVisible: ChannelOptionVisibilityPredicate by ListenerDelegate(isDeleteOptionVisible) { realPredicate ->
        ChannelOptionVisibilityPredicate { channel ->
            realPredicate().invoke(channel)
        }
    }
}
