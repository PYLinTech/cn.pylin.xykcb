package cn.pylin.xykcb;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 多账号管理器类
 * 用于管理多个学校账号的增删改查功能
 */
public class MultiAccountManager {
    private static final String PREF_NAME = "MultiAccountInfo";
    private static final String KEY_ACCOUNT_LIST = "account_list";
    
    private Context context;
    
    public MultiAccountManager(Context context) {
        this.context = context;
    }
    
    /**
     * 账号信息类
     */
    public static class AccountInfo {
        public String username;
        public String password;
        public String schoolCode;
        public String schoolName;
        public long timestamp;
        
        public AccountInfo(String username, String password, String schoolCode, String schoolName) {
            this.username = username;
            this.password = password;
            this.schoolCode = schoolCode;
            this.schoolName = schoolName;
            this.timestamp = System.currentTimeMillis();
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("password", password);
            json.put("schoolCode", schoolCode);
            json.put("schoolName", schoolName);
            json.put("timestamp", timestamp);
            return json;
        }
        
        public static AccountInfo fromJson(JSONObject json) throws JSONException {
            AccountInfo account = new AccountInfo(
                json.getString("username"),
                json.getString("password"),
                json.getString("schoolCode"),
                json.getString("schoolName")
            );
            account.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            return account;
        }
        
        // Getter方法
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public String getSchoolCode() {
            return schoolCode;
        }
        
        public String getSchoolName() {
            return schoolName;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 保存账号到多账号列表
     */
    public void saveAccount(String username, String password, String schoolCode, String schoolName) {
        List<AccountInfo> accounts = getAccountList();
        
        // 检查是否已存在相同账号
        for (int i = 0; i < accounts.size(); i++) {
            AccountInfo account = accounts.get(i);
            if (account.username.equals(username) && account.schoolCode.equals(schoolCode)) {
                // 更新现有账号
                accounts.set(i, new AccountInfo(username, password, schoolCode, schoolName));
                saveAccountList(accounts);
                return;
            }
        }
        
        // 添加新账号
        accounts.add(new AccountInfo(username, password, schoolCode, schoolName));
        saveAccountList(accounts);
    }
    
    /**
     * 获取所有账号列表
     */
    public List<AccountInfo> getAccountList() {
        List<AccountInfo> accounts = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String accountListJson = prefs.getString(KEY_ACCOUNT_LIST, "[]");
        
        try {
            JSONArray jsonArray = new JSONArray(accountListJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                accounts.add(AccountInfo.fromJson(json));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return accounts;
    }
    
    /**
     * 根据用户名和学校代码获取账号信息
     */
    public AccountInfo getAccount(String username, String schoolCode) {
        List<AccountInfo> accounts = getAccountList();
        for (AccountInfo account : accounts) {
            if (account.username.equals(username) && account.schoolCode.equals(schoolCode)) {
                return account;
            }
        }
        return null;
    }
    
    /**
     * 删除指定账号
     */
    public void deleteAccount(String username, String schoolCode) {
        List<AccountInfo> accounts = getAccountList();
        for (int i = 0; i < accounts.size(); i++) {
            AccountInfo account = accounts.get(i);
            if (account.username.equals(username) && account.schoolCode.equals(schoolCode)) {
                accounts.remove(i);
                saveAccountList(accounts);
                return;
            }
        }
    }
    
    /**
     * 清空所有账号
     */
    public void clearAllAccounts() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_ACCOUNT_LIST).apply();
    }
    
    /**
     * 保存账号列表到SharedPreferences
     */
    private void saveAccountList(List<AccountInfo> accounts) {
        JSONArray jsonArray = new JSONArray();
        for (AccountInfo account : accounts) {
            try {
                jsonArray.put(account.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ACCOUNT_LIST, jsonArray.toString()).apply();
    }
}