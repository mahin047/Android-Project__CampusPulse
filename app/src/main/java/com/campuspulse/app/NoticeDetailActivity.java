
package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.campuspulse.app.databinding.ActivityNoticeDetailBinding;

import java.util.Date;

public class NoticeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "n_title";
    public static final String EXTRA_BODY = "n_body";
    public static final String EXTRA_CATEGORY = "n_category";
    public static final String EXTRA_PRIORITY = "n_priority";
    public static final String EXTRA_AUTHOR = "n_author";
    public static final String EXTRA_TIME = "n_time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityNoticeDetailBinding b =
                ActivityNoticeDetailBinding.inflate(getLayoutInflater());

        setContentView(b.getRoot());

        String priority =
                getIntent().getStringExtra(EXTRA_PRIORITY);

        NoticeModel n = new NoticeModel();
        n.setCategory(getIntent().getStringExtra(EXTRA_CATEGORY));
        n.setPriority(priority);

        int color = NoticeModel.priorityColor(priority);

        b.tvBadge.setText(NoticeModel.badgeText(n));
        b.tvBadge.setTextColor(color);

        b.tvBadge.setBackgroundTintList(
                ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(color, 0x33)
                )
        );

        b.tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        b.tvBody.setText(getIntent().getStringExtra(EXTRA_BODY));

        long t = getIntent().getLongExtra(EXTRA_TIME, -1L);

        String when = TimeUtil.fullDate(
                t < 0 ? null : new Date(t)
        );

        String author = getIntent().getStringExtra(EXTRA_AUTHOR);

        b.tvMeta.setText(
                (author == null ? "Administration" : author)
                        + "  •  " + when
        );

        b.btnBack.setOnClickListener(v -> finish());
    }
}
