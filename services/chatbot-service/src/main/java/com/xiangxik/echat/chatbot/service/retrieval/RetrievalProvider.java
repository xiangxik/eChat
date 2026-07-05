package com.xiangxik.echat.chatbot.service.retrieval;

import com.xiangxik.echat.chatbot.service.context.ContextMemoryItem;
import java.util.List;

public interface RetrievalProvider {

    List<ContextMemoryItem> retrieve(RetrievalRequest request);
}