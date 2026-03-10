package org.example;

import com.nandbox.bots.api.Nandbox;
import com.nandbox.bots.api.NandboxClient;
import com.nandbox.bots.api.data.*;
import com.nandbox.bots.api.inmessages.*;
import com.nandbox.bots.api.outmessages.*;
import com.nandbox.bots.api.util.*;
import com.nandbox.bots.api.test.*;

import net.minidev.json.*;
import net.minidev.json.parser.JSONParser;

import org.example.CallbackAdapter;
import java.io.FileInputStream;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ExtensionCustomLogic extends CallbackAdapter {
    private Nandbox.Api api;

    private static final String TODOIST_API_BASE = "https://api.todoist.com";
    private static final String TODOIST_TASKS_PATH = "/api/v1/tasks";
    private static final int HTTP_TIMEOUT_MS = 15000;

    private static final String HELP_TEXT = "Available commands:\n"
            + "/help - show this help\n"
            + "/tasks - list open tasks\n"
            + "/add <content> [| projectId=<id>] [| due=<string>] [| priority=1-4] - create a task\n"
            + "/done <taskId> - close a task\n"
            + "/delete <taskId> - delete a task\n"
            + "/usage - show examples\n";

    private static final String USAGE_TEXT = "Examples:\n"
            + "/tasks\n"
            + "/add Buy milk\n"
            + "/add Submit report | due=tomorrow 17:00 | priority=4\n"
            + "/add Fix bug #123 | projectId=2203306141\n"
            + "/done 2995104339\n"
            + "/delete 2995104339\n";

    public static void main(String[] args) throws Exception {
        String TOKEN = "90091783792236740:0:go0gSHnPNT9qQIBlk47lQmhty91hax";
        NandboxClient client = NandboxClient.get();
        client.connect(TOKEN, new ExtensionCustomLogic());
    }

    @Override
    public void onConnect(Nandbox.Api api) {
        this.api = api;
    }

    @Override
    public void onReceive(IncomingMessage incomingMsg) {
        if (incomingMsg == null || api == null) {
            return;
        }

        String chatId = safeChatId(incomingMsg);
        String userId = safeUserId(incomingMsg);
        String appId = incomingMsg.getAppId();
        Integer chatSettings = incomingMsg.getChatSettings();

        String text = incomingMsg.getText();
        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.length() == 0) {
            return;
        }

        try {
            if (isHelp(text)) {
                sendText(chatId, HELP_TEXT, userId, appId, chatSettings);
                return;
            }

            if (equalsCommand(text, "/usage")) {
                sendText(chatId, USAGE_TEXT, userId, appId, chatSettings);
                return;
            }

            if (equalsCommand(text, "/tasks")) {
                JSONArray tasks = todoistGetTasks();
                String formatted = formatTasks(tasks);
                sendText(chatId, formatted, userId, appId, chatSettings);
                return;
            }

            if (startsWithCommand(text, "/add")) {
                String args = extractArgs(text, "/add");
                if (args.length() == 0) {
                    sendText(chatId, "Usage: /add <content> [| projectId=<id>] [| due=<string>] [| priority=1-4]", userId, appId, chatSettings);
                    return;
                }
                AddCommand add = parseAddCommand(args);
                if (add == null || add.content == null || add.content.trim().length() == 0) {
                    sendText(chatId, "Could not parse /add command. Use /usage for examples.", userId, appId, chatSettings);
                    return;
                }
                JSONObject created = todoistAddTask(add);
                String reply = formatCreatedTask(created);
                sendText(chatId, reply, userId, appId, chatSettings);
                return;
            }

            if (startsWithCommand(text, "/done")) {
                String args = extractArgs(text, "/done");
                if (args.length() == 0) {
                    sendText(chatId, "Usage: /done <taskId>", userId, appId, chatSettings);
                    return;
                }
                String taskId = args.trim();
                todoistCloseTask(taskId);
                sendText(chatId, "Closed task " + taskId + ".", userId, appId, chatSettings);
                return;
            }

            if (startsWithCommand(text, "/delete")) {
                String args = extractArgs(text, "/delete");
                if (args.length() == 0) {
                    sendText(chatId, "Usage: /delete <taskId>", userId, appId, chatSettings);
                    return;
                }
                String taskId = args.trim();
                todoistDeleteTask(taskId);
                sendText(chatId, "Deleted task " + taskId + ".", userId, appId, chatSettings);
                return;
            }

            if (text.startsWith("/")) {
                sendText(chatId, "Unknown command. Type /help", userId, appId, chatSettings);
                return;
            }

            sendText(chatId, "Type /help to see available commands.", userId, appId, chatSettings);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.length() == 0) {
                msg = e.toString();
            }
            sendText(chatId, "Error: " + msg, userId, appId, chatSettings);
        }
    }

    @Override
    public void onReceive(JSONObject obj) {
        if (obj == null) {
            return;
        }
        if (looksLikeChatMessage(obj)) {
            return;
        }
    }

    @Override
    public void onClose() {
    }

    @Override
    public void onError() {
    }

    @Override
    public void onChatMenuCallBack(ChatMenuCallback chatMenuCallback) {
    }

    @Override
    public void onInlineMessageCallback(InlineMessageCallback inlineMsgCallback) {
    }

    @Override
    public void onMessagAckCallback(MessageAck msgAck) {
    }

    @Override
    public void onUserJoinedBot(User user) {
    }

    @Override
    public void onChatMember(ChatMember chatMember) {
    }

    @Override
    public void onChatAdministrators(ChatAdministrators chatAdministrators) {
    }

    @Override
    public void userStartedBot(User user) {
    }

    @Override
    public void onMyProfile(User user) {
    }

    @Override
    public void onProductDetail(ProductItemResponse productItem) {
    }

    @Override
    public void onCollectionProduct(GetProductCollectionResponse collectionProduct) {
    }

    @Override
    public void listCollectionItemResponse(ListCollectionItemResponse collections) {
    }

    @Override
    public void onUserDetails(User user, String appId) {
    }

    @Override
    public void userStoppedBot(User user) {
    }

    @Override
    public void userLeftBot(User user) {
    }

    @Override
    public void permanentUrl(PermanentUrl permenantUrl) {
    }

    @Override
    public void onChatDetails(Chat chat, String appId) {
    }

    @Override
    public void onInlineSearh(InlineSearch inlineSearch) {
    }

    @Override
    public void onBlackListPattern(Pattern pattern) {
    }

    @Override
    public void onWhiteListPattern(Pattern pattern) {
    }

    @Override
    public void onBlackList(BlackList blackList) {
    }

    @Override
    public void onDeleteBlackList(List_ak blackList) {
    }

    @Override
    public void onWhiteList(WhiteList whiteList) {
    }

    @Override
    public void onDeleteWhiteList(List_ak whiteList) {
    }

    @Override
    public void onScheduleMessage(IncomingMessage incomingScheduleMsg) {
    }

    @Override
    public void onWorkflowDetails(WorkflowDetails workflowDetails) {
    }

    @Override
    public void onCreateChat(Chat chat) {
    }

    @Override
    public void onMenuCallBack(MenuCallback menuCallback) {
    }

    private void sendText(String chatId, String text, String toUserId, String appId, Integer chatSettings) {
        api.sendText(
                chatId,
                text,
                Utils.getUniqueId(),
                null,
                toUserId,
                Integer.valueOf(0),
                Boolean.FALSE,
                chatSettings,
                null,
                null,
                null,
                appId
        );
    }

    private static String safeChatId(IncomingMessage msg) {
        if (msg.getChat() != null && msg.getChat().getId() != null) {
            return msg.getChat().getId();
        }
        return null;
    }

    private static String safeUserId(IncomingMessage msg) {
        if (msg.getFrom() != null && msg.getFrom().getId() != null) {
            return msg.getFrom().getId();
        }
        return null;
    }

    private static boolean equalsCommand(String text, String cmd) {
        return text.equalsIgnoreCase(cmd);
    }

    private static boolean startsWithCommand(String text, String cmd) {
        if (text.length() < cmd.length()) {
            return false;
        }
        if (!text.regionMatches(true, 0, cmd, 0, cmd.length())) {
            return false;
        }
        if (text.length() == cmd.length()) {
            return true;
        }
        char c = text.charAt(cmd.length());
        return Character.isWhitespace(c);
    }

    private static boolean isHelp(String text) {
        return equalsCommand(text, "/help") || equalsCommand(text, "/start") || equalsCommand(text, "/?");
    }

    private static String extractArgs(String text, String cmd) {
        if (text.length() == cmd.length()) {
            return "";
        }
        String rest = text.substring(cmd.length()).trim();
        return rest;
    }

    private static class AddCommand {
        String content;
        String projectId;
        String due;
        Integer priority;
    }

    private static AddCommand parseAddCommand(String args) {
        AddCommand cmd = new AddCommand();
        String[] parts = splitByPipe(args);
        if (parts.length == 0) {
            return null;
        }
        cmd.content = parts[0].trim();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.length() == 0) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = p.substring(0, eq).trim();
            String val = p.substring(eq + 1).trim();
            if (key.equalsIgnoreCase("projectId") || key.equalsIgnoreCase("project_id")) {
                cmd.projectId = val;
            } else if (key.equalsIgnoreCase("due") || key.equalsIgnoreCase("due_string")) {
                cmd.due = val;
            } else if (key.equalsIgnoreCase("priority")) {
                try {
                    cmd.priority = Integer.valueOf(Integer.parseInt(val));
                } catch (Exception e) {
                    cmd.priority = null;
                }
            }
        }
        return cmd;
    }

    private static String[] splitByPipe(String s) {
        java.util.ArrayList list = new java.util.ArrayList();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '|') {
                list.add(s.substring(start, i));
                start = i + 1;
            }
        }
        list.add(s.substring(start));
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = (String) list.get(i);
        }
        return out;
    }

    private JSONArray todoistGetTasks() throws Exception {
        String url = TODOIST_API_BASE + TODOIST_TASKS_PATH;
        HttpResult res = httpRequest("GET", url, null);
        if (res.status < 200 || res.status >= 300) {
            throw new IOException("Todoist GET tasks failed (" + res.status + "): " + truncate(res.body, 400));
        }
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(res.body);
        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }
        return new JSONArray();
    }

    private JSONObject todoistAddTask(AddCommand add) throws Exception {
        String url = TODOIST_API_BASE + TODOIST_TASKS_PATH;
        JSONObject payload = new JSONObject();
        payload.put("content", add.content);
        if (add.projectId != null && add.projectId.length() > 0) {
            payload.put("project_id", add.projectId);
        }
        if (add.priority != null) {
            int p = add.priority.intValue();
            if (p >= 1 && p <= 4) {
                payload.put("priority", Integer.valueOf(p));
            }
        }
        if (add.due != null && add.due.length() > 0) {
            JSONObject due = new JSONObject();
            due.put("string", add.due);
            payload.put("due", due);
        }

        HttpResult res = httpRequest("POST", url, payload.toJSONString());
        if (res.status < 200 || res.status >= 300) {
            throw new IOException("Todoist add task failed (" + res.status + "): " + truncate(res.body, 400));
        }
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(res.body);
        if (parsed instanceof JSONObject) {
            return (JSONObject) parsed;
        }
        JSONObject o = new JSONObject();
        o.put("raw", res.body);
        return o;
    }

    private void todoistCloseTask(String taskId) throws Exception {
        String url = TODOIST_API_BASE + TODOIST_TASKS_PATH + "/" + urlEncode(taskId) + "/close";
        HttpResult res = httpRequest("POST", url, "");
        if (res.status < 200 || res.status >= 300) {
            throw new IOException("Todoist close task failed (" + res.status + "): " + truncate(res.body, 400));
        }
    }

    private void todoistDeleteTask(String taskId) throws Exception {
        String url = TODOIST_API_BASE + TODOIST_TASKS_PATH + "/" + urlEncode(taskId);
        HttpResult res = httpRequest("DELETE", url, null);
        if (res.status != 204 && (res.status < 200 || res.status >= 300)) {
            throw new IOException("Todoist delete task failed (" + res.status + "): " + truncate(res.body, 400));
        }
    }

    private String formatTasks(JSONArray tasks) {
        if (tasks == null || tasks.size() == 0) {
            return "No open tasks.";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("Open tasks (").append(tasks.size()).append("):\n");
        for (int i = 0; i < tasks.size(); i++) {
            Object o = tasks.get(i);
            if (!(o instanceof JSONObject)) {
                continue;
            }
            JSONObject t = (JSONObject) o;
            String id = asString(t.get("id"));
            String content = asString(t.get("content"));
            String priority = asString(t.get("priority"));
            String dueStr = "";
            Object dueObj = t.get("due");
            if (dueObj instanceof JSONObject) {
                String ds = asString(((JSONObject) dueObj).get("string"));
                String dt = asString(((JSONObject) dueObj).get("datetime"));
                String d = ds.length() > 0 ? ds : dt;
                if (d.length() > 0) {
                    dueStr = " | due: " + d;
                }
            }
            String pr = "";
            if (priority.length() > 0) {
                pr = " | p" + priority;
            }
            sb.append("- [").append(id).append("] ").append(content).append(pr).append(dueStr).append("\n");
        }
        sb.append("\nTip: /done <id> or /delete <id>");
        return sb.toString();
    }

    private String formatCreatedTask(JSONObject task) {
        if (task == null) {
            return "Task created.";
        }
        String id = asString(task.get("id"));
        String content = asString(task.get("content"));
        String url = asString(task.get("url"));
        StringBuffer sb = new StringBuffer();
        sb.append("Created task");
        if (id.length() > 0) {
            sb.append(" [").append(id).append("]");
        }
        if (content.length() > 0) {
            sb.append(": ").append(content);
        }
        if (url.length() > 0) {
            sb.append("\n").append(url);
        }
        return sb.toString();
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private static class HttpResult {
        int status;
        String body;
        Map headers;
    }

    private HttpResult httpRequest(String method, String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setUseCaches(false);

            conn.setRequestProperty("Authorization", "Bearer " + getTodoistToken());
            conn.setRequestProperty("Accept", "application/json");

            if (jsonBody != null && (method.equals("POST") || method.equals("PUT"))) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = jsonBody.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 400) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
                if (is == null) {
                    is = conn.getInputStream();
                }
            }

            String body = readAll(is);
            HttpResult r = new HttpResult();
            r.status = status;
            r.body = body;
            r.headers = new HashMap();
            return r;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception e) { }
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) { }
            }
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String urlEncode(String s) throws Exception {
        if (s == null) {
            return "";
        }
        return java.net.URLEncoder.encode(s, "UTF-8");
    }

    private static boolean looksLikeChatMessage(JSONObject obj) {
        try {
            if (obj.containsKey("message")) {
                Object m = obj.get("message");
                if (m instanceof JSONObject) {
                    return true;
                }
            }
            if (obj.containsKey("text") && obj.containsKey("chat")) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static String getTodoistToken() throws Exception {
        String env = System.getenv("TODOIST_TOKEN");
        if (env != null && env.trim().length() > 0) {
            return env.trim();
        }
        String prop = System.getProperty("todoist.token");
        if (prop != null && prop.trim().length() > 0) {
            return prop.trim();
        }
        String embedded = "77f063eb6f75fea4d48bff454ece5387997c611c77f063eb6f75fea4d48bff454ece5387997c611c";
        if (embedded != null && embedded.trim().length() > 0) {
            return embedded.trim();
        }
        throw new IOException("Missing Todoist token. Set TODOIST_TOKEN env var or -Dtodoist.token");
    }
}
