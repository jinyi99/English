package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 分别存储四级和六级的数据列表
    private final List<ListeningItem> cet4List = new ArrayList<>();
    private final List<ListeningItem> cet6List = new ArrayList<>();

    // 数据模型内部类
    private static class ListeningItem {
        String title;
        int audioResId;
        String srtFileName;

        ListeningItem(String title, int audioResId, String srtFileName) {
            this.title = title;
            this.audioResId = audioResId;
            this.srtFileName = srtFileName;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("选择考试类型");

        // 1. 初始化数据库 (以后加新听力，直接往这两个列表里加就行)
        initData();

        // 2. 绑定按钮并设置点击事件
        Button btnCet4 = findViewById(R.id.btn_cet4);
        btnCet4.setOnClickListener(v -> showListDialog("四级听力精选", cet4List));

        Button btnCet6 = findViewById(R.id.btn_cet6);
        btnCet6.setOnClickListener(v -> showListDialog("六级听力精选", cet6List));
    }

    /**
     * 准备所有听力数据
     */
    private void initData() {
        // --- 添加四级数据 ---

        cet4List.add(new ListeningItem("2025年 英语四级听力第一套",R.raw.cet4_2025_06_1, "cet4_2025_06_1.srt"));
        cet4List.add(new ListeningItem("2025年 英语四级听力第二套",R.raw.cet4_2025_06_2, "cet4_2025_06_2.srt"));
        // --- 添加六级数据 ---
        cet6List.add(new ListeningItem("2025年6月 英语六级听力第一套", R.raw.cet6_2025_06_1, "cet6_2025_06_1.srt"));
        cet6List.add(new ListeningItem("2025年6月 英语六级听力第二套", R.raw.cet6_2025_06_2, "cet6_2025_06_2.srt"));
    }

    /**
     * 核心方法：弹出一个包含列表的对话框
     */
    private void showListDialog(String dialogTitle, List<ListeningItem> dataList) {
        // 如果列表是空的，给个提示
        if (dataList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(dialogTitle)
                    .setMessage("暂无数据，敬请期待！")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        // 把 List<ListeningItem> 转换成系统对话框需要的 String[] 数组
        String[] displayTitles = new String[dataList.size()];
        for (int i = 0; i < dataList.size(); i++) {
            displayTitles[i] = dataList.get(i).title;
        }

        // 构建并显示列表对话框
        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setItems(displayTitles, (dialog, which) -> {
                    // which 就是用户点击的行号（从 0 开始）
                    ListeningItem selectedItem = dataList.get(which);

                    // 跳转到播放页面
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra("AUDIO_RES_ID", selectedItem.audioResId);
                    intent.putExtra("SRT_FILE_NAME", selectedItem.srtFileName);
                    intent.putExtra("PAGE_TITLE", selectedItem.title);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null) // 加一个取消按钮方便返回
                .show();
    }
}