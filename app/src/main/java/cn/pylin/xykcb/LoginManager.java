package cn.pylin.xykcb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 登录管理器类 - 合并了所有学校的登录逻辑
 */
public class LoginManager {
    private static final String TAG = "LoginManager";
    private final Context context;
    private final CourseDataCallback callback;
    private OkHttpClient httpClient;
    private String currentWeek = "1";
    private String schoolCode;
    // 标志变量：记录是否已经尝试过网络更新
    private boolean hasAttemptedUpdate = false;

    // 登录类型枚举
    public enum LoginType {
        HNIT_A(1, "HNIT-A", "湖南工学院(通用)"),
        HNIT_B(2, "HNIT-B", "湖南工学院(内网)"),
        HYNU(3, "HYNU", "衡阳师范学院"),
        USC(4, "USC", "南华大学");

        private final int id;
        private final String code;
        private final String name;

        LoginType(int id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public static LoginType fromCode(String code) {
            for (LoginType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return HNIT_A; // 默认返回湖南工学院
        }
    }

    public interface CourseDataCallback {
        void onCourseDataReceived(List<List<Course>> weeklyCourses);
        void onError(String message);
    }

    public LoginManager(Context context, CourseDataCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * 执行登录操作
     * @param username 用户名
     * @param password 密码
     * @param schoolCode 学校代码
     */
    public void performLogin(String username, String password, String schoolCode) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            notifyError("用户名或密码不能为空");
            return;
        }

        // 重置更新标志，确保每次登录都是一个新的会话状态
        hasAttemptedUpdate = false;
        
        this.schoolCode = schoolCode;
        LoginType loginType = LoginType.fromCode(schoolCode);

        // 首先检查是否存在本地数据，如果不存在，先显示加载提示
        SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
        String localCourseList = sharedPreferences.getString("CourseList", "");
        
        if (localCourseList.isEmpty()) {
            notifyError("正在获取课程数据...");
        } else {
            // 存在本地数据，先加载本地数据
            loadLocalCourseData();
        }

        // 然后根据学校类型执行不同的登录逻辑
        switch (loginType) {
            case HNIT_A:
                performHnitALogin(username, password);
                break;
//            case HNIT_B:
//                performHnitBLogin(username, password);
//                break;
            // 可以在这里添加其他学校的登录逻辑
            default:
                notifyError("暂不支持该学校，敬请期待");
        }
    }

    /**
     * 湖南工学院外网登录
     */
    private void performHnitALogin(String username, String password) {
        // 初始化 OkHttpClient
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        String encryptedPassword = encryptPassword(password);
        String loginUrl = "https://jw.hnit.edu.cn/njwhd/login?userNo=" + username + "&pwd=" + encryptedPassword;

        Request request = new Request.Builder()
                .url(loginUrl)
                .post(RequestBody.create(new byte[0]))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("登录失败");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            
                            // 获取Msg字段内容
                            String msg = jsonResponse.optString("Msg", "");
                            
                            // 根据Msg内容判断登录结果
                            if (msg.contains("成功")) {
                                // 登录成功，检查是否有token
                                if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("token")) {
                                    JSONObject data = jsonResponse.getJSONObject("data");
                                    String token = data.getString("token");
                                    saveLoginInfo(username, password);
                                    // 保存token用于后续获取周次
                                    SharedPreferences sharedPreferences = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("token", token);
                                    
                                    // 保存用户信息
                                    if (data.has("name")) {
                                        editor.putString("userName", data.getString("name"));
                                    }
                                    if (data.has("academyName")) {
                                        editor.putString("academyName", data.getString("academyName"));
                                    }
                                    if (data.has("clsName")) {
                                        editor.putString("className", data.getString("clsName"));
                                    }
                                    editor.apply();
                                    
                                    getXnxqListId(token);
                                } else {
                                    notifyError("登录失败：服务器返回异常");
                                }
                            } else if (msg.contains("错误")) {
                                // 账号或密码错误
                                notifyError("登录失败：该帐号不存在或密码错误");
                            } else if (msg.contains("失败")) {
                                // 登录失败
                                notifyError("登录失败：登录失败");
                            } else {
                                // 其他未知情况
                                notifyError("登录失败：服务器返回异常");
                            }
                        }
                    } catch (JSONException e) {
                        notifyError("登录失败：数据解析错误");
                    }
                } else {
                    notifyError("登录失败：服务器错误");
                }
            }
        });
    }

    /**
     * 湖南工学院内网登录
     */
    private void performHnitBLogin(String username, String password) {
        httpClient = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        new Thread(() -> {
            try {
                Request initialRequest = new Request.Builder()
                        .url("https://jwxt.hnit.edu.cn/jsxsd/")
                        .build();

                try (Response initialResponse = httpClient.newCall(initialRequest).execute()) {
                    if (!initialResponse.isSuccessful()) {
                        throw new IOException("初始请求失败: " + initialResponse.code());
                    }
                }

                String encoded = prepareEncodedValue(username, password);
                RequestBody formBody = new FormBody.Builder()
                        .add("loginMethod", "LoginToXk")
                        .add("userAccount", username)
                        .add("userPassword", "")
                        .add("encoded", encoded)
                        .build();

                Request loginRequest = new Request.Builder()
                        .url("https://jwxt.hnit.edu.cn/jsxsd/xk/LoginToXk")
                        .post(formBody)
                        .build();

                try (Response loginResponse = httpClient.newCall(loginRequest).execute()) {
                    if (!loginResponse.isSuccessful()) {
                        throw new IOException("登录请求失败: " + loginResponse.code());
                    }
                }

                getCurrentWeekFromSystem();
                getCourseSchedule();

            } catch (IOException e) {
                Log.e(TAG, "登录或获取课表失败", e);
                notifyError("操作失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 加载本地课程数据
     */
    private void loadLocalCourseData() {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
            String localCourseList = sharedPreferences.getString("CourseList", "");

            if (!localCourseList.isEmpty()) {
                // 检查是否有保存的真实周次信息
                SharedPreferences weekPrefs = context.getSharedPreferences("WeekDates", Context.MODE_PRIVATE);
                if (weekPrefs.getAll().isEmpty()) {
                    // 如果没有周次信息，尝试从网络获取真实周次
                    SharedPreferences loginPrefs = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
                    String token = loginPrefs.getString("token", "");
                    if (!token.isEmpty()) {
                        getCurrentWeekFromApi(token);
                        return; // 等待网络获取完成后再加载数据
                    }
                }
                
                // 使用现有的周次信息
                int currentWeek = CourseDataManager.getCurrentWeek(context);
                // 更新MainActivity中的Week静态变量，确保UI显示正确的周次
                MainActivity.Week = String.valueOf(currentWeek);
                
                List<List<Course>> weeklyCourses = CourseDataManager.parseCourseData(localCourseList);
                callback.onCourseDataReceived(weeklyCourses);
                // 只有在已经尝试过网络更新但失败的情况下才显示本地数据提示
                if (hasAttemptedUpdate) {
                    notifyError("当前查看的是本地数据");
                }
            } else {
                notifyError("正在尝试更新数据...");
            }
        } catch (Exception e) {
            notifyError("加载本地数据失败");
        }
    }

    /**
     * 加密密码（湖南工学院外网登录使用）
     */
    private String encryptPassword(String password) {
        try {
            byte[] keyBytes = hexStringToByteArray("717a6b6a316b6a6768643d383736262a");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            String quotedPassword = "\"" + password + "\"";
            byte[] encryptedBytes = cipher.doFinal(quotedPassword.getBytes(StandardCharsets.UTF_8));

            String base64Encoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            String doubleBase64Encoded = Base64.encodeToString(base64Encoded.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            return doubleBase64Encoded;
        } catch (Exception e) {
            Log.e(TAG, "密码加密失败", e);
            return null;
        }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 保存登录信息
     */
    private void saveLoginInfo(String username, String password) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", username);
        editor.putString("password", password);
        editor.putString("schoolCode", schoolCode);
        editor.apply();
    }

    /**
     * 获取学年学期ID（湖南工学院外网登录使用）
     */
    private void getXnxqListId(String token) {
        String XnxqListIdUrl = "https://jw.hnit.edu.cn/njwhd/getXnxqList?token=" + token;

        Request request = new Request.Builder()
                .url(XnxqListIdUrl)
                .post(RequestBody.create(new byte[0]))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("获取学年学期失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONArray jsonArray = new JSONArray(responseString);
                            JSONObject maxNumObject = null;
                            int maxNum = Integer.MIN_VALUE;

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonObject = jsonArray.getJSONObject(i);
                                int num = jsonObject.getInt("num");
                                if (num > maxNum) {
                                    maxNum = num;
                                    maxNumObject = jsonObject;
                                }
                            }

                            if (maxNumObject != null) {
                                String xnxq01id = maxNumObject.getString("xnxq01id");
                                // 先获取当前周次，然后在回调中获取课程列表
                                getCurrentWeekFromApi(token);
                                // 同时获取课程节次模式
                                getKbjcmsid(token, xnxq01id);
                            } else {
                                notifyError("获取学年学期失败！");
                            }
                        }
                    } catch (JSONException e) {
                        notifyError("解析学年学期数据失败：" + e.getMessage());
                    }
                } else {
                    notifyError("获取学年学期失败，错误码：" + response.code());
                }
            }
        });
    }

    /**
     * 获取当前周次
     */
    public void getWeek() {
        // 获取保存的token
        SharedPreferences sharedPreferences = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        String token = sharedPreferences.getString("token", "");
        
        if (!token.isEmpty()) {
            getCurrentWeekFromApi(token);
        } else {
            // token为空时，使用默认值
            currentWeek = "1";
            notifyError("未找到有效token，使用默认周次");
        }
    }

    /**
     * 通过API获取当前周次
     */
    private void getCurrentWeekFromApi(String token) {
        String teachingWeekUrl = "https://jw.hnit.edu.cn/njwhd/teachingWeek?token=" + token;

        Request request = new Request.Builder()
                .url(teachingWeekUrl)
                .post(RequestBody.create(new byte[0]))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("获取当前周次失败：" + e.getMessage());
                currentWeek = "1";
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            if (jsonResponse.has("nowWeek")) {
                                String week = jsonResponse.getString("nowWeek");
                                currentWeek = week;
                                Log.d(TAG, "获取到当前周次: " + currentWeek);
                                
                                // 计算并保存每周的日期范围
                                calculateAndSaveWeekDates(Integer.parseInt(currentWeek));
                                
                                // 保存当前周次到SharedPreferences
                                SharedPreferences prefs = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
                                prefs.edit().putString("currentWeek", currentWeek).apply();
                                
                                // 注意：这里不直接重新加载课程数据，而是依赖外部调用来确保数据同步
                                Log.d(TAG, "当前周次已更新，等待下次数据加载时应用");
                            } else {
                                notifyError("获取当前周次失败：服务器返回数据不完整");
                                currentWeek = "1";
                            }
                        }
                    } catch (JSONException e) {
                        notifyError("解析周次数据失败：" + e.getMessage());
                        currentWeek = "1";
                    }
                } else {
                    notifyError("获取当前周次失败，错误码：" + response.code());
                    currentWeek = "1";
                }
            }
        });
    }

    /**
     * 计算并保存每周的日期范围
     * @param currentWeek 当前周数
     */
    private void calculateAndSaveWeekDates(int currentWeek) {
        try {
            // 获取当前系统日期
            Calendar calendar = Calendar.getInstance();
            int todayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            
            // 计算本周周一的日期（Calendar中周日是1，周一是2，所以需要调整）
            int daysToMonday = (todayOfWeek == Calendar.SUNDAY) ? -6 : 2 - todayOfWeek;
            calendar.add(Calendar.DAY_OF_MONTH, daysToMonday);
            
            // 计算第1周周一的日期
            calendar.add(Calendar.DAY_OF_MONTH, -(currentWeek - 1) * 7);
            Calendar firstWeekMonday = (Calendar) calendar.clone();
            
            // 保存每周的日期范围
            SharedPreferences weekDatesPrefs = context.getSharedPreferences("WeekDates", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = weekDatesPrefs.edit();
            editor.clear(); // 清除旧数据
            
            SimpleDateFormat sdf = new SimpleDateFormat("M.d", Locale.getDefault());
            
            // 计算并保存每周的日期范围（假设总共有20周）
            for (int week = 1; week <= 20; week++) {
                Calendar weekStart = (Calendar) firstWeekMonday.clone();
                weekStart.add(Calendar.DAY_OF_MONTH, (week - 1) * 7);
                Calendar weekEnd = (Calendar) weekStart.clone();
                weekEnd.add(Calendar.DAY_OF_MONTH, 6);
                
                StringBuilder dateRange = new StringBuilder();
                for (int day = 0; day < 7; day++) {
                    Calendar currentDay = (Calendar) weekStart.clone();
                    currentDay.add(Calendar.DAY_OF_MONTH, day);
                    dateRange.append(sdf.format(currentDay.getTime()));
                    if (day < 6) {
                        dateRange.append(",");
                    }
                }
                
                editor.putString(String.valueOf(week), dateRange.toString());
            }
            
            editor.apply();
            Log.d(TAG, "已成功计算并保存每周的日期范围");
        } catch (Exception e) {
            Log.e(TAG, "计算日期范围失败", e);
        }
    }

    /**
     * 获取课程节次模式（湖南工学院外网登录使用）
     */
    private void getKbjcmsid(String token, String xnxq01id) {
        String sjkbmsUrl = "https://jw.hnit.edu.cn/njwhd/Get_sjkbms?token=" + token;

        Request request = new Request.Builder()
                .url(sjkbmsUrl)
                .post(RequestBody.create(new byte[0]))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("获取课程节次模式失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            if (jsonResponse.has("data") && jsonResponse.getJSONArray("data").length() > 0) {
                                String kbjcmsid = jsonResponse.getJSONArray("data").getJSONObject(0).getString("kbjcmsid");
                                getCourseList(token, xnxq01id, kbjcmsid);
                            } else {
                                notifyError("获取课程节次模式失败！");
                            }
                        }
                    } catch (JSONException e) {
                        notifyError("解析课程节次模式数据失败：" + e.getMessage());
                    }
                } else {
                    notifyError("获取课程节次模式失败，错误码：" + response.code());
                }
            }
        });
    }

    /**
     * 获取课程列表（湖南工学院外网登录使用）
     */
    private void getCourseList(String token, String xnxq01id, String kbjcmsid) {
        String CourseListUrl = "https://jw.hnit.edu.cn/njwhd/student/curriculum?token=" + token +
                "&xnxq01id=" + xnxq01id + "&kbjcmsid=" + kbjcmsid + "&week=all";

        Request request = new Request.Builder()
                .url(CourseListUrl)
                .post(RequestBody.create(new byte[0]))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("获取课程列表失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String newCourseList = responseBody.string();

                            SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
                            String existingCourseList = sharedPreferences.getString("CourseList", "");

                            // 设置标志表示已经尝试过网络更新
                            hasAttemptedUpdate = true;
                            
                            if (!courseContentsAreEqual(existingCourseList, newCourseList)) {
                                sharedPreferences.edit().putString("CourseList", newCourseList).apply();
                                // 课程数据已更新，显示提示
                                notifyError("已更新到最新数据");
                            } else {
                                // 数据没有变化，显示提示
                                notifyError("当前已是最新数据");
                            }

                            List<List<Course>> weeklyCourses = CourseDataManager.parseCourseData(newCourseList);
                            // 设置当前周次
                            SharedPreferences loginPrefs = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
                            String token = loginPrefs.getString("token", "");
                            if (!token.isEmpty()) {
                                getCurrentWeekFromApi(token);
                            }
                            callback.onCourseDataReceived(weeklyCourses);
                        }
                    } catch (Exception e) {
                        notifyError("解析课程列表数据失败：" + e.getMessage());
                    }
                } else {
                    notifyError("获取课程列表失败，错误码：" + response.code());
                }
            }
        });
    }

    /**
     * 准备加密值（湖南工学院内网登录使用）
     */
    private String prepareEncodedValue(String username, String password) {
        try {
            String combined = username + "%%%" + password;
            return Base64.encodeToString(combined.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT).trim();
        } catch (Exception e) {
            Log.e(TAG, "准备加密值失败", e);
            return "";
        }
    }

    /**
     * 比较两个课程数据字符串是否相同
     */
    private boolean courseContentsAreEqual(String data1, String data2) {
        return data1.equals(data2);
    }
    
    /**
     * 从系统获取当前周次（湖南工学院内网登录使用）
     */
    private void getCurrentWeekFromSystem() throws IOException {
        Request weekRequest = new Request.Builder()
                .url("https://jwxt.hnit.edu.cn/jsxsd/xskb/xskb_list.do")
                .build();

        try (Response weekResponse = httpClient.newCall(weekRequest).execute()) {
            if (!weekResponse.isSuccessful()) {
                throw new IOException("获取当前周次失败: " + weekResponse.code());
            }

            String html = weekResponse.body().string();
            int startIndex = html.indexOf("var zc = ");
            if (startIndex != -1) {
                int endIndex = html.indexOf(";" , startIndex);
                if (endIndex != -1) {
                    String weekStr = html.substring(startIndex + 9, endIndex).trim();
                    currentWeek = weekStr;
                    Log.d(TAG, "获取到当前周次: " + currentWeek);
                }
            }
        }
    }

    /**
     * 获取课程表（湖南工学院内网登录使用）
     */
    private void getCourseSchedule() throws IOException {
        Request scheduleRequest = new Request.Builder()
                .url("https://jwxt.hnit.edu.cn/jsxsd/xskb/xskb_list.do")
                .build();

        try (Response scheduleResponse = httpClient.newCall(scheduleRequest).execute()) {
            if (!scheduleResponse.isSuccessful()) {
                throw new IOException("获取课表失败: " + scheduleResponse.code());
            }

            String htmlContent = scheduleResponse.body().string();
            JSONObject jsonData = parseHtmlToJson(htmlContent);

            if (jsonData != null) {
                JSONObject standardData = convertToStandardFormat(jsonData);
                
                SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
                String existingData = sharedPreferences.getString("CourseList", "");

                // 设置标志表示已经尝试过网络更新
                hasAttemptedUpdate = true;
                
                if (!courseContentsAreEqual(existingData, standardData.toString())) {
                    sharedPreferences.edit()
                            .putString("CourseList", standardData.toString())
                            .apply();
                    notifyError("已更新到最新数据");
                } else {
                    // 数据没有变化，显示提示
                    notifyError("当前已是最新数据");
                }

                // 设置当前周次
                getCurrentWeekFromSystem();
                
                List<List<Course>> weeklyCourses = CourseDataManager.parseCourseData(standardData.toString());
                callback.onCourseDataReceived(weeklyCourses);
            } else {
                throw new IOException("解析课表HTML失败");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析HTML为JSON（湖南工学院内网登录使用）
     */
    private JSONObject parseHtmlToJson(String html) {
        try {
            JSONObject jsonData = new JSONObject();
            JSONArray dataArray = new JSONArray();

            JSONArray dateArray = parseDates(html);

            int startIndex = html.indexOf("<table id=\"kbtable\" ");
            if (startIndex == -1) return null;

            int endIndex = html.indexOf("</table>", startIndex);
            if (endIndex == -1) return null;

            String tableHtml = html.substring(startIndex, endIndex);
            String[] rows = tableHtml.split("<tr>");

            JSONArray itemArray = new JSONArray();

            for (int i = 2; i < rows.length; i++) {
                String row = rows[i];
                String[] cells = row.split("<td[^>]*>");

                String timeSlot = extractText(cells[1]).replaceAll("\\s+", "");

                for (int day = 2; day <= 8; day++) {
                    if (day >= cells.length) continue;

                    String cellContent = cells[day];
                    if (cellContent.contains("kbcontent")) {
                        String[] courses = cellContent.split("<div class=\"kbcontent\">");

                        for (int c = 1; c < courses.length; c++) {
                            String courseHtml = courses[c].split("</div>")[0];
                            JSONObject course = parseCourse(courseHtml, timeSlot, day - 1);
                            if (course != null) {
                                itemArray.put(course);
                            }
                        }
                    }
                }
            }

            JSONObject dataObject = new JSONObject();
            dataObject.put("item", itemArray);
            dataObject.put("date", dateArray);
            dataArray.put(dataObject);

            jsonData.put("data", dataArray);
            return jsonData;

        } catch (JSONException e) {
            Log.e(TAG, "解析HTML为JSON失败", e);
            return null;
        }
    }

    /**
     * 解析日期（湖南工学院内网登录使用）
     */
    private JSONArray parseDates(String html) throws JSONException {
        JSONArray dateArray = new JSONArray();
        int startIndex = html.indexOf("<table id=\"kbtable\" ");
        if (startIndex == -1) return dateArray;

        int endIndex = html.indexOf("</table>", startIndex);
        if (endIndex == -1) return dateArray;

        String tableHtml = html.substring(startIndex, endIndex);
        String[] rows = tableHtml.split("<tr>");
        if (rows.length < 2) return dateArray;

        String headerRow = rows[1];
        String[] headers = headerRow.split("<th[^>]*>");

        for (int i = 2; i < headers.length && i <= 8; i++) {
            String header = headers[i];
            String dateText = extractText(header).trim();

            JSONObject dateObj = new JSONObject();
            dateObj.put("xqmc", "星期" + (i - 1));
            dateObj.put("date", dateText.split("\\s+")[0]);

            dateArray.put(dateObj);
        }

        return dateArray;
    }

    /**
     * 解析课程信息（湖南工学院内网登录使用）
     */
    private JSONObject parseCourse(String courseHtml, String timeSlot, int weekday) throws JSONException {
        try {
            String[] parts = courseHtml.split("<br>");
            if (parts.length < 4) return null;

            String courseName = extractText(parts[0]).trim();
            String teacher = extractText(parts[1]).replace("教师:", "").trim();
            String location = extractText(parts[2]).replace("地点:", "").trim();
            String weekRange = extractText(parts[3]).replace("周次:", "").trim();

            String classTime = "第" + timeSlot.split("-")[0] + "-" + timeSlot.split("-")[1] + "节";
            String maxClassTime = getMaxClassTime(timeSlot);

            JSONObject course = new JSONObject();
            course.put("courseName", courseName);
            course.put("teacherName", teacher);
            course.put("location", location);
            course.put("classWeek", weekRange);
            course.put("classTime", weekday + classTime);
            course.put("maxClassTime", maxClassTime);

            String weekDetails = generateWeekDetails(weekRange);
            course.put("classWeekDetails", weekDetails);

            return course;
        } catch (Exception e) {
            Log.e(TAG, "解析课程信息失败", e);
            return null;
        }
    }

    /**
     * 提取文本（湖南工学院内网登录使用）
     */
    private String extractText(String html) {
        // 简单的HTML标签移除
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .trim();
    }

    /**
     * 获取最大课时（湖南工学院内网登录使用）
     */
    private String getMaxClassTime(String timeSlot) {
        String[] parts = timeSlot.split("-");
        if (parts.length < 2) return "第一大节";

        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[1]);

        if (start == 1 && end == 2) return "第一大节";
        else if (start == 3 && end == 4) return "第二大节";
        else if (start == 5 && end == 6) return "第三大节";
        else if (start == 7 && end == 8) return "第四大节";
        else if (start == 9 && end == 10) return "第五大节";
        else return "第一大节";
    }

    /**
     * 生成周次详情（湖南工学院内网登录使用）
     */
    private String generateWeekDetails(String weekRange) {
        StringBuilder details = new StringBuilder(",");
        
        // 这里可以根据具体的周次格式解析，这里是简单示例
        if (weekRange.contains(",")) {
            String[] weeks = weekRange.split(",");
            for (String week : weeks) {
                try {
                    details.append(Integer.parseInt(week)).append(",");
                } catch (NumberFormatException ignored) {}
            }
        } else if (weekRange.contains("-")) {
            String[] parts = weekRange.split("-");
            try {
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1].replaceAll("\\D", ""));
                
                for (int i = start; i <= end; i++) {
                    details.append(i).append(",");
                }
            } catch (NumberFormatException ignored) {}
        } else {
            try {
                details.append(Integer.parseInt(weekRange)).append(",");
            } catch (NumberFormatException ignored) {}
        }
        
        return details.toString();
    }

    /**
     * 转换为标准格式（湖南工学院内网登录使用）
     */
    private JSONObject convertToStandardFormat(JSONObject jsonData) throws JSONException {
        // 这里根据标准格式进行转换
        // 由于我们已经在parseCourseData中处理了格式，这里可能不需要额外转换
        return jsonData;
    }

    /**
     * 通知错误
     */
    private void notifyError(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            callback.onError(message);
        });
    }

    /**
     * 获取当前周次
     */
    public String getCurrentWeek() {
        return currentWeek;
    }
}