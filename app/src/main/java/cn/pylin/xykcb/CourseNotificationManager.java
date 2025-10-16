package cn.pylin.xykcb;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.List;

public class CourseNotificationManager extends BroadcastReceiver {
    private static final String CHANNEL_ID = "course_reminder_channel";
    private static final String CHANNEL_NAME = "课程提醒";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_SEND_NOTIFICATION = "cn.pylin.xykcb.ACTION_SEND_NOTIFICATION";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SEND_NOTIFICATION.equals(intent.getAction())) {
            sendDailyNotification(context);
        }
    }
    
    /**
     * 初始化通知渠道
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("每日课程提醒通知");
            
            android.app.NotificationManager notificationManager = 
                    context.getSystemService(android.app.NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * 设置每日定时通知
     */
    public static void setDailyNotification(Context context, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, CourseNotificationManager.class);
        intent.setAction(ACTION_SEND_NOTIFICATION);
        
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上需要指定FLAG_IMMUTABLE或FLAG_MUTABLE
            pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
        
        // 设置指定时间触发
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        
        // 如果当前时间已经过了设置的时间，设置为明天的同一时间
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
            Log.d("CourseNotificationManager", "已设置每日" + hour + ":" + minute + "课程提醒");
        } catch (Exception e) {
            Log.e("CourseNotificationManager", "设置定时通知失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 设置每日22点的定时通知（兼容旧版本）
     */
    public static void setDailyNotification(Context context) {
        setDailyNotification(context, 22, 0);
    }
    
    /**
     * 取消定时通知
     */
    public static void cancelDailyNotification(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, CourseNotificationManager.class);
        intent.setAction(ACTION_SEND_NOTIFICATION);
        
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上需要指定FLAG_IMMUTABLE或FLAG_MUTABLE
            pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
        
        try {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d("CourseNotificationManager", "已取消每日课程提醒");
        } catch (Exception e) {
            Log.e("CourseNotificationManager", "取消定时通知失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发送每日通知
     */
    private static void sendDailyNotification(Context context) {
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (!notificationManager.areNotificationsEnabled()) {
                Log.d("CourseNotificationManager", "通知权限未授予，无法发送通知");
                return;
            }
        }
        
        // 获取明天的课程
        List<Course> tomorrowCourses = getTomorrowCourses(context);
        
        if (tomorrowCourses.isEmpty()) {
            Log.d("CourseNotificationManager", "明天没有课程，不发送通知");
            return;
        }
        
        // 确保通知渠道已创建
        createNotificationChannel(context);
        
        // 构建通知内容
        StringBuilder contentBuilder = new StringBuilder();
        for (Course course : tomorrowCourses) {
            if (contentBuilder.length() > 0) {
                contentBuilder.append("、");
            }
            contentBuilder.append(course.getCourseName());
        }
        
        String content = contentBuilder.toString();
        
        // 创建点击通知后跳转到主界面的意图
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上需要指定FLAG_IMMUTABLE或FLAG_MUTABLE
            pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        
        // 创建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle("明天的课程")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // 设置点击意图
                .setAutoCancel(true);
        
        // 发送通知
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d("CourseNotificationManager", "已发送明日课程通知: " + content);
        } catch (Exception e) {
            Log.e("CourseNotificationManager", "发送通知失败: " + e.getMessage(), e);
        }
        
        // 设置下一次通知
        setDailyNotification(context);
    }
    
    /**
     * 获取明天的课程
     */
    private static List<Course> getTomorrowCourses(Context context) {
        // 获取明天的星期几
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        int tomorrowDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        
        // 转换为1-7的格式（周一=1, 周日=7）
        int tomorrowDay = (tomorrowDayOfWeek + 5) % 7 + 1;
        
        // 获取当前周次
        int currentWeek = CourseDataManager.getCurrentWeek(context);
        
        // 获取所有课程数据
        List<List<Course>> weeklyCourses = CourseDataManager.parseCourseData(context);
        
        // 获取明天的课程列表
        if (tomorrowDay >= 1 && tomorrowDay <= weeklyCourses.size()) {
            List<Course> dayCourses = weeklyCourses.get(tomorrowDay - 1);
            
            // 过滤出当前周的课程
            List<Course> result = new java.util.ArrayList<>();
            for (Course course : dayCourses) {
                if (course.isInWeek(currentWeek)) {
                    result.add(course);
                }
            }
            
            return result;
        }
        
        return new java.util.ArrayList<>();
    }
    
    /**
     * 检查通知权限是否启用（Android 12及以下版本）
     */
    public static boolean areNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+使用标准的权限检查
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return notificationManager.areNotificationsEnabled();
        } else {
            // Android 12及以下版本，检查通知渠道是否启用
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 检查通知渠道
                NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
                if (channel != null) {
                    return channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
                }
            }
            
            // 对于Android 7.1及以下版本，默认返回true（这些版本没有细粒度的通知控制）
            return true;
        }
    }
    
    /**
     * 检查通知功能是否启用
     */
    public static boolean isNotificationEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        return prefs.getBoolean("daily_course_reminder", false);
    }
    
    /**
     * 设置通知功能状态
     */
    public static void setNotificationEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("daily_course_reminder", enabled).apply();
        
        if (enabled) {
            createNotificationChannel(context);
            
            // 获取用户设置的时间
            int hour = prefs.getInt("notification_hour", 22);
            int minute = prefs.getInt("notification_minute", 0);
            setDailyNotification(context, hour, minute);
            
            Log.d("CourseNotificationManager", "已启用每日课程提醒，时间：" + hour + ":" + minute);
        } else {
            cancelDailyNotification(context);
            Log.d("CourseNotificationManager", "已禁用每日课程提醒");
        }
    }
}