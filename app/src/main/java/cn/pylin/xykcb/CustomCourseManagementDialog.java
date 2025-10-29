package cn.pylin.xykcb;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomCourseManagementDialog {
    
    private Context context;
    private AlertDialog dialog;
    private CustomCourseManager customCourseManager;
    private List<CustomCourse> customCourses;
    private CustomCourseAdapter customCourseAdapter;
    private RecyclerView recyclerView;
    private TextView tvNoCourses;
    
    // 添加课程完成回调接口
    public interface OnCourseAddedListener {
        void onCourseAdded();
    }
    
    private OnCourseAddedListener onCourseAddedListener;
    
    // 设置回调监听器
    public void setOnCourseAddedListener(OnCourseAddedListener listener) {
        this.onCourseAddedListener = listener;
    }
    
    public CustomCourseManagementDialog(Context context) {
        this.context = context;
        this.customCourseManager = new CustomCourseManager(context);
    }
    
    /**
     * 显示管理自定义课程对话框
     */
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_manage_custom_courses, null);
        
        // 获取控件
        recyclerView = dialogView.findViewById(R.id.recycler_custom_courses);
        tvNoCourses = dialogView.findViewById(R.id.tv_no_courses);
        Button btnAddCourse = dialogView.findViewById(R.id.btn_add_course);
        
        // 设置RecyclerView
        setupRecyclerView();
        
        // 加载自定义课程列表
        loadCustomCoursesList();
        
        // 设置按钮点击事件
        btnAddCourse.setOnClickListener(v -> {
            showAddCustomCourseDialog();
        });
        
        builder.setView(dialogView);
        dialog = builder.create();
        dialog.show();
    }
    
    /**
     * 设置RecyclerView
     */
    private void setupRecyclerView() {
        // 设置布局管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        
        // 添加分割线装饰器，模仿桌面小组件课程列表的分割线样式
        recyclerView.addItemDecoration(new CustomCourseDividerItemDecoration());
        
        // 创建适配器
        customCourseAdapter = new CustomCourseAdapter(context, new ArrayList<>());
        recyclerView.setAdapter(customCourseAdapter);
        
        // 设置项点击监听器
        customCourseAdapter.setOnItemClickListener(new CustomCourseAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(int position) {
                showEditCustomCourseDialog(position);
            }
            
            @Override
            public void onDeleteClick(int position) {
                showDeleteConfirmDialog(position);
            }
            
            @Override
            public void onItemClick(int position) {
                // 项点击事件，可以根据需要实现
            }
        });
    }
    
    /**
     * 加载自定义课程列表
     */
    private void loadCustomCoursesList() {
        customCourses = customCourseManager.getAllCustomCourses();
        
        if (customCourses.isEmpty()) {
            tvNoCourses.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvNoCourses.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            customCourseAdapter.setCustomCourses(customCourses);
        }
    }
    
    /**
     * 刷新自定义课程列表
     */
    private void refreshCustomCoursesList() {
        loadCustomCoursesList();
        
        // 通知课程添加监听器
        if (onCourseAddedListener != null) {
            onCourseAddedListener.onCourseAdded();
        }
    }
    void showAddCustomCourseDialog() {
        // 使用修改后的编辑课程对话框，传入-1表示添加模式
        showEditCustomCourseDialog(-1);
    }
    
    /**
     * 添加自定义课程
     */
    private boolean addCustomCourse(String courseName, String location, String teacher, 
                                   List<Integer> selectedWeeks, List<Integer> selectedTimes, String weeks) {
        CustomCourse course = new CustomCourse();
        course.setCourseName(courseName);
        course.setLocation(location);
        course.setTeacher(teacher);
        course.setWeekdays(selectedWeeks);
        course.setTimeSlots(selectedTimes);
        
        // 设置周次范围
        List<Integer> weekList = new ArrayList<>();
        if (!weeks.isEmpty()) {
            // 解析周次范围，例如"1,3,5-8,10,12-15"
            String[] parts = weeks.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    // 范围格式，如"5-8"
                    String[] range = part.split("-");
                    if (range.length == 2) {
                        try {
                            int startWeek = Integer.parseInt(range[0].trim());
                            int endWeek = Integer.parseInt(range[1].trim());
                            for (int week = startWeek; week <= endWeek; week++) {
                                weekList.add(week);
                            }
                        } catch (NumberFormatException e) {
                            // 如果解析失败，使用默认周次
                            for (int week = 1; week <= 16; week++) {
                                weekList.add(week);
                            }
                        }
                    }
                } else {
                    // 单个周次，如"1"或"3"
                    try {
                        int week = Integer.parseInt(part);
                        weekList.add(week);
                    } catch (NumberFormatException e) {
                        // 忽略无效的周次
                    }
                }
            }
        } else {
            // 如果没有指定周次，默认显示所有周（1-24周）
            for (int week = 1; week <= 24; week++) {
                weekList.add(week);
            }
        }
        course.setWeeks(weekList);
        
        // 保存课程
        boolean success = customCourseManager.addCustomCourse(course);
        return success;
    }
    
    /**
     * 显示编辑自定义课程对话框
     * @param index 课程索引，-1表示添加新课程
     */
    private void showEditCustomCourseDialog(int index) {
        boolean isAddMode = index == -1;
        
        // 如果是编辑模式，检查索引有效性
        if (!isAddMode && (index < 0 || index >= customCourses.size())) {
            return;
        }
        
        CustomCourse course = isAddMode ? new CustomCourse() : customCourses.get(index);
        
        // 创建编辑对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_custom_course, null);
        
        // 获取布局中的控件
        EditText etCourseName = view.findViewById(R.id.et_course_name);
        EditText etLocation = view.findViewById(R.id.et_location);
        EditText etTeacher = view.findViewById(R.id.et_teacher);
        EditText etWeeks = view.findViewById(R.id.et_weeks);
        
        // 填充现有数据
        etCourseName.setText(course.getCourseName());
        etLocation.setText(course.getLocation());
        etTeacher.setText(course.getTeacher());
        
        // 设置周次
        StringBuilder weeksBuilder = new StringBuilder();
        List<Integer> weekList = course.getWeeks();
        if (weekList != null && !weekList.isEmpty()) {
            // 将周次列表转换为字符串格式
            Collections.sort(weekList);
            for (int i = 0; i < weekList.size(); i++) {
                if (i > 0) weeksBuilder.append(",");
                weeksBuilder.append(weekList.get(i));
            }
        }
        etWeeks.setText(weeksBuilder.toString());
        
        // 星期按钮数组
        Button[] weekButtons = {
            view.findViewById(R.id.btn_monday),
            view.findViewById(R.id.btn_tuesday),
            view.findViewById(R.id.btn_wednesday),
            view.findViewById(R.id.btn_thursday),
            view.findViewById(R.id.btn_friday),
            view.findViewById(R.id.btn_saturday),
            view.findViewById(R.id.btn_sunday)
        };
        
        // 时间段按钮数组
        Button[] timeButtons = {
            view.findViewById(R.id.btn_time_1),
            view.findViewById(R.id.btn_time_2),
            view.findViewById(R.id.btn_time_3),
            view.findViewById(R.id.btn_time_4),
            view.findViewById(R.id.btn_time_5)
        };
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);
        
        AlertDialog editDialog = builder.setView(view).create();
        
        // 初始化选中状态 - 改为支持多选
        final List<Integer> selectedWeeks = new ArrayList<>(); // 选中的星期列表
        final List<Integer> selectedTimes = new ArrayList<>(); // 选中的时间段列表
        
        // 设置星期按钮初始状态
        List<Integer> courseWeeks = course.getWeekdays();
        if (courseWeeks != null && !courseWeeks.isEmpty()) {
            selectedWeeks.addAll(courseWeeks);
            for (int week : courseWeeks) {
                int weekIndex = week - 1; // 转换为0-6的索引
                if (weekIndex >= 0 && weekIndex < weekButtons.length) {
                    weekButtons[weekIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_selected));
                    weekButtons[weekIndex].setTextColor(Color.WHITE);
                }
            }
        }
        
        // 设置时间段按钮初始状态
        List<Integer> courseTimeSlots = course.getTimeSlots();
        if (courseTimeSlots != null && !courseTimeSlots.isEmpty()) {
            selectedTimes.addAll(courseTimeSlots);
            for (int timeSlot : courseTimeSlots) {
                int timeIndex = timeSlot - 1; // 转换为0-4的索引
                if (timeIndex >= 0 && timeIndex < timeButtons.length) {
                    timeButtons[timeIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_selected));
                    timeButtons[timeIndex].setTextColor(Color.WHITE);
                }
            }
        }
        
        // 设置星期按钮点击事件 - 改为支持多选
        for (int i = 0; i < weekButtons.length; i++) {
            final int weekIndex = i;
            final int weekValue = i + 1; // 转换为1-7的星期值
            weekButtons[i].setOnClickListener(v -> {
                // 切换选中状态
                if (selectedWeeks.contains(weekValue)) {
                    // 取消选中
                    weekButtons[weekIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_normal));
                    weekButtons[weekIndex].setTextColor(context.getResources().getColor(R.color.dialog_title_color));
                    selectedWeeks.remove(Integer.valueOf(weekValue));
                } else {
                    // 选中当前
                    weekButtons[weekIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_selected));
                    weekButtons[weekIndex].setTextColor(Color.WHITE);
                    selectedWeeks.add(weekValue);
                }
            });
        }
        
        // 设置时间段按钮点击事件 - 改为支持多选
        for (int i = 0; i < timeButtons.length; i++) {
            final int timeIndex = i;
            final int timeValue = i + 1; // 转换为1-5的时间段值
            timeButtons[i].setOnClickListener(v -> {
                // 切换选中状态
                if (selectedTimes.contains(timeValue)) {
                    // 取消选中
                    timeButtons[timeIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_normal));
                    timeButtons[timeIndex].setTextColor(context.getResources().getColor(R.color.dialog_title_color));
                    selectedTimes.remove(Integer.valueOf(timeValue));
                } else {
                    // 选中当前
                    timeButtons[timeIndex].setBackground(context.getResources().getDrawable(R.drawable.tag_button_selected));
                    timeButtons[timeIndex].setTextColor(Color.WHITE);
                    selectedTimes.add(timeValue);
                }
            });
        }
        
        // 取消按钮点击事件
        btnCancel.setOnClickListener(v -> editDialog.dismiss());
        
        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> {
            // 验证必填字段
            String courseName = etCourseName.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String teacher = etTeacher.getText().toString().trim();
            String weeks = etWeeks.getText().toString().trim();
            
            if (courseName.isEmpty()) {
                CustomToast.showShortToast(context, "请输入课程名称");
                return;
            }
            
            if (selectedWeeks.isEmpty()) {
                CustomToast.showShortToast(context, "请选择星期");
                return;
            }
            
            if (selectedTimes.isEmpty()) {
                CustomToast.showShortToast(context, "请选择时间段");
                return;
            }
            
            boolean success;
            if (isAddMode) {
                // 添加新课程
                success = addCustomCourse(courseName, location, teacher, selectedWeeks, selectedTimes, weeks);
            } else {
                // 更新现有课程
                success = updateCustomCourse(index, courseName, location, teacher, selectedWeeks, selectedTimes, weeks);
            }
            
            if (success) {
                CustomToast.showShortToast(context, isAddMode ? "课程添加成功" : "课程更新成功");
                editDialog.dismiss();
                // 发送广播通知课程表刷新
                if (context instanceof MainActivity) {
                    Intent intent = new Intent("CUSTOM_COURSE_UPDATED");
                    // 为Android 15+添加包名信息，确保广播能被正确接收
                    intent.setPackage(context.getPackageName());
                    context.sendBroadcast(intent);
                }
                // 刷新课程列表
                refreshCustomCoursesList();
            } else {
                CustomToast.showShortToast(context, isAddMode ? "课程添加失败" : "课程更新失败");
            }
        });
        
        editDialog.show();
    }
    
    /**
     * 更新自定义课程数据
     */
    private boolean updateCustomCourse(int index, String courseName, String location, String teacher, 
                                      List<Integer> selectedWeeks, List<Integer> selectedTimes, String weeks) {
        if (index < 0 || index >= customCourses.size()) {
            return false;
        }
        
        CustomCourse course = customCourses.get(index);
        
        // 更新课程信息
        course.setCourseName(courseName);
        course.setLocation(location);
        course.setTeacher(teacher);
        course.setWeekdays(selectedWeeks); // 设置多选星期
        course.setTimeSlots(selectedTimes); // 设置多选时间段
        
        // 设置周次范围
        List<Integer> weekList = new ArrayList<>();
        if (!weeks.isEmpty()) {
            // 解析周次范围，例如"1,3,5-8,10,12-15"
            String[] parts = weeks.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    // 范围格式，如"5-8"
                    String[] range = part.split("-");
                    if (range.length == 2) {
                        try {
                            int startWeek = Integer.parseInt(range[0].trim());
                            int endWeek = Integer.parseInt(range[1].trim());
                            for (int week = startWeek; week <= endWeek; week++) {
                                weekList.add(week);
                            }
                        } catch (NumberFormatException e) {
                            // 如果解析失败，使用默认周次
                            for (int week = 1; week <= 16; week++) {
                                weekList.add(week);
                            }
                        }
                    }
                } else {
                    // 单个周次，如"1"或"3"
                    try {
                        int week = Integer.parseInt(part);
                        weekList.add(week);
                    } catch (NumberFormatException e) {
                        // 忽略无效的周次
                    }
                }
            }
        } else {
            // 如果没有指定周次，默认显示所有周（1-24周）
            for (int week = 1; week <= 24; week++) {
                weekList.add(week);
            }
        }
        course.setWeeks(weekList);
        
        // 使用CustomCourseManager更新课程
        boolean success = customCourseManager.updateCustomCourse(index, course);
        
        if (success) {
            // 发送广播通知课程表刷新
            Intent intent = new Intent("CUSTOM_COURSE_UPDATED");
            // 为Android 15+添加包名信息，确保广播能被正确接收
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        }
        
        return success;
    }
    
    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(int index) {
        if (index < 0 || index >= customCourses.size()) {
            return;
        }
        
        CustomCourse course = customCourses.get(index);
        
        // 创建自定义对话框布局
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_custom_course, null);
        
        // 获取布局中的控件
        TextView tvCourseName = dialogView.findViewById(R.id.tv_course_name);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        // 设置课程名称
        tvCourseName.setText(course.getCourseName());
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setView(dialogView);
        
        AlertDialog deleteDialog = builder.create();
        
        // 设置按钮点击事件
        btnDelete.setOnClickListener(v -> {
            boolean success = customCourseManager.deleteCustomCourse(index);
            if (success) {
                CustomToast.showShortToast(context, "课程删除成功");
                // 发送广播通知课程表刷新
                if (context instanceof MainActivity) {
                    Intent intent = new Intent("CUSTOM_COURSE_UPDATED");
                    // 为Android 15+添加包名信息，确保广播能被正确接收
                    intent.setPackage(context.getPackageName());
                    context.sendBroadcast(intent);
                }
                // 刷新课程列表
                refreshCustomCoursesList();
            } else {
                CustomToast.showShortToast(context, "课程删除失败");
            }
            deleteDialog.dismiss();
        });
        
        btnCancel.setOnClickListener(v -> {
            deleteDialog.dismiss();
        });
        
        deleteDialog.show();
    }
}