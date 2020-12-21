package io.getstream.chat.android.livedata.usecase

import io.getstream.chat.android.client.call.Call
import io.getstream.chat.android.client.call.CoroutineCall
import io.getstream.chat.android.livedata.ChatDomainImpl
import io.getstream.chat.android.livedata.utils.validateCid

public interface Keystroke {
    /**
     * Keystroke should be called whenever a user enters text into the message input
     * It automatically calls stopTyping when the user stops typing after 5 seconds
     *
     * @param cid the full channel id i. e. messaging:123
     *
     * @return True when a typing event was sent, false if it wasn't sent
     */
    public operator fun invoke(cid: String): Call<Boolean>
}

internal class KeystrokeImpl(private val domainImpl: ChatDomainImpl) : Keystroke {
    override operator fun invoke(cid: String): Call<Boolean> {
        validateCid(cid)

        val channelController = domainImpl.channel(cid)
        return CoroutineCall(domainImpl.scope) {
            channelController.keystroke()
        }
    }
}
