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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtensionCustomLogic extends CallbackAdapter {
    private Nandbox.Api api;

    private static final String TODOIST_API_BASE = "https://api.todoist.com";
    private static final String TODOIST_REST_V2_BASE = "https://api.todoist.com/rest/v2";

    private static final int HTTP_TIMEOUT_MS = 15000;

    private String todoistToken;

    private final Map/*<String, String>*/ userTokens = new HashMap();

    public static void main(String[] args) throws Exception {
        String TOKEN = "77f063eb6f75fea4d48bff454ece5387997c611c77f063eb6f75fea4d48bff454ece5387997c611c";
        NandboxClient client = NandboxClient.get();
        client.connect(TOKEN, new ExtensionCustomLogic());
    }

    @Override
    public void onConnect(Nandbox.Api api) {
        this.api = api;
        this.todoistToken = loadTodoistTokenFromConfig();
        if (this.todoistToken == null || this.todoistToken.trim().length() == 0) {
            this.todoistToken = System.getProperty("TODOIST_TOKEN");
        }
        if (this.todoistToken == null || this.todoistToken.trim().length() == 0) {
            this.todoistToken = System.getenv("TODOIST_TOKEN");
        }
    }

    @Override
    public void onReceive(IncomingMessage incomingMsg) {
        if (incomingMsg == null || incomingMsg.getChat() == null || incomingMsg.getChat().getId() == null) {
            return;
        }

        String chatId = incomingMsg.getChat().getId();
        String userId = incomingMsg.getFrom() != null ? incomingMsg.getFrom().getId() : null;
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
            if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("start")) {
                send(chatId, helpText(), userId, appId, chatSettings);
                return;
            }

            if (text.equalsIgnoreCase("/help") || text.equalsIgnoreCase("help")) {
                send(chatId, helpText(), userId, appId, chatSettings);
                return;
            }

            if (text.toLowerCase().startsWith("/token") || text.toLowerCase().startsWith("token")) {
                String[] parts = splitFirstArg(text);
                String arg = parts[1];
                if (arg == null || arg.trim().length() == 0) {
                    send(chatId, "Usage: /token <todoist_personal_token>\nThis sets a token for your user only.", userId, appId, chatSettings);
                    return;
                }
                if (userId == null) {
                    send(chatId, "Could not determine user id for this chat.", userId, appId, chatSettings);
                    return;
                }
                userTokens.put(userId, arg.trim());
                send(chatId, "Token saved for your user. You can now use /tasks, /add, /done, /close.", userId, appId, chatSettings);
                return;
            }

            if (text.equalsIgnoreCase("/tasks") || text.equalsIgnoreCase("tasks")) {
                String token = getEffectiveTodoistToken(userId);
                if (!ensureToken(chatId, token, userId, appId, chatSettings)) return;

                JSONArray tasks = todoistGetActiveTasks(token);
                send(chatId, formatTasks(tasks), userId, appId, chatSettings);
                return;
            }

            if (text.toLowerCase().startsWith("/add") || text.toLowerCase().startsWith("add")) {
                String[] parts = splitFirstArg(text);
                String content = parts[1];
                if (content == null || content.trim().length() == 0) {
                    send(chatId, "Usage: /add <task content>", userId, appId, chatSettings);
                    return;
                }
                String token = getEffectiveTodoistToken(userId);
                if (!ensureToken(chatId, token, userId, appId, chatSettings)) return;

                JSONObject created = todoistCreateTask(token, content.trim());
                String id = created.getAsString("id");
                String c = created.getAsString("content");
                if (id == null) id = "";
                if (c == null) c = "";
                send(chatId, "Created task: " + c + "\nID: " + id, userId, appId, chatSettings);
                return;
            }

            if (text.toLowerCase().startsWith("/done") || text.toLowerCase().startsWith("done")) {
                String[] parts = splitFirstArg(text);
                String id = parts[1];
                if (id == null || id.trim().length() == 0) {
                    send(chatId, "Usage: /done <task_id>", userId, appId, chatSettings);
                    return;
                }
                String token = getEffectiveTodoistToken(userId);
                if (!ensureToken(chatId, token, userId, appId, chatSettings)) return;

                todoistCloseTask(token, id.trim());
                send(chatId, "Marked as completed: " + id.trim(), userId, appId, chatSettings);
                return;
            }

            if (text.toLowerCase().startsWith("/close") || text.toLowerCase().startsWith("close")) {
                String[] parts = splitFirstArg(text);
                String id = parts[1];
                if (id == null || id.trim().length() == 0) {
                    send(chatId, "Usage: /close <task_id>", userId, appId, chatSettings);
                    return;
                }
                String token = getEffectiveTodoistToken(userId);
                if (!ensureToken(chatId, token, userId, appId, chatSettings)) return;

                todoistCloseTask(token, id.trim());
                send(chatId, "Closed task: " + id.trim(), userId, appId, chatSettings);
                return;
            }

            send(chatId, "Unknown command.\n\n" + helpText(), userId, appId, chatSettings);

        } catch (Exception ex) {
            String msg = "Error: " + safe(ex.getMessage());
            send(chatId, msg, userId, appId, chatSettings);
        }
    }

    @Override
    public void onReceive(JSONObject obj) {
        if (obj == null) return;
        if (looksLikeChatMessagePayload(obj)) {
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

    private void send(String chatId, String text, String toUserId, String appId, Integer chatSettings) {
        if (this.api == null) return;
        String reference = Utils.getUniqueId();
        this.api.sendText(
                chatId,
                text,
                reference,
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

    private boolean ensureToken(String chatId, String token, String userId, String appId, Integer chatSettings) {
        if (token == null || token.trim().length() == 0) {
            send(chatId, "Todoist token not configured.\nSet it using /token <personal_token> or provide TODOIST_TOKEN via env/system property/config.", userId, appId, chatSettings);
            return false;
        }
        return true;
    }

    private String getEffectiveTodoistToken(String userId) {
        if (userId != null) {
            Object t = userTokens.get(userId);
            if (t != null) {
                String s = String.valueOf(t);
                if (s != null && s.trim().length() > 0) return s.trim();
            }
        }
        return todoistToken;
    }

    private String helpText() {
        StringBuffer sb = new StringBuffer();
        sb.append("Your Personal Task Manager!\n\n");
        sb.append("Commands:\n");
        sb.append("/help - Show this help\n");
        sb.append("/tasks - List active tasks\n");
        sb.append("/add <content> - Create a task\n");
        sb.append("/done <task_id> - Mark task completed\n");
        sb.append("/close <task_id> - Same as /done\n");
        sb.append("/token <personal_token> - Set Todoist token for your user\n\n");
        sb.append("Tip: If you have many tasks, use /tasks then copy the ID to /done.");
        return sb.toString();
    }

    private JSONArray todoistGetActiveTasks(String token) throws Exception {
        String url = TODOIST_REST_V2_BASE + "/tasks";
        HttpResponse resp = httpRequest("GET", url, token, null, null);
        if (resp.code != 200) {
            throw new RuntimeException("Todoist GET /tasks failed (" + resp.code + "): " + trimBody(resp.body));
        }
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(resp.body);
        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }
        JSONArray arr = new JSONArray();
        return arr;
    }

    private JSONObject todoistCreateTask(String token, String content) throws Exception {
        String url = TODOIST_REST_V2_BASE + "/tasks";
        JSONObject body = new JSONObject();
        body.put("content", content);
        HttpResponse resp = httpRequest("POST", url, token, "application/json", body.toJSONString());
        if (resp.code != 200) {
            throw new RuntimeException("Todoist POST /tasks failed (" + resp.code + "): " + trimBody(resp.body));
        }
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(resp.body);
        if (parsed instanceof JSONObject) {
            return (JSONObject) parsed;
        }
        return new JSONObject();
    }

    private void todoistCloseTask(String token, String taskId) throws Exception {
        String url = TODOIST_REST_V2_BASE + "/tasks/" + urlPathEncode(taskId) + "/close";
        HttpResponse resp = httpRequest("POST", url, token, "application/json", "{}");
        if (resp.code != 204 && resp.code != 200) {
            throw new RuntimeException("Todoist POST /tasks/{id}/close failed (" + resp.code + "): " + trimBody(resp.body));
        }
    }

    private String formatTasks(JSONArray tasks) {
        if (tasks == null || tasks.size() == 0) {
            return "No active tasks.";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("Active tasks (").append(tasks.size()).append("):\n\n");
        for (int i = 0; i < tasks.size(); i++) {
            Object o = tasks.get(i);
            if (!(o instanceof JSONObject)) continue;
            JSONObject t = (JSONObject) o;
            String id = t.getAsString("id");
            String content = t.getAsString("content");
            String dueStr = null;
            Object dueObj = t.get("due");
            if (dueObj instanceof JSONObject) {
                JSONObject due = (JSONObject) dueObj;
                String date = due.getAsString("date");
                String datetime = due.getAsString("datetime");
                if (datetime != null && datetime.length() > 0) dueStr = datetime;
                else if (date != null && date.length() > 0) dueStr = date;
            }
            if (content == null) content = "";
            if (id == null) id = "";
            sb.append("- ").append(content);
            if (dueStr != null && dueStr.length() > 0) {
                sb.append(" (due: ").append(dueStr).append(")");
            }
            sb.append("\n  id: ").append(id).append("\n\n");
        }
        sb.append("Use /done <id> to complete a task.");
        return sb.toString();
    }

    private static class HttpResponse {
        int code;
        String body;
        HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private HttpResponse httpRequest(String method, String urlStr, String bearerToken, String contentType, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setDoInput(true);

        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Accept", "application/json");

        if (contentType != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }

        if (body != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            conn.setDoOutput(true);
            byte[] bytes = body.getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream os = null;
            try {
                os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
            } finally {
                if (os != null) {
                    try { os.close(); } catch (Exception e) { }
                }
            }
        }

        int code = conn.getResponseCode();
        InputStream is = null;
        try {
            if (code >= 200 && code < 400) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }
            String respBody = readAll(is);
            return new HttpResponse(code, respBody);
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception e) { }
            }
            try { conn.disconnect(); } catch (Exception e) { }
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String loadTodoistTokenFromConfig() {
        FileInputStream fis = null;
        try {
            String path = System.getProperty("bot.config");
            if (path == null || path.trim().length() == 0) {
                path = System.getenv("BOT_CONFIG");
            }
            if (path == null || path.trim().length() == 0) {
                path = "bot_config.json";
            }
            fis = new FileInputStream(path);
            String json = readAll(fis);
            if (json == null || json.trim().length() == 0) return null;

            JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
            Object parsed = parser.parse(json);
            if (!(parsed instanceof JSONObject)) return null;
            JSONObject root = (JSONObject) parsed;

            Object integ = root.get("integration");
            if (integ instanceof JSONObject) {
                JSONObject integration = (JSONObject) integ;
                String token = integration.getAsString("token");
                if (token != null && token.trim().length() > 0) return token.trim();
            }

            String token2 = root.getAsString("todoist_token");
            if (token2 != null && token2.trim().length() > 0) return token2.trim();

            String token3 = root.getAsString("bot_token");
            if (token3 != null && token3.trim().length() > 0) {
                return null;
            }

            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception e) { }
            }
        }
    }

    private boolean looksLikeChatMessagePayload(JSONObject obj) {
        if (obj == null) return false;
        if (obj.containsKey("message")) return true;
        if (obj.containsKey("chat") && obj.containsKey("text")) return true;
        if (obj.containsKey("from") && obj.containsKey("chat")) return true;
        return false;
    }

    private String[] splitFirstArg(String text) {
        String[] out = new String[2];
        out[0] = text;
        out[1] = null;
        if (text == null) return out;
        String t = text.trim();
        int sp = t.indexOf(' ');
        if (sp < 0) {
            return out;
        }
        out[0] = t.substring(0, sp).trim();
        out[1] = t.substring(sp + 1).trim();
        return out;
    }

    private String urlPathEncode(String s) throws Exception {
        if (s == null) return "";
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private String trimBody(String body) {
        if (body == null) return "";
        String b = body.trim();
        if (b.length() > 500) {
            return b.substring(0, 500) + "...";
        }
        return b;
    }

    private String safe(String s) {
        if (s == null) return "";
        return s;
    }
}
