package com.subtlesight.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.ToolEffectSpec;
import com.subtlesight.agent.tools.annotations.ToolParam;
import com.subtlesight.agent.tools.annotations.ToolPresentation;
import com.subtlesight.agent.tools.annotations.ToolRuntime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

/** Provider-neutral value objects for the typed tool protocol. */
public final class ToolModels {
    public static final String PROTOCOL_VERSION = "1.0";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolModels() {}

    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum SideEffect { NONE, READ, WRITE, EXTERNAL }
    public enum ConfirmationMode { NEVER, POLICY, ALWAYS }
    public enum IdempotencyMode { NOT_SUPPORTED, OPTIONAL, REQUIRED }
    public enum ConcurrencyMode { PARALLEL, SERIAL_PER_RESOURCE, GLOBAL_SERIAL }
    public enum EffectType { RESOURCE_CREATED, RESOURCE_UPDATED, RESOURCE_DELETED, EXTERNAL_DISPATCHED }
    public enum ToolStatus { SUCCEEDED, FAILED, PARTIAL, CANCELLED, REQUIRES_CONFIRMATION }

    public record ParamDef(
            String name,
            String description,
            boolean required,
            Class<?> type,
            Type genericType,
            List<String> allowedValues,
            Map<String, Object> schemaOverride) {

        public ParamDef {
            requireText(name, "parameter name");
            description = description == null ? "" : description;
            allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
            schemaOverride = immutableMap(schemaOverride);
        }

        public Map<String, Object> toSchema() {
            Map<String, Object> schema = schemaOverride.isEmpty()
                    ? schemaFor(genericType == null ? type : genericType)
                    : new LinkedHashMap<>(schemaOverride);
            if (!allowedValues.isEmpty()) schema.put("enum", allowedValues);
            if (!description.isBlank()) schema.putIfAbsent("description", description);
            if (!required) makeNullable(schema);
            return Collections.unmodifiableMap(schema);
        }
    }

    public record RetryPolicy(int maxAttempts, long backoffMs, List<String> retryableErrorCodes) {
        public RetryPolicy {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            if (backoffMs < 0) throw new IllegalArgumentException("backoffMs must be >= 0");
            retryableErrorCodes = retryableErrorCodes == null ? List.of() : List.copyOf(retryableErrorCodes);
        }

        public Map<String, Object> toMap() {
            return Map.of("maxAttempts", maxAttempts, "backoffMs", backoffMs,
                    "retryableErrorCodes", retryableErrorCodes);
        }
    }

    public record IdempotencyPolicy(IdempotencyMode mode, Long ttlSeconds) {
        public IdempotencyPolicy {
            Objects.requireNonNull(mode);
            if (ttlSeconds != null && ttlSeconds < 1) throw new IllegalArgumentException("ttlSeconds must be positive");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", enumValue(mode));
            result.put("ttlSeconds", ttlSeconds);
            return Collections.unmodifiableMap(result);
        }
    }

    public record RuntimeDefinition(
            String executor,
            String location,
            List<String> contextRequirements,
            SideEffect sideEffect,
            RiskLevel riskLevel,
            List<String> permissions,
            ConfirmationMode confirmation,
            long timeoutMs,
            RetryPolicy retry,
            IdempotencyPolicy idempotency,
            ConcurrencyMode concurrency,
            boolean supportsCancellation) {

        public RuntimeDefinition {
            requireText(executor, "executor");
            requireText(location, "location");
            contextRequirements = contextRequirements == null ? List.of() : List.copyOf(contextRequirements);
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
            Objects.requireNonNull(sideEffect);
            Objects.requireNonNull(riskLevel);
            Objects.requireNonNull(confirmation);
            Objects.requireNonNull(retry);
            Objects.requireNonNull(idempotency);
            Objects.requireNonNull(concurrency);
            if (timeoutMs < 100) throw new IllegalArgumentException("timeoutMs must be >= 100");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("executor", executor);
            result.put("location", location);
            result.put("contextRequirements", contextRequirements);
            result.put("sideEffect", enumValue(sideEffect));
            result.put("riskLevel", enumValue(riskLevel));
            result.put("permissions", permissions);
            result.put("confirmation", enumValue(confirmation));
            result.put("timeoutMs", timeoutMs);
            result.put("retry", retry.toMap());
            result.put("idempotency", idempotency.toMap());
            result.put("concurrency", enumValue(concurrency));
            result.put("supportsCancellation", supportsCancellation);
            return Collections.unmodifiableMap(result);
        }
    }

    public record EffectDeclaration(
            EffectType type,
            String resourceType,
            List<String> changedFields,
            boolean requiredOnSuccess,
            boolean verifiable) {

        public EffectDeclaration {
            Objects.requireNonNull(type);
            requireText(resourceType, "resourceType");
            changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "type", effectValue(type),
                    "resourceType", resourceType,
                    "changedFields", changedFields,
                    "requiredOnSuccess", requiredOnSuccess,
                    "verifiable", verifiable);
        }
    }

    public record PresentationDefinition(
            String label,
            String category,
            String icon,
            Map<String, String> progressLabels) {

        public PresentationDefinition {
            requireText(label, "label");
            category = category == null || category.isBlank() ? "Agent" : category;
            icon = icon == null || icon.isBlank() ? "wrench" : icon;
            progressLabels = progressLabels == null ? Map.of() : Map.copyOf(progressLabels);
        }

        public String progressLabel(String phase) {
            return progressLabels.getOrDefault(phase, label);
        }

        public Map<String, Object> toMap() {
            return Map.of("label", label, "category", category, "icon", icon,
                    "progressLabels", progressLabels);
        }
    }

    public record ResourceRef(String type, String id, Integer expectedVersion) {
        public ResourceRef {
            requireText(type, "resource type");
            requireText(id, "resource id");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("id", id);
            result.put("expectedVersion", expectedVersion);
            return Collections.unmodifiableMap(result);
        }
    }

    public record ToolCall(
            String protocolVersion,
            String callId,
            String toolId,
            String toolName,
            String toolVersion,
            Map<String, Object> arguments,
            ResourceRef target,
            Map<String, Object> context,
            String idempotencyKey) {

        public ToolCall {
            protocolVersion = protocolVersion == null ? PROTOCOL_VERSION : protocolVersion;
            requireText(callId, "callId");
            requireText(toolId, "toolId");
            requireText(toolName, "toolName");
            requireText(toolVersion, "toolVersion");
            arguments = immutableMap(arguments);
            context = immutableMap(context);
        }
    }

    public record ToolEffect(
            String effectId,
            EffectType type,
            ResourceRef resource,
            List<String> changedFields,
            Integer versionBefore,
            Integer versionAfter,
            boolean verified) {

        public ToolEffect {
            requireText(effectId, "effectId");
            Objects.requireNonNull(type);
            Objects.requireNonNull(resource);
            changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("effectId", effectId);
            result.put("type", effectValue(type));
            result.put("resource", resource.toMap());
            result.put("changedFields", changedFields);
            result.put("versionBefore", versionBefore);
            result.put("versionAfter", versionAfter);
            result.put("verified", verified);
            return Collections.unmodifiableMap(result);
        }
    }

    public record ToolError(String code, String message, boolean retryable, Map<String, Object> details) {
        public ToolError {
            requireText(code, "error code");
            requireText(message, "error message");
            details = immutableMap(details);
        }

        public Map<String, Object> toMap() {
            return Map.of("code", code, "message", message, "retryable", retryable, "details", details);
        }
    }

    public record ToolMetrics(long durationMs, int attempts, Instant startedAt, Instant finishedAt) {
        public ToolMetrics {
            if (durationMs < 0) throw new IllegalArgumentException("durationMs must be >= 0");
            if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
            Objects.requireNonNull(startedAt);
            Objects.requireNonNull(finishedAt);
        }

        public Map<String, Object> toMap() {
            return Map.of("durationMs", durationMs, "attempts", attempts,
                    "startedAt", startedAt.toString(), "finishedAt", finishedAt.toString());
        }
    }

    public record ToolResult(
            String protocolVersion,
            String callId,
            String toolId,
            String toolName,
            String toolVersion,
            ToolStatus status,
            Map<String, Object> data,
            List<ToolEffect> effects,
            ToolError error,
            String content,
            ToolMetrics metrics) {

        public ToolResult {
            protocolVersion = protocolVersion == null ? PROTOCOL_VERSION : protocolVersion;
            requireText(callId, "callId");
            requireText(toolId, "toolId");
            requireText(toolName, "toolName");
            requireText(toolVersion, "toolVersion");
            Objects.requireNonNull(status);
            data = data == null ? null : immutableMap(data);
            effects = effects == null ? List.of() : List.copyOf(effects);
            content = content == null ? "" : content;
            Objects.requireNonNull(metrics);
        }

        public boolean succeeded() {
            return status == ToolStatus.SUCCEEDED;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protocolVersion", protocolVersion);
            result.put("callId", callId);
            result.put("toolId", toolId);
            result.put("toolName", toolName);
            result.put("toolVersion", toolVersion);
            result.put("status", enumValue(status));
            result.put("data", data);
            result.put("effects", effects.stream().map(ToolEffect::toMap).toList());
            result.put("error", error == null ? null : error.toMap());
            result.put("content", content);
            result.put("metrics", metrics.toMap());
            return Collections.unmodifiableMap(result);
        }
    }

    public record ToolDefinition(
            String protocolVersion,
            String id,
            String version,
            String name,
            String description,
            boolean strict,
            RiskLevel risk,
            List<ParamDef> params,
            RuntimeDefinition runtime,
            Map<String, Object> resultDataSchema,
            List<EffectDeclaration> effects,
            PresentationDefinition presentation,
            Method method,
            Object target) {

        public ToolDefinition {
            protocolVersion = protocolVersion == null ? PROTOCOL_VERSION : protocolVersion;
            requireText(id, "tool id");
            requireText(version, "tool version");
            requireText(name, "tool name");
            requireText(description, "tool description");
            Objects.requireNonNull(risk);
            params = params == null ? List.of() : List.copyOf(params);
            Objects.requireNonNull(runtime);
            resultDataSchema = immutableMap(resultDataSchema);
            effects = effects == null ? List.of() : List.copyOf(effects);
            Objects.requireNonNull(presentation);
            Objects.requireNonNull(method);
            Objects.requireNonNull(target);
        }

        public Map<String, Object> inputSchema() {
            return buildInputSchema(params);
        }

        /** Complete internal definition. Runtime method/bean references are intentionally excluded. */
        public Map<String, Object> toDefinitionMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protocolVersion", protocolVersion);
            result.put("id", id);
            result.put("version", version);
            result.put("name", name);
            result.put("description", description);
            result.put("strict", strict);
            result.put("inputSchema", inputSchema());
            result.put("runtime", runtime.toMap());
            result.put("resultDataSchema", resultDataSchema);
            result.put("effects", effects.stream().map(EffectDeclaration::toMap).toList());
            result.put("presentation", presentation.toMap());
            result.put("deprecated", false);
            return Collections.unmodifiableMap(result);
        }

        public Map<String, Object> toOpenAiFunctionSchema() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "function");
            result.put("name", name);
            result.put("description", description);
            result.put("parameters", inputSchema());
            result.put("strict", strict);
            return Collections.unmodifiableMap(result);
        }

        public Map<String, Object> toAnthropicToolSchema() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("description", description);
            result.put("input_schema", inputSchema());
            result.put("strict", strict);
            return Collections.unmodifiableMap(result);
        }

        /** Backward-compatible alias used by the prompt planner. */
        public Map<String, Object> toFunctionSchema() {
            return toOpenAiFunctionSchema();
        }

        public Map<String, Object> invokeRaw(Map<String, Object> args) throws Exception {
            Object[] javaArgs = new Object[method.getParameterCount()];
            Parameter[] methodParams = method.getParameters();
            try {
                for (int i = 0; i < methodParams.length; i++) {
                    ToolParam tp = methodParams[i].getAnnotation(ToolParam.class);
                    if (tp == null) continue;
                    Object value = args.get(tp.name());
                    javaArgs[i] = convert(value, methodParams[i].getType());
                }
                Object result = method.invoke(target, javaArgs);
                if (result instanceof Map<?, ?> mapResult) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    mapResult.forEach((key, value) -> converted.put(String.valueOf(key), value));
                    return converted;
                }
                return Map.of("result", String.valueOf(result));
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw new IllegalStateException(cause);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " | expected="
                        + Arrays.toString(method.getParameterTypes()) + " actual=" + Arrays.toString(javaArgs), e);
            }
        }

        public static ToolDefinition from(Method method, AgentTool annotation, Object target) {
            List<ParamDef> params = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
                if (toolParam == null) continue;
                params.add(new ParamDef(
                        toolParam.name(),
                        toolParam.description(),
                        toolParam.required(),
                        parameter.getType(),
                        parameter.getParameterizedType(),
                        List.of(toolParam.allowedValues()),
                        parseSchema(toolParam.schema())));
            }

            ToolRuntime runtimeAnnotation = method.getAnnotation(ToolRuntime.class);
            RuntimeDefinition runtime = runtimeDefinition(method, target, annotation, runtimeAnnotation);
            ToolEffectSpec effectAnnotation = method.getAnnotation(ToolEffectSpec.class);
            List<EffectDeclaration> effects = effectAnnotation == null ? List.of() : List.of(
                    new EffectDeclaration(
                            EffectType.valueOf(effectAnnotation.type().name()),
                            effectAnnotation.resourceType(),
                            List.of(effectAnnotation.changedFields()),
                            effectAnnotation.requiredOnSuccess(),
                            effectAnnotation.verifiable()));
            PresentationDefinition presentation = presentationDefinition(method, annotation);

            String id = annotation.id().isBlank() ? "subtlesight.tool." + annotation.name() : annotation.id();
            boolean strict = annotation.strict() && strictCompatible(buildInputSchema(params));
            return new ToolDefinition(
                    PROTOCOL_VERSION,
                    id,
                    annotation.version(),
                    annotation.name(),
                    annotation.description(),
                    strict,
                    RiskLevel.valueOf(annotation.risk().name()),
                    params,
                    runtime,
                    Map.of("type", "object", "additionalProperties", true),
                    effects,
                    presentation,
                    method,
                    target);
        }
    }

    private static RuntimeDefinition runtimeDefinition(Method method, Object target, AgentTool tool,
                                                        ToolRuntime runtime) {
        String executor = target.getClass().getName() + "#" + method.getName();
        if (runtime == null) {
            return new RuntimeDefinition(
                    executor, "server", List.of(), SideEffect.READ,
                    RiskLevel.valueOf(tool.risk().name()), List.of(), ConfirmationMode.POLICY,
                    30_000, new RetryPolicy(1, 0, List.of()),
                    new IdempotencyPolicy(IdempotencyMode.NOT_SUPPORTED, null),
                    ConcurrencyMode.PARALLEL, true);
        }
        Long ttl = runtime.idempotencyTtlSeconds() > 0 ? runtime.idempotencyTtlSeconds() : null;
        return new RuntimeDefinition(
                executor,
                enumValue(runtime.location()),
                List.of(runtime.contextRequirements()),
                SideEffect.valueOf(runtime.sideEffect().name()),
                RiskLevel.valueOf(tool.risk().name()),
                List.of(runtime.permissions()),
                ConfirmationMode.valueOf(runtime.confirmation().name()),
                runtime.timeoutMs(),
                new RetryPolicy(runtime.maxAttempts(), runtime.retryBackoffMs(),
                        List.of(runtime.retryableErrorCodes())),
                new IdempotencyPolicy(IdempotencyMode.valueOf(runtime.idempotency().name()), ttl),
                ConcurrencyMode.valueOf(runtime.concurrency().name()),
                runtime.supportsCancellation());
    }

    private static PresentationDefinition presentationDefinition(Method method, AgentTool tool) {
        ToolPresentation annotation = method.getAnnotation(ToolPresentation.class);
        if (annotation == null) {
            return new PresentationDefinition(tool.name(), "Agent", "wrench", Map.of(
                    "queued", "准备执行 " + tool.name(),
                    "running", "正在执行 " + tool.name(),
                    "verifying", "正在验证 " + tool.name(),
                    "succeeded", tool.name() + " 已完成"));
        }
        return new PresentationDefinition(annotation.label(), annotation.category(), annotation.icon(), Map.of(
                "queued", annotation.queued(),
                "running", annotation.running(),
                "verifying", annotation.verifying(),
                "succeeded", annotation.succeeded()));
    }

    private static Map<String, Object> buildInputSchema(List<ParamDef> params) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ParamDef param : params) {
            properties.put(param.name(), param.toSchema());
            // Provider strict mode requires every property here; optional values use null.
            required.add(param.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.unmodifiableMap(properties));
        schema.put("required", List.copyOf(required));
        schema.put("additionalProperties", false);
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> parseSchema(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return JSON.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid @ToolParam schema JSON", e);
        }
    }

    private static Map<String, Object> schemaFor(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)) {
                schema.put("type", "array");
                Type[] args = parameterized.getActualTypeArguments();
                schema.put("items", args.length == 0 ? Map.of() : schemaFor(args[0]));
                return schema;
            }
            if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                schema.put("type", "object");
                schema.put("additionalProperties", true);
                return schema;
            }
        }
        if (!(type instanceof Class<?> javaType)) {
            schema.put("type", "string");
            return schema;
        }
        if (Collection.class.isAssignableFrom(javaType) || javaType.isArray()) {
            schema.put("type", "array");
            schema.put("items", Map.of());
        } else if (Map.class.isAssignableFrom(javaType) || javaType == Object.class) {
            schema.put("type", "object");
            schema.put("additionalProperties", true);
        } else if (javaType == Integer.class || javaType == int.class
                || javaType == Long.class || javaType == long.class) {
            schema.put("type", "integer");
        } else if (javaType == Double.class || javaType == double.class
                || javaType == Float.class || javaType == float.class) {
            schema.put("type", "number");
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            schema.put("type", "boolean");
        } else {
            schema.put("type", "string");
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static void makeNullable(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof String stringType) {
            schema.put("type", List.of(stringType, "null"));
        } else if (type instanceof Collection<?> types && !types.contains("null")) {
            List<Object> nullable = new ArrayList<>(types);
            nullable.add("null");
            schema.put("type", nullable);
        } else if (type == null) {
            Map<String, Object> original = new LinkedHashMap<>(schema);
            schema.clear();
            schema.put("anyOf", List.of(original, Map.of("type", "null")));
        }
        if (schema.get("enum") instanceof Collection<?> values && !values.contains(null)) {
            List<Object> nullableValues = new ArrayList<>(values);
            nullableValues.add(null);
            schema.put("enum", nullableValues);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean strictCompatible(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return true;
        Map<String, Object> schema = (Map<String, Object>) raw;
        Object type = schema.get("type");
        boolean objectType = "object".equals(type)
                || (type instanceof Collection<?> collection && collection.contains("object"));
        if (objectType) {
            if (!Boolean.FALSE.equals(schema.get("additionalProperties"))) return false;
            Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> props
                    ? (Map<String, Object>) props : Map.of();
            Set<?> required = schema.get("required") instanceof Collection<?> req
                    ? new HashSet<>(req) : Set.of();
            if (!required.containsAll(properties.keySet())) return false;
            for (Object child : properties.values()) if (!strictCompatible(child)) return false;
        }
        boolean arrayType = "array".equals(type)
                || (type instanceof Collection<?> collection && collection.contains("array"));
        if (arrayType && !strictCompatible(schema.get("items"))) return false;
        for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
            if (schema.get(keyword) instanceof Collection<?> branches) {
                for (Object branch : branches) if (!strictCompatible(branch)) return false;
            }
        }
        return true;
    }

    private static Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType.isArray() || Collection.class.isAssignableFrom(targetType)
                || Map.class.isAssignableFrom(targetType)) {
            if (value instanceof Collection || value instanceof Map || value.getClass().isArray()) return value;
            throw new IllegalArgumentException("Expected " + targetType.getSimpleName()
                    + " but got " + value.getClass().getSimpleName());
        }
        if (targetType == Integer.class || targetType == int.class)
            return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
        if (targetType == Long.class || targetType == long.class)
            return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class)
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(value.toString());
        return value.toString();
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    static Integer versionFrom(Map<String, Object> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            Integer version = integerValue(values.get(key));
            if (version != null) return version;
        }
        return null;
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String effectValue(EffectType value) {
        return enumValue(value).replace('_', '.');
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
