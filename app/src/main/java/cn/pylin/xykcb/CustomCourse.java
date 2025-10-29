package cn.pylin.xykcb;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义课程数据模型
 * 支持用户手动添加的课程
 */
public class CustomCourse {
    private String courseName;
    private String location;
    private String teacher;
    private List<Integer> weekdays; // 1-7 对应周一到周日，支持多选
    private List<Integer> timeSlots; // 1-5 对应五大节
    private List<Integer> weeks; // 1-20 对应周次
    private String note; // 备注信息
    private int color; // 自定义颜色
    private boolean isCustom; // 标记是否为自定义课程

    public CustomCourse() {
        this.weekdays = new ArrayList<>();
        this.timeSlots = new ArrayList<>();
        this.weeks = new ArrayList<>();
        this.isCustom = true;
    }

    // Getters and Setters
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public List<Integer> getWeekdays() { return weekdays; }
    public void setWeekdays(List<Integer> weekdays) { this.weekdays = weekdays; }

    // 向后兼容的方法
    public int getWeekday() { 
        return weekdays.isEmpty() ? 1 : weekdays.get(0); 
    }
    public void setWeekday(int weekday) { 
        this.weekdays.clear();
        this.weekdays.add(weekday);
    }

    public List<Integer> getTimeSlots() { return timeSlots; }
    public void setTimeSlots(List<Integer> timeSlots) { this.timeSlots = timeSlots; }

    public List<Integer> getWeeks() { return weeks; }
    public void setWeeks(List<Integer> weeks) { this.weeks = weeks; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public void setCustom(boolean custom) { isCustom = custom; }
    public Course toCourseForWeekday(int weekday) {
        String weekRange = weeks.isEmpty() ? "1-20" : getWeekRangeString();
        String timeSlotString = getTimeSlotString();
        String weekDetails = getWeekDetailsString();
        
        // 为单个星期生成对应的星期显示字符串
        String[] weekdayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String weekdayString = (weekday >= 1 && weekday <= 7) ? weekdayNames[weekday - 1] : "周一";
        
        // classTime只包含单个星期数字，确保兼容现有逻辑
        String classTime = String.valueOf(weekday);
        
        return new Course(
                weekdayString, // date
                classTime,    // classTime只包含单个星期数字
                courseName,
                location,
                teacher,
                weekRange,
                timeSlotString,
                weekDetails
        );
    }

    private String getWeekRangeString() {
        if (weeks.isEmpty()) return "1-20";
        
        int min = weeks.get(0);
        int max = weeks.get(0);
        for (int week : weeks) {
            if (week < min) min = week;
            if (week > max) max = week;
        }
        return min + "-" + max;
    }

    private String getTimeSlotString() {
        StringBuilder sb = new StringBuilder();
        for (int slot : timeSlots) {
            if (sb.length() > 0) sb.append(",");
            switch (slot) {
                case 1: sb.append("第一大节"); break;
                case 2: sb.append("第二大节"); break;
                case 3: sb.append("第三大节"); break;
                case 4: sb.append("第四大节"); break;
                case 5: sb.append("第五大节"); break;
            }
        }
        return sb.toString();
    }

    private String getWeekDetailsString() {
        StringBuilder sb = new StringBuilder();
        for (int week : weeks) {
            if (sb.length() > 0) sb.append(",");
            sb.append(week);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "CustomCourse{" +
                "courseName='" + courseName + '\'' +
                ", location='" + location + '\'' +
                ", teacher='" + teacher + '\'' +
                ", weekdays=" + weekdays +
                ", timeSlots=" + timeSlots +
                ", weeks=" + weeks +
                ", note='" + note + '\'' +
                ", color=" + color +
                ", isCustom=" + isCustom +
                '}';
    }
}