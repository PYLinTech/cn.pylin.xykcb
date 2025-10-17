package cn.pylin.xykcb;

import android.Manifest;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    static String Week;
    private long lastBackPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000;
    public static RecyclerView recyclerView;
    private CourseAdapter adapter;
    private LoginManager loginManager;
    private UpdateManager updateManager;
    private boolean hasLoadedNetworkData = false; // 标记是否已加载网络数据
    private Switch switchDailyCourseReminder; // 添加为成员变量，解决作用域问题
    
    /**
     * 递归删除目录及其所有内容
     * @param directory 要删除的目录
     */
    private void deleteDirectory(File directory) {
        if (directory != null && directory.exists()) {
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteDirectory(file);
                    }
                }
            }
            directory.delete();
        }
    }

    @Override
    public Resources getResources() {
        Resources res = super.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.fontScale = 1.0f;
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressedTime < BACK_PRESS_INTERVAL) {
            super.onBackPressed();
        } else {
            CustomToast.showShortToast(this, "再按一次返回将退出");
            lastBackPressedTime = currentTime;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 先初始化所有View组件
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarTitle.setOnClickListener(v -> showToolbarTitleDialog());
        
        initNoteEditText();
        
        // 初始化更新管理器
        updateManager = new UpdateManager(this, recyclerView);

        // 然后初始化登录管理器
        loginManager = new LoginManager(this, new LoginManager.CourseDataCallback() {
            @Override
            public void onCourseDataReceived(List<List<Course>> weeklyCourses) {
                runOnUiThread(() -> {
                    try {
                        String[] weekHeaders = new String[] { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
                        // 使用基于日期判断的周次，确保本地数据也能显示正确周次
                        Week = String.valueOf(CourseDataManager.getCurrentWeek(MainActivity.this));

                        if (adapter == null) {
                            // 首次创建adapter
                            adapter = new CourseAdapter(MainActivity.this, weeklyCourses, weekHeaders);
                            recyclerView.setAdapter(adapter);
                            
                            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    int recyclerViewHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() - recyclerView.getPaddingBottom();
                                    if (adapter != null) {
                                        adapter.setRecyclerViewHeight(recyclerViewHeight);
                                    }
                                }
                            });
                        } else {
                            // 复用现有adapter，更新数据
                            adapter.updateCourses(weeklyCourses);
                        }
                        
                        // 获取并设置当前周次
                        int currentWeekNum = CourseDataManager.getCurrentWeek(MainActivity.this);
                        android.util.Log.d("MainActivity", "通过日期判断当前周次为: " + currentWeekNum);
                        adapter.setCurrentWeek(currentWeekNum);
                        
                        // 标记已加载网络数据
                        hasLoadedNetworkData = true;
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "加载课程表失败", e);
                        CustomToast.showShortToast(MainActivity.this, "加载课程表失败");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    // 如果消息包含"当前查看的是本地数据"，说明已经加载了本地数据，不需要显示登录对话框
                    if (message.contains("当前查看的是本地数据")) {
                        CustomToast.showLongToast(MainActivity.this, message);
                    } else {
                        CustomToast.showShortToast(MainActivity.this, message);
                        
                        // 如果错误消息包含"登录失败"或"登录过期"，重新拉起登录窗口
                        if (message.contains("登录失败") || message.contains("暂不支持")) {
                            showLoginDialog();
                        }
                    }
                });
            }
        });

        initNoteEditText();
        checkLoginStatus();
    }

    private void initNoteEditText() {
        EditText noteEditText = findViewById(R.id.toolbar_edit);
        TextView appTitle = findViewById(R.id.toolbar_app_title);
        
        // 检查显示设置 - 修改默认值为false（默认不显示）
        SharedPreferences settingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        boolean noteVisible = settingsPrefs.getBoolean("note_visible", false); // 改为false
        updateNoteEditTextVisibility(noteVisible);
        
        SharedPreferences sharedPreferences = getSharedPreferences("NoteInfo", Context.MODE_PRIVATE);
        String savedNote = sharedPreferences.getString("note", "");
        noteEditText.setText(savedNote);

        noteEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                sharedPreferences.edit().putString("note", s.toString()).apply();
            }
        });

        noteEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(noteEditText.getWindowToken(), 0);
                return true;
            }
            return false;
        });
    }

    private void checkLoginStatus() {
        SharedPreferences sharedPreferences = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String savedPassword = sharedPreferences.getString("password", "");
        String savedSchoolCode = sharedPreferences.getString("schoolCode", "HNIT-A");

        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
            loginManager.performLogin(savedUsername, savedPassword, savedSchoolCode);
        } else {
            showLoginDialog();
        }
    }

    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_login, null);

        final TextView tvSchoolSelector = view.findViewById(R.id.tv_school_selector);
        final EditText etUsername = view.findViewById(R.id.et_username);
        final EditText etPassword = view.findViewById(R.id.et_password);
        final Button btnLogin = view.findViewById(R.id.btn_login);
        TextView tvForgetPassword = view.findViewById(R.id.tv_forget_password);

        // 学校选择相关变量
        final String[] selectedSchool = {null}; // 存储选中的学校代码
        final String[] selectedSchoolName = {null}; // 存储选中的学校名称

        SharedPreferences sharedPreferences = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String savedPassword = sharedPreferences.getString("password", "");
        String savedSchoolCode = sharedPreferences.getString("schoolCode", "");
        String savedSchoolName = sharedPreferences.getString("schoolName", "");
        
        etUsername.setText(savedUsername);
        etPassword.setText(savedPassword);
        
        // 如果有保存的学校信息，显示在按钮上
        if (!savedSchoolName.isEmpty()) {
            tvSchoolSelector.setText(savedSchoolName);
            tvSchoolSelector.setTextColor(getResources().getColor(R.color.edit_text_color));
            selectedSchool[0] = savedSchoolCode;
            selectedSchoolName[0] = savedSchoolName;
        }
        final AlertDialog dialog = builder.setView(view).create();

        // 学校选择区域点击事件（整个RelativeLayout都可点击）
        view.findViewById(R.id.tv_school_selector).setOnClickListener(v -> {
            showSchoolSelectorDialog(tvSchoolSelector, selectedSchool, selectedSchoolName);
        });

        // 密码显示/隐藏切换 - 默认加密显示
        final boolean[] isPasswordVisible = {false}; // 移到外部，默认false表示加密显示
        // 设置初始状态为加密显示
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Switch switchPasswordToggle = view.findViewById(R.id.switch_password_toggle);
        switchPasswordToggle.setChecked(false); // 默认不选中表示加密显示
        
        switchPasswordToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 切换为显示
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                isPasswordVisible[0] = true;
            } else {
                // 切换为隐藏
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                isPasswordVisible[0] = false;
            }
            // 保持光标位置
            etPassword.setSelection(etPassword.getText().length());
        });

        tvForgetPassword.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pylin.cn/gxjwczmmdh"));
            startActivity(intent);
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etPassword.getWindowToken(), 0);
                handleLoginAction(etUsername, etPassword, selectedSchool[0], selectedSchoolName[0], dialog);
                return true;
            }
            return false;
        });

        btnLogin.setOnClickListener(v -> handleLoginAction(etUsername, etPassword, selectedSchool[0], selectedSchoolName[0], dialog));
        dialog.show();
    }

    // 新增方法：显示学校选择弹窗
    private void showSchoolSelectorDialog(TextView tvSchoolSelector, String[] selectedSchool, String[] selectedSchoolName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_school_selector, null);
        
        LinearLayout schoolListContainer = view.findViewById(R.id.school_list_container);
        AlertDialog schoolDialog = builder.setView(view).create();
        
        // 从LoginManager的LoginType枚举中获取学校列表，并按id排序
        List<LoginManager.LoginType> sortedLoginTypes = new ArrayList<>(Arrays.asList(LoginManager.LoginType.values()));
        sortedLoginTypes.sort(Comparator.comparingInt(LoginManager.LoginType::getId));
        
        for (LoginManager.LoginType loginType : sortedLoginTypes) {

            // 创建学校选项的TextView
            TextView schoolTextView = new TextView(this);
            // 设置样式，与原XML中的样式保持一致
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.school_item_margin);
            schoolTextView.setLayoutParams(params);
            schoolTextView.setText(loginType.getName());
            schoolTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            schoolTextView.setTextColor(getResources().getColor(R.color.edit_text_color));
            int padding = getResources().getDimensionPixelSize(R.dimen.school_item_padding);
            schoolTextView.setPadding(padding, padding, padding, padding);
            schoolTextView.setBackground(getResources().getDrawable(R.drawable.edit_text_background));
            schoolTextView.setClickable(true);
            schoolTextView.setFocusable(true);
            
            // 设置点击事件
            final String schoolCode = loginType.getCode();
            final String schoolName = loginType.getName();
            schoolTextView.setOnClickListener(v -> {
                selectedSchool[0] = schoolCode;
                selectedSchoolName[0] = schoolName;
                tvSchoolSelector.setText(schoolName);
                tvSchoolSelector.setTextColor(getResources().getColor(R.color.edit_text_color));
                schoolDialog.dismiss();
            });
            
            // 添加到容器中
            schoolListContainer.addView(schoolTextView);
        }
        
        schoolDialog.show();
    }

    // 修改登录处理方法
    private void handleLoginAction(EditText etUsername, EditText etPassword, String schoolCode, String schoolName, AlertDialog dialog) {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (schoolCode == null || schoolCode.isEmpty()) {
            CustomToast.showShortToast(MainActivity.this, "请选择学校");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            CustomToast.showShortToast(MainActivity.this, "用户名或密码不能为空");
            return;
        }

        // 保存学校信息
        SharedPreferences sharedPreferences = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putString("schoolCode", schoolCode)
                .putString("schoolName", schoolName)
                .apply();

        // 重置网络数据加载标志
        hasLoadedNetworkData = false;
        
        // 使用统一的登录管理器执行登录
        loginManager.performLogin(username, password, schoolCode);
        dialog.dismiss();
    }

    private void showToolbarTitleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_toolbar_title, null);
    
        // 获取标签按钮
        Button btnTabMine = dialogView.findViewById(R.id.btn_tab_mine);
        Button btnTabSettings = dialogView.findViewById(R.id.btn_tab_settings);
        Button btnTabAbout = dialogView.findViewById(R.id.btn_tab_about);
    
        // 获取内容区域
        LinearLayout layoutMine = dialogView.findViewById(R.id.layout_mine);
        LinearLayout layoutSettings = dialogView.findViewById(R.id.layout_settings);
        LinearLayout layoutAbout = dialogView.findViewById(R.id.layout_about);
    
        // 获取各页面的控件
        TextView tvDescription = dialogView.findViewById(R.id.tvDescription);
        TextView tvVersionInfo = dialogView.findViewById(R.id.tvVersionInfo);
        TextView tvICPInfo = dialogView.findViewById(R.id.tvICPInfo);
        Button btnReLogin = dialogView.findViewById(R.id.btn_reLogin);
        Button btnPYLinTech = dialogView.findViewById(R.id.btn_PYLinTech);
        Button btnCheckUpdate = dialogView.findViewById(R.id.btn_check_update);
        TextView btnShowWelcome = dialogView.findViewById(R.id.btn_show_welcome);
        TextView btnJoinQQGroup = dialogView.findViewById(R.id.btn_join_qq_group);
        
        // 获取用户信息控件
        TextView tvUserName = dialogView.findViewById(R.id.tv_user_name);
        TextView tvAcademyName = dialogView.findViewById(R.id.tv_academy_name);
        TextView tvClassName = dialogView.findViewById(R.id.tv_class_name);
        LinearLayout layoutUserInfo = (LinearLayout) tvUserName.getParent();
        
        // 获取设置页面的切换按钮（替换原来的TextView）
        Switch switchNoteVisibilityToggle = dialogView.findViewById(R.id.switch_note_visibility_toggle);
        Switch switchDailyCourseReminder = dialogView.findViewById(R.id.switch_daily_course_reminder);
        
        // 初始化切换按钮状态
        SharedPreferences settingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        boolean noteVisible = settingsPrefs.getBoolean("note_visible", false);
        boolean dailyReminderEnabled = settingsPrefs.getBoolean("daily_course_reminder", false);
        
        // 设置初始开关状态
        switchNoteVisibilityToggle.setChecked(noteVisible);
        switchDailyCourseReminder.setChecked(dailyReminderEnabled);
    
        // 设置状态改变监听器
        switchNoteVisibilityToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存设置
            settingsPrefs.edit().putBoolean("note_visible", isChecked).apply();
            
            // 立即更新备注栏显示状态
            updateNoteEditTextVisibility(isChecked);
        });
        
        // 设置每日课程提醒开关监听器
        switchDailyCourseReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存设置
            settingsPrefs.edit().putBoolean("daily_course_reminder", isChecked).apply();
            
            // 启用或禁用每日课程提醒
            if (isChecked) {
                // 检查通知权限（Android 13+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, 
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        // 请求通知权限
                        ActivityCompat.requestPermissions(MainActivity.this, 
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                                1001);
                        // 暂时保存用户的选择，但先不设置通知，等权限请求结果后再处理
                        return;
                    }
                }
                
                cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(MainActivity.this, true);
                CustomToast.showShortToast(MainActivity.this, "已开启每日课程提醒");
            } else {
                cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(MainActivity.this, false);
                CustomToast.showShortToast(MainActivity.this, "已关闭每日课程提醒");
            }
            
            // 显示或隐藏时间选择器
            LinearLayout layoutNotificationTime = dialogView.findViewById(R.id.layout_notification_time);
            if (layoutNotificationTime != null) {
                layoutNotificationTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        
        // 获取撤销同意用户协议及隐私政策开关
        Switch switchPrivacyPolicyConsent = dialogView.findViewById(R.id.switch_privacy_policy_consent);
        
        // 设置撤销同意用户协议及隐私政策开关监听器
        switchPrivacyPolicyConsent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                // 用户撤销同意，显示自定义确认对话框
                AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(MainActivity.this, R.style.DialogTheme);
                View confirmView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_main, null);
                
                // 设置标题和内容
                TextView tvTitle = confirmView.findViewById(R.id.tvCourseName);
                tvTitle.setText("确认撤销？");
                
                // 获取内容容器并添加消息
                LinearLayout contentContainer = confirmView.findViewById(R.id.courseContainer);
                TextView tvMessage = new TextView(MainActivity.this);
                tvMessage.setText("撤销同意将清除本应用的所有数据并退出，是否继续？");
                tvMessage.setTextSize(16);
                tvMessage.setTextColor(getResources().getColor(R.color.info_text_color));
                tvMessage.setPadding(0, 0, 0, 20);
                contentContainer.addView(tvMessage);
                
                // 设置按钮
                Button btnConfirm = confirmView.findViewById(R.id.btn_close);
                btnConfirm.setText("确认撤销");
                // 以代码形式设置确认按钮的背景颜色为红色
                GradientDrawable confirmBackground = new GradientDrawable();
                confirmBackground.setShape(GradientDrawable.RECTANGLE);
                confirmBackground.setColor(Color.parseColor("#FF5252")); // 红色背景
                confirmBackground.setCornerRadius(8);
                btnConfirm.setBackground(confirmBackground);
                btnConfirm.setTextColor(Color.WHITE); // 白色文字
                
                // 添加取消按钮
                Button btnCancel = new Button(MainActivity.this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 10, 0, 0);
                btnCancel.setLayoutParams(params);
                btnCancel.setText("取消");
                btnCancel.setTextSize(18);
                // 以代码形式设置取消按钮的背景颜色为灰色
                GradientDrawable cancelBackground = new GradientDrawable();
                cancelBackground.setShape(GradientDrawable.RECTANGLE);
                cancelBackground.setColor(Color.parseColor("#757575")); // 灰色背景
                cancelBackground.setCornerRadius(8);
                btnCancel.setBackground(cancelBackground);
                btnCancel.setTextColor(Color.WHITE); // 白色文字
                
                // 将取消按钮添加到对话框底部
                LinearLayout rootLayout = (LinearLayout) confirmView;
                rootLayout.addView(btnCancel);
                
                confirmBuilder.setView(confirmView);
                AlertDialog confirmDialog = confirmBuilder.create();
                confirmDialog.setCancelable(false);
                
                // 设置确认按钮点击事件
                btnConfirm.setOnClickListener(v -> {
                    // 用户确认撤销，清除所有APP数据并退出应用
                    CustomToast.showShortToast(MainActivity.this, "正在清除数据...");
                    
                    // 关闭对话框
                    confirmDialog.dismiss();
                    
                    // 在新线程中执行数据清除操作，确保阻塞完成
                    new Thread(() -> {
                        File dataDir = getFilesDir();
                        File sharedPrefsDir = new File(getApplicationInfo().dataDir + "/shared_prefs");
                        File cacheDir = getCacheDir();
                        File externalCacheDir = getExternalCacheDir();
                        
                        // 删除私有目录下的所有文件
                        deleteDirectory(dataDir);
                        deleteDirectory(sharedPrefsDir);
                        deleteDirectory(cacheDir);
                        if (externalCacheDir != null) {
                            deleteDirectory(externalCacheDir);
                        }
                        
                        // 在主线程中显示完成提示并退出
                        runOnUiThread(() -> {
                            CustomToast.showShortToast(MainActivity.this, "数据清除完成，即将退出...");
                            // 延迟一秒退出应用，让用户看到提示
                            new Handler().postDelayed(() -> {
                                finishAffinity(); // 关闭所有Activity
                                System.exit(0);   // 退出应用进程
                            }, 1000);
                        });
                    }).start();
                });
                
                // 设置取消按钮点击事件
                btnCancel.setOnClickListener(v -> {
                    // 用户取消撤销，恢复开关状态
                    switchPrivacyPolicyConsent.setChecked(true);
                    confirmDialog.dismiss();
                });
                
                confirmDialog.show();
            }
        });
        
        // 初始化时间选择器
        LinearLayout layoutNotificationTime = dialogView.findViewById(R.id.layout_notification_time);
        TextView tvNotificationTime = dialogView.findViewById(R.id.tv_notification_time);
        
        // 设置初始时间
        int savedHour = settingsPrefs.getInt("notification_hour", 22);
        int savedMinute = settingsPrefs.getInt("notification_minute", 0);
        String timeText = String.format("%02d:%02d", savedHour, savedMinute);
        tvNotificationTime.setText(timeText);
        
        // 设置时间选择器点击事件
        layoutNotificationTime.setOnClickListener(v -> {
            android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                    MainActivity.this,
                    (view, hourOfDay, minute) -> {
                        // 保存用户选择的时间
                        SharedPreferences.Editor editor = settingsPrefs.edit();
                        editor.putInt("notification_hour", hourOfDay);
                        editor.putInt("notification_minute", minute);
                        editor.apply();
                        
                        // 更新显示的时间
                        String newTimeText = String.format("%02d:%02d", hourOfDay, minute);
                        tvNotificationTime.setText(newTimeText);
                        
                        // 重新设置通知时间
                        if (cn.pylin.xykcb.CourseNotificationManager.isNotificationEnabled(MainActivity.this)) {
                            cn.pylin.xykcb.CourseNotificationManager.cancelDailyNotification(MainActivity.this);
                            cn.pylin.xykcb.CourseNotificationManager.setDailyNotification(MainActivity.this, hourOfDay, minute);
                        }
                        
                        CustomToast.showShortToast(MainActivity.this, "通知时间已设置为 " + newTimeText);
                    },
                    savedHour, savedMinute, true
            );
            timePickerDialog.show();
        });
        
        // 根据开关状态显示或隐藏时间选择器
        layoutNotificationTime.setVisibility(dailyReminderEnabled ? View.VISIBLE : View.GONE);

        // 初始化用户信息
        SharedPreferences sharedPreferences = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String userName = sharedPreferences.getString("userName", "");
        String academyName = sharedPreferences.getString("academyName", "");
        String className = sharedPreferences.getString("className", "");
        
        tvDescription.setText("学号：" + savedUsername);
        
        // 动态显示用户信息
        boolean hasUserInfo = false;
        if (!userName.isEmpty()) {
            tvUserName.setText("姓名：" + userName);
            tvUserName.setVisibility(View.VISIBLE);
            hasUserInfo = true;
        } else {
            tvUserName.setVisibility(View.GONE);
        }
        
        if (!academyName.isEmpty()) {
            tvAcademyName.setText("学院：" + academyName);
            tvAcademyName.setVisibility(View.VISIBLE);
            hasUserInfo = true;
        } else {
            tvAcademyName.setVisibility(View.GONE);
        }
        
        if (!className.isEmpty()) {
            tvClassName.setText("班级：" + className);
            tvClassName.setVisibility(View.VISIBLE);
            hasUserInfo = true;
        } else {
            tvClassName.setVisibility(View.GONE);
        }
        
        // 如果有用户信息，则显示用户信息区域
        if (hasUserInfo) {
            layoutUserInfo.setVisibility(View.VISIBLE);
        } else {
            layoutUserInfo.setVisibility(View.GONE);
        }
    
        // 动态获取并设置版本信息
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            int versionCode = packageInfo.versionCode;
            tvVersionInfo.setText("版本号：v" + versionName + " (" + versionCode + ")");
        } catch (Exception e) {
            tvVersionInfo.setText("版本号：获取失败");
        }
    
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
    
        // 当前选中的标签（0=我的，1=设置，2=关于）
        final int[] currentTab = {0};
    
        // 更新按钮样式的方法
        Runnable updateTabButtons = () -> {
            // 更新"我的"按钮
            if (currentTab[0] == 0) {
                btnTabMine.setBackgroundResource(R.drawable.tag_button_selected);
                btnTabMine.setTextColor(Color.WHITE);
            } else {
                btnTabMine.setBackgroundResource(R.drawable.tag_button_normal);
                btnTabMine.setTextColor(ContextCompat.getColor(this, R.color.dialog_title_color));
            }
    
            // 更新"设置"按钮
            if (currentTab[0] == 1) {
                btnTabSettings.setBackgroundResource(R.drawable.tag_button_selected);
                btnTabSettings.setTextColor(Color.WHITE);
            } else {
                btnTabSettings.setBackgroundResource(R.drawable.tag_button_normal);
                btnTabSettings.setTextColor(ContextCompat.getColor(this, R.color.dialog_title_color));
            }
    
            // 更新"关于"按钮
            if (currentTab[0] == 2) {
                btnTabAbout.setBackgroundResource(R.drawable.tag_button_selected);
                btnTabAbout.setTextColor(Color.WHITE);
            } else {
                btnTabAbout.setBackgroundResource(R.drawable.tag_button_normal);
                btnTabAbout.setTextColor(ContextCompat.getColor(this, R.color.dialog_title_color));
            }
        };
    
        // 页面切换方法
        Runnable showMine = () -> {
            layoutMine.setVisibility(View.VISIBLE);
            layoutSettings.setVisibility(View.GONE);
            layoutAbout.setVisibility(View.GONE);
            
            currentTab[0] = 0;
            updateTabButtons.run();
            
            btnTabMine.setSelected(true);
            btnTabSettings.setSelected(false);
            btnTabAbout.setSelected(false);
        };
    
        Runnable showSettings = () -> {
            layoutMine.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.VISIBLE);
            layoutAbout.setVisibility(View.GONE);
            
            currentTab[0] = 1;
            updateTabButtons.run();
            
            btnTabMine.setSelected(false);
            btnTabSettings.setSelected(true);
            btnTabAbout.setSelected(false);
        };
    
        Runnable showAbout = () -> {
            layoutMine.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.GONE);
            layoutAbout.setVisibility(View.VISIBLE);
            
            currentTab[0] = 2;
            updateTabButtons.run();
            
            btnTabMine.setSelected(false);
            btnTabSettings.setSelected(false);
            btnTabAbout.setSelected(true);
        };
    
        // 默认显示"我的"页面
        showMine.run();
    
        // 标签按钮点击事件
        btnTabMine.setOnClickListener(v -> showMine.run());
        btnTabSettings.setOnClickListener(v -> showSettings.run());
        btnTabAbout.setOnClickListener(v -> showAbout.run());
    
        // "我的"页面按钮事件
        btnReLogin.setOnClickListener(v -> {
            showLoginDialog();
            dialog.dismiss();
        });
    
        // "关于"页面按钮事件
        btnPYLinTech.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pylin.cn/xykcb"));
            startActivity(intent);
            dialog.dismiss();
        });
    
        // 在showToolbarTitleDialog方法中修改
        btnCheckUpdate.setOnClickListener(v -> {CustomToast.showShortToast(this, "正在检查更新...");
            // 使用UpdateManager检查更新，标记为手动检查以清除忽略记录
            updateManager.checkForUpdates(null, true);
        });

        // 添加备案号点击事件
        tvICPInfo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://beian.miit.gov.cn/"));
            startActivity(intent);
        });
        btnShowWelcome.setOnClickListener(v -> {
        // 启动引导页面
        Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
        // 添加标志，表示这是从设置页面进入的，不需要检查首次启动
        intent.putExtra("from_settings", true);
        startActivity(intent);
        dialog.dismiss();
    });

        // 加入内测QQ群按钮点击事件
        btnJoinQQGroup.setOnClickListener(v -> {
            String qqGroupNumber = "1047462477";
            try {
                // 使用QQ群key，这里使用一个示例群号
                String qqGroupKey = "5qSyFS9b8D1iglO-8U4LkXLX8DtMLVSj"; // 可以替换为实际的内测QQ群key
                // 尝试使用QQ群key打开QQ
                String url = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + qqGroupKey;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                // 提供备选方案：复制群号到剪贴板
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("群号", qqGroupNumber);
                clipboard.setPrimaryClip(clip);
                CustomToast.showShortToast(this, "未能跳转到QQ，已复制群号到剪贴板");
            }
            dialog.dismiss();
        });
    }

    // 添加更新备注栏显示状态的方法
    private void updateNoteEditTextVisibility(boolean visible) {
        EditText noteEditText = findViewById(R.id.toolbar_edit);
        TextView appTitle = findViewById(R.id.toolbar_app_title);
        
        noteEditText.setVisibility(visible ? View.VISIBLE : View.GONE);
        appTitle.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新小组件，使其完全更新一次
        refreshWidget();
        // 检查更新
        updateManager.checkForUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 通知权限已授予，启用通知
                cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(this, true);
                CustomToast.showShortToast(this, "已开启每日课程提醒");
            } else {
                // 通知权限被拒绝，关闭开关并提示用户
                SharedPreferences settingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
                settingsPrefs.edit().putBoolean("daily_course_reminder", false).apply();
                
                // 如果对话框还在显示，更新开关状态
                if (switchDailyCourseReminder != null) {
                    switchDailyCourseReminder.setChecked(false);
                }
                
                CustomToast.showShortToast(this, "需要通知权限才能使用每日课程提醒功能");
            }
        }
    }

    private void refreshWidget() {
        // 发送广播刷新小组件
        Intent intent = new Intent(this, CourseWidgetProvider.class);
        intent.setAction(CourseWidgetProvider.ACTION_REFRESH);
        sendBroadcast(intent);
        
        // 获取AppWidgetManager实例
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        // 获取小组件的ComponentName
        ComponentName thisAppWidget = new ComponentName(this.getPackageName(), CourseWidgetProvider.class.getName());
        // 获取所有小组件ID
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        
        // 如果有小组件实例，则触发更新
        if (appWidgetIds.length > 0) {
            // 手动调用小组件的onUpdate方法
            CourseWidgetProvider widgetProvider = new CourseWidgetProvider();
            widgetProvider.onUpdate(this, appWidgetManager, appWidgetIds);
        }
    }

}
