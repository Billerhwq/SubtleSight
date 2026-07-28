package com.subtlesight.agent.tools;

import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/** Value objects for the typed tool system. */
public final class ToolModels {
    private ToolModels() {}

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public record ParamDef(String name, String description, boolean required, Class<?> type) {
        public Map<String, Object> toSchema() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("type", javaTypeToSchema(type));
            s.put("description", description);
            return s;
        }
    }

    public record ToolDefinition(
            String name, String description, RiskLevel risk,
            List<ParamDef> params, Method method, Object target) {

        public Map<String, Object> toFunctionSchema() {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", name);
            fn.put("description", description);
            Map<String, Object> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (ParamDef p : params) {
                props.put(p.name(), p.toSchema());
                if (p.required()) required.add(p.name());
            }
            fn.put("parameters", Map.of("type", "object", "properties", props, "required", required));
            return fn;
        }

        public Map<String, Object> invoke(Map<String, Object> args) {
            Object[] javaArgs = new Object[method.getParameterCount()];
            try {
                Parameter[] methodParams = method.getParameters();
                for (int i = 0; i < methodParams.length; i++) {
                    ToolParam tp = methodParams[i].getAnnotation(ToolParam.class);
                    if (tp == null) continue;
                    Object value = args.get(tp.name());
                    if (value == null && tp.required())
                        return Map.of("error", "missing required param: " + tp.name());
                    javaArgs[i] = convert(value, methodParams[i].getType());
                }
                Object result = method.invoke(target, javaArgs);
                if (result instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) result;
                    return m;
                }
                return Map.of("result", String.valueOf(result));
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String detail = cause.getClass().getSimpleName() + ": " + cause.getMessage();
                if (cause instanceof IllegalArgumentException && "argument type mismatch".equals(cause.getMessage())) {
                    detail += " | expected=" + Arrays.toString(method.getParameterTypes())
                            + " actual=" + Arrays.toString(javaArgs)
                            + " (after conversion)";
                }
                return Map.of("error", detail);
            }
        }

        /** Create a definition from an annotated method. */
        public static ToolDefinition from(Method method, AgentTool anno, Object target) {
            List<ParamDef> params = new ArrayList<>();
            for (Parameter p : method.getParameters()) {
                ToolParam tp = p.getAnnotation(ToolParam.class);
                if (tp != null) {
                    params.add(new ParamDef(tp.name(), tp.description(), tp.required(), p.getType()));
                }
            }
            return new ToolDefinition(anno.name(), anno.description(),
                    RiskLevel.valueOf(anno.risk().name()),
                    List.copyOf(params), method, target);
        }

        private static Object convert(Object value, Class<?> targetType) {
            if (value == null) return null;
            if (targetType.isInstance(value)) return value;
            // Collections / Maps / arrays: keep as-is when the runtime value already matches.
            // This is required for tools like draw_diagram that accept List<Map<String, Object>>.
            if (targetType.isArray() || Collection.class.isAssignableFrom(targetType)
                    || Map.class.isAssignableFrom(targetType)) {
                if (value instanceof Collection || value instanceof Map || value.getClass().isArray()) {
                    return value;
                }
                // Otherwise we cannot safely convert a scalar to a collection/map.
                throw new IllegalArgumentException("Expected " + targetType.getSimpleName()
                        + " but got " + value.getClass().getSimpleName() + " for value " + value);
            }
            if (targetType == Integer.class || targetType == int.class)
                return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
            if (targetType == Long.class || targetType == long.class)
                return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
            if (targetType == Double.class || targetType == double.class)
                return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
            if (targetType == Boolean.class || targetType == boolean.class)
                return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            return value.toString();
        }

        }

    static String javaTypeToSchema(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) return "integer";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        return "string";
    }
}
