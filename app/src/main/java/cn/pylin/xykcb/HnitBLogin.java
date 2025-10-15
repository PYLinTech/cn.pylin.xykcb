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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HnitBLogin {
    private static final String TAG = "HnitJwxtLogin";
    private final Context context;
    private final CourseDataCallback callback;
    private final OkHttpClient httpClient;
    private String currentWeek = "1";

    public interface CourseDataCallback {
        void onCourseDataReceived(List<List<Course>> weeklyCourses);
        void onError(String message);
    }

    public HnitBLogin(Context context, CourseDataCallback callback) {
        this.context = context;
        this.callback = callback;
        this.httpClient = new OkHttpClient.Builder()
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
    }

    public void performLogin(String username, String password) {
        loadLocalCourseData();

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

    private void loadLocalCourseData() {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("CourseListInfo", Context.MODE_PRIVATE);
            String localCourseList = sharedPreferences.getString("CourseList", "");

            if (!localCourseList.isEmpty()) {
                JSONObject jsonData = new JSONObject(localCourseList);
                JSONObject standardData = convertToStandardFormat(jsonData);
                List<List<Course>> weeklyCourses = parseCourseData(standardData.toString());
                callback.onCourseDataReceived(weeklyCourses);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载本地数据失败", e);
        }
    }

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
                int endIndex = html.indexOf(";", startIndex);
                if (endIndex != -1) {
                    String weekStr = html.substring(startIndex + 9, endIndex).trim();
                    currentWeek = weekStr;
                    Log.d(TAG, "获取到当前周次: " + currentWeek);
                }
            }
        }
    }

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

                if (!standardData.toString().equals(existingData)) {
                    sharedPreferences.edit()
                            .putString("CourseList", standardData.toString())
                            .apply();
                    Log.d(TAG, "课程数据已更新");
                }

                List<List<Course>> weeklyCourses = parseCourseData(standardData.toString());
                callback.onCourseDataReceived(weeklyCourses);
            } else {
                throw new IOException("解析课表HTML失败");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject parseHtmlToJson(String html) {
        try {
            JSONObject jsonData = new JSONObject();
            JSONArray dataArray = new JSONArray();

            JSONArray dateArray = parseDates(html);

            int startIndex = html.indexOf("<table id=\"kbtable\"");
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

    private JSONArray parseDates(String html) throws JSONException {
        JSONArray dateArray = new JSONArray();
        int startIndex = html.indexOf("<table id=\"kbtable\"");
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
            Log.e(TAG, "解析课程失败: " + courseHtml, e);
            return null;
        }
    }

    private String generateWeekDetails(String weekRange) {
        try {
            StringBuilder details = new StringBuilder(",");

            if (weekRange.contains("-")) {
                String[] range = weekRange.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);

                for (int i = start; i <= end; i++) {
                    details.append(i).append(",");
                }
            } else if (weekRange.contains(",")) {
                String[] weeks = weekRange.split(",");
                for (String week : weeks) {
                    details.append(week.trim()).append(",");
                }
            } else {
                details.append(weekRange.trim()).append(",");
            }

            return details.toString();
        } catch (Exception e) {
            return ",";
        }
    }

    private String getMaxClassTime(String timeSlot) {
        switch (timeSlot) {
            case "1-2": return "第一大节";
            case "3-4": return "第二大节";
            case "5-6": return "第三大节";
            case "7-8": return "第四大节";
            case "9-10": return "第五大节";
            default: return timeSlot;
        }
    }

    private String extractText(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
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
                        dateInfo.getString("xqmc"),
                        item.getString("classTime"),
                        item.getString("courseName"),
                        item.getString("location"),
                        item.getString("teacherName"),
                        item.getString("classWeek"),
                        item.getString("maxClassTime"),
                        item.optString("classWeekDetails", "")
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

    private String prepareEncodedValue(String account, String password) {
        String encodedAccount = Base64.encodeToString(account.getBytes(), Base64.NO_WRAP);
        String encodedPassword = Base64.encodeToString(password.getBytes(), Base64.NO_WRAP);
        return encodedAccount + "%%%" + encodedPassword;
    }

    private void notifyError(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(message));
    }

    // 新增的转换方法，不改变原有代码结构
    private JSONObject convertToStandardFormat(JSONObject originalData) throws JSONException {
        JSONObject standardData = new JSONObject();
        JSONArray standardDataArray = new JSONArray();
        
        JSONObject originalDataObject = originalData.getJSONArray("data").getJSONObject(0);
        JSONObject standardDataObject = new JSONObject();
        
        // 转换日期信息
        JSONArray originalDateArray = originalDataObject.getJSONArray("date");
        JSONArray standardDateArray = new JSONArray();
        for (int i = 0; i < originalDateArray.length(); i++) {
            JSONObject dateObj = new JSONObject();
            dateObj.put("xqmc", originalDateArray.getJSONObject(i).getString("xqmc"));
            dateObj.put("date", originalDateArray.getJSONObject(i).optString("date", ""));
            standardDateArray.put(dateObj);
        }
        
        // 转换课程项
        JSONArray originalItemArray = originalDataObject.getJSONArray("item");
        JSONArray standardItemArray = new JSONArray();
        for (int i = 0; i < originalItemArray.length(); i++) {
            JSONObject originalItem = originalItemArray.getJSONObject(i);
            JSONObject standardItem = new JSONObject();
            
            standardItem.put("courseName", originalItem.getString("courseName"));
            standardItem.put("teacherName", originalItem.getString("teacherName"));
            standardItem.put("location", originalItem.getString("location"));
            standardItem.put("classWeek", originalItem.getString("classWeek"));
            standardItem.put("classTime", originalItem.getString("classTime"));
            standardItem.put("maxClassTime", originalItem.getString("maxClassTime"));
            standardItem.put("classWeekDetails", originalItem.optString("classWeekDetails", ""));
            
            standardItemArray.put(standardItem);
        }
        
        standardDataObject.put("item", standardItemArray);
        standardDataObject.put("date", standardDateArray);
        standardDataArray.put(standardDataObject);
        standardData.put("data", standardDataArray);
        
        return standardData;
    }
}