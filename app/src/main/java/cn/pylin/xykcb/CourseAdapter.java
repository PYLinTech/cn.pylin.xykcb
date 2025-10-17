// CourseAdapter.java
package cn.pylin.xykcb;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.CourseViewHolder> {
    private List<List<Course>> weeklyCourses;
    private final String[] weekHeaders;
    private final HashMap<String, Integer> courseColors = new HashMap<>();
    private final int[] colorPalette;
    private final Context context;
    private int recyclerViewHeight = 0;
    private final int HEADER_ROW_HEIGHT = 50;
    private int currentWeek = 1;
    private boolean showAllWeeks = false; // 新增：是否显示所有周次

    // 新增方法：明确设置当前周次并刷新UI
    public void setCurrentWeek(int week) {
        this.currentWeek = week;
        this.showAllWeeks = false;
        MainActivity.Week = String.valueOf(week);
        notifyDataSetChanged();
    }

    // 新增方法：设置显示所有周次
    public void setShowAllWeeks(boolean showAll) {
        this.showAllWeeks = showAll;
        MainActivity.Week = showAll ? "all" : String.valueOf(currentWeek);
        notifyDataSetChanged();
    }

    // 新增方法：更新课程数据
    public void updateCourses(List<List<Course>> newWeeklyCourses) {
        this.weeklyCourses = newWeeklyCourses;
        // 重新分配课程颜色
        assignCourseColors();
        notifyDataSetChanged();
    }

    public CourseAdapter(Context context, List<List<Course>> weeklyCourses, String[] weekHeaders) {
        this.context = context;
        this.weeklyCourses = weeklyCourses;
        this.weekHeaders = weekHeaders;
        this.currentWeek = Integer.parseInt(MainActivity.Week);

        colorPalette = new int[]{
                ContextCompat.getColor(context, R.color.course_color_1),
                ContextCompat.getColor(context, R.color.course_color_2),
                ContextCompat.getColor(context, R.color.course_color_3),
                ContextCompat.getColor(context, R.color.course_color_4),
                ContextCompat.getColor(context, R.color.course_color_5),
                ContextCompat.getColor(context, R.color.course_color_6),
                ContextCompat.getColor(context, R.color.course_color_7)
        };

        assignCourseColors();
    }

    public void setRecyclerViewHeight(int height) {
        this.recyclerViewHeight = height;
        notifyDataSetChanged();
    }

    private void assignCourseColors() {
        // 优化后的颜色分配算法，避免相同颜色聚集
        Map<String, Set<Integer>> coursePositionMap = new HashMap<>(); // 记录每门课程的位置
        
        // 第一步：收集所有课程名称和位置信息
        Set<String> allCourseNames = new HashSet<>();
        for (int day = 0; day < weeklyCourses.size(); day++) {
            for (Course course : weeklyCourses.get(day)) {
                String courseName = course.getCourseName();
                allCourseNames.add(courseName);
                
                // 记录课程位置
                if (!coursePositionMap.containsKey(courseName)) {
                    coursePositionMap.put(courseName, new HashSet<>());
                }
                
                // 添加课程的所有时间段位置
                for (int timeSlot = 1; timeSlot <= 5; timeSlot++) {
                    if (course.isInTimeSlot(day + 1, timeSlot)) {
                        coursePositionMap.get(courseName).add(day * 10 + timeSlot); // 用day*10+timeSlot作为唯一位置标识
                    }
                }
            }
        }
        
        // 第二步：为每门课程分配颜色，确保相邻课程颜色不同
        List<String> courseList = new ArrayList<>(allCourseNames);
        
        // 按课程出现次数排序，先处理出现次数多的课程
        courseList.sort((a, b) -> coursePositionMap.get(b).size() - coursePositionMap.get(a).size());
        
        for (String courseName : courseList) {
            if (!courseColors.containsKey(courseName)) {
                // 获取已分配颜色的课程及其位置
                Map<String, Set<Integer>> coloredCoursePositions = new HashMap<>();
                for (Map.Entry<String, Integer> entry : courseColors.entrySet()) {
                    String coloredCourse = entry.getKey();
                    if (coursePositionMap.containsKey(coloredCourse)) {
                        coloredCoursePositions.put(coloredCourse, coursePositionMap.get(coloredCourse));
                    }
                }
                
                // 选择最优颜色
                int selectedColorIndex = selectOptimalColor(courseName, coursePositionMap.get(courseName), coloredCoursePositions);
                courseColors.put(courseName, colorPalette[selectedColorIndex]);
            }
        }
    }
    
    // 选择最优颜色：优先选择与相邻课程不同的颜色
    private int selectOptimalColor(String courseName, Set<Integer> coursePositions, Map<String, Set<Integer>> coloredCoursePositions) {
        // 统计每种颜色的冲突分数（与相邻课程同色）
        int[] colorConflictScore = new int[colorPalette.length];
        // 统计每种颜色的使用次数
        int[] colorUsageCount = new int[colorPalette.length];
        
        // 初始化数组
        for (int i = 0; i < colorPalette.length; i++) {
            colorConflictScore[i] = 0;
            colorUsageCount[i] = 0;
        }
        
        // 计算每种颜色的使用次数
        for (Map.Entry<String, Integer> entry : courseColors.entrySet()) {
            for (int i = 0; i < colorPalette.length; i++) {
                if (entry.getValue().equals(colorPalette[i])) {
                    colorUsageCount[i]++;
                    break;
                }
            }
        }
        
        // 计算每种颜色的冲突分数
        for (Map.Entry<String, Set<Integer>> entry : coloredCoursePositions.entrySet()) {
            String coloredCourse = entry.getKey();
            Set<Integer> coloredPositions = entry.getValue();
            int coloredColorIndex = -1;
            
            // 找到已着色课程的颜色索引
            for (int i = 0; i < colorPalette.length; i++) {
                if (courseColors.get(coloredCourse).equals(colorPalette[i])) {
                    coloredColorIndex = i;
                    break;
                }
            }
            
            if (coloredColorIndex >= 0) {
                // 检查位置是否相邻
                for (int pos1 : coursePositions) {
                    for (int pos2 : coloredPositions) {
                        if (arePositionsAdjacent(pos1, pos2)) {
                            colorConflictScore[coloredColorIndex]++;
                        }
                    }
                }
            }
        }
        
        // 选择冲突分数最低且使用次数较少的颜色
        int bestColorIndex = 0;
        int bestScore = Integer.MAX_VALUE;
        
        for (int i = 0; i < colorPalette.length; i++) {
            // 综合考虑冲突分数和使用次数，冲突分数权重更高
            int currentScore = colorConflictScore[i] * 100 + colorUsageCount[i];
            
            if (currentScore < bestScore) {
                bestScore = currentScore;
                bestColorIndex = i;
            }
        }
        
        return bestColorIndex;
    }
    
    // 判断两个位置是否相邻（同一时间段、同一列或同一行）
    private boolean arePositionsAdjacent(int pos1, int pos2) {
        int day1 = pos1 / 10;
        int time1 = pos1 % 10;
        int day2 = pos2 / 10;
        int time2 = pos2 % 10;
        
        // 同一时间段（水平相邻）
        if (time1 == time2 && Math.abs(day1 - day2) <= 1) {
            return true;
        }
        
        // 同一天（垂直相邻）
        if (day1 == day2 && Math.abs(time1 - time2) <= 1) {
            return true;
        }
        
        return false;
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        int headerHeightPx = (int) (HEADER_ROW_HEIGHT * context.getResources().getDisplayMetrics().density);

        int rowHeight;
        if (position == 0) {
            rowHeight = headerHeightPx;
        } else {
            int remainingHeight = recyclerViewHeight - headerHeightPx;
            int courseRowCount = getItemCount() - 1;
            rowHeight = remainingHeight > 0 ? remainingHeight / courseRowCount : ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
        params.height = rowHeight;
        holder.itemView.setLayoutParams(params);

        if (position == 0) {
            holder.bindHeader();
        } else {
            holder.bindTimeRow(position);
        }
    }

    @Override
    public int getItemCount() {
        return 6;
    }

    class CourseViewHolder extends RecyclerView.ViewHolder {
        private final TextView timeHeader;
        private final CourseTextView[] dayViews = new CourseTextView[7]; // 改为CourseTextView类型

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            timeHeader = itemView.findViewById(R.id.time_header);
            dayViews[0] = itemView.findViewById(R.id.monday);
            dayViews[1] = itemView.findViewById(R.id.tuesday);
            dayViews[2] = itemView.findViewById(R.id.wednesday);
            dayViews[3] = itemView.findViewById(R.id.thursday);
            dayViews[4] = itemView.findViewById(R.id.friday);
            dayViews[5] = itemView.findViewById(R.id.saturday);
            dayViews[6] = itemView.findViewById(R.id.sunday);
        }

        void bindHeader() {
            SpannableString weekText = new SpannableString((showAllWeeks ? "N" : currentWeek) + "周");
            weekText.setSpan(
                    new AbsoluteSizeSpan(22, true),
                    0,
                    String.valueOf(showAllWeeks ? "N" : currentWeek).length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            weekText.setSpan(
                    new AbsoluteSizeSpan(12, true),
                    String.valueOf(showAllWeeks ? "N" : currentWeek).length(),
                    weekText.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            timeHeader.setText(weekText);
            timeHeader.setTextColor(Color.WHITE);
            timeHeader.setPadding(0, 0, 0, 0);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor("#566573"));
            drawable.setCornerRadius(10f);
            timeHeader.setBackground(drawable);

            // 修改日期显示逻辑：全部周次时清空日期，正常周次时显示日期
            if (showAllWeeks) {
                // 全部周次模式：只显示周一到周日，不显示具体日期
                for (int i = 0; i < 7; i++) {
                    String header = weekHeaders[i]; // 周一、周二...
                    dayViews[i].setText(header); // 只显示周几，不显示日期
                    dayViews[i].setTextSize(14);
                    dayViews[i].setGravity(Gravity.CENTER);
                    dayViews[i].setBackgroundColor(Color.TRANSPARENT);
                    dayViews[i].setOnClickListener(null);
                    // 确保标题行隐藏角标
                    dayViews[i].setBadgeVisible(false);
                }
            } else {
                // 正常周次模式：显示周几和具体日期
                SharedPreferences sp = context.getSharedPreferences("WeekDates", Context.MODE_PRIVATE);
                String weekDatesStr = sp.getString(String.valueOf(currentWeek), null);
                String[] weekDates = new String[]{"", "", "", "", "", "", ""}; // 默认空防止异常
                if (weekDatesStr != null) {
                    weekDates = weekDatesStr.split(",");
                }

                for (int i = 0; i < 7; i++) {
                    String header = weekHeaders[i]; // 周一、周二...
                    String date = weekDates[i]; // 03/04 这种
                    dayViews[i].setText(header + "\n" + date); // 一行周，一行日期
                    dayViews[i].setTextSize(14);
                    dayViews[i].setGravity(Gravity.CENTER); // 让文字居中好看
                    dayViews[i].setBackgroundColor(Color.TRANSPARENT);
                    dayViews[i].setOnClickListener(null);
                    // 确保标题行隐藏角标
                    dayViews[i].setBadgeVisible(false);
                }
            }

            timeHeader.setOnClickListener(v -> showWeekSwitchMenu(v));
        }

        void bindTimeRow(int timeSlot) {
            // 检测屏幕方向
            boolean isLandscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            
            String[] timeNames;
            if (isLandscape) {
                // 横屏时使用水平排列的文本
                timeNames = new String[]{"", "第一大节", "第二大节", "第三大节", "第四大节", "第五大节"};
            } else {
                // 竖屏时使用原来的竖直排列文本
                timeNames = new String[]{"", "第\n一\n大\n节", "第\n二\n大\n节", "第\n三\n大\n节", "第\n四\n大\n节", "第\n五\n大\n节"};
            }
            
            timeHeader.setText(timeNames[timeSlot]);
            timeHeader.setBackgroundColor(Color.TRANSPARENT);

            for (int day = 0; day < 7; day++) {
                StringBuilder courseInfo = new StringBuilder();
                boolean hasCourse = false;
                int courseCount = 0;
                Course displayCourse = null;
                List<Course> allCoursesInSlot = new ArrayList<>(); // 新增：收集所有重叠课程

                for (Course course : weeklyCourses.get(day)) {
                    if (course.isInTimeSlot(day + 1, timeSlot) &&
                            (showAllWeeks || course.isInWeek(currentWeek))) {

                        courseCount++;
                        allCoursesInSlot.add(course); // 添加到重叠课程列表
                        
                        if (displayCourse == null || getFirstWeek(course.getclassWeek()) < getFirstWeek(displayCourse.getclassWeek())) {
                            displayCourse = course;
                        }
                    }
                }

                if (displayCourse != null) {
                    String courseName = displayCourse.getCourseName();
                    String shortenedName = courseName;
                    int visualLength = getVisualLength(courseName);
                    if (visualLength > 9) {
                        shortenedName = truncateString(courseName, 5, 0);
                    }

                    String location = displayCourse.getLocation()
                            .replace("(智慧教室)", "")
                            .replace("（智慧教室）", "")
                            .replace("(多媒体)", "")
                            .replace("（多媒体）", "")
                            .replace("(语音室)", "")
                            .replace("（语音室）", "")
                            .trim();
                    if (getVisualLength(location) > 6) {
                        location = truncateString(location, 5, 0);
                    }

                    String teacher = displayCourse.getTeacher();
                    teacher = teacher.replace(",", "\n").replace("，", "\n").replace("、", "\n").replace(";", "\n").replace("；", "\n");
                    if (getVisualLength(teacher) > 6) {
                        teacher = truncateString(teacher, 5, 0);
                    }

                    if (isLandscape) {
                        // 横屏时减少换行，使用更紧凑的格式
                        courseInfo.append(shortenedName)
                                .append("\n")
                                .append(location)
                                .append("\n")
                                .append(teacher);
                    } else {
                        // 竖屏时使用原来的格式
                        courseInfo.append(shortenedName)
                                .append("\n\n")
                                .append(location)
                                .append("\n")
                                .append(teacher)
                                .append("\n");
                    }

                    int color = courseColors.get(displayCourse.getCourseName());
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setColor(color);
                    drawable.setCornerRadius(5);

                    dayViews[day].setBackground(drawable);
                    dayViews[day].setPadding(5, 5, 5, 5);
                    List<Course> finalAllCoursesInSlot = allCoursesInSlot;
                    int finalDay = day;
                    int finalTimeSlot = timeSlot;
                    dayViews[day].setOnClickListener(v -> showCourseInfoDialog(finalAllCoursesInSlot, finalDay + 1, finalTimeSlot));
                    hasCourse = true;

                    // 在bindTimeRow方法中，将原来的CompoundDrawable代码替换为：
                    // 如果有多个课程，显示角标
                    if (courseCount > 1) {
                        dayViews[day].setBadgeVisible(true);
                        // 可以根据课程颜色设置角标颜色
                        dayViews[day].setBadgeColor(0xFFFF5252); // 或者使用课程的主色调
                    } else {
                        dayViews[day].setBadgeVisible(false);
                    }
                }

                if (!hasCourse) {
                    dayViews[day].setBackgroundColor(Color.TRANSPARENT);
                    dayViews[day].setOnClickListener(null);
                    dayViews[day].setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    // 确保没有课程时隐藏角标
                    dayViews[day].setBadgeVisible(false);
                }

                dayViews[day].setText(courseInfo.toString().trim());
                dayViews[day].setTextSize(12);
            }
        }

        // 新增方法：获取课程的第一周
        private int getFirstWeek(String weekRange) {
            if (weekRange == null || weekRange.isEmpty()) return 1;
            try {
                String[] parts = weekRange.split("-");
                return Integer.parseInt(parts[0]);
            } catch (Exception e) {
                return 1;
            }
        }

        private void showCourseInfoDialog(List<Course> courses, int dayOfWeek, int timeSlot) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_main, null);

            TextView tvCourseName = dialogView.findViewById(R.id.tvCourseName);
            LinearLayout courseContainer = dialogView.findViewById(R.id.courseContainer);
            Button btnClose = dialogView.findViewById(R.id.btn_close); // 新增：获取关闭按钮

            // 设置标题
            String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            String[] timeNames = {"", "第一大节", "第二大节", "第三大节", "第四大节", "第五大节"};
            tvCourseName.setText(dayNames[dayOfWeek - 1] + " " + timeNames[timeSlot]);
        
            // 清空容器
            courseContainer.removeAllViews();
        
            // 为每个课程创建信息块
            for (int i = 0; i < courses.size(); i++) {
                Course course = courses.get(i);
                
                // 创建课程信息容器
                LinearLayout courseInfoLayout = new LinearLayout(context);
                courseInfoLayout.setOrientation(LinearLayout.VERTICAL);
                courseInfoLayout.setBackground(ContextCompat.getDrawable(context, R.drawable.edit_text_background));
                
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, 0, 0, (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics()));
                courseInfoLayout.setLayoutParams(layoutParams);
            
                // 课程名称
                TextView courseNameView = new TextView(context);
                courseNameView.setText(course.getCourseName());
                courseNameView.setTextSize(18);
                courseNameView.setTextColor(ContextCompat.getColor(context, R.color.dialog_title_color));
                courseNameView.setTypeface(null, Typeface.BOLD);
                courseNameView.setPadding(24, 20, 12, 4);
                courseInfoLayout.addView(courseNameView);
            
                // 地点 - 增加左边距
                TextView locationView = new TextView(context);
                locationView.setText("地点：" + course.getLocation());
                locationView.setTextSize(16);
                locationView.setTextColor(ContextCompat.getColor(context, R.color.info_text_color));
                locationView.setPadding(48, 4, 12, 4); // 左边距从12增加到24
                courseInfoLayout.addView(locationView);
            
                // 教师 - 增加左边距
                TextView teacherView = new TextView(context);
                teacherView.setText("教师：" + course.getTeacher());
                teacherView.setTextSize(16);
                teacherView.setTextColor(ContextCompat.getColor(context, R.color.info_text_color));
                teacherView.setPadding(48, 4, 12, 4); // 左边距从12增加到24
                courseInfoLayout.addView(teacherView);
            
                // 周次 - 增加左边距
                TextView timeView = new TextView(context);
                timeView.setText("周次：" + course.getclassWeek());
                timeView.setTextSize(16);
                timeView.setTextColor(ContextCompat.getColor(context, R.color.info_text_color));
                timeView.setPadding(48, 4, 12, 12); // 左边距从12增加到24
                courseInfoLayout.addView(timeView);
            
                courseContainer.addView(courseInfoLayout);
            }
        
            builder.setView(dialogView);
            AlertDialog dialog = builder.create();
            
            // 新增：设置关闭按钮点击事件和文本
            btnClose.setText("关闭");
            btnClose.setOnClickListener(v -> dialog.dismiss());
            
            dialog.show();
        }

        private String truncateString(String input, int frontLength, int backLength) {
            int visualLength = getVisualLength(input);
            if (visualLength > frontLength + backLength) {
                double visualCutLength = 0;
                int realCutIndex = 0;

                while (visualCutLength < frontLength && realCutIndex < input.length()) {
                    char c = input.charAt(realCutIndex);
                    visualCutLength += isEnglishLetter(c) ? 0.5 : 1;
                    realCutIndex++;
                }

                String firstPart = input.substring(0, realCutIndex);

                double reverseVisualCutLength = 0;
                int reverseRealCutIndex = input.length();

                while (reverseVisualCutLength < backLength && reverseRealCutIndex > 0) {
                    reverseRealCutIndex--;
                    char c = input.charAt(reverseRealCutIndex);
                    reverseVisualCutLength += isEnglishLetter(c) ? 0.5 : 1;
                }

                String lastPart = input.substring(reverseRealCutIndex);

                return firstPart + "..." + lastPart;
            }
            return input;
        }

        private int getVisualLength(String input) {
            int englishCount = 0;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (isEnglishLetter(c)) {
                    englishCount++;
                }
            }
            int otherCount = input.length() - englishCount;
            return otherCount + (englishCount + 1) / 2;
        }

        private boolean isEnglishLetter(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private void showWeekSwitchMenu(View view) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_week_selector, null);
            final AlertDialog dialog = builder.setView(dialogView).create();
        
            GridView weekGrid = dialogView.findViewById(R.id.weekGrid);
            Button btnReturnCurrentWeek = dialogView.findViewById(R.id.btnReturnCurrentWeek);
            Button btnAllWeeks = dialogView.findViewById(R.id.btnAllWeeks);
        
            final List<Integer> weeks = new ArrayList<>();
            for (int i = 1; i <= 24; i++) {
                weeks.add(i);
            }
        
            // 设置全部周次按钮的初始状态
            updateAllWeeksButtonState(btnAllWeeks);
        
            ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(context, R.layout.item_week_button, weeks) {
                @NonNull
                @Override
                public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = convertView;
                    if (view == null) {
                        view = LayoutInflater.from(context).inflate(R.layout.item_week_button, parent, false);
                    }
        
                    Button button = view.findViewById(R.id.weekButton);
                    int week = weeks.get(position);
                    button.setText("第" + week + "周");
        
                    if (week == currentWeek && !showAllWeeks) {
                        button.setBackgroundResource(R.drawable.tag_button_selected);
                        button.setTextColor(Color.WHITE);
                    } else {
                        button.setBackgroundResource(R.drawable.tag_button_normal);
                        button.setTextColor(ContextCompat.getColor(context, R.color.dialog_title_color));
                    }
        
                    button.setOnClickListener(v -> {
                        // 使用新的setCurrentWeek方法
                        setCurrentWeek(week);
                        dialog.dismiss();
                    });
                    return view;
                }
            };
        
            weekGrid.setAdapter(adapter);
        
            btnReturnCurrentWeek.setOnClickListener(v -> {
                // 获取当前周次并设置
                int currentWeekNum = CourseDataManager.getCurrentWeek(context);
                setCurrentWeek(currentWeekNum);
                dialog.dismiss();
            });
        
            btnAllWeeks.setOnClickListener(v -> {
                // 使用新的setShowAllWeeks方法
                setShowAllWeeks(true);
                dialog.dismiss();
            });
        
            dialog.show();
        }
        
        // 新增方法：更新全部周次按钮状态
        private void updateAllWeeksButtonState(Button btnAllWeeks) {
            if (showAllWeeks) {
                btnAllWeeks.setBackgroundResource(R.drawable.tag_button_selected);
                btnAllWeeks.setTextColor(Color.WHITE);
            } else {
                btnAllWeeks.setBackgroundResource(R.drawable.tag_button_normal);
                btnAllWeeks.setTextColor(ContextCompat.getColor(context, R.color.dialog_title_color));
            }
        }
    }
}