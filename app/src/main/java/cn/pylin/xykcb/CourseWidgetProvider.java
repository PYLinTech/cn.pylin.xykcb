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
    private static final long DOUBLE_CLICK_INTERVAL = 500; // 500毫秒内的双击检测
    
    private static int todayDay = getTodayDayOfWeek();
    static int currentDay = getTodayDayOfWeek();
    static int currentWeekOffset = 0; // 0表示当前周，-1表示上周，+1表示下周
    
    // 双击检测相关变量
    private static long lastClickTime = 0;
    private static int clickCount = 0;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 在小组件更新时检查是否需要发送通知
        CourseNotificationManager.checkAndSendNotification(context);
        
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
            //
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
            handleRefreshAction(context);
        } else if (ACTION_NEXT_DAY.equals(intent.getAction())) {
            // 实现周日点击下一天到下一周周一
            if (currentDay == 7) { // 周日
                // 检查是否已经是第24周，不允许切换到第25周
                int currentWeek = CourseDataManager.getCurrentWeek(context);
                if (currentWeek + currentWeekOffset < 24) {
                    currentWeekOffset++;
                    currentDay = 1; // 切换到下一周周一
                }
            } else {
                currentDay++;
            }
            updateDay(context, currentDay);
        } else if (ACTION_LAST_DAY.equals(intent.getAction())) {
            // 实现周一点击上一天到上一周周日
            if (currentDay == 1) { // 周一
                // 检查是否已经是第1周，不允许切换到第0周
                int currentWeek = CourseDataManager.getCurrentWeek(context);
                if (currentWeek + currentWeekOffset > 1) {
                    currentWeekOffset--;
                    currentDay = 7; // 切换到上一周周日
                }
            } else {
                currentDay--;
            }
            updateDay(context, currentDay);
        } else if (ACTION_DATE_CHANGED.equals(intent.getAction()) || 
                   ACTION_TIME_CHANGED.equals(intent.getAction()) || 
                   ACTION_TIMEZONE_CHANGED.equals(intent.getAction()) ||
                   ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            handleRefreshAction(context);
        }
    }

    private void handleRefreshAction(Context context) {
        // 检查是否已经切换到今天
        boolean isToday = (currentDay == todayDay && currentWeekOffset == 0);
        
        // 如果已经切换到今天，检查双击
        if (isToday) {
            long currentTime = System.currentTimeMillis();
            // 检查是否在1200毫秒内
            if (currentTime - lastClickTime < DOUBLE_CLICK_INTERVAL) {
                clickCount++;
                // 如果是第二次点击，启动主页面
                if (clickCount >= 2) {
                    // 重置计数器
                    clickCount = 0;
                    lastClickTime = 0;
                    
                    // 启动主页面，像点击应用图标那样启动，避免多个界面
                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(launchIntent);
                    }
                    return; // 不执行刷新操作
                }
            } else {
                // 超过1200毫秒，重置计数器
                clickCount = 1;
            }
            lastClickTime = currentTime;
        } else {
            // 未切换到今天，重置计数器
            clickCount = 0;
            lastClickTime = 0;
        }
        
        // 重置到系统当前日期
        todayDay = getTodayDayOfWeek();
        currentDay = todayDay;
        currentWeekOffset = 0;
        updateDay(context, currentDay);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), CourseWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        
        // 在刷新时检查是否需要发送通知
        CourseNotificationManager.checkAndSendNotification(context);
        
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
        
        // 先通知数据集已更改，确保Service获取最新的weekOffset
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}