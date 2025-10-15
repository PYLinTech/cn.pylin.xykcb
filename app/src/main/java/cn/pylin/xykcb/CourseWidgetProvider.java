// CourseWidgetProvider.java
package cn.pylin.xykcb;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.util.Calendar;

public class CourseWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "cn.pylin.xykcb.ACTION_REFRESH";
    private static final String ACTION_LAST_DAY = "cn.pylin.xykcb.ACTION_LAST_DAY";
    private static final String ACTION_NEXT_DAY = "cn.pylin.xykcb.ACTION_NEXT_DAY";
    private static final String ACTION_DATE_CHANGED = "android.intent.action.DATE_CHANGED";
    private static final String ACTION_TIME_CHANGED = "android.intent.action.TIME_SET";
    private static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";
    private static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static int todayDay = getTodayDayOfWeek();
    static int currentDay = getTodayDayOfWeek();
    static int currentWeekOffset = 0; // 0表示当前周，-1表示上周，+1表示下周

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_course_layout);

        // 保存当前weekOffset到SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("CourseWidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("currentWeekOffset", currentWeekOffset).apply();

        // 获取当前周数和显示信息
        int currentWeek = CourseDataManager.getCurrentWeek(context);
        int displayWeek = currentWeek + currentWeekOffset;
        
        // 设置周次文本
        views.setTextViewText(R.id.widget_week_text, "第" + displayWeek + "周");
        
        // 设置日期文本
        Calendar calendar = Calendar.getInstance();
        int daysToAdd = currentDay - getTodayDayOfWeek() + (currentWeekOffset * 7);
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
        
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String dateStr = month + "." + day;
        String dayText = "周" + getChineseDay(currentDay) + "(" + dateStr + ")";
        
        if (currentDay == todayDay && currentWeekOffset == 0) {
            dayText = "今日(" + dateStr + ")";
        } else if (currentWeekOffset == 0) {
            dayText = "周" + getChineseDay(currentDay) + "(" + dateStr + ")";
        }
        
        views.setTextViewText(R.id.widget_date_text, dayText);

        android.util.Log.d("CourseWidget", "updateAppWidget: currentWeekOffset=" + currentWeekOffset + ", currentDay=" + currentDay);

        try {
            Intent gridIntent = new Intent(context, CourseWidgetService.class);
            gridIntent.putExtra("appWidgetId", appWidgetId);
            gridIntent.putExtra("weekOffset", currentWeekOffset);
            views.setRemoteAdapter(R.id.widget_list, gridIntent);

            // 周次文本点击事件 - 返回系统现在的日期
            Intent refreshIntent = new Intent(context, CourseWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                    context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            // 为日期容器设置点击事件
            views.setOnClickPendingIntent(R.id.widget_date_container, refreshPendingIntent);

            // 上一天按钮
            Intent lastDayIntent = new Intent(context, CourseWidgetProvider.class);
            lastDayIntent.setAction(ACTION_LAST_DAY);
            PendingIntent lastDayPendingIntent = PendingIntent.getBroadcast(
                    context, 0, lastDayIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_lastday, lastDayPendingIntent);

            // 下一天按钮
            Intent nextDayIntent = new Intent(context, CourseWidgetProvider.class);
            nextDayIntent.setAction(ACTION_NEXT_DAY);
            PendingIntent nextDayPendingIntent = PendingIntent.getBroadcast(
                    context, 0, nextDayIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_nextday, nextDayPendingIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String getChineseDay(int day) {
        String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        return days[(day - 1 + 7) % 7];
    }



    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_REFRESH.equals(intent.getAction())) {
            android.util.Log.d("CourseWidget", "onReceive: ACTION_REFRESH");
            handleRefreshAction(context);
        } else if (ACTION_NEXT_DAY.equals(intent.getAction())) {
            android.util.Log.d("CourseWidget", "onReceive: ACTION_NEXT_DAY, currentDay=" + currentDay);
            // 实现周日点击下一天到下一周周一
            if (currentDay == 7) { // 周日
                // 检查是否已经是第24周，不允许切换到第25周
                int currentWeek = CourseDataManager.getCurrentWeek(context);
                if (currentWeek + currentWeekOffset < 24) {
                    currentWeekOffset++;
                    currentDay = 1; // 切换到下一周周一
                    android.util.Log.d("CourseWidget", "切换到下一周周一，currentWeekOffset=" + currentWeekOffset);
                }
            } else {
                currentDay++;
            }
            updateDay(context, currentDay);
        } else if (ACTION_LAST_DAY.equals(intent.getAction())) {
            android.util.Log.d("CourseWidget", "onReceive: ACTION_LAST_DAY, currentDay=" + currentDay);
            // 实现周一点击上一天到上一周周日
            if (currentDay == 1) { // 周一
                // 检查是否已经是第1周，不允许切换到第0周
                int currentWeek = CourseDataManager.getCurrentWeek(context);
                if (currentWeek + currentWeekOffset > 1) {
                    currentWeekOffset--;
                    currentDay = 7; // 切换到上一周周日
                    android.util.Log.d("CourseWidget", "切换到上一周周日，currentWeekOffset=" + currentWeekOffset);
                }
            } else {
                currentDay--;
            }
            updateDay(context, currentDay);
        } else if (ACTION_DATE_CHANGED.equals(intent.getAction()) || 
                   ACTION_TIME_CHANGED.equals(intent.getAction()) || 
                   ACTION_TIMEZONE_CHANGED.equals(intent.getAction()) ||
                   ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            android.util.Log.d("CourseWidget", "onReceive: 系统日期/时间变更，自动刷新小组件");
            handleRefreshAction(context);
        }
    }

    private void handleRefreshAction(Context context) {
        // 重置到系统当前日期
        todayDay = getTodayDayOfWeek();
        currentDay = todayDay;
        currentWeekOffset = 0;
        updateDay(context, currentDay);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), CourseWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    private static int getTodayDayOfWeek() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        return (dayOfWeek + 5) % 7 + 1;
    }

    private static void updateDay(Context context, int day) {
        SharedPreferences loginPrefs = context.getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        loginPrefs.edit().putString("currentDay", String.valueOf(day)).apply();

        // 确保SharedPreferences中有当前的weekOffset
        SharedPreferences prefs = context.getSharedPreferences("CourseWidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("currentWeekOffset", currentWeekOffset).apply();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), CourseWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}