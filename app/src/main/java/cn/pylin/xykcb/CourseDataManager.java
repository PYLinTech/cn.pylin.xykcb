// CourseDataManager.java
package cn.pylin.xykcb;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CourseDataManager {
    private static final String PREFS_NAME = "CourseListInfo";
    private static final String PREFS_WEEK_DATES = "WeekDates";
    private static final String PREFS_COURSE_LIST = "CourseList";

    public static int getCurrentWeek(Context context) {
        SharedPreferences weekPrefs = context.getSharedPreferences(PREFS_WEEK_DATES, Context.MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("M.d", Locale.getDefault());
        String today = sdf.format(new Date());

        for (String key : weekPrefs.getAll().keySet()) {
            String dates = weekPrefs.getString(key, "");
            if (dates != null && dates.contains(today)) {
                try {
                    return Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return 1; // 默认第1周
    }

    public static List<List<Course>> parseCourseData(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String courseListJson = sharedPreferences.getString(PREFS_COURSE_LIST, "");

        return parseCourseData(courseListJson);
    }

    public static List<List<Course>> parseCourseData(String jsonString) {
        List<Course> courseList = new ArrayList<>();
        if (jsonString == null || jsonString.isEmpty()) {
            return new ArrayList<>();
        }

        try {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return groupByWeekday(courseList);
    }

    private static List<List<Course>> groupByWeekday(List<Course> courses) {
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

    public static String processLocation(String location) {
        if (TextUtils.isEmpty(location)) {
            return "";
        }
        return location
                .replace("(智慧教室)", "")
                .replace("（智慧教室）", "")
                .replace("(多媒体)", "")
                .replace("（多媒体）", "")
                .replace("(语音室)", "")
                .replace("（语音室）", "")
                .trim();
    }
}