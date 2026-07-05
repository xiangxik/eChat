package com.xiangxik.echat.chatbot.service.eval;

import java.util.Map;

public record EvalScore(Map<String, Object> scores, boolean passed) {
}
