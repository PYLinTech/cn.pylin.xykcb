package cn.pylin.xykcb;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 自定义课程列表适配器
 * 用于在RecyclerView中显示自定义课程列表
 */
public class CustomCourseAdapter extends RecyclerView.Adapter<CustomCourseAdapter.CustomCourseViewHolder> {
    
    private Context context;
    private List<CustomCourse> customCourses;
    private OnItemClickListener onItemClickListener;
    
    public CustomCourseAdapter(Context context, List<CustomCourse> customCourses) {
        this.context = context;
        this.customCourses = customCourses;
    }
    
    public void setCustomCourses(List<CustomCourse> customCourses) {
        this.customCourses = customCourses;
        notifyDataSetChanged();
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
    
    @NonNull
    @Override
    public CustomCourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_custom_course, parent, false);
        return new CustomCourseViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CustomCourseViewHolder holder, int position) {
        CustomCourse course = customCourses.get(position);
        
        // 设置课程名称
        holder.tvCourseName.setText(course.getCourseName());
        
        // 设置时间信息
        String timeInfo = getTimeInfoString(course);
        holder.tvTimeInfo.setText(timeInfo);
        
        // 设置地点和教师信息
        String locationTeacher = getLocationTeacherString(course);
        holder.tvLocationTeacher.setText(locationTeacher);
        
        // 设置编辑按钮点击事件
        holder.btnEdit.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onEditClick(position);
            }
        });
        
        // 设置删除按钮点击事件
        holder.btnDelete.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onDeleteClick(position);
            }
        });
        
        // 设置整个项的点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return customCourses != null ? customCourses.size() : 0;
    }
    
    /**
     * 获取课程时间信息字符串
     */
    private String getTimeInfoString(CustomCourse course) {
        StringBuilder timeInfo = new StringBuilder();
        
        // 星期 - 支持多选显示
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<Integer> selectedWeeks = course.getWeekdays();
        if (selectedWeeks != null && !selectedWeeks.isEmpty()) {
            // 对星期进行排序
            selectedWeeks.sort(Integer::compareTo);
            for (int i = 0; i < selectedWeeks.size(); i++) {
                int week = selectedWeeks.get(i);
                if (week >= 1 && week <= 7) {
                    if (i > 0) timeInfo.append(",");
                    timeInfo.append(weekdays[week - 1]);
                }
            }
        }
        
        // 时间段 - 支持多选显示
        if (!course.getTimeSlots().isEmpty()) {
            timeInfo.append(" ");
            String[] timeNames = {"第一大节", "第二大节", "第三大节", "第四大节", "第五大节"};
            List<Integer> timeSlots = course.getTimeSlots();
            timeSlots.sort(Integer::compareTo);
            for (int i = 0; i < timeSlots.size(); i++) {
                int slot = timeSlots.get(i);
                if (slot >= 1 && slot <= 5) {
                    if (i > 0) timeInfo.append(",");
                    timeInfo.append(timeNames[slot - 1]);
                }
            }
        }
        
        return timeInfo.toString();
    }
    
    /**
     * 获取地点和教师信息字符串
     */
    private String getLocationTeacherString(CustomCourse course) {
        StringBuilder locationTeacher = new StringBuilder();
        
        if (course.getLocation() != null && !course.getLocation().isEmpty()) {
            locationTeacher.append(course.getLocation());
        }
        
        if (course.getTeacher() != null && !course.getTeacher().isEmpty()) {
            if (locationTeacher.length() > 0) {
                locationTeacher.append(" - ");
            }
            locationTeacher.append(course.getTeacher());
        }
        
        return locationTeacher.toString();
    }
    
    /**
     * 自定义课程视图持有者
     */
    public static class CustomCourseViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourseName;
        TextView tvTimeInfo;
        TextView tvLocationTeacher;
        ImageView btnEdit;
        ImageView btnDelete;
        
        public CustomCourseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourseName = itemView.findViewById(R.id.tv_course_name);
            tvTimeInfo = itemView.findViewById(R.id.tv_time_info);
            tvLocationTeacher = itemView.findViewById(R.id.tv_location_teacher);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
    
    /**
     * 项点击监听器接口
     */
    public interface OnItemClickListener {
        void onEditClick(int position);
        void onDeleteClick(int position);
        void onItemClick(int position);
    }
}