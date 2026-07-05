package com.xiangxik.echat.chatbot.service.context;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

@Component
public class ContextPolicyParser {

    public ContextPolicyDefinition parse(String dslContent) {
        Document document = parseDocument(dslContent);
        Element root = document.getDocumentElement();
        if (!"contextPolicy".equals(root.getTagName())) {
            throw new ContextDslException(List.of(new ContextDslError(lineOf(dslContent, root.getTagName()),
                    root.getTagName(), "Root tag must be contextPolicy")));
        }

        String name = requiredAttribute(root, "name", dslContent);
        int maxTokens = intAttribute(root, "maxTokens", 0, dslContent);
        List<ContextPolicyDefinition.SystemBlock> systemBlocks = new ArrayList<>();
        List<ContextPolicyDefinition.VariableDefinition> variables = new ArrayList<>();
        List<ContextPolicyDefinition.BudgetReserve> budgetReserves = new ArrayList<>();
        List<ContextPolicyDefinition.PolicyRule> rules = new ArrayList<>();
        List<ContextPolicyDefinition.OutputSection> outputSections = new ArrayList<>();

        for (Element child : childElements(root)) {
            switch (child.getTagName()) {
                case "system" -> systemBlocks.add(new ContextPolicyDefinition.SystemBlock(
                        intAttribute(child, "priority", 0, dslContent), normalizeText(child.getTextContent()),
                        lineOf(dslContent, "system")));
                case "variables" -> parseVariables(child, variables, dslContent);
                case "budget" -> parseBudget(child, budgetReserves, dslContent);
                case "rules" -> parseRules(child, rules, dslContent);
                case "output" -> parseOutput(child, outputSections, dslContent);
                default -> {
                }
            }
        }

        return new ContextPolicyDefinition(name, maxTokens, List.copyOf(systemBlocks), List.copyOf(variables),
                List.copyOf(budgetReserves), List.copyOf(rules), List.copyOf(outputSections));
    }

    Document parseDocument(String dslContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(dslContent)));
            document.getDocumentElement().normalize();
            return document;
        } catch (SAXParseException ex) {
            throw new ContextDslException(List.of(new ContextDslError(ex.getLineNumber(), "xml", ex.getMessage())));
        } catch (Exception ex) {
            throw new ContextDslException(List.of(new ContextDslError(1, "xml", ex.getMessage())));
        }
    }

    static List<Element> childElements(Element element) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < element.getChildNodes().getLength(); index++) {
            Node node = element.getChildNodes().item(index);
            if (node instanceof Element child) {
                elements.add(child);
            }
        }
        return elements;
    }

    static int lineOf(String dslContent, String tagName) {
        int index = dslContent.indexOf("<" + tagName);
        if (index < 0) {
            return 1;
        }
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (dslContent.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private void parseVariables(Element parent, List<ContextPolicyDefinition.VariableDefinition> variables,
                                String dslContent) {
        for (Element var : childElements(parent)) {
            if (!"var".equals(var.getTagName())) {
                continue;
            }
            variables.add(new ContextPolicyDefinition.VariableDefinition(
                    requiredAttribute(var, "name", dslContent),
                    requiredAttribute(var, "source", dslContent),
                    intAttribute(var, "maxMessages", 0, dslContent),
                    intAttribute(var, "limit", 0, dslContent),
                    intAttribute(var, "topK", 0, dslContent),
                    doubleAttribute(var, "minScore", 0, dslContent),
                    booleanAttribute(var, "optional", false),
                    lineOf(dslContent, "var")));
        }
    }

    private void parseBudget(Element parent, List<ContextPolicyDefinition.BudgetReserve> budgetReserves,
                             String dslContent) {
        for (Element reserve : childElements(parent)) {
            if (!"reserve".equals(reserve.getTagName())) {
                continue;
            }
            budgetReserves.add(new ContextPolicyDefinition.BudgetReserve(
                    requiredAttribute(reserve, "target", dslContent),
                    intAttribute(reserve, "tokens", 0, dslContent),
                    lineOf(dslContent, "reserve")));
        }
    }

    private void parseRules(Element parent, List<ContextPolicyDefinition.PolicyRule> rules, String dslContent) {
        for (Element rule : childElements(parent)) {
            String type = rule.getTagName();
            if (!List.of("include", "exclude", "truncate").contains(type)) {
                continue;
            }
            rules.add(new ContextPolicyDefinition.PolicyRule(type, rule.getAttribute("when"),
                    requiredAttribute(rule, "target", dslContent), rule.getAttribute("strategy"), lineOf(dslContent, type)));
        }
    }

    private void parseOutput(Element parent, List<ContextPolicyDefinition.OutputSection> outputSections,
                             String dslContent) {
        for (Element section : childElements(parent)) {
            if (!"section".equals(section.getTagName())) {
                continue;
            }
            outputSections.add(new ContextPolicyDefinition.OutputSection(requiredAttribute(section, "name", dslContent),
                    booleanAttribute(section, "optional", false), lineOf(dslContent, "section")));
        }
    }

    private String requiredAttribute(Element element, String attribute, String dslContent) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            throw new ContextDslException(List.of(new ContextDslError(lineOf(dslContent, element.getTagName()),
                    element.getTagName(), "Missing required attribute: " + attribute)));
        }
        return value.trim();
    }

    private int intAttribute(Element element, String attribute, int defaultValue, String dslContent) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ContextDslException(List.of(new ContextDslError(lineOf(dslContent, element.getTagName()),
                    element.getTagName(), attribute + " must be an integer")));
        }
    }

    private double doubleAttribute(Element element, String attribute, double defaultValue, String dslContent) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw new ContextDslException(List.of(new ContextDslError(lineOf(dslContent, element.getTagName()),
                    element.getTagName(), attribute + " must be a number")));
        }
    }

    private boolean booleanAttribute(Element element, String attribute, boolean defaultValue) {
        String value = element.getAttribute(attribute);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.strip();
    }
}