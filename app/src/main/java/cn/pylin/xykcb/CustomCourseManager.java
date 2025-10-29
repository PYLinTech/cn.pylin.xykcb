package cn.pylin.xykcb;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CustomCourseManager {
    private static final String TAG = "CustomCourseManager";
    private static final String PREFS_NAME = "CustomCourses";
    private static final String KEY_COURSES = "custom_courses";
    private static final String KEY_NEXT_ID = "next_course_id";

    private Context context;
    private List<CustomCourse> customCourses;
    private int nextCourseId;

    public CustomCourseManager(Context context) {
        this.context = context;
        this.customCourses = new ArrayList<>();
        loadCustomCourses();
    }

    /**
     * 添加自定义课程
     */
    public boolean addCustomCourse(CustomCourse course) {
        if (course == null || course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
            return false;
        }
        
        customCourses.add(course);
        return saveCustomCourses();
    }

    /**
     * 更新自定义课程
     */
    public boolean updateCustomCourse(int index, CustomCourse course) {
        if (index < 0 || index >= customCourses.size() || course == null) {
            return false;
        }
        
        customCourses.set(index, course);
        return saveCustomCourses();
    }

    /**
     * 删除自定义课程
     */
    public boolean deleteCustomCourse(int index) {
        if (index < 0 || index >= customCourses.size()) {
            return false;
        }
        
        customCourses.remove(index);
        return saveCustomCourses();
    }

    /**
     * 获取所有自定义课程
     */
    public List<CustomCourse> getAllCustomCourses() {
        return new ArrayList<>(customCourses);
    }

    /**
     * 将自定义课程转换为标准课程列表格式
     */
    public static List<List<Course>> getCustomCoursesAsCourseList(Context context) {
        List<CustomCourse> customCourses = getCustomCourses(context);
        List<List<Course>> result = new ArrayList<>();
        
        // 初始化7天的课程列表
        for (int i = 0; i < 7; i++) {
            result.add(new ArrayList<>());
        }
        
        // 将自定义课程转换为标准课程并分组
        for (CustomCourse customCourse : customCourses) {
            // 对于多选星期的课程，为每个选中的星期创建单独的课程副本
            for (int weekday : customCourse.getWeekdays()) {
                if (weekday >= 1 && weekday <= 7) {
                    // 为每个星期创建单独的课程对象，classTime只包含对应的星期数字
                    Course course = customCourse.toCourseForWeekday(weekday);
                    result.get(weekday - 1).add(course);
                }
            }
        }
        
        return result;
    }

    // 获取自定义课程列表
    public static List<CustomCourse> getCustomCourses(Context context) {
        CustomCourseManager manager = new CustomCourseManager(context);
        return manager.getAllCustomCourses();
    }

    /**
     * 保存自定义课程到SharedPreferences
     */
    private boolean saveCustomCourses() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            JSONArray coursesArray = new JSONArray();
            for (CustomCourse course : customCourses) {
                coursesArray.put(courseToJson(course));
            }
            
            editor.putString(KEY_COURSES, coursesArray.toString());
            editor.putInt(KEY_NEXT_ID, nextCourseId);
            
            return editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "保存自定义课程失败", e);
            return false;
        }
    }

    /**
     * 从SharedPreferences加载自定义课程
     */
    private void loadCustomCourses() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String coursesJson = prefs.getString(KEY_COURSES, "[]");
            nextCourseId = prefs.getInt(KEY_NEXT_ID, 1);
            
            JSONArray coursesArray = new JSONArray(coursesJson);
            customCourses.clear();
            
            for (int i = 0; i < coursesArray.length(); i++) {
                JSONObject courseJson = coursesArray.getJSONObject(i);
                CustomCourse course = jsonToCourse(courseJson);
                if (course != null) {
                    customCourses.add(course);
                }
            }
            
            Log.d(TAG, "加载自定义课程成功，数量：" + customCourses.size());
        } catch (Exception e) {
            Log.e(TAG, "加载自定义课程失败", e);
            customCourses.clear();
        }
    }

    /**
     * 将CustomCourse转换为JSON对象
     */
    private JSONObject courseToJson(CustomCourse course) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("courseName", course.getCourseName());
        json.put("location", course.getLocation());
        json.put("teacher", course.getTeacher());
        json.put("weekday", course.getWeekday()); // 向后兼容
        json.put("note", course.getNote());
        json.put("color", course.getColor());
        
        // 星期列表（支持多选）
        JSONArray weekdaysArray = new JSONArray();
        for (int weekday : course.getWeekdays()) {
            weekdaysArray.put(weekday);
        }
        json.put("weekdays", weekdaysArray);
        
        // 时间槽列表
        JSONArray timeSlotsArray = new JSONArray();
        for (int slot : course.getTimeSlots()) {
            timeSlotsArray.put(slot);
        }
        json.put("timeSlots", timeSlotsArray);
        
        // 周次列表
        JSONArray weeksArray = new JSONArray();
        for (int week : course.getWeeks()) {
            weeksArray.put(week);
        }
        json.put("weeks", weeksArray);
        
        return json;
    }

    /**
     * 从JSON对象创建CustomCourse
     */
    private CustomCourse jsonToCourse(JSONObject json) {
        try {
            CustomCourse course = new CustomCourse();
            course.setCourseName(json.optString("courseName", ""));
            course.setLocation(json.optString("location", ""));
            course.setTeacher(json.optString("teacher", ""));
            course.setNote(json.optString("note", ""));
            course.setColor(json.optInt("color", 0));
            course.setCustom(json.optBoolean("isCustom", true));
            
            // 星期列表（支持多选）
            JSONArray weekdaysArray = json.optJSONArray("weekdays");
            if (weekdaysArray != null) {
                List<Integer> weekdays = new ArrayList<>();
                for (int i = 0; i < weekdaysArray.length(); i++) {
                    weekdays.add(weekdaysArray.getInt(i));
                }
                course.setWeekdays(weekdays);
            } else {
                // 向后兼容：如果不存在weekdays字段，使用旧的weekday字段
                course.setWeekday(json.optInt("weekday", 1));
            }
            
            // 时间槽列表
            JSONArray timeSlotsArray = json.optJSONArray("timeSlots");
            if (timeSlotsArray != null) {
                List<Integer> timeSlots = new ArrayList<>();
                for (int i = 0; i < timeSlotsArray.length(); i++) {
                    timeSlots.add(timeSlotsArray.getInt(i));
                }
                course.setTimeSlots(timeSlots);
            }
            
            // 周次列表
            JSONArray weeksArray = json.optJSONArray("weeks");
            if (weeksArray != null) {
                List<Integer> weeks = new ArrayList<>();
                for (int i = 0; i < weeksArray.length(); i++) {
                    weeks.add(weeksArray.getInt(i));
                }
                course.setWeeks(weeks);
            }
            
            return course;
        } catch (Exception e) {
            Log.e(TAG, "解析自定义课程失败", e);
            return null;
        }
    }
}