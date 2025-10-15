package cn.pylin.xykcb;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HnitALogin {
    private final Context context;
    private String token;
    private String xnxq01id;
    private String kbjcmsid;
    private String currentWeek;
    private final CourseDataCallback callback;
    private final OkHttpClient httpClient;

    public interface CourseDataCallback {
        void onCourseDataReceived(List<List<Course>> weeklyCourses);
        void onError(String message);
    }

    public HnitALogin(Context context, CourseDataCallback callback) {
        this.context = context;
        this.callback = callback;
        // 初始化 OkHttpClient
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public void performLogin(String username, String password) {
        // 首先直接加载本地数据
        loadLocalCourseData();
        
        // 然后执行网络登录和更新
        String encryptedPassword = encryptPassword(password);
        String loginUrl = "https://jw.hnit.edu.cn/njwhd/login?userNo=" + username + "&pwd=" + encryptedPassword;
    
        Request request = new Request.Builder()
                .url(loginUrl)
                .post(okhttp3.RequestBody.create("", null))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (e instanceof java.net.SocketTimeoutException) {
                    // 处理超时异常
                } else {
                    // 处理其他异常
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("token")) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                token = data.getString("token");
                                saveLoginInfo(username, password);
                                getXnxqListId();
                            } else {
                                // 登录失败处理
                            }
                        }
                    } catch (JSONException e) {
                        // JSON解析异常处理
                    }
                } else {
                    // HTTP错误处理
                }
            }
        });
    }

    // 添加加载本地课程数据的方法
    private void loadLocalCourseData() {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
            String localCourseList = sharedPreferences.getString("CourseList", "");
            
            if (!localCourseList.isEmpty()) {
                // 解析本地课程数据
                getWeek();
                List<List<Course>> weeklyCourses = parseCourseData(localCourseList);
                // 通过回调返回数据
                callback.onCourseDataReceived(weeklyCourses);
            } else {
                // 没有本地数据
                callback.onError("正在尝试更新数据...");
            }
        } catch (Exception e) {
            callback.onError("加载本地数据失败");
        }
    }

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
            return null;
        }
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private void saveLoginInfo(String username, String password) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();
    }

    private void getXnxqListId() {
        String XnxqListIdUrl = "https://jw.hnit.edu.cn/njwhd/getXnxqList?token=" + token;

        Request request = new Request.Builder()
                .url(XnxqListIdUrl)
                .post(okhttp3.RequestBody.create("", null))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取学年学期失败：" + e.getMessage());
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
                                xnxq01id = maxNumObject.getString("xnxq01id");
                                getWeek();
                            } else {
                                callback.onError("获取学年学期失败！");
                            }
                        }
                    } catch (JSONException e) {
                        callback.onError("解析学年学期数据失败：" + e.getMessage());
                    }
                } else {
                    callback.onError("获取学年学期失败，错误码：" + response.code());
                }
            }
        });
    }

    private void getWeek() {
        // 不再通过网络请求获取周数，而是从WeekDates反求
        try {
            // 使用CourseDataManager的getCurrentWeek方法从WeekDates获取当前周数
            int week = CourseDataManager.getCurrentWeek(context);
            currentWeek = String.valueOf(week);
            // 直接调用下一步
            getKbjcmsid();
        } catch (Exception e) {
            // 如果获取失败，设置默认值
            currentWeek = "1";
            callback.onError("从本地获取当前周次失败，使用默认值：" + e.getMessage());
            getKbjcmsid();
        }
    }

    private void getKbjcmsid() {
        String sjkbmsUrl = "https://jw.hnit.edu.cn/njwhd/Get_sjkbms?token=" + token;

        Request request = new Request.Builder()
                .url(sjkbmsUrl)
                .post(okhttp3.RequestBody.create("", null))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取课程节次模式失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            if (jsonResponse.has("data") && jsonResponse.getJSONArray("data").length() > 0) {
                                kbjcmsid = jsonResponse.getJSONArray("data").getJSONObject(0).getString("kbjcmsid");
                                getCourseList();
                            } else {
                                callback.onError("获取课程节次模式失败！");
                            }
                        }
                    } catch (JSONException e) {
                        callback.onError("解析课程节次模式数据失败：" + e.getMessage());
                    }
                } else {
                    callback.onError("获取课程节次模式失败，错误码：" + response.code());
                }
            }
        });
    }

    public void getCourseList() {
        String CourseListUrl = "https://jw.hnit.edu.cn/njwhd/student/curriculum?token=" + token +
                "&xnxq01id=" + xnxq01id + "&kbjcmsid=" + kbjcmsid + "&week=all";

        Request request = new Request.Builder()
                .url(CourseListUrl)
                .post(okhttp3.RequestBody.create("", null))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取课程列表失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String newCourseList = responseBody.string();

                            SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
                            String existingCourseList = sharedPreferences.getString("CourseList", "");

                            if (!existingCourseList.equals(newCourseList)) {
                                sharedPreferences.edit().putString("CourseList", newCourseList).apply();
                                // 课程数据已更新，显示提示
                                callback.onError("课程已更新！");
                            }

                            List<List<Course>> weeklyCourses = parseCourseData(newCourseList);
                            callback.onCourseDataReceived(weeklyCourses);
                        }
                    } catch (JSONException e) {
                        callback.onError("解析课程列表数据失败：" + e.getMessage());
                    }
                } else {
                    callback.onError("获取课程列表失败，错误码：" + response.code());
                }
            }
        });
    }

    private List<List<Course>> parseCourseData(String jsonString) throws JSONException {
        List<Course> courseList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray dataArray = jsonObject.getJSONArray("data");

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject dataObject = dataArray.getJSONObject(i);
            JSONArray itemArray = dataObject.getJSONArray("item");
            JSONArray dateArray = dataObject.getJSONArray("date");

            for (int j = 0; j < itemArray.length(); j++) {
                JSONObject item = itemArray.getJSONObject(j);
                JSONObject dateInfo = dateArray.getJSONObject(j % dateArray.length());

                Course course = new Course(
                        dateInfo.getString("xqmc"), //星期
                        item.getString("classTime"), //截取第一位判断星期
                        item.getString("courseName"),  //课程名称
                        item.getString("location"),  //授课地点
                        item.getString("teacherName"), //教师名称
                        item.getString("classWeek"), //显示用周次
                        item.getString("maxClassTime"), //判断当日节次
                        item.optString("classWeekDetails", "") //判断周次
                );
                courseList.add(course);
            }
        }
        return groupByWeekday(courseList);
    }

    private List<List<Course>> groupByWeekday(List<Course> courses) {
        List<List<Course>> groupedCourses = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            groupedCourses.add(new ArrayList<>());
        }

        for (Course course : courses) {
            int weekday = course.getWeekday();
            if (weekday >= 1 && weekday <= 7) {
                groupedCourses.get(weekday - 1).add(course);
            }
        }
        return groupedCourses;
    }

    public String getCurrentWeek() {
        return currentWeek;
    }
}