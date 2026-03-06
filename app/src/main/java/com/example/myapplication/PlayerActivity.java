package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerControlView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private List<Sentence> sentenceList = new ArrayList<>();
    private int currentIndex = 0;
    private TextView tvCompletedSentences;

    // 动态接收的配置参数
    private int audioResId;
    private String srtFileName;
    private String pageTitle;

    private static class Sentence {
        long startTime;
        long endTime;
        String text;
        Sentence(long startTime, long endTime, String text) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 绑定刚刚重命名的布局
        setContentView(R.layout.activity_player);

        // 接收从首页传过来的数据
        audioResId = getIntent().getIntExtra("AUDIO_RES_ID", R.raw.cet6_2025_06_1);
        srtFileName = getIntent().getStringExtra("SRT_FILE_NAME");
        pageTitle = getIntent().getStringExtra("PAGE_TITLE");

        if (srtFileName == null) srtFileName = "cet6_2025_06_1.srt";
        if (pageTitle != null) setTitle(pageTitle);

        loadSrtData();

        if (!sentenceList.isEmpty()) {
            initPlayer();
            setupUI();
        } else {
            Toast.makeText(this, "字幕加载失败，请检查 assets 是否有 " + srtFileName, Toast.LENGTH_LONG).show();
        }
    }

    private void loadSrtData() {
        try {
            InputStream is = getAssets().open(srtFileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            long start = 0, end = 0;
            StringBuilder textBuilder = new StringBuilder();
            int state = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    if (textBuilder.length() > 0) {
                        sentenceList.add(new Sentence(start, end, textBuilder.toString().trim()));
                        textBuilder.setLength(0);
                    }
                    state = 0;
                    continue;
                }
                if (state == 0) {
                    state = 1;
                } else if (state == 1) {
                    String[] times = line.split("-->");
                    if (times.length == 2) {
                        start = parseSrtTime(times[0]);
                        end = parseSrtTime(times[1]);
                    }
                    state = 2;
                } else if (state == 2) {
                    textBuilder.append(line).append(" ");
                }
            }
            if (textBuilder.length() > 0) {
                sentenceList.add(new Sentence(start, end, textBuilder.toString().trim()));
            }
            reader.close();

            loadCorrectedSubtitles();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 注意这里：缓存的表名加上了 srtFileName，防止不同年份的字幕互相覆盖
    private void loadCorrectedSubtitles() {
        SharedPreferences prefs = getSharedPreferences("Subtitles_" + srtFileName, MODE_PRIVATE);
        for (int i = 0; i < sentenceList.size(); i++) {
            String savedText = prefs.getString("sentence_" + i, null);
            if (savedText != null) {
                sentenceList.get(i).text = savedText;
            }
        }
    }

    private void saveCorrectedSubtitles() {
        SharedPreferences.Editor editor = getSharedPreferences("Subtitles_" + srtFileName, MODE_PRIVATE).edit();
        for (int i = 0; i < sentenceList.size(); i++) {
            editor.putString("sentence_" + i, sentenceList.get(i).text);
        }
        editor.apply();
    }

    private long parseSrtTime(String timeString) {
        timeString = timeString.trim().replace(",", ":");
        String[] parts = timeString.split(":");
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            long millis = Long.parseLong(parts[3]);
            return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis;
        } catch (Exception e) {
            return 0;
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        // 动态加载传过来的音频资源文件
        MediaItem mediaItem = MediaItem.fromUri("android.resource://" + getPackageName() + "/" + audioResId);
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    private void setupUI() {
        PlayerControlView playerControlView = findViewById(R.id.player_control_view);
        if (playerControlView != null) playerControlView.setPlayer(player);

        tvCompletedSentences = findViewById(R.id.tv_completed_sentences);
        Button btnReplay = findViewById(R.id.btn_replay);
        if (btnReplay != null) btnReplay.setOnClickListener(v -> replayCurrentSentence());
        Button btnPrev = findViewById(R.id.btn_prev);
        if (btnPrev != null) btnPrev.setOnClickListener(v -> playPreviousSentence());
        Button btnNext = findViewById(R.id.btn_next);
        if (btnNext != null) btnNext.setOnClickListener(v -> playNextSentence());
        Button btnEdit = findViewById(R.id.btn_edit_subtitles);
        if (btnEdit != null) btnEdit.setOnClickListener(v -> showEditSubtitlesDialog());

        updateCompletedText();
        replayCurrentSentence();
    }

    private void showEditSubtitlesDialog() {
        if (player != null && player.isPlaying()) player.pause();
        StringBuilder sb = new StringBuilder();
        for (Sentence s : sentenceList) {
            sb.append(s.text).append("\n");
        }

        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setLines(15);
        input.setVerticalScrollBarEnabled(true);
        input.setText(sb.toString().trim());
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle("校对原文 (" + pageTitle + ")")
                .setMessage("请勿增删行数！")
                .setView(input)
                .setPositiveButton("保存修改", (dialog, which) -> {
                    String[] newLines = input.getText().toString().split("\\r?\\n");
                    List<String> validLines = new ArrayList<>();
                    for (String line : newLines) validLines.add(line.trim());
                    while (!validLines.isEmpty() && validLines.get(validLines.size() - 1).isEmpty()) {
                        validLines.remove(validLines.size() - 1);
                    }
                    if (validLines.size() == sentenceList.size()) {
                        for (int i = 0; i < validLines.size(); i++) {
                            sentenceList.get(i).text = validLines.get(i);
                        }
                        saveCorrectedSubtitles();
                        updateCompletedText();
                        Toast.makeText(this, "修改已永久保存！", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "保存失败！行数不匹配。", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCompletedText() {
        if (tvCompletedSentences == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentIndex; i++) {
            sb.append(i + 1).append(". ").append(sentenceList.get(i).text).append("\n\n");
        }
        tvCompletedSentences.setText(sb.toString());
    }

    private void playPreviousSentence() {
        if (currentIndex > 0) {
            currentIndex--;
            updateCompletedText();
            replayCurrentSentence();
        } else {
            Toast.makeText(this, "已经是第一句啦！", Toast.LENGTH_SHORT).show();
        }
    }

    private void playNextSentence() {
        if (currentIndex < sentenceList.size() - 1) {
            currentIndex++;
            updateCompletedText();
            replayCurrentSentence();
        } else {
            Toast.makeText(this, "已经是最后一句啦！", Toast.LENGTH_SHORT).show();
        }
    }

    private final Runnable checkProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && currentIndex < sentenceList.size()) {
                long currentEndTime = sentenceList.get(currentIndex).endTime;
                long currentPos = player.getCurrentPosition();
                if (currentPos >= currentEndTime) {
                    player.pause();
                    showInputDialog();
                    return;
                }
            }
            progressHandler.postDelayed(this, 100);
        }
    };

    private void showInputDialog() {
        final EditText input = new EditText(this);
        String hint = "当前第 " + (currentIndex + 1) + " 句 / 共 " + sentenceList.size() + " 句";
        new AlertDialog.Builder(this)
                .setTitle("听写输入")
                .setMessage(hint + "\n\n请输入刚才听到的句子：")
                .setView(input)
                .setPositiveButton("检查", (dialog, which) -> checkAnswer(input.getText().toString()))
                .setCancelable(false)
                .show();
    }

    private void checkAnswer(String userInput) {
        if (currentIndex >= sentenceList.size()) return;

        String correctText = sentenceList.get(currentIndex).text;

        // 依然保留基本的数据清洗：去标点、去多余空格、转小写
        String cleanInput = userInput.replaceAll("[^a-zA-Z0-9]", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase();
        String cleanCorrect = correctText.replaceAll("[^a-zA-Z0-9]", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase();

        // 计算两个字符串的“编辑距离”（相差了几个字母）
        int distance = getLevenshteinDistance(cleanInput, cleanCorrect);

        // 容错逻辑：允许一定的拼写错误。
        // 例如：长度超过20个字母的句子，允许2个字母的拼写错误；短句允许1个错误。完全正确是0。
        int allowedErrors = cleanCorrect.length() / 15; // 每15个字母允许错1个

        if (distance <= allowedErrors) {
            if (distance == 0) {
                Toast.makeText(this, "完全正确！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "基本正确！(存在轻微拼写误差)\n" + correctText, Toast.LENGTH_LONG).show();
            }

            currentIndex++;
            updateCompletedText();
            if (currentIndex < sentenceList.size()) {
                replayCurrentSentence();
            } else {
                Toast.makeText(this, "太牛了！听力全部通关！", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "错误，请重听。答案很长注意拼写哦~", Toast.LENGTH_SHORT).show();
            replayCurrentSentence();
        }
    }

    /**
     * 动态规划算法：计算两个字符串的编辑距离
     */
    private int getLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1],
                            Math.min(dp[i - 1][j], dp[i][j - 1])) + 1;
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private void replayCurrentSentence() {
        if (player != null && currentIndex < sentenceList.size()) {
            progressHandler.removeCallbacks(checkProgressRunnable);
            long currentStartTime = sentenceList.get(currentIndex).startTime;
            player.seekTo(currentStartTime);
            player.play();
            progressHandler.post(checkProgressRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        progressHandler.removeCallbacks(checkProgressRunnable);
    }
}