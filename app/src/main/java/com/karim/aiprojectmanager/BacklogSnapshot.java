package com.karim.aiprojectmanager;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BacklogSnapshot {
    static final String ASSET = "backlog-snapshot-v1.json";

    final String id;
    final String name;
    final Map<String, String> milestones;
    final List<Task> tasks;
    final NextAction nextAction;

    private BacklogSnapshot(String id, String name, Map<String, String> milestones,
                            List<Task> tasks, NextAction nextAction) {
        this.id = id;
        this.name = name;
        this.milestones = Collections.unmodifiableMap(milestones);
        this.tasks = Collections.unmodifiableList(tasks);
        this.nextAction = nextAction;
    }

    static final class Task {
        final String id, title, status, milestoneId, verification;
        final List<String> dependencies;

        Task(Map<?, ?> task) {
            id = string(task.get("id"));
            title = string(task.get("title"));
            status = string(task.get("status"));
            require(status.equals("To Do") || status.equals("In Progress") || status.equals("Done"));
            milestoneId = string(task.get("milestoneId"));
            List<String> ids = new ArrayList<>();
            for (Object dependency : array(task.get("dependencies"))) ids.add(string(dependency));
            require(new HashSet<>(ids).size() == ids.size());
            dependencies = Collections.unmodifiableList(ids);

            Map<?, ?> state = object(task.get("verification"));
            verification = string(state.get("state"));
            List<?> evidence = array(state.get("evidence"));
            if (verification.equals("verified")) {
                require(status.equals("Done") && !evidence.isEmpty());
                for (Object entry : evidence) {
                    Map<?, ?> artifact = object(entry);
                    string(artifact.get("path"));
                    require(string(artifact.get("sha256")).matches("[0-9a-f]{64}"));
                }
            } else {
                require(verification.equals("unverified") && evidence.isEmpty());
            }
        }
    }

    static final class NextAction {
        final String taskId, reason, policy;

        NextAction(Map<?, ?> action) {
            taskId = string(action.get("taskId"));
            reason = string(action.get("reason"));
            policy = string(action.get("policy"));
        }
    }

    static BacklogSnapshot read(Reader input) throws IOException {
        try (JsonReader reader = new JsonReader(input)) {
            Map<?, ?> root = object(readValue(reader, 0));
            require(reader.peek() == JsonToken.END_DOCUMENT);
            Object version = root.get("schemaVersion");
            require(version instanceof JsonNumber && ((JsonNumber) version).literal.equals("1"));
            require("ai-project-snapshot".equals(root.get("kind")));
            Map<?, ?> project = object(root.get("project"));
            Map<String, String> milestones = new LinkedHashMap<>();
            for (Object entry : array(root.get("milestones"))) {
                Map<?, ?> milestone = object(entry);
                require(milestones.put(string(milestone.get("id")), string(milestone.get("title"))) == null);
            }

            Map<String, Task> tasks = new LinkedHashMap<>();
            for (Object entry : array(root.get("tasks"))) {
                Task task = new Task(object(entry));
                require(milestones.containsKey(task.milestoneId));
                require(tasks.put(task.id, task) == null);
            }
            for (Task task : tasks.values()) {
                for (String dependency : task.dependencies) {
                    require(tasks.containsKey(dependency) && !dependency.equals(task.id));
                }
            }

            require(root.containsKey("nextAction"));
            Object action = root.get("nextAction");
            NextAction nextAction = action == null ? null : new NextAction(object(action));
            if (nextAction != null) {
                Task task = tasks.get(nextAction.taskId);
                require(task != null && !task.status.equals("Done"));
                for (String dependency : task.dependencies) require(tasks.get(dependency).status.equals("Done"));
            }
            return new BacklogSnapshot(string(project.get("id")), string(project.get("name")),
                    milestones, new ArrayList<>(tasks.values()), nextAction);
        }
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException {
        require(depth <= 32);
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                Map<String, Object> entries = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    require(!entries.containsKey(key));
                    entries.put(key, readValue(reader, depth + 1));
                }
                reader.endObject();
                return entries;
            case BEGIN_ARRAY:
                List<Object> items = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) items.add(readValue(reader, depth + 1));
                reader.endArray();
                return items;
            case STRING: return reader.nextString();
            case NUMBER: return new JsonNumber(reader.nextString());
            case BOOLEAN: return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default: throw new IllegalArgumentException("Unexpected snapshot JSON token");
        }
    }

    private static final class JsonNumber {
        final String literal;
        JsonNumber(String literal) { this.literal = literal; }
    }

    private static Map<?, ?> object(Object value) {
        require(value instanceof Map);
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        require(value instanceof List);
        return (List<?>) value;
    }

    private static String string(Object value) {
        require(value instanceof String && !((String) value).trim().isEmpty());
        return (String) value;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid or unsupported snapshot");
    }
}
