package cn.pylin.xykcb;

import java.util.ArrayList;
import java.util.List;

public class Course {
    private final String date;
    private final String classTime;
    private final String courseName;
    private final String location;
    private final String teacher;
    private final String weekRange;
    private final String maxClassTime;
    final String classWeekDetails;

    public Course(String date, String classTime, String courseName,
                  String location, String teacher, String weekRange,
                  String maxClassTime, String classWeekDetails) {
        this.date = date;
        this.classTime = classTime;
        this.courseName = courseName;
        this.location = location;
        this.teacher = teacher;
        this.weekRange = weekRange;
        this.maxClassTime = maxClassTime;
        this.classWeekDetails = classWeekDetails;
    }

    public String getCourseName() { return courseName; }
    public String getLocation() { return location; }
    public String getTeacher() { return teacher; }
    public String getclassWeek() { return weekRange; }

    public int getWeekday() {
        if (classTime == null || classTime.isEmpty()) return -1;
        try {
            return Integer.parseInt(classTime.substring(0, 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public List<Integer> getTimeSlots() {
        List<Integer> slots = new ArrayList<>();
        if (maxClassTime == null) return slots;

        String[] parts = maxClassTime.split(",");
        for (String part : parts) {
            String time = part.trim();
            switch (time) {
                case "第一大节": slots.add(1); break;
                case "第二大节": slots.add(2); break;
                case "第三大节": slots.add(3); break;
                case "第四大节": slots.add(4); break;
                case "第五大节": slots.add(5); break;
            }
        }
        return slots;
    }

    public boolean isInWeek(int week) {
        // 如果没有详细周数信息，默认显示所有周的课程
        if (classWeekDetails == null || classWeekDetails.isEmpty()) {
            return true;
        }
        
        // 按英文逗号分割周数字符串，然后精准匹配
        String[] weekArray = classWeekDetails.split(",");
        for (String weekStr : weekArray) {
            try {
                if (Integer.parseInt(weekStr.trim()) == week) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // 忽略无效的周数格式
            }
        }
        return false;
    }

    public boolean isInTimeSlot(int weekday, int timeSlot) {
        return getWeekday() == weekday && getTimeSlots().contains(timeSlot);
    }
}