// CourseWidgetService.java
package cn.pylin.xykcb;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.List;

public class CourseWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CourseRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class CourseRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final int appWidgetId;
    private final int weekOffset;
    private List<List<Course>> weeklyCourses;
    private int currentWeek;

    public CourseRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra("appWidgetId", 0);
        this.weekOffset = intent.getIntExtra("weekOffset", 0);
        this.currentWeek = CourseDataManager.getCurrentWeek(context) + weekOffset;
        this.weeklyCourses = CourseDataManager.parseCourseData(context);
    }

    @Override
    public void onCreate() {
        // 更新数据源
        this.currentWeek = CourseDataManager.getCurrentWeek(context) + weekOffset;
        this.weeklyCourses = CourseDataManager.parseCourseData(context);
    }

    @Override
    public void onDataSetChanged() {
        // 更新数据源
        this.currentWeek = CourseDataManager.getCurrentWeek(context) + weekOffset;
        this.weeklyCourses = CourseDataManager.parseCourseData(context);
    }

    @Override
    public void onDestroy() {
        // 清理资源
        weeklyCourses = null;
    }

    @Override
    public int getCount() {
        return 5; // 假设一天有5节课
    }

    @Override
    public RemoteViews getViewAt(int position) {
        // 根据位置返回对应的 RemoteViews
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_course_item);

        // 获取当前显示的天和节次
        int timeSlot = position + 1;
        int day = CourseWidgetProvider.currentDay - 1;

        // 默认显示为空
        views.setTextViewText(R.id.widget_period_text, String.valueOf(position + 1));
        views.setTextViewText(R.id.widget_course_name, "");
        views.setTextViewText(R.id.widget_course_location, "");
        views.setTextViewText(R.id.widget_course_teacher, "");

        try {
            // 确保day在有效范围内
            if (day >= 0 && day < weeklyCourses.size()) {
                // 遍历当天的课程，找到匹配的课程信息
                for (Course course : weeklyCourses.get(day)) {
                    // 检查课程是否在当前显示的周和时间段内
                    if (course.isInWeek(currentWeek) && course.isInTimeSlot(day + 1, timeSlot)) {
                        String courseName = course.getCourseName();
                        String location = CourseDataManager.processLocation(course.getLocation());
                        String teacher = course.getTeacher();

                        views.setTextViewText(R.id.widget_course_name, courseName);
                        views.setTextViewText(R.id.widget_course_location, location);
                        views.setTextViewText(R.id.widget_course_teacher, teacher);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        // 返回加载中的视图
        return new RemoteViews(context.getPackageName(), R.layout.widget_course_item);
    }

    @Override
    public int getViewTypeCount() {
        // 返回视图类型的数量
        return 1;
    }

    @Override
    public long getItemId(int position) {
        // 返回每个视图的唯一 ID
        return position;
    }

    @Override
    public boolean hasStableIds() {
        // 是否有稳定的 ID
        return true;
    }
}