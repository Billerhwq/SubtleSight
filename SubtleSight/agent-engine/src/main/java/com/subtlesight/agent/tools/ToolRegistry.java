package com.subtlesight.agent.tools;

import com.subtlesight.agent.tools.ToolModels.*;
import com.subtlesight.agent.tools.annotations.AgentTool;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Registers tool contracts, exports provider adapters, validates calls, and normalizes results. */
public final class ToolRegistry {

    private final Map<String, ToolDefinition> toolsByName = new ConcurrentHashMap<>();
    private final Map<String, ToolDefinition> toolsById = new ConcurrentHashMap<>();
    private final Map<String, CachedResult> idempotencyCache = new ConcurrentHashMap<>();

    /** Register all {@code @AgentTool} methods on the given bean instance. */
    public void register(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation == null) continue;
            ToolDefinition definition = ToolDefinition.from(method, annotation, bean);
            ToolDefinition nameCollision = toolsByName.putIfAbsent(definition.name(), definition);
            if (nameCollision != null) throw new IllegalStateException("Duplicate tool name: " + definition.name());
            ToolDefinition idCollision = toolsById.putIfAbsent(definition.id(), definition);
            if (idCollision != null) {
                toolsByName.remove(definition.name(), definition);
                throw new IllegalStateException("Duplicate tool id: " + definition.id());
            }
        }
    }

    public Optional<ToolDefinition> get(String nameOrId) {
        ToolDefinition byName = toolsByName.get(nameOrId);
        return Optional.ofNullable(byName != null ? byName : toolsById.get(nameOrId));
    }

    public List<ToolDefinition> all() {
        return toolsByName.values().stream()
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
    }

    public Set<String> toolNames() {
        return Set.copyOf(toolsByName.keySet());
    }

    public Set<String> highRiskTools() {
        Set<String> result = new LinkedHashSet<>();
        for (ToolDefinition definition : all()) {
            if (definition.risk() == RiskLevel.HIGH) result.add(definition.name());
        }
        return Set.copyOf(result);
    }

    /** Execute the provider-neutral call and always return a structured result envelope. */
    public ToolResult execute(ToolCall call) {
        Instant startedAt = Instant.now();
        ToolDefinition definition = get(call.toolId()).orElseGet(() -> get(call.toolName()).orElse(null));
        if (definition == null) {
            return failed(call, "UNKNOWN_TOOL", "unknown tool: " + call.toolName(), false, startedAt);
        }
        if (!definition.version().equals(call.toolVersion())) {
            return failed(call, "TOOL_VERSION_MISMATCH",
                    "tool version mismatch: expected " + definition.version() + " but received " + call.toolVersion(),
                    false, startedAt);
        }

        ToolError validationError = validateArguments(definition, call.arguments());
        if (validationError != null) {
            return failed(call, validationError, startedAt);
        }

        IdempotencyPolicy idempotency = definition.runtime().idempotency();
        if (idempotency.mode() == IdempotencyMode.REQUIRED
                && (call.idempotencyKey() == null || call.idempotencyKey().isBlank())) {
            return failed(call, "IDEMPOTENCY_KEY_REQUIRED", "tool requires an idempotency key", false, startedAt);
        }
        String cacheKey = call.idempotencyKey() == null || call.idempotencyKey().isBlank()
                ? null : definition.id() + ":" + call.idempotencyKey();
        if (cacheKey != null) {
            cleanupExpiredCache();
            CachedResult cached = idempotencyCache.get(cacheKey);
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.result();
        }

        try {
            Map<String, Object> raw = definition.invokeRaw(call.arguments());
            if (raw.containsKey("error")) {
                ToolError error = new ToolError("TOOL_EXECUTION_FAILED",
                        String.valueOf(raw.get("error")), false, Map.of());
                return failed(call, error, startedAt);
            }

            List<ToolEffect> effects = buildEffects(definition, call, raw);
            boolean effectsVerified = definition.effects().stream()
                    .filter(EffectDeclaration::requiredOnSuccess)
                    .allMatch(declaration -> effects.stream().anyMatch(effect ->
                            effect.type() == declaration.type()
                                    && effect.resource().type().equals(declaration.resourceType())
                                    && (!declaration.verifiable() || effect.verified())));

            Instant finishedAt = Instant.now();
            ToolStatus status = effectsVerified ? ToolStatus.SUCCEEDED : ToolStatus.PARTIAL;
            ToolError error = effectsVerified ? null : new ToolError(
                    "EFFECT_NOT_VERIFIED", "tool returned without a verifiable required effect", false, Map.of());
            String content = effectsVerified
                    ? definition.presentation().progressLabel("succeeded")
                    : "工具已执行，但未能验证资源修改结果";
            ToolResult result = new ToolResult(
                    ToolModels.PROTOCOL_VERSION,
                    call.callId(),
                    definition.id(),
                    definition.name(),
                    definition.version(),
                    status,
                    raw,
                    effects,
                    error,
                    content,
                    new ToolMetrics(Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli()),
                            1, startedAt, finishedAt));
            cacheResult(cacheKey, idempotency, result);
            return result;
        } catch (Exception exception) {
            return failed(call, "TOOL_EXCEPTION",
                    exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    false, startedAt);
        }
    }

    /** Backward-compatible map API for older callers. */
    public Map<String, Object> execute(String toolName, Map<String, Object> args) {
        ToolDefinition definition = get(toolName).orElse(null);
        if (definition == null) return Map.of("error", "unknown tool: " + toolName);
        String callId = "legacy_" + UUID.randomUUID();
        ToolCall call = new ToolCall(
                ToolModels.PROTOCOL_VERSION,
                callId,
                definition.id(),
                definition.name(),
                definition.version(),
                args,
                resourceTarget(definition, args, Map.of()),
                Map.of(),
                definition.runtime().idempotency().mode() == IdempotencyMode.REQUIRED ? callId : null);
        ToolResult result = execute(call);
        if (result.succeeded()) return result.data() == null ? Map.of() : result.data();
        return Map.of("error", result.error() == null ? "tool failed" : result.error().message());
    }

    public List<Map<String, Object>> definitions() {
        return all().stream().map(ToolDefinition::toDefinitionMap).toList();
    }

    /** OpenAI Responses/Chat Completions adapter. */
    public List<Map<String, Object>> functionSchemas() {
        return all().stream().map(ToolDefinition::toOpenAiFunctionSchema).toList();
    }

    /** Anthropic Messages API adapter. */
    public List<Map<String, Object>> anthropicSchemas() {
        return all().stream().map(ToolDefinition::toAnthropicToolSchema).toList();
    }

    public String toolSummary() {
        StringBuilder summary = new StringBuilder();
        for (ToolDefinition definition : all()) {
            summary.append("- ").append(definition.name()).append(": ").append(definition.description());
            if (definition.risk() == RiskLevel.HIGH) summary.append(" [高风险]");
            summary.append('\n');
        }
        return summary.toString();
    }

    public static ResourceRef resourceTarget(ToolDefinition definition, Map<String, Object> arguments,
                                             Map<String, Object> context) {
        if (definition.effects().isEmpty()) return null;
        EffectDeclaration declaration = definition.effects().getFirst();
        Object id = first(arguments, "documentId", "resourceId", "drawId", "id");
        if (id == null) id = first(context, "currentDocId", "documentId", "resourceId");
        if (id == null || id.toString().isBlank()) return null;
        Integer expectedVersion = ToolModels.versionFrom(arguments, "expectedVersion", "version");
        if (expectedVersion == null) {
            expectedVersion = ToolModels.versionFrom(context, "currentDocVersion", "expectedVersion", "version");
        }
        return new ResourceRef(declaration.resourceType(), id.toString(), expectedVersion);
    }

    private static ToolError validateArguments(ToolDefinition definition, Map<String, Object> arguments) {
        Set<String> allowed = new LinkedHashSet<>();
        for (ParamDef param : definition.params()) allowed.add(param.name());
        Set<String> unknown = new LinkedHashSet<>(arguments.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            return new ToolError("UNKNOWN_ARGUMENT", "unknown arguments: " + unknown, false,
                    Map.of("unknown", List.copyOf(unknown)));
        }
        for (ParamDef param : definition.params()) {
            if (param.required() && (!arguments.containsKey(param.name()) || arguments.get(param.name()) == null)) {
                return new ToolError("MISSING_ARGUMENT", "missing required argument: " + param.name(), false,
                        Map.of("argument", param.name()));
            }
            if (arguments.containsKey(param.name())) {
                String schemaError = validateValue(param.toSchema(), arguments.get(param.name()), param.name());
                if (schemaError != null) {
                    return new ToolError("INVALID_ARGUMENT_SCHEMA", schemaError, false,
                            Map.of("argument", param.name()));
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String validateValue(Map<String, Object> schema, Object value, String path) {
        if (schema.get("anyOf") instanceof Collection<?> branches) {
            for (Object branch : branches) {
                if (branch instanceof Map<?, ?> map
                        && validateValue((Map<String, Object>) map, value, path) == null) return null;
            }
            return path + " does not match any allowed schema";
        }

        Object declaredType = schema.get("type");
        if (!matchesType(declaredType, value)) {
            return path + " has invalid type; expected " + declaredType + " but received "
                    + (value == null ? "null" : value.getClass().getSimpleName());
        }
        if (value == null) return null;

        if (schema.get("enum") instanceof Collection<?> allowed && !allowed.contains(value)) {
            return path + " must be one of " + allowed;
        }
        if (value instanceof String text) {
            Number minLength = schema.get("minLength") instanceof Number number ? number : null;
            Number maxLength = schema.get("maxLength") instanceof Number number ? number : null;
            if (minLength != null && text.length() < minLength.intValue()) return path + " is too short";
            if (maxLength != null && text.length() > maxLength.intValue()) return path + " is too long";
            if (schema.get("pattern") instanceof String pattern && !text.matches(pattern)) {
                return path + " does not match " + pattern;
            }
        }
        if (value instanceof Number number) {
            Number minimum = schema.get("minimum") instanceof Number min ? min : null;
            Number maximum = schema.get("maximum") instanceof Number max ? max : null;
            if (minimum != null && number.doubleValue() < minimum.doubleValue()) return path + " is below minimum";
            if (maximum != null && number.doubleValue() > maximum.doubleValue()) return path + " exceeds maximum";
        }
        if (value instanceof Collection<?> values) {
            Number minItems = schema.get("minItems") instanceof Number number ? number : null;
            Number maxItems = schema.get("maxItems") instanceof Number number ? number : null;
            if (minItems != null && values.size() < minItems.intValue()) return path + " has too few items";
            if (maxItems != null && values.size() > maxItems.intValue()) return path + " has too many items";
            if (schema.get("items") instanceof Map<?, ?> itemSchema) {
                int index = 0;
                for (Object item : values) {
                    String error = validateValue((Map<String, Object>) itemSchema, item,
                            path + "[" + index++ + "]");
                    if (error != null) return error;
                }
            }
        }
        if (value instanceof Map<?, ?> object) {
            Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> rawProperties
                    ? (Map<String, Object>) rawProperties : Map.of();
            if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                for (Object key : object.keySet()) {
                    if (!properties.containsKey(String.valueOf(key))) return path + " contains unknown field " + key;
                }
            }
            if (schema.get("required") instanceof Collection<?> required) {
                for (Object field : required) {
                    if (!object.containsKey(field)) return path + " is missing field " + field;
                }
            }
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                if (!object.containsKey(property.getKey())) continue;
                if (property.getValue() instanceof Map<?, ?> propertySchema) {
                    String error = validateValue((Map<String, Object>) propertySchema,
                            object.get(property.getKey()), path + "." + property.getKey());
                    if (error != null) return error;
                }
            }
        }
        return null;
    }

    private static boolean matchesType(Object declaredType, Object value) {
        if (declaredType == null) return true;
        if (declaredType instanceof Collection<?> types) {
            for (Object type : types) if (matchesType(type, value)) return true;
            return false;
        }
        if (!(declaredType instanceof String type)) return true;
        return switch (type) {
            case "null" -> value == null;
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Collection<?> || value != null && value.getClass().isArray();
            case "object" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }

    private static List<ToolEffect> buildEffects(ToolDefinition definition, ToolCall call,
                                                  Map<String, Object> data) {
        List<ToolEffect> effects = new ArrayList<>();
        for (EffectDeclaration declaration : definition.effects()) {
            Object resourceId = first(data, "documentId", "drawId", "id", "resourceId");
            if (resourceId == null && call.target() != null) resourceId = call.target().id();
            if (resourceId == null || resourceId.toString().isBlank()) continue;
            Integer versionBefore = ToolModels.versionFrom(data, "previousVersion", "versionBefore");
            if (versionBefore == null && call.target() != null) versionBefore = call.target().expectedVersion();
            Integer versionAfter = ToolModels.versionFrom(data, "version", "versionAfter");
            boolean verified = !declaration.verifiable()
                    || declaration.type() == EffectType.RESOURCE_DELETED
                    || versionAfter != null;
            ResourceRef resource = new ResourceRef(declaration.resourceType(), resourceId.toString(), versionAfter);
            effects.add(new ToolEffect(
                    "effect_" + UUID.randomUUID(), declaration.type(), resource,
                    declaration.changedFields(), versionBefore, versionAfter, verified));
        }
        return List.copyOf(effects);
    }

    private static Object first(Map<String, Object> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private static ToolResult failed(ToolCall call, String code, String message,
                                     boolean retryable, Instant startedAt) {
        return failed(call, new ToolError(code, message, retryable, Map.of()), startedAt);
    }

    private static ToolResult failed(ToolCall call, ToolError error, Instant startedAt) {
        Instant finishedAt = Instant.now();
        return new ToolResult(
                ToolModels.PROTOCOL_VERSION,
                call.callId(),
                call.toolId(),
                call.toolName(),
                call.toolVersion(),
                ToolStatus.FAILED,
                null,
                List.of(),
                error,
                error.message(),
                new ToolMetrics(Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli()),
                        1, startedAt, finishedAt));
    }

    private void cacheResult(String cacheKey, IdempotencyPolicy policy, ToolResult result) {
        if (cacheKey == null || !result.succeeded()) return;
        long ttl = policy.ttlSeconds() == null ? 86_400 : policy.ttlSeconds();
        idempotencyCache.put(cacheKey, new CachedResult(result, Instant.now().plusSeconds(ttl)));
    }

    private void cleanupExpiredCache() {
        Instant now = Instant.now();
        idempotencyCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record CachedResult(ToolResult result, Instant expiresAt) {}
}
