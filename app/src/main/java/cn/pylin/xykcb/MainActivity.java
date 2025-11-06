package cn.pylin.xykcb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {
    static String Week;
    private long lastBackPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000;
    public static RecyclerView recyclerView;
    private CourseAdapter adapter;
    private LoginManager loginManager;
    private UpdateManager updateManager;
    private Switch switchDailyCourseReminder; // 添加为成员变量，解决作用域问题
    private CustomCourseUpdateReceiver customCourseUpdateReceiver; // 自定义课程更新广播接收器
    


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

        // 在登录之前创建空白的课程列表UI
        createEmptyCourseListUI();

        // 然后初始化登录管理器
        loginManager = new LoginManager(this, new LoginManager.CourseDataCallback() {
            @Override
            public void onCourseDataReceived(List<List<Course>> weeklyCourses) {
                runOnUiThread(() -> {
                    try {
                        String[] weekHeaders = new String[] { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
                        // 使用基于日期判断的周次，确保本地数据也能显示正确周次
                        Week = String.valueOf(CourseDataManager.getCurrentWeek(MainActivity.this));
                        
                        // 合并标准课程和自定义课程
                        List<List<Course>> mergedCourses = CourseDataManager.getMergedCourses(MainActivity.this, weeklyCourses);

                        if (adapter == null) {
                            // 先获取RecyclerView的高度，再创建adapter
                            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    int recyclerViewHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() - recyclerView.getPaddingBottom();
                                    
                                    // 在获取到正确高度后创建adapter
                                    adapter = new CourseAdapter(MainActivity.this, mergedCourses, weekHeaders);
                                    adapter.setRecyclerViewHeight(recyclerViewHeight);
                                    recyclerView.setAdapter(adapter);
                                }
                            });
                        } else {
                            // 复用现有adapter，更新数据
                            adapter.updateCourses(mergedCourses);
                        }
                        
                        // 获取并设置当前周次
                        int currentWeekNum = CourseDataManager.getCurrentWeek(MainActivity.this);
                        adapter.setCurrentWeek(currentWeekNum);
                    } catch (Exception e) {
                        //
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    CustomToast.showShortToast(MainActivity.this, message);
                    
                    // 如果错误消息包含"登录失败"或"登录过期"，重新拉起登录窗口
                    if (message.contains("登录失败") || message.contains("暂不支持")) {
                        showLoginDialog();
                    }
                });
            }
        });

        initNoteEditText();
        checkLoginStatus();
        
        // 注册自定义课程更新广播接收器
        registerCustomCourseUpdateReceiver();
    }

    private void initNoteEditText() {
        EditText noteEditText = findViewById(R.id.toolbar_edit);
        
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

    /**
     * 在登录之前创建空白的课程列表UI
     */
    private void createEmptyCourseListUI() {
        // 直接在主线程中创建空白的课程列表UI
        try {
            // 创建空的课程数据（7天的空列表）
            List<List<Course>> emptyCourses = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                emptyCourses.add(new ArrayList<>());
            }
            
            String[] weekHeaders = new String[] { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
            Week = "1"; // 默认显示第一周
            
            // 获取RecyclerView的高度，然后创建adapter
            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int recyclerViewHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() - recyclerView.getPaddingBottom();
                    
                    // 创建空的课程适配器
                    adapter = new CourseAdapter(MainActivity.this, emptyCourses, weekHeaders);
                    adapter.setRecyclerViewHeight(recyclerViewHeight);
                    adapter.setCurrentWeek(1); // 设置当前周次为第一周
                    recyclerView.setAdapter(adapter);

                }
            });
        } catch (Exception e) {
            // 如果出现异常，确保UI不会崩溃
            e.printStackTrace();
        }
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

        // 账号下拉图标点击事件
        ImageView ivAccountDropdown = view.findViewById(R.id.iv_account_dropdown);
        ivAccountDropdown.setOnClickListener(v -> {
            showAccountListDialog(etUsername, etPassword, tvSchoolSelector, selectedSchool, selectedSchoolName);
        });

        // 密码显示/隐藏切换 - 默认加密显示
        final boolean[] isPasswordVisible = {false}; // 移到外部，默认false表示加密显示
        // 设置初始状态为加密显示
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ImageView ivPasswordToggle = view.findViewById(R.id.iv_password_toggle);
        ivPasswordToggle.setImageResource(R.drawable.ic_eye_closed); // 默认显示闭眼图标
        
        ivPasswordToggle.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                // 切换为隐藏
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivPasswordToggle.setImageResource(R.drawable.ic_eye_closed);
                isPasswordVisible[0] = false;
            } else {
                // 切换为显示
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivPasswordToggle.setImageResource(R.drawable.ic_eye_open);
                isPasswordVisible[0] = true;
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

    // 新增方法：显示多账号列表弹窗
    private void showAccountListDialog(EditText etUsername, EditText etPassword, TextView tvSchoolSelector, String[] selectedSchool, String[] selectedSchoolName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_account_list, null);
        
        LinearLayout accountListContainer = view.findViewById(R.id.account_list_container);
        AlertDialog accountDialog = builder.setView(view).create();
        
        // 获取多账号列表
        MultiAccountManager accountManager = new MultiAccountManager(this);
        List<MultiAccountManager.AccountInfo> accountList = accountManager.getAccountList();
        
        if (accountList.isEmpty()) {
            // 如果没有保存的账号，显示提示信息
            TextView emptyTextView = new TextView(this);
            emptyTextView.setText("暂无保存的账号");
            emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            emptyTextView.setTextColor(getResources().getColor(R.color.edit_text_tip_color));
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setPadding(0, 40, 0, 40);
            accountListContainer.addView(emptyTextView);
        } else {
            // 显示账号列表
            for (MultiAccountManager.AccountInfo account : accountList) {
                View accountItemView = LayoutInflater.from(this).inflate(R.layout.item_account, null);
                
                TextView tvUsername = accountItemView.findViewById(R.id.tv_username);
                TextView tvSchoolName = accountItemView.findViewById(R.id.tv_school_name);
                ImageButton btnDeleteAccount = accountItemView.findViewById(R.id.btn_delete_account);
                
                tvUsername.setText(account.getUsername());
                tvSchoolName.setText(account.getSchoolName());
                
                // 账号项点击事件
                accountItemView.setOnClickListener(v -> {
                    // 填充账号密码到登录界面
                    etUsername.setText(account.getUsername());
                    etPassword.setText(account.getPassword());
                    tvSchoolSelector.setText(account.getSchoolName());
                    tvSchoolSelector.setTextColor(getResources().getColor(R.color.edit_text_color));
                    selectedSchool[0] = account.getSchoolCode();
                    selectedSchoolName[0] = account.getSchoolName();
                    accountDialog.dismiss();
                });
                
                // 删除按钮点击事件
                btnDeleteAccount.setOnClickListener(v -> {
                    // 检查是否删除的是当前登录的账号
                    SharedPreferences loginPrefs = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
                    String currentUsername = loginPrefs.getString("username", "");
                    String currentSchoolCode = loginPrefs.getString("schoolCode", "");
                    
                    // 检查是否删除的是当前登录界面显示的账号
                    boolean isCurrentDisplayedAccount = account.getUsername().equals(etUsername.getText().toString()) && 
                                                       account.getSchoolCode().equals(selectedSchool[0]);
                    
                    // 删除账号
                    accountManager.deleteAccount(account.getUsername(), account.getSchoolCode());
                    
                    // 如果删除的是当前登录的账号，清除登录信息
                    if (account.getUsername().equals(currentUsername) && account.getSchoolCode().equals(currentSchoolCode)) {
                        loginPrefs.edit()
                                .remove("username")
                                .remove("password")
                                .remove("schoolCode")
                                .remove("schoolName")
                                .apply();
                    }
                    
                    // 如果删除的是当前登录界面显示的账号，清空输入框
                    if (isCurrentDisplayedAccount) {
                        etUsername.setText("");
                        etPassword.setText("");
                        tvSchoolSelector.setText("选择学校");
                        tvSchoolSelector.setTextColor(getResources().getColor(R.color.edit_text_tip_color));
                        selectedSchool[0] = null;
                        selectedSchoolName[0] = null;
                    }
                    
                    // 刷新列表
                    accountListContainer.removeView(accountItemView);
                    if (accountListContainer.getChildCount() == 0) {
                        TextView emptyTextView = new TextView(this);
                        emptyTextView.setText("暂无保存的账号");
                        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        emptyTextView.setTextColor(getResources().getColor(R.color.edit_text_tip_color));
                        emptyTextView.setGravity(Gravity.CENTER);
                        emptyTextView.setPadding(0, 40, 0, 40);
                        accountListContainer.addView(emptyTextView);
                    }
                });
                
                accountListContainer.addView(accountItemView);
            }
        }
        
        accountDialog.show();
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
        TextView tvOpenSource = dialogView.findViewById(R.id.tvOpenSource);
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
        switchDailyCourseReminder = dialogView.findViewById(R.id.switch_daily_course_reminder);
        
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
        
        // 设置明日课程提醒开关监听器
        switchDailyCourseReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存设置
            settingsPrefs.edit().putBoolean("daily_course_reminder", isChecked).apply();
            
            // 显示或隐藏时间选择器
            LinearLayout layoutNotificationTime = dialogView.findViewById(R.id.layout_notification_time);
            TextView tvWidgetWarning = dialogView.findViewById(R.id.tv_widget_warning);
            if (layoutNotificationTime != null) {
                layoutNotificationTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
            if (tvWidgetWarning != null) {
                // 设置开且小组件数目小于1则显示；大于1不显示；设置关不管1都不显示
                int widgetCount = getWidgetCount();
                if (isChecked && widgetCount < 1) {
                    tvWidgetWarning.setVisibility(View.VISIBLE);
                } else {
                    tvWidgetWarning.setVisibility(View.GONE);
                }
            }
            
            // 启用或禁用明日课程提醒
            if (isChecked) {
                handleNotificationSwitchOn();
            } else {
                handleNotificationSwitchOff();
            }
        });
        
        // 获取撤销同意用户协议及隐私政策开关
        Switch switchPrivacyPolicyConsent = dialogView.findViewById(R.id.switch_privacy_policy_consent);
        
        // 设置撤销同意用户协议及隐私政策开关监听器
        switchPrivacyPolicyConsent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                // 用户撤销同意，显示确认弹窗
                PrivacyPolicyManager.showRevokeConsentDialog(MainActivity.this, () -> {
                    // 用户取消撤销，恢复开关状态
                    switchPrivacyPolicyConsent.setChecked(true);
                });
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

                        CustomToast.showShortToast(MainActivity.this, "通知时间已设置为 " + newTimeText);
                    },
                    savedHour, savedMinute, true
            );
            timePickerDialog.show();
        });
        
        // 根据开关状态显示或隐藏时间选择器和警告文本
        layoutNotificationTime.setVisibility(dailyReminderEnabled ? View.VISIBLE : View.GONE);
        TextView tvWidgetWarning = dialogView.findViewById(R.id.tv_widget_warning);
        if (tvWidgetWarning != null) {
            // 设置开且小组件数目小于1则显示；大于1不显示；设置关不管1都不显示
            int widgetCount = getWidgetCount();
            if (dailyReminderEnabled && widgetCount < 1) {
                tvWidgetWarning.setVisibility(View.VISIBLE);
            } else {
                tvWidgetWarning.setVisibility(View.GONE);
            }
        }

        // 初始化用户信息
        updateUserInfoDisplay(tvDescription, tvUserName, tvAcademyName, tvClassName, layoutUserInfo);
    
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

        // 管理自定义课程按钮事件
        Button btnManageCustomCourses = dialogView.findViewById(R.id.btn_manage_custom_courses);
        btnManageCustomCourses.setOnClickListener(v -> {
            // 显示管理自定义课程弹窗
            showManageCustomCoursesDialog();
        });

        // "关于"页面按钮事件
        btnPYLinTech.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pylin.cn/xykcb"));
            startActivity(intent);
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
        
        // 添加开源地址点击事件
        tvOpenSource.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PYLinTech/cn.pylin.xykcb"));
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
        });

        // 创建桌面小组件按钮点击事件
        View layoutCreateWidget = layoutSettings.findViewById(R.id.layout_create_widget);
        layoutCreateWidget.setOnClickListener(v -> {
            createDesktopWidget();
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
    protected void onDestroy() {
        super.onDestroy();
        // 取消注册自定义课程更新广播接收器
        unregisterCustomCourseUpdateReceiver();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 只要界面发生变化就刷新UI
        handleOrientationChange();

    }
    
    /**
     * 处理屏幕方向变化的UI重载逻辑
     */
    private void handleOrientationChange() {
        // 重新设置RecyclerView的LayoutManager以适应新的屏幕尺寸
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
        
        // 更新UI布局
        updateUILayout();
        
        // 更新备注栏的显示状态
        SharedPreferences settingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        boolean noteVisible = settingsPrefs.getBoolean("note_visible", false);
        updateNoteEditTextVisibility(noteVisible);
    }
    
    /**
     * 统一更新UI布局
     */
    private void updateUILayout() {
        // 重新计算RecyclerView高度
        if (adapter != null && recyclerView != null) {
            recyclerView.post(() -> {
                int recyclerViewHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() - recyclerView.getPaddingBottom();
                adapter.setRecyclerViewHeight(recyclerViewHeight);
                
                // 通知适配器数据变化，触发UI重绘
                if (adapter.getItemCount() > 0) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    /**
     * 处理开关开启逻辑
     */
    private void handleNotificationSwitchOn() {
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 请求通知权限
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        1001);
                // 先显示开关和时间设置，等权限请求结果后再处理通知设置
                return;
            }
        }
        
        // 权限已授予，启用通知
        cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(this, true);
        CustomToast.showShortToast(this, "已开启明日课程提醒");
    }
    
    /**
     * 处理开关关闭逻辑
     */
    private void handleNotificationSwitchOff() {
        cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(this, false);
        
        // 检查是否是因为权限被拒绝导致的关闭
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 权限被拒绝导致的关闭，不显示"已关闭"提示
                return;
            }
        }
        
        // 正常关闭，显示关闭提示
        CustomToast.showShortToast(this, "已关闭明日课程提醒");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 通知权限已授予，启用通知
                cn.pylin.xykcb.CourseNotificationManager.setNotificationEnabled(this, true);
                CustomToast.showShortToast(this, "已开启明日课程提醒");
            } else {
                // 通知权限被拒绝，关闭开关并提示用户
                SharedPreferences settingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
                settingsPrefs.edit().putBoolean("daily_course_reminder", false).apply();
                
                // 如果对话框还在显示，更新开关状态和时间设置区域
                if (switchDailyCourseReminder != null) {
                    switchDailyCourseReminder.setChecked(false);
                }
                
                CustomToast.showShortToast(this, "需要通知权限才能使用明日课程提醒功能");
            }
        }
    }

    /**
     * 获取已添加的小组件数量
     * @return 小组件数量
     */
    private int getWidgetCount() {
        // 获取AppWidgetManager实例
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        // 获取小组件的ComponentName
        ComponentName thisAppWidget = new ComponentName(this.getPackageName(), CourseWidgetProvider.class.getName());
        // 获取所有小组件ID
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        
        return appWidgetIds.length;
    }

    /**
     * 创建桌面小组件
     */
    private void createDesktopWidget() {
        // 检查Android版本是否支持requestPinAppWidget方法（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            ComponentName appWidget = new ComponentName(this, CourseWidgetProvider.class);
            
            // 检查小组件是否可用
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                // 创建PinAppWidget请求
                Intent pinnedWidgetCallbackIntent = new Intent(this, MainActivity.class);
                PendingIntent successCallback = PendingIntent.getActivity(
                    this, 0, pinnedWidgetCallbackIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                
                // 请求固定小组件
                appWidgetManager.requestPinAppWidget(appWidget, null, successCallback);
                CustomToast.showShortToast(this, "添加失败，请长按桌面进行添加");
            } else {
                CustomToast.showShortToast(this, "添加失败，请长按桌面进行添加");
            }
        } else {
            CustomToast.showShortToast(this, "添加失败，请长按桌面进行添加");
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

    /**
     * 注册自定义课程更新广播接收器
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerCustomCourseUpdateReceiver() {
        if (customCourseUpdateReceiver == null) {
            customCourseUpdateReceiver = new CustomCourseUpdateReceiver();
            IntentFilter filter = new IntentFilter("CUSTOM_COURSE_UPDATED");
            // 为Android 15+添加包名过滤器，确保广播只在应用内传递
            filter.addCategory(getPackageName());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(customCourseUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(customCourseUpdateReceiver, filter);
            }
        }
    }
    
    /**
     * 取消注册自定义课程更新广播接收器
     */
    private void unregisterCustomCourseUpdateReceiver() {
        if (customCourseUpdateReceiver != null) {
            try {
                unregisterReceiver(customCourseUpdateReceiver);
                customCourseUpdateReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器可能已经被取消注册，忽略异常
            }
        }
    }
    
    /**
     * 自定义课程更新广播接收器
     */
    private class CustomCourseUpdateReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("CUSTOM_COURSE_UPDATED".equals(intent.getAction())) {
                // 收到自定义课程更新通知，刷新课程表
                refreshCourseTable();
            }
        }
    }
    
    /**
     * 刷新课程表数据
     */
    void refreshCourseTable() {
        if (loginManager != null) {
            // 重新加载课程数据
            loginManager.loadLocalCourseData();
        }
    }

    private void showManageCustomCoursesDialog() {
        CustomCourseManagementDialog dialog = new CustomCourseManagementDialog(this);
        dialog.show();
    }

    /**
     * 更新用户信息显示，从LoginManager的运行时变量读取
     */
    private void updateUserInfoDisplay(TextView tvDescription, TextView tvUserName, 
                                      TextView tvAcademyName, TextView tvClassName, 
                                      LinearLayout layoutUserInfo) {
        // 从SharedPreferences读取用户名，从LoginManager运行时变量读取其他用户信息
        SharedPreferences sharedPreferences = getSharedPreferences("LoginInfo", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String userName = "";
        String academyName = "";
        String className = "";
        
        if (loginManager != null) {
            userName = loginManager.getRuntimeUserName();
            academyName = loginManager.getRuntimeAcademyName();
            className = loginManager.getRuntimeClassName();
        }
        
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
    }

}
