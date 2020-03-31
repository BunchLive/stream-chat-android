package com.getstream.sdk.chat.rest.response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import io.getstream.chat.android.client.models.Message;

public class GetRepliesResponse {

    @SerializedName("messages")
    @Expose
    private List<Message> messages;

    public List<Message> getMessages() {
        return messages;
    }
}
