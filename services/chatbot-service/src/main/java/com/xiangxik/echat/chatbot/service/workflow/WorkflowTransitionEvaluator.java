package com.xiangxik.echat.chatbot.service.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WorkflowTransitionEvaluator {

    public void validateExpression(String expression) {
        parse(expression);
    }

    public boolean evaluate(String expression, WorkflowTransitionEvaluationContext context) {
        return parse(expression).evaluate(context == null ? emptyContext() : context);
    }

    private Expression parse(String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("conditionExpression must not be blank");
        }
        Parser parser = new Parser(tokenize(expression));
        Expression parsed = parser.parseExpression();
        parser.expect(TokenType.END);
        return parsed;
    }

    private WorkflowTransitionEvaluationContext emptyContext() {
        return new WorkflowTransitionEvaluationContext(null, null, null, null, null, Map.of(), Map.of());
    }

    private List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
            } else if (current == '(') {
                tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                index++;
            } else if (current == ')') {
                tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                index++;
            } else if (current == '&' && peek(expression, index + 1) == '&') {
                tokens.add(new Token(TokenType.AND, "&&"));
                index += 2;
            } else if (current == '|' && peek(expression, index + 1) == '|') {
                tokens.add(new Token(TokenType.OR, "||"));
                index += 2;
            } else if (current == '=' && peek(expression, index + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "=="));
                index += 2;
            } else if (current == '!' && peek(expression, index + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!="));
                index += 2;
            } else if (current == '!') {
                tokens.add(new Token(TokenType.NOT, "!"));
                index++;
            } else if (current == '\'' || current == '"') {
                int next = readString(expression, index, tokens);
                index = next;
            } else if (isIdentifierStart(current)) {
                int start = index;
                index++;
                while (index < expression.length() && isIdentifierPart(expression.charAt(index))) {
                    index++;
                }
                String text = expression.substring(start, index);
                String normalized = text.toLowerCase(Locale.ROOT);
                if ("contains".equals(normalized) || "matches".equals(normalized)) {
                    tokens.add(new Token(TokenType.OPERATOR, normalized));
                } else if ("true".equals(normalized) || "false".equals(normalized)) {
                    tokens.add(new Token(TokenType.BOOLEAN, normalized));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, text));
                }
            } else if (Character.isDigit(current)) {
                int start = index;
                index++;
                while (index < expression.length() && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) {
                    index++;
                }
                tokens.add(new Token(TokenType.NUMBER, expression.substring(start, index)));
            } else {
                throw new IllegalArgumentException("Unexpected token in workflow condition: " + current);
            }
        }
        tokens.add(new Token(TokenType.END, ""));
        return tokens;
    }

    private char peek(String expression, int index) {
        return index >= expression.length() ? '\0' : expression.charAt(index);
    }

    private int readString(String expression, int start, List<Token> tokens) {
        char quote = expression.charAt(start);
        StringBuilder value = new StringBuilder();
        int index = start + 1;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == quote) {
                tokens.add(new Token(TokenType.STRING, value.toString()));
                return index + 1;
            }
            if (current == '\\' && index + 1 < expression.length()) {
                value.append(expression.charAt(index + 1));
                index += 2;
            } else {
                value.append(current);
                index++;
            }
        }
        throw new IllegalArgumentException("Unterminated string in workflow condition");
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '.' || value == '-';
    }

    private enum TokenType {
        IDENTIFIER, STRING, NUMBER, BOOLEAN, OPERATOR, AND, OR, NOT, LEFT_PAREN, RIGHT_PAREN, END
    }

    private record Token(TokenType type, String text) {
    }

    private interface Expression {
        boolean evaluate(WorkflowTransitionEvaluationContext context);
    }

    private interface ValueExpression {
        Object value(WorkflowTransitionEvaluationContext context);
    }

    private static class Parser {
        private final List<Token> tokens;
        private int index;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Expression parseExpression() {
            return parseOr();
        }

        private Expression parseOr() {
            Expression expression = parseAnd();
            while (match(TokenType.OR)) {
                Expression left = expression;
                Expression right = parseAnd();
                expression = context -> left.evaluate(context) || right.evaluate(context);
            }
            return expression;
        }

        private Expression parseAnd() {
            Expression expression = parseUnary();
            while (match(TokenType.AND)) {
                Expression left = expression;
                Expression right = parseUnary();
                expression = context -> left.evaluate(context) && right.evaluate(context);
            }
            return expression;
        }

        private Expression parseUnary() {
            if (match(TokenType.NOT)) {
                Expression expression = parseUnary();
                return context -> !expression.evaluate(context);
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            if (match(TokenType.LEFT_PAREN)) {
                Expression expression = parseExpression();
                expect(TokenType.RIGHT_PAREN);
                return expression;
            }
            ValueExpression left = parseValue();
            if (peek().type() == TokenType.OPERATOR) {
                String operator = advance().text();
                ValueExpression right = parseValue();
                return context -> compare(left.value(context), operator, right.value(context));
            }
            return context -> truthy(left.value(context));
        }

        private ValueExpression parseValue() {
            Token token = advance();
            return switch (token.type()) {
                case IDENTIFIER -> context -> resolveIdentifier(context, token.text());
                case STRING -> context -> token.text();
                case NUMBER -> context -> Double.parseDouble(token.text());
                case BOOLEAN -> context -> Boolean.parseBoolean(token.text());
                default -> throw new IllegalArgumentException("Expected value in workflow condition");
            };
        }

        private boolean match(TokenType type) {
            if (peek().type() == type) {
                index++;
                return true;
            }
            return false;
        }

        private Token advance() {
            Token token = peek();
            if (token.type() != TokenType.END) {
                index++;
            }
            return token;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private void expect(TokenType type) {
            Token token = advance();
            if (token.type() != type) {
                throw new IllegalArgumentException("Expected " + type + " in workflow condition");
            }
        }

        private static Object resolveIdentifier(WorkflowTransitionEvaluationContext context, String identifier) {
            return switch (identifier) {
                case "currentNode.key", "currentNodeKey" -> context.currentNodeKey();
                case "user.message", "latestUserMessage" -> context.latestUserMessage();
                case "assistant.message", "assistantMessage" -> context.assistantMessage();
                case "chatbot.id", "chatbotId" -> context.chatbotId();
                case "conversation.id", "conversationId" -> context.conversationId();
                default -> resolveMapIdentifier(context, identifier);
            };
        }

        private static Object resolveMapIdentifier(WorkflowTransitionEvaluationContext context, String identifier) {
            if (identifier.startsWith("metadata.")) {
                return resolvePath(context.metadata(), identifier.substring("metadata.".length()));
            }
            if (identifier.startsWith("workflowState.")) {
                return resolvePath(context.workflowState(), identifier.substring("workflowState.".length()));
            }
            throw new IllegalArgumentException("Unknown workflow condition variable: " + identifier);
        }

        @SuppressWarnings("unchecked")
        private static Object resolvePath(Map<String, Object> map, String path) {
            Object current = map == null ? null : map;
            for (String segment : path.split("\\.")) {
                if (!(current instanceof Map<?, ?> currentMap)) {
                    return null;
                }
                current = ((Map<String, Object>) currentMap).get(segment);
            }
            return current;
        }

        private static boolean compare(Object left, String operator, Object right) {
            return switch (operator) {
                case "==" -> normalized(left).equals(normalized(right));
                case "!=" -> !normalized(left).equals(normalized(right));
                case "contains" -> String.valueOf(left == null ? "" : left).contains(String.valueOf(right == null ? "" : right));
                case "matches" -> String.valueOf(left == null ? "" : left).matches(String.valueOf(right == null ? "" : right));
                default -> throw new IllegalArgumentException("Unsupported workflow condition operator: " + operator);
            };
        }

        private static String normalized(Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        private static boolean truthy(Object value) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.doubleValue() != 0;
            }
            return StringUtils.hasText(value == null ? null : String.valueOf(value));
        }
    }
}